package com.example.audiobook.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackNavigationTest {
    private val chapters = listOf(ChapterMarker(0), ChapterMarker(10_000), ChapterMarker(30_000))

    @Test
    fun previousAndNextUseEditionWideChapterPositions() {
        assertEquals(10_000, PlaybackNavigation.previousChapter(chapters, 20_000))
        assertEquals(30_000, PlaybackNavigation.nextChapter(chapters, 20_000, 60_000))
        assertEquals(60_000, PlaybackNavigation.nextChapter(chapters, 40_000, 60_000))
    }

    @Test
    fun missingGapStopsTransition() {
        val timeline = EditionTimeline(listOf(TimelineItem("a", 1_000, true), TimelineItem("b", 1_000, false), TimelineItem("c", 1_000, true)))
        assertEquals(true, timeline.shouldStopBeforeTransition(0, 2))
    }
}