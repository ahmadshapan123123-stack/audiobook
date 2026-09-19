package com.example.audiobook.background.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiobook.AppForeground
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.notifications.AtherNotificationCenter
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * تذكير المتابعة: يُجدوَل بعد 24 ساعة من آخر تشغيل/جلسة. يفحص لاحقًا:
 * التطبيق في الخلفية، الإشعارات مفعلة، والتذكير مفعل، وهناك كتاب قيد الاستماع
 * لم يُكمل — وإلا يُلغي نفسه بصمت (Result.success).
 */
class ResumeReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, ResumeReminderEntryPoint::class.java)
        if (AppForeground.foreground) return Result.success()
        val settings = entry.appSettings()
        if (!settings.notificationsEnabled.value || !settings.resumeReminderEnabled.value) return Result.success()
        val rows = entry.database().statisticsDao().getInProgressRows()
        val first = rows.firstOrNull() ?: return Result.success()
        entry.notificationCenter().showResumeReminder(first.bookTitle)
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "resume-reminder"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ResumeReminderEntryPoint {
    fun appSettings(): AppSettings
    fun database(): AppDatabase
    fun notificationCenter(): AtherNotificationCenter
}