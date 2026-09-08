package com.example.audiobook.playback

data class ChapterMarker(val startPositionMs: Long)

object PlaybackNavigation {
    fun previousChapter(markers: List<ChapterMarker>, currentPositionMs: Long): Long =
        markers.lastOrNull { it.startPositionMs < currentPositionMs - 2_000L }?.startPositionMs ?: 0L

    fun nextChapter(markers: List<ChapterMarker>, currentPositionMs: Long, durationMs: Long): Long =
        markers.firstOrNull { it.startPositionMs > currentPositionMs + 1_000L }?.startPositionMs ?: durationMs
}