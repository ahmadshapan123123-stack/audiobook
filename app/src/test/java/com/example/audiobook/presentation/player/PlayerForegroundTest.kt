package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import com.example.audiobook.domain.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerForegroundTest {
    @Test
    fun darkThemeKeepsDarkGradientWhiteTextWithDarkPopupRoles() {
        val g = PlayerGradient(Color(0xFF78350F), Color(0xFF3C1A07), GradientSource.SERIES)
        val fg = playerForeground(g, AppThemeMode.DARK)
        assertEquals(Color.White, fg.ink)
        assertEquals(0.78f, fg.soft.alpha, 0.01f)
        assertFalse(fg.colors.isLight)
        assertEquals(Color(0xFFEDF2FF), fg.colors.popupInk)
        assertEquals(Color(0xFFABB4CE), fg.colors.popupSoft)
        assertEquals(Color(0x4DFFFFFF), fg.colors.popupOutline)
        assertEquals(Color(0x1EFFFFFF), fg.colors.popupSurface)
    }

    @Test
    fun scrimIsDeepAccentTintedNearBlackAndNeverFlatBlack() {
        val g = PlayerGradient(Color(0xFF78350F), Color(0xFF3C1A07), GradientSource.SERIES)
        for (mode in AppThemeMode.entries) {
            val fg = playerForeground(g, mode)
            val sc = fg.colors.scrim
            val expectedAlpha = when (mode) {
                AppThemeMode.LIGHT -> 0.40f
                AppThemeMode.DARK -> 0.50f
                AppThemeMode.AMOLED -> 0.65f
            }
            assertEquals(expectedAlpha, sc.alpha, 0.01f)
            val expect = g.start.playerAccent(onLightBackground = mode == AppThemeMode.LIGHT)
            assertEquals(expect.red * 0.18f, sc.red, 0.01f)
            assertEquals(expect.green * 0.18f, sc.green, 0.01f)
            assertEquals(expect.blue * 0.18f, sc.blue, 0.01f)
            assertTrue("scrim must not be pure black", sc != Color.Black)
        }
    }

    @Test
    fun lightThemeDarkGradientUsesWhiteInkAndLightPopupRoles() {
        val g = PlayerGradient(Color(0xFF78350F), Color(0xFF3C1A07), GradientSource.SERIES)
        val fg = playerForeground(g, AppThemeMode.LIGHT)
        assertEquals(Color.White, fg.ink)
        assertEquals(0.78f, fg.soft.alpha, 0.01f)
        assertTrue(fg.colors.isLight)
        assertEquals(Color(0xFFEDF2FF), fg.colors.popupInk)
        assertEquals(Color(0xFFABB4CE), fg.colors.popupSoft)
        assertEquals(Color(0x4D000000), fg.colors.popupOutline)
        assertEquals(Color(0x1E000000), fg.colors.popupSurface)
    }

    @Test
    fun lightThemeLightGradientUsesDarkInkWhilePopupRolesStayThemeDriven() {
        val g = PlayerGradient(Color(0xFFE8B88A), Color(0xFFB07E4F), GradientSource.SERIES)
        val fg = playerForeground(g, AppThemeMode.LIGHT)
        assertEquals(Color(0xFF1F2A44), fg.ink)
        assertEquals(0.72f, fg.soft.alpha, 0.01f)
        assertEquals(Color(0xFF1D1B3B), fg.colors.popupInk)
        assertTrue(fg.colors.isLight)
    }

    @Test
    fun accentStaysDerivedFromGradientStartInEveryMode() {
        for (mode in AppThemeMode.entries) {
            val g = PlayerGradient(Color(0xFF78350F), Color(0xFF3C1A07), GradientSource.SERIES)
            val fg = playerForeground(g, mode)
            assertTrue(fg.colors.accent != Color.Unspecified)
            val expected = g.start.playerAccent(onLightBackground = mode == AppThemeMode.LIGHT)
            assertEquals(expected, fg.colors.accent)
        }
    }
}