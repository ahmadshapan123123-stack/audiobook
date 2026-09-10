package com.example.audiobook.presentation.bookmarks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.playback.PlaybackController
import kotlinx.coroutines.launch

@Composable
fun BookmarksScreen(
    controller: PlaybackController,
    onBack: () -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text("رجوع") }
            Text(
                "Bookmarks وNotes",
                modifier = Modifier.weight(1f).padding(start = AppSpacing.sm),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(uiState.bookmarks, key = { it.id }) { bookmark ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${bookmark.positionMs / 1000}s · ${if (bookmark.noteText.isNullOrBlank()) "Bookmark" else "Note"}")
                        bookmark.noteText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(onClick = { controller.seekTo(bookmark.positionMs) }, modifier = Modifier.minTouchTarget()) { Text("انتقال") }
                    TextButton(onClick = { viewModel.deleteBookmark(bookmark) }, modifier = Modifier.minTouchTarget()) { Text("حذف") }
                }
            }
        }
    }
}
