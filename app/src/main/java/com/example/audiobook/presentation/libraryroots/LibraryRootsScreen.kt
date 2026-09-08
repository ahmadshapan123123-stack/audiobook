package com.example.audiobook.presentation.libraryroots

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.audiobook.data.localfilesystem.StorageAccess
import com.example.audiobook.data.room.entity.LibraryRootEntity

@Composable
fun LibraryRootsScreen(viewModel: LibraryRootsViewModel) {
    val roots by viewModel.roots.collectAsState()
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::addRoot)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Library folders", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { folderPicker.launch(null) }) {
            Text("Add folder")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(roots, key = { it.id }) { root ->
                LibraryRootRow(
                    root = root,
                    onPriorityChanged = { viewModel.setPriority(root, it) },
                    onEnabledChanged = { viewModel.setEnabled(root, it) },
                    onRefresh = { viewModel.refresh(root) }
                )
            }
        }
    }
}

@Composable
private fun LibraryRootRow(
    root: LibraryRootEntity,
    onPriorityChanged: (Boolean) -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(root.displayName, style = MaterialTheme.typography.titleMedium)
        Text(root.uri, style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                Text("Priority")
                Switch(checked = root.isPriority, onCheckedChange = onPriorityChanged)
            }
            Row {
                Text("Enabled")
                Switch(checked = root.isEnabled, onCheckedChange = onEnabledChanged)
            }
        }
        TextButton(onClick = onRefresh, enabled = root.isEnabled) {
            Text("Refresh")
        }
        HorizontalDivider()
    }
}