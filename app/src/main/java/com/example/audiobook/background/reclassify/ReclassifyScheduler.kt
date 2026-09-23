package com.example.audiobook.background.reclassify

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * جدولة «إعادة تصنيف المكتبة» كمهمة خلفية واحدة (REPLACE): يمنع تكدّس المهام
 * ويعمل حتى لو غادر المستخدم الشاشة. لا يُنشأ WorkManager إلا عند أول enqueue
 * (لازي) كي لا يتعطل إنشاء ViewModel في أي بيئة دون تهيئة WorkManager.
 */
@Singleton
class ReclassifyScheduler @Inject constructor(
    @ApplicationContext context: Context
) {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    fun enqueue() {
        val request = OneTimeWorkRequestBuilder<ReclassifyWorker>()
            .addTag(RECLASSIFY_TAG)
            .build()
        workManager.enqueueUniqueWork(RECLASSIFY_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val RECLASSIFY_WORK_NAME = "reclassify-library"
        const val RECLASSIFY_TAG = "reclassify"
    }
}