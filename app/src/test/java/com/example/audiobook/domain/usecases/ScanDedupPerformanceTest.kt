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
 * PART 2 guard: فحص التكرار كان مسحًا خطيًا (`bucket.any`) لكل ملف داخل
 * مجلده، أي O(N²). على مجلد واحد فيه 10,000 ملف كان ذلك ~50 مليون مقارنة
 * (قابل للقياس) و50,000 ملف ≈ 1.25 مليار مقارنة.
 *
 * الحدّ الآن 500ms لكل 50,000 ملف — متساهل جدًا مقارنة بسلوك O(N²) الذي
 * يستغرق عشرات الثواني، فيلتقط أي رجوع إلى الحلقة الخطية فورًا.
 */
@RunWith(RobolectricTestRunner::class)
class ScanDedupPerformanceTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var source: StaticFileSource
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

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
    fun tenThousandFilesInOneFolderDedupUnderBudget() = runBlocking {
        val files = fakeFiles(10_000)
        val report = ScanRoot.MutableScanReport(root.id)

        val ms = measureNanoTime {
            scanRoot.prepareFiles(
                rootId = root.id,
                files = files,
                root = root,
                foundUris = mutableSetOf(),
                report = report
            )
        } / 1_000_000

        assertEquals("كل الملفات رُصدت", 10_000, report.filesSeen)
        assertEquals("لا تكرار في أسماء فريدة", 0, report.filesDeduped)
        assertTrue("10,000 ملف في مجلد واحد: ${ms}ms — تجاوز 100ms", ms < 100)
        println("PERF dedup-10k=${ms}ms files=${report.filesSeen}")
    }

    @Test
    fun fiftyThousandFilesInOneFolderDedupUnderBudget() = runBlocking {
        val files = fakeFiles(50_000)
        val report = ScanRoot.MutableScanReport(root.id)

        val ms = measureNanoTime {
            scanRoot.prepareFiles(
                rootId = root.id,
                files = files,
                root = root,
                foundUris = mutableSetOf(),
                report = report
            )
        } / 1_000_000

        assertEquals("كل الملفات رُصدت", 50_000, report.filesSeen)
        assertTrue("50,000 ملف في مجلد واحد: ${ms}ms — تجاوز 500ms", ms < 500)
        println("PERF dedup-50k=${ms}ms files=${report.filesSeen}")
    }

    @Test
    fun duplicateNamesWithinAFolderAreStillDeduped() = runBlocking {
        // 5,000 فريد + 5,000 نسخة بنفس الاسم والحجم في نفس المجلد.
        val unique = fakeFiles(5_000)
        val duplicates = unique.map {
            it.copy(uri = Uri.parse("content://dup/${it.fileName}"))
        }
        val report = ScanRoot.MutableScanReport(root.id)

        scanRoot.prepareFiles(
            rootId = root.id,
            files = unique + duplicates,
            root = root,
            foundUris = mutableSetOf(),
            report = report
        )

        assertEquals("كل الملفات رُصدت", 10_000, report.filesSeen)
        assertEquals("نسخ نفس الاسم والحجم تُستبعد", 5_000, report.filesDeduped)
    }

    @Test
    fun differentNamesWithSameSizeAreBothKept() = runBlocking {
        // حارس ضد تعميم مفرط: الاسم جزء من المفتاح، فملفان بالحجم
        // نفسه وأسماء مختلفة ملفان حقيقيان لا تكرار.
        val files = fakeFiles(2).mapIndexed { index, file ->
            file.copy(fileName = if (index == 0) "a.mp3" else "b.mp3")
        }
        val report = ScanRoot.MutableScanReport(root.id)

        val prepared = scanRoot.prepareFiles(
            rootId = root.id,
            files = files,
            root = root,
            foundUris = mutableSetOf(),
            report = report
        )

        assertEquals("لا تكرار", 0, report.filesDeduped)
        assertEquals("ملفان محفوظان", 2, prepared.getValue(folder).files.size)
    }

    private fun fakeFiles(count: Int): List<ScanFile> = (1..count).map { index ->
        val name = "%06d.mp3".format(index)
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
