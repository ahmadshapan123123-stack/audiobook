package com.example.audiobook.data.di

import com.example.audiobook.playback.SleepTimerClock
import com.example.audiobook.playback.SystemSleepTimerClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SleepTimerModule {
    @Provides
    @Singleton
    fun provideSleepTimerClock(): SleepTimerClock = SystemSleepTimerClock()
}