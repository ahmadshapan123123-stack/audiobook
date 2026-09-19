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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

/**
 * BUG 1+2 regression through the full scan pipeline on API 23: يعمل الفحص الكامل
 * ويستدعي EditionSignalExtractor (قراءة المجموعات بالفهرس بعد الإصلاح) وبناء
 * الإشعارات. أي إعادة لإدخال الوصول بالأسماء/API 26 في مسار الفحص ستكسر هذا الاختبار.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class ScanRootApiCompatTest {
    private lateinit var database: AppDatabase
    private lateinit var source: FakeFileSource
    private lateinit var reader: CountingMetadataReader
    private lateinit var scanRoot: ScanRoot
    private lateinit var appSettings: AppSettings
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = FakeFileSource()
        reader = CountingMetadataReader()
        appSettings = AppSettings(context)
        scanRoot = ScanRoot(database, source, reader, appSettings, EditionMerge(database))
        root = LibraryRootEntity(uri = "content://library", displayName = "Library", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun fullScanCreatesBooksFromSeriesAndNarratorFoldersOnApi23() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_800_000L, "audio/mp4", null, "فلان الراوي", "سيرة", emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/01.mp3"), "السيرة النبوية/الجزء الثالث/01.mp3", "السيرة النبوية/الجزء الثالث", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/02.mp3"), "السيرة النبوية/الجزء الثالث/02.mp3", "السيرة النبوية/الجزء الثالث", "02.mp3", 200, 10),
            ScanFile(Uri.parse("content://audio/03.mp3"), "الأدب العربي/Book 3/01.mp3", "الأدب العربي/Book 3", "01.mp3", 100, 10)
        )

        val report = scanRoot(root.id)

        assertEquals("الفحص يكتمل بلا استثناء على API 23 ويُنشئ كتبًا", 2, report.editionsCreated)
        assertEquals(2, database.bookDao().getAll().size)
        val sira = database.editionDao().getByRootAndFolder(root.id, "السيرة النبوية/الجزء الثالث")!!
        assertNotNull(sira)
        assertEquals("فلان الراوي", sira.narratorName)
        assertEquals(3_600_000L, sira.totalDurationMs)
        val kitab = database.editionDao().getByRootAndFolder(root.id, "الأدب العربي/Book 3")!!
        assertNotNull(kitab)
        assertEquals("فلان الراوي", kitab.narratorName)
    }

    @Test
    fun fullScanExtractsSeriesPartOnApi23() = runBlocking {
        assertEquals(SeriesPart("juz", 3), EditionSignalExtractor.extractSeriesPart("الجزء الثالث"))
        assertEquals(SeriesPart("book", 3), EditionSignalExtractor.extractSeriesPart("Book 3"))
    }

    // ---- نسخ ذاتي من FakeFileSource/CountingMetadataReader (هما private في ScanRootTest) ----

    private class FakeFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class CountingMetadataReader : AudioMetadataReader {
        var readCount = 0
        var overrides = mutableMapOf<String, AudioMetadata>()
        var metadataByUri = mutableMapOf<String, AudioMetadata>()

        override fun read(uri: Uri, fileName: String): AudioMetadata {
            readCount++
            return metadataByUri[uri.toString()] ?: overrides["default"] ?: AudioMetadata(1000, "audio/mp4", "Book", null, null, emptyList())
        }
    }
}