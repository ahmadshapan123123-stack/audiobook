package com.example.audiobook

import android.util.Log

/**
 * STAGE 1E — سجل إفراغات ضغط الذاكرة.
 *
 * قبل هذا كان `onTrimMemory`/`onLowMemory` يسجّلان فقط: عند ضغط الذاكرة
 * أثناء فحص 50 ألف ملف لم يُحرَّر أي بايت، فيقتل lmkd العملية.
 *
 * أي مكوّن يملك كاشًا قابلًا لإعادة البناء (قابل للإسقاط بأمان في أي
 * لحظة) يسجّل مُفرِغًا هنا؛ عند الضغط تُستدعى كل المفرغات ثم `System.gc()`
 * ليعيد ART الصفحات الفارغة للنظام. آلية الإفراج الأساسية للفحص نفسه
 * هي نطاق الأطوار في [com.example.audiobook.domain.usecases.ScanRoot]
 * (القوائم الكبيرة تُصفَّر فور انتهاء طورها) — وهذا السجل هو خط الدفاع
 * الثاني على مستوى العملية.
 */
object MemoryPressureReleasers {
    private const val TAG = "Ather"
    private val lock = Any()
    private val releasers = LinkedHashMap<String, () -> Unit>()

    /** يسجّل مُفرِغًا باسم ثابت (التسجيل الثاني بنفس الاسم يستبدل الأول). */
    fun register(name: String, releaser: () -> Unit) {
        synchronized(lock) { releasers[name] = releaser }
    }

    fun unregister(name: String) {
        synchronized(lock) { releasers.remove(name) }
    }

    /**
     * يشغّل كل المفرغات المسجلة ثم GC صريح.
     * @return عدد المفرغات التي نُفذت بنجاح.
     */
    fun releaseAll(): Int {
        val snapshot: List<Pair<String, () -> Unit>> = synchronized(lock) { releasers.toList() }
        var ran = 0
        snapshot.forEach { (name, releaser) ->
            runCatching { releaser() }
                .onSuccess { ran++ }
                .onFailure { error -> Log.w(TAG, "memory-releaser failed: $name", error) }
        }
        // GC صريح مقصود هنا فقط: بعد إفراغ الكاشات توجد قمامة مؤكدة،
        // والهدف إعادة الصفحات للنظام قبل أن يتدخل lmkd — لا في المسار الساخن.
        System.gc()
        if (ran > 0 || snapshot.isNotEmpty()) {
            Log.i(TAG, "memory-release ran=$ran registered=${snapshot.size}")
        }
        return ran
    }
}
