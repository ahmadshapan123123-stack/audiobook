package com.example.audiobook.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppContinueListeningCard
import java.util.UUID

private enum class LibrarySection(val label: String) {
    ALL_BOOKS("كل الكتب"),
    CURRENTLY_LISTENING("أستمع الآن"),
    FINISHED("مكتملة"),
    FAVORITES("المفضلة"),
    COLLECTIONS("المجموعات"),
    RECENTLY_ADDED("أضيفت حديثًا")
}

private enum class LibraryLayout { GRID, LIST }

private fun Modifier.minTouchTarget(): Modifier =
    sizeIn(minWidth = 48.dp, minHeight = 48.dp)

@Composable
fun LibraryScreen(
    onBookSelected: (UUID) -> Unit,
    onManageRoots: () -> Unit = {},
    onStatistics: () -> Unit = {},
    onHistory: () -> Unit = {},
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedSection by remember { mutableStateOf(LibrarySection.ALL_BOOKS) }
    var layout by remember { mutableStateOf(LibraryLayout.GRID) }
    var searchQuery by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(LibraryStatusFilter.ALL) }
    var genre by remember { mutableStateOf<String?>(null) }
    var selectedCollection by remember { mutableStateOf("للاستماع الليلي") }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }

    val filteredBooks = uiState.filtered
    val sectionBooks = when (selectedSection) {
        LibrarySection.ALL_BOOKS -> filteredBooks
        LibrarySection.CURRENTLY_LISTENING -> uiState.books.filter { it.progressFraction in 0.01f..0.99f }
        LibrarySection.FINISHED -> uiState.books.filter { it.progressFraction >= 1f }
        LibrarySection.FAVORITES -> uiState.books.filter { it.isFavorite }
        LibrarySection.COLLECTIONS -> uiState.books
        LibrarySection.RECENTLY_ADDED -> uiState.books.sortedByDescending { it.addedOrder }.take(6)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg)) {
        Spacer(Modifier.height(AppSpacing.lg))
        Text("مكتبتك", style = MaterialTheme.typography.displaySmall)
        Text("كل ما تريد الاستماع إليه، في مكان هادئ وواضح", style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${uiState.books.size} كتاب في المكتبة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                TextButton(onClick = onHistory, modifier = Modifier.minTouchTarget()) { Text("السجل") }
                TextButton(onClick = onStatistics, modifier = Modifier.minTouchTarget()) { Text("الإحصائيات") }
                TextButton(onClick = onManageRoots, modifier = Modifier.minTouchTarget()) { Text("مجلدات المكتبة") }
            }
        }
        Spacer(Modifier.height(AppSpacing.md))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it; viewModel.updateSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("ابحث في مكتبتك") },
            placeholder = { Text("العنوان أو المؤلف") }
        )
        Spacer(Modifier.height(AppSpacing.lg))
        uiState.books.firstOrNull { it.progressFraction in 0.01f..0.99f }?.let { ContinueListeningCard(it) }
        Spacer(Modifier.height(AppSpacing.lg))
        if (selectedSection == LibrarySection.COLLECTIONS && uiState.collections.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                uiState.collections.forEach { collection ->
                    AssistChip(onClick = { selectedCollection = collection.name }, label = { Text(collection.name) })
                }
                OutlinedButton(onClick = { showCollectionDialog = true }, modifier = Modifier.minTouchTarget()) { Text("+ مجموعة") }
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (layout == LibraryLayout.GRID) 2 else 1),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    LibrarySection.entries.forEach { section ->
                        FilterChip(selected = section == selectedSection, onClick = { selectedSection = section }, label = { Text(section.label, maxLines = 1) }, modifier = Modifier.minTouchTarget())
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${sectionBooks.size} كتب", style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { layout = LibraryLayout.GRID }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.GridView, contentDescription = "عرض شبكي") }
                        IconButton(onClick = { layout = LibraryLayout.LIST }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(Icons.Outlined.List, contentDescription = "عرض قائمة") }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        FilterChip(selected = status == LibraryStatusFilter.ALL, onClick = { status = LibraryStatusFilter.ALL; viewModel.updateStatus(LibraryStatusFilter.ALL) }, label = { Text("الكل") }, modifier = Modifier.minTouchTarget())
                        FilterChip(selected = status == LibraryStatusFilter.IN_PROGRESS, onClick = { status = LibraryStatusFilter.IN_PROGRESS; viewModel.updateStatus(LibraryStatusFilter.IN_PROGRESS) }, label = { Text("قيد الاستماع") }, modifier = Modifier.minTouchTarget())
                        FilterChip(selected = status == LibraryStatusFilter.FINISHED, onClick = { status = LibraryStatusFilter.FINISHED; viewModel.updateStatus(LibraryStatusFilter.FINISHED) }, label = { Text("مكتملة") }, modifier = Modifier.minTouchTarget())
                        FilterChip(selected = genre == "رواية", onClick = { genre = if (genre == "رواية") null else "رواية"; viewModel.updateGenre(genre) }, label = { Text("رواية") }, modifier = Modifier.minTouchTarget())
                    }
                    Spacer(Modifier.height(AppSpacing.xs))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
AssistChip(onClick = { viewModel.updateSort(LibrarySort.NAME) }, label = { Text("الاسم") }, modifier = Modifier.minTouchTarget())
                        AssistChip(onClick = { viewModel.updateSort(LibrarySort.ADDED_DATE) }, label = { Text("الأحدث") }, modifier = Modifier.minTouchTarget())
                        AssistChip(onClick = { viewModel.updateSort(LibrarySort.LAST_PLAYED) }, label = { Text("آخر استماع") }, modifier = Modifier.minTouchTarget())
                        AssistChip(onClick = { viewModel.updateSort(LibrarySort.PROGRESS) }, label = { Text("الإنجاز") }, modifier = Modifier.minTouchTarget())
                    }
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            }
            items(sectionBooks, key = { it.book.id }) { book ->
                if (layout == LibraryLayout.GRID) {
                    BookGridCard(book, isFavorite = book.isFavorite, onBookSelected = { onBookSelected(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) })
                } else {
                    BookListRow(book, isFavorite = book.isFavorite, onBookSelected = { onBookSelected(book.book.id) }, onFavoriteToggle = { viewModel.toggleFavorite(book.book.id) })
                }
            }
        }
    }
    if (showCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text("إنشاء مجموعة") },
            text = { OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it }, singleLine = true, label = { Text("اسم المجموعة") }) },
            confirmButton = {
                TextButton(onClick = {
                    if (newCollectionName.isNotBlank()) viewModel.createCollection(newCollectionName)
                    newCollectionName = ""
                    showCollectionDialog = false
                }) { Text("إضافة") }
            },
            dismissButton = { TextButton(onClick = { showCollectionDialog = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun ContinueListeningCard(book: LibraryBookUi) {
    AppContinueListeningCard(book.book.title, book.authorName, book.progressFraction, formatRemaining(book.remainingMs), Color(0xFF356B68))
}

@Composable
private fun BookGridCard(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onBookSelected).padding(AppSpacing.xxs), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        CoverBlock(book, Modifier.fillMaxWidth().aspectRatio(.72f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(book.book.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onFavoriteToggle, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") }
        }
        Text(book.authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(progress = { book.progressFraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BookListRow(book: LibraryBookUi, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(onClick = onBookSelected).padding(vertical = AppSpacing.xs), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        CoverBlock(book, Modifier.size(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.authorName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${(book.progressFraction * 100).toInt()}٪", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onFavoriteToggle, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = if (isFavorite) "إزالة من المفضلة" else "إضافة إلى المفضلة") }
    }
}

@Composable
private fun CoverBlock(book: LibraryBookUi, modifier: Modifier) {
    Box(modifier = modifier.background(Color(0xFF356B68), RoundedCornerShape(AppSpacing.xs)), contentAlignment = Alignment.BottomStart) {
        Text("كتاب صوتي", modifier = Modifier.padding(AppSpacing.sm), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

private fun formatRemaining(ms: Long): String {
    if (ms <= 0L) return "مكتملة"
    val totalMinutes = ms / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} ساعة و${minutes} دقيقة" else "$minutes دقيقة"
}
