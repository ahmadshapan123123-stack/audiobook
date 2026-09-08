package com.example.audiobook.presentation.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlayerTimelineEditorTest {
    @Test
    fun insertingChapterBetweenTwoMarkersRenumbersEverything() {
        val first = PlayerChapter(title = "الأول", startPositionMs = 0)
        val third = PlayerChapter(title = "الثالث", startPositionMs = 20_000)
        val state = PlayerTimelineState(60_000, listOf(first, third))
        val updated = PlayerTimelineEditor.addChapter(state, 10_000, "الثاني")
        assertEquals(listOf(1, 2, 3), PlayerTimelineEditor.numbered(updated).map { it.number })
        assertEquals("الثاني", PlayerTimelineEditor.numbered(updated)[1].chapter.title)
    }

    @Test
    fun dragUpdatesTimeAndMarkCapturesBeforeChoice() {
        val first = PlayerChapter(title = "الأول", startPositionMs = 0)
        val state = PlayerTimelineState(60_000, listOf(first))
        val moved = PlayerTimelineEditor.moveChapter(state, first.id, 12_345)
        val marked = PlayerTimelineEditor.markNow(moved, 23_456)
        assertEquals(12_345L, moved.chapters.single().startPositionMs)
        assertEquals(23_456L, marked.markCaptureMs)
        assertNotEquals(marked.markCaptureMs, marked.chapters.single().startPositionMs)
    }

    @Test
    fun adaptiveSplitCreatesChaptersWhenNoneExist() {
        val chapters = PlayerTimelineEditor.adaptiveAutoSplit(75 * 60_000L, 30)
        assertEquals(3, chapters.size)
        assertEquals(30 * 60_000L, chapters[1].startPositionMs)
    }
}