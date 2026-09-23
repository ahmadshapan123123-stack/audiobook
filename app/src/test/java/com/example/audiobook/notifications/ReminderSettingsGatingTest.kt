package com.example.audiobook.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * BUG 6 regression: كان showDailyReminder/showResumeReminder يمرران true لـ canPost
 * (بدل مفتاح النوع الخاص) — فتُنشر التذكيرات حتى مع تعطيلها. الآن يُحترم
 * AppSettings.dailyReminderEnabled / resumeReminderEnabled.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReminderSettingsGatingTest {

    private lateinit var appSettings: AppSettings
    private lateinit var center: AtherNotificationCenter
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        appSettings = AppSettings(context)
        center = AtherNotificationCenter(context, appSettings)
        manager = context.getSystemService(NotificationManager::class.java)
    }

    private fun postedCount(): Int = shadowOf(manager).allNotifications.size

    @Test
    fun dailyReminder_honorsDailyReminderEnabled() {
        appSettings.setNotificationsEnabled(true)
        appSettings.setDailyReminderEnabled(true)

        center.showDailyReminder()
        assertEquals(1, postedCount())

        appSettings.setDailyReminderEnabled(false)
        center.showDailyReminder()
        assertEquals("لا تُنشر رسالة جديدة عند تعطيل التذكير اليومي", 1, postedCount())
    }

    @Test
    fun resumeReminder_honorsResumeReminderEnabled() {
        appSettings.setNotificationsEnabled(true)
        appSettings.setResumeReminderEnabled(true)

        center.showResumeReminder("كتاب")
        assertEquals(1, postedCount())

        appSettings.setResumeReminderEnabled(false)
        center.showResumeReminder("كتاب")
        assertEquals("لا تُنشر رسالة جديدة عند تعطيل تذكير المتابعة", 1, postedCount())
    }

    @Test
    fun dailyReminder_stillRespectsMasterNotificationsSwitch() {
        appSettings.setNotificationsEnabled(false)
        appSettings.setDailyReminderEnabled(true)

        center.showDailyReminder()
        assertEquals(0, postedCount())
    }
}