package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.minTouchTarget

/** رأس صفحة تفاصيل (سلسلة/مؤلف/مجموعة): غلاف + اسم + تسمية ثانوية + عدد الكتب. */
@Composable
internal fun EntityHeaderBlock(
    name: String,
    subtitle: String,
    count: Int,
    coverTitle: String,
    coverColor: Color,
    modifier: Modifier = Modifier,
    onSubtitleClick: (() -> Unit)? = null,
    description: String? = null,
    imagePath: String? = null,
    onEditClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AtherCoverBlock(
            title = coverTitle,
            coverColor = coverColor,
            modifier = Modifier.width(120.dp).aspectRatio(0.72f)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            Text(name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                val base = Modifier.minTouchTarget().then(
                    if (onSubtitleClick != null) Modifier.clip(RoundedCornerShape(AppSpacing.xs)).clickable(onClick = onSubtitleClick) else Modifier
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (onSubtitleClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = base
                )
            }
            Text(
                pluralStringResource(R.plurals.book_count, count, count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** صف كتاب داخل صفحات التفاصيل: غلاف + عنوان + مؤلف/سلسلة + بقي + شريط التقدّم. */
@Composable
internal fun EntityBookRowItem(row: EntityBookRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AtherCoverBlock(
                title = row.title,
                coverColor = Color(row.coverColor.toInt()),
                modifier = Modifier.size(52.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Text(row.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(row.seriesName, row.authorName).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (row.hasProgress) {
                Text(
                    stringResource(R.string.entity_progress_remaining, formatDurationShort(row.remainingMs)),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (row.hasProgress) {
            LinearProgressIndicator(
                progress = { row.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
internal fun EntitySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = AppSpacing.sm)
    )
}

private fun formatDurationShort(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}س ${minutes}د" else "${minutes}د"
}
