package com.example.audiobook.data.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.entity.*
import com.example.audiobook.domain.usecases.RecoverInterruptedSession
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
class RoomDataTest {
    private lateinit var database: AppDatabase
    private lateinit var root: LibraryRootEntity
    private lateinit var author: AuthorEntity
    private lateinit var book: BookEntity
    private lateinit var edition: EditionEntity

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        author = AuthorEntity(name = "Author", colorTheme = null)
        book = BookEntity(title = "Book", authorId = author.id, seriesId = null, orderInSeries = null, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        edition = EditionEntity(bookId = book.id, narratorName = "Narrator", label = "Edition", totalDurationMs = 1000, fileFormat = "M4B", libraryRootId = root.id, sourceFolderPath = "/books", confidenceScore = 1f, isUserConfirmed = true, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.libraryRootDao().insert(root)
        database.authorDao().insert(author)
        database.bookDao().insert(book)
        database.editionDao().insert(edition)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun insertsAndGetsRootsAuthorsAndBooksInOrder() = runBlocking {
        val second = book.copy(id = UUID.randomUUID(), title = "Second", orderInSeries = 2)
        val first = book.copy(id = UUID.randomUUID(), title = "First", orderInSeries = 1)
        database.bookDao().insert(second)
        database.bookDao().insert(first)

        assertEquals(root, database.libraryRootDao().getById(root.id))
        assertEquals(author, database.authorDao().getById(author.id))
        assertEquals(listOf(first, second), database.bookDao().getByParent(author.id).take(2))
    }

    @Test
    fun seriesAndCrudDaosSupportUpdateAndDelete() = runBlocking {
        val series = SeriesEntity(authorId = author.id, name = "Series", colorTheme = null)
        database.seriesDao().insert(series)
        assertEquals(series, database.seriesDao().getByParent(author.id).single())
        database.seriesDao().update(series.copy(name = "Updated"))
        assertEquals("Updated", database.seriesDao().getById(series.id)?.name)
        database.seriesDao().delete(series.copy(name = "Updated"))
        assertEquals(null, database.seriesDao().getById(series.id))

        val collection = CollectionEntity(name = "C", icon = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.collectionDao().insert(collection)
        val ref = CollectionBookCrossRef(collection.id, book.id)
        database.collectionBookCrossRefDao().insert(ref)
        assertEquals(ref, database.collectionBookCrossRefDao().getById(collection.id, book.id))
        database.collectionBookCrossRefDao().update(ref)
        database.collectionBookCrossRefDao().delete(collection.id, book.id)
        assertEquals(null, database.collectionBookCrossRefDao().getById(collection.id, book.id))
    }

    @Test
    fun libraryRootsKeepOnePriorityAndAllowEnableToggle() = runBlocking {
        val second = root.copy(id = UUID.randomUUID(), uri = "content://second", displayName = "Second")
        database.libraryRootDao().insert(second)
        database.libraryRootDao().clearPriorityExcept(second.id)
        database.libraryRootDao().setPriority(second.id, true)
        database.libraryRootDao().setEnabled(root.id, false)

        assertEquals(false, database.libraryRootDao().getById(root.id)?.isPriority)
        assertEquals(true, database.libraryRootDao().getById(second.id)?.isPriority)
        assertEquals(false, database.libraryRootDao().getById(root.id)?.isEnabled)
    }

    @Test
    fun childDaosReturnParentRowsInRequiredOrder() = runBlocking {
        val fileSecond = audioFile(2)
        val fileFirst = audioFile(1)
        database.audioFileDao().insert(fileSecond)
        database.audioFileDao().insert(fileFirst)
        val chapterSecond = chapter(2)
        val chapterFirst = chapter(1)
        database.chapterDao().insert(chapterSecond)
        database.chapterDao().insert(chapterFirst)
        val bookmark = BookmarkEntity(editionId = edition.id, positionMs = 50, createdAt = 1, type = BookmarkType.BOOKMARK, noteText = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.bookmarkDao().insert(bookmark)

        assertEquals(listOf(fileFirst, fileSecond), database.audioFileDao().getByParent(edition.id))
        assertEquals(listOf(chapterFirst, chapterSecond), database.chapterDao().getByParent(edition.id))
        assertEquals(bookmark, database.bookmarkDao().getById(bookmark.id))
        assertEquals(edition, database.editionDao().getByParent(book.id).single())
    }

    @Test
    fun collectionFavoritesAndMatchDecisionPreserveRelationships() = runBlocking {
        val collection = CollectionEntity(name = "Favorites", icon = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.collectionDao().insert(collection)
        database.collectionBookCrossRefDao().insert(CollectionBookCrossRef(collection.id, book.id))
        database.favoriteBookDao().insert(FavoriteBook(book.id, 1))
        val decision = EditionMatchDecisionEntity(subjectEditionId = edition.id, comparedAgainstEditionId = null, signalsSnapshot = "{}", userDecision = UserDecision.SAME_EDITION, createdAt = 1)
        database.editionMatchDecisionDao().insert(decision)

        assertEquals(listOf(CollectionBookCrossRef(collection.id, book.id)), database.collectionBookCrossRefDao().getByParent(collection.id))
        assertEquals(book.id, database.favoriteBookDao().getById(book.id)?.bookId)
        assertEquals(edition.id, database.editionMatchDecisionDao().getByParent(edition.id).single().subjectEditionId)
    }

    @Test
    fun sessionRecoveryMarksActiveSessionInterruptedAtLastSavedProgress() = runBlocking {
        val progress = ListeningProgressEntity(editionId = edition.id, currentPositionMs = 700, lastPlayedAt = 1234, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        val active = ListeningSessionEntity(editionId = edition.id, startedAt = 100, endedAt = null, durationListenedMs = 600, endReason = null, sessionState = SessionState.ACTIVE)
        database.progressDao().insert(progress)
        database.listeningSessionDao().insert(active)

        RecoverInterruptedSession(database.listeningSessionDao(), database.progressDao()).invoke()

        val recovered = database.listeningSessionDao().getById(active.id)
        assertNotNull(recovered)
        assertEquals(SessionState.INTERRUPTED, recovered?.sessionState)
        assertEquals(SessionEndReason.INTERRUPTED, recovered?.endReason)
        assertEquals(1234L, recovered?.endedAt)
    }

    private fun audioFile(order: Int) = AudioFileEntity(editionId = edition.id, fileUri = "content://file/$order", relativePath = "$order.m4b", fileName = "$order.m4b", orderIndex = order, durationMs = 100, fileSizeBytes = 10, lastModified = 1, contentFingerprint = "10:1:$order", mimeType = "audio/mp4", fileStatus = FileStatus.AVAILABLE)

    private fun chapter(order: Int) = ChapterEntity(editionId = edition.id, title = "Chapter $order", startPositionMs = order * 100L, orderIndex = order, createdFrom = ChapterCreatedFrom.IMPORTED)
}