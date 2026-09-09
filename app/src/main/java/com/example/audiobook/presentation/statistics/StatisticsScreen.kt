package com.example.audiobook.presentation.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.example.audiobook.presentation.theme.AppBookCard
import com.example.audiobook.presentation.theme.AppSpacing

/**
 * [R4-النقطة 3] شاشة Statistics الحقيقية المربوطة بـ[StatisticsViewModel]:
 * وقت الاستماع (اليوم/الأسبوع/الشهر)، الكتب المكتملة، الفصول المكتملة
 * (حسب قاعدة الـ90% الحرفية)، الستريك، ومتوسط السرعة.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.statistics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("الإحصائيات", style = MaterialTheme.typography.headlineSmall)
        }
        AppBookCard("الاستماع اليوم", formatDuration(state.listeningTodayMs))
        AppBookCard("الاستماع خلال الأسبوع", formatDuration(state.listeningWeekMs))
        AppBookCard("الاستماع خلال الشهر", formatDuration(state.listeningMonthMs))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = "الكتب المكتملة",
                subtitle = state.completedBooks.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = "الفصول المكتملة",
                subtitle = state.completedChapters.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = "الستريك (أيام متتالية)",
                subtitle = state.currentStreak.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = "متوسط السرعة",
                subtitle = if (state.averageSpeed > 0f) "${state.averageSpeed}×" else "—",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours} س ${minutes} د" else "${minutes} د"
}