package com.example.audiobook.presentation.splash

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * شاشة ترحيب "أثير": الاسم العربي بارزًا و"Ather" بعلامة لاتينية مصغرة تحته،
 * مع موتيف صوت هادئ — تتوارى بعد لحظة قصيرة دون أن تُحفظ في حزمة التنقل.
 */
@Composable
fun AtherSplash(onFinished: () -> Unit, durationMs: Long = 1_600L) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        onFinished()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AudioBars(
                modifier = Modifier.size(width = 72.dp, height = 40.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "أثير",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "ATHER",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DrawScope.AudioBars(color: Color) {
    val barW = size.width / 7f
    val bars = floatArrayOf(0.45f, 0.72f, 1f, 0.6f)
    bars.forEachIndexed { i, h ->
        val bx = i * (barW * 1.6f) + barW * 0.4f
        val bh = size.height * h
        drawRoundRect(
            color = color,
            topLeft = Offset(bx, size.height - bh),
            size = Size(barW, bh),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
        )
    }
}

@Composable
private fun AudioBars(modifier: Modifier, color: Color) {
    Canvas(modifier) { AudioBars(color) }
}