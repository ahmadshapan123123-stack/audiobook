package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.domain.usecases.LibraryManagement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** صف سلسلة في قائمة "عرض الكل" — سلسلة حقيقية مع مؤلفها وعدد كتبها ولونها. */
data class SeriesListRow(
    val series: SeriesEntity,
    val authorName: String,
    val bookCount: Int,
    val coverColor: Long
)

data class SeriesListUiState(
    val rows: List<SeriesListRow> = emptyList()
)

@HiltViewModel
class SeriesListViewModel @Inject constructor(
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val management: LibraryManagement
) : ViewModel() {

    val uiState: StateFlow<SeriesListUiState> = combine(
        authorDao.observeAll(),
        seriesDao.observeAll(),
        bookDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val authors = values[0] as List<AuthorEntity>
        val series = values[1] as List<SeriesEntity>
        val books = values[2] as List<BookEntity>
        val counts = books.groupingBy { it.seriesId }.eachCount()
        val authorById = authors.associateBy { it.id }
        SeriesListUiState(
            rows = series.map { s ->
                SeriesListRow(
                    series = s,
                    authorName = authorById[s.authorId]?.name ?: "",
                    bookCount = counts[s.id] ?: 0,
                    coverColor = parseColor(s.colorTheme, 0xFF2563EB)
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesListUiState())

    fun renameSeries(seriesId: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            seriesDao.getById(seriesId)?.let { seriesDao.update(it.copy(name = trimmed)) }
        }
    }

    fun deleteSeries(seriesId: UUID) {
        viewModelScope.launch {
            management.deleteSeries(seriesId)
        }
    }
}