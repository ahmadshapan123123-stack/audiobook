package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.CollectionBookCrossRefDao
import com.example.audiobook.data.room.dao.CollectionDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CollectionBookCrossRef
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.domain.usecases.LibraryManagement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CollectionDetailsUiState(
    val collection: CollectionEntity? = null,
    val books: List<EntityBookRow> = emptyList(),
    val coverColor: Long = 0xFF356B68,
    val allBooks: List<BookEntity> = emptyList()
)

@HiltViewModel
class CollectionDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val collectionId: UUID = UUID.fromString(
        savedStateHandle.get<String>("id") ?: throw IllegalArgumentException("collection id navigation argument missing")
    )

    val uiState: StateFlow<CollectionDetailsUiState> = combine(
        collectionDao.observeAll(),
        crossRefDao.observeAll(),
        authorDao.observeAll(),
        seriesDao.observeAll(),
        bookDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val collections = values[0] as List<CollectionEntity>
        val crossRefs = values[1] as List<CollectionBookCrossRef>
        val authors = values[2] as List<AuthorEntity>
        val allSeries = values[3] as List<SeriesEntity>
        val books = values[4] as List<BookEntity>
        val editions = values[5] as List<EditionEntity>
        val progressList = values[6] as List<ListeningProgressEntity>

        val collection = collections.firstOrNull { it.id == collectionId }
        val memberIds = crossRefs
            .filter { it.collectionId == collectionId }
            .mapTo(HashSet()) { it.bookId }
        val rows = buildEntityBookRows(
            books = books.filter { it.id in memberIds },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        ).sortedBy { it.title }

        CollectionDetailsUiState(
            collection = collection,
            books = rows,
            coverColor = parseColor(null, 0xFF356B68),
            allBooks = books
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollectionDetailsUiState())

    fun updateCollectionName(name: String) {
        viewModelScope.launch {
            management.updateCollectionName(collectionId, name)
        }
    }

    fun deleteCollection(onDone: () -> Unit) {
        viewModelScope.launch {
            management.deleteCollection(collectionId)
            onDone()
        }
    }

    fun addBookToCollection(bookId: UUID) {
        viewModelScope.launch {
            management.addBookToCollection(collectionId, bookId)
        }
    }

    fun removeBookFromCollection(bookId: UUID) {
        viewModelScope.launch {
            management.removeBookFromCollection(collectionId, bookId)
        }
    }
}