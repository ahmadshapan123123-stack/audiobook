package com.example.audiobook.presentation.theme

import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryThemeTest {
    @Test
    fun libraryHasDistinctLightDarkAndTrueAmoledBackgrounds() {
        val light = appColorScheme(AppThemeMode.LIGHT)
        val dark = appColorScheme(AppThemeMode.DARK)
        val amoled = appColorScheme(AppThemeMode.AMOLED)

        assertNotEquals(light.background, dark.background)
        assertNotEquals(dark.background, amoled.background)
        assertEquals(0f, amoled.background.luminance(), 0.001f)
        assertTrue(contrast(light.onBackground.luminance(), light.background.luminance()) >= 4.5f)
        assertTrue(contrast(dark.onBackground.luminance(), dark.background.luminance()) >= 4.5f)
        assertTrue(contrast(amoled.onBackground.luminance(), amoled.background.luminance()) >= 4.5f)
    }

    private fun contrast(first: Float, second: Float): Float {
        val brighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (brighter + 0.05f) / (darker + 0.05f)
    }
}