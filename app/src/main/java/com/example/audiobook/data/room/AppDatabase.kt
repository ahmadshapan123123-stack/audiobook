package com.example.audiobook.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*

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

val DATABASE_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

@Database(
	entities = [
		LibraryRootEntity::class, AuthorEntity::class, SeriesEntity::class, BookEntity::class,
		EditionEntity::class, AudioFileEntity::class, ChapterEntity::class, BookmarkEntity::class,
		ListeningProgressEntity::class, CollectionEntity::class, CollectionBookCrossRef::class,
		FavoriteBook::class, ListeningSessionEntity::class, EditionMatchDecisionEntity::class,
        ChapterCompletionEntity::class
	],
	version = 3,
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
}