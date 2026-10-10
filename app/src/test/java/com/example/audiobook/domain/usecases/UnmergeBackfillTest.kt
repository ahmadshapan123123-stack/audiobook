package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * PART 2+3 — فك الدمج والتعبئة الرجعية: تجميع المجلدات، حدود IMPORTED،
 * تقسيم الملفات/الفصول/العلامات، وعدم مساس فصول المستخدم.
 */
@RunWith(RobolectricTestRunner::class)
class UnmergeBackfillTest {
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

    /** كتاب بنسخة واحدة وملفات موزعة على مجلدات (محاكاة دمج قديم بلا حدود). */
    private suspend fun seedMergedBook(folders: List<Pair<String, List<Long>>>): Pair<BookEntity, EditionEntity> {
        val book = BookEntity(
            title = "Merged", authorId = null, seriesId = null, orderInSeries = null, genre = null,
            coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
            defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        val edition = EditionEntity(
            bookId = book.id, narratorName = null, label = "E",
            totalDurationMs = folders.sumOf { it.second.sum() }, fileFormat = "MP3",
            libraryRootId = root.id, sourceFolderPath = "/Merged",
            confidenceScore = 1f, isUserConfirmed = false,
            remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.editionDao().insert(edition)
        var order = 0
        folders.forEach { (folder, durations) ->
            durations.forEachIndexed { fileIndex, duration ->
                database.audioFileDao().insert(
                    AudioFileEntity(
                        editionId = edition.id, fileUri = "content://library/$folder/$fileIndex",
                        relativePath = "$folder/$fileIndex.mp3", fileName = "$fileIndex.mp3", orderIndex = order++,
                        durationMs = duration, fileSizeBytes = 100L, lastModified = 1L,
                        contentFingerprint = "fp-$folder-$fileIndex",
                        mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE
                    )
                )
            }
        }
        database.bookDao().update(book.copy(defaultEditionId = edition.id))
        return database.bookDao().getById(book.id)!! to edition
    }

    @Test
    fun folderGroups_groupsByParentFolderWithOffsets() {
        val files = listOf(
            AudioFileEntity(editionId = UUID.randomUUID(), fileUri = "c://a/1", relativePath = "A/1.mp3", fileName = "1.mp3", orderIndex = 0, durationMs = 1000L, fileSizeBytes = 1L, lastModified = 1L, contentFingerprint = "x1", mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE),
            AudioFileEntity(editionId = UUID.randomUUID(), fileUri = "c://a/2", relativePath = "A/2.mp3", fileName = "2.mp3", orderIndex = 1, durationMs = 1000L, fileSizeBytes = 1L, lastModified = 1L, contentFingerprint = "x2", mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE),
            AudioFileEntity(editionId = UUID.randomUUID(), fileUri = "c://b/1", relativePath = "B/1.mp3", fileName = "1.mp3", orderIndex = 2, durationMs = 2000L, fileSizeBytes = 1L, lastModified = 1L, contentFingerprint = "x3", mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE)
        )
        val groups = management.folderGroups(files)
        assertEquals(2, groups.size)
        assertEquals(0L, groups[0].startOffsetMs)
        assertEquals(2000L, groups[1].startOffsetMs)
        assertEquals(2, groups[0].files.size)
        assertEquals(1, groups[1].files.size)
    }

    @Test
    fun backfill_createsBoundariesOnlyForMultiFolderChaptersless() = runBlocking {
        val (merged, _) = seedMergedBook(listOf("BookA" to listOf(1000L, 1000L), "BookB" to listOf(2000L)))
        val (single, singleEdition) = seedMergedBook(listOf("Only" to listOf(1000L, 1000L)))
        // نسخة بفصل مستخدم: لا تُمس أبدًا.
        database.chapterDao().insert(
            ChapterEntity(editionId = singleEdition.id, title = "Mine", startPositionMs = 100L, orderIndex = 0, createdFrom = ChapterCreatedFrom.MANUAL)
        )

        val result = management.backfillMergeBoundaries()

        assertEquals(1, result.chaptersCreated)
        assertEquals(1, result.editionsTouched)
        val boundaries = database.chapterDao().getByParent(database.editionDao().getByParent(merged.id).first().id)
        assertEquals(1, boundaries.size)
        assertEquals("BookB", boundaries.first().title)
        assertEquals(2000L, boundaries.first().startPositionMs)
        assertEquals(ChapterCreatedFrom.IMPORTED, boundaries.first().createdFrom)
        // فصل المستخدم باقٍ وحده.
        assertEquals(1, database.chapterDao().getByParent(singleEdition.id).size)
        // idempotent: إعادة التشغيل لا تُنشئ شيئًا.
        assertEquals(0, management.backfillMergeBoundaries().chaptersCreated)
    }

    @Test
    fun unmerge_splitsFilesChaptersBookmarksIntoNewBooks() = runBlocking {
        val (merged, edition) = seedMergedBook(listOf("BookA" to listOf(1000L, 1000L), "BookB" to listOf(2000L, 2000L)))
        management.backfillMergeBoundaries()
        database.bookmarkDao().insert(
            BookmarkEntity(editionId = edition.id, positionMs = 2500L, createdAt = 8L, type = BookmarkType.BOOKMARK, noteText = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        )
        database.progressDao().insert(
            ListeningProgressEntity(editionId = edition.id, currentPositionMs = 100L, lastPlayedAt = 9L, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        )

        val preview = management.unmergeSegments(merged.id)
        assertEquals(2, preview.size)
        assertEquals("BookB", preview[1].title)

        val result = management.unmergeBook(merged.id)
        assertEquals(2, result.segmentCount)
        assertEquals(1, result.newBookIds.size)
        val newBook = database.bookDao().getById(result.newBookIds.first())!!
        assertEquals("BookB", newBook.title)
        val newEdition = database.editionDao().getByParent(newBook.id).first()
        assertEquals(2, database.audioFileDao().getByParent(newEdition.id).size)
        assertEquals(4000L, newEdition.totalDurationMs)
        // الأصلي يحتفظ بالمقطع الأول والتقدّم والعلامة المنقولة للجديد.
        assertEquals(2, database.audioFileDao().getByParent(edition.id).size)
        assertEquals(2000L, database.editionDao().getById(edition.id)!!.totalDurationMs)
        assertTrue(database.progressDao().getByParent(edition.id) != null)
        assertEquals(1, database.bookmarkDao().getByParent(newEdition.id).size)
        assertEquals(500L, database.bookmarkDao().getByParent(newEdition.id).first().positionMs)
    }

    @Test
    fun unmerge_singleFolderBookIsNotSplittable() = runBlocking {
        val (book, _) = seedMergedBook(listOf("Only" to listOf(1000L, 1000L)))
        assertTrue(management.unmergeSegments(book.id).isEmpty())
        assertEquals(0, management.unmergeBook(book.id).segmentCount)
    }

    /** كتاب أحادي المجلد بأسماء بلا ترقيم (محاكاة دمج قديم ضائع الأثر). */
    private suspend fun seedSingleFolderBook(names: List<Pair<String, Long>>): Pair<BookEntity, EditionEntity> {
        val book = BookEntity(
            title = "OldMerged", authorId = null, seriesId = null, orderInSeries = null, genre = null,
            coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
            defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        val edition = EditionEntity(
            bookId = book.id, narratorName = null, label = "E",
            totalDurationMs = names.sumOf { it.second }, fileFormat = "MP3",
            libraryRootId = root.id, sourceFolderPath = "/OldMerged",
            confidenceScore = 1f, isUserConfirmed = false,
            remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.editionDao().insert(edition)
        names.forEachIndexed { index, (name, duration) ->
            database.audioFileDao().insert(
                AudioFileEntity(
                    editionId = edition.id, fileUri = "content://library/OldMerged/$name",
                    relativePath = "OldMerged/$name", fileName = name, orderIndex = index,
                    durationMs = duration, fileSizeBytes = 100L, lastModified = 1L,
                    contentFingerprint = "fp-$name",
                    mimeType = "audio/mpeg", fileStatus = FileStatus.AVAILABLE
                )
            )
        }
        database.bookDao().update(book.copy(defaultEditionId = edition.id))
        return database.bookDao().getById(book.id)!! to edition
    }

    @Test
    fun backfillSingleGroupNoSplits_createsAnchorAtZero() = runBlocking {
        val (book, edition) = seedSingleFolderBook(
            listOf("intro.mp3" to 1000L, "outro.mp3" to 1000L, "bonus.mp3" to 1000L)
        )
        val result = management.backfillMergeBoundaries()
        assertEquals(1, result.chaptersCreated)
        assertEquals(1, result.booksTouched)
        val chapters = database.chapterDao().getByParent(edition.id)
        assertEquals(1, chapters.size)
        assertEquals(0L, chapters.first().startPositionMs)
        assertEquals("OldMerged", chapters.first().title)
        assertEquals(ChapterCreatedFrom.IMPORTED, chapters.first().createdFrom)
    }

    @Test
    fun backfillFilenameSplit_createsPartBoundary() = runBlocking {
        // ترقيم يعيد البدء من 01 (نمط الكتاب الثاني المدمج). الأسماء فريدة
        // لأن fileUri فريد في القاعدة (إدخال مكرر يستبدل سابقه).
        val (_, edition) = seedSingleFolderBook(
            listOf("02a.mp3" to 1000L, "03.mp3" to 1000L, "01.mp3" to 2000L, "02b.mp3" to 2000L)
        )
        val result = management.backfillMergeBoundaries()
        assertEquals(1, result.chaptersCreated)
        val chapters = database.chapterDao().getByParent(edition.id)
        assertEquals(1, chapters.size)
        assertEquals(2000L, chapters.first().startPositionMs)
        assertEquals("الجزء 2", chapters.first().title)
    }

    @Test
    fun backfillSkipsSingleFile() = runBlocking {
        seedSingleFolderBook(listOf("only.mp3" to 1000L))
        assertEquals(0, management.backfillMergeBoundaries().chaptersCreated)
    }

    @Test
    fun backfillV2Flag_defaultsFalseAndSets() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = com.example.audiobook.data.preferences.AppSettings(context)
        assertEquals(false, settings.mergeBoundaryBackfillDoneV2)
        settings.setMergeBoundaryBackfillDoneV2(true)
        assertEquals(true, settings.mergeBoundaryBackfillDoneV2)
    }
}
