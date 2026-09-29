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
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FIX 5.3 — غلاف المستخدم ينجو من إعادة الفحص: `coverImagePath` المختار
 * يدويًا لا يمسّه `resolveUnit` (ينسخ الحقول المكتشفة فقط)، و`CoverPolicy`
 * تمنع الكتابة فوقه — فيبقى بعد الفحص كما كان.
 */
@RunWith(RobolectricTestRunner::class)
class UserCoverRescanTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        appSettings = AppSettings(context)
        scanRoot = ScanRoot(database, StaticFileSource(), ConstantMetadataReader(), appSettings, EditionMerge(database))
        root = LibraryRootEntity(
            uri = "content://library", displayName = "Library", isPriority = true,
            isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE
        )
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun userSelectedCoverSurvivesRescan() = runBlocking {
        val author = AuthorEntity(name = "A", colorTheme = null).also { database.authorDao().insert(it) }
        val series = SeriesEntity(authorId = author.id, name = "S", colorTheme = null).also { database.seriesDao().insert(it) }
        val book = BookEntity(
            id = UUID.randomUUID(), title = "T", authorId = author.id, seriesId = series.id,
            orderInSeries = 1, genre = null, coverImagePath = "/covers/book.jpg",
            coverSource = CoverSource.FOLDER_COVER, isCoverUserSelected = true,
            defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        ).also { database.bookDao().insert(it) }
        val edition = EditionEntity(
            bookId = book.id, narratorName = null, label = "T", totalDurationMs = 1_800_000L,
            fileFormat = "mp3", libraryRootId = root.id, sourceFolderPath = "A/S",
            confidenceScore = 1f, isUserConfirmed = false, remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY, authorId = author.id, seriesId = series.id, bookTitle = "T"
        ).also { database.editionDao().insert(it) }
        // ملف مطابق تمامًا (cache hit) — الفحص يرفقه بالموجود لا ينشئ جديدًا.
        database.audioFileDao().insert(
            AudioFileEntity(
                editionId = edition.id, fileUri = "content://t/A/S/T.mp3", relativePath = "A/S/T.mp3",
                fileName = "T.mp3", orderIndex = 0, durationMs = 1_800_000L, fileSizeBytes = 1_000L,
                lastModified = 10L, contentFingerprint = "fp", mimeType = "audio/mp3",
                fileStatus = FileStatus.AVAILABLE
            )
        )

        val report = scanRoot(root.id)

        assertEquals("لا كتب جديدة", 1, database.bookDao().getAll().size)
        assertEquals("لا إصدارات جديدة", 1, database.editionDao().getByRoot(root.id).size)
        val after = database.bookDao().getById(book.id)!!
        assertEquals("مسار الغلاف باقٍ", "/covers/book.jpg", after.coverImagePath)
        assertTrue("علامة اختيار المستخدم باقية", after.isCoverUserSelected)
        assertEquals("كل الملفات رُصدت", 1, report.filesSeen)
    }

    private class StaticFileSource : LibraryFileSource {
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = listOf(
            ScanFile(
                uri = Uri.parse("content://t/A/S/T.mp3"), relativePath = "A/S/T.mp3",
                folderPath = "A/S", fileName = "T.mp3", size = 1_000L, lastModified = 10L
            )
        )
    }

    private class ConstantMetadataReader : AudioMetadataReader {
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            AudioMetadata(1_800_000L, "audio/mp3", null, null, null, emptyList())
    }
}
