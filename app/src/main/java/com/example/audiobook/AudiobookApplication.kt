package com.example.audiobook

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.example.audiobook.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AudiobookApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                AppForeground.foreground = true
            }

            override fun onActivityStopped(activity: Activity) {
                AppForeground.foreground = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/** متابعة ما إذا كان أي نشاط في المقدمة — للتذكيرات: لا تُرسل أثناء فتح التطبيق. */
object AppForeground {
    @Volatile
    var foreground: Boolean = false
}