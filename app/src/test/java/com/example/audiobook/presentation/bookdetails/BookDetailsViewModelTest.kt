package com.example.audiobook.presentation.bookdetails

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.domain.usecases.EditionMerge
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BookDetailsViewModelTest {

    private lateinit var database: AppDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun seedBook(root: LibraryRootEntity, author: AuthorEntity, title: String): Pair<BookEntity, EditionEntity> {
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = title,
            authorId = author.id,
            seriesId = null,
            orderInSeries = null,
            genre = "رواية",
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val edition = EditionEntity(
            id = UUID.randomUUID(),
            bookId = book.id,
            narratorName = "راوٍ",
            label = "إصدار $title",
            totalDurationMs = 10_000,
            fileFormat = "M4B",
            libraryRootId = root.id,
            sourceFolderPath = "/$title",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        runBlockingIO {
            database.bookDao().insert(book)
            database.editionDao().insert(edition)
        }
        return book to edition
    }

    @Test
    fun openingSecondBookShowsSecondBookDataNotFirstOrMock() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "مؤلف", colorTheme = null)
        runBlockingIO {
            database.libraryRootDao().insert(root)
            database.authorDao().insert(author)
        }
        val (firstBook, _) = seedBook(root, author, "الكتاب الأول")
        val (secondBook, _) = seedBook(root, author, "الكتاب الثاني")

        assertEquals("الكتاب الأول", firstBook.title)
        assertEquals("الكتاب الثاني", secondBook.title)

        val viewModel = BookDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to secondBook.id.toString())),
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            editionDao = database.editionDao(),
            chapterDao = database.chapterDao(),
            bookmarkDao = database.bookmarkDao(),
            progressDao = database.progressDao(),
            editionMerge = EditionMerge(database)
        )

        val state = viewModel.uiState.first { it.book != null }
        assertNotNull(state.book)
        assertEquals("الكتاب الثاني", state.book?.title)
        assertEquals("مؤلف", state.authorName)
        assertEquals("رواية", state.book?.genre)
    }

    @Test
    fun editingTitlePersistsToDatabase() = runTest(dispatcher) {
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "مؤلف", colorTheme = null)
        runBlockingIO {
            database.libraryRootDao().insert(root)
            database.authorDao().insert(author)
        }
        val (book, _) = seedBook(root, author, "عنوان أصلي")

        val viewModel = BookDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to book.id.toString())),
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            editionDao = database.editionDao(),
            chapterDao = database.chapterDao(),
            bookmarkDao = database.bookmarkDao(),
            progressDao = database.progressDao(),
            editionMerge = EditionMerge(database)
        )
        viewModel.updateTitle(book, "عنوان معدل")

        val state = viewModel.uiState.first { it.book?.title == "عنوان معدل" }
        assertEquals("عنوان معدل", database.bookDao().getById(book.id)?.title)
        assertNotNull(state.book)
    }

    private fun runBlockingIO(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) { block() }
        }
    }
}
