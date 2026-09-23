package com.example.audiobook.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import java.util.UUID

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN isTitleUserConfirmed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE editions ADD COLUMN isNarratorUserConfirmed INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE editions ADD COLUMN isLabelUserConfirmed INTEGER NOT NULL DEFAULT 0")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `chapter_completions` (" +
                "`chapterId` TEXT NOT NULL, " +
                "`editionId` TEXT NOT NULL, " +
                "`completedAtMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`chapterId`), " +
                "FOREIGN KEY(`chapterId`) REFERENCES `chapters`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                "FOREIGN KEY(`editionId`) REFERENCES `editions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_chapter_completions_editionId` ON `chapter_completions` (`editionId`)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
    }
}

private val DEMO_AUTHOR_IDS = listOf("author.tawfiq", "author.mahfouz", "author.zaidan", "author.samman")
    .map { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
private val DEMO_SERIES_IDS = listOf("series.assateer", "series.thalathia")
    .map { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
private val DEMO_COLLECTION_IDS = listOf("coll.night", "coll.queue")
    .map { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }
private val DEMO_ROOT_IDS = listOf("root.demo", "root.mobile")
    .map { UUID.nameUUIDFromBytes(it.toByteArray()).toString() }

/**
 * Migration 4→5: توسيم عناصر التجربة بدلًا من الاعتماد على «عدد الكتب» أو معرفات ثابتة.
 * إضافة عمود isDemo إلى المؤلفين/السلاسل/المجموعات وجذور المكتبة (إضافة فقط، لا تُحذف بيانات)،
 * ليتمكن «إزالة بيانات التجربة» من تنظيف اليتامى بدقة دون أي خطر على بيانات المستخدم.
 *
 * ثم نُعيد تسمية الصفوف التي زرعها DatabaseSeeder في قاعدة سابقة (هجرة من v4):
 * معرفاتها الذاتيّة متطابقة (UUID.nameUUIDFromBytes لنفس الوسوم) — أي أن هذه القشط يعلّم
 * صفوف البذر فقط ولا يمس أي صف يستخدمه المستخدم بعد.
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE authors ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE series ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE collections ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE library_roots ADD COLUMN isDemo INTEGER NOT NULL DEFAULT 0")

        DEMO_AUTHOR_IDS.forEach { id ->
            db.execSQL("UPDATE authors SET isDemo = 1 WHERE id = ?", arrayOf(id))
        }
        DEMO_SERIES_IDS.forEach { id ->
            db.execSQL("UPDATE series SET isDemo = 1 WHERE id = ?", arrayOf(id))
        }
        DEMO_COLLECTION_IDS.forEach { id ->
            db.execSQL("UPDATE collections SET isDemo = 1 WHERE id = ?", arrayOf(id))
        }
        DEMO_ROOT_IDS.forEach { id ->
            db.execSQL("UPDATE library_roots SET isDemo = 1 WHERE id = ?", arrayOf(id))
        }
    }
}

/**
 * Migration 5→6: قائمة انتظار الاكتشافات الجديدة (Part 4) — جدول إضافة فقط،
 * لا يمسّ أي بيانات موجودة وتُترك بياناته للمستخدم عبر قرارات البوب-أب/الإعدادات.
 * IGNORE + المفتاح الفريد (rootId, folderPath) يجعل إعادة الفحص بلا تأثير على القائمة.
 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pending_discoveries` (" +
                "`id` TEXT NOT NULL, " +
                "`rootId` TEXT NOT NULL, " +
                "`folderPath` TEXT NOT NULL, " +
                "`detectedTitle` TEXT NOT NULL, " +
                "`authorName` TEXT NOT NULL, " +
                "`seriesName` TEXT, " +
                "`discoveredAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`rootId`) REFERENCES `library_roots`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_pending_discoveries_rootId_folderPath` ON `pending_discoveries` (`rootId`, `folderPath`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_discoveries_status` ON `pending_discoveries` (`status`)")
    }
}

val DATABASE_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

@Database(
	entities = [
		LibraryRootEntity::class, AuthorEntity::class, SeriesEntity::class, BookEntity::class,
		EditionEntity::class, AudioFileEntity::class, ChapterEntity::class, BookmarkEntity::class,
		ListeningProgressEntity::class, CollectionEntity::class, CollectionBookCrossRef::class,
		FavoriteBook::class, ListeningSessionEntity::class, EditionMatchDecisionEntity::class,
        ChapterCompletionEntity::class, PendingDiscoveryEntity::class
	],
	version = 6,
	exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
	abstract fun libraryRootDao(): LibraryRootDao
	abstract fun authorDao(): AuthorDao
	abstract fun seriesDao(): SeriesDao
	abstract fun bookDao(): BookDao
	abstract fun editionDao(): EditionDao
	abstract fun audioFileDao(): AudioFileDao
	abstract fun chapterDao(): ChapterDao
	abstract fun bookmarkDao(): BookmarkDao
	abstract fun progressDao(): ProgressDao
	abstract fun collectionDao(): CollectionDao
	abstract fun collectionBookCrossRefDao(): CollectionBookCrossRefDao
	abstract fun favoriteBookDao(): FavoriteBookDao
	abstract fun statisticsDao(): StatisticsDao
	abstract fun listeningSessionDao(): ListeningSessionDao
	abstract fun editionMatchDecisionDao(): EditionMatchDecisionDao
	abstract fun audioFileAggregateDao(): AudioFileAggregateDao
    abstract fun chapterCompletionDao(): ChapterCompletionDao
    abstract fun pendingDiscoveryDao(): PendingDiscoveryDao
}