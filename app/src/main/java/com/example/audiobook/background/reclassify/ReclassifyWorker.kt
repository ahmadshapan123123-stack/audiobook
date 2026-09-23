package com.example.audiobook.background.reclassify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReclassifyEntryPoint {
    fun reclassifyLibrary(): ReclassifyLibrary
}

/**
 * وكيل ثابت يحمل [ReclassifyLibrary]: في الإنتاج يُستخرج من Hilt عبر
 * [ReclassifyEntryPoint] مثل ScanWorker، وفي الاختبارات يُضبط مباشرة
 * ([testDelegate]) دون الحاجة إلى بيئة Hilt.
 */
object ReclassifyWorkRunner {
    @Volatile
    var testDelegate: ReclassifyLibrary? = null

    fun library(context: Context): ReclassifyLibrary =
        testDelegate ?: EntryPointAccessors.fromApplication(context, ReclassifyEntryPoint::class.java)
            .reclassifyLibrary()
}

/**
 * «إعادة تصنيف المكتبة» كمهمة خلفية: تطبيق ReclassifyLibrary(dryRun = false)
 * على قاعدة البيانات ثم تمرير النتيجة إلى [ReclassifyResultNotifier]
 * لتعرض الواجهة Snackbar «تم إعادة تصنيف N كتابًا».
 */
class ReclassifyWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            val applied = ReclassifyWorkRunner.library(applicationContext)(dryRun = false)
            ReclassifyResultNotifier.notify(applied)
            Result.success()
        } catch (error: Throwable) {
            Result.failure()
        }
    }
}