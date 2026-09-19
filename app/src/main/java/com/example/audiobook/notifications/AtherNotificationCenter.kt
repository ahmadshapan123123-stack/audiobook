package com.example.audiobook.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.audiobook.MainActivity
import com.example.audiobook.R
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.playback.PlaybackService
import com.example.audiobook.playback.PlaybackStateHolder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil

/**
 * منبع واحد للنشر والإلغاء لكل إشعارات التطبيق غير إشعار التشغيل.
 * القواعد المشتركة: قناة دائمًا، ولا نشر بلا [POST_NOTIFICATIONS] على API 33+
 * مع احترام المفتاح الرئيسي [AppSettings.notificationsEnabled] ومفتاح النوع الفرعي.
 */
@Singleton
class AtherNotificationCenter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettings: AppSettings
) {
    private val manager = NotificationManagerCompat.from(context)
    private val handler = Handler(Looper.getMainLooper())

    // ── التشغيل المصغّر ضمن خدمة التشغيل (يُدار من PlaybackService) ──

    // ── مؤقت النوم (قناة sleep_timer، عدّاد حيّ + أزرار) ──
    fun postSleepTimer(remainingMs: Long) {
        remainingMs.coerceAtLeast(0L).let { showSleepTimer(it) }
    }

    fun cancelSleepTimer() {
        manager.cancel(NotificationChannels.ID_SLEEP_TIMER)
    }

    private fun showSleepTimer(remainingMs: Long) {
        if (!canPost(appSettings.sleepTimerNotificationsEnabled.value)) return
        val time = formatClock(remainingMs)
        val contentIntent = openPlayerIntent()
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val builder = NotificationCompat.Builder(context, NotificationChannels.SLEEP_TIMER)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_sleep_title))
            .setContentText(context.getString(R.string.notif_sleep_remaining, time))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, context.getString(R.string.notif_sleep_extend_15), sleepAction(+15, pendingFlags))
            .addAction(0, context.getString(R.string.notif_sleep_extend_30), sleepAction(+30, pendingFlags))
            .addAction(0, context.getString(R.string.notif_sleep_cancel), sleepAction(-1, pendingFlags))
        manager.notify(NotificationChannels.ID_SLEEP_TIMER, builder.build())
    }

    private fun sleepAction(minutes: Int, flags: Int): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(SLEEP_ACTION)
            .putExtra(SLEEP_ACTION_MINUTES, minutes)
        return PendingIntent.getForegroundService(context, 1000 + minutes, intent, flags)
    }

    // ── حفظ اللحظة (قناة saved_moment، يُلغى تلقائيًا بعد 3 ثوانٍ) ──
    fun showSaveMoment(positionMs: Long) {
        if (!canPost(appSettings.saveMomentNotificationsEnabled.value)) return
        val text = context.getString(R.string.notif_save_moment_text, formatClock(positionMs))
        val notification = NotificationCompat.Builder(context, NotificationChannels.SAVED_MOMENT)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_save_moment_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openPlayerIntent())
            .setTimeoutAfter(SAVE_MOMENT_TIMEOUT_MS)
            .build()
        manager.notify(NotificationChannels.ID_SAVED_MOMENT, notification)
        // API < 26: setTimeoutAfter غير مدعوم — إلغاء يدوي بعد 3 ثوانٍ.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            handler.removeCallbacksAndMessages(NULL_TOKEN)
            handler.postDelayed({ manager.cancel(NotificationChannels.ID_SAVED_MOMENT) }, SAVE_MOMENT_TIMEOUT_MS)
        }
    }

    // ── إكمال الكتاب (قناة achievements، زر يفتح المكتبة) ──
    fun showBookCompleted(bookTitle: String, authorName: String?) {
        if (!canPost(appSettings.bookCompletionNotificationsEnabled.value)) return
        val text = context.getString(R.string.notif_book_completed_text, bookTitle, authorName ?: "")
        val libraryIntent = openLibraryIntent()
        val libraryPending = PendingIntent.getActivity(
            context, 2001, libraryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.ACHIEVEMENTS)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_book_completed_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(libraryPending)
            .addAction(0, context.getString(R.string.notif_book_completed_action), libraryPending)
            .build()
        manager.notify(NotificationChannels.ID_BOOK_COMPLETED, notification)
    }

    // ── التذكيرات (قناة reminders) ──
    fun showDailyReminder() {
        if (!canPost(true)) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_daily_reminder_title))
            .setContentText(context.getString(R.string.notif_daily_reminder_text))
            .setAutoCancel(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 2002, openLibraryIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()
        manager.notify(NotificationChannels.ID_DAILY_REMINDER, notification)
    }

    fun showResumeReminder(bookTitle: String) {
        if (!canPost(true)) return
        val notification = NotificationCompat.Builder(context, NotificationChannels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_resume_reminder_title))
            .setContentText(context.getString(R.string.notif_resume_reminder_text, bookTitle))
            .setAutoCancel(true)
            .setContentIntent(openPlayerIntent())
            .build()
        manager.notify(NotificationChannels.ID_RESUME_REMINDER, notification)
    }

    private fun canPost(kindEnabled: Boolean): Boolean {
        if (!kindEnabled) return false
        if (!appSettings.notificationsEnabled.value) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    private fun openPlayerIntent(): PendingIntent {
        val playerEditionId = PlaybackStateHolder.editionId
        val intent = if (playerEditionId != null) {
            openActivityIntent().apply { putExtra(EXTRA_ROUTE, "player/$playerEditionId") }
        } else openLibraryIntent()
        return PendingIntent.getActivity(
            context, 2004, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openLibraryIntent(): Intent =
        openActivityIntent().apply { putExtra(EXTRA_ROUTE, "library") }

    private fun openActivityIntent(): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

    companion object {
        const val EXTRA_ROUTE = "ather_notification_route"
        const val SLEEP_ACTION = "com.example.audiobook.sleep_timer.notification_action"
        const val SLEEP_ACTION_MINUTES = "minutes"
        private const val SAVE_MOMENT_TIMEOUT_MS = 3_000L
        private val NULL_TOKEN = Any()

        /** "MM:SS" أو "HH:MM:SS" وفق الطول — لعرض موضع اللحظة والعدّاد. */
        fun formatClock(ms: Long): String {
            val totalSeconds = ceil(ms / 1000.0).toLong().coerceAtLeast(0L)
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}