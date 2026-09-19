package com.example.audiobook.presentation.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.autofill.ContentDataType
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentPadding
import com.example.audiobook.presentation.theme.minTouchTarget
import java.util.UUID

private enum class LibrarySection(val labelRes: Int) {
    ALL_BOOKS(R.string.section_all),
    CURRENTLY_LISTENING(R.string.section_current),
    FINISHED(R.string.section_finished),
    FAVORITES(R.string.section_favorites),
    COLLECTIONS(R.string.section_collections),
    RECENTLY_ADDED(R.string.section_recent)
}

private enum class LibraryLayout { GRID, LIST }

@Composable
fun LibraryScreen(
    onBookSelected: (UUID) -> Unit,
    initialSection: String = "ALL_BOOKS",
    onOpenPlayer: (UUID) -> Unit = {},
    onManageRoots: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onHistory: () -> Unit = {},
    onReviewMatches: () -> Unit = {},
    reviewBadgeCount: Int = 0,
    onSettings: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initial = runCatching { LibrarySection.valueOf(initialSection) }.getOrDefault(LibrarySection.ALL_BOOKS)
    var selectedSection by remember(initial) { mutableStateOf(initial) }
    var layout by remember { mutableStateOf(LibraryLayout.GRID) }
    var searchQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(LibraryStatusFilter.ALL) }
    var genre by remember { mutableStateOf<String?>(null) }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    val activeCollection = selectedCollection ?: uiState.collections.firstOrNull()?.name ?: ""
    var showCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val gridCollapsed = gridState.firstVisibleItemIndex > 0

    val filteredBooks = uiState.filtered
    val sectionBooks = when (selectedSection) {
        LibrarySection.ALL_BOOKS -> filteredBooks
        LibrarySection.CURRENTLY_LISTENING -> uiState.books.filter { it.progressFraction in 0.01f..0.99f }
        LibrarySection.FINISHED -> uiState.books.filter { it.progressFraction >= 1f }
        LibrarySection.FAVORITES -> uiState.books.filter { it.isFavorite }
        LibrarySection.COLLECTIONS -> uiState.collections
            .firstOrNull { it.name == activeCollection }
            ?.let { c -> uiState.books.filter { b -> b.book.id in (uiState.collectionMembers[c.name] ?: emptySet()) } }
            ?: emptyList()
        LibrarySection.RECENTLY_ADDED -> uiState.books.sortedByDescending { it.addedOrder }.take(6)
    }
    val searching = searchQuery.isNotBlank()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg)) {
        Spacer(Modifier.height(AppSpacing.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = !gridCollapsed,
                    enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
                ) {
                    Column {
                        CosmicScreenHeader(
                            title = stringResource(R.string.library_title),
                            subtitle = stringResource(R.string.library_subtitle),
                            collapsed = false,
                            compact = gridCollapsed
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            pluralStringResource(R.plurals.book_count, uiState.books.size, uiState.books.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.library_menu_more))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_menu_history)) },
                        onClick = { showMenu = false; onHistory() },
                        modifier = Modifier.minTouchTarget()
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_menu_statistics)) },
                        onClick = { showMenu = false; onStatistics() },
                        modifier = Modifier.minTouchTarget()
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(if (reviewBadgeCount > 0) R.string.library_menu_review_count else R.string.library_menu_review, reviewBadgeCount)) },
                        onClick = { showMenu = false; onReviewMatches() },
                        modifier = Modifier.minTouchTarget()
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_menu_roots)) },
                        onClick = { showMenu = false; onManageRoots() },
                        modifier = Modifier.minTouchTarget()
                    )
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Box(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(AppSpacing.md))
                .background(if (gridCollapsed) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; viewModel.updateSearch(it) },
                modifier = Modifier.fillMaxWidth()
                    .semantics { this[SemanticsProperties.ContentDataType] = ContentDataType.None },
                singleLine = true,
                shape = RoundedCornerShape(AppSpacing.md),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                label = { Text(stringResource(R.string.library_search_label)) },
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = ""; viewModel.updateSearch("") }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_search_clear))
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(AppSpacing.md))
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (layout == LibraryLayout.GRID) 2 else 1),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            state = gridState,
            contentPadding = bottomContentPadding(),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val scroll1 = rememberScrollState()
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll1), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    LibrarySection.entries.forEach { section ->
                        FilterChip(selected = section == selectedSection, onClick = { selectedSection = section }, label = { Text(stringResource(section.labelRes), maxLines = 1) }, modifier = Modifier.minTouchTarget())
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.book_count, sectionBooks.size, sectionBooks.size), style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { layout = LibraryLayout.GRID }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.view_grid)) }
                        IconButton(onClick = { layout = LibraryLayout.LIST }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.List, contentDescription = stringResource(R.string.view_list)) }
                    }
                }
            }
            if (!searching) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            FilterChip(selected = uiState.query.status == LibraryStatusFilter.ALL, onClick = { status = LibraryStatusFilter.ALL; viewModel.updateStatus(LibraryStatusFilter.ALL) }, label = { Text(stringResource(R.string.filter_status_all)) }, modifier = Modifier.minTouchTarget())
                            FilterChip(selected = uiState.query.status == LibraryStatusFilter.IN_PROGRESS, onClick = { status = LibraryStatusFilter.IN_PROGRESS; viewModel.updateStatus(LibraryStatusFilter.IN_PROGRESS) }, label = { Text(stringResource(R.string.filter_status_in_progress)) }, modifier = Modifier.minTouchTarget())
                            FilterChip(selected = uiState.query.status == LibraryStatusFilter.FINISHED, onClick = { status = LibraryStatusFilter.FINISHED; viewModel.updateStatus(LibraryStatusFilter.FINISHED) }, label = { Text(stringResource(R.string.filter_status_finished)) }, modifier = Modifier.minTouchTarget())
                            FilterChip(selected = uiState.query.status == LibraryStatusFilter.NOT_STARTED, onClick = { status = LibraryStatusFilter.NOT_STARTED; viewModel.updateStatus(LibraryStatusFilter.NOT_STARTED) }, label = { Text(stringResource(R.string.filter_status_not_started)) }, modifier = Modifier.minTouchTarget())
                            FilterChip(selected = genre == "رواية", onClick = { genre = if (genre == "رواية") null else "رواية"; viewModel.updateGenre(genre) }, label = { Text(stringResource(R.string.filter_genre_novel)) }, modifier = Modifier.minTouchTarget())
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                            AssistChip(onClick = { viewModel.updateSort(LibrarySort.NAME) }, label = { Text(stringResource(R.string.sort_name)) }, modifier = Modifier.minTouchTarget())
                            AssistChip(onClick = { viewModel.updateSort(LibrarySort.ADDED_DATE) }, label = { Text(stringResource(R.string.sort_added)) }, modifier = Modifier.minTouchTarget())
                            AssistChip(onClick = { viewModel.updateSort(LibrarySort.LAST_PLAYED) }, label = { Text(stringResource(R.string.sort_last_played)) }, modifier = Modifier.minTouchTarget())
                            AssistChip(onClick = { viewModel.updateSort(LibrarySort.PROGRESS) }, label = { Text(stringResource(R.string.sort_progress)) }, modifier = Modifier.minTouchTarget())
                        }
                        Spacer(Modifier.height(AppSpacing.xs))
                    }
                }
            }
            if (!searching && uiState.seriesNames.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        FilterChip(selected = uiState.query.series == null, onClick = { viewModel.updateSeries(null) }, label = { Text(stringResource(R.string.filter_series_all)) }, modifier = Modifier.minTouchTarget())
                        uiState.seriesNames.forEach { name ->
                            FilterChip(selected = uiState.query.series == name, onClick = { viewModel.updateSeries(name) }, label = { Text(name, maxLines = 1) }, modifier = Modifier.minTouchTarget())
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.xxs))
                }
            }
            if (!searching && selectedSection == LibrarySection.COLLECTIONS && uiState.collections.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        uiState.collections.forEach { collection ->
                            val memberCount = uiState.collectionMembers[collection.name]?.size ?: 0
                            AssistChip(
                                onClick = { selectedCollection = collection.name },
                                label = { Text(stringResource(R.string.collection_chip_label, collection.name, memberCount), maxLines = 1) }
                            )
                        }
                        OutlinedButton(onClick = { showCollectionDialog = true }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.collection_new)) }
                    }
                    Spacer(Modifier.height(AppSpacing.xxs))
                }
            }
            if (sectionBooks.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        searching = searching,
                        emptyLibrary = !searching && selectedSection == LibrarySection.ALL_BOOKS && uiState.books.isEmpty(),
                        onClearSearch = { searchQuery = ""; viewModel.updateSearch("") },
                        onManageRoots = onManageRoots
                    )
                }
            } else {
                items(sectionBooks, key = { it.book.id }) { book ->
                    if (layout == LibraryLayout.GRID) {
                        BookGridCard(book, isFavorite = book.isFavorite, onBookSelected = { onBookSelected(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) })
                    } else {
                        BookListRow(book, isFavorite = book.isFavorite, onBookSelected = { onBookSelected(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) })
                    }
                }
            }
        }
    }
    if (showCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text(stringResource(R.string.collection_create_title)) },
            text = {
                OutlinedTextField(
                    value = newCollectionName,
                    onValueChange = { newCollectionName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.collection_name_label)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCollectionName.isNotBlank()) viewModel.createCollection(newCollectionName)
                    newCollectionName = ""
                    showCollectionDialog = false
                }) { Text(stringResource(R.string.collection_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showCollectionDialog = false }) {
                    Text(stringResource(R.string.collection_cancel))
                }
            }
        )
    }
}

@Composable
private fun EmptyState(searching: Boolean, emptyLibrary: Boolean, onClearSearch: () -> Unit, onManageRoots: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (searching) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.no_results_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.no_results_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onClearSearch, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.clear_search)) }
        } else if (emptyLibrary) {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.library_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onManageRoots, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.manage_roots)) }
        } else {
            Icon(Icons.Outlined.Collections, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.section_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.section_empty_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BookGridCard(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = onBookSelected).padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        AtherCoverBlock(
            title = book.book.title,
            coverColor = Color(book.coverColor.toInt()),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
            showMissingBadge = book.hasMissingFile,
            missingFileDescription = stringResource(R.string.missing_file)
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                book.book.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                Icon(
                    if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            book.authorName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}

@Composable
private fun BookListRow(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = onBookSelected).padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AtherCoverBlock(
            title = book.book.title,
            coverColor = Color(book.coverColor.toInt()),
            modifier = Modifier.width(64.dp).aspectRatio(0.72f),
            showMissingBadge = book.hasMissingFile,
            missingFileDescription = stringResource(R.string.missing_file)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(book.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(stringResource(R.string.progress_percent, (book.progressFraction * 100).toInt()), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(
                if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}