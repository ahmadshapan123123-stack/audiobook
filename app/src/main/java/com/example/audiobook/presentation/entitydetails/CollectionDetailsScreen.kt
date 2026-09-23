package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Remove
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
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

@Composable
fun CollectionDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onBookOptions: (UUID) -> Unit = {},
    viewModel: CollectionDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddBook by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<EntityBookRow?>(null) }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    // عملية مدمرة → سناكبار "تراجع" لنافذة 5 ثوانٍ؛ إن انتهت دون نقرة نعود للقائمة
    // (إلا عند إزالة كتاب فقط — نبقى في الصفحة).
    EntityUndoEffect(
        message = messages,
        snackbarHostState = snackbarHostState,
        onUndo = viewModel::undo,
        onTimedOut = { if (viewModel.shouldPopOnTimeout) onBack() },
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
            title = stringResource(R.string.collection_details_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )

        val collection = state.collection
        if (collection == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            EntityHeaderBlock(
                name = collection.name,
                subtitle = "",
                count = state.books.size,
                coverTitle = state.books.firstOrNull()?.title ?: collection.name,
                coverColor = Color(state.coverColor.toInt())
            )

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { showEditDialog = true }) {
                    Text(stringResource(R.string.collection_menu_edit))
                }
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.collection_menu_delete), color = MaterialTheme.colorScheme.error)
                }
            }

            OutlinedButton(onClick = { showAddBook = true }) {
                Text(stringResource(R.string.series_add_book))
            }

            if (showEditDialog) {
                InputDialog(
                    title = stringResource(R.string.collection_menu_edit),
                    label = stringResource(R.string.collection_edit_name_label),
                    initialValue = collection.name,
                    onConfirm = { newName ->
                        showEditDialog = false
                        viewModel.updateCollectionName(newName)
                    },
                    onDismiss = { showEditDialog = false }
                )
            }

            if (showDeleteDialog) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.confirm_delete_title),
                    message = stringResource(R.string.confirm_delete_collection, collection.name, state.books.size),
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteCollection()
                    },
                    onDismiss = { showDeleteDialog = false }
                )
            }

            EntitySectionTitle(stringResource(R.string.entity_books_header))
            if (state.books.isEmpty()) {
                Text(stringResource(R.string.entity_no_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.books.forEach { row ->
                    EntityBookRowItem(
                        row = row,
                        onClick = { onBookSelected(row.bookId) },
                        onBookOptions = { onBookOptions(row.bookId) },
                        trailing = {
                            IconButton(
                                onClick = { removeTarget = row },
                                modifier = Modifier.minTouchTarget()
                            ) {
                                Icon(
                                    Icons.Outlined.Remove,
                                    contentDescription = stringResource(R.string.collection_menu_remove_book),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }

            if (showAddBook) {
                PickBookDialog(
                    candidates = state.candidateBooks,
                    titleRes = R.string.collection_options_add_title,
                    emptyMessage = stringResource(R.string.collection_options_add_none),
                    onSelect = { bookId ->
                        showAddBook = false
                        viewModel.addBookToCollection(bookId)
                    },
                    onDismiss = { showAddBook = false }
                )
            }

            removeTarget?.let { target ->
                ConfirmDeleteDialog(
                    title = stringResource(R.string.collection_menu_remove_book),
                    message = stringResource(R.string.collection_remove_confirm, target.title),
                    confirmText = stringResource(R.string.collection_menu_remove_book),
                    onConfirm = {
                        removeTarget = null
                        viewModel.removeBookFromCollection(target.bookId)
                    },
                    onDismiss = { removeTarget = null }
                )
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