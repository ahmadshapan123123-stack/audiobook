package com.example.audiobook.presentation.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {

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

    private fun seed(context: Context) {
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
            }
        }
        seedBook(root.id, author.id, "ما وراء الطبيعة")
        seedBook(root.id, author.id, "موسم الهجرة إلى الشمال")
    }

    private fun seedBook(rootId: UUID, authorId: UUID, title: String) {
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = title,
            authorId = authorId,
            seriesId = null,
            orderInSeries = null,
            genre = if (title.contains("الطبيعة")) "خيال" else "رواية",
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
            totalDurationMs = 3_600_000,
            fileFormat = "M4B",
            libraryRootId = rootId,
            sourceFolderPath = "/$title",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        kotlinx.coroutines.runBlocking {
            withContext(Dispatchers.IO) {
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
            }
        }
    }

    @Test
    fun libraryShowsRealBooksFromRoomWithAuthorNames() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seed(context)

        val viewModel = LibraryViewModel(
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            favoriteBookDao = database.favoriteBookDao(),
            collectionDao = database.collectionDao(),
            crossRefDao = database.collectionBookCrossRefDao()
        )

        val state = viewModel.uiState.first { it.books.size == 2 }
        assertEquals(2, state.books.size)
        assertTrue(state.books.any { it.book.title == "ما وراء الطبيعة" && it.authorName == "أحمد خالد توفيق" })
        assertTrue(state.books.any { it.book.title == "موسم الهجرة إلى الشمال" })
    }

    @Test
    fun arabicSearchFiltersRealBooksByNormalizedTitle() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        seed(context)

        val viewModel = LibraryViewModel(
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            favoriteBookDao = database.favoriteBookDao(),
            collectionDao = database.collectionDao(),
            crossRefDao = database.collectionBookCrossRefDao()
        )

        val state = viewModel.uiState.first { it.books.size == 2 }
        viewModel.updateSearch("ماوراء الطبيعه")
        val filtered = viewModel.uiState.first { it.query.search == "ماوراء الطبيعه" }.filtered
        assertEquals(listOf("ما وراء الطبيعة"), filtered.map { it.book.title })
    }
}