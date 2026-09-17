package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.audiobook.presentation.theme.AppThemeMode

data class PlayerGradient(val start: Color, val end: Color, val source: GradientSource)
enum class GradientSource { SERIES, AUTHOR, COVER, DEFAULT }

object PlayerGradientResolver {
    /** سطوع بداية التدرج الذي يضمن بياض readable (نسبة تباين ≈4.5:1 فأعلى) — هدف تعتيم الوضع الفاتح. */
    internal const val READABLE_DARK_LUMINANCE = 0.18f
    /** أي بداية تدرج أعلى من هذا تكتفي بحبر داكن (تباين ≥4.5:1) ولا تُعتَّم في الوضع الفاتح. */
    internal const val DARK_INK_MIN_LUMINANCE = 0.29f

    fun resolve(
        mode: AppThemeMode,
        seriesColor: Color?,
        authorColor: Color?,
        coverColor: Color?
    ): PlayerGradient {
        val base = when {
            seriesColor != null -> PlayerGradient(seriesColor, seriesColor.deepenedEnd(), GradientSource.SERIES)
            authorColor != null -> PlayerGradient(authorColor, authorColor.deepenedEnd(), GradientSource.AUTHOR)
            coverColor != null -> PlayerGradient(coverColor, coverColor.deepenedEnd(), GradientSource.COVER)
            else -> defaultGradient(mode)
        }
        return base.adjustForLightReadability(mode)
    }

    private fun defaultGradient(mode: AppThemeMode) = when (mode) {
        AppThemeMode.LIGHT -> PlayerGradient(Color(0xFFE4D7C6), Color(0xFFF5F1EA), GradientSource.DEFAULT)
        AppThemeMode.DARK -> PlayerGradient(Color(0xFF243B45), Color(0xFF18242B), GradientSource.DEFAULT)
        AppThemeMode.AMOLED -> PlayerGradient(Color(0xFF123B38), Color.Black, GradientSource.DEFAULT)
    }

    private fun PlayerGradient.adjustForLightReadability(mode: AppThemeMode): PlayerGradient {
        if (mode != AppThemeMode.LIGHT) return this
        val startLum = start.luminance()
        if (startLum <= READABLE_DARK_LUMINANCE || startLum >= DARK_INK_MIN_LUMINANCE) return this
        val factor = READABLE_DARK_LUMINANCE / startLum
        return PlayerGradient(start.scaledToLuminanceFactor(factor), end.scaledToLuminanceFactor(factor), source)
    }

    private fun Color.scaledToLuminanceFactor(factor: Float) = copy(
        red = (red * factor).coerceIn(0f, 1f),
        green = (green * factor).coerceIn(0f, 1f),
        blue = (blue * factor).coerceIn(0f, 1f),
        alpha = alpha
    )

    private fun Color.deepenedEnd(): Color = Color(
        red = (red * 0.55f).coerceIn(0f, 1f),
        green = (green * 0.55f).coerceIn(0f, 1f),
        blue = (blue * 0.55f).coerceIn(0f, 1f),
        alpha = alpha
    )
}