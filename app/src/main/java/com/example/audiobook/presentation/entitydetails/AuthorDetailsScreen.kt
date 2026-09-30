package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.ConfirmMergeDialog
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * REDESIGN — تفاصيل المؤلف: بطل مضغوط + إجراء رئيسي + أقسام تدريجية.
 * HERO (أفاتار/إحصاءات/تشغيل) → أكمل الاستماع → السلاسل (شبكة) →
 * المستقلة (شبكة) → كل الكتب (قابل للطي) → ⋮ للثانوي والمدمّر.
 */
@Composable
fun AuthorDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onSeriesSelected: (UUID) -> Unit,
    onPlayEdition: (UUID) -> Unit = {},
    onBookOptions: (UUID) -> Unit = {},
    viewModel: AuthorDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEntityEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }
    var showAddSeries by remember { mutableStateOf(false) }
    var moveTarget by remember { mutableStateOf<EntityBookRow?>(null) }
    var seriesExpanded by remember { mutableStateOf(false) }
    var standaloneExpanded by remember { mutableStateOf(false) }
    var allBooksExpanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)
    val scope = rememberCoroutineScope()

    EntityUndoEffect(
        message = messages,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undo,
        onTimedOut = onBack,
        onConsumed = viewModel::consumeMessage
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
        Spacer(Modifier.height(AppSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CosmicScreenHeader(
                title = stringResource(R.string.author_details_title),
                collapsed = collapsed,
                onBack = onBack,
                backAsTextButton = true,
                modifier = Modifier.weight(1f)
            )
            EntityOverflowMenuButton(
                actions = listOf(
                    EntityMenuAction(R.string.library_folder_edit) { showEntityEdit = true },
                    EntityMenuAction(R.string.author_menu_merge) { showMergeDialog = true },
                    EntityMenuAction(R.string.series_add_book) { showAddBook = true },
                    EntityMenuAction(R.string.author_add_series) { showAddSeries = true },
                    EntityMenuAction(R.string.author_menu_delete, danger = true) { showDeleteDialog = true }
                )
            )
        }

        val author = state.author
        if (author == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            val primaryEdition = state.continueRow?.editionId ?: state.firstEditionId
            EntityHeroSection(
                avatarTitle = author.name,
                avatarColor = Color(state.coverColor.toInt()),
                name = author.name,
                // FIX C3-display: عرض صورة المؤلف المختارة — null = الحرف كما كان.
                imagePath = author.imagePath,
                stats = listOf(
                    pluralStringResource(R.plurals.book_count, state.totalBooks, state.totalBooks),
                    pluralStringResource(R.plurals.series_count, state.seriesCards.size, state.seriesCards.size),
                    stringResource(R.string.entity_in_progress_count, state.inProgressCount)
                ),
                primaryLabel = when {
                    state.continueRow != null -> stringResource(R.string.home_continue_play)
                    primaryEdition != null -> stringResource(R.string.bd_play_default)
                    else -> null
                },
                onPrimary = primaryEdition?.let { { onPlayEdition(it) } }
            )

            if (showEntityEdit) {
                EntityEditDialog(
                    initialName = author.name,
                    initialDescription = author.description.orEmpty(),
                    initialImagePath = author.imagePath,
                    entityId = author.id,
                    onDismiss = { showEntityEdit = false },
                    onSave = { name, description, imagePath ->
                        viewModel.saveAuthor(name, description, imagePath)
                        showEntityEdit = false
                    }
                )
            }

            if (showAddSeries) {
                val seriesCreatedMsg = stringResource(R.string.series_created_done)
                InputDialog(
                    title = stringResource(R.string.author_add_series),
                    label = stringResource(R.string.author_add_series_label),
                    onConfirm = { name ->
                        showAddSeries = false
                        viewModel.createSeries(name)
                        scope.launch { snackbarHostState.showSnackbar(seriesCreatedMsg) }
                    },
                    onDismiss = { showAddSeries = false }
                )
            }

            if (showAddBook) {
                PickBookDialog(
                    candidates = state.candidateBooks,
                    titleRes = R.string.author_add_book_title,
                    emptyMessage = stringResource(R.string.author_add_book_none),
                    onSelect = { bookId ->
                        showAddBook = false
                        viewModel.addBookToAuthor(bookId)
                    },
                    onDismiss = { showAddBook = false }
                )
            }

            moveTarget?.let { target ->
                MoveToAuthorDialog(
                    authors = state.allAuthors,
                    title = stringResource(R.string.move_to_author_pick),
                    onSelect = { targetId ->
                        val bookId = target.bookId
                        moveTarget = null
                        viewModel.moveBookToOtherAuthor(bookId, targetId)
                    },
                    onDismiss = { moveTarget = null }
                )
            }

            if (showDeleteDialog) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.confirm_delete_title),
                    message = stringResource(R.string.confirm_delete_author, author.name, state.totalBooks),
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteAuthor()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }

            if (showMergeDialog) {
                ConfirmMergeDialog(
                    title = stringResource(R.string.confirm_merge_title),
                    message = stringResource(R.string.author_merge_select),
                    onConfirm = { showMergeDialog = false },
                    onDismiss = { showMergeDialog = false }
                )
                MoveToAuthorDialog(
                    authors = state.allAuthors,
                    title = stringResource(R.string.author_merge_select),
                    onSelect = { targetId ->
                        showMergeDialog = false
                        viewModel.mergeAuthors(targetId)
                    },
                    onDismiss = { showMergeDialog = false }
                )
            }

            state.continueRow?.let { row ->
                EntitySectionTitle(stringResource(R.string.continue_label))
                ContinueListeningCard(
                    title = row.title,
                    coverColor = Color(row.coverColor.toInt()),
                    progressFraction = row.progressFraction,
                    onContinue = { row.editionId?.let(onPlayEdition) },
                    onOpenBook = { onBookSelected(row.bookId) }
                )
            }

            if (state.seriesCards.isNotEmpty()) {
                EntitySectionTitle(stringResource(R.string.entity_author_series_header))
                val visible = if (seriesExpanded) state.seriesCards else state.seriesCards.take(4)
                visible.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        pair.forEach { card ->
                            SeriesGridCell(
                                name = card.seriesName,
                                countText = pluralStringResource(R.plurals.book_count, card.bookCount, card.bookCount),
                                coverColor = Color(state.coverColor.toInt()),
                                onClick = { onSeriesSelected(card.seriesId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (state.seriesCards.size > 4) {
                    TextButton(onClick = { seriesExpanded = !seriesExpanded }, modifier = Modifier.minTouchTarget()) {
                        Text(stringResource(R.string.home_view_all))
                    }
                }
            }

            val standaloneBooks = state.groups.filter { it.seriesId == null }.flatMap { it.books }
            if (standaloneBooks.isNotEmpty()) {
                EntitySectionTitle(stringResource(R.string.entity_standalone_header))
                val visible = if (standaloneExpanded) standaloneBooks else standaloneBooks.take(6)
                visible.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        pair.forEach { row ->
                            BookGridCell(
                                row = row,
                                onClick = { onBookSelected(row.bookId) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                if (standaloneBooks.size > 6) {
                    TextButton(onClick = { standaloneExpanded = !standaloneExpanded }, modifier = Modifier.minTouchTarget()) {
                        Text(stringResource(R.string.home_view_all))
                    }
                }
            }

            val allRows = state.groups.flatMap { it.books }
            if (allRows.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minTouchTarget()
                        .clickable { allBooksExpanded = !allBooksExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EntitySectionTitle(stringResource(R.string.entity_author_books_header))
                }
                if (allBooksExpanded) {
                    allRows.forEach { row ->
                        EntityBookRowItem(
                            row = row,
                            onClick = { onBookSelected(row.bookId) },
                            onBookOptions = { onBookOptions(row.bookId) },
                            trailing = {
                                IconButton(
                                    onClick = { moveTarget = row },
                                    modifier = Modifier.minTouchTarget()
                                ) {
                                    Icon(
                                        Icons.Outlined.DriveFileMove,
                                        contentDescription = stringResource(R.string.move_book_title),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (state.seriesCards.isEmpty() && standaloneBooks.isEmpty()) {
                Text(
                    stringResource(R.string.entity_no_books),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = { showAddBook = true }, modifier = Modifier.minTouchTarget()) {
                    Text(stringResource(R.string.series_add_book))
                }
            }
        }
        Spacer(Modifier.height(bottomContentInset()))
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomContentInset() + AppSpacing.md)
                .padding(horizontal = AppSpacing.md)
        )
    }
}

/** خلية شبكة لسلسلة: غلاف + اسم + عدّ. */
@Composable
private fun SeriesGridCell(
    name: String,
    countText: String,
    coverColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AtherCoverBlock(
            title = name,
            coverColor = coverColor,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** خلية شبكة لكتاب مستقل: غلاف + عنوان + تقدّم. */
@Composable
private fun BookGridCell(
    row: EntityBookRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AtherCoverBlock(
            title = row.title,
            coverColor = Color(row.coverColor.toInt()),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            row.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (row.hasProgress) {
            LinearProgressIndicator(
                progress = { row.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun MoveToAuthorDialog(
    authors: List<com.example.audiobook.data.room.entity.AuthorEntity>,
    title: String,
    onSelect: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filtered = authors.filter {
        it.name.contains(searchText, ignoreCase = true)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppSpacing.sm))
                filtered.forEach { author ->
                    Text(
                        author.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minTouchTarget()
                            .clickable { onSelect(author.id) }
                            .padding(AppSpacing.sm)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
