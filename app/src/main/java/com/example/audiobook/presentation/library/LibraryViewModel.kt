package com.example.audiobook.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.CollectionBookCrossRefDao
import com.example.audiobook.data.room.dao.CollectionDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.FavoriteBookDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.CollectionBookCrossRef
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FavoriteBook
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryBookUi(
    val book: BookEntity,
    val authorName: String,
    val seriesName: String?,
    val progressFraction: Float,
    val remainingMs: Long,
    val isFavorite: Boolean,
    val addedOrder: Int,
    val lastPlayedAt: Long
)

data class LibraryUiState(
    val books: List<LibraryBookUi> = emptyList(),
    val query: LibraryQuery = LibraryQuery(),
    val collections: List<CollectionEntity> = emptyList()
) {
    val filtered: List<LibraryBookUi>
        get() {
            val base = when (query.status) {
                LibraryStatusFilter.ALL -> books
                LibraryStatusFilter.IN_PROGRESS -> books.filter { it.progressFraction in 0.01f..0.99f }
                LibraryStatusFilter.FINISHED -> books.filter { it.progressFraction >= 1f }
                LibraryStatusFilter.NOT_STARTED -> books.filter { it.progressFraction <= 0f }
            }
            val searchFiltered = base.filter { book ->
                ArabicSearchNormalizer.matches(query.search, book.book.title, book.authorName, book.seriesName)
            }
            val genreFiltered = searchFiltered.filter { query.genre == null || it.book.genre == query.genre }
            return genreFiltered.sortedWith(
                when (query.sort) {
                    LibrarySort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, { it.book.title })
                    LibrarySort.ADDED_DATE -> compareByDescending { it.addedOrder }
                    LibrarySort.LAST_PLAYED -> compareByDescending { it.lastPlayedAt }
                    LibrarySort.PROGRESS -> compareByDescending { it.progressFraction }
                }
            )
        }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val favoriteBookDao: FavoriteBookDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())

    val uiState: StateFlow<LibraryUiState> = combine(
        bookDao.observeAll(),
        authorDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll(),
        favoriteBookDao.observeAll(),
        collectionDao.observeAll(),
        query
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val books = values[0] as List<BookEntity>
        val authors = values[1] as List<AuthorEntity>
        val editions = values[2] as List<EditionEntity>
        val progressList = values[3] as List<ListeningProgressEntity>
        val favorites = values[4] as List<FavoriteBook>
        val collections = values[5] as List<CollectionEntity>
        val q = values[6] as LibraryQuery
        val authorName = { id: UUID -> authors.firstOrNull { it.id == id }?.name ?: "" }
        val favoriteIds = favorites.mapTo(HashSet()) { it.bookId }
        val progressById = progressList.associateBy { it.editionId }
        val byAddedIndex = books.withIndex().associate { it.value.id to it.index }

        val mapped = books.map { book ->
            val edition = effectiveEdition(book, editions, progressById)
            val progress = edition?.let { progressById[it.id] }
            val played = progress?.currentPositionMs ?: 0L
            val total = edition?.totalDurationMs ?: 0L
            val fraction = if (total > 0L) (played.toFloat() / total).coerceIn(0f, 1f) else 0f
            LibraryBookUi(
                book = book,
                authorName = authorName(book.authorId),
                seriesName = null,
                progressFraction = fraction,
                remainingMs = (total - played).coerceAtLeast(0L),
                isFavorite = book.id in favoriteIds,
                addedOrder = byAddedIndex[book.id] ?: 0,
                lastPlayedAt = progress?.lastPlayedAt ?: 0L
            )
        }
        LibraryUiState(books = mapped, query = q, collections = collections)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun updateQuery(query: LibraryQuery) {
        this.query.value = query
    }

    fun updateSearch(search: String) {
        this.query.value = this.query.value.copy(search = search)
    }

    fun updateSort(sort: LibrarySort) {
        this.query.value = this.query.value.copy(sort = sort)
    }

    fun updateStatus(status: LibraryStatusFilter) {
        this.query.value = this.query.value.copy(status = status)
    }

    fun updateGenre(genre: String?) {
        this.query.value = this.query.value.copy(genre = genre)
    }

    fun toggleFavorite(bookId: UUID) {
        viewModelScope.launch {
            if (favoriteBookDao.getById(bookId) != null) favoriteBookDao.delete(bookId)
            else favoriteBookDao.insert(FavoriteBook(bookId, System.currentTimeMillis()))
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            if (collectionDao.getByName(trimmed) == null) {
                collectionDao.insert(CollectionEntity(name = trimmed, icon = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
            }
        }
    }

    fun addBookToCollection(collectionName: String, bookId: UUID) {
        viewModelScope.launch {
            val collection = collectionDao.getByName(collectionName) ?: return@launch
            if (crossRefDao.getById(collection.id, bookId) == null) {
                crossRefDao.insert(CollectionBookCrossRef(collection.id, bookId))
            }
        }
    }

    private fun effectiveEdition(
        book: BookEntity,
        editions: List<EditionEntity>,
        progress: Map<UUID, ListeningProgressEntity>
    ): EditionEntity? {
        val bookEditions = editions.filter { it.bookId == book.id }
        book.defaultEditionId?.let { id -> return bookEditions.firstOrNull { it.id == id } }
        return bookEditions.firstOrNull { progress[it.id]?.status == ProgressStatus.IN_PROGRESS }
            ?: bookEditions.firstOrNull()
    }
}
