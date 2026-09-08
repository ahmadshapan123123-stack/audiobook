package com.example.audiobook.background.scanworker

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.example.audiobook.data.repository.LibraryRootRepository
import com.example.audiobook.data.room.entity.LibraryRootEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class ScanScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val rootRepository: LibraryRootRepository
) {
    private val workManager = WorkManager.getInstance(context)

    fun schedulePriorityScan(root: LibraryRootEntity) {
        val request = OneTimeWorkRequestBuilder<ScanWorker>()
            .setInputData(input(root, priority = true))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(PRIORITY_TAG)
            .addTag(rootTag(root))
            .build()
        workManager.enqueueUniqueWork(rootWorkName(root), ExistingWorkPolicy.REPLACE, request)
    }

    suspend fun scheduleBackgroundScans() {
        rootRepository.getEnabledBackgroundRoots().forEach { root ->
            val request = OneTimeWorkRequestBuilder<ScanWorker>()
                .setInputData(input(root, priority = false))
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .addTag(BACKGROUND_TAG)
                .addTag(rootTag(root))
                .build()
            workManager.enqueueUniqueWork(rootWorkName(root), ExistingWorkPolicy.REPLACE, request)
        }
    }

    suspend fun scheduleStartupScans() {
        rootRepository.getEnabledPriorityRoots().forEach(::schedulePriorityScan)
        scheduleBackgroundScans()
    }

    private fun input(root: LibraryRootEntity, priority: Boolean) = Data.Builder()
        .putString(ScanWorker.KEY_ROOT_ID, root.id.toString())
        .putBoolean(ScanWorker.KEY_PRIORITY, priority)
        .build()

    private fun rootWorkName(root: LibraryRootEntity) = "scan-root-${root.id}"
    private fun rootTag(root: LibraryRootEntity) = "root-${root.id}"

    companion object {
        const val PRIORITY_TAG = "scan-priority"
        const val BACKGROUND_TAG = "scan-background"
    }
}