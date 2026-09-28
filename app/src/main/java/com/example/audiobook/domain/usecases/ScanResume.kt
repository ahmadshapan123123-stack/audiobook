package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.ScanCheckpointEntity
import java.util.UUID

/**
 * STAGE 4 — اكتشاف «فحص متوقف» من نقاط التوقف الباقية.
 *
 * الفحص يحفظ checkpoint قبل كل دفعة ويحذفه عند الإنجاز (`ScanRoot`)، فأي
 * checkpoint باقٍ بعد موت العملية = فحص انقطع. الاستئناف نفسه تلقائي:
 * `resumeIndexFor` يبدأ من مجلد الـcheckpoint، فيكفي إطلاق FULL_SCAN عادي.
 */
object ScanResume {
    /** نقاط التوقف أقدم من هذا تُعتبر منتهية وتُحذف بدل عرضها. */
    const val TTL_MS = 24L * 60L * 60L * 1000L

    data class ResumeInfo(
        val rootId: UUID,
        val rootLabel: String,
        val lastFolder: String,
        val stoppedAt: Long
    )

    /** أحدث checkpoint صالح (أقل من 24 ساعة) أو null. */
    suspend fun freshCheckpoint(database: AppDatabase, now: Long = System.currentTimeMillis()): ResumeInfo? {
        val dao = database.scanCheckpointDao()
        val all = dao.getAll()
        // تنظيف المنتهية أولًا حتى لا تتراكم.
        all.filter { now - it.scannedAt > TTL_MS }.forEach { dao.deleteForRoot(it.rootId) }
        val fresh = all.filter { now - it.scannedAt <= TTL_MS }.maxByOrNull { it.scannedAt } ?: return null
        val root = database.libraryRootDao().getById(fresh.rootId) ?: run {
            dao.deleteForRoot(fresh.rootId)
            return null
        }
        if (!root.isEnabled) return null
        return ResumeInfo(
            rootId = fresh.rootId,
            rootLabel = root.displayName,
            lastFolder = fresh.lastProcessedFolderPath,
            stoppedAt = fresh.scannedAt
        )
    }

    /** STAGE 4B — نقاط التوقف الصالحة للعرض (للاختبار أيضًا). */
    suspend fun allFresh(database: AppDatabase, now: Long = System.currentTimeMillis()): List<ScanCheckpointEntity> =
        database.scanCheckpointDao().getAll().filter { now - it.scannedAt <= TTL_MS }
}
