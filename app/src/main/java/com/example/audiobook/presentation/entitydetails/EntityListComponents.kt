package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.minTouchTarget
import java.util.UUID

/** اتجاه فرز قوائم المؤلفين والسلاسل. */
internal enum class EntitySort { NAME, BOOKS }

/** إجراء من قائمة "المزيد" في صف قائمة (مؤلف/سلسلة). */
internal data class EntityMenuAction(
    val labelRes: Int,
    val danger: Boolean = false,
    val action: () -> Unit
)

/** شريط البحث الموحّد لقوائم المؤلفين والسلاسل. */
@Composable
internal fun EntitySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(hint) },
        leadingIcon = {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        } else null,
        modifier = modifier.fillMaxWidth()
    )
}

/** اختيار اتجاه الفرز (الاسم / عدد الكتب). */
@Composable
internal fun EntitySortSelector(
    sort: EntitySort,
    onSortChange: (EntitySort) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        FilterChip(
            selected = sort == EntitySort.NAME,
            onClick = { onSortChange(EntitySort.NAME) },
            label = { Text(stringResource(R.string.entity_sort_name)) }
        )
        FilterChip(
            selected = sort == EntitySort.BOOKS,
            onClick = { onSortChange(EntitySort.BOOKS) },
            label = { Text(stringResource(R.string.entity_sort_books)) }
        )
    }
}

/** صورة رمزية ممتلئة بحرف أول لأسماء المؤلفين والسلاسل في القوائم. */
@Composable
private fun EntityAvatarBlock(title: String, color: Color, size: Int, circle: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(if (circle) CircleShape else RoundedCornerShape(AppSpacing.sm))
            .background(color.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.trim().take(1).takeIf { it.isNotEmpty() } ?: "؟",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 1
        )
    }
}

/**
 * صف عنصر في قائمة (مؤلف/سلسلة): صورة رمزية + عنوان + تعداد + قائمة "المزيد"
 * بإجراءات سياقية (فتح التفاصيل / إعادة تسمية / حذف).
 *
 * REDESIGN: أفاتار دائري/حجم اختياري + شريط تقدّم + شيفرون — كلها افتراضية
 * مطفأة فلا تتأثر الشاشات القديمة.
 */
@Composable
internal fun EntityListRowCard(
    title: String,
    subtitle: String,
    avatarTitle: String,
    avatarColor: Color,
    onClick: () -> Unit,
    menuActions: List<EntityMenuAction>,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    avatarSizeDp: Int = 48,
    avatarCircle: Boolean = false,
    progressFraction: Float? = null,
    showChevron: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntityAvatarBlock(title = avatarTitle, color = avatarColor, size = avatarSizeDp, circle = avatarCircle)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progressFraction != null && progressFraction > 0f) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
        if (showChevron) {
            // شيفرون نصي صريح الاتجاه (‹ دائمًا لليسار = للأمام في RTL) —
            // أيقونات الأسهم الأحادية غائبة عن نسخة icons المثبتة.
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.minTouchTarget()) {
                Icon(
                    Icons.Outlined.MoreVert,
                    contentDescription = stringResource(R.string.entity_more),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                menuActions.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(item.labelRes),
                                color = if (item.danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        onClick = {
                            menuOpen = false
                            item.action()
                        }
                    )
                }
            }
        }
    }
}

/** نافذة اختيار كتاب من المكتبة لإضافته لمؤلف/سلسلة — ببحث عربي محسّن. */
@Composable
internal fun PickBookDialog(
    candidates: List<EntityBookRow>,
    titleRes: Int,
    emptyMessage: String,
    onSelect: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = candidates.filter { ArabicSearchNormalizer.matches(query, it.title, it.authorName, it.seriesName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 440.dp)) {
                EntitySearchField(query = query, onQueryChange = { query = it }, hint = stringResource(R.string.book_picker_search))
                Spacer(Modifier.height(AppSpacing.sm))
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.no_results_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        filtered.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minTouchTarget()
                                    .clip(RoundedCornerShape(AppSpacing.sm))
                                    .clickable { onSelect(row.bookId) }
                                    .padding(AppSpacing.xs),
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AtherCoverBlock(
                                    title = row.title,
                                    coverColor = Color(row.coverColor.toInt()),
                                    modifier = Modifier.size(40.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(1.dp)
                                ) {
                                    Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val sub = listOfNotNull(row.authorName, row.seriesName).joinToString(" · ")
                                    if (sub.isNotBlank()) {
                                        Text(
                                            sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

/** نافذة إعادة ترتيب كتب السلسلة: أسهم لأعلى/لأسفل ثم "تم" للحفظ. */
@Composable
internal fun ReorderBooksDialog(
    order: List<EntityBookRow>,
    onDone: (List<UUID>) -> Unit,
    onDismiss: () -> Unit
) {
    val items = remember(order) { mutableStateOf(order) }
    fun move(index: Int, delta: Int) {
        val list = items.value.toMutableList()
        val newIndex = index + delta
        if (newIndex in list.indices) {
            val tmp = list[index]
            list[index] = list[newIndex]
            list[newIndex] = tmp
            items.value = list
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.series_menu_reorder)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                items.value.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(24.dp)
                        )
                        AtherCoverBlock(
                            title = row.title,
                            coverColor = Color(row.coverColor.toInt()),
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            row.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { move(index, -1) },
                            enabled = index > 0,
                            modifier = Modifier.minTouchTarget()
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowUp, contentDescription = stringResource(R.string.reorder_up))
                        }
                        IconButton(
                            onClick = { move(index, 1) },
                            enabled = index < items.value.lastIndex,
                            modifier = Modifier.minTouchTarget()
                        ) {
                            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = stringResource(R.string.reorder_down))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(items.value.map { it.bookId }) }) {
                Text(stringResource(R.string.series_reorder_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}