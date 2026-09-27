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
enum class DiscoveryStatus { PENDING, RESOLVED, IGNORED }

@Entity(tableName = "library_roots")
data class LibraryRootEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val uri: String,
    val displayName: String,
    val isPriority: Boolean,
    val isEnabled: Boolean,
    val lastScanAt: Long?,
    val scanStatus: ScanStatus,
    val isDemo: Boolean = false
)

@Entity(tableName = "authors")
data class AuthorEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val name: String,
    val colorTheme: String?,
    val imagePath: String? = null,
    val description: String? = null,
    val isDemo: Boolean = false
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
    val colorTheme: String?,
    val imagePath: String? = null,
    val description: String? = null,
    val isDemo: Boolean = false
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
    /** null = كتاب مستقل غير مُصنَّف (مثل ملف مباشر في جذر المكتبة) — يعرضه الواجهة «غير مصنف». */
    val authorId: UUID?,
    val seriesId: UUID?,
    val orderInSeries: Int?,
    val genre: String?,
    val coverImagePath: String?,
    val coverSource: CoverSource,
    val isCoverUserSelected: Boolean,
    val isTitleUserConfirmed: Boolean = false,
    val defaultEditionId: UUID?,
    val remoteId: UUID?,
    val syncStatus: SyncStatus,
    val isDemo: Boolean = false
)

@Entity(
    tableName = "editions",
    foreignKeys = [
        ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["bookId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = LibraryRootEntity::class, parentColumns = ["id"], childColumns = ["libraryRootId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index("bookId"),
        Index("libraryRootId"),
        // هوية الكتاب البنيوية مُنسخة على جدول editions نفسه (بدل الاعتماد على
        // books عبر join) فيصير فهرسًا مركّبًا حقيقيًا على المفتاح كاملًا، وتصبح
        // إعادة تسمية مجلد لا تُنتج كتابًا جديدًا: العنوان يبقى هو نفسه.
        Index(value = ["libraryRootId", "authorId", "seriesId", "bookTitle"])
    ]
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
    val syncStatus: SyncStatus,
    /**
     * نسخة من هوية كتابه على صفحه (denormalized من [BookEntity]): مؤلفه وسلسلته
     * وعنوانه وقت الأرشفة. تُملأ مع الإصدار وتبقى ثابتة بعد إعادة التسمية؛
     * والقراءة عبر getByStructuralKey لا تحتاج join، والفهرس المركب أعلاه يغطيها.
     */
    val authorId: UUID? = null,
    val seriesId: UUID? = null,
    val bookTitle: String? = null
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

/**
 * سجل اكتمال فصل (قاعدة الـ90% الحرفية). المفتاح الأساسي هو chapterId نفسه
 * فيستحيل تسجيل الفصل نفسه مرتين — "مرة واحدة فقط لكل فصل، لا تكرار".
 */
@Entity(
    tableName = "chapter_completions",
    foreignKeys = [
        ForeignKey(entity = ChapterEntity::class, parentColumns = ["id"], childColumns = ["chapterId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = EditionEntity::class, parentColumns = ["id"], childColumns = ["editionId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("editionId")]
)
data class ChapterCompletionEntity(
    @androidx.room.PrimaryKey val chapterId: UUID,
    val editionId: UUID,
    val completedAtMs: Long
)

/**
 * نقطة استئناف الفحص (المرحلة 4): تسجل آخر دفعة مجلدات بدأت معالجتها (rootId +
 * أول مجلد في الدفعة). تُكتب قبل كل دفعة؛ إن أُوقف الفحص/انقطع تُبقى للتعافي —
 * الفحص التالي يستأنف من مجلدها إذا كان checkpoint حديثًا (< 24 ساعة) ويُحذف
 * عند الإنجاز التام. بلا FK للجذور (مخزن معرّف بشكل مستقل ويُحذف صراحة).
 */
@Entity(tableName = "scan_checkpoints")
data class ScanCheckpointEntity(
    @androidx.room.PrimaryKey val rootId: UUID,
    val lastProcessedFolderPath: String,
    val scannedAt: Long
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
    val syncStatus: SyncStatus,
    val isDemo: Boolean = false
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

/**
 * اكتشاف جديد يُسجَّل عند ظهور مجلد صوتي لأول مرة (قائمة انتظار التعامل مع الاكتشافات).
 * يُسجَّل بالتوازي مع الاستيراد التلقائي فلا يغيّر سلوك الفحص؛ «تجاهل» يمنع
 * استيراد هذا المجلد في الفحوصات اللاحقة، و«الإسناد» يعدّل البنية التي أنشأها الفحص.
 * المفتاح الطبيعي (rootId, folderPath) يضمن تسجيلًا واحدًا لكل مجلد مهما تكرر الفحص.
 */
@Entity(
    tableName = "pending_discoveries",
    foreignKeys = [ForeignKey(entity = LibraryRootEntity::class, parentColumns = ["id"], childColumns = ["rootId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["rootId", "folderPath"], unique = true), Index("status")]
)
data class PendingDiscoveryEntity(
    @androidx.room.PrimaryKey val id: UUID = UUID.randomUUID(),
    val rootId: UUID,
    val folderPath: String,
    val detectedTitle: String,
    val authorName: String,
    val seriesName: String?,
    val discoveredAt: Long,
    val status: DiscoveryStatus
)

/**
 * تعديل تصنيف تم اعتماده في المعاينة (المرحلة 5) — يُخزَّن ليُطبَّق حين ينفَّذ
 * الاستيراد الفعلي (ScanRoot غير مُعدَّل: تسبق إنشاء شجرة القاعدة لتصنيف المستخدم)،
 * ويُفرَّغ السجل بعد استيراد ناجح. rootId هنا هو uri المجلد المختار (قبل تكوين
 * LibraryRoot في القاعدة).
 */
@Entity(
    tableName = "onboarding_edits",
    indices = [Index(value = ["rootId", "path", "editType"], unique = true)]
)
data class OnboardingEditEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rootId: String,
    val path: String,
    val editType: String,
    val newValue: String?,
    val createdAt: Long
)