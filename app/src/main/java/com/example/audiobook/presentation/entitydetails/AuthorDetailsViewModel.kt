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

/** مجموعة كتب داخل صفحة المؤلف: سلسلة (أو null للكتب المستقلة). */
data class AuthorBookGroup(
    val seriesId: UUID?,
    val seriesName: String?,
    val seriesColorTheme: String?,
    val books: List<EntityBookRow>
)

data class AuthorDetailsUiState(
    val author: AuthorEntity? = null,
    val groups: List<AuthorBookGroup> = emptyList(),
    val totalBooks: Int = 0,
    val coverColor: Long = 0xFF356B68
)

@HiltViewModel
class AuthorDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao
) : ViewModel() {

    private val authorId: UUID = UUID.fromString(
        savedStateHandle.get<String>("id") ?: throw IllegalArgumentException("author id navigation argument missing")
    )

    val uiState: StateFlow<AuthorDetailsUiState> = combine(
        authorDao.observeAll(),
        seriesDao.observeAll(),
        bookDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val authors = values[0] as List<AuthorEntity>
        val allSeries = values[1] as List<SeriesEntity>
        val books = values[2] as List<BookEntity>
        val editions = values[3] as List<EditionEntity>
        val progressList = values[4] as List<ListeningProgressEntity>

        val author = authors.firstOrNull { it.id == authorId }
        val rows = buildEntityBookRows(
            books = books.filter { it.authorId == authorId },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        )

        fun sortRows(list: List<EntityBookRow>): List<EntityBookRow> =
            list.sortedWith(compareBy<EntityBookRow> { it.orderInSeries ?: Int.MAX_VALUE }.thenBy { it.title })

        val bySeries = rows.filter { it.seriesId != null }.groupBy { it.seriesId }
        val groups = bySeries.entries
            .map { (sid, list) ->
                AuthorBookGroup(
                    seriesId = sid,
                    seriesName = allSeries.firstOrNull { it.id == sid }?.name,
                    seriesColorTheme = allSeries.firstOrNull { it.id == sid }?.colorTheme,
                    books = sortRows(list)
                )
            }
            .sortedBy { it.seriesName }
        val standalone = rows.filter { it.seriesId == null }
        val allGroups = if (standalone.isEmpty()) groups else groups + AuthorBookGroup(null, null, null, sortRows(standalone))

        AuthorDetailsUiState(
            author = author,
            groups = allGroups,
            totalBooks = rows.size,
            coverColor = parseColor(author?.colorTheme, 0xFF356B68)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthorDetailsUiState())

    fun saveAuthor(name: String, description: String?, imagePath: String?) {
        viewModelScope.launch {
            authorDao.getById(authorId)?.let { current ->
                authorDao.update(current.copy(name = name, description = description, imagePath = imagePath))
            }
        }
    }
}
