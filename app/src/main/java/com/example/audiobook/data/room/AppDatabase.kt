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

val DEMO_AUTHOR_IDS = listOf("author.tawfiq", "author.mahfouz", "author.zaidan", "author.samman")
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

/**
 * Migration 6→7: تسوية كتب التجربة القديمة في جدول الكتب.
 *
 * في v4 أُضيف books.isDemo، لكن هجرة v4→v5 وسّمت المؤلفين/السلاسل/المجموعات/
 * الجذور بالمعرفات الذاتيّة ولم تُسوِّ كتبهم — فالكتب التي بذرها DatabaseSeeder قبل v4
 * بقيت isDemo=0 فتعجز «إزالة بيانات التجربة» عن حذفها (DELETE/WHERE isDemo=1).
 * هنا نعلّم كل كتاب مؤلفُه أحد مؤلفي البذر الذاتيّين (نفس المعرفات الثابتة) isDemo=1،
 * مع استثناء الكتب التي أكّد المستخدم عنوانها يدويًا (isTitleUserConfirmed=1) كي لا
 * تُحذف لاحقًا رغم انتمائها لمعرّف تجريبي.
 *
 * exact SQL:
 *   UPDATE books
 *   SET isDemo = 1
 *   WHERE authorId IN (<معرفات مؤلفي البذر…>)
 *     AND isTitleUserConfirmed = 0
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val placeholders = DEMO_AUTHOR_IDS.joinToString(",") { "'$it'" }
        db.execSQL("UPDATE books SET isDemo = 1 WHERE authorId IN ($placeholders) AND isTitleUserConfirmed = 0")
    }
}

/**
 * Migration 7→8: سماح الكتب بمؤلف "غير مُصنَّف" (authorId=null) — الملفات المباشرة
 * في جذر المكتبة لم تعد تتحول إلى مؤلف باسم الجذر؛ تصبح كتابًا مستقلًا بلا مؤلف.
 * النموذج الصارم: عمق1=مؤلف، عمق2=سلسلة، عمق3فحص=كتاب، والجذر حاوية فقط.
 * نعيد بناء جدول books كاملًا لأن SQLite لا يستطيع تعديل قيد NOT NULL، مع
 * نسخ كل البيانات كما هي (المؤلفون القائمون يبقون) — فحوصات لاحقة تعيد التصنيف.
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `books_new` (" +
                "`id` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`authorId` TEXT, " +
                "`seriesId` TEXT, " +
                "`orderInSeries` INTEGER, " +
                "`genre` TEXT, " +
                "`coverImagePath` TEXT, " +
                "`coverSource` TEXT NOT NULL, " +
                "`isCoverUserSelected` INTEGER NOT NULL, " +
                "`isTitleUserConfirmed` INTEGER NOT NULL, " +
                "`defaultEditionId` TEXT, " +
                "`remoteId` TEXT, " +
                "`syncStatus` TEXT NOT NULL, " +
                "`isDemo` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), " +
                "FOREIGN KEY(`authorId`) REFERENCES `authors`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT, " +
                "FOREIGN KEY(`seriesId`) REFERENCES `series`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)"
        )
        db.execSQL(
            "INSERT INTO books_new (id, title, authorId, seriesId, orderInSeries, genre, coverImagePath, coverSource, isCoverUserSelected, isTitleUserConfirmed, defaultEditionId, remoteId, syncStatus, isDemo) " +
                "SELECT id, title, authorId, seriesId, orderInSeries, genre, coverImagePath, coverSource, isCoverUserSelected, isTitleUserConfirmed, defaultEditionId, remoteId, syncStatus, isDemo FROM books"
        )
        db.execSQL("DROP TABLE books")
        db.execSQL("ALTER TABLE books_new RENAME TO books")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_authorId` ON `books` (`authorId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_seriesId` ON `books` (`seriesId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_books_defaultEditionId` ON `books` (`defaultEditionId`)")
    }
}

/**
 * Migration 8→9 (المرحلة 4 — فحص المكتبات الكبيرة):
 *  1) جدول scan_checkpoints: نقطة استئناف الفحص (جذر واحد لكل سجل). إضافة فقط.
 *  2) تعزيز دفاعي لعامود audio_files.lastModified: يتأكد أنه موجود (آلية الكاش
 *     التزايدي تعتمد عليه)، ويضيفه إن غاب في قاعدة خارجية قديمة — بأمان عبر
 *     PRAGMA table_info (ALTER без IF NOT EXISTS في SQLite).
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `scan_checkpoints` (" +
                "`rootId` TEXT NOT NULL, " +
                "`lastProcessedFolderPath` TEXT NOT NULL, " +
                "`scannedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`rootId`))"
        )
        val hasLastModified = db.query("PRAGMA table_info(audio_files)").use { cursor ->
            var found = false
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == "lastModified") {
                    found = true
                    break
                }
            }
            found
        }
        if (!hasLastModified) {
            db.execSQL("ALTER TABLE audio_files ADD COLUMN lastModified INTEGER NOT NULL DEFAULT 0")
        }
    }
}

/**
 * Migration 9→10 (المرحلة 5 — معاينة/اعتماد التصنيف أثناء الإعداد):
 * جدول onboarding_edits يحفظ تعديلات التصنيف المعتمدة من المستخدم (تُطبَّق عند
 * الاستيراد الفعلي وتُفرَّغ بعده). مفتاح فريد (rootId, path, editType) — إضافة فقط.
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `onboarding_edits` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`rootId` TEXT NOT NULL, " +
                "`path` TEXT NOT NULL, " +
                "`editType` TEXT NOT NULL, " +
                "`newValue` TEXT, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_onboarding_edits_rootId_path_editType` " +
                "ON `onboarding_edits` (`rootId`, `path`, `editType`)"
        )
    }
}

/**
 * Migration 10→11 (Phase 2 — هوية الكتاب البنيوية):
 * تنسخ هوية كل إصدار من جدول `books` إلى صفّه في `editions` (authorId, seriesId,
 * bookTitle) ثم تُنشئ فهرسًا مركّبًا على (libraryRootId, authorId, seriesId,
 * bookTitle). السبب: `libraryRootId` في `editions` بينما `authorId/seriesId/title`
 * في `books` — والفهرس المركّب لا يجمع جدولين، فنُنزع المفتاح ليصير قابلًا
 * للاستعلام والفهرسة على جدول واحد. `sourceFolderPath` تبقى كما هي (لا تُحذف).
 * النسخة تُنسخ من `books.id` (لا من `editions.id`).
 */
private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `editions` ADD COLUMN `authorId` TEXT")
        db.execSQL("ALTER TABLE `editions` ADD COLUMN `seriesId` TEXT")
        db.execSQL("ALTER TABLE `editions` ADD COLUMN `bookTitle` TEXT")
        // تعبئة رجعية: كل إصدار يورث هوية كتابه القائم (كتب بلا مؤلف/سلسلة تبقى NULL).
        db.execSQL(
            "UPDATE `editions` SET " +
                "`authorId` = (SELECT `books`.`authorId` FROM `books` WHERE `books`.`id` = `editions`.`bookId`), " +
                "`seriesId` = (SELECT `books`.`seriesId` FROM `books` WHERE `books`.`id` = `editions`.`bookId`), " +
                "`bookTitle` = (SELECT `books`.`title` FROM `books` WHERE `books`.`id` = `editions`.`bookId`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_editions_libraryRootId_authorId_seriesId_bookTitle` " +
                "ON `editions` (`libraryRootId`, `authorId`, `seriesId`, `bookTitle`)"
        )
    }
}

val DATABASE_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)

@Database(
	entities = [
		LibraryRootEntity::class, AuthorEntity::class, SeriesEntity::class, BookEntity::class,
		EditionEntity::class, AudioFileEntity::class, ChapterEntity::class, BookmarkEntity::class,
		ListeningProgressEntity::class, CollectionEntity::class, CollectionBookCrossRef::class,
		FavoriteBook::class, ListeningSessionEntity::class, EditionMatchDecisionEntity::class,
        ChapterCompletionEntity::class, PendingDiscoveryEntity::class, ScanCheckpointEntity::class,
        OnboardingEditEntity::class
	],
	version = 11,
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
    abstract fun scanCheckpointDao(): ScanCheckpointDao
    abstract fun onboardingEditDao(): OnboardingEditDao
}