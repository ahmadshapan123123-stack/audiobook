package com.example.audiobook.background.scanworker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.domain.usecases.ScanRoot
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScanWorkerEntryPoint {
    fun database(): AppDatabase
    fun scanRoot(): ScanRoot
}

class ScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val rootId = inputData.getString(KEY_ROOT_ID)?.let(UUID::fromString) ?: return Result.failure()
        val priority = inputData.getBoolean(KEY_PRIORITY, false)
        val database = EntryPointAccessors.fromApplication(applicationContext, ScanWorkerEntryPoint::class.java).database()
        val scanRoot = EntryPointAccessors.fromApplication(applicationContext, ScanWorkerEntryPoint::class.java).scanRoot()
        val root = database.libraryRootDao().getById(rootId) ?: return Result.failure()
        if (!root.isEnabled) return Result.success()
        if (!hasPersistedAccess(root.uri)) {
            // فُقد إذن SAF بعد إعادة التثبيت: نتخطى الفحص بصمت، وترصد الواجهة
            // الجذور المفقودة عبر accessRevokedRoots وتعرض أزرار إعادة المنح.
            android.util.Log.w(TAG, "Skipping scan for ${root.displayName}: SAF permission lost")
            database.libraryRootDao().setScanStatus(root.id, com.example.audiobook.data.room.entity.ScanStatus.ERROR)
            return Result.success()
        }

        // المرحلة 4: الفحص ذو الأولوية يعمل كمهمة أمامية (expedited) — يُلزم
        // إشعار فوري؛ نعلنه غير المحدد ونخفي الإشعار فور الانتهاء (نجاحًا أو فشلًا).
        if (priority) setForeground(scanForegroundInfo(root.displayName))

        return try {
            scanRoot(root.id)
            // Part 4: فقط جذور الأولوية، وفقط إن بقيت اكتشافات لم يُبتَّ فيها —
            // يُشعل البوب-أب في الواجهة عبر DiscoveryNotifier (كائن ثابت، Worker خارج ViewModel).
            if (root.isPriority && database.pendingDiscoveryDao().countPendingByRoot(root.id) > 0) {
                com.example.audiobook.presentation.pendingdiscoveries.DiscoveryNotifier.notify(root.id)
            }
            Result.success()
        } catch (error: Throwable) {
            database.libraryRootDao().setScanStatus(root.id, com.example.audiobook.data.room.entity.ScanStatus.ERROR)
            Result.failure()
        } finally {
            if (priority) clearScanNotification()
        }
    }

    /** إشعار أمامي غير محدد أثناء الفحص ذي الأولوية (dataSync على أندرويد 14+). */
    private fun scanForegroundInfo(rootName: String): androidx.work.ForegroundInfo {
        val notification = androidx.core.app.NotificationCompat.Builder(
            applicationContext, com.example.audiobook.notifications.NotificationChannels.SCAN
        )
            .setSmallIcon(com.example.audiobook.R.drawable.ic_scan)
            .setContentTitle(applicationContext.getString(com.example.audiobook.R.string.notif_scan_title))
            .setContentText(applicationContext.getString(com.example.audiobook.R.string.notif_scan_text, rootName))
            .setOngoing(true)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
            .setProgress(0, 0, true)
            .build()
        return androidx.work.ForegroundInfo(
            com.example.audiobook.notifications.NotificationChannels.ID_SCAN,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun clearScanNotification() {
        androidx.core.app.NotificationManagerCompat.from(applicationContext)
            .cancel(com.example.audiobook.notifications.NotificationChannels.ID_SCAN)
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