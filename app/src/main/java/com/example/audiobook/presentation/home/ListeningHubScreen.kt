package com.example.audiobook.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/**
 * [استمع الآن] — أثير يجهّز لك جلسة بحسب الوقت المتاح.
 * كل قسم يُعرض بأسلوب يليق بمحتواه (بطاقة مميزة/رف/كتلة نوم/شبكة) بدل تكرار النمط نفسه.
 */
@Composable
fun ListeningHubScreen(
    onOpenPlayer: (UUID) -> Unit,
    onPlayWithSleepTimer: (UUID) -> Unit,
    onBookSelected: (UUID) -> Unit,
    onOpenSeries: (UUID) -> Unit,
    onOpenLibrarySection: (String) -> Unit,
    onBack: () -> Unit,
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
        } else if (state.totalBooks == 0) {
            HomeEmptyState(onExplore = { onOpenLibrarySection("ALL_BOOKS") })
        } else {
            HubTimeSelector(
                options = HUB_TIME_OPTIONS_MINUTES,
                selected = state.selectedMinutes,
                onSelect = viewModel::selectTime
            )

            state.featured?.let { cont ->
                HomeSectionTitle(stringResource(R.string.listen_now_fits_title))
                ContinueFeaturedCard(
                    cont = cont,
                    onOpenCard = { onBookSelected(it) },
                    onOpenPlayer = onOpenPlayer
                )
            }

            if (state.fitsWindow.isNotEmpty()) {
                HomeSectionTitle(
                    text = stringResource(R.string.listen_now_shelf_title),
                    actionLabel = stringResource(R.string.listen_now_play_all),
                    onAction = { state.fitsWindow.firstOrNull()?.editionId?.let(onOpenPlayer) }
                )
                LazyRow(
                    contentPadding = PaddingValues(end = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(state.fitsWindow, key = { it.bookId }) { item ->
                        HomeBookCard(item, Modifier.width(132.dp), onClick = { item.editionId?.let(onOpenPlayer) })
                    }
                }
            }

            if (state.bedtime.isNotEmpty()) {
                HubBedtimeBlock(
                    books = state.bedtime,
                    onPlay = onPlayWithSleepTimer
                )
            }

            if (state.series.isNotEmpty()) {
                HomeSectionTitle(stringResource(R.string.listen_now_series_title))
                LazyRow(
                    contentPadding = PaddingValues(end = AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(state.series, key = { it.seriesId }) { s ->
                        HomeSeriesCard(s, onClick = { onOpenSeries(s.seriesId) })
                    }
                }
            }

            if (state.longSessions.isNotEmpty()) {
                HomeSectionTitle(stringResource(R.string.listen_now_long_title))
                HubLongSessionGrid(
                    books = state.longSessions,
                    onPlay = { item -> item.editionId?.let(onOpenPlayer) }
                )
            }
        }

        Spacer(Modifier.height(bottomContentInset()))
    }
}

/** شريط أزرار الوقت (أزرار تنقّل مدمجة أعلى المركز) — لا يعتمد أسلوب الرفوف. */
@Composable
internal fun HubTimeSelector(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        options.forEach { minutes ->
            FilterChip(
                selected = minutes == selected,
                onClick = { onSelect(minutes) },
                label = {
                    Text(stringResource(R.string.listen_now_time_minutes, minutes))
                },
                modifier = Modifier.minTouchTarget()
            )
        }
    }
}

/** كتلة "قبل النوم": عرض مستقل (لا رف ولا شبكة) بلمسة غسق هادئة. */
@Composable
internal fun HubBedtimeBlock(
    books: List<HomeBook>,
    onPlay: (UUID) -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(top = AppSpacing.md)
            .clip(RoundedCornerShape(AppSpacing.md))
            .background(containerColor)
            .padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Icon(
                imageVector = Icons.Outlined.DarkMode,
                contentDescription = null,
                tint = Cosmic.StardustAmber,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = stringResource(R.string.listen_now_bedtime_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.listen_now_bedtime_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val shape = RoundedCornerShape(AppSpacing.sm)
        books.forEach { book ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(shape)
                    .clickable { onPlay(book.editionId!!) }
                    .padding(vertical = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AtherCoverBlock(
                    title = book.title,
                    coverColor = Color(book.coverColor.toInt()),
                    modifier = Modifier.width(44.dp).aspectRatio(0.72f)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatRemaining(book.remainingMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { onPlay(book.editionId!!) },
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(R.string.listen_now_bedtime_play),
                        tint = Cosmic.StardustAmber
                    )
                }
            }
        }
    }
}

/** شبكة "جلسة طويلة": عرض شبكي (صفوف من بطاقتين) بدل رف أفقي للتّنويع. */
@Composable
internal fun HubLongSessionGrid(
    books: List<HomeBook>,
    onPlay: (HomeBook) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        books.chunked(2).forEach { rowBooks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                rowBooks.forEach { book ->
                    HomeBookCard(book, Modifier.weight(1f), onClick = { onPlay(book) })
                }
                if (rowBooks.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}