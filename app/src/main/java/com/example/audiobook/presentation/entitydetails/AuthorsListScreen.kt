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

/** قائمة "عرض الكل" للمؤلفين: بحث + فرز + إجراءات سياقية وفتح صفحة المؤلف. */
@Composable
fun AuthorsListScreen(
    onBack: () -> Unit,
    onOpenAuthor: (UUID) -> Unit,
    onAuthorOptions: (UUID) -> Unit = {},
    viewModel: AuthorsListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(EntitySort.NAME) }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Spacer(Modifier.height(AppSpacing.md))
        CosmicScreenHeader(
            title = stringResource(R.string.authors_list_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )
        EntitySearchField(query = query, onQueryChange = { query = it }, hint = stringResource(R.string.authors_search_hint))
        EntitySortSelector(sort = sort, onSortChange = { sort = it })

        if (filtered.isEmpty()) {
            Text(
                stringResource(R.string.entity_empty_authors),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            filtered.forEach { row ->
                val author = row.author
                EntityListRowCard(
                    title = author.name,
                    subtitle = pluralStringResource(R.plurals.book_count, row.bookCount, row.bookCount),
                    avatarTitle = author.name,
                    avatarColor = Color(row.coverColor.toInt()),
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
        Spacer(Modifier.height(bottomContentInset()))
    }
}