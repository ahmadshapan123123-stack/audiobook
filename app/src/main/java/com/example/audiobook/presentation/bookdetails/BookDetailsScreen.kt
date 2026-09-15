package com.example.audiobook.presentation.bookdetails

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.domain.usecases.CoverCandidate
import com.example.audiobook.domain.usecases.CoverCandidateSource
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

@Composable
fun BookDetailsScreen(
    onBack: () -> Unit = {},
    onPlay: (editionId: UUID) -> Unit,
    onPlayAt: (editionId: UUID, startPositionMs: Long) -> Unit,
    onBookmarks: (editionId: UUID) -> Unit,
    viewModel: BookDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("overview") }
    var saved by remember { mutableStateOf(false) }

    val book = uiState.book

    if (book == null) {
        Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg)) {
            TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_back)) }
            Text(stringResource(R.string.bd_book_not_found), style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    if (title.isBlank()) title = book.title
    if (author.isBlank()) author = uiState.authorName

    val defaultEdition = uiState.editions.firstOrNull { it.id == uiState.defaultEditionId }
    val coverColor = Color(uiState.coverColor.toInt())
    val progressFraction = if ((uiState.progress?.currentPositionMs ?: 0L) > 0L && (defaultEdition?.totalDurationMs ?: 0L) > 0L) {
        (uiState.progress!!.currentPositionMs.toFloat() / defaultEdition!!.totalDurationMs).coerceIn(0f, 1f)
    } else 0f
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Spacer(Modifier.height(AppSpacing.md))
        CosmicScreenHeader(
            title = stringResource(R.string.book_details_title),
            collapsed = collapsed,
            onBack = onBack,
            backContentDescription = stringResource(R.string.bd_back)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.Top) {
            AtherCoverBlock(
                title = book.title,
                coverColor = coverColor,
                modifier = Modifier.width(120.dp).aspectRatio(0.72f)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(book.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(uiState.authorName.ifBlank { stringResource(R.string.bd_edition_narrator) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (uiState.seriesName != null || book.genre != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        uiState.seriesName?.let { name ->
                            AssistChip(onClick = {}, label = { Text(stringResource(R.string.bd_series, name)) }, modifier = Modifier.minTouchTarget())
                        }
                        book.genre?.let { genre ->
                            AssistChip(onClick = {}, label = { Text(genre) }, modifier = Modifier.minTouchTarget())
                        }
                    }
                }
                Button(
                    onClick = { uiState.defaultEditionId?.let(onPlay) },
                    modifier = Modifier.minTouchTarget(),
                    enabled = uiState.defaultEditionId != null
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(stringResource(R.string.bd_play_default))
                }
            }
        }

        Text(stringResource(R.string.bd_progress_header), style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progressFraction > 0f) stringResource(R.string.bd_progress_percent, (progressFraction * 100).toInt()) else stringResource(R.string.bd_progress_start),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (uiState.defaultEditionId != null) {
                Text(formatDuration(uiState.progress?.currentPositionMs ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            FilterChip(activeTab == "overview", { activeTab = "overview" }, label = { Text(stringResource(R.string.bd_tab_overview)) }, modifier = Modifier.minTouchTarget())
            FilterChip(activeTab == "editions", { activeTab = "editions" }, label = { Text(stringResource(R.string.bd_tab_editions, uiState.editions.size)) }, modifier = Modifier.minTouchTarget())
            FilterChip(activeTab == "content", { activeTab = "content" }, label = { Text(stringResource(R.string.bd_tab_content)) }, modifier = Modifier.minTouchTarget())
        }

        HorizontalDivider()

        when (activeTab) {
            "overview" -> {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    SectionTitle(stringResource(R.string.bd_overview_header))
                    OutlinedTextField(title, { title = it; saved = false }, label = { Text(stringResource(R.string.bd_title_field)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(author, { author = it; saved = false }, label = { Text(stringResource(R.string.bd_author_field)) }, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                        FilledTonalButton(
                            onClick = {
                                if (title != book.title) viewModel.updateTitle(book, title)
                                if (author != uiState.authorName) viewModel.updateAuthor(book, author)
                                saved = true
                            },
                            modifier = Modifier.minTouchTarget()
                        ) { Text(stringResource(R.string.bd_save)) }
                        if (saved) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = stringResource(R.string.bd_saved_confirmation), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.bd_saved_confirmation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    SectionTitle(stringResource(R.string.bd_cover_header))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        FilledTonalButton(onClick = { viewModel.setUserCover(book, "user-selected-cover.jpg") }, modifier = Modifier.minTouchTarget()) {
                            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(AppSpacing.xs))
                            Text(stringResource(R.string.bd_set_user_cover))
                        }
                        TextButton(onClick = {
                            viewModel.rediscoverCover(
                                book.copy(isCoverUserSelected = false),
                                listOf(CoverCandidate("folder-cover.jpg", CoverCandidateSource.FOLDER_COVER))
                            )
                        }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_rediscover_cover)) }
                    }
                    Text(
                        if (book.isCoverUserSelected) stringResource(R.string.bd_cover_user_note) else stringResource(R.string.bd_cover_auto_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            "editions" -> EditionManagement(uiState.editions, uiState.defaultEditionId, viewModel)
            "content" -> {
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    SectionTitle(stringResource(R.string.bd_content_chapters_header, uiState.chapters.size))
                    Text(stringResource(R.string.bd_jump_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (uiState.chapters.isEmpty()) {
                        Text(stringResource(R.string.bd_content_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.defaultEditionId?.let { editionId ->
                            uiState.chapters.forEachIndexed { index, chapter ->
                                ChapterRow(
                                    number = index + 1,
                                    title = chapter.title ?: "",
                                    positionMs = chapter.startPositionMs
                                ) { onPlayAt(editionId, chapter.startPositionMs) }
                            }
                        } ?: Text(stringResource(R.string.bd_content_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider()

                    SectionTitle(stringResource(R.string.bd_content_marks_header, uiState.bookmarks.size))
                    if (uiState.bookmarks.isEmpty()) {
                        Text(stringResource(R.string.bd_marks_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        uiState.bookmarks.forEach { mark ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (mark.type == BookmarkType.NOTE) Icons.Outlined.Label else Icons.Outlined.BookmarkBorder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (mark.type == BookmarkType.NOTE) stringResource(R.string.bd_mark_type_note) else stringResource(R.string.bd_mark_type_bookmark),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    mark.noteText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                                }
                                Text(stringResource(R.string.bd_mark_position, formatDuration(mark.positionMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    uiState.defaultEditionId?.let { editionId ->
                        Button(onClick = { onBookmarks(editionId) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_open_bookmarks)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(AppSpacing.lg))
    }
}

@Composable
private fun ChapterRow(number: Int, title: String, positionMs: Long, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = onClick).padding(vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.small, modifier = Modifier.size(36.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            title.ifBlank { stringResource(R.string.bd_content_none) },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(formatDuration(positionMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EditionManagement(editions: List<EditionEntity>, defaultId: UUID?, viewModel: BookDetailsViewModel) {
    SectionTitle(stringResource(R.string.bd_editions_header))
    if (editions.isEmpty()) {
        Text(stringResource(R.string.bd_content_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    editions.forEach { edition ->
        var label by remember(edition.id, edition.label) { mutableStateOf(edition.label) }
        var narrator by remember(edition.id, edition.narratorName) { mutableStateOf(edition.narratorName.orEmpty()) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(edition.label, style = MaterialTheme.typography.titleMedium)
                        Text(
                            edition.narratorName ?: stringResource(R.string.bd_edition_narrator),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            stringResource(R.string.bd_edition_file_format, edition.fileFormat, formatDuration(edition.totalDurationMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (defaultId == edition.id) {
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.bd_edition_default_yes)) }, modifier = Modifier.minTouchTarget())
                    }
                }
                OutlinedTextField(label, { label = it }, label = { Text(edition.label) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(narrator, { narrator = it }, label = { Text(stringResource(R.string.bd_author_field)) }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    FilledTonalButton(onClick = {
                        viewModel.renameEdition(edition, label)
                        viewModel.changeNarrator(edition, narrator.ifBlank { null })
                    }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_save)) }
                    TextButton(onClick = { viewModel.setDefaultEdition(edition.id) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_edition_set_default)) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = { viewModel.deleteEdition(edition) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_edition_delete)) }
                    TextButton(onClick = { viewModel.splitEdition(edition) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bd_edition_split)) }
                }
            }
        }
    }
    Text(stringResource(R.string.bd_edition_authoritative_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}