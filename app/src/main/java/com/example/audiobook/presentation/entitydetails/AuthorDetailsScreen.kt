package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.ConfirmMergeDialog
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun AuthorDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onSeriesSelected: (UUID) -> Unit,
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
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)
    val scope = rememberCoroutineScope()

    // عملية مدمرة → سناكبار "تراجع" لنافذة 5 ثوانٍ؛ إن انتهت دون نقرة نعود للقائمة.
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
        CosmicScreenHeader(
            title = stringResource(R.string.author_details_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )

        val author = state.author
        if (author == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            val firstBookTitle = state.groups.firstOrNull()?.books?.firstOrNull()?.title ?: author.name
            EntityHeaderBlock(
                name = author.name,
                subtitle = "",
                count = state.totalBooks,
                coverTitle = firstBookTitle,
                coverColor = Color(state.coverColor.toInt())
            )

            EntityEditButton(onClick = { showEntityEdit = true })
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

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { showMergeDialog = true }) {
                    Text(stringResource(R.string.author_menu_merge))
                }
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.author_menu_delete), color = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedButton(onClick = { showAddBook = true }) {
                Text(stringResource(R.string.series_add_book))
            }

            OutlinedButton(onClick = { showAddSeries = true }) {
                Text(stringResource(R.string.author_add_series))
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

            EntitySectionTitle(stringResource(R.string.entity_author_books_header))
            if (state.groups.isEmpty()) {
                Text(stringResource(R.string.entity_no_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.groups.forEach { group ->
                    if (group.seriesId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minTouchTarget()
                                .clickable { onSeriesSelected(group.seriesId) }
                                .padding(vertical = AppSpacing.xxs)
                        ) {
                            Text(
                                stringResource(R.string.entity_series_group_header, group.seriesName ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.entity_standalone_header),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = AppSpacing.xxs)
                        )
                    }
                    group.books.forEach { row ->
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