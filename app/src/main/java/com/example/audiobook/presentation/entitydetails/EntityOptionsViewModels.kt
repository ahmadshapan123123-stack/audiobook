package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.R
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
import com.example.audiobook.presentation.common.OpMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** حالة ورقة خيارات المؤلف: الكيان نفسه + كتبه + مرشّحات الإجراءات. */
data class AuthorOptionsUiState(
    val entity: AuthorEntity? = null,
    val books: List<EntityBookRow> = emptyList(),
    val otherAuthors: List<AuthorEntity> = emptyList(),
    val candidateBooks: List<EntityBookRow> = emptyList()
)

/** حالة ورقة خيارات السلسلة. */
data class SeriesOptionsUiState(
    val entity: SeriesEntity? = null,
    val authorName: String = "",
    val books: List<EntityBookRow> = emptyList(),
    val allSeries: List<SeriesEntity> = emptyList(),
    val candidateBooks: List<EntityBookRow> = emptyList()
)

/** حالة ورقة خيارات المجموعة. */
data class CollectionOptionsUiState(
    val entity: CollectionEntity? = null,
    val members: List<EntityBookRow> = emptyList(),
    val candidateBooks: List<EntityBookRow> = emptyList()
)

/**
 * ورقة خيارات المؤلف: تُفتح من الضغطة المطوّلة على أي بطاقة مؤلف (الرئيسية /
 * قائمة المؤلفين)، وتقدّم نفس إجراءات صفحة التفاصيل دون الحاجة لفتحها.
 */
@HiltViewModel
class AuthorOptionsViewModel @Inject constructor(
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val selectedId = MutableStateFlow<UUID?>(null)

    private sealed interface AuthorSheetUndo {
        data class Deleted(val snapshot: LibraryManagement.AuthorSnapshot) : AuthorSheetUndo
        data class Merged(val snapshot: LibraryManagement.AuthorMergeSnapshot) : AuthorSheetUndo
    }

    private val pendingUndo = MutableStateFlow<AuthorSheetUndo?>(null)
    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun openOptions(authorId: UUID) {
        selectedId.value = authorId
    }

    fun closeOptions() {
        selectedId.value = null
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val uiState: StateFlow<AuthorOptionsUiState?> = selectedId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(
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

                val author = authors.firstOrNull { it.id == id } ?: return@combine null
                val idBooks = books.filter { it.authorId == id }
                val others = books.filter { it.authorId != id }
                AuthorOptionsUiState(
                    entity = author,
                    books = buildEntityBookRows(
                        books = idBooks, authors = authors, editions = editions,
                        progressList = progressList, series = allSeries
                    ),
                    otherAuthors = authors.filter { it.id != id },
                    candidateBooks = buildEntityBookRows(
                        books = others, authors = authors, editions = editions,
                        progressList = progressList, series = allSeries
                    ).sortedBy { it.title }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveEntity(name: String, description: String, imagePath: String?) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            authorDao.getById(id)?.let { authorDao.update(it.copy(name = name, description = description, imagePath = imagePath)) }
        }
    }

    fun addBookToAuthor(bookId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == id) return@launch
            management.moveBookToAuthor(bookId, id)
        }
    }

    fun moveBookToOtherAuthor(bookId: UUID, targetId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == targetId) return@launch
            management.moveBookToAuthor(bookId, targetId)
        }
    }

    fun mergeAuthors(targetId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            if (targetId == id) return@launch
            val snapshot = management.mergeAuthors(id, targetId)
            pendingUndo.value = AuthorSheetUndo.Merged(snapshot)
            _messages.value = OpMessage(R.string.author_merged_undo)
        }
    }

    fun deleteAuthor() {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val snapshot = management.snapshotAuthor(id)
            management.deleteAuthor(id)
            pendingUndo.value = AuthorSheetUndo.Deleted(snapshot)
            selectedId.value = null
            _messages.value = OpMessage(R.string.author_deleted_undo)
        }
    }

    fun undo() {
        val action = pendingUndo.value ?: return
        viewModelScope.launch {
            when (action) {
                is AuthorSheetUndo.Deleted -> management.restoreAuthor(action.snapshot)
                is AuthorSheetUndo.Merged -> management.undoMergeAuthors(action.snapshot)
            }
            pendingUndo.value = null
            _messages.value = null
        }
    }
}

/** ورقة خيارات السلسلة: نفس إجراءات صفحة التفاصيل عبر ضغطة مطوّلة. */
@HiltViewModel
class SeriesOptionsViewModel @Inject constructor(
    private val seriesDao: SeriesDao,
    private val authorDao: AuthorDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val selectedId = MutableStateFlow<UUID?>(null)

    private sealed interface SeriesSheetUndo {
        data class Deleted(val snapshot: LibraryManagement.SeriesSnapshot) : SeriesSheetUndo
        data class Merged(val snapshot: LibraryManagement.SeriesMergeSnapshot) : SeriesSheetUndo
    }

    private val pendingUndo = MutableStateFlow<SeriesSheetUndo?>(null)
    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun openOptions(seriesId: UUID) {
        selectedId.value = seriesId
    }

    fun closeOptions() {
        selectedId.value = null
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val uiState: StateFlow<SeriesOptionsUiState?> = selectedId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(
                seriesDao.observeAll(),
                authorDao.observeAll(),
                bookDao.observeAll(),
                editionDao.observeAll(),
                progressDao.observeAll()
            ) { values ->
                @Suppress("UNCHECKED_CAST")
                val seriesList = values[0] as List<SeriesEntity>
                val authors = values[1] as List<AuthorEntity>
                val books = values[2] as List<BookEntity>
                val editions = values[3] as List<EditionEntity>
                val progressList = values[4] as List<ListeningProgressEntity>

                val series = seriesList.firstOrNull { it.id == id } ?: return@combine null
                val seriesBooks = books.filter { it.seriesId == id }
                val others = books.filter { it.seriesId != id }
                SeriesOptionsUiState(
                    entity = series,
                    authorName = authors.firstOrNull { it.id == series.authorId }?.name.orEmpty(),
                    books = buildEntityBookRows(
                        books = seriesBooks, authors = authors, editions = editions,
                        progressList = progressList, series = seriesList
                    ).sortedBy { it.orderInSeries ?: Int.MAX_VALUE },
                    allSeries = seriesList.filter { it.id != id },
                    candidateBooks = buildEntityBookRows(
                        books = others, authors = authors, editions = editions,
                        progressList = progressList, series = seriesList
                    ).sortedBy { it.title }
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveEntity(name: String, description: String, imagePath: String?) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            seriesDao.getById(id)?.let { seriesDao.update(it.copy(name = name, description = description, imagePath = imagePath)) }
        }
    }

    fun addBookToSeries(bookId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.seriesId == id) return@launch
            val nextOrder = (uiState.value?.books?.maxOfOrNull { it.orderInSeries ?: 0 } ?: 0) + 1
            bookDao.update(book.copy(seriesId = id, orderInSeries = nextOrder))
        }
    }

    fun applyBookOrder(orderedIds: List<UUID>) {
        viewModelScope.launch {
            val id = selectedId.value ?: return@launch
            management.reorderBooksInSeries(id, orderedIds)
        }
    }

    fun mergeSeries(targetId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            if (targetId == id) return@launch
            val snapshot = management.mergeSeries(id, targetId)
            pendingUndo.value = SeriesSheetUndo.Merged(snapshot)
            _messages.value = OpMessage(R.string.series_merged_undo)
        }
    }

    fun deleteSeries() {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val snapshot = management.snapshotSeries(id)
            management.deleteSeries(id)
            pendingUndo.value = SeriesSheetUndo.Deleted(snapshot)
            selectedId.value = null
            _messages.value = OpMessage(R.string.series_deleted_undo)
        }
    }

    fun undo() {
        val action = pendingUndo.value ?: return
        viewModelScope.launch {
            when (action) {
                is SeriesSheetUndo.Deleted -> management.restoreSeries(action.snapshot)
                is SeriesSheetUndo.Merged -> management.undoMergeSeries(action.snapshot)
            }
            pendingUndo.value = null
            _messages.value = null
        }
    }
}

/** ورقة خيارات المجموعة: تعديل الاسم + إضافة/إزالة كتب + حذف. */
@HiltViewModel
class CollectionOptionsViewModel @Inject constructor(
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val selectedId = MutableStateFlow<UUID?>(null)

    private sealed interface CollectionSheetUndo {
        data class Deleted(val snapshot: LibraryManagement.CollectionSnapshot) : CollectionSheetUndo
        data class Removed(val bookId: UUID) : CollectionSheetUndo
    }

    private val pendingUndo = MutableStateFlow<CollectionSheetUndo?>(null)
    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun openOptions(collectionId: UUID) {
        selectedId.value = collectionId
    }

    fun closeOptions() {
        selectedId.value = null
    }

    fun consumeMessage() {
        _messages.value = null
    }

    val uiState: StateFlow<CollectionOptionsUiState?> = selectedId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(
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

                val collection = collections.firstOrNull { it.id == id } ?: return@combine null
                val memberIds = crossRefs
                    .filter { it.collectionId == id }
                    .mapTo(HashSet()) { it.bookId }
                val members = buildEntityBookRows(
                    books = books.filter { it.id in memberIds }, authors = authors,
                    editions = editions, progressList = progressList, series = allSeries
                ).sortedBy { it.title }
                val candidates = buildEntityBookRows(
                    books = books.filter { it.id !in memberIds }, authors = authors,
                    editions = editions, progressList = progressList, series = allSeries
                ).sortedBy { it.title }
                CollectionOptionsUiState(
                    entity = collection,
                    members = members,
                    candidateBooks = candidates
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun updateName(name: String) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            management.updateCollectionName(id, name)
        }
    }

    fun addBookToCollection(bookId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            management.addBookToCollection(id, bookId)
            _messages.value = OpMessage(R.string.collection_added_done, undolable = false)
        }
    }

    fun removeBookFromCollection(bookId: UUID) {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            management.removeBookFromCollection(id, bookId)
            pendingUndo.value = CollectionSheetUndo.Removed(bookId)
            _messages.value = OpMessage(R.string.collection_removed_done)
        }
    }

    fun deleteCollection() {
        val id = selectedId.value ?: return
        viewModelScope.launch {
            val snapshot = management.snapshotCollection(id)
            management.deleteCollection(id)
            pendingUndo.value = CollectionSheetUndo.Deleted(snapshot)
            selectedId.value = null
            _messages.value = OpMessage(R.string.collection_deleted_undo)
        }
    }

    fun undo() {
        val action = pendingUndo.value ?: return
        val id = selectedId.value
        viewModelScope.launch {
            when (action) {
                is CollectionSheetUndo.Deleted -> management.restoreCollection(action.snapshot)
                is CollectionSheetUndo.Removed -> if (id != null) management.addBookToCollection(id, action.bookId)
            }
            pendingUndo.value = null
            _messages.value = null
        }
    }
}