package com.example.audiobook.presentation.reviewmatches

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.EditionSignals
import com.example.audiobook.presentation.theme.AppBookCard
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed

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
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = bottomContentInset()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        CosmicScreenHeader(
            title = stringResource(R.string.review_title),
            subtitle = stringResource(R.string.review_subtitle),
            collapsed = collapsed,
            onBack = onBack,
            backAsTextButton = true
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = stringResource(R.string.review_tab_files),
                subtitle = state.summary.files.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = stringResource(R.string.review_tab_books),
                subtitle = state.summary.books.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            AppBookCard(
                title = stringResource(R.string.review_tab_series),
                subtitle = state.summary.series.toString(),
                modifier = Modifier.weight(1f)
            )
            AppBookCard(
                title = stringResource(R.string.review_tab_authors),
                subtitle = state.summary.authors.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        AppBookCard(
            title = stringResource(R.string.review_suspect_cases),
            subtitle = state.summary.suspectCases.toString()
        )
        if (state.cases.isEmpty()) {
            Text(
                stringResource(R.string.review_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.lg)
            )
        } else {
            state.cases.forEach { uiCase -> ReviewCaseCard(uiCase, viewModel) }
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
            Text(stringResource(R.string.review_subject), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(uiCase.subjectLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.review_confidence, percentOf(uiCase.subjectConfidence)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SignalRows(uiCase.subjectSignals)

            Row(modifier = Modifier.padding(top = AppSpacing.xs)) {
                Text(stringResource(R.string.review_compared_to), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            }
            Text(uiCase.candidateLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.review_confidence, percentOf(uiCase.candidateConfidence)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SignalRows(uiCase.candidateSignals)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                TextButton(
                    onClick = { viewModel.decideSameEdition(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text(stringResource(R.string.review_same)) }
                TextButton(
                    onClick = { viewModel.decideDifferentEdition(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text(stringResource(R.string.review_different)) }
                TextButton(
                    onClick = { viewModel.decideNotSameBook(uiCase.subjectEditionId, uiCase.candidateEditionId) },
                    modifier = Modifier.minTouchTarget()
                ) { Text(stringResource(R.string.review_not_same)) }
            }
        }
    }
}

@Composable
private fun SignalRows(signals: EditionSignals) {
    readableSignals(signals).forEach { (label, value) ->
        Text(
            stringResource(R.string.review_signal_row, label, value),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** الإشارات نفسها التي ستُلتقط إذا اتُّخذ القرار (نفس مصدر EditionStoredSignals). */
@Composable
private fun readableSignals(signals: EditionSignals): List<Pair<String, String>> = buildList {
    signals.resolvedTitle()?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.review_signal_title) to it) }
    signals.folderName.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.review_signal_folder) to it) }
    signals.authorFolderName?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.review_signal_author_folder) to it) }
    signals.narrator?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.review_signal_narrator) to it) }
    signals.seriesPart?.let { add(stringResource(R.string.review_signal_series) to if (it.partNumber != null) "${it.pattern} ${it.partNumber}" else it.pattern) }
    if (signals.totalDurationMs > 0L) add(stringResource(R.string.review_signal_duration) to formatDuration(signals.totalDurationMs))
    if (signals.fileCount > 0) add(stringResource(R.string.review_signal_files) to signals.fileCount.toString())
    signals.format?.takeIf { it.isNotBlank() }?.let { add(stringResource(R.string.review_signal_format) to it) }
}

@Composable
private fun percentOf(confidence: Float): String = stringResource(R.string.review_percent, (confidence * 100).toInt())

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) {
        stringResource(R.string.review_duration_hm, hours, minutes)
    } else {
        stringResource(R.string.review_duration_m, minutes)
    }
}