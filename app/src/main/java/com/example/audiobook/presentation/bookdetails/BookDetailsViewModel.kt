package com.example.audiobook.presentation.bookdetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.BookmarkDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.domain.usecases.BookDetailsManagement
import com.example.audiobook.domain.usecases.CoverCandidate
import com.example.audiobook.domain.usecases.CoverPolicy
import com.example.audiobook.domain.usecases.EditionManagementState
import com.example.audiobook.domain.usecases.EditionMerge
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookDetailsUiState(
    val book: BookEntity? = null,
    val authorName: String = "",
    val authorId: UUID? = null,
    val seriesName: String? = null,
    val editions: List<EditionEntity> = emptyList(),
    val defaultEditionId: UUID? = null,
    val chapters: List<ChapterEntity> = emptyList(),
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val progress: ListeningProgressEntity? = null
)

@HiltViewModel
class BookDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val editionDao: EditionDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ProgressDao,
    private val editionMerge: EditionMerge
) : ViewModel() {

    private val manager = BookDetailsManagement()
    private val bookId: UUID = UUID.fromString(
        savedStateHandle.get<String>("bookId") ?: throw IllegalArgumentException("bookId navigation argument missing")
    )

    private val bookFlow = bookDao.observeById(bookId)
    private val editionsFlow = editionDao.observeByParent(bookId)

    private val defaultEditionFlow = combine(bookFlow, editionsFlow) { book, editions ->
        book?.defaultEditionId ?: editions.firstOrNull()?.id
    }

    private val progressFlow = defaultEditionFlow.flatMapLatest { defaultId ->
        val editionId = defaultId ?: return@flatMapLatest flowOf(null)
        progressDao.observeByParent(editionId)
    }

    val uiState: StateFlow<BookDetailsUiState> = combine(
        bookFlow,
        authorDao.observeAll(),
        seriesDao.observeAll(),
        editionsFlow,
        chapterDao.observeByParent(bookId),
        bookmarkDao.observeByParent(bookId),
        defaultEditionFlow,
        progressFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val book = values[0] as? BookEntity
        val authors = values[1] as List<AuthorEntity>
        val series = values[2] as List<SeriesEntity>
        val editions = values[3] as List<EditionEntity>
        val chapters = values[4] as List<ChapterEntity>
        val bookmarks = values[5] as List<BookmarkEntity>
        val defaultId = values[6] as UUID?
        val progress = values[7] as ListeningProgressEntity?
        BookDetailsUiState(
            book = book,
            authorName = book?.let { authors.firstOrNull { a -> a.id == it.authorId }?.name } ?: "",
            authorId = book?.authorId,
            seriesName = book?.seriesId?.let { sid -> series.firstOrNull { it.id == sid }?.name },
            editions = editions,
            defaultEditionId = defaultId,
            chapters = chapters.sortedBy { it.startPositionMs },
            bookmarks = bookmarks,
            progress = progress
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookDetailsUiState())

    fun updateTitle(book: BookEntity, title: String) {
        viewModelScope.launch { bookDao.update(manager.updateMetadata(book, title, book.genre, book.seriesId)) }
    }

    fun updateGenre(book: BookEntity, genre: String?) {
        viewModelScope.launch { bookDao.update(manager.updateMetadata(book, book.title, genre, book.seriesId)) }
    }

    fun updateAuthor(book: BookEntity, authorName: String) {
        viewModelScope.launch {
            val trimmed = authorName.trim()
            if (trimmed.isBlank()) return@launch
            val existing = authorDao.getByName(trimmed)
            val targetId = existing?.id ?: run {
                val created = AuthorEntity(name = trimmed, colorTheme = null)
                authorDao.insert(created)
                created.id
            }
            if (targetId != book.authorId) bookDao.update(book.copy(authorId = targetId))
        }
    }

    fun setUserCover(book: BookEntity, path: String) {
        viewModelScope.launch { bookDao.update(CoverPolicy.userSelected(book, path)) }
    }

    fun rediscoverCover(book: BookEntity, candidates: List<CoverCandidate>) {
        viewModelScope.launch { bookDao.update(CoverPolicy.choose(book, candidates)) }
    }

    fun renameEdition(edition: EditionEntity, label: String) {
        viewModelScope.launch { editionDao.update(manager.renameEdition(edition, label)) }
    }

    fun changeNarrator(edition: EditionEntity, narrator: String?) {
        viewModelScope.launch { editionDao.update(manager.changeNarrator(edition, narrator)) }
    }

    fun setDefaultEdition(editionId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            bookDao.update(book.copy(defaultEditionId = editionId))
        }
    }

    fun deleteEdition(edition: EditionEntity) {
        viewModelScope.launch { editionDao.delete(edition) }
    }

    fun splitEdition(edition: EditionEntity) {
        viewModelScope.launch {
            val split = manager.splitEdition(EditionManagementState(listOf(edition), edition.id), edition.id)
                .editions.lastOrNull() ?: return@launch
            editionDao.insert(split)
        }
    }

    fun mergeEditions(subjectId: UUID, comparedId: UUID) {
        viewModelScope.launch { editionMerge.merge(subjectId, comparedId, userInitiated = true) }
    }

    /** Reset Metadata: ترفع حماية User Override Wins على الكتاب وكل إصداراته ليعيد الفحص الاكتشاف. */
    fun resetMetadata() {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            bookDao.update(manager.resetMetadata(book))
            editionDao.getByParent(bookId).forEach { editionDao.update(manager.resetEditionMetadata(it)) }
        }
    }
}
