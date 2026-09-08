package com.example.audiobook.presentation.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audiobook.playback.PlaybackController
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(controller: PlaybackController, onBack: () -> Unit = {}) {
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = onBack) { Text("رجوع") }
        Text("مشغل الكتاب", style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(
            progress = { if (state.durationMs == 0L) 0f else state.positionMs.toFloat() / state.durationMs },
            modifier = Modifier.fillMaxWidth()
        )
        Text("${state.positionMs / 1_000}s / ${state.durationMs / 1_000}s")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { controller.skipBack15Seconds() }) { Text("-15s") }
            Button(onClick = { if (state.isPlaying) controller.pause() else controller.play() }) { Text(if (state.isPlaying) "إيقاف" else "تشغيل") }
            Button(onClick = { controller.skipForward15Seconds() }) { Text("+15s") }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { scope.launch { controller.previousChapter() } }) { Text("الفصل السابق") }
            Button(onClick = { scope.launch { controller.nextChapter() } }) { Text("الفصل التالي") }
        }
        state.missingFileMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}