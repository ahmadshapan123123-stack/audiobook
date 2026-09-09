package com.example.audiobook.data.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

enum class ScanStatus { IDLE, SCANNING, ERROR }
enum class CoverSource { EMBEDDED, FOLDER_COVER, FOLDER_IMAGE, PLACEHOLDER }
enum class SyncStatus { LOCAL_ONLY, SYNCED, PENDING_SYNC }
enum class FileStatus { AVAILABLE, MISSING }
enum class ChapterCreatedFrom { AUTO_SPLIT, USER_MARK, MANUAL, IMPORTED }
enum class BookmarkType { BOOKMARK, NOTE }
enum class ProgressStatus { NOT_STARTED, IN_PROGRESS, FINISHED }
enum class SessionEndReason { MANUAL_PAUSE, SLEEP_TIMER, FINISHED_BOOK, APP_CLOSED, INTERRUPTED }
enum class SessionState { ACTIVE, COMPLETED, INTERRUPTED }
enum class UserDecision { SAME_EDITION, DIFFERENT_EDITION, NOT_SAME_BOOK }

@Entity(tableName = "library_roots")
data class LibraryRootEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val uri: String,
    val displayName: String,
    val isPriority: Boolean,
    val isEnabled: Boolean,
    val lastScanAt: Long?,
    val scanStatus: ScanStatus
)

@Entity(tableName = "authors")
data class AuthorEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val colorTheme: String?
)

@Entity(
    tableName = "series",
    foreignKeys = [ForeignKey(entity = AuthorEntity::class, parentColumns = ["id"], childColumns = ["authorId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("authorId")]
)
data class SeriesEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val authorId: UUID,
    val name: String,
    val colorTheme: String?
)

@Entity(
    tableName = "books",
    foreignKeys = [
        ForeignKey(entity = AuthorEntity::class, parentColumns = ["id"], childColumns = ["authorId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SeriesEntity::class, parentColumns = ["id"], childColumns = ["seriesId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("authorId"), Index("seriesId"), Index("defaultEditionId")]
)
data class BookEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val title: String,
    val authorId: UUID,
    val seriesId: UUID?,
    val orderInSeries: Int?,
    val genre: String?,
    val coverImagePath: String?,
    val coverSource: CoverSource,
    val isCoverUserSelected: Boolean,
    val isTitleUserConfirmed: Boolean = false,
    val defaultEditionId: UUID?,
    val remoteId: UUID?,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "editions",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryRootEntity::class, parentColumns = ["id"], childColumns = ["libraryRootId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("bookId"), Index("libraryRootId")]
)
data class EditionEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val bookId: UUID,
    val narratorName: String?,
    val label: String,
    val totalDurationMs: Long,
    val fileFormat: String,
    val libraryRootId: UUID,
    val sourceFolderPath: String,
    val confidenceScore: Float,
    val isUserConfirmed: Boolean,
    val isNarratorUserConfirmed: Boolean = false,
    val isLabelUserConfirmed: Boolean = false,
    val remoteId: UUID?,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "audio_files",
    foreignKeys = [ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("editionId"), Index(value = ["fileUri"], unique = true)]
)
data class AudioFileEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val editionId: UUID,
    val fileUri: String,
    val relativePath: String,
    val fileName: String,
    val orderIndex: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val lastModified: Long,
    val contentFingerprint: String,
    val mimeType: String,
    val fileStatus: FileStatus
)

@Entity(
    tableName = "chapters",
    foreignKeys = [ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("editionId")]
)
data class ChapterEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val editionId: UUID,
    val title: String?,
    val startPositionMs: Long,
    val orderIndex: Int,
    val createdFrom: ChapterCreatedFrom
)

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("editionId")]
)
data class BookmarkEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val editionId: UUID,
    val positionMs: Long,
    val createdAt: Long,
    val type: BookmarkType,
    val noteText: String?,
    val remoteId: UUID?,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "listening_progress",
    foreignKeys = [ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["editionId"], unique = true)]
)
data class ListeningProgressEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val editionId: UUID,
    val currentPositionMs: Long,
    val lastPlayedAt: Long,
    val status: ProgressStatus,
    val playbackSpeed: Float,
    val remoteId: UUID?,
    val syncStatus: SyncStatus
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val icon: String?,
    val remoteId: UUID?,
    val syncStatus: SyncStatus
)

@Entity(
    tableName = "collection_book_cross_ref",
    primaryKeys = ["collectionId", "bookId"],
    foreignKeys = [
        ForeignKey(entity = CollectionEntity::class, parentColumns = ["id"], childColumns = ["collectionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("bookId")]
)
data class CollectionBookCrossRef(
    val collectionId: UUID,
    val bookId: UUID
)

@Entity(
    tableName = "favorite_books",
    primaryKeys = ["bookId"],
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE)]
)
data class FavoriteBook(
    val bookId: UUID,
    val addedAt: Long
)

@Entity(
    tableName = "listening_sessions",
    foreignKeys = [ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("editionId"), Index("sessionState")]
)
data class ListeningSessionEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val editionId: UUID,
    val startedAt: Long,
    val endedAt: Long?,
    val durationListenedMs: Long,
    val endReason: SessionEndReason?,
    val sessionState: SessionState
)

@Entity(
    tableName = "edition_match_decisions",
    foreignKeys = [
        ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["subjectEditionId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["comparedAgainstEditionId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("subjectEditionId"), Index("comparedAgainstEditionId")]
)
data class EditionMatchDecisionEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val subjectEditionId: UUID,
    val comparedAgainstEditionId: UUID?,
    val signalsSnapshot: String,
    val userDecision: UserDecision,
    val createdAt: Long
)