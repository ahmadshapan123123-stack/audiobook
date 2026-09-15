package com.example.audiobook.presentation.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private fun formatPosition(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private enum class SavedTab { BOOKMARKS, NOTES, CHAPTERS }

@Composable
fun SavedScreen(
    onBack: () -> Unit,
    onOpenPlayer: (editionId: UUID, startMs: Long) -> Unit,
    viewModel: SavedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)
    var selectedTab by remember { mutableStateOf(SavedTab.BOOKMARKS) }
    var searchQuery by remember { mutableStateOf("") }
    var chapterToRename by remember { mutableStateOf<SavedChapter?>(null) }
    var renameText by remember { mutableStateOf("") }
    var bookmarkToDelete by remember { mutableStateOf<SavedBookmark?>(null) }
    var chapterToDelete by remember { mutableStateOf<SavedChapter?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Spacer(Modifier.height(AppSpacing.lg))
            CosmicScreenHeader(
                title = stringResource(R.string.saved_title),
                subtitle = stringResource(R.string.saved_subtitle),
                collapsed = collapsed,
                onBack = null
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                FilterChip(
                    selected = selectedTab == SavedTab.BOOKMARKS,
                    onClick = { selectedTab = SavedTab.BOOKMARKS },
                    label = { Text(stringResource(R.string.saved_tab_bookmarks)) },
                    modifier = Modifier.minTouchTarget()
                )
                FilterChip(
                    selected = selectedTab == SavedTab.NOTES,
                    onClick = { selectedTab = SavedTab.NOTES },
                    label = { Text(stringResource(R.string.saved_tab_notes)) },
                    modifier = Modifier.minTouchTarget()
                )
                FilterChip(
                    selected = selectedTab == SavedTab.CHAPTERS,
                    onClick = { selectedTab = SavedTab.CHAPTERS },
                    label = { Text(stringResource(R.string.saved_tab_chapters)) },
                    modifier = Modifier.minTouchTarget()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(AppSpacing.md))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it; viewModel.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(AppSpacing.md),
                    label = { Text(stringResource(R.string.saved_search_label)) },
                    placeholder = { Text(stringResource(R.string.saved_search_placeholder)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    viewModel.updateQuery("")
                                },
                                modifier = Modifier.minTouchTarget()
                            ) {
                                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.saved_search_clear))
                            }
                        }
                    }
                )
            }

            val bookmarks = if (selectedTab == SavedTab.BOOKMARKS) uiState.bookmarks else emptyList()
            val notes = if (selectedTab == SavedTab.NOTES) uiState.notes else emptyList()
            val chapters = if (selectedTab == SavedTab.CHAPTERS) uiState.chapters else emptyList()

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 168.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                when (selectedTab) {
                    SavedTab.BOOKMARKS -> {
                        if (bookmarks.isEmpty()) {
                            item { EmptySaved(searching = searchQuery.isNotBlank(), emptyTitleRes = R.string.saved_empty_bookmarks_title) }
                        } else {
                            items(bookmarks, key = { "b_${it.bookId}" }) { group ->
                                SavedGroup(
                                    group = group,
                                    onOpen = { onOpenPlayer(it.editionId, it.positionMs) },
                                    onDelete = { bookmarkToDelete = it }
                                )
                            }
                        }
                    }
                    SavedTab.NOTES -> {
                        if (notes.isEmpty()) {
                            item { EmptySaved(searching = searchQuery.isNotBlank(), emptyTitleRes = R.string.saved_empty_notes_title) }
                        } else {
                            items(notes, key = { "n_${it.bookId}" }) { group ->
                                SavedGroup(
                                    group = group,
                                    onOpen = { onOpenPlayer(it.editionId, it.positionMs) },
                                    onDelete = { bookmarkToDelete = it }
                                )
                            }
                        }
                    }
                    SavedTab.CHAPTERS -> {
                        if (chapters.isEmpty()) {
                            item { EmptySaved(searching = searchQuery.isNotBlank(), emptyTitleRes = R.string.saved_empty_chapters_title) }
                        } else {
                            items(chapters, key = { "c_${it.bookId}" }) { group ->
                                ChapterGroup(
                                    group = group,
                                    onOpen = { onOpenPlayer(it.editionId, it.startPositionMs) },
                                    onRename = {
                                        chapterToRename = it
                                        renameText = it.title.orEmpty()
                                    },
                                    onDelete = { chapterToDelete = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    bookmarkToDelete?.let { bookmark ->
        AlertDialog(
            onDismissRequest = { bookmarkToDelete = null },
            title = { Text(stringResource(R.string.saved_delete)) },
            text = { Text(bookmark.bookTitle) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBookmark(bookmark.bookmarkId)
                    bookmarkToDelete = null
                }) { Text(stringResource(R.string.saved_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { bookmarkToDelete = null }) { Text(stringResource(R.string.saved_cancel)) }
            }
        )
    }

    chapterToDelete?.let { chapter ->
        AlertDialog(
            onDismissRequest = { chapterToDelete = null },
            title = { Text(stringResource(R.string.saved_delete)) },
            text = { Text(chapter.title ?: stringResource(R.string.saved_tab_chapters)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChapter(chapter.chapterId)
                    chapterToDelete = null
                }) { Text(stringResource(R.string.saved_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { chapterToDelete = null }) { Text(stringResource(R.string.saved_cancel)) }
            }
        )
    }

    chapterToRename?.let { chapter ->
        AlertDialog(
            onDismissRequest = { chapterToRename = null },
            title = { Text(stringResource(R.string.saved_rename)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.saved_rename_title)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) viewModel.renameChapter(chapter.chapterId, renameText.trim())
                    chapterToRename = null
                }) { Text(stringResource(R.string.saved_rename_save)) }
            },
            dismissButton = {
                TextButton(onClick = { chapterToRename = null }) { Text(stringResource(R.string.saved_cancel)) }
            }
        )
    }
}

@Composable
private fun SavedGroup(
    group: SavedBookGroup,
    onOpen: (SavedBookmark) -> Unit,
    onDelete: (SavedBookmark) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        GroupHeader(bookTitle = group.bookTitle, authorName = group.authorName, seriesName = group.seriesName, coverColor = group.coverColor)
        group.items.forEach { item ->
            SavedItemRow(
                item = item,
                onOpen = { onOpen(item) },
                onDelete = { onDelete(item) }
            )
        }
    }
}

@Composable
private fun ChapterGroup(
    group: SavedChapterGroup,
    onOpen: (SavedChapter) -> Unit,
    onRename: (SavedChapter) -> Unit,
    onDelete: (SavedChapter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        GroupHeader(bookTitle = group.bookTitle, authorName = group.authorName, seriesName = group.seriesName, coverColor = group.coverColor)
        group.chapters.forEach { chapter ->
            ChapterRow(
                chapter = chapter,
                onOpen = { onOpen(chapter) },
                onRename = { onRename(chapter) },
                onDelete = { onDelete(chapter) }
            )
        }
    }
}

@Composable
private fun GroupHeader(
    bookTitle: String,
    authorName: String,
    seriesName: String?,
    coverColor: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AtherCoverBlock(
            title = bookTitle,
            coverColor = Color(coverColor.toInt()),
            modifier = Modifier.size(44.dp)
        )
        Column {
            Text(bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(
                    authorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                seriesName?.let {
                    Text(
                        stringResource(R.string.home_series_of, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedItemRow(
    item: SavedBookmark,
    onOpen: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .minTouchTarget()
            .clickable(onClick = onOpen)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            item.noteText?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                stringResource(R.string.bookmark_row_label, formatPosition(item.positionMs), item.chapterLabel ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.saved_item_saved_at, formatDate(item.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete, modifier = Modifier.minTouchTarget()) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.saved_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChapterRow(
    chapter: SavedChapter,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .minTouchTarget()
            .clickable(onClick = onOpen)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(AppSpacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                chapter.title ?: stringResource(R.string.saved_tab_chapters),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                stringResource(R.string.saved_item_position, formatPosition(chapter.startPositionMs)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onRename, modifier = Modifier.minTouchTarget()) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.saved_rename), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete, modifier = Modifier.minTouchTarget()) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.saved_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptySaved(searching: Boolean, emptyTitleRes: Int) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (searching) {
            Icon(Icons.Outlined.SearchOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.no_results_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.no_results_hint), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(stringResource(emptyTitleRes), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    when (emptyTitleRes) {
                        R.string.saved_empty_bookmarks_title -> R.string.saved_empty_bookmarks_hint
                        R.string.saved_empty_notes_title -> R.string.saved_empty_notes_hint
                        else -> R.string.saved_empty_chapters_hint
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}