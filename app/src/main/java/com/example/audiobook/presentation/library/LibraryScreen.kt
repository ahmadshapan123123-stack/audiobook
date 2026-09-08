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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppContinueListeningCard
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer

private enum class LibrarySection(val label: String) {
    ALL_BOOKS("كل الكتب"),
    CURRENTLY_LISTENING("أستمع الآن"),
    FINISHED("مكتملة"),
    FAVORITES("المفضلة"),
    COLLECTIONS("المجموعات"),
    RECENTLY_ADDED("أضيفت حديثًا")
}

private enum class LibraryLayout { GRID, LIST }

private data class LibraryBook(
    val title: String,
    val author: String,
    val progress: Float,
    val remaining: String,
    val coverColor: Color,
    val genre: String,
    val series: String?,
    val addedOrder: Int,
    val lastPlayedOrder: Int
)

private val sampleBooks = listOf(
    LibraryBook("ما وراء الطبيعة", "أحمد خالد توفيق", .42f, "٦ ساعات متبقية", Color(0xFF356B68), "خيال", "سلسلة ما وراء الطبيعة", 6, 5),
    LibraryBook("موسم الهجرة إلى الشمال", "الطيب صالح", .78f, "ساعة و٢٠ دقيقة", Color(0xFF9A583E), "رواية", null, 5, 2),
    LibraryBook("رجال في الشمس", "غسان كنفاني", 1f, "مكتملة", Color(0xFF6D5A83), "رواية", null, 4, 1),
    LibraryBook("عائد إلى حيفا", "غسان كنفاني", .18f, "٤ ساعات متبقية", Color(0xFF38617A), "رواية", null, 3, 4),
    LibraryBook("حي بن يقظان", "ابن طفيل", .64f, "٥٠ دقيقة", Color(0xFF7D683B), "فلسفة", null, 2, 3),
    LibraryBook("الأيام", "طه حسين", 0f, "لم تبدأ", Color(0xFF7C4F5B), "سيرة", null, 1, 6)
)

@Composable
fun LibraryScreen(onBookSelected: () -> Unit = {}) {
    var selectedSection by remember { mutableStateOf(LibrarySection.ALL_BOOKS) }
    var layout by remember { mutableStateOf(LibraryLayout.GRID) }
    var searchQuery by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(LibrarySort.NAME) }
    var status by remember { mutableStateOf(LibraryStatusFilter.ALL) }
    var genre by remember { mutableStateOf<String?>(null) }
    val favoriteTitles = remember { mutableStateOf(setOf("ما وراء الطبيعة")) }
    val collections = remember { mutableStateListOf("للاستماع الليلي", "قراءات عربية") }
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    var showCollectionDialog by remember { mutableStateOf(false) }
    var newCollectionName by remember { mutableStateOf("") }
    val sectionBooks = when (selectedSection) {
        LibrarySection.CURRENTLY_LISTENING -> sampleBooks.filter { it.progress in 0.01f..0.99f }
        LibrarySection.FINISHED -> sampleBooks.filter { it.progress >= 1f }
        LibrarySection.FAVORITES -> sampleBooks.filter { it.title in favoriteTitles.value }
        LibrarySection.COLLECTIONS -> selectedCollection?.let { collection ->
            when (collection) {
                "للاستماع الليلي" -> sampleBooks.filter { it.progress in 0f..0.99f }
                "قراءات عربية" -> sampleBooks.filter { it.author.contains("غسان") || it.author.contains("طه") }
                else -> emptyList()
            }
        } ?: emptyList()
        LibrarySection.RECENTLY_ADDED -> sampleBooks.takeLast(3)
        else -> sampleBooks
    }
    val books = sectionBooks.filter { book ->
        ArabicSearchNormalizer.matches(searchQuery, book.title, book.author)
    }.filter { book ->
        status == LibraryStatusFilter.ALL ||
            (status == LibraryStatusFilter.FINISHED && book.progress >= 1f) ||
            (status == LibraryStatusFilter.IN_PROGRESS && book.progress in 0.01f..0.99f) ||
            (status == LibraryStatusFilter.NOT_STARTED && book.progress == 0f)
    }.filter { book -> genre == null || book.genre == genre }
        .sortedWith(
            when (sort) {
                LibrarySort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
                LibrarySort.ADDED_DATE -> compareByDescending { it.addedOrder }
                LibrarySort.LAST_PLAYED -> compareByDescending { it.lastPlayedOrder }
                LibrarySort.PROGRESS -> compareByDescending { it.progress }
            }
        )

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.lg)) {
        Spacer(Modifier.height(AppSpacing.lg))
        Text("مكتبتك", style = MaterialTheme.typography.displaySmall)
        Text("كل ما تريد الاستماع إليه، في مكان هادئ وواضح", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(AppSpacing.md))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("ابحث في مكتبتك") },
            placeholder = { Text("العنوان أو المؤلف") }
        )
        Spacer(Modifier.height(AppSpacing.lg))
        ContinueListeningCard(sampleBooks.first())
        Spacer(Modifier.height(AppSpacing.lg))
        if (selectedSection == LibrarySection.COLLECTIONS) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                collections.forEach { collection ->
                    AssistChip(onClick = { selectedCollection = collection }, label = { Text(collection) })
                }
                OutlinedButton(onClick = { showCollectionDialog = true }) { Text("+ مجموعة") }
            }
            Spacer(Modifier.height(AppSpacing.md))
        }
        LazyColumn(horizontalAlignment = Alignment.Start) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    LibrarySection.entries.forEach { section ->
                        FilterChip(selected = section == selectedSection, onClick = { selectedSection = section }, label = { Text(section.label, maxLines = 1) })
                    }
                }
                Spacer(Modifier.height(AppSpacing.md))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${books.size} كتب", style = MaterialTheme.typography.titleMedium)
                    Row {
                        IconButton(onClick = { layout = LibraryLayout.GRID }) { Icon(Icons.Outlined.GridView, "عرض شبكي") }
                        IconButton(onClick = { layout = LibraryLayout.LIST }) { Icon(Icons.Outlined.List, "عرض قائمة") }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    FilterChip(selected = status == LibraryStatusFilter.ALL, onClick = { status = LibraryStatusFilter.ALL }, label = { Text("الكل") })
                    FilterChip(selected = status == LibraryStatusFilter.IN_PROGRESS, onClick = { status = LibraryStatusFilter.IN_PROGRESS }, label = { Text("قيد الاستماع") })
                    FilterChip(selected = status == LibraryStatusFilter.FINISHED, onClick = { status = LibraryStatusFilter.FINISHED }, label = { Text("مكتملة") })
                    FilterChip(selected = genre == "رواية", onClick = { genre = if (genre == "رواية") null else "رواية" }, label = { Text("رواية") })
                }
                Spacer(Modifier.height(AppSpacing.xs))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    AssistChip(onClick = { sort = LibrarySort.NAME }, label = { Text("الاسم") })
                    AssistChip(onClick = { sort = LibrarySort.ADDED_DATE }, label = { Text("الأحدث") })
                    AssistChip(onClick = { sort = LibrarySort.LAST_PLAYED }, label = { Text("آخر استماع") })
                    AssistChip(onClick = { sort = LibrarySort.PROGRESS }, label = { Text("الإنجاز") })
                }
                Spacer(Modifier.height(AppSpacing.sm))
                if (layout == LibraryLayout.GRID) {
                    LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), modifier = Modifier.height(600.dp)) {
                        items(books) { book -> BookGridCard(book, book.title in favoriteTitles.value, onBookSelected) { toggleFavorite(book.title, favoriteTitles) } }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) { books.forEach { BookListRow(it, it.title in favoriteTitles.value, onBookSelected) { toggleFavorite(it.title, favoriteTitles) } } }
                }
            }
        }
    }
    if (showCollectionDialog) {
        AlertDialog(
            onDismissRequest = { showCollectionDialog = false },
            title = { Text("إنشاء مجموعة") },
            text = { androidx.compose.material3.OutlinedTextField(value = newCollectionName, onValueChange = { newCollectionName = it }, singleLine = true, label = { Text("اسم المجموعة") }) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (newCollectionName.isNotBlank() && newCollectionName !in collections) collections.add(newCollectionName.trim())
                    newCollectionName = ""
                    showCollectionDialog = false
                }) { Text("إضافة") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showCollectionDialog = false }) { Text("إلغاء") } }
        )
    }
}

private fun toggleFavorite(title: String, favorites: androidx.compose.runtime.MutableState<Set<String>>) {
    favorites.value = if (title in favorites.value) favorites.value - title else favorites.value + title
}

@Composable
private fun ContinueListeningCard(book: LibraryBook) {
    AppContinueListeningCard(book.title, book.author, book.progress, book.remaining, book.coverColor)
}

@Composable
private fun BookGridCard(book: LibraryBook, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onBookSelected).padding(AppSpacing.xxs), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        CoverBlock(book, Modifier.fillMaxWidth().aspectRatio(.72f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(book.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            IconButton(onClick = onFavoriteToggle) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "المفضلة") }
        }
        Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BookListRow(book: LibraryBook, isFavorite: Boolean, onBookSelected: () -> Unit, onFavoriteToggle: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onBookSelected).padding(vertical = AppSpacing.xs), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
        CoverBlock(book, Modifier.size(64.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${(book.progress * 100).toInt()}٪", style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = onFavoriteToggle) { Icon(if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "المفضلة") }
    }
}

@Composable
private fun CoverBlock(book: LibraryBook, modifier: Modifier) {
    Box(modifier = modifier.background(book.coverColor, RoundedCornerShape(AppSpacing.xs)), contentAlignment = Alignment.BottomStart) {
        Text("كتاب صوتي", modifier = Modifier.padding(AppSpacing.sm), color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}