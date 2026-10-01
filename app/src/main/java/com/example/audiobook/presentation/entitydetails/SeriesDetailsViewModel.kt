package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.SavedStateHandle
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
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.presentation.common.OpMessage
import com.example.audiobook.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SeriesDetailsUiState(
    val series: SeriesEntity? = null,
    val authorId: UUID? = null,
    val authorName: String = "",
    val books: List<EntityBookRow> = emptyList(),
    /** REDESIGN: مدة كل كتاب (edition duration) + عدد المنتهية + التالية للمتابعة. */
    val durationsMs: Map<UUID, Long> = emptyMap(),
    val finishedCount: Int = 0,
    val continueRow: EntityBookRow? = null,
    val firstEditionId: UUID? = null,
    val candidateBooks: List<EntityBookRow> = emptyList(),
    val coverColor: Long = 0xFF6D28D9,
    val allSeries: List<SeriesEntity> = emptyList()
)

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seriesDao: SeriesDao,
    private val authorDao: AuthorDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val seriesId: UUID = UUID.fromString(
        savedStateHandle.get<String>("id") ?: throw IllegalArgumentException("series id navigation argument missing")
    )

    /** أثر قابل للتراجع ضمن نافذة السناكبار (5 ثوانٍ): حذف أو دمج. */
    private sealed interface SeriesUndo {
        data class Deleted(val snapshot: LibraryManagement.SeriesSnapshot) : SeriesUndo
        data class Merged(val snapshot: LibraryManagement.SeriesMergeSnapshot) : SeriesUndo
    }

    private val pendingUndo = MutableStateFlow<SeriesUndo?>(null)
    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun consumeMessage() {
        _messages.value = null
    }

    val uiState: StateFlow<SeriesDetailsUiState> = combine(
        seriesDao.observeAll(),
        authorDao.observeAll(),
        bookDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allSeries = values[0] as List<SeriesEntity>
        val authors = values[1] as List<AuthorEntity>
        val books = values[2] as List<BookEntity>
        val editions = values[3] as List<EditionEntity>
        val progressList = values[4] as List<ListeningProgressEntity>

        val series = allSeries.firstOrNull { it.id == seriesId }
        val author = series?.let { s -> authors.firstOrNull { it.id == s.authorId } }
        val rows = buildEntityBookRows(
            books = books.filter { it.seriesId == seriesId },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        ).sortedWith(compareBy<EntityBookRow> { it.orderInSeries ?: Int.MAX_VALUE }.thenBy { it.title })
        val candidates = buildEntityBookRows(
            books = books.filter { it.seriesId != seriesId },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        ).sortedBy { it.title }

        SeriesDetailsUiState(
            series = series,
            authorId = author?.id,
            authorName = author?.name ?: "",
            books = rows,
            durationsMs = rows.associate { row ->
                row.bookId to (editions.filter { it.bookId == row.bookId }.firstOrNull()?.totalDurationMs ?: 0L)
            },
            finishedCount = rows.count { row ->
                editions.filter { it.bookId == row.bookId }.any {
                    progressList.firstOrNull { p -> p.editionId == it.id }?.status ==
                        com.example.audiobook.data.room.entity.ProgressStatus.FINISHED
                }
            },
            continueRow = rows.firstOrNull { it.hasProgress },
            firstEditionId = rows.firstNotNullOfOrNull { it.editionId },
            candidateBooks = candidates,
            coverColor = parseColor(series?.colorTheme ?: author?.colorTheme, 0xFF6D28D9),
            allSeries = allSeries.filter { it.id != seriesId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailsUiState())

    fun saveSeries(name: String, description: String?, imagePath: String?) {
        viewModelScope.launch {
            seriesDao.getById(seriesId)?.let { current ->
                seriesDao.update(current.copy(name = name, description = description, imagePath = imagePath))
            }
        }
    }

    fun addBookToSeries(bookId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.seriesId == seriesId) return@launch
            val nextOrder = (uiState.value.books.maxOfOrNull { it.orderInSeries ?: 0 } ?: 0) + 1
            bookDao.update(book.copy(seriesId = seriesId, orderInSeries = nextOrder))
        }
    }

    fun removeBookFromSeries(bookId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.seriesId != seriesId) return@launch
            bookDao.update(book.copy(seriesId = null, orderInSeries = null))
        }
    }

    fun applyBookOrder(bookIds: List<UUID>) {
        viewModelScope.launch {
            management.reorderBooksInSeries(seriesId, bookIds)
        }
    }

    fun deleteSeries() {
        viewModelScope.launch {
            val snapshot = management.snapshotSeries(seriesId)
            management.deleteSeries(seriesId)
            pendingUndo.value = SeriesUndo.Deleted(snapshot)
            _messages.value = OpMessage(R.string.series_deleted_undo)
        }
    }

    fun mergeSeries(targetId: UUID) {
        viewModelScope.launch {
            if (targetId == seriesId) return@launch
            val snapshot = management.mergeSeries(seriesId, targetId)
            pendingUndo.value = SeriesUndo.Merged(snapshot)
            _messages.value = OpMessage(R.string.series_merged_undo)
        }
    }

    fun undo() {
        val action = pendingUndo.value ?: return
        viewModelScope.launch {
            when (action) {
                is SeriesUndo.Deleted -> management.restoreSeries(action.snapshot)
                is SeriesUndo.Merged -> management.undoMergeSeries(action.snapshot)
            }
            pendingUndo.value = null
            _messages.value = null
        }
    }
}
