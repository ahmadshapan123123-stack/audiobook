package com.example.audiobook.notifications

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG 2 regression: كان sleepAction يستدعي PendingIntent.getForegroundService وهو
 * API 26+. على API 23-25 يُنهار عند بدء مؤقت النوم (NoSuchMethodError). الحل:
 * فرع شرطي — getForegroundService على O+، وإلا getService (الخدمة تستدعي
 * startForeground بنفسها داخل onStartCommand).
 *
 * تُنفَّذ هذه الاختبارات على SDK 23 و24 و25 نفسها وتتأكد من أن بناء مؤقت النوم
 * يكتمل بلا استثناء وأن PendingIntent يُنشأ فعليًا.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 24, 25])
class AtherNotificationCenterApiCompatTest {

    @Test
    fun postSleepTimer_buildsAndPostsWithoutCrash() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val center = AtherNotificationCenter(context, AppSettings(context))
        center.postSleepTimer(5 * 60_000L)
    }

    @Test
    fun sleepAction_returnsNonNullPendingIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val center = AtherNotificationCenter(context, AppSettings(context))
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val method = AtherNotificationCenter::class.java.getDeclaredMethod(
            "sleepAction",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        )
        method.isAccessible = true
        val pending = method.invoke(center, 15, flags) as PendingIntent
        assertNotNull("PendingIntent يجب أن يُنشأ على كل SDK من 23 إلى 25", pending)
    }

    @Test
    fun formatClock_unaffectedByApiLevel() {
        assertEquals("05:00", AtherNotificationCenter.formatClock(5 * 60_000L))
        assertEquals("1:05:00", AtherNotificationCenter.formatClock(65 * 60_000L))
    }
}