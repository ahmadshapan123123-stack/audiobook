package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onAuthorSelected: (UUID) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEntityEdit by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll)
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Spacer(Modifier.height(AppSpacing.md))
        com.example.audiobook.presentation.theme.CosmicScreenHeader(
            title = stringResource(R.string.series_details_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )

        val series = state.series
        if (series == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            com.example.audiobook.presentation.entitydetails.EntityHeaderBlock(
                name = series.name,
                subtitle = state.authorName,
                count = state.books.size,
                coverTitle = state.books.firstOrNull()?.title ?: series.name,
                coverColor = Color(state.coverColor.toInt()),
                onSubtitleClick = state.authorId?.let { authorId ->
                    { onAuthorSelected(authorId) }
                }
            )

            com.example.audiobook.presentation.entitydetails.EntityEditButton(onClick = { showEntityEdit = true })
            if (showEntityEdit) {
                com.example.audiobook.presentation.entitydetails.EntityEditDialog(
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

            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(onClick = { showMergeDialog = true }) {
                    Text(stringResource(R.string.series_menu_merge))
                }
                OutlinedButton(onClick = { showDeleteDialog = true }) {
                    Text(stringResource(R.string.series_menu_delete), color = MaterialTheme.colorScheme.error)
                }
            }

            if (showDeleteDialog) {
                ConfirmDeleteDialog(
                    title = stringResource(R.string.confirm_delete_title),
                    message = stringResource(R.string.confirm_delete_series, series.name, state.books.size),
                    onConfirm = {
                        showDeleteDialog = false
                        viewModel.deleteSeries(onBack)
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
                                            viewModel.mergeSeries(target.id) { onBack() }
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

            com.example.audiobook.presentation.entitydetails.EntitySectionTitle(stringResource(R.string.entity_series_books_header))
            if (state.books.isEmpty()) {
                Text(stringResource(R.string.entity_no_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.books.forEach { row ->
                    com.example.audiobook.presentation.entitydetails.EntityBookRowItem(row = row, onClick = { onBookSelected(row.bookId) })
                }
            }
        }
        Spacer(Modifier.height(bottomContentInset()))
    }
}