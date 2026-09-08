package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import com.example.audiobook.presentation.theme.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerGradientTest {
    @Test
    fun followsSeriesThenAuthorThenCoverThenDefaultPriority() {
        val series = Color(0xFF8B4A3C)
        val author = Color(0xFF2A6C68)
        val cover = Color(0xFF5D507A)
        assertEquals(GradientSource.SERIES, PlayerGradientResolver.resolve(AppThemeMode.DARK, series, author, cover).source)
        assertEquals(GradientSource.AUTHOR, PlayerGradientResolver.resolve(AppThemeMode.DARK, null, author, cover).source)
        assertEquals(GradientSource.COVER, PlayerGradientResolver.resolve(AppThemeMode.DARK, null, null, cover).source)
        assertEquals(GradientSource.DEFAULT, PlayerGradientResolver.resolve(AppThemeMode.DARK, null, null, null).source)
    }

    @Test
    fun defaultGradientChangesForEachThemeAndAmoledEndsInBlack() {
        val light = PlayerGradientResolver.resolve(AppThemeMode.LIGHT, null, null, null)
        val dark = PlayerGradientResolver.resolve(AppThemeMode.DARK, null, null, null)
        val amoled = PlayerGradientResolver.resolve(AppThemeMode.AMOLED, null, null, null)
        assertNotEquals(light.start, dark.start)
        assertNotEquals(dark.start, amoled.start)
        assertTrue(amoled.end == Color.Black)
    }
}