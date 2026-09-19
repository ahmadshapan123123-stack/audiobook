package com.example.audiobook.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.audiobook.R

/**
 * إنشاء جميع قنوات الإشعارات دفعة واحدة في [AudiobookApplication.onCreate].
 * على API 26+ لا تتغير القنوات بعد إنشائها (الاسم والصوت والأولوية ثابتة).
 */
object NotificationChannels {
    const val PLAYBACK_FULL = "playback_full"
    const val PLAYBACK_MINIMAL = "playback_minimal"
    const val SLEEP_TIMER = "sleep_timer"
    const val SAVED_MOMENT = "saved_moment"
    const val REMINDERS = "reminders"
    const val ACHIEVEMENTS = "achievements"

    const val ID_PLAYBACK = 1001
    const val ID_SLEEP_TIMER = 1002
    const val ID_SAVED_MOMENT = 1003
    const val ID_BOOK_COMPLETED = 1004
    const val ID_DAILY_REMINDER = 1005
    const val ID_RESUME_REMINDER = 1006

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        mgr.createNotificationChannels(listOf(
            channel(context, PLAYBACK_FULL, R.string.channel_playback_full_name, R.string.channel_playback_full_desc, NotificationManager.IMPORTANCE_LOW),
            channel(context, PLAYBACK_MINIMAL, R.string.channel_playback_minimal_name, R.string.channel_playback_minimal_desc, NotificationManager.IMPORTANCE_MIN),
            channel(context, SLEEP_TIMER, R.string.channel_sleep_timer_name, R.string.channel_sleep_timer_desc, NotificationManager.IMPORTANCE_LOW),
            channel(context, SAVED_MOMENT, R.string.channel_saved_moment_name, R.string.channel_saved_moment_desc, NotificationManager.IMPORTANCE_DEFAULT),
            channel(context, REMINDERS, R.string.channel_reminders_name, R.string.channel_reminders_desc, NotificationManager.IMPORTANCE_DEFAULT),
            channel(context, ACHIEVEMENTS, R.string.channel_achievements_name, R.string.channel_achievements_desc, NotificationManager.IMPORTANCE_LOW)
        ))
    }

    private fun channel(ctx: Context, id: String, nameRes: Int, descRes: Int, importance: Int) =
        NotificationChannel(id, ctx.getString(nameRes), importance).apply {
            description = ctx.getString(descRes)
            setShowBadge(false)
        }
}
