package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlayerEditionUiState(
    val edition: EditionEntity? = null,
    val book: BookEntity? = null,
    val title: String = "",
    val authorName: String = "",
    val seriesColor: Color? = null,
    val authorColor: Color? = null,
    val coverColor: Color? = null
)

private fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (e: IllegalArgumentException) { null }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editionDao: EditionDao,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao
) : ViewModel() {
    private val editionId: UUID = UUID.fromString(
        savedStateHandle.get<String>("editionId") ?: throw IllegalArgumentException("editionId navigation argument missing")
    )

    private val editionFlow = editionDao.observeById(editionId)

    private val bookFlow = editionFlow.let { ef ->
        combine(ef, bookDao.observeAll()) { edition, books ->
            edition?.let { books.firstOrNull { b -> b.id == it.bookId } }
        }
    }

    val uiState: StateFlow<PlayerEditionUiState> = combine(
        editionFlow,
        bookFlow,
        authorDao.observeAll(),
        seriesDao.observeAll()
    ) { edition, book, authors, series ->
        val author = book?.let { authors.firstOrNull { a -> a.id == it.authorId } }
        val seriesColor = book?.seriesId?.let { sid -> series.firstOrNull { it.id == sid }?.colorTheme }
        PlayerEditionUiState(
            edition = edition,
            book = book,
            title = book?.title ?: "",
            authorName = author?.name ?: "",
            seriesColor = parseColor(seriesColor),
            authorColor = parseColor(author?.colorTheme),
            coverColor = book?.let { deterministicColor(it.id) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerEditionUiState())

    private fun deterministicColor(id: UUID): Color {
        val hue = (id.hashCode() and 0xFF) / 255f
        return Color.hsv(hue * 360f, 0.45f, 0.55f)
    }
}
