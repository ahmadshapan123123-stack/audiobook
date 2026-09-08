package com.example.audiobook.presentation.player

import java.util.UUID
import kotlin.math.ceil

enum class TimelineLevel { OVERVIEW, ZOOMED }

data class PlayerChapter(
    val id: UUID = UUID.randomUUID(),
    val title: String,
    val startPositionMs: Long
)

data class PlayerBookmark(val id: UUID = UUID.randomUUID(), val positionMs: Long, val label: String? = null)

data class NumberedChapter(val number: Int, val chapter: PlayerChapter)

data class PlayerTimelineState(
    val durationMs: Long,
    val chapters: List<PlayerChapter> = emptyList(),
    val bookmarks: List<PlayerBookmark> = emptyList(),
    val level: TimelineLevel = TimelineLevel.OVERVIEW,
    val zoomCenterMs: Long = 0L,
    val markCaptureMs: Long? = null
)

object PlayerTimelineEditor {
    fun numbered(state: PlayerTimelineState): List<NumberedChapter> =
        state.chapters.sortedBy { it.startPositionMs }.mapIndexed { index, chapter -> NumberedChapter(index + 1, chapter) }

    fun addChapter(state: PlayerTimelineState, positionMs: Long, title: String = "فصل جديد"): PlayerTimelineState =
        state.copy(chapters = (state.chapters + PlayerChapter(title = title, startPositionMs = positionMs.coerceIn(0L, state.durationMs))).sortedBy { it.startPositionMs })

    fun moveChapter(state: PlayerTimelineState, chapterId: UUID, newPositionMs: Long): PlayerTimelineState =
        state.copy(chapters = state.chapters.map { chapter -> if (chapter.id == chapterId) chapter.copy(startPositionMs = newPositionMs.coerceIn(0L, state.durationMs)) else chapter }.sortedBy { it.startPositionMs })

    fun markNow(state: PlayerTimelineState, positionMs: Long): PlayerTimelineState =
        state.copy(markCaptureMs = positionMs.coerceIn(0L, state.durationMs))

    fun consumeMark(state: PlayerTimelineState, label: String?, asChapter: Boolean): PlayerTimelineState {
        val captured = state.markCaptureMs ?: return state
        return if (asChapter) state.copy(chapters = (state.chapters + PlayerChapter(title = label ?: "فصل جديد", startPositionMs = captured)).sortedBy { it.startPositionMs }, markCaptureMs = null)
        else state.copy(bookmarks = state.bookmarks + PlayerBookmark(positionMs = captured, label = label), markCaptureMs = null)
    }

    fun zoom(state: PlayerTimelineState, centerMs: Long): PlayerTimelineState =
        state.copy(level = TimelineLevel.ZOOMED, zoomCenterMs = centerMs.coerceIn(0L, state.durationMs))

    fun overview(state: PlayerTimelineState): PlayerTimelineState = state.copy(level = TimelineLevel.OVERVIEW)

    fun adaptiveAutoSplit(durationMs: Long, targetChapterMinutes: Int = 30): List<PlayerChapter> {
        if (durationMs <= 0L) return emptyList()
        val interval = targetChapterMinutes * 60_000L
        val count = ceil(durationMs.toDouble() / interval).toInt()
        return (0 until count).map { index -> PlayerChapter(title = "فصل ${index + 1}", startPositionMs = index * interval) }
    }
}