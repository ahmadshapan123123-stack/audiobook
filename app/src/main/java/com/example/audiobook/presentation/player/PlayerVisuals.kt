package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.audiobook.presentation.theme.AppThemeMode

data class PlayerGradient(val start: Color, val end: Color, val source: GradientSource)
enum class GradientSource { SERIES, AUTHOR, COVER, DEFAULT }

object PlayerGradientResolver {
    fun resolve(
        mode: AppThemeMode,
        seriesColor: Color?,
        authorColor: Color?,
        coverColor: Color?
    ): PlayerGradient {
        val base = when {
            seriesColor != null -> PlayerGradient(seriesColor, seriesColor.shift(0.18f), GradientSource.SERIES)
            authorColor != null -> PlayerGradient(authorColor, authorColor.shift(0.18f), GradientSource.AUTHOR)
            coverColor != null -> PlayerGradient(coverColor, coverColor.shift(0.18f), GradientSource.COVER)
            else -> defaultGradient(mode)
        }
        return base.contrastFor(mode)
    }

    private fun defaultGradient(mode: AppThemeMode) = when (mode) {
        AppThemeMode.LIGHT -> PlayerGradient(Color(0xFFE4D7C6), Color(0xFFF5F1EA), GradientSource.DEFAULT)
        AppThemeMode.DARK -> PlayerGradient(Color(0xFF243B45), Color(0xFF18242B), GradientSource.DEFAULT)
        AppThemeMode.AMOLED -> PlayerGradient(Color(0xFF123B38), Color.Black, GradientSource.DEFAULT)
    }

    private fun PlayerGradient.contrastFor(mode: AppThemeMode): PlayerGradient {
        val minimum = if (mode == AppThemeMode.LIGHT) 0.22f else 0.1f
        return if (start.luminance() < minimum && end.luminance() < minimum && mode == AppThemeMode.LIGHT) {
            PlayerGradient(start.shift(0.25f), end.shift(0.25f), source)
        } else this
    }

    private fun Color.shift(amount: Float): Color = Color(
        red = (red + amount).coerceIn(0f, 1f),
        green = (green + amount).coerceIn(0f, 1f),
        blue = (blue + amount).coerceIn(0f, 1f),
        alpha = alpha
    )
}