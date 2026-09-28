package com.example.audiobook.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** STAGE 2 — قواعد تقليص المسارات للعرض. */
class PathDisplayTest {

    @Test
    fun shortPathIsUnchanged() {
        assertEquals("a/b/c.mp3", "a/b/c.mp3".middleTruncated())
    }

    @Test
    fun longPathKeepsFirstAndLastThreeSegments() {
        val out = "root/author/series/vol1/part2/book/file.mp3".middleTruncated(32)
        assertTrue("head kept: $out", out.startsWith("root/"))
        assertTrue("tail kept: $out", out.endsWith("part2/book/file.mp3"))
        assertTrue("ellipsis present: $out", "…" in out)
        assertTrue("within limit: $out", out.length <= 32)
    }

    @Test
    fun tightLimitKeepsTailOverHead() {
        // 24 حرفًا لا تسع الرأس + الذيل معًا — الذيل (الموضع الجاري) يفوز.
        val out = "root/author/series/vol1/part2/book/file.mp3".middleTruncated(24)
        assertTrue("tail kept: $out", out.endsWith("part2/book/file.mp3"))
        assertTrue("within limit: $out", out.length <= 24)
    }

    @Test
    fun longFileNameIsCutFromTheMiddle() {
        val name = "a".repeat(60) + ".mp3"
        val out = name.middleTruncated(32)
        assertEquals(32, out.length)
        assertTrue("ellipsis present: $out", "…" in out)
    }

    @Test
    fun resultNeverExceedsLimit() {
        val path = (1..20).joinToString("/") { "folder$it" } + "/file.mp3"
        listOf(16, 24, 48, 64).forEach { limit ->
            assertTrue("limit=$limit", path.middleTruncated(limit).length <= limit)
        }
    }
}
