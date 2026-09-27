package com.example.audiobook.presentation.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.example.audiobook.BuildConfig
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.ScanPhase
import com.example.audiobook.domain.usecases.ScanProgress
import com.example.audiobook.presentation.common.BookManagerViewModel
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.MoveBookDialog
import com.example.audiobook.presentation.common.MoveBookTab
import com.example.audiobook.presentation.common.cleanDisplayTitle
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.bottomContentPadding
import com.example.audiobook.presentation.theme.minTouchTarget
import java.util.UUID
import kotlinx.coroutines.flow.StateFlow

internal enum class LibrarySection(val labelRes: Int) {
    ALL_BOOKS(R.string.section_all),
    CURRENTLY_LISTENING(R.string.section_current),
    FINISHED(R.string.section_finished),
    FAVORITES(R.string.section_favorites),
    COLLECTIONS(R.string.section_collections),
    RECENTLY_ADDED(R.string.section_recent)
}

private enum class LibraryLayout { GRID, LIST }

/** عمليّة جماعية قيد التأكيد في مكتبة وضع التحديد المتعدد. */
private sealed interface BulkOp {
    data class Delete(val count: Int) : BulkOp
    data class MoveAuthor(val authorId: UUID, val count: Int, val name: String) : BulkOp
    data class MoveSeries(val seriesId: UUID?, val count: Int, val name: String) : BulkOp
    data class AddCollection(val collectionId: UUID, val count: Int, val name: String) : BulkOp
    data class Favorite(val count: Int) : BulkOp
}

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
    onOpenAuthors: () -> Unit = {},
    onOpenSeries: () -> Unit = {},
    onBookOptions: (UUID) -> Unit = {},
    bookManager: BookManagerViewModel,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val initial = runCatching { LibrarySection.valueOf(initialSection) }.getOrDefault(LibrarySection.ALL_BOOKS)
    var selectedSection by remember(initial) { mutableStateOf(initial) }
    var layout by remember { mutableStateOf(LibraryLayout.GRID) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilterSheet by remember { mutableStateOf(false) }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    val activeCollection = selectedCollection ?: uiState.collections.firstOrNull()?.name ?: ""
    var showCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    // الرأس يُنهار فور بدء التمرير (أي إزاحة رأسية) ويعود للتوسّع عند العودة للأعلى.
    val gridCollapsed = gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0

    // ── التحديد المتعدد ──
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    var movePickerTab by remember { mutableStateOf<MoveBookTab?>(null) }
    var pendingBulkOp by remember { mutableStateOf<BulkOp?>(null) }

    val exitSelection: () -> Unit = {
        selectionMode = false
        selectedIds = emptySet()
    }
    val onCardClicked: (UUID) -> Unit = { id ->
        if (selectionMode) {
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        } else {
            onBookSelected(id)
        }
    }
    val onCardLongPress: (UUID) -> Unit = { id ->
        if (selectionMode) {
            selectedIds = selectedIds + id
        } else {
            onBookOptions(id)
        }
    }

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
        LibrarySection.RECENTLY_ADDED -> uiState.books.sortedByDescending { it.addedOrder }.take(LibraryViewModel.RECENTLY_ADDED_LIMIT)
    }
    val searching = searchQuery.isNotBlank()
    // الفلاتر النشطة = أبعاد الاستعلام غير الافتراضية + القسم غير «كل الكتب».
    val activeFilterCount = uiState.query.activeFilterCount + if (selectedSection != LibrarySection.ALL_BOOKS) 1 else 0

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg)) {
        // ── الرأس المثبّت: عند التمرير ينكمش إلى شريط زجاجي رفيع (المكتبة + بحث + قائمة)،
        //    وعند العودة للأعلى يعيد التوسّع ليُظهر العنوان مع حقل البحث في سطر واحد.
        val hasGlassBar = gridCollapsed && !selectionMode && !searchOpen
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(if (hasGlassBar) RoundedCornerShape(bottomStart = AppSpacing.md, bottomEnd = AppSpacing.md) else RoundedCornerShape(0.dp))
                .background(if (hasGlassBar) MaterialTheme.colorScheme.surface.copy(alpha = 0.7f) else Color.Transparent)
                .padding(top = AppSpacing.xs)
                .animateContentSize()
        ) {
            if (searchOpen) {
                // وضع البحث المنبثق: سطر واحد فيه حقل بحث مركّز + زر إغلاق.
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { searchOpen = false }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_close_search))
                    }
                    LibrarySearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; viewModel.updateSearch(it) },
                        onClear = { searchQuery = ""; viewModel.updateSearch("") },
                        autoFocus = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else if (selectionMode) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.bulk_selection_count, selectedIds.size),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == sectionBooks.size) {
                                emptySet()
                            } else {
                                sectionBooks.map { it.book.id }.toSet()
                            }
                        },
                        modifier = Modifier.minTouchTarget()
                    ) {
                        Text(stringResource(R.string.bulk_select_toggle))
                    }
                    IconButton(onClick = exitSelection, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.bulk_close_selection))
                    }
                }
            } else {
                val bookCountText = pluralStringResource(R.plurals.book_count, uiState.books.size, uiState.books.size)
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (gridCollapsed) {
                            Text(
                                stringResource(R.string.library_topbar_title),
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Text(
                                stringResource(R.string.library_title),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                bookCountText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { searchOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.library_search_action))
                    }
                    // ── مسح سريع: يظهر فقط عند وجود فلاتر نشطة، بجوار زر التصفية ──
                    if (activeFilterCount > 0) {
                        IconButton(
                            onClick = {
                                selectedSection = LibrarySection.ALL_BOOKS
                                viewModel.resetFilters()
                            },
                            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.filter_action_clear),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    // ── زر التصفية + شارة عدد الفلاتر النشطة ──
                    Box {
                        IconButton(onClick = { showFilterSheet = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                            Icon(Icons.Outlined.Tune, contentDescription = stringResource(R.string.filter_action_open))
                        }
                        if (activeFilterCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end = 5.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeFilterCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1
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
                                text = { Text(stringResource(R.string.bulk_selection_title)) },
                                onClick = { showMenu = false; selectionMode = true; selectedIds = emptySet() },
                                modifier = Modifier.minTouchTarget()
                            )
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
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_menu_authors)) },
                                onClick = { showMenu = false; onOpenAuthors() },
                                modifier = Modifier.minTouchTarget()
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.library_menu_series)) },
                                onClick = { showMenu = false; onOpenSeries() },
                                modifier = Modifier.minTouchTarget()
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !gridCollapsed,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(Modifier.height(AppSpacing.xs))
                        LibrarySearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it; viewModel.updateSearch(it) },
                            onClear = { searchQuery = ""; viewModel.updateSearch("") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(AppSpacing.sm))
                    }
                }
            }
        }
        ScanProgressBanner(
            progress = viewModel.scanProgress,
            active = viewModel.scanActive,
            onCancel = viewModel::cancelScan
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (layout == LibraryLayout.GRID) 2 else 1),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            state = gridState,
            contentPadding = bottomContentPadding(),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(pluralStringResource(R.plurals.book_count, sectionBooks.size, sectionBooks.size), style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { layout = LibraryLayout.GRID }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.GridView, contentDescription = stringResource(R.string.view_grid)) }
                        IconButton(onClick = { layout = LibraryLayout.LIST }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.List, contentDescription = stringResource(R.string.view_list)) }
                    }
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
                    val isSelected = book.book.id in selectedIds
                    if (layout == LibraryLayout.GRID) {
                        BookGridCard(book, isFavorite = book.isFavorite, onBookSelected = { onCardClicked(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) }, onBookOptions = { onCardLongPress(book.book.id) }, selectionMode = selectionMode, selected = isSelected)
                    } else {
                        BookListRow(book, isFavorite = book.isFavorite, onBookSelected = { onCardClicked(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) }, onBookOptions = { onCardLongPress(book.book.id) }, selectionMode = selectionMode, selected = isSelected)
                    }
                }
            }
        }
        if (selectionMode) {
        AnimatedVisibility(visible = selectedIds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(AppSpacing.md))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f))
                    .padding(vertical = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { pendingBulkOp = BulkOp.Delete(selectedIds.size) }, modifier = Modifier.minTouchTarget()) {
                    Text(stringResource(R.string.bulk_action_delete), color = MaterialTheme.colorScheme.error)
                }
                AssistChip(onClick = { movePickerTab = MoveBookTab.AUTHOR }, label = { Text(stringResource(R.string.bulk_action_move_author)) }, modifier = Modifier.minTouchTarget())
                AssistChip(onClick = { movePickerTab = MoveBookTab.SERIES }, label = { Text(stringResource(R.string.bulk_action_move_series)) }, modifier = Modifier.minTouchTarget())
                AssistChip(onClick = { movePickerTab = MoveBookTab.COLLECTION }, label = { Text(stringResource(R.string.bulk_action_add_collection)) }, modifier = Modifier.minTouchTarget())
                AssistChip(onClick = { pendingBulkOp = BulkOp.Favorite(selectedIds.size) }, label = { Text(stringResource(R.string.bulk_action_favorite)) }, modifier = Modifier.minTouchTarget())
            }
        }
        }
    }
    if (selectionMode) {
        val manager = bookManager
        val catalog by manager.catalog.collectAsStateWithLifecycle()
        val noSeriesLabel = stringResource(R.string.move_book_no_series)
        movePickerTab?.let { tab ->
        MoveBookDialog(
            catalog = catalog,
            initialTab = tab,
            onSelectAuthor = { id ->
                val name = catalog.authors.firstOrNull { it.id == id }?.name.orEmpty()
                movePickerTab = null
                pendingBulkOp = BulkOp.MoveAuthor(id, selectedIds.size, name)
            },
            onCreateAuthor = { name ->
                movePickerTab = null
                manager.bulkCreateAuthorAndMove(selectedIds.toList(), name)
                exitSelection()
            },
            onSelectSeries = { id ->
                val name = id?.let { sid -> catalog.series.firstOrNull { it.id == sid }?.name }.orEmpty()
                movePickerTab = null
                pendingBulkOp = BulkOp.MoveSeries(id, selectedIds.size, name.ifBlank { noSeriesLabel })
            },
            onCreateSeries = { name ->
                movePickerTab = null
                manager.bulkCreateSeriesAndMove(selectedIds.toList(), name)
                exitSelection()
            },
            onSelectCollection = { id ->
                val name = catalog.collections.firstOrNull { it.id == id }?.name.orEmpty()
                movePickerTab = null
                pendingBulkOp = BulkOp.AddCollection(id, selectedIds.size, name)
            },
            onCreateCollection = { name ->
                movePickerTab = null
                manager.bulkCreateCollectionAndAdd(selectedIds.toList(), name)
                exitSelection()
            },
            onDismiss = { movePickerTab = null }
        )
    }
    val confirmMessage = pendingBulkOp?.let { op ->
        when (op) {
            is BulkOp.Delete -> stringResource(R.string.bulk_action_confirm_delete, op.count)
            is BulkOp.MoveAuthor -> stringResource(R.string.bulk_action_confirm_move_author, op.count, op.name)
            is BulkOp.MoveSeries -> stringResource(R.string.bulk_action_confirm_move_series, op.count, op.name)
            is BulkOp.AddCollection -> stringResource(R.string.bulk_action_confirm_add_collection, op.count, op.name)
            is BulkOp.Favorite -> stringResource(R.string.bulk_action_confirm_favorite, op.count)
        }
    }
    if (confirmMessage != null) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.bulk_selection_title),
            message = confirmMessage,
            confirmText = stringResource(R.string.btn_confirm),
            onConfirm = {
                val op = pendingBulkOp
                val ids = selectedIds.toList()
                when (op) {
                    is BulkOp.Delete -> manager.bulkDelete(ids)
                    is BulkOp.MoveAuthor -> manager.bulkMoveToAuthor(ids, op.authorId)
                    is BulkOp.MoveSeries -> manager.bulkMoveToSeries(ids, op.seriesId)
                    is BulkOp.AddCollection -> manager.bulkAddToCollection(op.collectionId, ids)
                    is BulkOp.Favorite -> manager.bulkSetFavorite(ids, true)
                    null -> {}
                }
                pendingBulkOp = null
                exitSelection()
            },
            onDismiss = { pendingBulkOp = null }
        )
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
    // ── ورقة التصفية والترتيب (تستبدل صفوف الرقائق القديمة) ──
    if (showFilterSheet) {
        LibraryFilterSheet(
            section = selectedSection,
            query = uiState.query,
            seriesNames = uiState.seriesNames,
            genreNames = uiState.genreNames,
            activeCount = activeFilterCount,
            onSectionChange = { selectedSection = it },
            onStatusChange = viewModel::updateStatus,
            onSortChange = viewModel::updateSort,
            onSeriesChange = viewModel::updateSeries,
            onGenreChange = viewModel::updateGenre,
            onReset = {
                selectedSection = LibrarySection.ALL_BOOKS
                viewModel.resetFilters()
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    autoFocus: Boolean = false
) {
    val focusRequester = remember { FocusRequester() }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppSpacing.md))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { this[SemanticsProperties.ContentDataType] = ContentDataType.None },
            singleLine = true,
            shape = RoundedCornerShape(AppSpacing.md),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            label = { Text(stringResource(R.string.library_search_label)) },
            placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.library_search_clear))
                    }
                }
            }
        )
    }
    if (autoFocus) {
        LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
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
private fun BookGridCard(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit, onBookOptions: () -> Unit = {}, selectionMode: Boolean = false, selected: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxWidth().minTouchTarget().combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onBookSelected,
            onLongClick = onBookOptions
        ).padding(AppSpacing.xxs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Box {
            AtherCoverBlock(
                title = cleanDisplayTitle(book.book.title),
                coverColor = Color(book.coverColor.toInt()),
                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
                showMissingBadge = book.hasMissingFile,
                missingFileDescription = stringResource(R.string.missing_file)
            )
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                cleanDisplayTitle(book.book.title),
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
        if (book.book.isDemo) DemoBadge()
        LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth().height(6.dp))
    }
}

@Composable
private fun BookListRow(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit, onBookOptions: () -> Unit = {}, selectionMode: Boolean = false, selected: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onBookSelected,
            onLongClick = onBookOptions
        ).padding(vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = null)
        }
        AtherCoverBlock(
            title = cleanDisplayTitle(book.book.title),
            coverColor = Color(book.coverColor.toInt()),
            modifier = Modifier.width(64.dp).aspectRatio(0.72f),
            showMissingBadge = book.hasMissingFile,
            missingFileDescription = stringResource(R.string.missing_file)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(cleanDisplayTitle(book.book.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (book.book.isDemo) DemoBadge()
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

/** شارة "بيانات تجريبية": تظهر فقط في إصدارات التصحيح وعلى الكتب الموسومة هويًا isDemo. */
@Composable
private fun DemoBadge() {
    if (!BuildConfig.DEBUG) return
    Surface(
        shape = RoundedCornerShape(AppSpacing.xs),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            stringResource(R.string.library_demo_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = AppSpacing.xs, vertical = AppSpacing.xxs)
        )
    }
}

/** نص وصف طور الفحص الحالي وفق المحتوى المرسل في نشرة التقدّم. */
@Composable
private fun phaseLabel(progress: ScanProgress?): String = when (progress?.phase) {
    ScanPhase.DISCOVERING -> stringResource(R.string.scan_phase_discovering)
    ScanPhase.PARSING -> stringResource(R.string.scan_phase_parsing, progress.processed, progress.total)
    ScanPhase.CLASSIFYING -> stringResource(R.string.scan_phase_classifying)
    ScanPhase.CREATING -> if (progress.currentFolder.isBlank()) {
        stringResource(R.string.scan_phase_creating, progress.processed, progress.total)
    } else {
        stringResource(R.string.scan_phase_creating, progress.processed, progress.total) + " · " + progress.currentFolder
    }
    ScanPhase.DONE -> stringResource(R.string.library_scan_progress)
    null -> stringResource(R.string.library_scan_progress)
}

/**
 * شريط تقدّم الفحص (المرحلة 4): يظهر أثناء فحص نشط فعليًا فوق الشبكة. يعرض
 * الطور بالعربية مع النسبة، وزر إلغاء يرسل إشارة توقف تعاوني (يُكمل الفحصَ
 * تحرّره ويتوقف عند بداية الدفعة التالية ويترك checkpoint للاستئناف).
 */
@Composable
private fun ScanProgressBanner(
    progress: StateFlow<ScanProgress?>,
    active: StateFlow<Boolean>,
    onCancel: () -> Unit
) {
    val current by progress.collectAsStateWithLifecycle()
    val isActive by active.collectAsStateWithLifecycle()
    if (!isActive) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = phaseLabel(current),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
        LinearProgressIndicator(
            progress = {
                val total = current?.total ?: 1
                val fraction = if (total > 0) (current?.processed ?: 0).toFloat() / total else 0f
                fraction.coerceIn(0f, 1f)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}