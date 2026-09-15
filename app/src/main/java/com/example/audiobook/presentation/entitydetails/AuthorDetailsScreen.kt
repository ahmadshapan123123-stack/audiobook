package com.example.audiobook.presentation.entitydetails

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/**
 * صفحة تفاصيل المؤلف: اسمه ولونه، ثم كتبه مجمّعة بسلاسلها (نقرة على السلسلة =
 * صفحة السلسلة)، وكل كتاب يفتح صفحة تفاصيله — لا المشغّل مباشرة.
 */
@Composable
fun AuthorDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onSeriesSelected: (UUID) -> Unit,
    viewModel: AuthorDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showEntityEdit by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

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
                        EntityBookRowItem(row = row, onClick = { onBookSelected(row.bookId) })
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}