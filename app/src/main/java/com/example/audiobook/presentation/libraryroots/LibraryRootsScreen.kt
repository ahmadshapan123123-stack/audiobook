package com.example.audiobook.presentation.libraryroots

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.presentation.theme.AppSpacing
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
                onReGrant = { reGrantPicker.launch(android.net.Uri.parse(root.uri)) },
                onDelete = { viewModel.deleteRoot(root) },
                onRename = { name -> viewModel.renameRoot(root, name) },
                onEdit = { name, priority, enabled -> viewModel.editRoot(root.id, name, priority, enabled) }
            )
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryRootRow(
    root: LibraryRootEntity,
    accessRevoked: Boolean,
    onPriorityChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onReGrant: () -> Unit,
    // STAGE 6B — حذف/إعادة تسمية عبر ضغطة مطوّلة.
    onDelete: () -> Unit,
    onRename: (String) -> Unit,
    // تحرير موحّد: الاسم + الأولوية + التفعيل في حوار واحد.
    onEdit: (String, Boolean, Boolean) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    val appAccent = LocalAppAccent.current
    val switchColors = SwitchDefaults.colors(
        checkedThumbColor = appAccent.onAccent,
        checkedTrackColor = appAccent.accent,
        checkedBorderColor = appAccent.accent,
        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { showMenu = true }
            )
    ) {
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
        // STAGE 6B — قائمة الضغطة المطوّلة: تعديل / إعادة تسمية / حذف.
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_folder_edit)) },
                onClick = { showMenu = false; showEdit = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_folder_rename)) },
                onClick = { showMenu = false; showRename = true }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_folder_delete)) },
                onClick = { showMenu = false; showDeleteConfirm = true }
            )
        }
        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.library_folder_delete)) },
                text = { Text(stringResource(R.string.library_folder_delete_confirm, root.displayName)) }
            )
        }
        if (showRename) {
            var draft by remember(root.id) { mutableStateOf(root.displayName) }
            AlertDialog(
                onDismissRequest = { showRename = false },
                confirmButton = {
                    TextButton(
                        enabled = draft.trim().isNotEmpty(),
                        onClick = { showRename = false; onRename(draft) }
                    ) { Text(stringResource(R.string.library_folder_rename)) }
                },
                dismissButton = {
                    TextButton(onClick = { showRename = false }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.library_folder_rename)) },
                text = {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text(stringResource(R.string.library_folder_rename_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            )
        }
        if (showEdit) {
            var editName by remember(root.id) { mutableStateOf(root.displayName) }
            var editPriority by remember(root.id) { mutableStateOf(root.isPriority) }
            var editEnabled by remember(root.id) { mutableStateOf(root.isEnabled) }
            AlertDialog(
                onDismissRequest = { showEdit = false },
                confirmButton = {
                    TextButton(
                        enabled = editName.trim().isNotEmpty(),
                        onClick = { showEdit = false; onEdit(editName, editPriority, editEnabled) }
                    ) { Text(stringResource(R.string.bd_save)) }
                },
                dismissButton = {
                    TextButton(onClick = { showEdit = false }) { Text(stringResource(R.string.cancel)) }
                },
                title = { Text(stringResource(R.string.library_folder_edit)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            placeholder = { Text(stringResource(R.string.library_folder_rename_hint)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.library_folder_priority))
                            Switch(
                                checked = editPriority,
                                onCheckedChange = { editPriority = it },
                                colors = switchColors,
                                modifier = Modifier.minTouchTarget()
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.library_folder_enabled))
                            Switch(
                                checked = editEnabled,
                                onCheckedChange = { editEnabled = it },
                                colors = switchColors,
                                modifier = Modifier.minTouchTarget()
                            )
                        }
                    }
                }
            )
        }
        HorizontalDivider()
    }
}