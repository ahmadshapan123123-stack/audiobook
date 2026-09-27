package com.example.audiobook.presentation.libraryroots

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.LocalAppAccent
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed

@Composable
fun LibraryRootsScreen(viewModel: LibraryRootsViewModel, onBack: () -> Unit = {}) {
    val roots by viewModel.roots.collectAsState()
    val revokedRoots by viewModel.accessRevokedRoots.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addRoot)
    }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = bottomContentInset()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        CosmicScreenHeader(
            title = stringResource(R.string.library_folder_title),
            subtitle = "تحديد ملفاتك الصوتية",
            collapsed = collapsed,
            onBack = onBack
        )
        Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.minTouchTarget()) {
            Text(stringResource(R.string.library_folder_add))
        }
        if (revokedRoots.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.library_folder_access_revoked_banner),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        roots.forEach { root ->
            val reGrantPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let { viewModel.reGrantAccess(root, it) }
            }
            LibraryRootRow(
                root = root,
                accessRevoked = root.id in revokedRoots,
                onPriorityChanged = { viewModel.setPriority(root, it) },
                onEnabledChanged = { viewModel.setEnabled(root, it) },
                onRefresh = { viewModel.refresh(root) },
                onReGrant = { reGrantPicker.launch(android.net.Uri.parse(root.uri)) }
            )
        }
    }
    }
}

@Composable
private fun LibraryRootRow(
    root: LibraryRootEntity,
    accessRevoked: Boolean,
    onPriorityChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onReGrant: () -> Unit
) {
    val appAccent = LocalAppAccent.current
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = appAccent.onAccent,
        checkedTrackColor = appAccent.accent,
        checkedBorderColor = appAccent.accent,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(root.displayName, style = MaterialTheme.typography.titleMedium)
        Text(root.uri, style = MaterialTheme.typography.bodySmall)
        val priorityDesc = stringResource(R.string.library_folder_priority_toggle, root.displayName)
        val enabledDesc = stringResource(R.string.library_folder_enabled_toggle, root.displayName)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Text(stringResource(R.string.library_folder_priority))
                Switch(
                    checked = root.isPriority,
                    onCheckedChange = onPriorityChanged,
                    colors = switchColors,
                    modifier = Modifier
                        .minTouchTarget()
                        .semantics { contentDescription = priorityDesc }
                )
            }
            Row {
                Text(stringResource(R.string.library_folder_enabled))
                Switch(
                    checked = root.isEnabled,
                    onCheckedChange = onEnabledChanged,
                    colors = switchColors,
                    modifier = Modifier
                        .minTouchTarget()
                        .semantics { contentDescription = enabledDesc }
                )
            }
        }
        TextButton(onClick = onRefresh, enabled = root.isEnabled, modifier = Modifier.minTouchTarget()) {
            Text(stringResource(R.string.library_folder_refresh))
        }
        // المرحلة 4: فحص فشل (إذن/استثناء) — زر استئناف صريح يعيد الجدولة
        // (يستأنف من آخر checkpoint إن بقي، وإلا من البداية).
        if (root.scanStatus == ScanStatus.ERROR && root.isEnabled) {
            OutlinedButton(onClick = onRefresh, modifier = Modifier.minTouchTarget()) {
                Text(stringResource(R.string.library_folder_retry))
            }
        }
        if (accessRevoked) {
            Button(onClick = onReGrant, modifier = Modifier.minTouchTarget()) {
                Text(stringResource(R.string.library_folder_access_repair))
            }
        }
        HorizontalDivider()
    }
}