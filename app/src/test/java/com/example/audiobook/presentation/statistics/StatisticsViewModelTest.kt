package com.example.audiobook.presentation.statistics

import com.example.audiobook.data.repository.DateRange
import com.example.audiobook.data.repository.StatisticsRepository
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * [R4-النقطة 3] يثبت أن ViewModel المخصص يربط شاشتي Statistics وHistory
 * بالقيم الحقيقية من [StatisticsRepository] (لا دوال ميتة ولا عرض وهمي).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(mainDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun mapsRepositoryValuesIntoStatisticsUiState() = runTest(mainDispatcher) {
        val repository = FakeStatisticsRepository()
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        val state = viewModel.statistics.value
        assertEquals(15_000L, state.listeningTodayMs)
        assertEquals(17_000L, state.listeningWeekMs)
        assertEquals(77_000L, state.listeningMonthMs)
        assertEquals(2, state.completedBooks)
        assertEquals(3, state.completedChapters)
        assertEquals(4, state.currentStreak)
        assertEquals(1.5f, state.averageSpeed, 0.0001f)
    }

    @Test
    fun mapsRepositoryHistoryIntoHistoryUiState() = runTest(mainDispatcher) {
        val repository = FakeStatisticsRepository()
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        val sessions = viewModel.history.value.sessions
        assertEquals(2, sessions.size)
        assertEquals("كتاب أول", sessions.first().bookTitle)
        assertEquals("كتاب ثانٍ", sessions.last().bookTitle)
    }

    private class FakeStatisticsRepository : StatisticsRepository {
        override suspend fun completedSessions(): List<ListeningSessionEntity> = emptyList()

        override suspend fun listeningTimeForRange(range: DateRange): Long = when (range) {
            DateRange.TODAY -> 15_000L
            DateRange.WEEK -> 17_000L
            DateRange.MONTH -> 77_000L
        }

        override suspend fun completedBooksCount(): Int = 2
        override suspend fun completedChaptersCount(): Int = 3
        override suspend fun currentStreak(): Int = 4
        override suspend fun averageSpeed(): Float = 1.5f

        override suspend fun history(): List<ListeningHistoryRow> = listOf(
            ListeningHistoryRow(
                sessionId = UUID.randomUUID(), bookTitle = "كتاب أول", editionLabel = null,
                startedAt = 1_000L, durationListenedMs = 10_000L, endReason = SessionEndReason.MANUAL_PAUSE
            ),
            ListeningHistoryRow(
                sessionId = UUID.randomUUID(), bookTitle = "كتاب ثانٍ", editionLabel = null,
                startedAt = 900L, durationListenedMs = 5_000L, endReason = SessionEndReason.SLEEP_TIMER
            )
        )
    }
}