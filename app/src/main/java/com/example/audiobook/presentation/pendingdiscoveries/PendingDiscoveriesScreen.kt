package com.example.audiobook.presentation.pendingdiscoveries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.DiscoveryDecision
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.UUID

/** طلب إسناد إلى سلسلة/مؤلف ينتظر إدخال الاسم. */
private data class NameRequest(val decision: DiscoveryDecision)

// =====================================================================
// البوب-أب: يعرضه MainActivity فور انتهاء فحص جذر ذي أولوية مع اكتشافات.
// =====================================================================

@Composable
fun PendingDiscoveriesPopup(
    rootId: UUID,
    snackbarHostState: SnackbarHostState,
    viewModel: PendingDiscoveriesViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val group = groups.firstOrNull { it.rootId == rootId }
    var nameRequest by remember { mutableStateOf<NameRequest?>(null) }

    // نغلق البوب-أب تلقائيًا فقط إذا كانت المجموعة حاضرة ثم خلت (قرار اتُّخذ)؛
    // أما أول فتح فقد تكون القائمة لم تُحمَّل بعد، فلا يُغلق قبل الوصول إليها.
    var hadGroup by remember(rootId) { mutableStateOf(false) }
    if (group != null) hadGroup = true
    LaunchedEffect(hadGroup, group) {
        if (hadGroup && group == null) DiscoveryNotifier.dismiss()
    }

    val message by viewModel.message.collectAsStateWithLifecycle()
    val feedback = message?.let { (res, arg) ->
        if (arg != null) stringResource(res, arg) else stringResource(res)
    }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val current = group ?: return
    val currentRootId = current.rootId

    AlertDialog(
        onDismissRequest = { DiscoveryNotifier.dismiss() },
        title = { Text(stringResource(R.string.discovery_popup_title, current.count)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Text(
                    stringResource(R.string.pending_discoveries_root, current.rootName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                current.items.take(5).forEach { item ->
                    DiscoveryItemText(item = item)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                DiscoveryDecisionActions(
                    onSeries = { nameRequest = NameRequest(DiscoveryDecision.ASSIGN_SERIES) },
                    onAuthor = { nameRequest = NameRequest(DiscoveryDecision.ASSIGN_AUTHOR) },
                    onCreateBooks = { viewModel.decideGroup(currentRootId, DiscoveryDecision.CREATE_BOOKS) },
                    onMerge = { viewModel.decideGroup(currentRootId, DiscoveryDecision.MERGE_AS_ONE) },
                    onIgnore = { viewModel.decideGroup(currentRootId, DiscoveryDecision.IGNORE_ALL) }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { DiscoveryNotifier.dismiss() }) {
                Text(stringResource(R.string.discovery_popup_later))
            }
        }
    )

    nameRequest?.let { request ->
        val isSeries = request.decision == DiscoveryDecision.ASSIGN_SERIES
        InputDialog(
            title = stringResource(
                if (isSeries) R.string.discovery_decision_assign_series else R.string.discovery_decision_assign_author
            ),
            label = stringResource(
                if (isSeries) R.string.discovery_assign_hint_series else R.string.discovery_assign_hint_author
            ),
            onConfirm = { name ->
                nameRequest = null
                if (name.isNotBlank()) {
                    viewModel.decideGroup(currentRootId, request.decision, targetName = name)
                }
            },
            onDismiss = { nameRequest = null }
        )
    }
}

// =====================================================================
// شاشة الإعدادات: كل الاكتشافات المعلّقة مع قرارات لكل مجموعة.
// =====================================================================

@Composable
fun PendingDiscoveriesScreen(
    onBack: () -> Unit,
    viewModel: PendingDiscoveriesViewModel = hiltViewModel()
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    var nameRequest by remember { mutableStateOf<Pair<UUID, NameRequest>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    val message by viewModel.message.collectAsStateWithLifecycle()
    val feedback = message?.let { (res, arg) ->
        if (arg != null) stringResource(res, arg) else stringResource(res)
    }
    LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = bottomContentInset() + AppSpacing.md)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = AppSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Spacer(Modifier.height(AppSpacing.md))
            CosmicScreenHeader(
                title = stringResource(R.string.pending_discoveries_title),
                collapsed = collapsed,
                onBack = onBack,
                backAsTextButton = true
            )

            if (groups.isEmpty()) {
                Text(
                    stringResource(R.string.pending_discoveries_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Text(
                            stringResource(R.string.pending_discoveries_root, group.rootName),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.pending_discoveries_count, group.count),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        group.items.forEach { item ->
                            DiscoveryItemText(item = item)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        DiscoveryDecisionActions(
                            onSeries = { nameRequest = group.rootId to NameRequest(DiscoveryDecision.ASSIGN_SERIES) },
                            onAuthor = { nameRequest = group.rootId to NameRequest(DiscoveryDecision.ASSIGN_AUTHOR) },
                            onCreateBooks = { viewModel.decideGroup(group.rootId, DiscoveryDecision.CREATE_BOOKS) },
                            onMerge = { viewModel.decideGroup(group.rootId, DiscoveryDecision.MERGE_AS_ONE) },
                            onIgnore = { viewModel.decideGroup(group.rootId, DiscoveryDecision.IGNORE_ALL) }
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.sm))
                }
            }
            Spacer(Modifier.height(bottomContentInset()))
        }
    }

    nameRequest?.let { (rootId, request) ->
        val isSeries = request.decision == DiscoveryDecision.ASSIGN_SERIES
        InputDialog(
            title = stringResource(
                if (isSeries) R.string.discovery_decision_assign_series else R.string.discovery_decision_assign_author
            ),
            label = stringResource(
                if (isSeries) R.string.discovery_assign_hint_series else R.string.discovery_assign_hint_author
            ),
            onConfirm = { name ->
                nameRequest = null
                if (name.isNotBlank()) {
                    viewModel.decideGroup(rootId, request.decision, targetName = name)
                }
            },
            onDismiss = { nameRequest = null }
        )
    }
}

@Composable
private fun DiscoveryItemText(item: PendingDiscoveryUiItem) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(item.detectedTitle, style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.pending_discoveries_folder, item.folderPath),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

/** الشريط المشترك لقرارات الاكتشاف: الخمسة خيارات يطبَّقة على المجموعة كلها. */
@Composable
private fun DiscoveryDecisionActions(
    onSeries: () -> Unit,
    onAuthor: () -> Unit,
    onCreateBooks: () -> Unit,
    onMerge: () -> Unit,
    onIgnore: () -> Unit
) {
    Column {
        DecisionRow(stringResource(R.string.discovery_decision_create_books), onCreateBooks)
        DecisionRow(stringResource(R.string.discovery_decision_merge_one), onMerge)
        DecisionRow(stringResource(R.string.discovery_decision_assign_series), onSeries)
        DecisionRow(stringResource(R.string.discovery_decision_assign_author), onAuthor)
        DecisionRow(stringResource(R.string.discovery_decision_ignore), onIgnore)
    }
}

@Composable
private fun DecisionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xxs),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onClick) {
            Text(label)
        }
    }
}