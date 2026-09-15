package com.example.audiobook.presentation.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audiobook.R

fun Modifier.minTouchTarget(): Modifier = sizeIn(minWidth = 48.dp, minHeight = 48.dp)

@Composable
fun AppPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.minTouchTarget(), shape = RoundedCornerShape(AppSpacing.xs)) { Text(text) }
}

@Composable
fun AppBookCard(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) }, modifier = Modifier.minTouchTarget())
}

@Composable
fun AppProgressSlider(value: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: (() -> Unit)? = null) {
    Box(modifier = Modifier.fillMaxWidth().minTouchTarget().testTag("slider-touch-target")) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** غلاف بديل حقيقي (الحرف الأول + نمط لوني هادئ) بدل المربع الصلب الفارغ. */
@Composable
fun AtherCoverBlock(
    title: String,
    coverColor: Color,
    modifier: Modifier = Modifier,
    showMissingBadge: Boolean = false,
    missingFileDescription: String? = null
) {
    val shape = RoundedCornerShape(AppSpacing.xs)
    Box(
        modifier = modifier
            .clip(shape)
            .background(coverColor, shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.16f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.12f)
                    )
                )
            )
        )
        val letter = title.trim().firstOrNull()?.toString() ?: "؟"
        Text(
            text = letter,
            color = Color.White,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        if (showMissingBadge) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(AppSpacing.xxs).size(26.dp)
                    .clip(CircleShape).background(Color(0xCC1F2933)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = missingFileDescription ?: "ملف ناقص",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AtherCoverBlockSquare(
    title: String,
    coverColor: Color,
    modifier: Modifier = Modifier,
    showMissingBadge: Boolean = false,
    missingFileDescription: String? = null
) {
    AtherCoverBlock(
        title = title,
        coverColor = coverColor,
        modifier = modifier.aspectRatio(0.72f),
        showMissingBadge = showMissingBadge,
        missingFileDescription = missingFileDescription
    )
}

@Composable
fun AppContinueListeningCard(
    title: String,
    author: String,
    progress: Float,
    remainingLabel: String,
    coverColor: Color,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit = {}
) {
    Card(
        modifier = modifier,
        onClick = onContinue,
        shape = RoundedCornerShape(AppSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            AtherCoverBlock(title = title, coverColor = coverColor, modifier = Modifier.size(96.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(stringResource(R.string.continue_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
                Text(author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(AppSpacing.xxs))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text(
                    stringResource(R.string.progress_percent_remaining, (progress.coerceIn(0f, 1f) * 100).toInt(), remainingLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}