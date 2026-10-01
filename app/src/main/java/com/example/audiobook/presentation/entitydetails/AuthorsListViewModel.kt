package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.domain.usecases.LibraryManagement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** صف مؤلف في قائمة "عرض الكل" — مؤلف حقيقي مع عدد كتبه وسلاسله وتقدّمه. */
data class AuthorListRow(
    val author: AuthorEntity,
    val bookCount: Int,
    val seriesCount: Int,
    val coverColor: Long,
    val progressFraction: Float,
    val hasProgress: Boolean
)

data class AuthorsListUiState(
    val rows: List<AuthorListRow> = emptyList()
)

@HiltViewModel
class AuthorsListViewModel @Inject constructor(
    private val authorDao: AuthorDao,
    private val bookDao: BookDao,
    private val seriesDao: SeriesDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    val uiState: StateFlow<AuthorsListUiState> = combine(
        authorDao.observeAll(),
        bookDao.observeAll(),
        seriesDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val authors = values[0] as List<AuthorEntity>
        val books = values[1] as List<BookEntity>
        val allSeries = values[2] as List<com.example.audiobook.data.room.entity.SeriesEntity>
        val editions = values[3] as List<com.example.audiobook.data.room.entity.EditionEntity>
        val progressList = values[4] as List<com.example.audiobook.data.room.entity.ListeningProgressEntity>
        val booksByAuthor = books.groupBy { it.authorId }
        val seriesCountByAuthor = allSeries.groupingBy { it.authorId }.eachCount()
        val progressByEdition = progressList.associateBy { it.editionId }
        val editionsByBook = editions.groupBy { it.bookId }
        AuthorsListUiState(
            rows = authors.map { author ->
                val authorBooks = booksByAuthor[author.id].orEmpty()
                val bestFraction = authorBooks.mapNotNull { book ->
                    editionsByBook[book.id]?.mapNotNull { edition ->
                        val progress = progressByEdition[edition.id] ?: return@mapNotNull null
                        if (progress.currentPositionMs <= 0L || edition.totalDurationMs <= 0L) null
                        else (progress.currentPositionMs.toFloat() / edition.totalDurationMs).coerceIn(0f, 1f)
                    }?.maxOrNull()
                }.maxOrNull() ?: 0f
                AuthorListRow(
                    author = author,
                    bookCount = authorBooks.size,
                    seriesCount = seriesCountByAuthor[author.id] ?: 0,
                    coverColor = parseColor(author.colorTheme, 0xFF6D28D9),
                    progressFraction = bestFraction,
                    hasProgress = bestFraction > 0f
                )
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthorsListUiState())

    fun renameAuthor(authorId: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            authorDao.getById(authorId)?.let { authorDao.update(it.copy(name = trimmed)) }
        }
    }

    fun deleteAuthor(authorId: UUID) {
        viewModelScope.launch {
            management.deleteAuthor(authorId)
        }
    }
}