package com.example.audiobook.data.di

import android.content.Context
import androidx.room.Room
import com.example.audiobook.data.repository.*
import com.example.audiobook.data.room.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "audiobook.db").build()

    @Provides fun provideBookDao(db: AppDatabase) = db.bookDao()
    @Provides fun provideEditionDao(db: AppDatabase) = db.editionDao()
    @Provides fun provideChapterDao(db: AppDatabase) = db.chapterDao()
    @Provides fun provideBookmarkDao(db: AppDatabase) = db.bookmarkDao()
    @Provides fun provideProgressDao(db: AppDatabase) = db.progressDao()
    @Provides fun provideCollectionDao(db: AppDatabase) = db.collectionDao()
    @Provides fun provideCollectionCrossRefDao(db: AppDatabase) = db.collectionBookCrossRefDao()
    @Provides fun provideStatisticsDao(db: AppDatabase) = db.statisticsDao()
    @Provides fun provideLibraryRootDao(db: AppDatabase) = db.libraryRootDao()
    @Provides fun provideSessionDao(db: AppDatabase) = db.listeningSessionDao()

    @Provides fun provideBookRepository(dao: com.example.audiobook.data.room.dao.BookDao): BookRepository = LocalOnlyBookRepository(dao)
    @Provides fun provideEditionRepository(dao: com.example.audiobook.data.room.dao.EditionDao): EditionRepository = LocalOnlyEditionRepository(dao)
    @Provides fun provideChapterRepository(dao: com.example.audiobook.data.room.dao.ChapterDao): ChapterRepository = LocalOnlyChapterRepository(dao)
    @Provides fun provideBookmarkRepository(dao: com.example.audiobook.data.room.dao.BookmarkDao): BookmarkRepository = LocalOnlyBookmarkRepository(dao)
    @Provides fun provideProgressRepository(dao: com.example.audiobook.data.room.dao.ProgressDao): ProgressRepository = LocalOnlyProgressRepository(dao)
    @Provides fun provideCollectionRepository(dao: com.example.audiobook.data.room.dao.CollectionDao, crossRef: com.example.audiobook.data.room.dao.CollectionBookCrossRefDao): CollectionRepository = LocalOnlyCollectionRepository(dao, crossRef)
    @Provides fun provideStatisticsRepository(dao: com.example.audiobook.data.room.dao.StatisticsDao): StatisticsRepository = LocalOnlyStatisticsRepository(dao)
    @Provides fun provideLibraryRootRepository(dao: com.example.audiobook.data.room.dao.LibraryRootDao): LibraryRootRepository = LocalOnlyLibraryRootRepository(dao)
}