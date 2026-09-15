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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.navBarGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.util.UUID

/**
 * مشغّل مصغّر دائم يظهر فوق شريط التنقل السفلي في كل الشاشات
 * (باستثناء شاشة Player الكاملة) طالما فيه كتاب شغّال أو متوقف مؤقتًا.
 *
 * مواصفات الزجاجية: مشتركة مع شريط التنقل السفلي (navBarGlassStyle) —
 * Blur 20-25dp، Tint #0B0F24 @ 0.40-0.55، حافة علوية بيضاء 1dp @ 10-15%، ظل ناعم.
 */
@Composable
fun MiniPlayer(
    editionId: UUID,
    controller: PlaybackController,
    haze: HazeState,
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(28.dp),
                ambientColor = Color.Black.copy(alpha = 0.40f),
                spotColor = Color.Black.copy(alpha = 0.40f)
            )
            .clip(RoundedCornerShape(28.dp))
            .background(Cosmic.NavBarBlue.copy(alpha = 0.88f), RoundedCornerShape(28.dp))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(28.dp)
            )
            .hazeChild(haze, navBarGlassStyle())
            .clickable(onClick = onClick)
    ) {
        // ---- الزجاج العائم (البطاقة الرئيسية) ----
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            // الغلاف المصغّر — الحرف الأول + تدرج سديمي
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(AppSpacing.xxs))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Cosmic.Teal.copy(alpha = 0.75f),
                                Cosmic.StardustViolet.copy(alpha = 0.75f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.title.trim().firstOrNull()?.toString() ?: "؟",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // العنوان + اسم المؤلف
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = uiState.title.ifEmpty { "جاري التحميل..." },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (uiState.authorName.isNotBlank()) {
                    Text(
                        text = uiState.authorName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // زر التشغيل/الإيقاف المؤقت
            IconButton(
                onClick = {
                    if (playbackState.isPlaying) controller.pause()
                    else controller.play()
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "إيقاف مؤقت" else "تشغيل",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // ---- شريط التقدم الرفيع على الحافة السفلية داخل الزجاج ----
        LinearProgressIndicator(
            progress = { progressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(3.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = Color.Transparent
        )
    }
}
