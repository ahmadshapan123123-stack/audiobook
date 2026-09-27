package com.example.audiobook.domain.usecases

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureNanoTime

/**
 * أداء المكتبة الكبيرة: مؤلف واحد وسلسلة واحدة فيها 200 ملف مباشر.
 *
 * النموذج الصارم يجعل كل ملف في سلسلة العمق-2 كتابًا مستقلًا، فالمهم هنا أن
 * 200 كتاب تُبنى ضمن ميزانية زمنية معقولة: مستخدم يملك مكتبة بآلاف الملفات
 * لا يشاهد شاشة متجمّدة.
 *
 * القياس يجري على محاكي Robolectric فيبطؤ التنفيذ عمليًا عدة مرات عن جهاز
 * حقيقي، فحدود 5s/10s هنا متساهلة لا معايير إنتاجية.
 */
@RunWith(RobolectricTestRunner::class)
class ScanLargeLibraryPerformanceTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var source: StaticFileSource
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

    private val fileCount = 200
    private val folder = "مؤلف كبير/سلسلة طويلة"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        appSettings = AppSettings(context)
        source = StaticFileSource()
        scanRoot = ScanRoot(database, source, ConstantMetadataReader(), appSettings, EditionMerge(database))
        root = LibraryRootEntity(
            uri = "content://library",
            displayName = "Library",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun twoHundredFilesInOneSeriesResolveIntoTwoHundredBooksWithinBudget() = runBlocking {
        val files = fakeFiles()
        source.files = files

        // ---- المرحلة 1: تحضير الملفات ----
        val report = ScanRoot.MutableScanReport(root.id)
        var prepared: Map<String, ScanRoot.PreparedFolder> = emptyMap()
        val prepareMs = measureNanoTime {
            prepared = scanRoot.prepareFiles(
                rootId = root.id,
                files = files,
                existing = emptyMap(),
                foundUris = mutableSetOf(),
                report = report
            )
        } / 1_000_000

        assertEquals("كل الملفات رُصدت", fileCount, report.filesSeen)
        assertTrue("prepareFiles استغرق ${prepareMs}ms — تجاوز 5000ms", prepareMs < 5_000)

        // ---- المرحلة 2: ربط الكتب بملفاتها ثم حفظها ----
        val classified = StrictFolderClassifier.classify(
            files.map {
                StrictFolderClassifier.InputFile(
                    uri = it.uri.toString(),
                    filename = it.fileName,
                    folderPath = it.folderPath,
                    sizeBytes = it.size,
                    durationMs = 0L
                )
            },
            rootName = "Library"
        )
        val units = scanRoot.bookUnits(classified, prepared, "Library")
        assertEquals("كتاب لكل ملف في السلسلة", fileCount, units.size)

        val resolveMs = measureNanoTime {
            scanRoot.resolveClassifiedBooks(
                root = root,
                units = units,
                report = report,
                resumeIndex = 0,
                onCreated = { _, _, _ -> }
            )
        } / 1_000_000

        assertTrue(
            "resolveClassifiedBooks استغرق ${resolveMs}ms — تجاوز 10000ms",
            resolveMs < 10_000
        )

        // ---- النتيجة: 200 كتاب + 200 إصدار، كلٌّ بملف واحد ----
        assertEquals("عدد الكتب", fileCount, database.bookDao().getAll().size)
        assertEquals("عدد الإصدارات", fileCount, database.editionDao().getByRoot(root.id).size)
        assertEquals("عدد ملفات الصوت", fileCount, database.audioFileDao().getByRoot(root.id).size)
        database.editionDao().getByRoot(root.id).forEach { edition ->
            assertEquals(
                "كل كتاب ملفٌ واحد لا أكثر",
                1,
                database.audioFileDao().getByParent(edition.id).size
            )
        }

        println(
            "PERF prepareFiles=${prepareMs}ms resolveClassifiedBooks=${resolveMs}ms " +
                "books=${database.bookDao().getAll().size} files=${report.filesSeen}"
        )
    }

    @Test
    fun rescanningTwoHundredBooksCreatesNoDuplicates() = runBlocking {
        source.files = fakeFiles()
        assertEquals("الفحص الأول يبني 200 كتاب", fileCount, scanRoot(root.id).editionsCreated)

        val second = scanRoot(root.id)

        assertEquals("لا كتاب جديد في إعادة الفحص", 0, second.editionsCreated)
        assertEquals("لا كتاب مكرر", fileCount, database.bookDao().getAll().size)
        assertEquals("لا إصدار مكرر", fileCount, database.editionDao().getByRoot(root.id).size)
        assertEquals("كل الملفات من ذاكرة التخزين المؤقتة", fileCount, second.cacheHits)
    }

    private fun fakeFiles(): List<ScanFile> = (1..fileCount).map { index ->
        val name = "%03d.mp3".format(index)
        ScanFile(
            uri = Uri.parse("content://audio/$name"),
            relativePath = "$folder/$name",
            folderPath = folder,
            fileName = name,
            size = 1_000_000L + index,
            lastModified = 10L
        )
    }

    private class StaticFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class ConstantMetadataReader : AudioMetadataReader {
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            AudioMetadata(1_800_000L, "audio/mp3", null, null, null, emptyList())
    }
}
