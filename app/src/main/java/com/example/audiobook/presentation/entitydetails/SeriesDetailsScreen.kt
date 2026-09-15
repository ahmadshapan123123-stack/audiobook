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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/**
 * صفحة تفاصيل السلسلة: اسم السلسلة، المؤلف (نقرة = صفحة المؤلف)،
 * وقائمة كتبها بالترتيب — كل كتاب يفتح صفحة تفاصيله (لا المشغّل مباشرة).
 */
@Composable
fun SeriesDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    onAuthorSelected: (UUID) -> Unit,
    viewModel: SeriesDetailsViewModel = hiltViewModel()
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
            title = stringResource(R.string.series_details_title),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )

        val series = state.series
        if (series == null) {
            Text(stringResource(R.string.entity_not_found), style = MaterialTheme.typography.titleLarge)
        } else {
            EntityHeaderBlock(
                name = series.name,
                subtitle = state.authorName,
                count = state.books.size,
                coverTitle = state.books.firstOrNull()?.title ?: series.name,
                coverColor = Color(state.coverColor.toInt()),
                onSubtitleClick = state.authorId?.let { authorId ->
                    { onAuthorSelected(authorId) }
                }
            )

                        EntityEditButton(onClick = { showEntityEdit = true })
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
EntitySectionTitle(stringResource(R.string.entity_series_books_header))
            if (state.books.isEmpty()) {
                Text(stringResource(R.string.entity_no_books), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                state.books.forEach { row ->
                    EntityBookRowItem(row = row, onClick = { onBookSelected(row.bookId) })
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}