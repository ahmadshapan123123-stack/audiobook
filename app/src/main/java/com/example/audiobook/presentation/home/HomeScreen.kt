package com.example.audiobook.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    onOpenPlayer: (UUID) -> Unit,
    onOpenLibrarySection: (String) -> Unit,
    onBookSelected: (UUID) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSeries: (UUID) -> Unit = {},
    onOpenAuthor: (UUID) -> Unit = {},
    onOpenCollection: (UUID) -> Unit = {},
    onOpenListenNow: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    val bookClick: (HomeBook) -> Unit = { book ->
        onBookSelected(book.bookId)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            CosmicScreenHeader(
                title = stringResource(R.string.home_greeting),
                subtitle = stringResource(R.string.home_subtitle),
                collapsed = collapsed
            )
            Spacer(Modifier.height(AppSpacing.md))

            if (state.isLoading) {
                Spacer(Modifier.height(AppSpacing.xxl))
            } else if (state.totalBooks == 0) {
                HomeEmptyState(onExplore = { onOpenLibrarySection("ALL_BOOKS") })
            } else {
                state.continueListening?.let { cont ->
                    HomeSectionTitle(stringResource(R.string.home_continue_title))
                    ContinueFeaturedCard(
                        cont = cont,
                        onOpenCard = { onBookSelected(it) },
                        onOpenPlayer = onOpenPlayer
                    )
                }

                HomeListenNowCard(
                    onClick = onOpenListenNow,
                    modifier = Modifier.padding(top = AppSpacing.md)
                )

                if (state.nextUp.isNotEmpty()) {
                    HomeSectionTitle(stringResource(R.string.home_next_title))
                    LazyRow(
                        contentPadding = PaddingValues(end = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(state.nextUp, key = { it.bookId }) { item ->
                            HomeBookCard(item, Modifier.width(132.dp), onClick = { bookClick(item) })
                        }
                    }
                }

                if (state.recentlyListened.isNotEmpty()) {
                    HomeSectionTitle(
                        text = stringResource(R.string.home_recent_title),
                        actionLabel = stringResource(R.string.home_view_all),
                        onAction = { onOpenHistory() }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(end = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(state.recentlyListened, key = { it.bookId }) { item ->
                            HomeBookCard(item, Modifier.width(132.dp), onClick = { bookClick(item) })
                        }
                    }
                }

                if (state.series.isNotEmpty()) {
                    HomeSectionPanel(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(top = AppSpacing.sm)
                    ) {
                        HomeSectionTitle(stringResource(R.string.home_series_title))
                        LazyRow(
                            contentPadding = PaddingValues(end = AppSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            items(state.series, key = { it.seriesId }) { s ->
                                HomeSeriesCard(s, onClick = { onOpenSeries(s.seriesId) })
                            }
                        }
                    }
                }

                if (state.authors.isNotEmpty()) {
                    HomeSectionTitle(stringResource(R.string.home_authors_title))
                    LazyRow(
                        contentPadding = PaddingValues(end = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(state.authors, key = { it.authorId }) { a ->
                            HomeAuthorCard(a, onClick = { onOpenAuthor(a.authorId) })
                        }
                    }
                }

                if (state.collections.isNotEmpty()) {
                    HomeSectionTitle(stringResource(R.string.home_collections_title))
                    LazyRow(
                        contentPadding = PaddingValues(end = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(state.collections, key = { it.collectionId }) { c ->
                            HomeCollectionCard(c, onClick = { onOpenCollection(c.collectionId) })
                        }
                    }
                }

                if (state.favorites.isNotEmpty()) {
                    HomeSectionTitle(
                        text = stringResource(R.string.home_favorites_title),
                        actionLabel = stringResource(R.string.home_view_all_favorites),
                        onAction = { onOpenLibrarySection("FAVORITES") }
                    )
                    LazyRow(
                        contentPadding = PaddingValues(end = AppSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        items(state.favorites, key = { it.bookId }) { item ->
                            HomeBookCard(item, Modifier.width(132.dp), onClick = { bookClick(item) })
                        }
                    }
                }
            }

            Spacer(Modifier.height(bottomContentInset()))
        }
    }
}

@Composable
internal fun HomeSectionTitle(
    text: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, modifier = Modifier.minTouchTarget()) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun HomeSectionPanel(
    color: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.md))
            .background(color)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
    ) {
        content()
    }
}

@Composable
internal fun HomeBookCard(
    book: HomeBook,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AtherCoverBlock(
            title = book.title,
            coverColor = Color(book.coverColor.toInt()),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
        )
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = book.authorName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        book.seriesName?.let {
            Text(
                text = stringResource(R.string.home_series_of, it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (book.hasProgress) {
            LinearProgressIndicator(
                progress = { book.progressFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
internal fun ContinueFeaturedCard(
    cont: HomeContinue,
    onOpenCard: (UUID) -> Unit,
    onOpenPlayer: (UUID) -> Unit
) {
    val book = cont.book
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.md))
            .clickable(onClick = { onOpenCard(book.bookId) })
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Cosmic.StardustViolet.copy(alpha = 0.24f),
                            Cosmic.Teal.copy(alpha = 0.14f),
                            Color.Transparent
                        )
                    )
                )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AtherCoverBlock(
                title = book.title,
                coverColor = Color(book.coverColor.toInt()),
                modifier = Modifier.width(116.dp).aspectRatio(0.72f)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Text(
                    text = stringResource(R.string.continue_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.authorName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.seriesName?.let {
                    Text(
                        text = stringResource(R.string.home_series_of, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                cont.currentChapterTitle?.let {
                    Text(
                        text = stringResource(R.string.home_current_chapter, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                LinearProgressIndicator(
                    progress = { book.progressFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.progress_percent_remaining,
                            (book.progressFraction * 100).roundToInt(),
                            formatRemaining(book.remainingMs)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { book.editionId?.let(onOpenPlayer) },
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = stringResource(R.string.home_continue_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeListenNowCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val onPrimary = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.md),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(AppSpacing.sm))
                    .background(onPrimary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = null,
                    tint = onPrimary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.listen_now_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = onPrimary
                )
                Text(
                    text = stringResource(R.string.listen_now_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = onPrimary.copy(alpha = 0.8f)
                )
            }
            TextButton(onClick = onClick, modifier = Modifier.minTouchTarget()) {
                Text(
                    text = stringResource(R.string.listen_now_open),
                    style = MaterialTheme.typography.labelLarge,
                    color = onPrimary
                )
            }
        }
    }
}

@Composable
internal fun HomeSeriesCard(series: HomeSeries, onClick: () -> Unit) {
    val covers = series.books.take(4)
    Column(
        modifier = Modifier.width(160.dp)
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.xs))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            covers.chunked(2).forEach { rowCovers ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowCovers.forEach { b ->
                        AtherCoverBlock(
                            title = b.title,
                            coverColor = Color(b.coverColor.toInt()),
                            modifier = Modifier.weight(1f).aspectRatio(1.05f)
                        )
                    }
                    if (rowCovers.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            if (covers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .aspectRatio(1.05f)
                        .clip(RoundedCornerShape(AppSpacing.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
        Text(
            text = series.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = series.authorName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(R.plurals.book_count, series.books.size, series.books.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeAuthorCard(author: HomeAuthor, onClick: () -> Unit) {
    val sample = author.books.take(2)
    Column(
        modifier = Modifier.width(160.dp)
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.xs))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sample.forEach { b ->
                AtherCoverBlock(
                    title = b.title,
                    coverColor = Color(b.coverColor.toInt()),
                    modifier = Modifier.weight(1f).aspectRatio(0.9f)
                )
            }
            repeat(2 - sample.size) { Spacer(Modifier.weight(1f)) }
        }
        Text(
            text = author.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(R.plurals.book_count, author.books.size, author.books.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeCollectionCard(collection: HomeCollection, onClick: () -> Unit) {
    val covers = collection.books.take(4)
    Column(
        modifier = Modifier.width(160.dp)
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.xs))
            .clickable(onClick = onClick)
            .padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            if (covers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(AppSpacing.xs))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            } else {
                covers.forEachIndexed { i, b ->
                    AtherCoverBlock(
                        title = b.title,
                        coverColor = Color(b.coverColor.toInt()),
                        modifier = Modifier.height(132.dp)
                            .aspectRatio(0.72f)
                            .offset(x = 22.dp * (i - (covers.size - 1) / 2f))
                    )
                }
            }
        }
        Text(
            text = collection.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = pluralStringResource(R.plurals.book_count, collection.books.size, collection.books.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun HomeEmptyState(onExplore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Icon(
            imageVector = Icons.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Text(
            text = stringResource(R.string.home_empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.home_empty_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onExplore, modifier = Modifier.minTouchTarget()) {
            Text(stringResource(R.string.home_explore_library))
        }
    }
}

@Composable
internal fun formatRemaining(ms: Long): String {
    if (ms <= 0L) return stringResource(R.string.time_done)
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.time_remaining_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.time_remaining_minutes, minutes)
    }
}