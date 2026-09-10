package com.example.audiobook.presentation.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.presentation.theme.minTouchTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * [R4-النقطة 3] شاشة History الحقيقية: قائمة زمنية تنازلية بآخر الجلسات
 * (اسم الكتاب، التاريخ، المدة) — مربوطة بـ[StatisticsViewModel].
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text("رجوع") }
            Text("السجل", style = MaterialTheme.typography.headlineSmall)
        }
        if (history.sessions.isEmpty()) {
            Text("لا توجد جلسات بعد", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history.sessions, key = { it.sessionId }) { row -> HistoryRow(row) }
            }
        }
    }
}

@Composable
private fun HistoryRow(row: ListeningHistoryRow) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(row.bookTitle ?: row.editionLabel ?: "نسخة محذوفة", style = MaterialTheme.typography.titleMedium)
        Text(
            "${formatDate(row.startedAt)} · ${formatDuration(row.durationListenedMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours} س ${minutes} د" else "${minutes} د"
}