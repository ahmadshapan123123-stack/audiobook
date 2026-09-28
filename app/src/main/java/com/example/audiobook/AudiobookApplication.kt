package com.example.audiobook

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
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

        // PART 7.3: معالج استثناءات فادح عام. بدونه ينتهي أي خيط بلا أثر، فلا
        // يبقى سجل يوضّح ما الذي قتل التطبيق فعلًا — وهو ما جعل تشخيص انهيار
        // الفحص على مكتبة كبيرة بلا بيانات. نُبقي المعالج الأصلي (إن وُجد) ونسجّل
        // إلى Logcat بعلامة واضحة "AtherCrash" تظهر في `adb logcat`.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            Log.e(TAG, "AtherCrash: uncaught exception on thread '${thread.name}'", error)
            previousHandler?.uncaughtException(thread, error)
        }

        // PART 7.2: onTrimMemory / onLowMemory. التطبيق يحتفظ بكائنات كبيرة
        // (قوائم فحص، خرائط تصنيف)، فالتنبيه يLogged فقط — الإفراج الفعلي
        // يجري بمقدار الحارس (tryBegin) والتفريغ التدريجي (PART 4/5).
        //
        // STAGE 1E: لم يعد التسجيل وحده كافيًا — لقد قُتلت العملية تحت ضغط
        // الذاكرة لأن شيئًا لم يُحرَّر فعلًا. الآن يُستدعى سجل الإفراغات
        // (كاشات قابلة لإعادة البناء + GC يعيد الصفحات للنظام).
        registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                Log.i(TAG, "onTrimMemory level=$level")
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    // لا نُبطل الفحص الجاري: إبطاؤه فقط عبر تساهل الحارس.
                    MemoryPressure.onLowMemory(level)
                    MemoryPressureReleasers.releaseAll()
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            override fun onLowMemory() {
                Log.w(TAG, "onLowMemory")
                MemoryPressure.onLowMemory(ComponentCallbacks2.TRIM_MEMORY_COMPLETE)
                MemoryPressureReleasers.releaseAll()
            }
        })
    }

    private companion object {
        const val TAG = "Ather"
    }
}

/**
 * PART 7.2: إشارة ضغط الذاكرة. الغرض تشخيصي وتنظيمي: نُسجّل الحالة كي
 * تُربط Future crashes بضغط ذاكرة سابق. لا نُبطل أي فحص: الإلغاء قد يفقد
 * المستخدم تقدّمه ويضطر لإعادة فحص 50 ألف ملف.
 */
internal object MemoryPressure {
    @Volatile
    var lastTrimLevel: Int = -1
        private set

    internal fun onLowMemory(level: Int) {
        lastTrimLevel = level
        Log.i("Ather", "memory-pressure level=$level (scan continues; guarded + batched)")
    }
}

/** متابعة ما إذا كان أي نشاط في المقدمة — للتذكيرات: لا تُرسل أثناء فتح التطبيق. */
object AppForeground {
    @Volatile
    var foreground: Boolean = false
}