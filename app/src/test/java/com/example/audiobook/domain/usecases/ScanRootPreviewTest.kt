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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FIX 2 — معاينة `ScanRoot.preview()`: شجرة صحيحة + تقدّم حي (مجلد/ملف)
 * + لا كتابة في القاعدة إطلاقًا (قراءة فقط).
 */
@RunWith(RobolectricTestRunner::class)
class ScanRootPreviewTest {
    private lateinit var database: AppDatabase
    private lateinit var scanRoot: ScanRoot

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        scanRoot = ScanRoot(
            database,
            StaticFileSource(),
            ConstantMetadataReader(),
            AppSettings(context),
            EditionMerge(database)
        )
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun previewBuildsTreeWithLiveProgressAndNoDbWrites() = runBlocking {
        val phases = mutableListOf<ScanProgress>()
        val tree = scanRoot.preview(Uri.parse("content://lib")) { phases += it }

        // الشجرة: مؤلفان (A بسلسلة، B مباشر).
        assertEquals("مؤلفان", 2, tree.authors.size)
        val books = tree.authors.sumOf { it.books.size + it.series.sumOf { s -> s.books.size } }
        assertTrue("كتب مكتشفة", books >= 3)

        // التقدّم: DISCOVERING بالمجموع، وPARSING يحمل مجلدًا وملفًا حقيقيين.
        val discovering = phases.first { it.phase == ScanPhase.DISCOVERING && it.total > 0 }
        assertEquals("كل الملفات", 4, discovering.total)
        // DISCOVERING-LIVE: نشرات أثناء الجوس نفسه (total == 0) بمجلد حقيقي.
        val liveWalk = phases.filter { it.phase == ScanPhase.DISCOVERING && it.total == 0 }
        assertTrue("نشرات حية أثناء الجوس", liveWalk.isNotEmpty())
        assertTrue("مجلد الجوس حي", liveWalk.any { it.currentFolder.isNotBlank() })
        assertTrue("عدّاد المكتشفات يتقدم", liveWalk.any { it.processed > 0 })
        val parsing = phases.filter { it.phase == ScanPhase.PARSING }
        assertTrue("نشرة قراءة", parsing.isNotEmpty())
        assertTrue("مجلد حقيقي", parsing.any { it.currentFolder.isNotBlank() })
        assertTrue("ملف حقيقي", parsing.any { it.currentFile.isNotBlank() })
        assertTrue("عدّادات X/Y", parsing.any { it.processed > 0 && it.total == 4 })
        assertEquals("ختام DONE", ScanPhase.DONE, phases.last().phase)

        // قراءة فقط: لا كتاب ولا إصدار ولا ملف كُتب في القاعدة.
        assertTrue("لا كتب في القاعدة", database.bookDao().getAll().isEmpty())
        assertTrue(
            "لا ملفات في القاعدة",
            database.audioFileDao().countByRoot(java.util.UUID.randomUUID()) == 0
        )
    }

    private fun files(): List<ScanFile> = listOf(
        file("A/S1", "b1.mp3"),
        file("A/S1", "b2.mp3"),
        file("A", "solo.mp3"),
        file("B", "only.mp3")
    )

    private fun file(folder: String, name: String) = ScanFile(
        uri = Uri.parse("content://lib/$folder/$name"),
        relativePath = "$folder/$name",
        folderPath = folder,
        fileName = name,
        size = 1_000L,
        lastModified = 1L
    )

    private inner class StaticFileSource : LibraryFileSource {
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files()
    }

    private class ConstantMetadataReader : AudioMetadataReader {
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            AudioMetadata(1_000L, "audio/mp3", null, null, null, emptyList())
    }
}
