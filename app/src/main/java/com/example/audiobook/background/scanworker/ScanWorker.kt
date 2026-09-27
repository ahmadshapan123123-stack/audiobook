package com.example.audiobook.background.scanworker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiobook.background.scan.ScanJob
import com.example.audiobook.background.scan.ScanRequest
import com.example.audiobook.background.scan.ScanServiceLauncher
import com.example.audiobook.data.room.AppDatabase
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScanWorkerEntryPoint {
    fun database(): AppDatabase
    fun scanServiceLauncher(): ScanServiceLauncher
}

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    /**
     * GAP 1 — العامل يطلب لا ينفّذ.
     *
     * كان `doWork` ينادي `scanRoot(root.id)` مباشرة. هذا يترك الفحص رهينة
     * دورة حياة WorkManager: المهمة يمكن أن تُلغى أو تُقتَل تحت ضغط ذاكرة أو
     * عند تحديث التطبيق، ثم يُقطع فحص 50 ألف ملف في منتصفه. أما الآن فالفحص
     * يجري في [com.example.audiobook.background.scan.ScanForegroundService]
     * التي نطاقُها مستقلّ عن المهمة وعليها إشعار أمامي دائم.
     *
     * لا يعود العامل بانتظار النتيجة، ويكتفي بتسليم الطلب: إتمام الفحص وإدارة
     * إشعاره وإشعارات الاكتشافات كلها مسؤوليات الخدمة الآن. الاشتقاق
     * `Result.retry()` عند تعذّر البدء يبقى مهمًّا: من Racing/service يمكن أن
     * يُرفض الطلب إن كان فحص آخر يعمل.
     */
    override suspend fun doWork(): Result {
        val rootId = inputData.getString(KEY_ROOT_ID)?.let(UUID::fromString) ?: return Result.failure()
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, ScanWorkerEntryPoint::class.java)
        val database = entryPoint.database()
        val root = database.libraryRootDao().getById(rootId) ?: return Result.failure()
        if (!root.isEnabled) return Result.success()
        if (!hasPersistedAccess(root.uri)) {
            // فُقد إذن SAF بعد إعادة التثبيت: نتخطى الفحص بصمت، وترصد الواجهة
            // الجذور المفقودة عبر accessRevokedRoots وتعرض أزرار إعادة المنح.
            android.util.Log.w(TAG, "Skipping scan for ${root.displayName}: SAF permission lost")
            database.libraryRootDao().setScanStatus(root.id, com.example.audiobook.data.room.entity.ScanStatus.ERROR)
            return Result.success()
        }

        val launched = entryPoint.scanServiceLauncher()
            .launch(ScanRequest(job = ScanJob.SINGLE_ROOT, rootId = rootId.toString()))
        return if (launched) {
            android.util.Log.i(TAG, "scan-delegated-to-service root=$rootId priority=${root.isPriority}")
            Result.success()
        } else {
            android.util.Log.w(TAG, "scan-launch-deferred root=$rootId reason=service-unavailable")
            Result.retry()
        }
    }

    private fun hasPersistedAccess(uriString: String): Boolean = runCatching {
        com.example.audiobook.data.localfilesystem.StorageAccess.hasPersistedPermission(
            applicationContext.contentResolver,
            android.net.Uri.parse(uriString)
        )
    }.getOrDefault(false)

    companion object {
        const val KEY_ROOT_ID = "root_id"
        const val KEY_PRIORITY = "priority"
        private const val TAG = "ScanWorker"
    }
}