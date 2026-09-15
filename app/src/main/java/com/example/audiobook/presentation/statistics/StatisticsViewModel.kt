package com.example.audiobook.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.repository.DateRange
import com.example.audiobook.data.repository.StatisticsRepository
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.domain.statistics.Achievements
import com.example.audiobook.domain.statistics.BookListeningStat
import com.example.audiobook.domain.statistics.HabitStat
import com.example.audiobook.domain.statistics.InProgressStat
import com.example.audiobook.domain.statistics.ListeningBar
import com.example.audiobook.domain.statistics.PeriodOverview
import com.example.audiobook.domain.statistics.SpeedBucket
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** بند عرض سطر إنجاز: تسمية + قيمة محسوبة حقيقيًا. */
data class AchievementRow(val label: String, val value: String)

/**
 * حالة شاشة الإحصائيات الكاملة — كل قيمة محسوبة فعليًا من [StatisticsRepository]
 * (لا أرقام تجريبية ولا عروض وهمية).
 */
data class StatisticsUiState(
    val selectedRange: DateRange = DateRange.WEEK,
    val overview: PeriodOverview = PeriodOverview(),
    val bars: List<ListeningBar> = emptyList(),
    val topBooks: List<BookListeningStat> = emptyList(),
    val inProgress: List<InProgressStat> = emptyList(),
    val achievements: List<AchievementRow> = emptyList(),
    val habits: List<HabitStat> = emptyList(),
    val favoriteTime: String? = null,
    val averageSpeed: Float = 0f,
    val speedBuckets: List<SpeedBucket> = emptyList(),
    val speedSampleCount: Int = 0,
    val history: List<ListeningHistoryRow> = emptyList()
) {
    val periodLabel: String
        get() = when (selectedRange) {
            DateRange.TODAY -> "اليوم"
            DateRange.WEEK -> "هذا الأسبوع"
            DateRange.MONTH -> "هذا الشهر"
            DateRange.YEAR -> "هذا العام"
            DateRange.ALL -> "كل الأوقات"
        }
}

/** حالة شاشة السجل: آخر الجلسات بترتيب زمني. */
data class HistoryUiState(val sessions: List<ListeningHistoryRow> = emptyList())

/**
 * [R4-النقطة 3] ViewModel مخصص يربط شاشة Statistics بالدوال الحقيقية
 * في [StatisticsRepository] (ملخصات الفترات، الأعمدة، الأكثر استماعًا،
 * إنجازات، عادات، سرعة، والسجل).
 */
@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val statisticsRepository: StatisticsRepository
) : ViewModel() {
    private val _statistics = MutableStateFlow(StatisticsUiState())
    val statistics: StateFlow<StatisticsUiState> = _statistics.asStateFlow()

    private val _history = MutableStateFlow(HistoryUiState())
    val history: StateFlow<HistoryUiState> = _history.asStateFlow()

    init {
        refresh()
    }

    fun selectRange(range: DateRange) {
        refresh(range)
    }

    fun refresh() {
        refresh(_statistics.value.selectedRange)
    }

    private fun refresh(range: DateRange) {
        viewModelScope.launch {
            val overview = statisticsRepository.periodOverview(range)
            val habits = statisticsRepository.listeningHabits()
            val speedSampleCount = statisticsRepository.speedSampleCount()
            val maxHabit = habits.maxByOrNull { it.listenedMs }
            _statistics.value = StatisticsUiState(
                selectedRange = range,
                overview = overview,
                bars = statisticsRepository.listeningBars(range),
                topBooks = statisticsRepository.topListenedBooks(range, limit = 6),
                inProgress = statisticsRepository.inProgressBooks(),
                achievements = buildAchievementRows(statisticsRepository.achievements()),
                habits = habits,
                favoriteTime = maxHabit?.takeIf { it.listenedMs > 0L }?.label,
                averageSpeed = statisticsRepository.averageSpeed(),
                speedBuckets = statisticsRepository.speedBuckets(),
                speedSampleCount = speedSampleCount,
                history = statisticsRepository.history()
            )
        }
        viewModelScope.launch {
            _history.value = HistoryUiState(statisticsRepository.history())
        }
    }

    private fun buildAchievementRows(achievements: Achievements): List<AchievementRow> {
        val rows = mutableListOf<AchievementRow>()
        achievements.firstCompletedBookTitle?.let { rows += AchievementRow("أول كتاب أكملته", it) }
        if (achievements.longestSessionMs > 0L) {
            val title = achievements.longestSessionBookTitle ?: "جلسة"
            rows += AchievementRow("أطول جلسة", "$title · ${formatDuration(achievements.longestSessionMs)}")
        }
        achievements.mostListenedDayLabel?.let {
            rows += AchievementRow("أكثر يوم استمعت فيه", "$it · ${formatDuration(achievements.mostListenedDayMs)}")
        }
        achievements.topSeries?.let {
            rows += AchievementRow("سلسلة قطعت فيها شوطًا", "${it.seriesName} · ${it.listenedBooks} من ${it.totalBooks}")
        }
        return rows
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = ms / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return if (hours > 0L) "${hours} س ${minutes} د" else "${minutes} د"
    }
}