package com.example.audiobook.presentation.common

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.R
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.room.dao.AudioFileDao
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.CollectionBookCrossRefDao
import com.example.audiobook.data.room.dao.CollectionDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CollectionBookCrossRef
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.domain.usecases.LibraryManagement
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** بيئة العرض لقائمة خيارات الكتاب: الأقسام المتاحة للاختيار في النوافذ الفرعية. */
data class BookManagerCatalog(
    val authors: List<AuthorEntity> = emptyList(),
    val series: List<com.example.audiobook.data.room.entity.SeriesEntity> = emptyList(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionMembers: Map<UUID, Set<UUID>> = emptyMap(),
    val allBooks: List<BookEntity> = emptyList()
) {
    fun memberIds(collectionId: UUID): Set<UUID> = collectionMembers[collectionId] ?: emptySet()
}

/** سياق الكتاب المفتوح في القائمة: بيانات ثابتة لحظة فتحها + نسخه. */
data class BookOptionsContext(
    val bookId: UUID,
    val title: String,
    val authorId: UUID?,
    val authorName: String,
    val seriesId: UUID?,
    val seriesName: String?,
    val editions: List<EditionEntity>,
    val defaultEditionId: UUID?
)

/** رسالة سناكبار تعرض في المضيف مع زر تراجع اختياري. */
data class OpMessage(
    @StringRes val messageRes: Int,
    val args: List<Any> = emptyList(),
    val undolable: Boolean = true
)

/** أثر قابل للتراجع: عمليّة واحدة تكفي لعكسها لاحقًا. */
sealed interface BookUndo {
    data class Deleted(val snapshot: LibraryManagement.DeletedBooksSnapshot) : BookUndo
    data class Merged(val snapshot: LibraryManagement.MergeBooksSnapshot) : BookUndo
    data class MovedSingle(val book: BookEntity, val createdSeriesId: UUID? = null) : BookUndo
    data class MovedBulk(val books: List<BookEntity>) : BookUndo
    data class AddedFile(val editionId: UUID, val before: LibraryManagement.EditionSnapshot) : BookUndo
}

@HiltViewModel
class BookManagerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val management: LibraryManagement,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val editionDao: EditionDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val audioFileDao: AudioFileDao,
    private val chapterDao: ChapterDao,
    private val audioMetadataReader: AudioMetadataReader
) : ViewModel() {

    private val selectedBookId = MutableStateFlow<UUID?>(null)
    private val pendingUndo = MutableStateFlow<BookUndo?>(null)

    private val catalogFlow: Flow<BookManagerCatalog> = combine(
        authorDao.observeAll(),
        seriesDao.observeAll(),
        collectionDao.observeAll(),
        crossRefDao.observeAll(),
        bookDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val authors = values[0] as List<AuthorEntity>
        val series = values[1] as List<com.example.audiobook.data.room.entity.SeriesEntity>
        val collections = values[2] as List<CollectionEntity>
        val refs = values[3] as List<CollectionBookCrossRef>
        val allBooks = values[4] as List<BookEntity>
        val members = collections.associate { collection ->
            collection.id to refs.filter { it.collectionId == collection.id }.mapTo(HashSet()) { it.bookId }
        }
        BookManagerCatalog(authors, series, collections, members, allBooks)
    }

    val catalog: StateFlow<BookManagerCatalog> = catalogFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookManagerCatalog())

    val context: StateFlow<BookOptionsContext?> = selectedBookId.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(bookDao.observeById(id), editionDao.observeByParent(id), catalogFlow) { book, editions, cat ->
                if (book == null) return@combine null
                BookOptionsContext(
                    bookId = book.id,
                    title = book.title,
                    authorId = book.authorId,
                    authorName = cat.authors.firstOrNull { it.id == book.authorId }?.name.orEmpty(),
                    seriesId = book.seriesId,
                    seriesName = book.seriesId?.let { sid -> cat.series.firstOrNull { it.id == sid }?.name },
                    editions = editions,
                    defaultEditionId = book.defaultEditionId
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun openOptions(bookId: UUID) {
        selectedBookId.value = bookId
    }

    fun closeOptions() {
        selectedBookId.value = null
    }

    fun consumeMessage() {
        _messages.value = null
    }

    // ── تنفيذ العمليات ──

    fun moveBookToAuthor(bookId: UUID, authorId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == authorId) return@launch
            pendingUndo.value = BookUndo.MovedSingle(book)
            management.moveBookToAuthor(bookId, authorId)
            _messages.value = OpMessage(R.string.move_book_undo)
        }
    }

    fun createAuthorAndMove(bookId: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val author = management.getOrCreateAuthor(trimmed)
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == author.id) return@launch
            pendingUndo.value = BookUndo.MovedSingle(book)
            management.moveBookToAuthor(bookId, author.id)
            _messages.value = OpMessage(R.string.move_book_undo)
        }
    }

    fun moveBookToSeries(bookId: UUID, seriesId: UUID?) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.seriesId == seriesId) return@launch
            pendingUndo.value = BookUndo.MovedSingle(book)
            management.moveBookToSeries(bookId, seriesId)
            _messages.value = OpMessage(R.string.move_book_undo)
        }
    }

    fun createSeriesAndMove(bookId: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val book = bookDao.getById(bookId) ?: return@launch
            val targetAuthorId = book.authorId
                ?: run {
                    _messages.value = OpMessage(R.string.move_book_series_requires_author)
                    return@launch
                }
            val created = com.example.audiobook.data.room.entity.SeriesEntity(
                id = UUID.randomUUID(),
                authorId = targetAuthorId,
                name = trimmed,
                colorTheme = null
            )
            seriesDao.insert(created)
            pendingUndo.value = BookUndo.MovedSingle(book, createdSeriesId = created.id)
            management.moveBookToSeries(bookId, created.id)
            _messages.value = OpMessage(R.string.move_book_undo)
        }
    }

    fun addBookToCollections(bookId: UUID, collectionIds: List<UUID>) {
        viewModelScope.launch {
            if (collectionIds.isEmpty()) return@launch
            for (collectionId in collectionIds) {
                management.addBookToCollection(collectionId, bookId)
            }
            _messages.value = OpMessage(R.string.collection_added_undo, undolable = false)
        }
    }

    fun createCollectionAndAdd(bookId: UUID, name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val collection = management.getOrCreateCollection(trimmed)
            management.addBookToCollection(collection.id, bookId)
            _messages.value = OpMessage(R.string.collection_added_undo, undolable = false)
        }
    }

    fun noEditionMessage() {
        _messages.value = OpMessage(R.string.add_file_no_edition, undolable = false)
    }

    fun setDefaultEdition(bookId: UUID, editionId: UUID) {
        viewModelScope.launch {
            management.setDefaultEdition(bookId, editionId)
            _messages.value = OpMessage(R.string.set_default_edition_done, undolable = false)
        }
    }

    fun removeBookFromSeries(bookId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.seriesId == null) return@launch
            pendingUndo.value = BookUndo.MovedSingle(book)
            management.removeBookFromSeries(bookId)
            _messages.value = OpMessage(R.string.remove_from_series_done)
        }
    }

    fun deleteBook(bookId: UUID) {
        viewModelScope.launch {
            val snapshot = management.snapshotBook(bookId) ?: return@launch
            management.deleteBook(bookId)
            pendingUndo.value = BookUndo.Deleted(LibraryManagement.DeletedBooksSnapshot(listOf(snapshot)))
            _messages.value = OpMessage(R.string.delete_book_undo)
        }
    }

    fun mergeBookInto(bookId: UUID, targetBookId: UUID) {
        viewModelScope.launch {
            val result = management.mergeBooks(bookId, targetBookId) ?: return@launch
            pendingUndo.value = BookUndo.Merged(result)
            selectedBookId.value = null
            _messages.value = OpMessage(R.string.merge_book_undo)
        }
    }

    fun addAudioFile(opts: BookOptionsContext, editionId: UUID, uri: Uri) {
        viewModelScope.launch {
            // FIX-URI: إذن دائم للقراءة — بدونه يموت الـURI بعد إعادة التشغيل
            // فيصبح الملف المضاف غير قابل للتشغيل. try/catch لبعض الموفرين.
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val edition = editionDao.getById(editionId)
            if (edition == null || edition.bookId != opts.bookId) {
                _messages.value = OpMessage(R.string.add_file_no_edition, undolable = false)
                return@launch
            }
            val file = withContext(Dispatchers.IO) { readPickedFile(uri) }
            if (file == null) {
                _messages.value = OpMessage(R.string.add_file_failed, undolable = false)
                return@launch
            }
            val metadata = withContext(Dispatchers.IO) { audioMetadataReader.read(uri, file.name) }
            val before = management.snapshotEdition(editionId)
            val imported = management.addAudioFileToEdition(
                editionId = editionId,
                uri = uri,
                fileName = file.name,
                fileSizeBytes = file.size,
                lastModified = file.modified,
                metadata = metadata
            )
            pendingUndo.value = BookUndo.AddedFile(editionId, before)
            _messages.value = if (imported > 0) {
                OpMessage(R.string.add_file_chapters_imported, listOf(imported), undolable = false)
            } else {
                OpMessage(R.string.add_file_added, undolable = false)
            }
        }
    }

    // ── التحديد المتعدد ──

    fun bulkMoveToAuthor(bookIds: List<UUID>, authorId: UUID) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val before = bookIds.mapNotNull { bookDao.getById(it) }
            management.moveBooksToAuthor(bookIds, authorId)
            pendingUndo.value = BookUndo.MovedBulk(before)
            _messages.value = OpMessage(R.string.bulk_done)
        }
    }

    fun bulkMoveToSeries(bookIds: List<UUID>, seriesId: UUID?) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val before = bookIds.mapNotNull { bookDao.getById(it) }
            management.moveBooksToSeries(bookIds, seriesId)
            pendingUndo.value = BookUndo.MovedBulk(before)
            _messages.value = OpMessage(R.string.bulk_done)
        }
    }

    fun bulkCreateAuthorAndMove(bookIds: List<UUID>, name: String) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val author = management.getOrCreateAuthor(trimmed)
            val before = bookIds.mapNotNull { bookDao.getById(it) }
            management.moveBooksToAuthor(bookIds, author.id)
            pendingUndo.value = BookUndo.MovedBulk(before)
            _messages.value = OpMessage(R.string.bulk_done)
        }
    }

    fun bulkCreateSeriesAndMove(bookIds: List<UUID>, name: String) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val trimmed = name.trim()
            if (trimmed.isBlank() || bookIds.isEmpty()) return@launch
            val firstBook = bookDao.getById(bookIds.first()) ?: return@launch
            val targetAuthorId = firstBook.authorId
                ?: run {
                    _messages.value = OpMessage(R.string.move_book_series_requires_author)
                    return@launch
                }
            val created = com.example.audiobook.data.room.entity.SeriesEntity(
                id = UUID.randomUUID(),
                authorId = targetAuthorId,
                name = trimmed,
                colorTheme = null
            )
            seriesDao.insert(created)
            val before = bookIds.mapNotNull { bookDao.getById(it) }
            management.moveBooksToSeries(bookIds, created.id)
            pendingUndo.value = BookUndo.MovedBulk(before)
            _messages.value = OpMessage(R.string.bulk_done)
        }
    }

    fun bulkCreateCollectionAndAdd(bookIds: List<UUID>, name: String) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            val collection = management.getOrCreateCollection(trimmed)
            management.addBooksToCollection(collection.id, bookIds)
            _messages.value = OpMessage(R.string.collection_added_undo, undolable = false)
        }
    }

    fun bulkAddToCollection(collectionId: UUID, bookIds: List<UUID>) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            management.addBooksToCollection(collectionId, bookIds)
            _messages.value = OpMessage(R.string.collection_added_undo, undolable = false)
        }
    }

    fun bulkSetFavorite(bookIds: List<UUID>, favorite: Boolean) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            management.setBooksFavorite(bookIds, favorite)
            _messages.value = OpMessage(R.string.bulk_done, undolable = false)
        }
    }

    fun bulkDelete(bookIds: List<UUID>) {
        viewModelScope.launch {
            if (bookIds.isEmpty()) return@launch
            val snapshot = management.deleteBooks(bookIds)
            if (snapshot.books.isNotEmpty()) {
                pendingUndo.value = BookUndo.Deleted(snapshot)
                _messages.value = OpMessage(R.string.bulk_done)
            }
        }
    }

    // ── التراجع ──

    fun undo() {
        val action = pendingUndo.value ?: return
        viewModelScope.launch {
            when (action) {
                is BookUndo.Deleted -> management.restoreBooks(action.snapshot)
                is BookUndo.Merged -> management.undoMergeBooks(action.snapshot)
                is BookUndo.MovedSingle -> {
                    bookDao.update(action.book)
                    action.createdSeriesId?.let { seriesId ->
                        if (bookDao.observeAll().first().none { it.seriesId == seriesId }) {
                            seriesDao.getById(seriesId)?.let { seriesDao.delete(it) }
                        }
                    }
                }
                is BookUndo.MovedBulk -> action.books.forEach { bookDao.update(it) }
                is BookUndo.AddedFile -> {
                    val files = audioFileDao.getByParent(action.editionId).drop(action.before.audioFiles.size)
                    files.forEach { audioFileDao.delete(it) }
                    val chapters = chapterDao.getByParent(action.editionId).drop(action.before.chapters.size)
                    chapters.forEach { chapterDao.delete(it) }
                    editionDao.update(action.before.edition)
                }
            }
            pendingUndo.value = null
            _messages.value = OpMessage(R.string.book_restored, undolable = false)
        }
    }

    companion object {
        /** ما يعرض في قائمة الدمج لاختيار الأهداف. */
        fun mergeTargets(catalog: BookManagerCatalog, excludeBookId: UUID): List<Pair<UUID, String>> {
            val authorName = { id: UUID? -> id?.let { catalog.authors.firstOrNull { a -> a.id == it }?.name }.orEmpty() }
            return catalog.allBooks
                .filter { it.id != excludeBookId }
                .map { it.id to if (authorName(it.authorId).isNotBlank()) "${it.title} — ${authorName(it.authorId)}" else it.title }
                .sortedBy { it.second.lowercase() }
        }
    }

    private data class PickedFile(val name: String, val size: Long, val modified: Long)

    private fun readPickedFile(uri: Uri): PickedFile? = runCatching {
        var name = uri.lastPathSegment ?: "audio"
        var size = 0L
        var modified = System.currentTimeMillis()
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
            }
        }
        if (uri.scheme == "content") {
            runCatching {
                appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val modifiedIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                        if (modifiedIdx >= 0 && !cursor.isNull(modifiedIdx)) modified = cursor.getLong(modifiedIdx)
                    }
                }
            }
        }
        PickedFile(name, size, modified)
    }.getOrNull()
}