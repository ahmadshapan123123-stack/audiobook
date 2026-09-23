package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/** قائمة "عرض الكل" للسلاسل: بحث + فرز + إجراءات سياقية وفتح صفحة السلسلة. */
@Composable
fun SeriesListScreen(
    onBack: () -> Unit,
    onOpenSeries: (UUID) -> Unit,
    onSeriesOptions: (UUID) -> Unit = {},
    viewModel: SeriesListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(EntitySort.NAME) }
    var renameTarget by remember { mutableStateOf<SeriesListRow?>(null) }
    var deleteTarget by remember { mutableStateOf<SeriesListRow?>(null) }

    val filtered = remember(state.rows, query, sort) {
        val normalizedQuery = ArabicSearchNormalizer.normalize(query)
        val base = if (normalizedQuery.isEmpty()) {
            state.rows
        } else {
            state.rows.filter {
                ArabicSearchNormalizer.normalize(it.series.name).contains(normalizedQuery) ||
                    ArabicSearchNormalizer.normalize(it.authorName).contains(normalizedQuery)
            }
        }
        when (sort) {
            EntitySort.NAME -> base.sortedBy { ArabicSearchNormalizer.normalize(it.series.name) }
            EntitySort.BOOKS -> base.sortedWith(compareByDescending<SeriesListRow> { it.bookCount }.thenBy { ArabicSearchNormalizer.normalize(it.series.name) })
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Spacer(Modifier.height(AppSpacing.md))
        CosmicScreenHeader(
            title = stringResource(R.string.series_list_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )
        EntitySearchField(query = query, onQueryChange = { query = it }, hint = stringResource(R.string.series_search_hint))
        EntitySortSelector(sort = sort, onSortChange = { sort = it })

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.entity_empty_series),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filtered.forEach { row ->
                val series = row.series
                EntityListRowCard(
                    title = series.name,
                    subtitle = pluralStringResource(R.plurals.book_count, row.bookCount, row.bookCount),
                    avatarTitle = series.name,
                    avatarColor = Color(row.coverColor.toInt()),
                    onClick = { onOpenSeries(series.id) },
                    onLongPress = { onSeriesOptions(series.id) },
                    menuActions = listOf(
                        EntityMenuAction(R.string.entity_context_open) { onOpenSeries(series.id) },
                        EntityMenuAction(R.string.entity_context_rename) { renameTarget = row },
                        EntityMenuAction(R.string.entity_context_delete, danger = true) { deleteTarget = row }
                    )
                )
            }
        }

        renameTarget?.let { target ->
            InputDialog(
                title = stringResource(R.string.rename_series_title),
                label = stringResource(R.string.entity_edit_name_label),
                initialValue = target.series.name,
                onConfirm = {
                    viewModel.renameSeries(target.series.id, it)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }
        deleteTarget?.let { target ->
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete_title),
                message = stringResource(R.string.confirm_delete_series, target.series.name, target.bookCount),
                onConfirm = {
                    deleteTarget = null
                    viewModel.deleteSeries(target.series.id)
                },
                onDismiss = { deleteTarget = null }
            )
        }
        Spacer(Modifier.height(bottomContentInset()))
    }
}