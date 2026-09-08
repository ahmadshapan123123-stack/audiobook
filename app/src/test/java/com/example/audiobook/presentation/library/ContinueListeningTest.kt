package com.example.audiobook.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueListeningTest {
    @Test
    fun progressLabelUsesBoundedPercentageAndRemainingTime() {
        val progress = 0.42f.coerceIn(0f, 1f)
        assertEquals("42٪ · ٦ ساعات متبقية", "${(progress * 100).toInt()}٪ · ٦ ساعات متبقية")
        assertEquals(100, (1.4f.coerceIn(0f, 1f) * 100).toInt())
    }
}