package com.example.audiobook.presentation.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import com.example.audiobook.playback.PlaybackController
import kotlinx.coroutines.launch

private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)

@Composable
fun BookmarksScreen(
    controller: PlaybackController,
    onBack: () -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = bottomContentInset()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CosmicScreenHeader(
                title = stringResource(R.string.bookmarks_title),
                subtitle = stringResource(R.string.bookmarks_subtitle),
                collapsed = collapsed,
                onBack = onBack
            )
            uiState.bookmarks.forEach { bookmark ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                R.string.bookmark_row_label,
                                formatTime(bookmark.positionMs),
                                stringResource(
                                    if (bookmark.type == BookmarkType.NOTE) R.string.bd_mark_type_note else R.string.bd_mark_type_bookmark
                                )
                            )
                        )
                        bookmark.noteText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(onClick = { controller.seekTo(bookmark.positionMs) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bookmarks_go_to)) }
                    TextButton(onClick = { viewModel.deleteBookmark(bookmark) }, modifier = Modifier.minTouchTarget()) { Text(stringResource(R.string.bookmarks_delete)) }
                }
            }
        }
    }
}
