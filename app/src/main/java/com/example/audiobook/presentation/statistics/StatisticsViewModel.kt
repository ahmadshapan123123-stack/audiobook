package com.example.audiobook.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.repository.DateRange
import com.example.audiobook.data.repository.StatisticsRepository
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** حالة شاشة الإحصائيات: كل القيم محسوبة فعليًا من [StatisticsRepository]. */
data class StatisticsUiState(
    val listeningTodayMs: Long = 0L,
    val listeningWeekMs: Long = 0L,
    val listeningMonthMs: Long = 0L,
    val completedBooks: Int = 0,
    val completedChapters: Int = 0,
    val currentStreak: Int = 0,
    val averageSpeed: Float = 0f
)

/** حالة شاشة السجل: آخر الجلسات بترتيب زمني. */
data class HistoryUiState(val sessions: List<ListeningHistoryRow> = emptyList())

/**
 * [R4-النقطة 3] ViewModel مخصص يربط شاشتي Statistics وHistory بالدوال
 * الحقيقية في [StatisticsRepository] (وقت النطاقات، كتب/فصول مكتملة،
 * الستريك، متوسط السرعة، وعرض الجلسات).
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

    fun refresh() {
        viewModelScope.launch {
            _statistics.value = StatisticsUiState(
                listeningTodayMs = statisticsRepository.listeningTimeForRange(DateRange.TODAY),
                listeningWeekMs = statisticsRepository.listeningTimeForRange(DateRange.WEEK),
                listeningMonthMs = statisticsRepository.listeningTimeForRange(DateRange.MONTH),
                completedBooks = statisticsRepository.completedBooksCount(),
                completedChapters = statisticsRepository.completedChaptersCount(),
                currentStreak = statisticsRepository.currentStreak(),
                averageSpeed = statisticsRepository.averageSpeed()
            )
        }
        viewModelScope.launch {
            _history.value = HistoryUiState(statisticsRepository.history())
        }
    }
}