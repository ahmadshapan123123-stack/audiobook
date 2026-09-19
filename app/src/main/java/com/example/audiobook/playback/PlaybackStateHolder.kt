package com.example.audiobook.playback

import java.util.UUID

/** معرف النسخة قيد التشغيل — تهمّه الإشعارات التي تُفتح إلى المشغّل (مؤقت النوم وغيره). */
object PlaybackStateHolder {
    @Volatile
    var editionId: UUID? = null
        private set

    fun update(editionId: UUID?) {
        this.editionId = editionId
    }
}