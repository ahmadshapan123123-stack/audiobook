package com.example.audiobook.background.reminders

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.audiobook.data.preferences.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * جدولة التذكيرات عبر WorkManager الوحيد المشترك (نفس مثيل [WorkManager.getInstance]).
 * يُستراح عند تغيّر الإعدادات وعند بدء التطبيق.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val appSettings: AppSettings
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    /**
     * تذكير يومي: عمل دوري 24 ساعة ببداية عند وقت التذكير التالي (افتراضيًا 20:00).
     * تذكير المتابعة: عمل مرة واحدة بعد 24 ساعة.
     */
    fun syncWithSettings() {
        syncDaily()
        syncResume()
    }

    fun rearmResumeReminder() {
        syncResume()
    }

    private fun syncDaily() {
        val enabled = appSettings.notificationsEnabled.value && appSettings.dailyReminderEnabled.value
        if (!enabled) {
            workManager.cancelUniqueWork(DailyReminderWorker.UNIQUE_NAME)
            return
        }
        val hour = appSettings.dailyReminderHour.value
        val minute = appSettings.dailyReminderMinute.value
        val now = Calendar.getInstance()
        val firstRun = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!firstRun.after(now)) firstRun.add(Calendar.DAY_OF_YEAR, 1)
        val initialDelay = firstRun.timeInMillis - now.timeInMillis
        val request = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        workManager.enqueueUniquePeriodicWork(
            DailyReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun syncResume() {
        val enabled = appSettings.notificationsEnabled.value && appSettings.resumeReminderEnabled.value
        if (!enabled) {
            workManager.cancelUniqueWork(ResumeReminderWorker.UNIQUE_NAME)
            return
        }
        val request = OneTimeWorkRequestBuilder<ResumeReminderWorker>()
            .setInitialDelay(24, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniqueWork(
            ResumeReminderWorker.UNIQUE_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}