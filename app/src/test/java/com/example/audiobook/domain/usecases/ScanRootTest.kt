package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ScanRootTest {
    private lateinit var database: AppDatabase
    private lateinit var source: FakeFileSource
    private lateinit var reader: CountingMetadataReader
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = FakeFileSource()
        reader = CountingMetadataReader()
        scanRoot = ScanRoot(database, source, reader)
        root = LibraryRootEntity(uri = "content://library", displayName = "Library", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun repeatedScanUsesCacheThenMissingAndRestorePreserveListeningData() = runBlocking {
        val file = ScanFile(UUID.randomUUID().let { android.net.Uri.parse("content://audio/$it.m4b") }, "Book/Part.m4b", "Book", "Part.m4b", 100, 10)
        source.files = listOf(file)

        val first = scanRoot(root.id)
        val second = scanRoot(root.id)
        assertEquals(1, first.metadataReads)
        assertEquals(0, first.cacheHits)
        assertEquals(0, second.metadataReads)
        assertEquals(1, second.cacheHits)
        assertEquals(1, reader.readCount)

        val edition = database.editionDao().getByRootAndFolder(root.id, "Book")!!
        val bookmark = BookmarkEntity(editionId = edition.id, positionMs = 50, createdAt = 1, type = BookmarkType.NOTE, noteText = "Keep", remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        val progress = ListeningProgressEntity(editionId = edition.id, currentPositionMs = 50, lastPlayedAt = 1, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.bookmarkDao().insert(bookmark)
        database.progressDao().insert(progress)

        source.files = emptyList()
        val missing = scanRoot(root.id)
        assertEquals(1, missing.missingMarked)
        assertEquals(FileStatus.MISSING, database.audioFileDao().getByUri(file.uri.toString())?.fileStatus)
        assertNotNull(database.editionDao().getById(edition.id))
        assertNotNull(database.bookmarkDao().getById(bookmark.id))
        assertNotNull(database.progressDao().getByParent(edition.id))

        source.files = listOf(file)
        val restored = scanRoot(root.id)
        assertEquals(1, restored.restored)
        assertEquals(0, restored.metadataReads)
        assertEquals(FileStatus.AVAILABLE, database.audioFileDao().getByUri(file.uri.toString())?.fileStatus)
        assertNotNull(database.bookmarkDao().getById(bookmark.id))
        assertNotNull(database.progressDao().getByParent(edition.id))
        assertEquals(1, reader.readCount)
    }

    private class FakeFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: android.net.Uri): List<ScanFile> = files
    }

    private class CountingMetadataReader : AudioMetadataReader {
        var readCount = 0
        override fun read(uri: android.net.Uri, fileName: String): AudioMetadata {
            readCount++
            return AudioMetadata(1000, "audio/mp4", "Book", "Narrator", null, emptyList())
        }
    }
}