package com.example.audiobook.playback

import com.example.audiobook.data.room.entity.ChapterCompletionEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.domain.statistics.StatisticsRules
import java.util.UUID

/**
 * [R4-النقطة 2] مراقب اكتمال الفصل أثناء التشغيل الفعلي.
 *
 * عند كل تحديث موضع ([onPositionUpdate]) يحدد الفصل الحالي حسب
 * [ChapterEntity.startPositionMs]، ثم يتحقق من قاعدة الـ90% الحرفية
 * ([StatisticsRules.isChapterCompleted]) ويُسجّل الاكتمال فور تجاوزها.
 *
 * "مرة واحدة فقط لكل فصل، لا تكرار": مستوى المراقب يحتفظ بمجموعة الفصول
 * المسجَّلة لكل نسخة، ومستوى القاعدة يحمي عبر المفتاح الأساسي (chapterId)
 * بسياسة IGNORE — حماية مزدوجة ضد أي تكرار.
 *
 * [clock] و[writer] قابلان للحقن لاختبار السلوك بمعزل عن ExoPlayer وقاعدة البيانات.
 */
class ChapterCompletionObserver(
    private val clock: () -> Long,
    private val writer: suspend (ChapterCompletionEntity) -> Unit
) {
    private val completedPerEdition = mutableMapOf<UUID, MutableSet<UUID>>()

    suspend fun onPositionUpdate(editionId: UUID, positionMs: Long, chapters: List<ChapterEntity>, editionEndMs: Long) {
        if (editionEndMs <= 0L || positionMs < 0L) return
        val ordered = chapters.sortedBy { it.startPositionMs }
        val current = ordered.lastOrNull { it.startPositionMs <= positionMs } ?: ordered.firstOrNull() ?: return
        val nextStart = ordered.firstOrNull { it.startPositionMs > current.startPositionMs }?.startPositionMs
        val chapterEnd = StatisticsRules.chapterEndMs(current.startPositionMs, nextStart, editionEndMs)
        if (!StatisticsRules.isChapterCompleted(positionMs, current.startPositionMs, chapterEnd)) return

        val recorded = completedPerEdition.getOrPut(editionId) { mutableSetOf() }
        if (!recorded.add(current.id)) return
        writer(ChapterCompletionEntity(chapterId = current.id, editionId = editionId, completedAtMs = clock()))
    }
}