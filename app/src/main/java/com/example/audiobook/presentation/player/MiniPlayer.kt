package com.example.audiobook.presentation.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.domain.model.AppThemeMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import java.util.UUID

/**
 * مشغّل مصغّر دائم يظهر فوق شريط التنقل السفلي في كل الشاشات
 * (باستثناء شاشة Player الكاملة) طالما فيه كتاب شغّال أو متوقف مؤقتًا.
 *
 * الزجاجية هنا مشتقة من تدرج الكتاب نفسه (السلسلة/المؤلف/الغلاف) عبر
 * [PlayerGradientResolver] + ميّز [playerForeground]: سطح زجاجي بلون
 * الكتاب المموّه بالمادة اللونية للوضع، حافة بُبنية popup، وأكسانت من بداية
 * التدرج — لا أزرق/بنفسجي ثابتين، ولا Material primary.
 */
@Composable
fun MiniPlayer(
    editionId: UUID,
    controller: PlaybackController,
    haze: HazeState,
    mode: AppThemeMode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MiniPlayerViewModel = hiltViewModel()
) {
    LaunchedEffect(editionId) { viewModel.observeEdition(editionId) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by controller.state.collectAsStateWithLifecycle()

    val progressFraction = if (playbackState.durationMs > 0L) {
        (playbackState.positionMs.toFloat() / playbackState.durationMs).coerceIn(0f, 1f)
    } else 0f

    val resolved = PlayerGradientResolver.resolve(
        mode = mode,
        seriesColor = uiState.seriesColor,
        authorColor = uiState.authorColor,
        coverColor = uiState.coverColor
    )
    val glass = when (mode) {
        AppThemeMode.LIGHT -> lerp(resolved.start, Color(0xFFF2EEE3), 0.62f).copy(alpha = 0.92f)
        AppThemeMode.DARK -> lerp(resolved.start, Color(0xFF0B0F24), 0.55f).copy(alpha = 0.86f)
        AppThemeMode.AMOLED -> lerp(resolved.start, Color.Black, 0.82f).copy(alpha = 0.90f)
    }
    val face = playerForeground(
        gradient = PlayerGradient(start = glass, end = glass, source = resolved.source),
        mode = mode
    )
    val onLightBackground = glass.luminance() >= 0.25f
    val accent = resolved.start.playerAccent(onLightBackground = onLightBackground)
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White
    val fg = PlayerFg(
        ink = face.ink,
        soft = face.soft,
        colors = face.colors.copy(accent = accent, onAccent = onAccent)
    )
    val coverFg = playerForeground(resolved, mode).ink
    val shape = RoundedCornerShape(28.dp)
    val miniGlassStyle = HazeStyle(
        backgroundColor = glass,
        tint = HazeTint(accent.copy(alpha = 0.06f)),
        blurRadius = 30.dp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md)
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(shape)
            .background(glass, shape)
            .border(1.dp, fg.colors.popupOutline, shape)
            .hazeChild(haze, miniGlassStyle)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            // الغلاف المصغّر — الحرف الأول + تدرج الكتاب الحقيقي
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppSpacing.xxs))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(resolved.start, resolved.end)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.title.trim().firstOrNull()?.toString() ?: "؟",
                    color = coverFg,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title.ifEmpty { "جاري التحميل..." },
                    style = MaterialTheme.typography.titleSmall,
                    color = fg.colors.popupInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.authorName.isNotBlank()) {
                    Text(
                        text = uiState.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = fg.colors.popupSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    if (playbackState.isPlaying) controller.pause()
                    else controller.play()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, fg.colors.popupOutline, CircleShape)
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                    tint = accent,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(3.dp),
            color = accent,
            trackColor = Color.Transparent
        )
    }
}