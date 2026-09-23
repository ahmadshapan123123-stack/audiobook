package com.example.audiobook.domain.usecases

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.audiobook.background.reclassify.ReclassifyResultNotifier
import com.example.audiobook.background.reclassify.ReclassifyScheduler
import com.example.audiobook.background.reclassify.ReclassifyWorkRunner
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.repository.LocalOnlyLibraryRootRepository
import com.example.audiobook.presentation.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import kotlinx.coroutines.runBlocking

/**
 * Bug 5 — تحكُّم المستخدم في اكتشاف السلاسل + إعادة التصنيف:
 *  - E.1: التصنيف المحافظ مفعّل → «فانتازيا» تبقى سلسلة في الفحص.
 *  - E.2: الإيقاف من الإعدادات → في فحص جديد «فانتازيا» كتاب بلا سلسلة.
 *  - E.3: معاينة → شجرة تُعرض → تأكيد → Worker خلفي يطبّق البنية.
 *  - E.4: معاينة ثم إلغاء → لا تتغير قاعدة البيانات.
 *  - E.5: ما أسنده المستخدم (سلسلة بمؤلف وعنوان مؤكَّد) يُحفظ عبر إعادة التصنيف.
 *  - E.6: التقدّم والعلامات والفصول والملفات تُحفظ عبر إعادة التصنيف.
 * WorkManager يُهيَّأ بمُنفِّذ متزامن → تشغيل Worker حتمي، و ReclassifyWorkRunner
 * يمرّر ReclassifyLibrary حقيقيًا (بلا Hilt) لتطبيق البنية فعليًا في قاعدة البيانات.
 *
 * ملاحظة قراءة سريعة: كل الكتابات في قاعدة البيانات تتم عبر (runBlocking + استعلام
 * روم) أي على مؤشر ترابط الاختبار نفسه، بينما معيَّنات الـViewModel تتماوج على
 * الرئيسي؛ لذلك نقرأ reclassifyPreview/reclassifyTree/reclassifyApplied بعد أن تنتهي
 * حالة «جارٍ إعادة التصنيف» (isReclassifying = false) لضمان الثبات.
 */
@RunWith(RobolectricTestRunner::class)
class SeriesAutoSeriesClassificationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var source: FakeFileSource
    private lateinit var reader: CountingMetadataReader
    private lateinit var appSettings: AppSettings
    private lateinit var viewModel: SettingsViewModel
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity
    private lateinit var reclassifyLibrary: ReclassifyLibrary

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .setExecutor(SynchronousExecutor())
                .build()
        )
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        source = FakeFileSource()
        reader = CountingMetadataReader()
        appSettings = AppSettings(context)
        appSettings.setAutoSeriesClassification(true)
        reclassifyLibrary = ReclassifyLibrary(database, appSettings)
        ReclassifyWorkRunner.testDelegate = reclassifyLibrary
        scanRoot = ScanRoot(database, source, reader, appSettings, EditionMerge(database))
        val preview = LibraryClassificationPreview(
            source,
            LocalOnlyLibraryRootRepository(database.libraryRootDao()),
            appSettings
        )
        viewModel = SettingsViewModel(
            appSettings,
            ReminderScheduler(context, appSettings),
            libraryManagementFor(database),
            database.libraryRootDao(),
            scanLibraryNowFor(database, appSettings),
            reclassifyLibrary,
            preview,
            ReclassifyScheduler(context)
        )
        root = LibraryRootEntity(
            uri = "content://library", displayName = "Library", isPriority = true,
            isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE
        )
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() {
        ReclassifyWorkRunner.testDelegate = null
        ReclassifyResultNotifier.reset()
        appSettings.setAutoSeriesClassification(true)
        database.close()
    }

    private fun seedBiblioteca() {
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.mp3"), "أحمد خالد توفيق/فانتازيا/01.mp3", "أحمد خالد توفيق/فانتازيا", "01.mp3", 10, 5),
            ScanFile(Uri.parse("content://audio/2.mp3"), "أحمد خالد توفيق/ما وراء الطبيعة/01.mp3", "أحمد خالد توفيق/ما وراء الطبيعة", "01.mp3", 10, 6)
        )
        reader.overrides["default"] = AudioMetadata(10_000L, "audio/mpeg", null, null, null, emptyList())
    }

    private fun scanOnce() = runBlocking { scanRoot(root.id) }

    private suspend fun bookAt(folderPath: String): BookEntity? {
        val edition = database.editionDao().getByRootAndFolder(root.id, folderPath) ?: return null
        return database.bookDao().getById(edition.bookId)
    }

    private suspend fun seriesNames(): List<String> {
        val author = database.authorDao().getByName("أحمد خالد توفيق") ?: return emptyList()
        return database.seriesDao().getByParent(author.id).map { it.name }.sorted()
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /** يُنفَّذ أي عمل رئيسي معلّق حتى تنتهي حالة «جارٍ إعادة التصنيف». */
    private fun awaitNotReclassifying() {
        var tries = 0
        while (viewModel.isReclassifying.value && tries++ < 500) {
            idleMainLooper()
            Thread.sleep(10)
        }
        idleMainLooper()
    }

    // ── E.1: المحافظ مفعّل → «فانتازيا» سلسلة ──

    @Test
    fun autoSeriesOnKeepsFantasiaAsSeries() {
        seedBiblioteca()
        scanOnce()

        val series = runBlocking { seriesNames() }
        assertEquals(listOf("فانتازيا", "ما وراء الطبيعة"), series)
        val fantasia = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        assertNotNull("فانتازيا مربوطة بسلسلتها في قاعدة البيانات", fantasia.seriesId)
    }

    // ── E.2: الإيقاف → «فانتازيا» كتاب بلا سلسلة في فحص جديد ──

    @Test
    fun autoSeriesOffMakesFantasiaABookWithNoSeries() {
        appSettings.setAutoSeriesClassification(false)
        seedBiblioteca()
        scanOnce()

        assertEquals("لا سلسلة تُنشأ في الفحص المحافظ", emptyList<String>(), runBlocking { seriesNames() })
        val fantasia = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        assertNull("فانتازيا كتاب بلا سلسلة عند الإيقاف", fantasia.seriesId)
    }

    // ── E.3: معاينة → تأكيد → Worker خلفي يطبّق البنية ──

    @Test
    fun reclassifyShowsPreviewThenConfirmAppliesBackgroundWorker() {
        seedBiblioteca()
        scanOnce()
        val fantasiaBefore = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        assertNotNull(fantasiaBefore.seriesId)

        appSettings.setAutoSeriesClassification(false)

        viewModel.requestReclassify()
        awaitNotReclassifying()
        val preview = viewModel.reclassifyPreview.value
        assertNotNull("معاينة إعادة التصنيف تظهر قبل التأكيد", preview)
        assertTrue("يوجد مجلدات لتصحيحها تحت التصنيف المحافظ", preview!!.foldersToFix >= 1)
        val treeText = viewModel.reclassifyTree.value
            ?.flatMap { it.lines }
            ?.map { it.text }
            ?.joinToString("\n").orEmpty()
        assertTrue("شجرة البنية القادمة تظهر (تتضمن فانتازيا)", treeText.contains("فانتازيا"))

        viewModel.confirmReclassify()
        awaitNotReclassifying()

        val applied = viewModel.reclassifyApplied.value
        assertNotNull("نتيجة Worker الخلفي تصل للواجهة", applied)
        assertTrue("تمت إعادة تصنيف كتاب واحد على الأقل", applied!!.affectedBooks >= 1)

        val fantasiaAfter = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        assertNull("بعد التأكيد: فانتازيا كتاب بلا سلسلة", fantasiaAfter.seriesId)
        assertEquals("المؤلف يبقى محفوظًا", fantasiaBefore.authorId, fantasiaAfter.authorId)
    }

    // ── E.4: معاينة ثم إلغاء → لا تتغير قاعدة البيانات ──

    @Test
    fun reclassifyCancelLeavesDatabaseUntouched() {
        seedBiblioteca()
        scanOnce()
        val before = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }.seriesId

        viewModel.requestReclassify()
        awaitNotReclassifying()
        assertNotNull(viewModel.reclassifyPreview.value)
        assertNotNull(viewModel.reclassifyTree.value)

        viewModel.cancelReclassify()
        awaitNotReclassifying()
        assertNull(viewModel.reclassifyPreview.value)
        assertNull(viewModel.reclassifyTree.value)
        val after = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }.seriesId
        assertEquals("إلغاء المعاينة لا يكتب شيئًا", before, after)
    }

    // ── E.5: ما أسنده المستخدم (سلسلة/مؤلف) يُحفظ عبر إعادة التصنيف ──

    @Test
    fun userConfirmedSeriesIsPreservedAcrossReclassify() {
        seedBiblioteca()
        scanOnce()
        val fantasia = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        val userSeries = runBlocking {
            val author = database.authorDao().getByName("أحمد خالد توفيق")!!
            SeriesEntity(authorId = author.id, name = "مفضلاتي", colorTheme = null)
                .also { database.seriesDao().insert(it) }
        }
        // إسناد المستخدم عبر تفاصيل الكتاب: ربط سلسلة يدوية + تأكيد العنوان.
        runBlocking {
            database.bookDao().update(
                fantasia.copy(seriesId = userSeries.id, isTitleUserConfirmed = true)
            )
        }
        appSettings.setAutoSeriesClassification(false)

        viewModel.requestReclassify()
        awaitNotReclassifying()
        viewModel.confirmReclassify()
        awaitNotReclassifying()

        val fantasiaAfter = runBlocking { bookAt("أحمد خالد توفيق/فانتازيا")!! }
        assertEquals("سلسلة المستخدم اليدوية لا تُمس", userSeries.id, fantasiaAfter.seriesId)
        assertEquals("مؤلف المستخدم لا يُعاد اشتقاقه", fantasia.authorId, fantasiaAfter.authorId)
    }

    // ── E.6: التقدّم والعلامات والفصول والملفات تُحفظ عبر إعادة التصنيف ──

    @Test
    fun reclassifyPreservesProgressBookmarksChaptersAndFiles() {
        seedBiblioteca()
        scanOnce()
        val edition = runBlocking {
            database.editionDao().getByRootAndFolder(root.id, "أحمد خالد توفيق/فانتازيا")!!
        }
        runBlocking {
            database.progressDao().insert(
                ListeningProgressEntity(
                    editionId = edition.id, currentPositionMs = 42_000L, lastPlayedAt = 1234L,
                    status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1.25f,
                    remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
                )
            )
            database.bookmarkDao().insert(
                BookmarkEntity(
                    editionId = edition.id, positionMs = 1_000L, createdAt = 5L,
                    type = BookmarkType.BOOKMARK, noteText = null, remoteId = null,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            )
            database.chapterDao().insert(
                ChapterEntity(
                    editionId = edition.id, title = "الفصل الأول", startPositionMs = 0L,
                    orderIndex = 0, createdFrom = ChapterCreatedFrom.IMPORTED
                )
            )
        }
        val audioCountBefore = runBlocking { database.audioFileDao().countAll() }
        val progressBefore = runBlocking { database.progressDao().getByParent(edition.id)!! }

        appSettings.setAutoSeriesClassification(false)
        viewModel.requestReclassify()
        awaitNotReclassifying()
        viewModel.confirmReclassify()
        awaitNotReclassifying()

        val afterProgress = runBlocking { database.progressDao().getByParent(edition.id)!! }
        val bookmarks = runBlocking { database.bookmarkDao().getByParent(edition.id) }
        val chapters = runBlocking { database.chapterDao().getByParent(edition.id) }

        assertEquals("التقدّم محفوظ بنفس القيمة", progressBefore.currentPositionMs, afterProgress.currentPositionMs)
        assertEquals("سرعة التشغيل محفوظة", progressBefore.playbackSpeed, afterProgress.playbackSpeed)
        assertEquals("العلامات محفوظة", 1, bookmarks.size)
        assertEquals("الفصول محفوظة", 1, chapters.size)
        assertEquals("الملفات الصوتية لم تتغير", audioCountBefore, runBlocking { database.audioFileDao().countAll() })
        assertEquals("الإصدارات لم تتغير", 2, runBlocking { database.editionDao().getByRoot(root.id).size })
    }

    private class FakeFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class CountingMetadataReader : AudioMetadataReader {
        var overrides = mutableMapOf<String, AudioMetadata>()
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            overrides["default"] ?: AudioMetadata(1000, "audio/mp4", "Book", null, null, emptyList())
    }
}
