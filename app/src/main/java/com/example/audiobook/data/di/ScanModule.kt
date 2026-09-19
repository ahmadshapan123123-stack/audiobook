package com.example.audiobook.data.di

import android.content.Context
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.DocumentTreeFileSource
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.MediaAudioMetadataReader
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScanModule {
    @Provides
    @Singleton
    fun provideLibraryFileSource(@ApplicationContext context: Context): LibraryFileSource = DocumentTreeFileSource(context)

    @Provides
    @Singleton
    fun provideAudioMetadataReader(@ApplicationContext context: Context): AudioMetadataReader = MediaAudioMetadataReader(context)
}