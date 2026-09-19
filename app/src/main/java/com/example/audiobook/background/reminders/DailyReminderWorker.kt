package com.example.audiobook.background.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiobook.AppForeground
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.notifications.AtherNotificationCenter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DailyReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)
        if (AppForeground.foreground) return Result.success()
        val settings = entry.appSettings()
        if (!settings.notificationsEnabled.value || !settings.dailyReminderEnabled.value) return Result.success()
        entry.notificationCenter().showDailyReminder()
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "daily-reminder"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun appSettings(): AppSettings
    fun notificationCenter(): AtherNotificationCenter
}