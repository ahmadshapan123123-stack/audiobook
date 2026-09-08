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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.playback.PlaybackController
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun BookmarksScreen(
    editionId: UUID,
    marks: MarksCoordinator,
    controller: PlaybackController,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val bookmarks by marks.bookmarks(editionId).collectAsState(initial = emptyList())
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("Bookmarks وNotes", style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(bookmarks, key = { it.id }) { bookmark ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${bookmark.positionMs / 1000}s · ${if (bookmark.noteText.isNullOrBlank()) "Bookmark" else "Note"}")
                        bookmark.noteText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Button(onClick = { scope.launch { marks.seekToBookmark(controller, bookmark) } }) { Text("انتقال") }
                    TextButton(onClick = { scope.launch { marks.deleteBookmark(bookmark) } }) { Text("حذف") }
                }
            }
        }
    }
}