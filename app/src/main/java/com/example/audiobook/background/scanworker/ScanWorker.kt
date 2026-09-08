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
        val database = EntryPointAccessors.fromApplication(applicationContext, ScanWorkerEntryPoint::class.java).database()
        val scanRoot = EntryPointAccessors.fromApplication(applicationContext, ScanWorkerEntryPoint::class.java).scanRoot()
        val root = database.libraryRootDao().getById(rootId) ?: return Result.failure()
        if (!root.isEnabled) return Result.success()

        return try {
            scanRoot(root.id)
            Result.success()
        } catch (error: Throwable) {
            database.libraryRootDao().setScanStatus(root.id, com.example.audiobook.data.room.entity.ScanStatus.ERROR)
            Result.failure()
        }
    }

    companion object {
        const val KEY_ROOT_ID = "root_id"
        const val KEY_PRIORITY = "priority"
    }
}