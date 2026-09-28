package com.example.audiobook.presentation.onboarding

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.audiobook.background.scan.ScanJob
import com.example.audiobook.background.scan.ScanOutcome
import com.example.audiobook.background.scan.ScanRequest
import com.example.audiobook.background.scan.ScanServiceLauncher
import com.example.audiobook.background.scan.ScanServiceNotifier
import com.example.audiobook.domain.usecases.OnboardingImportMaterializer
import com.example.audiobook.domain.usecases.PendingOnboardingImport
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
import com.example.audiobook.domain.usecases.PreviewResultBus
import com.example.audiobook.domain.usecases.ScanRoot
import com.example.audiobook.domain.usecases.StrictFolderClassifier
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** حالات آلة الإعداد (المرحلة 5): ترحيب → اختيار مجلد → معاينة → اعتماد/تعديل → استيراد → تم. */
sealed interface OnboardingState {
    data object Welcome : OnboardingState
    data object PickFolder : OnboardingState
    data class Previewing(
        val phase: ScanPhase,
        val processed: Int,
        val total: Int,
        /** FIX 4: الموضع الجاري من الناقل — لا (0/0) مجمّدة. */
        val folder: String = "",
        val file: String = ""
    ) : OnboardingState
    data class ShowPreview(val tree: PreviewTree) : OnboardingState
    data class Editing(val tree: PreviewTree, val edits: List<ClassificationEdit>) : OnboardingState
    /**
     * STAGE 2 — شاشة الاستيراد تعرض المجلد والملف الجاريين (تُملآن من
     * الناقل المشترك عبر [publishImport])، لا العدّادات وحدها.
     */
    data class Importing(
        val phase: ScanPhase,
        val processed: Int,
        val total: Int,
        val folder: String = "",
        val file: String = ""
    ) : OnboardingState
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
    private val appSettings: AppSettings,
    private val scanServiceLauncher: ScanServiceLauncher,
    private val pendingOnboardingImport: PendingOnboardingImport
) : AndroidViewModel(application) {

    // GAP 2: لم يعد هذا الـViewModel يملك ScanRoot ولا يفتح الخدمة بنفسه.
    // كان يدخل ScanRoot ليُسقط مسارًا احتياطيًا يستورد داخل النطاق، وهو
    // تحديدًا ما أُزيل: الاستيراد كله صار في ScanForegroundService عبر
    // scanServiceLauncher، فلا حاجة لـContext هنا ولا لمسار فحص مباشر.

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Welcome)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _pickedUri = MutableStateFlow<Uri?>(null)
    val pickedUri: StateFlow<Uri?> = _pickedUri.asStateFlow()

    /** معاينة جارية: يمنع ضغطتين متتاليتين على «التالي» (فحص SAF مزدوج على المكتبة كلها). */
    private val _previewInFlight = MutableStateFlow(false)
    val previewInFlight: StateFlow<Boolean> = _previewInFlight.asStateFlow()

    /** شجرة المعاينة الأصلية (قبل أي تعديل) — أساس كل تحويلات التعديل. */
    private var originalTree: PreviewTree? = null

    private val edits = mutableListOf<ClassificationEdit>()

    /**
     * FIX 9 — قناة رسائل الخطأ للشاشة (Snackbar): المعاينة لم تعد تبتلع
     * الفشل صامتةً — كل فشل يصل هنا بنص يُعرض.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun emitMessage(text: String) {
        _messages.tryEmit(text)
    }

    private fun backToPickFolder() {
        _state.value = OnboardingState.PickFolder
    }

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
        // FIX 7: الشجرة المحتفظ بها للتعديل لم يعد لها استخدام بعد مغادرة
        // الاستهلال — تُحرَّر هنا (وفي مسح الـVM تلقائيًا).
        originalTree = null
        edits.clear()
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
        if (_previewInFlight.value) return
        _previewInFlight.value = true
        _state.value = OnboardingState.Previewing(ScanPhase.DISCOVERING, 0, 0)
        // FIX 4: التقدّم حيّ من الناقل المشترك — نفس نمط confirmAndImport.
        // الشاشة تعرض المجلد/الملف الجاريين لا (0/0) مجمّدة.
        val progressJob = viewModelScope.launch {
            ScanProgressBus.state.collect { progress ->
                if (_state.value is OnboardingState.Previewing && progress != null) {
                    _state.value = OnboardingState.Previewing(
                        progress.phase, progress.processed, progress.total,
                        folder = progress.currentFolder, file = progress.currentFile
                    )
                }
            }
        }
        viewModelScope.launch {
            try {
                // FIX 4: المعاينة في الخدمة الأمامية (حماية foreground +
                // تقدّم حيّ + إلغاء تعاوني) — لا `withContext` محلي.
                // FIX 9: بلا التقاط صامت؛ الفشل/الإلغاء يصلان عبر
                // PreviewResultBus برسالة تُعرض للمستخدم.
                PreviewResultBus.clear()
                val started = scanServiceLauncher.launch(
                    ScanRequest(ScanJob.PREVIEW, rootUri = uri.toString())
                )
                if (!started) {
                    emitMessage("تعذّر بدء المعاينة — أعد المحاولة")
                    backToPickFolder()
                    return@launch
                }
                // `first` بشرط غير-null لا يعيد null أبدًا — `!!` ليرى
                // المترجم exhaustiveness الـsealed interface.
                when (val result = PreviewResultBus.result.first { it != null }!!) {
                    is PreviewResultBus.PreviewResult.Ready -> {
                        originalTree = result.tree
                        edits.clear()
                        _state.value = OnboardingState.ShowPreview(result.tree)
                    }
                    is PreviewResultBus.PreviewResult.Failed -> {
                        emitMessage("فشلت المعاينة: ${result.reason}")
                        backToPickFolder()
                    }
                    PreviewResultBus.PreviewResult.Cancelled -> backToPickFolder()
                }
            } finally {
                // CancellationException الخاص بنا (مسح الـVM) يمرّ من هنا
                // ويُعاد رميه — لا يُبتلَع (FIX 9).
                progressJob.cancel()
                _previewInFlight.value = false
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
        // PART 1: بمجرد تفويض الفحص للخدمة، نراقب تقدّمها على الناقل المشترك
        // حتى تُبقي شاشة Importing حيّة. الإلغاء يمرّ عبر نفس الناقل فيصل
        // الخدمة عبر requestCancel.
        val progressJob = viewModelScope.launch {
            ScanProgressBus.state.collect { progress ->
                if (_state.value is OnboardingState.Importing && progress != null) {
                    publishImport(progress)
                }
            }
        }
        viewModelScope.launch {
            try {
                // 1) الجذر — يلزم أولًا لأن PendingDiscovery تحمل FK إليه.
                val root = LibraryRootEntity(
                    uri = uri.toString(),
                    displayName = StorageAccess.displayNameOf(uri),
                    isPriority = true,
                    isEnabled = true,
                    lastScanAt = null,
                    scanStatus = ScanStatus.SCANNING
                )
                database.libraryRootDao().insert(root)

                // 2) GAP 2: لم نعد نموّه الشجرة هنا. الشجرة تُسطَّح إلى قائمة
                //    مواصفات وتُحفظ حالةً معلَّقة، ثم تتولّى الخدمة الأمامية
                //    الكتابة والفحص. السبب: `materializeTree` كانتDirectories
                //    آلاف INSERT في معاملات صغيرة بلا أي تغطية foreground،
                //    فخروج المستخدم أو ضغط النظام على العملية يقتلها في
                //    منتصف شجرة 10,000 كتاب.
                val bookSpecs = flattenTree(tree)
                val renamedPaths = edits
                    .filterIsInstance<ClassificationEdit.RenameBook>()
                    .map { it.path }
                    .toSet()
                val skippedPaths = edits.filterIsInstance<ClassificationEdit.SkipFolder>().map { it.path }
                pendingOnboardingImport.enqueue(
                    PendingOnboardingImport.Payload(
                        rootId = root.id,
                        books = bookSpecs,
                        renamedPaths = renamedPaths,
                        skippedPaths = skippedPaths
                    )
                )

                // 3) الفحص الفعلي يملأ الملفات/المدد/الفصول ويربطها بالإصدارات
                //    الممهَّدة؛ المتخطاة تُتجاهَل (أُشير إليها IGNORED).
                //    الخدمة هي التي تطبّق التوجيه ثم تفحص — كلاهما في نطاق
                //    أمامي واحد. التقدّم يصل عبر ScanProgressBus (المشترك)
                //    والنتيجة عبر ScanServiceNotifier — observes أدناه تنقلهما
                //    لشاشة Importing.
                // الناقل يُصفَّر **قبل** الإطلاق: الخدمة قد تنشر حصيلتها وتعود
                // قبل أن يُرجع `launch` قيمة `true`، فتصفييرٌ بعده يمحو حصيلة
                // هذا الاستيراد وينتظر الشاشة التالية إلى الأبد.
                ScanServiceNotifier.reset()
                val started = scanServiceLauncher.launch(
                    ScanRequest(ScanJob.ONBOARDING_IMPORT, root.id.toString())
                )
                if (started) {
                    // الخدمة تملك التوجيه والفحص من الآن. نجلس على الناقل المشترك حتى
                    // تُنهي الخدمة العمل، ثم نُكمل خطوات ما بعد الاستيراد
                    // (تفريغ التعديلات + الانتقال للمكتبة) في هذا النطاق نفسه.
                    val outcome = ScanServiceNotifier.result.first { it != null }
                        ?: ScanOutcome.Failed("no-result")
                    ScanServiceNotifier.reset()
                    when (outcome) {
                        is ScanOutcome.Completed -> completeImport(
                            root = root,
                            filesSeen = outcome.outcome.filesSeen,
                            booksFound = outcome.outcome.booksFound
                        )
                        ScanOutcome.Cancelled -> {
                            Log.i(TAG, "import-cancelled root=${root.id}")
                            currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
                        }
                        is ScanOutcome.Failed -> throw IllegalStateException(outcome.reason)
                        // رفض بالحارس: shouldn’t happen (we are the only scan)، لكن
                        // نُعيد المستخدم لشاشة الاعتماد بدل تعليق.
                        ScanOutcome.Rejected -> {
                            Log.w(TAG, "import-rejected reason=scan-busy")
                            currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
                        }
                    }
                } else {
                    // GAP 2: لا مسار احتياطي. استيراد 10,000 كتاب داخل
                    // ViewModel بلا تغطية أمامية هو تحديدًا ما نحاول إلغاؤه.
                    pendingOnboardingImport.clear()
                    Log.w(TAG, "scan-service-unavailable; import aborted before any insert")
                    _state.value = OnboardingState.ShowPreview(currentTree() ?: return@launch)
                }
            } catch (error: Throwable) {
                // لا نترك الإعداد عالقًا: العودة لشاشة الاعتماد مع إبقاء التعديلات للاستئناف.
                Log.w(TAG, "import-failed", error)
                currentTree()?.let { _state.value = OnboardingState.ShowPreview(it) }
            } finally {
                progressJob.cancel()
            }
        }
    }

    /** ما بعد فحص ناجح: تفريغ التعديلات والانتقال للمكتبة. */
    private suspend fun completeImport(root: LibraryRootEntity, filesSeen: Int, booksFound: Int) {
        database.onboardingEditDao().deleteForRoot(root.uri)
        appSettings.setHasCompletedOnboarding(true)
        _state.value = OnboardingState.Done
        Log.i(TAG, "import-done root=${root.id} files=$filesSeen books=$booksFound")
    }

    fun retry() = confirmAndImport()

    /**
     * إيقاف تعاوني للاستيراد/المعاينة الجارية.
     * FIX 6: المعاينة قابلة للإلغاء بزرها — يصل الخدمة عبر الناقل فيتوقف
     * الجوس مبكرًا وتعود الشاشة لاختيار المجلد.
     */
    fun cancel() {
        val state = _state.value
        if (state is OnboardingState.Importing || state is OnboardingState.Previewing) {
            ScanProgressBus.requestCancel()
        }
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
        _state.value = OnboardingState.Importing(
            progress.phase, progress.processed, progress.total,
            folder = progress.currentFolder, file = progress.currentFile
        )
    }

    private fun currentTree(): PreviewTree? {
        val base = originalTree ?: return null
        return if (edits.isEmpty()) base else applyEditsToTree(base, edits)
    }

    /**
     * GAP 2: تسطيح الشجرة المعتمدة إلى قائمة مواصفات تعبر إلى الخدمة.
     *
     * الترتيب محفوظ كما كان في `materializeTree` القديم: كتب المؤلف أولًا،
     * ثم كتب كل سلسلة بالترتيب، ثم الكتب غير المنسوبة.
     */
    private fun flattenTree(tree: PreviewTree): List<OnboardingImportMaterializer.BookSpec> {
        val specs = ArrayList<OnboardingImportMaterializer.BookSpec>()
        tree.authors.forEach { authorNode ->
            val authorName = authorNode.name
            authorNode.books.forEach { book -> specs += bookSpec(authorName, null, book) }
            authorNode.series.forEach { seriesNode ->
                seriesNode.books.forEach { book -> specs += bookSpec(authorName, seriesNode.name, book) }
            }
        }
        tree.unassignedBooks.forEach { book -> specs += bookSpec(null, null, book) }
        return specs
    }

    private fun bookSpec(
        authorName: String?,
        seriesName: String?,
        book: PreviewBook
    ) = OnboardingImportMaterializer.BookSpec(
        authorName = authorName,
        seriesName = seriesName,
        title = book.title,
        folderPath = book.folderPath,
        totalDurationMs = book.totalDurationMs
    )

    /**
     * إنشاء شجرة قاعدة البيانات من شجرة المعاينة المعتمَدَة (داخل معاملة واحدة):
     * مؤلف ← سلسلة ← كتاب + إصدار لكل مسار، حتى يعثر عليها الفحص بمسار المجلد
     * ولا يعيد اشتقاق الأسماء وفق التصنيف التلقائي (تطبيق «التعديلات عند الفحص»).
     * العنوان المعاد تسميته يُعلَّم isTitleUserConfirmed حتى لا يكتب فوقه الفحص.
     * المتخطاة تُسجَّل اكتشافًا IGNORED فيتجاهلها ScanRoot (لا تُنشأ لها كتب).
     */
    companion object {
        private const val TAG = "Onboarding"

        /** PART 9: حجم دفعة الاستيراد — WAL قصير وعدد معاملات معقول. */
        private const val MATERIALIZE_BATCH_SIZE = 50
    }
}