package com.example.audiobook.presentation.statistics

import com.example.audiobook.data.repository.DateRange
import com.example.audiobook.data.repository.StatisticsRepository
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.domain.statistics.Achievements
import com.example.audiobook.domain.statistics.BookListeningStat
import com.example.audiobook.domain.statistics.HabitStat
import com.example.audiobook.domain.statistics.InProgressStat
import com.example.audiobook.domain.statistics.ListeningBar
import com.example.audiobook.domain.statistics.PeriodOverview
import com.example.audiobook.domain.statistics.SeriesAchievement
import com.example.audiobook.domain.statistics.SpeedBucket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [R4-النقطة 3] يثبت أن ViewModel المخصص يربط شاشة Statistics بالقيم الحقيقية
 * من [StatisticsRepository] (ملخص الفترة، الأكثر استماعًا، تقدّمك، الإنجازات، العادات).
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
        assertEquals(DateRange.WEEK, state.selectedRange)
        assertEquals(10_000L, state.overview.listenedMs)
        assertEquals(4_000L, state.overview.previousPeriodMs)
        assertEquals(5, state.overview.sessionsCount)
        assertEquals(2, state.overview.booksCount)
        assertEquals(7, state.overview.chaptersCount)
        assertEquals(3, state.overview.daysCount)
        assertEquals(2, state.bars.size)
        assertEquals("السبت", state.bars[0].label)
        assertEquals(listOf("كتاب أول", "كتاب ثانٍ"), state.topBooks.map { it.title })
        assertEquals(listOf("تقدّم"), state.inProgress.map { it.title })
        assertEquals(listOf("أول كتاب أكملته", "أكثر يوم استمعت فيه", "سلسلة قطعت فيها شوطًا"), state.achievements.map { it.label })
        assertEquals(1.5f, state.averageSpeed, 0.0001f)
        assertEquals("ليلًا", state.favoriteTime)
    }

    @Test
    fun selectRangeReloadsOverviewForThatPeriod() = runTest(mainDispatcher) {
        val repository = FakeStatisticsRepository()
        val viewModel = StatisticsViewModel(repository)
        advanceUntilIdle()

        viewModel.selectRange(DateRange.ALL)
        advanceUntilIdle()

        assertEquals(DateRange.ALL, viewModel.statistics.value.selectedRange)
        assertEquals(99_000L, viewModel.statistics.value.overview.listenedMs)
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
            DateRange.WEEK -> 10_000L
            DateRange.MONTH -> 77_000L
            DateRange.YEAR -> 88_000L
            DateRange.ALL -> 99_000L
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

        override suspend fun periodOverview(range: DateRange): PeriodOverview = PeriodOverview(
            range = range,
            listenedMs = if (range == DateRange.ALL) 99_000L else 10_000L,
            previousPeriodMs = 4_000L,
            sessionsCount = 5,
            booksCount = 2,
            chaptersCount = 7,
            daysCount = 3
        )

        override suspend fun listeningBars(range: DateRange): List<ListeningBar> = listOf(
            ListeningBar("السبت", 6_000L),
            ListeningBar("الأحد", 4_000L)
        )

        override suspend fun topListenedBooks(range: DateRange, limit: Int): List<BookListeningStat> = listOf(
            BookListeningStat(UUID.randomUUID(), UUID.randomUUID(), "كتاب أول", "مؤلف", "سلسلة", null, 3),
            BookListeningStat(UUID.randomUUID(), UUID.randomUUID(), "كتاب ثانٍ", "مؤلف", "سلسلة", null, 1)
        )

        override suspend fun inProgressBooks(): List<InProgressStat> = listOf(
            InProgressStat(
                editionId = UUID.randomUUID(), bookId = UUID.randomUUID(), title = "تقدّم",
                authorName = null, seriesName = null, coverColorTheme = null,
                totalDurationMs = 10_000L, currentPositionMs = 5_000L
            )
        )

        override suspend fun achievements(): Achievements = Achievements(
            firstCompletedBookTitle = "أول كتاب",
            longestSessionBookTitle = "أطول جلسة",
            mostListenedDayLabel = "السبت",
            mostListenedDayMs = 6_000L,
            topSeries = SeriesAchievement("سلسلة", 7, 18)
        )

        override suspend fun listeningHabits(): List<HabitStat> = listOf(
            HabitStat("صباحًا", 1_000L),
            HabitStat("ظهرًا", 2_000L),
            HabitStat("مساءً", 3_000L),
            HabitStat("ليلًا", 4_000L)
        )

        override suspend fun speedBuckets(): List<SpeedBucket> = listOf(
            SpeedBucket("1×", 1), SpeedBucket("1.25×", 2), SpeedBucket("1.5×", 3)
        )

        override suspend fun speedSampleCount(): Int = 6
    }
}