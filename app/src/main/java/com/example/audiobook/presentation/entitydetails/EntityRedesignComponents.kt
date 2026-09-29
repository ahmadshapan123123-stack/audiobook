package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.minTouchTarget

/**
 * REDESIGN — مكونات مشتركة لصفحات الكيانات (مؤلف/سلسلة/مجموعة):
 * بطل + قائمة ⋮ + شارة إحصاء + بطاقة إكمال الاستماع.
 */

/** قسم البطل المضغوط (~200dp): أفاتار 80dp + اسم + إحصاءات + إجراء رئيسي واحد. */
@Composable
internal fun EntityHeroSection(
    avatarTitle: String,
    avatarColor: Color,
    name: String,
    stats: List<String>,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = avatarTitle.trim().take(1).takeIf { it.isNotEmpty() } ?: "؟",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                maxLines = 1
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (stats.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                stats.forEach { EntityStatChip(it) }
            }
        }
        if (primaryLabel != null && onPrimary != null) {
            Button(onClick = onPrimary, modifier = Modifier.minTouchTarget()) {
                Text(primaryLabel)
            }
        }
    }
}

/** شارة إحصاء صغيرة ("N كتاب"). */
@Composable
internal fun EntityStatChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs)
    )
}

/** زر ⋮ بقائمة إجراءات قابلة لإعادة الاستخدام (المدمّر أحمر في الأسفل). */
@Composable
internal fun EntityOverflowMenuButton(
    actions: List<EntityMenuAction>,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.minTouchTarget()) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.entity_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            actions.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(item.labelRes),
                            color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        open = false
                        item.action()
                    }
                )
            }
        }
    }
}

/** بطاقة "أكمل الاستماع": غلاف + عنوان + تقدّم + زر متابعة. */
@Composable
internal fun ContinueListeningCard(
    title: String,
    coverColor: Color,
    progressFraction: Float,
    continueLabel: String = stringResource(R.string.home_continue_play),
    onContinue: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onOpenBook)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AtherCoverBlock(
                title = title,
                coverColor = coverColor,
                modifier = Modifier.size(52.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Text(
                    stringResource(R.string.continue_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(onClick = onContinue, modifier = Modifier.minTouchTarget()) {
                Text(continueLabel)
            }
        }
        LinearProgressIndicator(
            progress = { progressFraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
