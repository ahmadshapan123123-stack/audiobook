package com.example.audiobook.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionTimelineTest {
    private val timeline = EditionTimeline(
        listOf(
            TimelineItem("one", 1_000, true),
            TimelineItem("two", 2_000, false),
            TimelineItem("three", 3_000, true)
        )
    )

    @Test
    fun reportsEditionWidePositionAndDuration() {
        assertEquals(6_000, timeline.durationMs)
        assertEquals(4_000, timeline.positionFor(2, 1_000))
        assertEquals(2, timeline.fileAt(3_500))
    }

    @Test
    fun identifiesMissingBoundaryInsteadOfSkippingSilently() {
        assertTrue(timeline.hasMissingAfter(0))
        assertEquals(2, timeline.nextAvailableIndex(0))
        assertEquals(0, timeline.previousAvailableIndex(2))
    }
}