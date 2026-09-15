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
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SeriesDetailsUiState(
    val series: SeriesEntity? = null,
    val authorId: UUID? = null,
    val authorName: String = "",
    val books: List<EntityBookRow> = emptyList(),
    val coverColor: Long = 0xFF356B68
)

@HiltViewModel
class SeriesDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val seriesDao: SeriesDao,
    private val authorDao: AuthorDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao
) : ViewModel() {

    private val seriesId: UUID = UUID.fromString(
        savedStateHandle.get<String>("id") ?: throw IllegalArgumentException("series id navigation argument missing")
    )

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

        SeriesDetailsUiState(
            series = series,
            authorId = author?.id,
            authorName = author?.name ?: "",
            books = rows,
            coverColor = parseColor(series?.colorTheme ?: author?.colorTheme, 0xFF356B68)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailsUiState())

    fun saveSeries(name: String, description: String?, imagePath: String?) {
        viewModelScope.launch {
            seriesDao.getById(seriesId)?.let { current ->
                seriesDao.update(current.copy(name = name, description = description, imagePath = imagePath))
            }
        }
    }
}
