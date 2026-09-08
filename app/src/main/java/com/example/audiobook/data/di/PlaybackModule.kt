package com.example.audiobook.data.di

import com.example.audiobook.playback.ExoPlaybackController
import com.example.audiobook.playback.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {
    @Binds
    @Singleton
    abstract fun bindPlaybackController(controller: ExoPlaybackController): PlaybackController
}