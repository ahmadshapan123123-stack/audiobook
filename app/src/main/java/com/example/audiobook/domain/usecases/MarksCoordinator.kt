package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class MarksCoordinator @Inject constructor(private val database: AppDatabase) {
    fun chapters(editionId: UUID): Flow<List<ChapterEntity>> = database.chapterDao().observeByParent(editionId)
    fun bookmarks(editionId: UUID): Flow<List<BookmarkEntity>> = database.bookmarkDao().observeByParent(editionId)

    suspend fun addBookmark(editionId: UUID, positionMs: Long, note: String? = null) {
        database.bookmarkDao().insert(BookmarkEntity(editionId = editionId, positionMs = positionMs, createdAt = System.currentTimeMillis(), type = if (note == null) BookmarkType.BOOKMARK else BookmarkType.NOTE, noteText = note, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
    }

    suspend fun addChapter(editionId: UUID, positionMs: Long, title: String = "فصل جديد"): UUID {
        val chapter = ChapterEntity(editionId = editionId, title = title, startPositionMs = positionMs, orderIndex = Int.MAX_VALUE, createdFrom = ChapterCreatedFrom.USER_MARK)
        database.chapterDao().insert(chapter)
        reorderChapters(editionId)
        return chapter.id
    }

    suspend fun updateChapter(chapter: ChapterEntity, positionMs: Long, title: String = chapter.title ?: "فصل") {
        database.chapterDao().update(chapter.copy(startPositionMs = positionMs, title = title))
        reorderChapters(chapter.editionId)
    }

    suspend fun deleteChapter(chapter: ChapterEntity) = database.chapterDao().delete(chapter)
    suspend fun deleteBookmark(bookmark: BookmarkEntity) = database.bookmarkDao().delete(bookmark)
    suspend fun seekToBookmark(controller: com.example.audiobook.playback.PlaybackController, bookmark: BookmarkEntity) = controller.seekTo(bookmark.positionMs)

    private suspend fun reorderChapters(editionId: UUID) {
        database.chapterDao().getByParent(editionId).sortedBy { it.startPositionMs }.forEachIndexed { index, chapter ->
            if (chapter.orderIndex != index) database.chapterDao().update(chapter.copy(orderIndex = index))
        }
    }
}