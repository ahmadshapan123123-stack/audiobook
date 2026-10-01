package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * PHASE 6 — اختبارات الدمج الجماعي: كإصدارات (نقل صفوف النسخ) وكفصول
 * (نقل الملفات بإزاحة) على قاعدة داخلية حقيقية.
 */
@RunWith(RobolectricTestRunner::class)
class BulkMergeTest {
    private lateinit var database: AppDatabase
    private lateinit var management: LibraryManagement
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        management = libraryManagementFor(database)
        root = LibraryRootEntity(uri = "content://library", displayName = "Library", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun seedBook(title: String, editionLabels: List<String>, fileDurations: List<Long>): BookEntity {
        val book = BookEntity(
            title = title, authorId = null, seriesId = null, orderInSeries = null, genre = null,
            coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
            defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        var defaultId: UUID? = null
        editionLabels.forEachIndexed { index, label ->
            val edition = EditionEntity(
                bookId = book.id, narratorName = "N$index", label = label,
                totalDurationMs = fileDurations.sum(), fileFormat = "MP3",
                libraryRootId = root.id, sourceFolderPath = "/$title/$label",
                confidenceScore = 1f, isUserConfirmed = false,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
            database.editionDao().insert(edition)
            if (defaultId == null) defaultId = edition.id
            fileDurations.forEachIndexed { fileIndex, duration ->
                database.audioFileDao().insert(
                    AudioFileEntity(
                        editionId = edition.id, fileUri = "content://$title/$label/$fileIndex",
                        relativePath = "$label", fileName = "$fileIndex.mp3", orderIndex = fileIndex,
                        durationMs = duration, fileSizeBytes = 100L, lastModified = 1L,
                        contentFingerprint = "fp-$title-$label-$fileIndex",
                        mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE
                    )
                )
            }
        }
        database.bookDao().update(book.copy(defaultEditionId = defaultId))
        return database.bookDao().getById(book.id)!!
    }

    @Test
    fun mergeBooksAsEditions_movesAllEditionsAndKeepsData() = runBlocking {
        val primary = seedBook("Primary", listOf("E1"), listOf(1000L))
        val second = seedBook("Second", listOf("E2a", "E2b"), listOf(2000L))
        val third = seedBook("Third", listOf("E3a", "E3b"), listOf(3000L))
        // تقدّم وعلامة ومفضّلة ومجموعة على الثاني للتحقق من النقل.
        val secondEdition = database.editionDao().getByParent(second.id).first()
        database.progressDao().insert(
            ListeningProgressEntity(
                editionId = secondEdition.id, currentPositionMs = 500L, lastPlayedAt = 9L,
                status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        database.bookmarkDao().insert(
            BookmarkEntity(
                editionId = secondEdition.id, positionMs = 100L, createdAt = 8L,
                type = BookmarkType.BOOKMARK, noteText = null,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        database.favoriteBookDao().insert(FavoriteBook(second.id, 7L))

        val result = management.mergeBooksAsEditions(primary.id, listOf(second.id, third.id))

        assertEquals(2, result.mergedCount)
        assertEquals(5, database.editionDao().getByParent(primary.id).size)
        assertNull(database.bookDao().getById(second.id))
        assertNull(database.bookDao().getById(third.id))
        // البيانات تابعة للنسخ (لا تتأثر بنقل bookId).
        assertEquals(1, database.bookmarkDao().getByParent(secondEdition.id).size)
        assertNotNull(database.progressDao().getByParent(secondEdition.id))
        assertNotNull(database.favoriteBookDao().getById(primary.id))
        assertTrue(result.affectedEditionIds.size == 4)
        assertNotNull(database.bookDao().getById(primary.id)!!.defaultEditionId)
    }

    @Test
    fun mergeBooksAsChapters_movesFilesSequentiallyWithOffsets() = runBlocking {
        val primary = seedBook("Primary", listOf("PE"), listOf(1000L, 1000L))
        val second = seedBook("Second", listOf("SE"), listOf(2000L, 2000L))
        val third = seedBook("Third", listOf("TE"), listOf(3000L, 3000L))
        val primaryEdition = database.editionDao().getByParent(primary.id).first()
        val secondEdition = database.editionDao().getByParent(second.id).first()
        // فصل وعلامة على نسخة المصدر للتحقق من الإزاحة (الأساسي 2000ms).
        database.chapterDao().insert(
            ChapterEntity(
                editionId = secondEdition.id, title = "C1",
                startPositionMs = 500L, orderIndex = 0, createdFrom = ChapterCreatedFrom.IMPORTED
            )
        )
        database.bookmarkDao().insert(
            BookmarkEntity(
                editionId = secondEdition.id, positionMs = 1500L, createdAt = 8L,
                type = BookmarkType.NOTE, noteText = "Keep",
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        database.progressDao().insert(
            ListeningProgressEntity(
                editionId = secondEdition.id, currentPositionMs = 100L, lastPlayedAt = 9L,
                status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )

        val result = management.mergeBooksAsChapters(primary.id, listOf(second.id, third.id))

        assertEquals(2, result.mergedCount)
        val files = database.audioFileDao().getByParent(primaryEdition.id).sortedBy { it.orderIndex }
        assertEquals(6, files.size)
        assertEquals((0..5).toList(), files.map { it.orderIndex })
        assertEquals(2000L + 4000L + 6000L, files.sumOf { it.durationMs })
        val movedChapter = database.chapterDao().getByParent(primaryEdition.id).firstOrNull { it.title == "C1" }
        assertNotNull(movedChapter)
        assertEquals(2000L + 500L, movedChapter!!.startPositionMs)
        val movedNote = database.bookmarkDao().getByParent(primaryEdition.id).firstOrNull { it.noteText == "Keep" }
        assertNotNull(movedNote)
        assertEquals(2000L + 1500L, movedNote!!.positionMs)
        // تقدّم الأساسي غائب → يُستعار من المصدر.
        assertNotNull(database.progressDao().getByParent(primaryEdition.id))
        assertNull(database.bookDao().getById(second.id))
        assertNull(database.bookDao().getById(third.id))
        assertEquals(2000L + 4000L + 6000L, database.editionDao().getById(primaryEdition.id)!!.totalDurationMs)
    }
}
