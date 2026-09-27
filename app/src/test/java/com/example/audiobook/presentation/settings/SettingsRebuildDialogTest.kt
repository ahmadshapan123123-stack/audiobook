package com.example.audiobook.presentation.settings

import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.background.reclassify.ReclassifyScheduler
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.background.scan.ScanJob
import com.example.audiobook.background.scan.ScanOutcome
import com.example.audiobook.background.scan.ScanServiceLauncher
import com.example.audiobook.background.scan.ScanServiceNotifier
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.repository.LocalOnlyLibraryRootRepository
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.EditionMerge
import com.example.audiobook.domain.usecases.NoopMetadataReader
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import com.example.audiobook.domain.usecases.RebuildLibraryStructure
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.ScanRoot
import com.example.audiobook.domain.usecases.classificationPreviewFor
import com.example.audiobook.domain.usecases.libraryManagementFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
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
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * عقد حوار «إعادة بناء المكتبة» (PHASE 8):
 *  - قبل التأكيد: عدد الكتب الحالي منشور في [SettingsViewModel.rebuildBookCount] بلا أي كتابة.
 *  - أثناء التنفيذ: يبقى العدد منشورًا مع `isRebuilding = true`، فيظل الحوار ظاهرًا
 *    ليعرض مؤشّر التقدّم (وحاجزه يحجب بقية أزرار الإعدادات).
 *  - بعد الانتهاء: يُغلق الحوار (العدد = null) وتُنشر النتيجة مرة واحدة.
 *  - الإلغاء مسموح قبل التنفيذ فقط؛ أثناء التنفيذ يُتجاهل.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsRebuildDialogTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings

    /** يُنخفض عند دخول الفحص [listAudioFiles] → إثبات أن التنفيذ بدأ فعلًا. */
    private val scanEntered = CountDownLatch(1)

    /** ينتظره الفحص حتى يسمح له الاختبار بالإكمال. */
    private val scanRelease = CountDownLatch(1)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appSettings = AppSettings(context)
    }

    @After
    fun tearDown() {
        scanRelease.countDown()
        database.close()
    }

    /** مصدر ملفات يحبس الفحص عند [scanEntered] حتى يُطلقه [scanRelease]. */
    private class GatedFileSource : LibraryFileSource {
        private val entered: CountDownLatch
        private val release: CountDownLatch

        constructor() : this(CountDownLatch(1), CountDownLatch(1))

        constructor(entered: CountDownLatch, release: CountDownLatch) {
            this.entered = entered
            this.release = release
        }

        override fun listAudioFiles(rootUri: Uri): List<ScanFile> {
            entered.countDown()
            // مهلة حماية: لا يعلّق الاختبار إن لم يُستدعَ الفحص أصلًا.
            release.await(10, TimeUnit.SECONDS)
            return emptyList()
        }
    }

    /** مصدر بلا حجب: يكمل الفحص فورًا (لاختبار ما قبل التنفيذ فقط). */
    private class ImmediateFileSource : LibraryFileSource {
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = emptyList()
    }

    private fun scanNowWith(source: LibraryFileSource): ScanLibraryNow = ScanLibraryNow(
        LocalOnlyLibraryRootRepository(database.libraryRootDao()),
        ScanRoot(database, source, NoopMetadataReader(), appSettings, EditionMerge(database)),
        database
    )

    private fun viewModel(rebuild: RebuildLibraryStructure): SettingsViewModel = SettingsViewModel(
        appSettings,
        ReminderScheduler(context, appSettings),
        libraryManagementFor(database),
        database.libraryRootDao(),
        database.bookDao(),
        // PART 1: `SettingsViewModel` لا يملك مسار فحص مباشر بعد الآن — الطلب
        // يمرّ عبر [ScanServiceLauncher]. هذا الخادم المزيف يحاكي ما تفعله
        // الخدمة الأمامية (تنظّف القشور ثم تفحص) ليبقى الحوار قابلًا للاختبار
        // دون تشغيل Service فعلي داخل Robolectric.
        ScanServiceLauncher { request ->
            lastLaunchedJob = request.job
            if (request.job == ScanJob.REBUILD) {
                // تحاكي الخدمة الأمامية: تنفيذ العمل ثم نشر الحصيلة.
                val result = rebuild()
                ScanServiceNotifier.notify(
                    ScanOutcome.Completed(
                        outcome = result.scan,
                        rebuildResult = result
                    )
                )
            }
            true
        },
        ReclassifyLibrary(database, appSettings),
        classificationPreviewFor(database, appSettings),
        ReclassifyScheduler(context),
        context
    )

    /** آخر job طُلب عبر الخدمة — يثبت أن الطلب مرّ من المسار الأمامي. */
    private var lastLaunchedJob: ScanJob? = null

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    /** ينتظر تحقّق شرط عبر تصفير الحاجز الرئيسي بين دورات (Room يعمل على خيوط خلفية). */
    private fun awaitCondition(condition: () -> Boolean): Boolean {
        var tries = 0
        while (!condition() && tries++ < 1000) {
            idleMainLooper()
            Thread.sleep(5)
        }
        idleMainLooper()
        return condition()
    }

    private fun seedRootAndBooks(bookCount: Int): Int {
        val root = LibraryRootEntity(
            uri = "content://root/audio",
            displayName = "Books",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                repeat(bookCount) { index ->
                    database.bookDao().insert(
                        BookEntity(
                            id = UUID.randomUUID(),
                            title = "كتاب ${index + 1}",
                            authorId = author.id,
                            seriesId = null,
                            orderInSeries = null,
                            genre = "رواية",
                            coverImagePath = null,
                            coverSource = CoverSource.PLACEHOLDER,
                            isCoverUserSelected = false,
                            defaultEditionId = null,
                            remoteId = null,
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    )
                }
            }
        }
        return bookCount
    }

    @Test
    fun requestRebuildPublishesCurrentBookCountWithoutWriting() {
        val expected = seedRootAndBooks(3)
        val viewModel = viewModel(RebuildLibraryStructure(database, scanNowWith(ImmediateFileSource())))

        viewModel.requestRebuild()

        assertTrue("book count should be published", awaitCondition { viewModel.rebuildBookCount.value != null })
        assertEquals(expected, viewModel.rebuildBookCount.value)
        assertEquals(false, viewModel.isRebuilding.value)
        assertNull("no rebuild should have run", viewModel.rebuildResult.value)
    }

    @Test
    fun cancelBeforeStartingClosesTheDialog() {
        seedRootAndBooks(2)
        val viewModel = viewModel(RebuildLibraryStructure(database, scanNowWith(ImmediateFileSource())))

        viewModel.requestRebuild()
        assertTrue(awaitCondition { viewModel.rebuildBookCount.value != null })

        viewModel.cancelRebuild()

        assertNull(viewModel.rebuildBookCount.value)
    }

    @Test
    fun countStaysVisibleWhileRebuildRunsThenDialogClosesOnCompletion() {
        val expected = seedRootAndBooks(4)
        val viewModel = viewModel(
            RebuildLibraryStructure(database, scanNowWith(GatedFileSource(scanEntered, scanRelease)))
        )

        viewModel.requestRebuild()
        assertTrue(awaitCondition { viewModel.rebuildBookCount.value != null })
        assertEquals(expected, viewModel.rebuildBookCount.value)

        viewModel.rebuildStructure()

        assertTrue("rebuild should be running", awaitCondition { viewModel.isRebuilding.value })
        // لا يمكن الحجب بـlatch هنا: خيط الاختبار هو خيط الحاجز الرئيسي في Robolectric،
        // وحجبُه يجمّد استئناف الـcoroutine فلا يصل الفحص إلى البوابة أبدًا.
        assertTrue("scan should be in flight", awaitCondition { scanEntered.count == 0L })
        idleMainLooper()
        assertEquals(
            "dialog must stay open while rebuilding",
            expected,
            viewModel.rebuildBookCount.value
        )

        // الإلغاء أثناء التنفيذ لا يُغلق الحوار، وإلا اختفى مؤشّر التقدّم.
        viewModel.cancelRebuild()
        assertEquals(expected, viewModel.rebuildBookCount.value)

        scanRelease.countDown()
        assertTrue(awaitCondition { !viewModel.isRebuilding.value })

        assertNull("dialog should close once the rebuild is done", viewModel.rebuildBookCount.value)
        assertNotNull("result should be published", viewModel.rebuildResult.value)
    }
}
