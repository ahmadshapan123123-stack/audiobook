package com.example.audiobook.data.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*

@Database(
	entities = [
		LibraryRootEntity::class, AuthorEntity::class, SeriesEntity::class, BookEntity::class,
		EditionEntity::class, AudioFileEntity::class, ChapterEntity::class, BookmarkEntity::class,
		ListeningProgressEntity::class, CollectionEntity::class, CollectionBookCrossRef::class,
		FavoriteBook::class, ListeningSessionEntity::class, EditionMatchDecisionEntity::class
	],
	version = 1,
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
}