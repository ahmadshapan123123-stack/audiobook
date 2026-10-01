package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID
import androidx.compose.ui.graphics.Color

/**
 * REDESIGN — تفاصيل السلسلة: بطل مضغوط + إجراء رئيسي + صفوف مرقّمة
 * (شارة/عنوان/مدة/تقدّم/شيفرون) + ⋮ للثانوي. إعادة الترتيب تبقى حوار
 * الأسهم الحالي (يعمل) بدل مقابض السحب.
 */
@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onAuthorSelected: (UUID) -> Unit,
    onPlayEdition: (UUID) -> Unit = {},
    onBookOptions: (UUID) -> Unit = {},
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showEntityEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }
    var showReorder by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<EntityBookRow?>(null) }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

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
                .fillMaxWidth()
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
            com.example.audiobook.presentation.theme.CosmicScreenHeader(
                title = stringResource(R.string.series_details_title),
                collapsed = collapsed,
                onBack = onBack,
                backAsTextButton = true,
                modifier = Modifier.weight(1f)
            )
            EntityOverflowMenuButton(
                actions = listOf(
                    EntityMenuAction(R.string.library_folder_edit) { showEntityEdit = true },
                    EntityMenuAction(R.string.series_menu_merge) { showMergeDialog = true },
                    EntityMenuAction(R.string.series_add_book) { showAddBook = true },
                    EntityMenuAction(R.string.series_menu_reorder) { showReorder = true },
                    EntityMenuAction(R.string.series_menu_delete, danger = true) { showDeleteDialog = true }
                )
            )
        }

        val series = state.series
        if (series == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            val primaryEdition = state.continueRow?.editionId ?: state.firstEditionId
            EntityHeroSection(
                avatarTitle = series.name,
                avatarColor = Color(state.coverColor.toInt()),
                name = series.name,
                // FIX C3-display: عرض صورة السلسلة المختارة — null = الحرف كما كان.
                imagePath = series.imagePath,
                stats = listOf(
                    pluralStringResource(R.plurals.book_count, state.books.size, state.books.size),
                    stringResource(R.string.entity_series_finished_count, state.finishedCount)
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
                    initialName = series.name,
                    initialDescription = series.description.orEmpty(),
                    initialImagePath = series.imagePath,
                    entityId = series.id,
                    onDismiss = { showEntityEdit = false },
                    onSave = { name, description, imagePath ->
                        viewModel.saveSeries(name, description, imagePath)
                        showEntityEdit = false
                    }
                )
            }

            if (showAddBook) {
                PickBookDialog(
                    candidates = state.candidateBooks,
                    titleRes = R.string.series_add_book_title,
                    emptyMessage = stringResource(R.string.series_add_book_none),
                    onSelect = { bookId ->
                        showAddBook = false
                        viewModel.addBookToSeries(bookId)
                    },
                    onDismiss = { showAddBook = false }
                )
            }

            if (showReorder) {
                ReorderBooksDialog(
                    order = state.books,
                    onDone = { orderedIds ->
                        showReorder = false
                        viewModel.applyBookOrder(orderedIds)
                    },
                    onDismiss = { showReorder = false }
                )
            }

            removeTarget?.let { target ->
                ConfirmDeleteDialog(
                    title = stringResource(R.string.remove_from_series_title),
                    message = stringResource(R.string.remove_from_series_confirm, target.title),
                    confirmText = stringResource(R.string.series_remove_book_cd),
                    onConfirm = {
                        removeTarget = null
                        viewModel.removeBookFromSeries(target.bookId)
                    },
                    onDismiss = { removeTarget = null }
                )
            }

            if (showDeleteDialog) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.confirm_delete_title),
                    message = stringResource(R.string.confirm_delete_series, series.name, state.books.size),
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteSeries()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }

            if (showMergeDialog) {
                var searchText by remember { mutableStateOf("") }
                val filtered = state.allSeries.filter {
                    it.name.contains(searchText, ignoreCase = true)
                }
                AlertDialog(
                    onDismissRequest = { showMergeDialog = false },
                    title = { Text(stringResource(R.string.confirm_merge_title)) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = searchText,
                                onValueChange = { searchText = it },
                                singleLine = true,
                                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(AppSpacing.sm))
                            filtered.forEach { target ->
                                Text(
                                    target.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minTouchTarget()
                                        .clickable {
                                            showMergeDialog = false
                                            viewModel.mergeSeries(target.id)
                                        }
                                        .padding(AppSpacing.sm)
                                )
                            }
                            if (filtered.isEmpty()) {
                                Text(
                                    stringResource(R.string.no_results_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showMergeDialog = false }) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
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

            EntitySectionTitle(stringResource(R.string.entity_series_books_header))
            if (state.books.isEmpty()) {
                Text(stringResource(R.string.entity_no_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showAddBook = true }, modifier = Modifier.minTouchTarget()) {
                    Text(stringResource(R.string.series_add_book))
                }
            } else {
                state.books.forEachIndexed { index, row ->
                    SeriesBookRow(
                        number = row.orderInSeries ?: (index + 1),
                        row = row,
                        durationMs = state.durationsMs[row.bookId] ?: 0L,
                        authorName = state.authorName,
                        onAuthorClick = state.authorId?.let { { onAuthorSelected(it) } },
                        onClick = { onBookSelected(row.bookId) },
                        onBookOptions = { onBookOptions(row.bookId) },
                        onRemove = { removeTarget = row }
                    )
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

/**
 * صف كتاب سلسلة مرقّم: شارة الترتيب + غلاف + عنوان/مؤلف + مدة +
 * تقدّم + شيفرون + إزالة. الترتيب من orderInSeries لا الفهرس.
 */
@Composable
private fun SeriesBookRow(
    number: Int,
    row: EntityBookRow,
    durationMs: Long,
    authorName: String,
    onAuthorClick: (() -> Unit)?,
    onClick: () -> Unit,
    onBookOptions: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
            AtherCoverBlock(
                title = row.title,
                coverColor = Color(row.coverColor.toInt()),
                imagePath = row.coverImagePath,
                modifier = Modifier.size(48.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    if (authorName.isNotBlank()) {
                        val authorMod = if (onAuthorClick != null) {
                            Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.xs))
                                .clickable(onClick = onAuthorClick)
                        } else Modifier
                        Text(
                            authorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (onAuthorClick != null) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = authorMod
                        )
                    }
                    if (durationMs > 0L) {
                        Text(
                            formatShort(durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Remove,
                    contentDescription = stringResource(R.string.series_remove_book_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
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

private fun formatShort(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}س ${minutes}د" else "${minutes}د"
}
