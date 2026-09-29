package com.example.audiobook.presentation.entitydetails

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.libraryManagementFor
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FIX 5.1 — بطاقات السلاسل في صفحة المؤلف: تُشتق من جدول السلاسل
 * (authorId == X) لا من روابط الكتب — فسلسلة فارغة الرابط تظهر بعدّ صفر
 * بدل أن تختفي، والكتب المستقلة تبقى في قسمها.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthorSeriesCardsTest {

    private lateinit var database: AppDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun seriesCardsComeFromSeriesTableWithCounts() = runTest(dispatcher) {
        val author = AuthorEntity(name = "A", colorTheme = null).also { database.authorDao().insert(it) }
        val s1 = SeriesEntity(authorId = author.id, name = "S1", colorTheme = null).also { database.seriesDao().insert(it) }
        val s2 = SeriesEntity(authorId = author.id, name = "S2", colorTheme = null).also { database.seriesDao().insert(it) }
        // كتاب في S1 + كتاب مستقل — S2 فارغة الرابط عمدًا.
        database.bookDao().insert(
            BookEntity(
                id = UUID.randomUUID(), title = "B1", authorId = author.id, seriesId = s1.id,
                orderInSeries = 1, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER,
                isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        database.bookDao().insert(
            BookEntity(
                id = UUID.randomUUID(), title = "B0", authorId = author.id, seriesId = null,
                orderInSeries = null, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER,
                isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )

        val viewModel = AuthorDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to author.id.toString())),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            bookDao = database.bookDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            management = libraryManagementFor(database)
        )
        val state = viewModel.uiState.first { it.author != null }

        assertEquals("بطاقتان (إحداهما فارغة الرابط)", 2, state.seriesCards.size)
        assertEquals("S1", state.seriesCards[0].seriesName)
        assertEquals(1, state.seriesCards[0].bookCount)
        assertEquals("S2", state.seriesCards[1].seriesName)
        assertEquals(0, state.seriesCards[1].bookCount)
        val standalone = state.groups.filter { it.seriesId == null }.flatMap { it.books }
        assertEquals("كتاب مستقل واحد", 1, standalone.size)
        assertEquals("B0", standalone.first().title)
    }
}
