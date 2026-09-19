package com.example.audiobook.notifications

import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.ProgressStatus
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * إشعار إكمال الكتاب (قناة achievements). القاعدة: عند أول وصول فعلي لنهاية
 * النسخة (status = FINISHED) تُصدر إشعارًا بالعنوان والمؤلف، مرة واحدة لكل
 * اكتمال، مع حدّ 24 ساعة لمنع إعادة التنبيه إذا أعيدت نهاية الكتاب
 * ([AppSettings.markBookCompletionNotified]).
 */
@Singleton
class BookCompletionNotifier @Inject constructor(
    private val database: AppDatabase,
    private val appSettings: AppSettings,
    private val notificationCenter: AtherNotificationCenter
) {
    suspend fun onPlaybackEnded(editionId: UUID) {
        val progress = database.progressDao().getByParent(editionId) ?: return
        if (progress.status != ProgressStatus.FINISHED) return
        val edition = database.editionDao().getById(editionId) ?: return
        val book = database.bookDao().getById(edition.bookId) ?: return
        val now = System.currentTimeMillis()
        if (now - appSettings.lastBookCompletionNotifiedAt(book.id) < BOOK_COMPLETION_THROTTLE_MS) return
        val author = database.authorDao().getById(book.authorId)
        appSettings.markBookCompletionNotified(book.id)
        notificationCenter.showBookCompleted(book.title, author?.name)
    }

    companion object {
        const val BOOK_COMPLETION_THROTTLE_MS = 24 * 60 * 60 * 1000L
    }
}