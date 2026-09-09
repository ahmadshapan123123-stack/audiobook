package com.example.audiobook.data.repository

import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import com.example.audiobook.domain.statistics.StatisticsDates
import com.example.audiobook.domain.statistics.StatisticsRanges
import com.example.audiobook.domain.statistics.StatisticsRules
import com.example.audiobook.playback.SleepTimerClock
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

/**
 * نطاقات تقرير وقت الاستماع:
 * TODAY → اليوم الحالي من منتصف ليله حتى الآن.
 * WEEK  → الأسبوع المتدحرج (آخر 7 أيام حتى الآن).
 * MONTH → الشهر التقويمي الحالي حتى الآن.
 */
enum class DateRange { TODAY, WEEK, MONTH }

interface StatisticsRepository {
    suspend fun completedSessions(): List<ListeningSessionEntity>

    /** مجموع [ListeningSessionEntity.durationListenedMs] للجلسات داخل النطاق (حسب [ListeningSessionEntity.startedAt]). */
    suspend fun listeningTimeForRange(range: DateRange): Long

    /** عدد النسخ التي بلغت نهايتها فعليًا (status = FINISHED في listening_progress). */
    suspend fun completedBooksCount(): Int

    /** عدد سجلات اكتمال الفصول المسجَّلة فعليًا (قاعدة الـ90% الحرفية، مرة لكل فصل). */
    suspend fun completedChaptersCount(): Int

    /** أيام متتالية حتى اليوم بها جلسة استماع واحدة على الأقل — خوارزمية صريحة على التواريخ. */
    suspend fun currentStreak(): Int

    /** متوسط [ListeningProgressEntity.playbackSpeed] عبر النسخ المسجَّلة (0f عند غياب بيانات). */
    suspend fun averageSpeed(): Float

    /** أحدث الجلسات بترتيب زمني تنازلي مع اسم الكتاب (شاشة History). */
    suspend fun history(): List<ListeningHistoryRow>
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

class LocalOnlyStatisticsRepository(
    private val dao: StatisticsDao,
    private val clock: SleepTimerClock
) : StatisticsRepository {
    override suspend fun completedSessions() = dao.getCompletedSessions()

    override suspend fun listeningTimeForRange(range: DateRange): Long {
        val now = clock.nowMillis()
        val rangeStart = StatisticsRanges.rangeStartMillis(range, now)
        return dao.getSessionsBetween(rangeStart, now).sumOf { it.durationListenedMs }
    }

    override suspend fun completedBooksCount(): Int = dao.getCompletedBooks().size

    override suspend fun completedChaptersCount(): Int = dao.countCompletedChapters()

    override suspend fun currentStreak(): Int {
        val sessionDays = dao.getAllSessions().map { StatisticsDates.dayNumber(it.startedAt) }
        val today = StatisticsDates.dayNumber(clock.nowMillis())
        return StatisticsRules.computeStreak(sessionDays, today)
    }

    override suspend fun averageSpeed(): Float {
        val speeds = dao.getPlaybackSpeeds()
        return if (speeds.isEmpty()) 0f else speeds.sum() / speeds.size
    }

    override suspend fun history(): List<ListeningHistoryRow> = dao.getHistory()
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