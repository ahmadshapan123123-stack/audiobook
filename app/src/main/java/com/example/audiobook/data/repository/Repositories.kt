package com.example.audiobook.data.repository

import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import java.util.UUID

interface BookRepository {
    suspend fun insert(book: BookEntity)
    suspend fun update(book: BookEntity)
    suspend fun delete(book: BookEntity)
    suspend fun getById(id: UUID): BookEntity?
    suspend fun getByAuthor(authorId: UUID): List<BookEntity>
}

interface EditionRepository {
    suspend fun insert(edition: EditionEntity)
    suspend fun update(edition: EditionEntity)
    suspend fun delete(edition: EditionEntity)
    suspend fun getById(id: UUID): EditionEntity?
    suspend fun getByBook(bookId: UUID): List<EditionEntity>
}

interface ChapterRepository {
    suspend fun insert(chapter: ChapterEntity)
    suspend fun update(chapter: ChapterEntity)
    suspend fun delete(chapter: ChapterEntity)
    suspend fun getById(id: UUID): ChapterEntity?
    suspend fun getByEdition(editionId: UUID): List<ChapterEntity>
}

interface BookmarkRepository {
    suspend fun insert(bookmark: BookmarkEntity)
    suspend fun update(bookmark: BookmarkEntity)
    suspend fun delete(bookmark: BookmarkEntity)
    suspend fun getById(id: UUID): BookmarkEntity?
    suspend fun getByEdition(editionId: UUID): List<BookmarkEntity>
}

interface ProgressRepository {
    suspend fun insert(progress: ListeningProgressEntity)
    suspend fun update(progress: ListeningProgressEntity)
    suspend fun getByEdition(editionId: UUID): ListeningProgressEntity?
}

interface CollectionRepository {
    suspend fun insert(collection: CollectionEntity)
    suspend fun update(collection: CollectionEntity)
    suspend fun delete(collection: CollectionEntity)
    suspend fun getById(id: UUID): CollectionEntity?
    suspend fun addBook(collectionId: UUID, bookId: UUID)
    suspend fun getBooks(collectionId: UUID): List<CollectionBookCrossRef>
}

interface StatisticsRepository {
    suspend fun completedSessions(): List<ListeningSessionEntity>
}

interface LibraryRootRepository {
    suspend fun insert(root: LibraryRootEntity)
    suspend fun update(root: LibraryRootEntity)
    suspend fun delete(root: LibraryRootEntity)
    suspend fun getById(id: UUID): LibraryRootEntity?
        suspend fun clearPriorityExcept(id: UUID): Unit
        suspend fun setPriority(id: UUID, isPriority: Boolean): Unit
        suspend fun setEnabled(id: UUID, isEnabled: Boolean): Unit
        suspend fun setScanStatus(id: UUID, status: ScanStatus): Unit
        suspend fun markScanFinished(id: UUID, timestamp: Long, status: ScanStatus): Unit
        suspend fun getEnabledBackgroundRoots(): List<LibraryRootEntity>
        suspend fun getEnabledPriorityRoots(): List<LibraryRootEntity>
}

interface RemoteDataSource

class LocalOnlyBookRepository(private val dao: BookDao) : BookRepository {
    override suspend fun insert(book: BookEntity) = dao.insert(book)
    override suspend fun update(book: BookEntity) = dao.update(book)
    override suspend fun delete(book: BookEntity) = dao.delete(book)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByAuthor(authorId: UUID) = dao.getByParent(authorId)
}

class LocalOnlyEditionRepository(private val dao: EditionDao) : EditionRepository {
    override suspend fun insert(edition: EditionEntity) = dao.insert(edition)
    override suspend fun update(edition: EditionEntity) = dao.update(edition)
    override suspend fun delete(edition: EditionEntity) = dao.delete(edition)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByBook(bookId: UUID) = dao.getByParent(bookId)
}

class LocalOnlyChapterRepository(private val dao: ChapterDao) : ChapterRepository {
    override suspend fun insert(chapter: ChapterEntity) = dao.insert(chapter)
    override suspend fun update(chapter: ChapterEntity) = dao.update(chapter)
    override suspend fun delete(chapter: ChapterEntity) = dao.delete(chapter)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyBookmarkRepository(private val dao: BookmarkDao) : BookmarkRepository {
    override suspend fun insert(bookmark: BookmarkEntity) = dao.insert(bookmark)
    override suspend fun update(bookmark: BookmarkEntity) = dao.update(bookmark)
    override suspend fun delete(bookmark: BookmarkEntity) = dao.delete(bookmark)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyProgressRepository(private val dao: ProgressDao) : ProgressRepository {
    override suspend fun insert(progress: ListeningProgressEntity) = dao.insert(progress)
    override suspend fun update(progress: ListeningProgressEntity) = dao.update(progress)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyCollectionRepository(private val dao: CollectionDao, private val crossRefDao: CollectionBookCrossRefDao) : CollectionRepository {
    override suspend fun insert(collection: CollectionEntity) = dao.insert(collection)
    override suspend fun update(collection: CollectionEntity) = dao.update(collection)
    override suspend fun delete(collection: CollectionEntity) = dao.delete(collection)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun addBook(collectionId: UUID, bookId: UUID) = crossRefDao.insert(CollectionBookCrossRef(collectionId, bookId))
    override suspend fun getBooks(collectionId: UUID) = crossRefDao.getByParent(collectionId)
}

class LocalOnlyStatisticsRepository(private val dao: StatisticsDao) : StatisticsRepository {
    override suspend fun completedSessions() = dao.getCompletedSessions()
}

class LocalOnlyLibraryRootRepository(private val dao: LibraryRootDao) : LibraryRootRepository {
    override suspend fun insert(root: LibraryRootEntity) = dao.insert(root)
    override suspend fun update(root: LibraryRootEntity) = dao.update(root)
    override suspend fun delete(root: LibraryRootEntity) = dao.delete(root)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun clearPriorityExcept(id: UUID) = dao.clearPriorityExcept(id)
    override suspend fun setPriority(id: UUID, isPriority: Boolean) = dao.setPriority(id, isPriority)
    override suspend fun setEnabled(id: UUID, isEnabled: Boolean) = dao.setEnabled(id, isEnabled)
    override suspend fun setScanStatus(id: UUID, status: ScanStatus) = dao.setScanStatus(id, status)
    override suspend fun markScanFinished(id: UUID, timestamp: Long, status: ScanStatus) = dao.markScanFinished(id, timestamp, status)
    override suspend fun getEnabledBackgroundRoots() = dao.getEnabledBackgroundRoots()
    override suspend fun getEnabledPriorityRoots() = dao.getEnabledPriorityRoots()
}