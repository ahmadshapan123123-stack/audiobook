package com.example.audiobook.presentation.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * [R4-النقطة 3] شاشة History الحقيقية: قائمة زمنية تنازلية بآخر الجلسات
 * (اسم الكتاب، التاريخ، المدة) — مربوطة بـ[StatisticsViewModel].
 * النقر على كتاب يفتح صفحة تفاصيل الملف عبر [onOpenEdition].
 */
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenBook: (UUID) -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = bottomContentInset()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        CosmicScreenHeader(
            title = stringResource(R.string.history_title),
            subtitle = stringResource(R.string.history_subtitle),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )
        if (history.sessions.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            history.sessions.forEach { session ->
                HistoryRow(session, onOpen = {
                    session.bookId?.let(onOpenBook)
                })
            }
        }
    }
    }
}

@Composable
private fun HistoryRow(row: ListeningHistoryRow, onOpen: () -> Unit) {
    val bookId = row.bookId
    Column(
        modifier = if (bookId != null) Modifier.fillMaxWidth().clickable(onClick = onOpen).minTouchTarget() else Modifier.fillMaxWidth()
    ) {
        Text(row.bookTitle ?: row.editionLabel ?: stringResource(R.string.history_deleted_edition), style = MaterialTheme.typography.titleMedium)
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

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        stringResource(R.string.history_duration_hm, hours, minutes)
    } else {
        stringResource(R.string.history_duration_m, minutes)
    }
}