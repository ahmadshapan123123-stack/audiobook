package com.example.audiobook.playback

import com.example.audiobook.data.room.entity.ChapterEntity
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

data class PlaybackState(
	val editionId: UUID? = null,
	val isPlaying: Boolean = false,
	val positionMs: Long = 0L,
	val durationMs: Long = 0L,
	val speed: Float = 1f,
	val sleepRemainingMs: Long? = null,
	val missingFileMessage: String? = null
)

interface PlaybackController {
	val state: StateFlow<PlaybackState>

	suspend fun openEdition(editionId: UUID)
	fun play()
	fun pause()
	fun seekTo(positionMs: Long)
	fun skipForward15Seconds()
	fun skipBack15Seconds()
	suspend fun previousChapter()
	suspend fun nextChapter()
	fun setSpeed(speed: Float)
	fun setSleepTimer(minutes: Int)
	fun cancelSleepTimer()
	fun release()
}

data class TimelineItem(val uri: String, val durationMs: Long, val available: Boolean)

class EditionTimeline(private val items: List<TimelineItem>) {
	val durationMs: Long = items.sumOf { it.durationMs }

	fun positionFor(fileIndex: Int, positionInFileMs: Long): Long =
		items.take(fileIndex).sumOf { it.durationMs } + positionInFileMs

	fun fileAt(positionMs: Long): Int? {
		if (positionMs < 0L || positionMs >= durationMs) return null
		var start = 0L
		items.forEachIndexed { index, item ->
			if (positionMs < start + item.durationMs) return index
			start += item.durationMs
		}
		return null
	}

	fun nextAvailableIndex(index: Int): Int? = (index + 1 until items.size).firstOrNull { items[it].available }
	fun previousAvailableIndex(index: Int): Int? = (index - 1 downTo 0).firstOrNull { items[it].available }
	fun hasMissingAfter(index: Int): Boolean = items.drop(index + 1).firstOrNull()?.available == false

	fun shouldStopBeforeTransition(previousOriginalIndex: Int, nextOriginalIndex: Int): Boolean =
		(previousOriginalIndex + 1 until nextOriginalIndex).any { !items[it].available }
}