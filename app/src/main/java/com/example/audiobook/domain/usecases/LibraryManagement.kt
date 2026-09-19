package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject

class LibraryManagement @Inject constructor(
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val audioFileDao: AudioFileDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ProgressDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val favoriteBookDao: FavoriteBookDao,
    private val libraryRootDao: LibraryRootDao,
    private val chapterCompletionDao: ChapterCompletionDao
) {

    // ── Author Operations ──

    data class AuthorSnapshot(
        val author: AuthorEntity,
        val books: List<BookEntity>,
        val series: List<SeriesEntity>
    )

    suspend fun snapshotAuthor(authorId: UUID): AuthorSnapshot {
        val author = authorDao.getById(authorId) ?: throw IllegalStateException("Author not found")
        val books = bookDao.getByParent(authorId)
        val series = seriesDao.getByParent(authorId)
        return AuthorSnapshot(author, books, series)
    }

    suspend fun deleteAuthor(authorId: UUID) {
        val books = bookDao.getByParent(authorId)
        for (book in books) {
            deleteBookCascade(book.id)
        }
        val series = seriesDao.getByParent(authorId)
        for (s in series) {
            seriesDao.delete(s)
        }
        val author = authorDao.getById(authorId) ?: return
        authorDao.delete(author)
    }

    suspend fun restoreAuthor(snapshot: AuthorSnapshot) {
        authorDao.insert(snapshot.author)
        for (s in snapshot.series) {
            seriesDao.insert(s)
        }
        for (book in snapshot.books) {
            bookDao.insert(book)
        }
    }

    data class AuthorMergeSnapshot(
        val sourceAuthor: AuthorEntity,
        val targetAuthor: AuthorEntity,
        val movedBooks: List<BookEntity>,
        val deletedSeries: List<SeriesEntity>
    )

    suspend fun mergeAuthors(sourceId: UUID, targetId: UUID): AuthorMergeSnapshot {
        val source = authorDao.getById(sourceId) ?: throw IllegalStateException("Source author not found")
        val target = authorDao.getById(targetId) ?: throw IllegalStateException("Target author not found")

        val sourceBooks = bookDao.getByParent(sourceId)
        val sourceSeries = seriesDao.getByParent(sourceId)

        for (book in sourceBooks) {
            bookDao.update(book.copy(authorId = targetId))
        }
        for (s in sourceSeries) {
            seriesDao.delete(s)
        }
        authorDao.delete(source)

        return AuthorMergeSnapshot(source, target, sourceBooks, sourceSeries)
    }

    suspend fun undoMergeAuthors(snapshot: AuthorMergeSnapshot) {
        for (s in snapshot.deletedSeries) {
            seriesDao.insert(s)
        }
        for (book in snapshot.movedBooks) {
            bookDao.update(book.copy(authorId = snapshot.sourceAuthor.id))
        }
        authorDao.insert(snapshot.sourceAuthor)
    }

    // ── Series Operations ──

    data class SeriesSnapshot(val series: SeriesEntity, val books: List<BookEntity>)

    suspend fun snapshotSeries(seriesId: UUID): SeriesSnapshot {
        val series = seriesDao.getById(seriesId) ?: throw IllegalStateException("Series not found")
        val books = bookDao.getBySeries(seriesId)
        return SeriesSnapshot(series, books)
    }

    suspend fun deleteSeries(seriesId: UUID) {
        val books = bookDao.getBySeries(seriesId)
        for (book in books) {
            bookDao.update(book.copy(seriesId = null))
        }
        val series = seriesDao.getById(seriesId) ?: return
        seriesDao.delete(series)
    }

    suspend fun restoreSeries(snapshot: SeriesSnapshot) {
        seriesDao.insert(snapshot.series)
        for (book in snapshot.books) {
            bookDao.update(book.copy(seriesId = snapshot.series.id))
        }
    }

    data class SeriesMergeSnapshot(
        val sourceSeries: SeriesEntity,
        val targetSeries: SeriesEntity,
        val movedBooks: List<BookEntity>
    )

    suspend fun mergeSeries(sourceId: UUID, targetId: UUID): SeriesMergeSnapshot {
        val source = seriesDao.getById(sourceId) ?: throw IllegalStateException("Source series not found")
        val target = seriesDao.getById(targetId) ?: throw IllegalStateException("Target series not found")

        val sourceBooks = bookDao.getBySeries(sourceId)
        for (book in sourceBooks) {
            bookDao.update(book.copy(seriesId = targetId))
        }
        seriesDao.delete(source)

        return SeriesMergeSnapshot(source, target, sourceBooks)
    }

    suspend fun undoMergeSeries(snapshot: SeriesMergeSnapshot) {
        for (book in snapshot.movedBooks) {
            bookDao.update(book.copy(seriesId = snapshot.sourceSeries.id))
        }
        seriesDao.insert(snapshot.sourceSeries)
    }

    suspend fun moveBookToSeries(bookId: UUID, seriesId: UUID?) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(seriesId = seriesId))
    }

    suspend fun reorderBooksInSeries(seriesId: UUID, bookIds: List<UUID>) {
        bookIds.forEachIndexed { index, bookId ->
            bookDao.getById(bookId)?.let { book ->
                bookDao.update(book.copy(orderInSeries = index + 1))
            }
        }
    }

    // ── Book Operations ──

    suspend fun deleteBook(bookId: UUID) {
        val editions = editionDao.getByParent(bookId)
        for (edition in editions) {
            deleteEditionCascade(edition.id)
        }
        favoriteBookDao.delete(bookId)
        bookDao.getById(bookId)?.let { bookDao.delete(it) }
    }

    suspend fun deleteBookCascade(bookId: UUID) {
        deleteBook(bookId)
    }

    suspend fun moveBookToAuthor(bookId: UUID, authorId: UUID) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(authorId = authorId))
    }

    // ── Edition Operations ──

    private suspend fun deleteEditionCascade(editionId: UUID) {
        chapterCompletionDao.deleteForEdition(editionId)
        chapterDao.getByParent(editionId).forEach { chapterDao.delete(it) }
        bookmarkDao.getByParent(editionId).forEach { bookmarkDao.delete(it) }
        progressDao.getByParent(editionId)?.let { progressDao.delete(it) }
        audioFileDao.getByParent(editionId).forEach { audioFileDao.delete(it) }
        editionDao.getById(editionId)?.let { editionDao.delete(it) }
    }

    data class EditionSnapshot(
        val edition: EditionEntity,
        val audioFiles: List<AudioFileEntity>,
        val chapters: List<ChapterEntity>,
        val bookmarks: List<BookmarkEntity>,
        val progress: ListeningProgressEntity?
    )

    suspend fun snapshotEdition(editionId: UUID): EditionSnapshot {
        val edition = editionDao.getById(editionId) ?: throw IllegalStateException("Edition not found")
        val audioFiles = audioFileDao.getByParent(editionId)
        val chapters = chapterDao.getByParent(editionId)
        val bookmarks = bookmarkDao.getByParent(editionId)
        val progress = progressDao.getByParent(editionId)
        return EditionSnapshot(edition, audioFiles, chapters, bookmarks, progress)
    }

    suspend fun deleteEdition(editionId: UUID) {
        val edition = editionDao.getById(editionId) ?: return
        val bookId = edition.bookId
        deleteEditionCascade(editionId)
        val remaining = editionDao.getByParent(bookId)
        if (remaining.isEmpty()) {
            favoriteBookDao.delete(bookId)
            bookDao.getById(bookId)?.let { bookDao.delete(it) }
        } else {
            bookDao.getById(bookId)?.let { book ->
                if (book.defaultEditionId == editionId) {
                    bookDao.update(book.copy(defaultEditionId = remaining.firstOrNull()?.id))
                }
            }
        }
    }

    // ── Collection Operations ──

    data class CollectionSnapshot(val collection: CollectionEntity, val memberBookIds: List<UUID>)

    suspend fun snapshotCollection(collectionId: UUID): CollectionSnapshot {
        val collection = collectionDao.getById(collectionId) ?: throw IllegalStateException("Collection not found")
        val refs = crossRefDao.getByParent(collectionId)
        return CollectionSnapshot(collection, refs.map { it.bookId })
    }

    suspend fun deleteCollection(collectionId: UUID) {
        val refs = crossRefDao.getByParent(collectionId)
        for (ref in refs) {
            crossRefDao.delete(collectionId, ref.bookId)
        }
        val collection = collectionDao.getById(collectionId) ?: return
        collectionDao.delete(collection)
    }

    suspend fun restoreCollection(snapshot: CollectionSnapshot) {
        collectionDao.insert(snapshot.collection)
        for (bookId in snapshot.memberBookIds) {
            crossRefDao.insert(CollectionBookCrossRef(snapshot.collection.id, bookId))
        }
    }

    // ── Library Root Operations ──

    data class RootSnapshot(
        val root: LibraryRootEntity,
        val editionCount: Int
    )

    suspend fun snapshotRoot(rootId: UUID): RootSnapshot {
        val root = libraryRootDao.getById(rootId) ?: throw IllegalStateException("Root not found")
        val count = audioFileDao.getByRoot(rootId).size
        return RootSnapshot(root, count)
    }

    suspend fun deleteRoot(rootId: UUID) {
        val root = libraryRootDao.getById(rootId) ?: return
        libraryRootDao.delete(root)
    }

    suspend fun deleteRootCascade(rootId: UUID) {
        val root = libraryRootDao.getById(rootId) ?: return
        val editions = editionDao.getByRoot(rootId)
        for (edition in editions) {
            deleteEditionCascade(edition.id)
            val bookId = edition.bookId
            val remaining = editionDao.getByParent(bookId)
            if (remaining.isEmpty()) {
                favoriteBookDao.delete(bookId)
                bookDao.getById(bookId)?.let { bookDao.delete(it) }
            } else {
                bookDao.getById(bookId)?.let { book ->
                    if (book.defaultEditionId == edition.id) {
                        bookDao.update(book.copy(defaultEditionId = remaining.firstOrNull()?.id))
                    }
                }
            }
        }
        libraryRootDao.delete(root)
    }

    // ── Create helpers ──

    suspend fun getOrCreateAuthor(name: String): AuthorEntity {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Author name cannot be blank")
        val existing = authorDao.getByName(trimmed)
        if (existing != null) return existing
        val created = AuthorEntity(name = trimmed, colorTheme = null)
        authorDao.insert(created)
        return created
    }

    suspend fun getOrCreateCollection(name: String): CollectionEntity {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Collection name cannot be blank")
        val existing = collectionDao.getByName(trimmed)
        if (existing != null) return existing
        val created = CollectionEntity(
            name = trimmed,
            icon = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        collectionDao.insert(created)
        return created
    }

    suspend fun addBookToCollection(collectionId: UUID, bookId: UUID) {
        if (crossRefDao.getById(collectionId, bookId) == null) {
            crossRefDao.insert(CollectionBookCrossRef(collectionId, bookId))
        }
    }

    suspend fun removeBookFromCollection(collectionId: UUID, bookId: UUID) {
        crossRefDao.delete(collectionId, bookId)
    }

    suspend fun updateBookGenre(bookId: UUID, genre: String?) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(genre = genre))
    }

    suspend fun updateCollectionName(collectionId: UUID, newName: String) {
        val collection = collectionDao.getById(collectionId) ?: return
        collectionDao.update(collection.copy(name = newName.trim()))
    }

    // ── Demo Data ──

    suspend fun clearDemoData() {
        val demoBooks = bookDao.getDemoBooks()
        for (book in demoBooks) {
            deleteBookCascade(book.id)
        }
        bookDao.deleteDemoBooks()
    }
}
