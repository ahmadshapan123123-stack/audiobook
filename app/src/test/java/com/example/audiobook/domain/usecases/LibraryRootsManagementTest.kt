package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.PendingDiscoveryEntity
import com.example.audiobook.data.room.entity.DiscoveryStatus
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanCheckpointEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import java.util.UUID
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

/**
 * STAGE 4/5/6B — اختبارات منطق الجذور الجديد: الاستئناف، الدمج، والحذف
 * المتتابع. كلها على قاعدة in-memory بلا فحص فعلي.
 */
@RunWith(RobolectricTestRunner::class)
class LibraryRootsManagementTest {
    private lateinit var database: AppDatabase
    private lateinit var management: LibraryManagement

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        management = libraryManagementFor(database)
    }

    @After
    fun tearDown() = database.close()

    // ---------- helpers ----------

    private suspend fun newRoot(name: String, uri: String = "content://$name"): LibraryRootEntity {
        val root = LibraryRootEntity(uri = uri, displayName = name, isPriority = false, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        database.libraryRootDao().insert(root)
        return root
    }

    private suspend fun newAuthor(name: String): AuthorEntity {
        val author = AuthorEntity(name = name, colorTheme = null)
        database.authorDao().insert(author)
        return author
    }

    private suspend fun newBook(title: String, authorId: UUID?): BookEntity {
        val book = BookEntity(
            title = title, authorId = authorId, seriesId = null, orderInSeries = null,
            genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        return book
    }

    private suspend fun newEdition(bookId: UUID, rootId: UUID, folder: String, authorId: UUID?): EditionEntity {
        val edition = EditionEntity(
            bookId = bookId, narratorName = null, label = folder, totalDurationMs = 1000L,
            fileFormat = "mp3", libraryRootId = rootId, sourceFolderPath = folder,
            confidenceScore = 1f, isUserConfirmed = false, remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY, authorId = authorId, seriesId = null, bookTitle = folder
        )
        database.editionDao().insert(edition)
        database.audioFileDao().insert(
            AudioFileEntity(
                editionId = edition.id, fileUri = "content://f/${edition.id}", relativePath = "$folder/1.mp3",
                fileName = "1.mp3", orderIndex = 0, durationMs = 1000L, fileSizeBytes = 100L,
                lastModified = 1L, contentFingerprint = "fp", mimeType = "audio/mp3", fileStatus = FileStatus.AVAILABLE
            )
        )
        database.chapterDao().insert(
            ChapterEntity(editionId = edition.id, title = "ch1", startPositionMs = 0L, orderIndex = 0, createdFrom = ChapterCreatedFrom.AUTO_SPLIT)
        )
        database.bookmarkDao().insert(
            BookmarkEntity(editionId = edition.id, positionMs = 10L, createdAt = 1L, type = BookmarkType.BOOKMARK, noteText = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        )
        database.progressDao().insert(
            ListeningProgressEntity(editionId = edition.id, currentPositionMs = 10L, lastPlayedAt = 1L, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        )
        return edition
    }

    // ---------- STAGE 4: resume ----------

    @Test
    fun freshCheckpointIsReportedForResume() = runBlocking {
        val root = newRoot("R1")
        database.scanCheckpointDao().upsert(ScanCheckpointEntity(root.id, "author/series", System.currentTimeMillis()))
        val info = management.getScanResumeInfo()
        assertNotNull(info)
        assertEquals(root.id, info!!.rootId)
        assertEquals("R1", info.rootLabel)
    }

    @Test
    fun expiredCheckpointIsCleanedAndNotReported() = runBlocking {
        val root = newRoot("R1")
        database.scanCheckpointDao().upsert(
            ScanCheckpointEntity(root.id, "author/series", System.currentTimeMillis() - ScanResume.TTL_MS - 1000)
        )
        assertNull(management.getScanResumeInfo())
        assertNull(database.scanCheckpointDao().getForRoot(root.id))
    }

    @Test
    fun disabledRootCheckpointIsNotReported() = runBlocking {
        val root = newRoot("R1")
        database.libraryRootDao().setEnabled(root.id, false)
        database.scanCheckpointDao().upsert(ScanCheckpointEntity(root.id, "a/b", System.currentTimeMillis()))
        assertNull(management.getScanResumeInfo())
    }

    // ---------- STAGE 5: merge ----------

    @Test
    fun singleAuthorRootsAreDetectedAsMergeCandidates() = runBlocking {
        val r1 = newRoot("Sanderson", "content://lib/Sanderson")
        val r2 = newRoot("Jordan", "content://lib/Jordan")
        val a1 = newAuthor("Sanderson")
        val a2 = newAuthor("Jordan")
        newEdition(newBook("B1", a1.id).id, r1.id, "Mistborn", a1.id)
        newEdition(newBook("B2", a2.id).id, r2.id, "Eye", a2.id)

        val candidates = management.detectMergeCandidates()
        assertEquals(2, candidates.size)
        assertEquals(setOf(r1.id, r2.id), candidates.map { it.rootId }.toSet())
    }

    @Test
    fun sharedAuthorRootsAreNotMergeCandidates() = runBlocking {
        val r1 = newRoot("R1")
        val r2 = newRoot("R2")
        val a = newAuthor("Same")
        newEdition(newBook("B1", a.id).id, r1.id, "F1", a.id)
        newEdition(newBook("B2", a.id).id, r2.id, "F2", a.id)
        assertTrue(management.detectMergeCandidates().isEmpty())
    }

    @Test
    fun applyMergeMovesEditionsAndKeepsBooks() = runBlocking {
        val r1 = newRoot("Sanderson", "content://lib/Sanderson")
        val r2 = newRoot("Jordan", "content://lib/Jordan")
        val a1 = newAuthor("Sanderson")
        val a2 = newAuthor("Jordan")
        val b1 = newBook("Mistborn", a1.id)
        val b2 = newBook("Eye", a2.id)
        newEdition(b1.id, r1.id, "Mistborn", a1.id)
        newEdition(b2.id, r2.id, "Eye", a2.id)
        database.pendingDiscoveryDao().insert(
            PendingDiscoveryEntity(rootId = r1.id, folderPath = "Mistborn", detectedTitle = "Mistborn", authorName = "Sanderson", seriesName = null, discoveredAt = 1L, status = DiscoveryStatus.PENDING)
        )

        val parentId = management.applyMerge("content://lib", "Library", listOf(r1.id, r2.id))

        // الجذران الأبناء زالا، والأب موجود.
        assertNull(database.libraryRootDao().getById(r1.id))
        assertNull(database.libraryRootDao().getById(r2.id))
        assertNotNull(database.libraryRootDao().getById(parentId))
        // الكتب والإصدارات باقية تحت الأب، بمسارات مسبوقة باسم مجلد الابن.
        assertEquals(2, database.bookDao().getAll().size)
        val editions = database.editionDao().getByRoot(parentId)
        assertEquals(2, editions.size)
        assertTrue(editions.all { it.sourceFolderPath.startsWith("Sanderson/") || it.sourceFolderPath.startsWith("Jordan/") })
        // الملفات والفصول والتقدم والعلامات باقية (مفاتيحها صفوف محفوظة).
        assertEquals(2, database.audioFileDao().getByRoot(parentId).size)
        assertEquals(2, database.bookmarkDao().getByParent(editions[0].id).size + database.bookmarkDao().getByParent(editions[1].id).size)
        // الاكتشافات انتقلت للأب بالبادئة نفسها.
        assertEquals(1, database.pendingDiscoveryDao().countPendingByRoot(parentId))
    }

    // ---------- STAGE 6B: delete + rename ----------

    @Test
    fun deleteRootCascadesButKeepsOtherRootsBooks() = runBlocking {
        val r1 = newRoot("R1")
        val r2 = newRoot("R2")
        val a = newAuthor("A")
        val shared = newBook("Shared", a.id)
        newEdition(shared.id, r1.id, "F1", a.id)
        newEdition(shared.id, r2.id, "F2", a.id)
        val solo = newBook("Solo", a.id)
        newEdition(solo.id, r1.id, "F3", a.id)
        database.scanCheckpointDao().upsert(ScanCheckpointEntity(r1.id, "F1", System.currentTimeMillis()))

        val result = management.deleteLibraryRoot(r1.id)
        assertNotNull(result)
        assertEquals("R1", result!!.rootName)
        assertEquals(2, result.editionsRemoved)
        // الكتاب المنفرد زال، والمشترك بقي (له إصدار في R2).
        assertNull(database.bookDao().getById(solo.id))
        assertNotNull(database.bookDao().getById(shared.id))
        // الجذر ونقطة توقفه زالا؛ ملفات R1 زالت.
        assertNull(database.libraryRootDao().getById(r1.id))
        assertNull(database.scanCheckpointDao().getForRoot(r1.id))
        assertEquals(1, database.audioFileDao().getByRoot(r2.id).size)
    }

    @Test
    fun renameRootUpdatesDisplayNameOnly() = runBlocking {
        val root = newRoot("Old")
        assertTrue(management.renameLibraryRoot(root.id, "  New  "))
        assertEquals("New", database.libraryRootDao().getById(root.id)!!.displayName)
        assertEquals("content://Old", database.libraryRootDao().getById(root.id)!!.uri)
    }
}
