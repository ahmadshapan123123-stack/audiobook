package com.example.audiobook.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * خيارات المدة المتاحة في مركز الاستماع: كتب متناهية الصغر ثم ممتدة.
 */
internal val HUB_TIME_OPTIONS_MINUTES = listOf(15, 30, 45, 60, 90, 120)

/** الحد الأقصى لما تعتبره "جلسة نوم": 40 دقيقة. */
private const val HUB_BEDTIME_MAX_REMAINING_MS = 40 * 60_000L

/** الحد الأدنى لمدة الكتاب حتى يصلح قسم "جلسة طويلة": ساعتان. */
private const val HUB_LONG_BOOK_MIN_MS = 2 * 60 * 60_000L

/** حالة مركز الاستماع: كل أقسامه مبنية من بيانات المكتبة الفعلية لا تخمين. */
data class ListeningHubUiState(
    val selectedMinutes: Int = 30,
    val featured: HomeContinue? = null,
    val fitsWindow: List<HomeBook> = emptyList(),
    val bedtime: List<HomeBook> = emptyList(),
    val series: List<HomeSeries> = emptyList(),
    val longSessions: List<HomeBook> = emptyList(),
    val totalBooks: Int = 0,
    val isLoading: Boolean = true
)

/** مصادر مركز الاستماع المنزوعة الأنواع من دالة الجمع. */
private data class HubSources(
    val minutes: Int,
    val books: List<BookEntity>,
    val authors: List<AuthorEntity>,
    val editions: List<EditionEntity>,
    val progressList: List<ListeningProgressEntity>,
    val series: List<SeriesEntity>,
    val chapters: List<ChapterEntity>
)

/** [استمع الآن] — أثير يجهّز لك جلسة بحسب الوقت المتاح من بيانات حقيقية. */
@HiltViewModel
class ListeningHubViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val seriesDao: SeriesDao,
    private val chapterDao: ChapterDao
) : ViewModel() {

    private val selectedMinutes = MutableStateFlow(HUB_TIME_OPTIONS_MINUTES[1])

    fun selectTime(minutes: Int) {
        if (minutes in HUB_TIME_OPTIONS_MINUTES) selectedMinutes.value = minutes
    }

    @Suppress("UNCHECKED_CAST")
    private fun extract(values: Array<Any>): HubSources = HubSources(
        minutes = values[0] as Int,
        books = values[1] as List<BookEntity>,
        authors = values[2] as List<AuthorEntity>,
        editions = values[3] as List<EditionEntity>,
        progressList = values[4] as List<ListeningProgressEntity>,
        series = values[5] as List<SeriesEntity>,
        chapters = values[6] as List<ChapterEntity>
    )

    val uiState: StateFlow<ListeningHubUiState> = combine(
        selectedMinutes,
        bookDao.observeAll(),
        authorDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll(),
        seriesDao.observeAll(),
        chapterDao.observeAll()
    ) { values ->
        val src = extract(values)
        val minutes = src.minutes
        val books = src.books
        val authors = src.authors
        val editions = src.editions
        val progressList = src.progressList
        val series = src.series
        val chapters = src.chapters
        val authorById = authors.associateBy { it.id }
        val homeBooks = HomeMapper.toHomeBooks(books, authors, editions, progressList, series)
        val homeById = homeBooks.associateBy { it.bookId }
        val editionById = editions.associateBy { it.id }
        val windowMs = minutes * 60_000L

        fun totalMs(book: HomeBook): Long = book.editionId?.let { editionById[it]?.totalDurationMs } ?: 0L

        val inProgress = homeBooks
            .filter { it.hasProgress && it.editionId != null && it.remainingMs > 0L }
            .sortedByDescending { it.lastPlayedAt }

        val continuing = progressList
            .filter { it.status == ProgressStatus.IN_PROGRESS }
            .maxByOrNull { it.lastPlayedAt }

        val featured = continuing?.let { progress ->
            val edition = editions.firstOrNull { it.id == progress.editionId }
            val book = edition?.let { homeById[it.bookId] } ?: return@let null
            HomeContinue(
                book = book,
                totalMs = edition.totalDurationMs,
                playedMs = progress.currentPositionMs,
                currentChapterTitle = HomeMapper.chapterTitleAt(chapters, edition.id, progress.currentPositionMs)
            )
        }

        val featuredId = featured?.book?.bookId

        val fitsWindow = inProgress
            .filter { it.bookId != featuredId && it.remainingMs <= windowMs }
            .sortedBy { it.remainingMs }
            .take(8)

        val bedtime = inProgress
            .filter { it.bookId != featuredId && it.remainingMs <= HUB_BEDTIME_MAX_REMAINING_MS }
            .take(3)

        val seriesSection = series
            .map { s ->
                val members = homeBooks.filter { it.seriesId == s.id }
                HomeSeries(
                    seriesId = s.id,
                    name = s.name,
                    authorName = authorById[s.authorId]?.name ?: "",
                    colorTheme = s.colorTheme,
                    books = members
                )
            }
            .filter { s -> s.books.any { it.hasProgress } }
            .sortedByDescending { s -> s.books.maxOf { it.lastPlayedAt } }

        val longSessions = homeBooks
            .filter { it.editionId != null && it.progressFraction < 1f && totalMs(it) >= HUB_LONG_BOOK_MIN_MS }
            .sortedByDescending { it.hasProgress }
            .sortedByDescending { totalMs(it) }
            .take(6)

        ListeningHubUiState(
            selectedMinutes = minutes,
            featured = featured,
            fitsWindow = fitsWindow,
            bedtime = bedtime,
            series = seriesSection,
            longSessions = longSessions,
            totalBooks = books.size,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningHubUiState())
}