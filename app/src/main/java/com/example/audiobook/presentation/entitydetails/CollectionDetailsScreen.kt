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
 * صفحة تفاصيل المجموعة: اسمها ووصفها وقائمة كتبها — كل كتاب يفتح
 * صفحة تفاصيله، لا المشغّل مباشرة.
 */
@Composable
fun CollectionDetailsScreen(
    onBack: () -> Unit,
    onBookSelected: (UUID) -> Unit,
    viewModel: CollectionDetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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

            EntitySectionTitle(stringResource(R.string.entity_books_header))
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