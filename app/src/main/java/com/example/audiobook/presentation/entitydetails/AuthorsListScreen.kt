package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import java.util.UUID

/**
 * REDESIGN — قائمة المؤلفين: رأس (عنوان + عدّ + بحث + فرز منبثق)،
 * بطاقات دائرية 56dp بتقدّم وشيفرون، وتجميع لاصق بالحرف الأول.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuthorsListScreen(
    onBack: () -> Unit,
    onOpenAuthor: (UUID) -> Unit,
    onAuthorOptions: (UUID) -> Unit = {},
    viewModel: AuthorsListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(EntitySort.NAME) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AuthorListRow?>(null) }
    var deleteTarget by remember { mutableStateOf<AuthorListRow?>(null) }

    val filtered = remember(state.rows, query, sort) {
        val normalizedQuery = ArabicSearchNormalizer.normalize(query)
        val base = if (normalizedQuery.isEmpty()) {
            state.rows
        } else {
            state.rows.filter { ArabicSearchNormalizer.normalize(it.author.name).contains(normalizedQuery) }
        }
        when (sort) {
            EntitySort.NAME -> base.sortedBy { ArabicSearchNormalizer.normalize(it.author.name) }
            EntitySort.BOOKS -> base.sortedWith(compareByDescending<AuthorListRow> { it.bookCount }.thenBy { ArabicSearchNormalizer.normalize(it.author.name) })
        }
    }
    // تجميع بالحرف الأول (بعد التطبيع) بترتيب الفرز الحالي.
    val grouped = remember(filtered) {
        filtered.groupBy { row ->
            ArabicSearchNormalizer.normalize(row.author.name).firstOrNull()?.toString() ?: "؟"
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg)) {
        Spacer(Modifier.height(AppSpacing.md))
        CosmicScreenHeader(
            title = stringResource(R.string.authors_list_title),
            collapsed = false,
            onBack = onBack,
            backAsTextButton = true
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                pluralStringResource(R.plurals.authors_count, filtered.size, filtered.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { searchVisible = !searchVisible }, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.authors_search_hint))
                }
                IconButton(onClick = { sortMenuOpen = true }, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Outlined.Sort, contentDescription = stringResource(R.string.entity_sort_books))
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.entity_sort_name)) },
                        onClick = { sortMenuOpen = false; sort = EntitySort.NAME }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.entity_sort_books)) },
                        onClick = { sortMenuOpen = false; sort = EntitySort.BOOKS }
                    )
                }
            }
        }
        if (searchVisible) {
            EntitySearchField(query = query, onQueryChange = { query = it }, hint = stringResource(R.string.authors_search_hint))
            Spacer(Modifier.height(AppSpacing.sm))
        }
        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.entity_empty_authors),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                contentPadding = PaddingValues(bottom = bottomContentInset())
            ) {
                grouped.forEach { (letter, rows) ->
                    stickyHeader(key = "h-$letter") {
                        Text(
                            text = "$letter · ${rows.size}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(vertical = AppSpacing.xs)
                        )
                    }
                    items(rows, key = { it.author.id }) { row ->
                        val author = row.author
                        EntityListRowCard(
                            title = author.name,
                            subtitle = pluralStringResource(R.plurals.book_count, row.bookCount, row.bookCount) +
                                " · " + pluralStringResource(R.plurals.series_count, row.seriesCount, row.seriesCount),
                            avatarTitle = author.name,
                            avatarColor = Color(row.coverColor.toInt()),
                            avatarSizeDp = 56,
                            avatarCircle = true,
                            progressFraction = row.progressFraction.takeIf { row.hasProgress },
                            showChevron = true,
                            onClick = { onOpenAuthor(author.id) },
                            onLongPress = { onAuthorOptions(author.id) },
                            menuActions = listOf(
                                EntityMenuAction(R.string.entity_context_open) { onOpenAuthor(author.id) },
                                EntityMenuAction(R.string.entity_context_rename) { renameTarget = row },
                                EntityMenuAction(R.string.entity_context_delete, danger = true) { deleteTarget = row }
                            )
                        )
                    }
                }
            }
        }

        renameTarget?.let { target ->
            InputDialog(
                title = stringResource(R.string.rename_author_title),
                label = stringResource(R.string.entity_edit_name_label),
                initialValue = target.author.name,
                onConfirm = {
                    viewModel.renameAuthor(target.author.id, it)
                    renameTarget = null
                },
                onDismiss = { renameTarget = null }
            )
        }
        deleteTarget?.let { target ->
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete_title),
                message = stringResource(R.string.confirm_delete_author, target.author.name, target.bookCount),
                onConfirm = {
                    deleteTarget = null
                    viewModel.deleteAuthor(target.author.id)
                },
                onDismiss = { deleteTarget = null }
            )
        }
    }
}
