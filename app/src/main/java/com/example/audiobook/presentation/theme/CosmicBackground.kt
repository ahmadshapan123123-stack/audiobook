package com.example.audiobook.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.random.Random

private data class CosmicSpec(val top: Color, val bottom: Color, val starCount: Int, val baseAlpha: Float)

/**
 * طبقة "أثير الكوني": تدرج سماوي + سدم ناعمة + نجوم ساكنة (الحركة تُفعَّل في C7 مع
 * احترام إعداد "تقليل الحركة"). تظهر خلف كل المحتوى عبر الـShell.
 */
@Composable
fun CosmicBackground(
    mode: AppThemeMode,
    modifier: Modifier = Modifier
) {
    val (top, bottom, count, baseAlpha) = when (mode) {
        AppThemeMode.LIGHT -> CosmicSpec(Cosmic.DawnTop, Cosmic.DawnBottom, 46, 0.30f)
        AppThemeMode.DARK -> CosmicSpec(Cosmic.InkTop, Cosmic.InkBottom, 88, 0.70f)
        AppThemeMode.AMOLED -> CosmicSpec(Color.Black, Color(0xFF020307), 108, 0.92f)
    }
    Canvas(modifier = modifier) {
        drawRect(Brush.verticalGradient(listOf(top, bottom)))

        val blobs = listOf(
            Triple(size.width * 0.20f, size.height * 0.16f, Cosmic.StardustViolet),
            Triple(size.width * 0.86f, size.height * 0.30f, Cosmic.StardustMagenta),
            Triple(size.width * 0.52f, size.height * 0.88f, Cosmic.StardustAmber)
        )
        blobs.forEach { (cx, cy, color) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(cx, cy)
            )
        }

        val rng = Random(1337L)
        repeat(count) {
            val x = rng.nextFloat() * size.width
            val y = rng.nextFloat() * size.height
            val r = (0.6f + rng.nextFloat() * 1.7f).dp.toPx()
            val a = baseAlpha * (0.4f + rng.nextFloat() * 0.6f)
            drawCircle(color = Cosmic.MoonIce.copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}