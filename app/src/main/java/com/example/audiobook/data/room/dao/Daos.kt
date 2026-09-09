package com.example.audiobook.data.room.dao

import androidx.room.*
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface CrudDao<T> {
    suspend fun insert(entity: T)
    suspend fun update(entity: T)
    suspend fun delete(entity: T)
}

@Dao
interface LibraryRootDao : CrudDao<LibraryRootEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: LibraryRootEntity)
    @Update override suspend fun update(entity: LibraryRootEntity)
    @Delete override suspend fun delete(entity: LibraryRootEntity)
    @Query("SELECT * FROM library_roots WHERE id = :id") suspend fun getById(id: UUID): LibraryRootEntity?
    @Query("SELECT * FROM library_roots ORDER BY displayName") fun observeAll(): Flow<List<LibraryRootEntity>>
        @Query("UPDATE library_roots SET isPriority = 0 WHERE id != :id") suspend fun clearPriorityExcept(id: UUID)
        @Query("UPDATE library_roots SET isPriority = :isPriority WHERE id = :id") suspend fun setPriority(id: UUID, isPriority: Boolean)
        @Query("UPDATE library_roots SET isEnabled = :isEnabled WHERE id = :id") suspend fun setEnabled(id: UUID, isEnabled: Boolean)
        @Query("UPDATE library_roots SET scanStatus = :status WHERE id = :id") suspend fun setScanStatus(id: UUID, status: ScanStatus)
        @Query("UPDATE library_roots SET lastScanAt = :timestamp, scanStatus = :status WHERE id = :id") suspend fun markScanFinished(id: UUID, timestamp: Long, status: ScanStatus)
        @Query("SELECT * FROM library_roots WHERE isEnabled = 1 AND isPriority = 0 ORDER BY displayName") suspend fun getEnabledBackgroundRoots(): List<LibraryRootEntity>
        @Query("SELECT * FROM library_roots WHERE isEnabled = 1 AND isPriority = 1 ORDER BY displayName") suspend fun getEnabledPriorityRoots(): List<LibraryRootEntity>
}

@Dao
interface AuthorDao : CrudDao<AuthorEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: AuthorEntity)
    @Update override suspend fun update(entity: AuthorEntity)
    @Delete override suspend fun delete(entity: AuthorEntity)
    @Query("SELECT * FROM authors WHERE id = :id") suspend fun getById(id: UUID): AuthorEntity?
    @Query("SELECT * FROM authors WHERE name = :name LIMIT 1") suspend fun getByName(name: String): AuthorEntity?
    @Query("SELECT * FROM authors") fun observeAll(): Flow<List<AuthorEntity>>
}

@Dao
interface SeriesDao : CrudDao<SeriesEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: SeriesEntity)
    @Update override suspend fun update(entity: SeriesEntity)
    @Delete override suspend fun delete(entity: SeriesEntity)
    @Query("SELECT * FROM series WHERE id = :id") suspend fun getById(id: UUID): SeriesEntity?
    @Query("SELECT * FROM series WHERE authorId = :authorId ORDER BY name") suspend fun getByParent(authorId: UUID): List<SeriesEntity>
    @Query("SELECT * FROM series") fun observeAll(): Flow<List<SeriesEntity>>
}

@Dao
interface BookDao : CrudDao<BookEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: BookEntity)
    @Update override suspend fun update(entity: BookEntity)
    @Delete override suspend fun delete(entity: BookEntity)
    @Query("SELECT * FROM books ORDER BY COALESCE(orderInSeries, 2147483647), title") suspend fun getAll(): List<BookEntity>
    @Query("SELECT * FROM books ORDER BY COALESCE(orderInSeries, 2147483647), title") fun observeAll(): Flow<List<BookEntity>>
    @Query("SELECT * FROM books WHERE id = :id") suspend fun getById(id: UUID): BookEntity?
    @Query("SELECT * FROM books WHERE id = :id") fun observeById(id: UUID): Flow<BookEntity?>
    @Query("SELECT * FROM books WHERE authorId = :authorId ORDER BY COALESCE(orderInSeries, 2147483647), title") suspend fun getByParent(authorId: UUID): List<BookEntity>
    @Query("SELECT * FROM books WHERE seriesId = :seriesId ORDER BY COALESCE(orderInSeries, 2147483647), title") suspend fun getBySeries(seriesId: UUID): List<BookEntity>
    @Query("SELECT * FROM books WHERE authorId = :authorId AND title = :title LIMIT 1") suspend fun getByAuthorAndTitle(authorId: UUID, title: String): BookEntity?
}

@Dao
interface EditionDao : CrudDao<EditionEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: EditionEntity)
    @Update override suspend fun update(entity: EditionEntity)
    @Delete override suspend fun delete(entity: EditionEntity)
    @Query("SELECT * FROM editions WHERE id = :id") suspend fun getById(id: UUID): EditionEntity?
    @Query("SELECT * FROM editions WHERE id = :id") fun observeById(id: UUID): Flow<EditionEntity?>
    @Query("SELECT * FROM editions WHERE bookId = :bookId ORDER BY label") suspend fun getByParent(bookId: UUID): List<EditionEntity>
    @Query("SELECT * FROM editions WHERE bookId = :bookId ORDER BY label") fun observeByParent(bookId: UUID): Flow<List<EditionEntity>>
    @Query("SELECT * FROM editions ORDER BY label") fun observeAll(): Flow<List<EditionEntity>>
    @Query("SELECT * FROM editions WHERE libraryRootId = :rootId AND sourceFolderPath = :folderPath LIMIT 1") suspend fun getByRootAndFolder(rootId: UUID, folderPath: String): EditionEntity?
}

@Dao
interface AudioFileDao : CrudDao<AudioFileEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: AudioFileEntity)
    @Update override suspend fun update(entity: AudioFileEntity)
    @Delete override suspend fun delete(entity: AudioFileEntity)
    @Query("SELECT * FROM audio_files WHERE id = :id") suspend fun getById(id: UUID): AudioFileEntity?
    @Query("SELECT * FROM audio_files WHERE editionId = :editionId ORDER BY orderIndex") suspend fun getByParent(editionId: UUID): List<AudioFileEntity>
    @Query("SELECT * FROM audio_files WHERE editionId = :editionId ORDER BY orderIndex") fun observeByParent(editionId: UUID): Flow<List<AudioFileEntity>>
    @Query("SELECT * FROM audio_files WHERE fileUri = :fileUri LIMIT 1") suspend fun getByUri(fileUri: String): AudioFileEntity?
    @Query("SELECT af.* FROM audio_files af INNER JOIN editions e ON af.editionId = e.id WHERE e.libraryRootId = :rootId") suspend fun getByRoot(rootId: UUID): List<AudioFileEntity>
}

data class AudioFileAggregateRow(val editionId: UUID, val totalDurationMs: Long, val fileCount: Int)

@Dao
interface AudioFileAggregateDao {
    @Query(
        "SELECT editionId, SUM(durationMs) AS totalDurationMs, COUNT(*) AS fileCount " +
            "FROM audio_files WHERE fileStatus = 'AVAILABLE' GROUP BY editionId"
    )
    fun observeAggregates(): Flow<List<AudioFileAggregateRow>>
}

@Dao
interface ChapterDao : CrudDao<ChapterEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: ChapterEntity)
    @Update override suspend fun update(entity: ChapterEntity)
    @Delete override suspend fun delete(entity: ChapterEntity)
    @Query("SELECT * FROM chapters WHERE id = :id") suspend fun getById(id: UUID): ChapterEntity?
    @Query("SELECT * FROM chapters WHERE editionId = :editionId ORDER BY orderIndex") suspend fun getByParent(editionId: UUID): List<ChapterEntity>
    @Query("SELECT * FROM chapters WHERE editionId = :editionId ORDER BY startPositionMs") fun observeByParent(editionId: UUID): Flow<List<ChapterEntity>>
    @Query("DELETE FROM chapters WHERE editionId = :editionId AND createdFrom = 'IMPORTED'") suspend fun deleteImported(editionId: UUID)
}

@Dao
interface BookmarkDao : CrudDao<BookmarkEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: BookmarkEntity)
    @Update override suspend fun update(entity: BookmarkEntity)
    @Delete override suspend fun delete(entity: BookmarkEntity)
    @Query("SELECT * FROM bookmarks WHERE id = :id") suspend fun getById(id: UUID): BookmarkEntity?
    @Query("SELECT * FROM bookmarks WHERE editionId = :editionId ORDER BY positionMs") suspend fun getByParent(editionId: UUID): List<BookmarkEntity>
    @Query("SELECT * FROM bookmarks WHERE editionId = :editionId ORDER BY positionMs") fun observeByParent(editionId: UUID): Flow<List<BookmarkEntity>>
}

@Dao
interface ProgressDao : CrudDao<ListeningProgressEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: ListeningProgressEntity)
    @Update override suspend fun update(entity: ListeningProgressEntity)
    @Delete override suspend fun delete(entity: ListeningProgressEntity)
    @Query("SELECT * FROM listening_progress WHERE id = :id") suspend fun getById(id: UUID): ListeningProgressEntity?
    @Query("SELECT * FROM listening_progress WHERE editionId = :editionId") suspend fun getByParent(editionId: UUID): ListeningProgressEntity?
    @Query("SELECT * FROM listening_progress WHERE editionId = :editionId") fun observeByParent(editionId: UUID): Flow<ListeningProgressEntity?>
    @Query("SELECT * FROM listening_progress") fun observeAll(): Flow<List<ListeningProgressEntity>>
}

@Dao
interface CollectionDao : CrudDao<CollectionEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: CollectionEntity)
    @Update override suspend fun update(entity: CollectionEntity)
    @Delete override suspend fun delete(entity: CollectionEntity)
    @Query("SELECT * FROM collections WHERE id = :id") suspend fun getById(id: UUID): CollectionEntity?
    @Query("SELECT * FROM collections WHERE name = :name LIMIT 1") suspend fun getByName(name: String): CollectionEntity?
    @Query("SELECT * FROM collections") fun observeAll(): Flow<List<CollectionEntity>>
}

@Dao
interface CollectionBookCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: CollectionBookCrossRef)
    @Update suspend fun update(entity: CollectionBookCrossRef)
    @Query("SELECT * FROM collection_book_cross_ref WHERE collectionId = :collectionId AND bookId = :bookId") suspend fun getById(collectionId: UUID, bookId: UUID): CollectionBookCrossRef?
    @Query("DELETE FROM collection_book_cross_ref WHERE collectionId = :collectionId AND bookId = :bookId") suspend fun delete(collectionId: UUID, bookId: UUID)
    @Query("SELECT * FROM collection_book_cross_ref WHERE collectionId = :collectionId") suspend fun getByParent(collectionId: UUID): List<CollectionBookCrossRef>
    @Query("SELECT * FROM collection_book_cross_ref") fun observeAll(): Flow<List<CollectionBookCrossRef>>
}

@Dao
interface FavoriteBookDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(entity: FavoriteBook)
    @Update suspend fun update(entity: FavoriteBook)
    @Query("DELETE FROM favorite_books WHERE bookId = :bookId") suspend fun delete(bookId: UUID)
    @Query("SELECT * FROM favorite_books WHERE bookId = :bookId") suspend fun getById(bookId: UUID): FavoriteBook?
    @Query("SELECT * FROM favorite_books WHERE bookId = :bookId") fun observeById(bookId: UUID): Flow<FavoriteBook?>
    @Query("SELECT * FROM favorite_books") fun observeAll(): Flow<List<FavoriteBook>>
}

@Dao
interface ListeningSessionDao : CrudDao<ListeningSessionEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: ListeningSessionEntity)
    @Update override suspend fun update(entity: ListeningSessionEntity)
    @Delete override suspend fun delete(entity: ListeningSessionEntity)
    @Query("SELECT * FROM listening_sessions WHERE id = :id") suspend fun getById(id: UUID): ListeningSessionEntity?
    @Query("SELECT * FROM listening_sessions WHERE editionId = :editionId ORDER BY startedAt DESC") suspend fun getByParent(editionId: UUID): List<ListeningSessionEntity>
    @Query("SELECT * FROM listening_sessions WHERE sessionState = 'ACTIVE'") suspend fun getActiveSessions(): List<ListeningSessionEntity>
}

@Dao
interface EditionMatchDecisionDao : CrudDao<EditionMatchDecisionEntity> {
    @Insert(onConflict = OnConflictStrategy.REPLACE) override suspend fun insert(entity: EditionMatchDecisionEntity)
    @Update override suspend fun update(entity: EditionMatchDecisionEntity)
    @Delete override suspend fun delete(entity: EditionMatchDecisionEntity)
    @Query("SELECT * FROM edition_match_decisions WHERE id = :id") suspend fun getById(id: UUID): EditionMatchDecisionEntity?
    @Query("SELECT * FROM edition_match_decisions WHERE subjectEditionId = :editionId ORDER BY createdAt DESC") suspend fun getByParent(editionId: UUID): List<EditionMatchDecisionEntity>
    @Query("SELECT * FROM edition_match_decisions ORDER BY createdAt DESC") suspend fun getAll(): List<EditionMatchDecisionEntity>
}

/** صف عرض سجل الاستماع: جلسة مصحوبة باسم الكتاب (مع بقاء الصف حتى لو حُذفت النسخة). */
data class ListeningHistoryRow(
    val sessionId: UUID,
    val bookTitle: String?,
    val editionLabel: String?,
    val startedAt: Long,
    val durationListenedMs: Long,
    val endReason: SessionEndReason?
)

@Dao
interface StatisticsDao {
    @Query("SELECT * FROM listening_sessions WHERE sessionState = 'COMPLETED' ORDER BY startedAt DESC") suspend fun getCompletedSessions(): List<ListeningSessionEntity>
    @Query("SELECT * FROM listening_sessions WHERE startedAt >= :startMillis AND startedAt < :endMillis ORDER BY startedAt ASC") suspend fun getSessionsBetween(startMillis: Long, endMillis: Long): List<ListeningSessionEntity>
    @Query("SELECT * FROM listening_sessions ORDER BY startedAt ASC") suspend fun getAllSessions(): List<ListeningSessionEntity>
    @Query("SELECT DISTINCT editionId FROM listening_progress WHERE status = 'FINISHED'") suspend fun getCompletedBooks(): List<UUID>
    @Query("SELECT playbackSpeed FROM listening_progress") suspend fun getPlaybackSpeeds(): List<Float>
    @Query("SELECT COUNT(*) FROM chapter_completions") suspend fun countCompletedChapters(): Int
    @Query(
        "SELECT s.id AS sessionId, b.title AS bookTitle, e.label AS editionLabel, " +
            "s.startedAt AS startedAt, s.durationListenedMs AS durationListenedMs, s.endReason AS endReason " +
            "FROM listening_sessions s " +
            "LEFT JOIN editions e ON e.id = s.editionId " +
            "LEFT JOIN books b ON b.id = e.bookId " +
            "ORDER BY s.startedAt DESC"
    )
    suspend fun getHistory(): List<ListeningHistoryRow>
}

@Dao
interface ChapterCompletionDao {
    /** IGNORE يحافظ على أول وقت اكتمال: التسجيل "مرة واحدة فقط لكل فصل". */
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: ChapterCompletionEntity)
    @Query("DELETE FROM chapter_completions WHERE editionId = :editionId") suspend fun deleteForEdition(editionId: UUID)
}