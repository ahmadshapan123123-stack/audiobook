package com.example.audiobook.presentation.home

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/**
 * [استمع الآن] (PART 5 / Phase 5) — ثلاثة أقسام فقط من كتب قيد التقدّم:
 * وقتك (رقائق) ← يناسب وقتك (ضمن النافذة) ← أكمل ما بدأته (الباقي).
 */
@Composable
fun ListeningHubScreen(
    onOpenPlayer: (UUID) -> Unit,
    onPlayWithSleepTimer: (UUID) -> Unit,
    onBookSelected: (UUID) -> Unit,
    onOpenSeries: (UUID) -> Unit,
    onOpenLibrarySection: (String) -> Unit,
    onBack: () -> Unit,
    onBookOptions: (UUID) -> Unit = {},
    onOpenSeriesList: () -> Unit = {},
    viewModel: ListeningHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        CosmicScreenHeader(
            title = stringResource(R.string.listen_now_title),
            subtitle = stringResource(R.string.listen_now_subtitle),
            collapsed = collapsed,
            onBack = onBack
        )
        Spacer(Modifier.height(AppSpacing.md))

        if (state.isLoading) {
            Spacer(Modifier.height(AppSpacing.xxl))
        } else if (state.fitsWindow.isEmpty() && state.rest.isEmpty()) {
            // PART 5: حالة فراغ واحدة فقط — لا قسمان فارغان مكرران.
            HubNoProgressCta(onExplore = { onOpenLibrarySection("ALL_BOOKS") })
        } else {
            // القسم 1 "كم من الوقت لديك؟" — الرقائق تُرشّح القسم 2 فقط.
            HomeSectionTitle(stringResource(R.string.listen_now_time_title))
            HubTimeSelector(
                options = HUB_TIME_OPTIONS_MINUTES,
                selected = state.selectedMinutes,
                onSelect = viewModel::selectTime
            )

            // القسم 2 "يناسب وقتك" — قيد التقدّم ضمن النافذة (أو الكل في "مفتوح").
            HomeSectionTitle(stringResource(R.string.listen_now_shelf_title))
            if (state.fitsWindow.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(end = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(state.fitsWindow, key = { it.bookId }) { item ->
                        HomeBookCard(item, Modifier.width(132.dp), onClick = { item.editionId?.let(onOpenPlayer) }, onBookOptions = { onBookOptions(item.bookId) })
                    }
                }
            } else {
                // النافذة أضيق من كل المتبقي — سطر واحد، والاستخدام الوحيد لهذا النص هنا.
                HubEmptyLine(
                    text = stringResource(R.string.entity_no_books),
                    icon = Icons.Outlined.PlayArrow
                )
            }

            // القسم 3 "أكمل ما بدأته" — الباقي غير المعروض أعلاه.
            if (state.rest.isNotEmpty()) {
                HomeSectionTitle(stringResource(R.string.listen_now_continue_title))
                LazyRow(
                    contentPadding = PaddingValues(end = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(state.rest, key = { it.bookId }) { item ->
                        HomeBookCard(item, Modifier.width(132.dp), onClick = { item.editionId?.let(onOpenPlayer) }, onBookOptions = { onBookOptions(item.bookId) })
                    }
                }
            }
        }

        Spacer(Modifier.height(bottomContentInset()))
    }
}

/**
 * FIX 1 — صف فارغ من سطر واحد + أيقونة (نصوص موجودة مسبقًا فقط):
 * الأقسام المختفية صامتًا كانت توحي بأن الصفحة معطوبة.
 */
@Composable
internal fun HubEmptyLine(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/**
 * PART 5: حالة فراغ واحدة لحظة غياب أي تقدّم — نص مخصص (لا entity_no_books
 * المكرر) + دعوة للمكتبة.
 */
@Composable
internal fun HubNoProgressCta(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.listen_now_empty_progress),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        androidx.compose.material3.OutlinedButton(onClick = onExplore, modifier = Modifier.minTouchTarget()) {
            Text(stringResource(R.string.home_explore_library))
        }
    }
}

/** شريط وقتك (PART 5): نوافذ 15/30/45د + ساعة + "مفتوح" (null = بلا حد). */
@Composable
internal fun HubTimeSelector(
    options: List<Int>,
    selected: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        options.forEach { minutes ->
            val label = if (minutes >= 60) stringResource(R.string.listen_now_time_hour)
            else stringResource(R.string.listen_now_time_minutes, minutes)
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = { Text(label) },
                modifier = Modifier.minTouchTarget()
            )
        }
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.listen_now_time_open)) },
            modifier = Modifier.minTouchTarget()
        )
    }
}
