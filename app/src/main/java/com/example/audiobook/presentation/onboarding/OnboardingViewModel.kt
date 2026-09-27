package com.example.audiobook.presentation.onboarding

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.DiscoveryStatus
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.OnboardingEditEntity
import com.example.audiobook.data.room.entity.PendingDiscoveryEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.ScanPhase
import com.example.audiobook.domain.usecases.ScanProgress
import com.example.audiobook.domain.usecases.ScanProgressBus
import com.example.audiobook.domain.usecases.ScanRoot
import com.example.audiobook.domain.usecases.StrictFolderClassifier
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** حالات آلة الإعداد (المرحلة 5): ترحيب → اختيار مجلد → معاينة → اعتماد/تعديل → استيراد → تم. */
sealed interface OnboardingState {
    data object Welcome : OnboardingState
    data object PickFolder : OnboardingState
    data class Previewing(val phase: ScanPhase, val processed: Int, val total: Int) : OnboardingState
    data class ShowPreview(val tree: PreviewTree) : OnboardingState
    data class Editing(val tree: PreviewTree, val edits: List<ClassificationEdit>) : OnboardingState
    data class Importing(val phase: ScanPhase, val processed: Int, val total: Int) : OnboardingState
    data object Done : OnboardingState
}

/**
 * آلة حالة الإعداد على المعاينة: لا يُكتب أي شيء في قاعدة البيانات إلا بعد اعتماد
 * المستخدم الصريح للتجانس المعروض (وتعديله إن شاء). الاستيراد الفعلي يستدعي
 * [ScanRoot] نفسه (المرحلة 4 بلا تغيير) بعد إنشاء شجرة القاعدة مطابقة للتعديلات
 * المعتمَدَة؛ ويُفرَّغ جدول التعديلات بعد نجاح الاستيراد.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    application: Application,
    private val database: AppDatabase,
    private val fileSource: LibraryFileSource,
    private val scanRoot: ScanRoot,
    private val appSettings: AppSettings
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Welcome)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _pickedUri = MutableStateFlow<Uri?>(null)
    val pickedUri: StateFlow<Uri?> = _pickedUri.asStateFlow()

    /** شجرة المعاينة الأصلية (قبل أي تعديل) — أساس كل تحويلات التعديل. */
    private var originalTree: PreviewTree? = null

    private val edits = mutableListOf<ClassificationEdit>()

    fun next() {
        when (_state.value) {
            OnboardingState.Welcome -> _state.value = OnboardingState.PickFolder
            OnboardingState.PickFolder -> startPreview()
            else -> Unit
        }
    }

    /** رجوع خطوة دون مسّ أي تعديل/إدخال. */
    fun back() {
        when (_state.value) {
            OnboardingState.PickFolder -> _state.value = OnboardingState.Welcome
            is OnboardingState.ShowPreview -> _state.value = OnboardingState.PickFolder
            is OnboardingState.Editing -> currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
            else -> Unit
        }
    }

    /** تخطي الإعداد صراحة: يُغلق الإعداد ويعرض المكتبة الفارغة (زر إضافة مجلد فيها). */
    fun skip() {
        appSettings.setHasCompletedOnboarding(true)
        appSettings.setHasSkippedOnboarding(true)
        _state.value = OnboardingState.Done
    }

    fun pickFolder(uri: Uri) {
        runCatching {
            StorageAccess.persistReadWritePermission(getApplication<Application>().contentResolver, uri)
        }
        _pickedUri.value = uri
    }

    fun startPreview() {
        val uri = _pickedUri.value ?: return
        _state.value = OnboardingState.Previewing(ScanPhase.CLASSIFYING, 0, 0)
        viewModelScope.launch {
            try {
                val rootName = displayNameOf(uri)
                val files = fileSource.listAudioFiles(uri)
                val input = files.map {
                    StrictFolderClassifier.InputFile(
                        uri = it.uri.toString(),
                        filename = it.fileName,
                        folderPath = it.folderPath,
                        sizeBytes = it.size,
                        durationMs = 0L
                    )
                }
                val tree = StrictFolderClassifier.preview(input, rootName)
                originalTree = tree
                edits.clear()
                _state.value = OnboardingState.ShowPreview(tree)
            } catch (error: Throwable) {
                // فشل القراءة (إذن سُحبت / URI تالف): نعود لاختيار المجلد.
                _state.value = OnboardingState.PickFolder
            }
        }
    }

    fun startEdit() {
        val tree = currentTree() ?: return
        _state.value = OnboardingState.Editing(tree, edits.toList())
    }

    /** تطبيق التعديل: يُحفظ في الجدول ويعاد تجميع شجرة المعاينة فورًا. */
    fun applyEdit(edit: ClassificationEdit) {
        val base = originalTree ?: return
        edits.removeAll { it.path == edit.path && it.type == edit.type }
        edits += edit
        persistEdits()
        val tree = applyEditsToTree(base, edits)
        _state.value = OnboardingState.Editing(tree, edits.toList())
    }

    /** إلغاء تعديلات الدورة: تُحذف من الذاكرة والجدول ويعاد عرض الشجرة الأصلية. */
    fun cancelEdit() {
        edits.clear()
        val uri = _pickedUri.value ?: return
        viewModelScope.launch { database.onboardingEditDao().deleteForRoot(uri.toString()) }
        originalTree?.let { _state.value = OnboardingState.ShowPreview(it) }
    }

    /** «حفظ التعديلات» (شاشة التعديل): نعود لشاشة الاعتماد بالشجرة النهائية. */
    fun saveAndPreview() {
        currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
    }

    fun confirmAndImport() {
        val tree = currentTree() ?: return
        val uri = _pickedUri.value ?: return
        _state.value = OnboardingState.Importing(ScanPhase.DISCOVERING, 0, 0)
        viewModelScope.launch {
            try {
                // 1) الجذر — يلزم أولًا لأن PendingDiscovery تحمل FK إليه.
                val root = LibraryRootEntity(
                    uri = uri.toString(),
                    displayName = displayNameOf(uri),
                    isPriority = true,
                    isEnabled = true,
                    lastScanAt = null,
                    scanStatus = ScanStatus.SCANNING
                )
                database.libraryRootDao().insert(root)

                // 2) شجرة القاعدة مطابقة للمعاينة المعتمَدة (تعديلات + تخطي).
                database.withTransaction { materializeTree(root, tree) }

                // 3) الفحص الفعلي (المرحلة 4 بلا تغيير) يملأ الملفات/المدد/الفصول
                //    ويربطها بالإصدارات الممهَّدة؛ المتخطاة تُتجاهَل (أُشير إليها IGNORED).
                val report = scanRoot(root.id, onProgress = { publishImport(it) })

                // 4) تفريغ التعديلات بعد الاستيراد الناجح ثم الخروج للمكتبة.
                database.onboardingEditDao().deleteForRoot(uri.toString())
                appSettings.setHasCompletedOnboarding(true)
                _state.value = OnboardingState.Done
                Log.i(TAG, "import-done root=${root.id} files=${report.filesSeen} books=${report.editionsCreated}")
            } catch (error: Throwable) {
                // لا نترك الإعداد عالقًا: العودة لشاشة الاعتماد مع إبقاء التعديلات للاستئناف.
                Log.w(TAG, "import-failed", error)
                currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
            }
        }
    }

    fun retry() = confirmAndImport()

    /** إيقاف تعاوني للاستيراد الجاري (ينهي الفحص بداية الدفعة القادمة). */
    fun cancel() {
        if (_state.value is OnboardingState.Importing) ScanProgressBus.requestCancel()
    }

    /** تخزين التعديلات المعتمدة في جدول onboarding_edits (تُفرَّغ عند النجاح). */
    private fun persistEdits() {
        val rootId = _pickedUri.value?.toString() ?: return
        val rows = edits.map { edit ->
            OnboardingEditEntity(
                rootId = rootId,
                path = edit.path,
                editType = edit.type,
                newValue = edit.newValueForStorage,
                createdAt = System.currentTimeMillis()
            )
        }
        viewModelScope.launch { rows.forEach { database.onboardingEditDao().insert(it) } }
    }

    private fun publishImport(progress: ScanProgress) {
        _state.value = OnboardingState.Importing(progress.phase, progress.processed, progress.total)
    }

    private fun currentTree(): PreviewTree? {
        val base = originalTree ?: return null
        return if (edits.isEmpty()) base else applyEditsToTree(base, edits)
    }

    private fun displayNameOf(uri: Uri): String =
        uri.lastPathSegment?.substringAfterLast(':') ?: uri.toString()

    /**
     * إنشاء شجرة قاعدة البيانات من شجرة المعاينة المعتمَدَة (داخل معاملة واحدة):
     * مؤلف ← سلسلة ← كتاب + إصدار لكل مسار، حتى يعثر عليها الفحص بمسار المجلد
     * ولا يعيد اشتقاق الأسماء وفق التصنيف التلقائي (تطبيق «التعديلات عند الفحص»).
     * العنوان المعاد تسميته يُعلَّم isTitleUserConfirmed حتى لا يكتب فوقه الفحص.
     * المتخطاة تُسجَّل اكتشافًا IGNORED فيتجاهلها ScanRoot (لا تُنشأ لها كتب).
     */
    private suspend fun materializeTree(root: LibraryRootEntity, tree: PreviewTree) {
        val authorCache = HashMap<String, AuthorEntity>()
        val seriesCache = HashMap<String, SeriesEntity>()
        val renamedFolders = edits
            .filterIsInstance<ClassificationEdit.RenameBook>()
            .map { it.path }
            .toSet()

        suspend fun authorFor(name: String?): AuthorEntity? {
            if (name.isNullOrBlank()) return null
            authorCache[name]?.let { return it }
            val author = database.authorDao().getByName(name)
                ?: AuthorEntity(name = name, colorTheme = null).also { database.authorDao().insert(it) }
            authorCache[name] = author
            return author
        }

        suspend fun seriesFor(author: AuthorEntity, name: String?): SeriesEntity? {
            if (name.isNullOrBlank()) return null
            val key = "${author.id}:$name"
            seriesCache[key]?.let { return it }
            val series = database.seriesDao().getByParent(author.id)
                .firstOrNull { it.name == name }
                ?: SeriesEntity(authorId = author.id, name = name, colorTheme = null)
                    .also { database.seriesDao().insert(it) }
            seriesCache[key] = series
            return series
        }

        suspend fun createBook(
            author: AuthorEntity?,
            series: SeriesEntity?,
            book: PreviewBook
        ) {
            val titleConfirmed = book.folderPath in renamedFolders
            val bookRow = BookEntity(
                title = book.title,
                authorId = author?.id,
                seriesId = series?.id,
                orderInSeries = null,
                genre = null,
                coverImagePath = null,
                coverSource = CoverSource.PLACEHOLDER,
                isCoverUserSelected = false,
                isTitleUserConfirmed = titleConfirmed,
                defaultEditionId = null,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            ).also { database.bookDao().insert(it) }
            database.editionDao().insert(
                EditionEntity(
                    bookId = bookRow.id,
                    narratorName = null,
                    label = book.folderPath.substringAfterLast('/').ifBlank { root.displayName },
                    totalDurationMs = book.totalDurationMs,
                    fileFormat = "UNKNOWN",
                    libraryRootId = root.id,
                    sourceFolderPath = book.folderPath,
                    confidenceScore = 1f,
                    isUserConfirmed = false,
                    remoteId = null,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            )
        }

        tree.authors.forEach { authorNode ->
            val author = authorFor(authorNode.name)
            authorNode.books.forEach { createBook(author, null, it) }
            authorNode.series.forEach { seriesNode ->
                val series = author?.let { seriesFor(it, seriesNode.name) }
                seriesNode.books.forEach { createBook(author, series, it) }
            }
        }
        tree.unassignedBooks.forEach { createBook(null, null, it) }

        // المجلدات المتخطاة: يتجاهلها الفحص (يمنع إنشاء كتب لها خلاف المعاينة).
        val skippedPaths = edits.filterIsInstance<ClassificationEdit.SkipFolder>().map { it.path }
        skippedPaths.forEach { folderPath ->
            database.pendingDiscoveryDao().insert(
                PendingDiscoveryEntity(
                    rootId = root.id,
                    folderPath = folderPath,
                    detectedTitle = "",
                    authorName = "",
                    seriesName = null,
                    discoveredAt = System.currentTimeMillis(),
                    status = DiscoveryStatus.IGNORED
                )
            )
        }
    }

    companion object {
        private const val TAG = "Onboarding"
    }
}