package com.example.audiobook.presentation.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.domain.usecases.ScanPhase
import com.example.audiobook.presentation.common.middleTruncated
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree

/**
 * شاشات الإعداد (المرحلة 5): ترحيب ← اختيار مجلد الكتب ← معاينة التصنيف (قراءة
 * فقط) ← تأكيد/تعديل ← استيراد فعلي. تنتهي بـ [OnboardingState.Done] فيستدعي
 * [onFinish] لنقل ملكية العرض للواجهة الرئيسية.
 */
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickedUri by viewModel.pickedUri.collectAsStateWithLifecycle()
    val previewInFlight by viewModel.previewInFlight.collectAsStateWithLifecycle()

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) viewModel.pickFolder(uri) }

    LaunchedEffect(state) {
        if (state is OnboardingState.Done) onFinish()
    }

    when (val current = state) {
        OnboardingState.Welcome -> WelcomeStep(
            onNext = viewModel::next,
            onSkip = viewModel::skip
        )
        OnboardingState.PickFolder -> PickFolderStep(
            pickedUri = pickedUri,
            isPreviewing = previewInFlight,
            onChooseFolder = { folderLauncher.launch(null) },
            onNext = viewModel::next,
            onBack = viewModel::back
        )
        is OnboardingState.Previewing -> ProgressStep(
            phase = current.phase, processed = current.processed, total = current.total
        )
        is OnboardingState.ShowPreview -> ConfirmStep(
            tree = current.tree,
            onEdit = viewModel::startEdit,
            onImport = viewModel::confirmAndImport,
            onBack = viewModel::back
        )
        is OnboardingState.Editing -> EditClassificationScreen(
            tree = current.tree,
            onDiscard = viewModel::cancelEdit,
            onSave = viewModel::saveAndPreview,
            onApplyEdit = viewModel::applyEdit
        )
        is OnboardingState.Importing -> ImportStep(
            phase = current.phase, processed = current.processed, total = current.total,
            folder = current.folder, file = current.file,
            onCancel = viewModel::cancel
        )
        OnboardingState.Done -> Unit // LaunchedEffect بدأ الفتح
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit, onSkip: () -> Unit) {
    StepContainer {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(40.dp))
        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.onboarding_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_next))
        }
    }
}

@Composable
private fun PickFolderStep(
    pickedUri: Uri?,
    isPreviewing: Boolean,
    onChooseFolder: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    StepContainer {
        Text(
            text = stringResource(R.string.onboarding_pick_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.onboarding_pick_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.FolderOpen,
                contentDescription = null
            )
            Spacer(Modifier.padding(4.dp))
            Text(stringResource(R.string.onboarding_choose_folder))
        }
        Spacer(Modifier.height(16.dp))
        if (pickedUri != null) {
            Text(
                text = stringResource(R.string.onboarding_selected_folder, StorageAccess.displayNameOf(pickedUri)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = stringResource(R.string.onboarding_no_folder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.onboarding_back))
            }
            Button(
                onClick = onNext,
                enabled = pickedUri != null && !isPreviewing,
                modifier = Modifier.weight(1f)
            ) {
                // المؤشّر يظهر في الإطار بين الضغطة وانتقال الحالة إلى Previewing،
                // ويمنع ضغطتين متتاليتين تُطلقان traversing SAF مزدوجًا للمكتبة كلها.
                if (isPreviewing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(stringResource(R.string.onboarding_next))
                }
            }
        }
    }
}

@Composable
private fun ConfirmStep(
    tree: PreviewTree,
    onEdit: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit
) {
    StepContainer(farStart = true) {
        Text(
            text = stringResource(R.string.onboarding_preview_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_preview_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (tree.authors.isEmpty() && tree.unassignedBooks.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_preview_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
            ) {
                PreviewTreeList(tree = tree, initiallyExpanded = true)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(
                R.string.onboarding_confirm_body,
                tree.authors.size,
                tree.authors.sumOf { it.series.size },
                tree.authors.sumOf { it.books.size + it.series.sumOf { s -> s.books.size } } + tree.unassignedBooks.size
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onImport, enabled = tree.authors.isNotEmpty() || tree.unassignedBooks.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_confirm_import))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_edit_manual))
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_back))
        }
    }
}

@Composable
private fun ProgressStep(phase: ScanPhase, processed: Int, total: Int) {
    StepContainer {
        Text(
            text = stringResource(R.string.onboarding_preview_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = phaseText(phase, processed, total),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ImportStep(
    phase: ScanPhase,
    processed: Int,
    total: Int,
    folder: String,
    file: String,
    onCancel: () -> Unit
) {
    StepContainer {
        Text(
            text = stringResource(R.string.onboarding_importing_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = phaseText(phase, processed, total),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        // STAGE 2 — الموضع الجاري: مجلد + ملف (مقلّصان من المنتصف)، ثم شريط X/Y.
        if (folder.isNotBlank() || file.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            if (folder.isNotBlank()) {
                Text(
                    text = folder.middleTruncated(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
            if (file.isNotBlank()) {
                Text(
                    text = file.middleTruncated(32),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { (processed.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.onboarding_cancel_import), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun phaseText(phase: ScanPhase, processed: Int, total: Int): String = when (phase) {
    ScanPhase.DISCOVERING -> stringResource(R.string.scan_phase_discovering)
    // GAP 2: التوجيه قبل الفحص — «N كتاب من M».
    ScanPhase.IMPORTING -> stringResource(R.string.scan_phase_importing, processed, total)
    ScanPhase.PARSING -> stringResource(R.string.scan_phase_parsing, processed, total)
    ScanPhase.CLASSIFYING -> stringResource(R.string.scan_phase_classifying)
    ScanPhase.CREATING -> stringResource(R.string.scan_phase_creating, processed, total)
    ScanPhase.DONE -> stringResource(R.string.scan_phase_classifying)
}

/** تخطيط أزرار/محتوى بسيط عبر الشاشة: `farStart` يجعل المحتوى متجهًا للأعلى (لا منتصف). */
@Composable
private fun StepContainer(
    farStart: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (farStart) Arrangement.Top else Arrangement.Center
    ) {
        content()
    }
}