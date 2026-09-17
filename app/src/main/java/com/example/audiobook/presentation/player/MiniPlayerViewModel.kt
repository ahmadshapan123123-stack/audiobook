package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.presentation.theme.Cosmic
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

data class MiniPlayerUiState(
    val title: String = "",
    val authorName: String = "",
    val seriesColor: Color? = null,
    val authorColor: Color? = null,
    val coverColor: Color = Cosmic.Teal,
    val isLoading: Boolean = true
)

private fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (e: IllegalArgumentException) { null }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(
    private val editionDao: EditionDao,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao
) : ViewModel() {

    private val _editionId = MutableStateFlow<UUID?>(null)

    fun observeEdition(id: UUID?) { _editionId.value = id }

    val uiState: StateFlow<MiniPlayerUiState> = _editionId.flatMapLatest { id ->
        if (id == null) {
            flowOf(MiniPlayerUiState(isLoading = false))
        } else {
            combine(
                editionDao.observeById(id),
                bookDao.observeAll(),
                authorDao.observeAll(),
                seriesDao.observeAll()
            ) { edition, books, authors, series ->
                val book = edition?.let { e -> books.firstOrNull { b -> b.id == e.bookId } }
                val author = book?.let { b -> authors.firstOrNull { a -> a.id == b.authorId } }
                val bookSeries = book?.seriesId?.let { sid -> series.firstOrNull { it.id == sid } }
                MiniPlayerUiState(
                    title = book?.title ?: "",
                    authorName = author?.name ?: "",
                    seriesColor = parseColor(bookSeries?.colorTheme),
                    authorColor = parseColor(author?.colorTheme),
                    coverColor = book?.let { deterministicColor(it.id) } ?: Cosmic.Teal,
                    isLoading = false
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MiniPlayerUiState())

    private fun deterministicColor(id: UUID): Color {
        val hue = (id.hashCode() and 0xFF) / 255f
        return Color.hsv(hue * 360f, 0.45f, 0.55f)
    }
}
