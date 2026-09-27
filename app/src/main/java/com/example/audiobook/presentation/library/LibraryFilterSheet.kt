package com.example.audiobook.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.cosmicGlassStyle
import com.example.audiobook.presentation.theme.minTouchTarget
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * ورقة التصفية والترتيب الموحّدة — تستبدل الصفوف الأربعة القديمة من الرقائق.
 *
 * التغييرات تُطبَّق **مباشرةً (live)** على `LibraryQuery` في الـViewModel بمجرّد تغيير
 * الاختيار، فترى النتائج خلف الورقة تتحدّث فورًا؛ ويبقى زر «تطبيق» لإغلاق الورقة فقط.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFilterSheet(
    section: LibrarySection,
    query: LibraryQuery,
    seriesNames: List<String>,
    genreNames: List<String>,
    activeCount: Int,
    haze: HazeState? = null,
    onSectionChange: (LibrarySection) -> Unit,
    onStatusChange: (LibraryStatusFilter) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onSeriesChange: (String?) -> Unit,
    onGenreChange: (String?) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        dragHandle = { SheetHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (haze != null) {
                        Modifier.hazeChild(
                            haze,
                            cosmicGlassStyle(
                                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                tintAlpha = 0.15f
                            )
                        )
                    } else Modifier
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = shape
                )
                .navigationBarsPadding()
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            // ── العنوان + عدّاد الفلاتر النشطة ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filter_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (activeCount > 0) {
                    Text(
                        text = pluralFilterCount(activeCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                FilterGroup(titleRes = R.string.filter_sheet_section) {
                    LibrarySection.entries.forEach { entry ->
                        FilterOptionRow(
                            label = stringResource(entry.labelRes),
                            selected = entry == section,
                            onClick = { onSectionChange(entry) }
                        )
                    }
                }

                FilterGroup(titleRes = R.string.filter_sheet_status) {
                    LibraryStatusFilter.entries.forEach { entry ->
                        FilterOptionRow(
                            label = stringResource(entry.labelRes),
                            selected = entry == query.status,
                            onClick = { onStatusChange(entry) }
                        )
                    }
                }

                FilterGroup(titleRes = R.string.filter_sheet_sort) {
                    LibrarySort.entries.forEach { entry ->
                        FilterOptionRow(
                            label = stringResource(entry.labelRes),
                            selected = entry == query.sort,
                            onClick = { onSortChange(entry) }
                        )
                    }
                }
                if (seriesNames.isNotEmpty()) {
                    FilterGroup(titleRes = R.string.filter_sheet_series) {
                        FilterOptionRow(
                            label = stringResource(R.string.filter_series_all),
                            selected = query.series == null,
                            onClick = { onSeriesChange(null) }
                        )
                        seriesNames.forEach { name ->
                            FilterOptionRow(
                                label = name,
                                selected = query.series == name,
                                onClick = { onSeriesChange(name) }
                            )
                        }
                    }
                }

                // الأنواع تُشتق من بيانات المكتبة نفسها، فلا يوجد نص نوع مثبّت في الواجهة.
                if (genreNames.isNotEmpty()) {
                    FilterGroup(titleRes = R.string.filter_sheet_genre) {
                        FilterOptionRow(
                            label = stringResource(R.string.filter_genre_all),
                            selected = query.genre == null,
                            onClick = { onGenreChange(null) }
                        )
                        genreNames.forEach { name ->
                            FilterOptionRow(
                                label = name,
                                selected = query.genre == name,
                                onClick = { onGenreChange(name) }
                            )
                        }
                    }
                }
            }

            // ── شريط سفلي: إعادة تعيين + تطبيق (تُغلق الورقة) ──
            Spacer(Modifier.height(AppSpacing.xxs))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                TextButton(
                    onClick = onReset,
                    enabled = activeCount > 0,
                    modifier = Modifier.weight(1f).minTouchTarget()
                ) {
                    Text(stringResource(R.string.filter_action_reset))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).minTouchTarget()
                ) {
                    Text(stringResource(R.string.filter_action_apply))
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier
            .size(width = 40.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
    )
}

@Composable
private fun FilterGroup(
    titleRes: Int,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

/** خيار تصفية بأسلوب زجاجي: صف كامل قابل للنقر مع علامة صح للمختار. */
@Composable
private fun FilterOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .minTouchTarget()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** نصّ الشارة: مفرد للواحد، وعدّدي لغيره. */
@Composable
private fun pluralFilterCount(count: Int): String = when (count) {
    1 -> stringResource(R.string.filter_active_count_one)
    else -> stringResource(R.string.filter_active_count, count)
}
