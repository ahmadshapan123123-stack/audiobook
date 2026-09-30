package com.example.audiobook.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * خيارات المدة في مركز الاستماع (PART 5 / Phase 5): ثلاث نوافذ + ساعة.
 * "مفتوح" (بلا حد) يُمثَّل بـnull في [ListeningHubUiState.selectedMinutes].
 */
internal val HUB_TIME_OPTIONS_MINUTES = listOf(15, 30, 45, 60)

/** حالة مركز الاستماع: ثلاثة أقسام فقط — وقتك، يناسب وقتك، أكمل ما بدأته. */
data class ListeningHubUiState(
    /** النافذة المختارة بالدقائق؛ null = "مفتوح" (بلا حد زمني). */
    val selectedMinutes: Int? = 30,
    /** كتب قيد التقدّم يقلّ متبقيها عن النافذة (أو الكل إن كانت مفتوحة) — بحد 8. */
    val fitsWindow: List<HomeBook> = emptyList(),
    /** بقية كتب قيد التقدّم غير المعروضة أعلاه — بحد 8. */
    val rest: List<HomeBook> = emptyList(),
    val totalBooks: Int = 0,
    val isLoading: Boolean = true
)

/** مصادر مركز الاستماع المنزوعة الأنواع من دالة الجمع. */
private data class HubSources(
    val minutes: Int?,
    val books: List<BookEntity>,
    val authors: List<AuthorEntity>,
    val editions: List<EditionEntity>,
    val progressList: List<ListeningProgressEntity>,
    /** تُمرَّر للمحوّل لأسماء السلاسل وألوانها فقط — لا قسم سلاسل بعد الآن. */
    val series: List<SeriesEntity>
)

/** [استمع الآن] — ثلاثة أقسام من كتب قيد التقدّم فقط (PART 5 / Phase 5). */
@HiltViewModel
class ListeningHubViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val seriesDao: SeriesDao
) : ViewModel() {

    private val selectedMinutes = MutableStateFlow<Int?>(HUB_TIME_OPTIONS_MINUTES[1])

    /** اختيار النافذة؛ null = "مفتوح". القيم الغريبة تُتجاهل. */
    fun selectTime(minutes: Int?) {
        if (minutes == null || minutes in HUB_TIME_OPTIONS_MINUTES) selectedMinutes.value = minutes
    }

    @Suppress("UNCHECKED_CAST")
    private fun extract(values: Array<Any?>): HubSources = HubSources(
        minutes = values[0] as Int?,
        books = values[1] as List<BookEntity>,
        authors = values[2] as List<AuthorEntity>,
        editions = values[3] as List<EditionEntity>,
        progressList = values[4] as List<ListeningProgressEntity>,
        series = values[5] as List<SeriesEntity>
    )

    val uiState: StateFlow<ListeningHubUiState> = combine(
        selectedMinutes,
        bookDao.observeAll(),
        authorDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll(),
        seriesDao.observeAll()
    ) { values ->
        val src = extract(values)
        val minutes = src.minutes
        val homeBooks = HomeMapper.toHomeBooks(src.books, src.authors, src.editions, src.progressList, src.series)
        val windowMs = minutes?.times(60_000L)

        // كل كتب قيد التقدّم القابلة للتشغيل، بالأحدث استماعًا أولًا.
        val inProgress = homeBooks
            .filter { it.hasProgress && it.editionId != null && it.remainingMs > 0L }
            .sortedByDescending { it.lastPlayedAt }

        // القسم 2 "يناسب وقتك": المتبقي ≤ النافذة (أو الكل في "مفتوح") — بحد 8.
        val fitsWindow = (if (windowMs == null) inProgress
            else inProgress.filter { it.remainingMs <= windowMs })
            .take(8)

        // القسم 3 "أكمل ما بدأته": الباقي غير المعروض أعلاه — بحد 8.
        val shownIds = fitsWindow.map { it.bookId }.toSet()
        val rest = inProgress
            .filter { it.bookId !in shownIds }
            .take(8)

        ListeningHubUiState(
            selectedMinutes = minutes,
            fitsWindow = fitsWindow,
            rest = rest,
            totalBooks = src.books.size,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningHubUiState())
}