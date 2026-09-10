package com.example.audiobook.presentation.reviewmatches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.domain.usecases.EditionSignals
import com.example.audiobook.presentation.theme.AppBookCard
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.minTouchTarget

/**
 * [R7-النقطة 2] شاشة مراجعة المطابقات: ملخص رقمي محسوب فعلًا من قاعدة البيانات
 * (الملفات/الكتب/السلاسل/المؤلفين/الحالات المشكوك فيها بعد آخر Scan) وقائمة
 * الحالات متوسطة/منخفضة الثقة فقط، مع الإشارات القابلة للقراءة والأزرار الثلاثة
 * (نفس الإصدار / إصدار مختلف / ليس نفس الكتاب) — كل قرار يُكتب فعليًا في
 * [com.example.audiobook.data.room.entity.EditionMatchDecisionEntity].
 */
@Composable
fun ReviewMatchesScreen(
    onBack: () -> Unit,
    viewModel: ReviewMatchesViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text("رجوع") }
            Text("مراجعة المطابقات", style = MaterialTheme.typography.headlineSmall)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = "الملفات",
                subtitle = state.summary.files.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = "الكتب",
                subtitle = state.summary.books.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = "السلاسل",
                subtitle = state.summary.series.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = "المؤلفون",
                subtitle = state.summary.authors.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        AppBookCard(
            title = "الحالات المشكوك فيها",
            subtitle = state.summary.suspectCases.toString()
        )
        if (state.cases.isEmpty()) {
            Text(
                "لا توجد حالات تحتاج مراجعة",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.lg)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(state.cases, key = { it.subjectEditionId }) { uiCase ->
                    ReviewCaseCard(uiCase, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ReviewCaseCard(uiCase: ReviewCaseUi, viewModel: ReviewMatchesViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(AppSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Text("الإصدار المشكوك فيه", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(uiCase.subjectLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("الثقة: ${percentOf(uiCase.subjectConfidence)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SignalRows(uiCase.subjectSignals)

            Row(modifier = Modifier.padding(top = AppSpacing.xs)) {
                Text("يقارن بـ:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            Text(uiCase.candidateLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("الثقة: ${percentOf(uiCase.candidateConfidence)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SignalRows(uiCase.candidateSignals)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                TextButton(
                    onClick = { viewModel.decideSameEdition(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text("نفس الإصدار") }
                TextButton(
                    onClick = { viewModel.decideDifferentEdition(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text("إصدار مختلف") }
                TextButton(
                    onClick = { viewModel.decideNotSameBook(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text("ليس نفس الكتاب") }
            }
        }
    }
}

@Composable
private fun SignalRows(signals: EditionSignals) {
    readableSignals(signals).forEach { (label, value) ->
        Text(
            "$label: $value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** الإشارات نفسها التي ستُلتقط إذا اتُّخذ القرار (نفس مصدر EditionStoredSignals). */
private fun readableSignals(signals: EditionSignals): List<Pair<String, String>> = buildList {
    signals.resolvedTitle()?.takeIf { it.isNotBlank() }?.let { add("العنوان" to it) }
    signals.folderName.takeIf { it.isNotBlank() }?.let { add("المجلد" to it) }
    signals.authorFolderName?.takeIf { it.isNotBlank() }?.let { add("مجلد المؤلف" to it) }
    signals.narrator?.takeIf { it.isNotBlank() }?.let { add("الراوي" to it) }
    signals.seriesPart?.let { add("السلسلة" to if (it.partNumber != null) "${it.pattern} ${it.partNumber}" else it.pattern) }
    if (signals.totalDurationMs > 0L) add("المدة" to formatDuration(signals.totalDurationMs))
    if (signals.fileCount > 0) add("الملفات" to signals.fileCount.toString())
    signals.format?.takeIf { it.isNotBlank() }?.let { add("الصيغة" to it) }
}

private fun percentOf(confidence: Float): String = "${(confidence * 100).toInt()}٪"

private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours} س ${minutes} د" else "${minutes} د"
}