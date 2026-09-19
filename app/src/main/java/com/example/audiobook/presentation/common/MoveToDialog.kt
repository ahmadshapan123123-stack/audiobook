package com.example.audiobook.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing

data class MoveToItem(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val icon: ImageVector? = null
)

@Composable
fun MoveToDialog(
    title: String,
    items: List<MoveToItem>,
    allowCreateNew: Boolean = false,
    newHint: String = "",
    onSelect: (String) -> Unit,
    onCreateNew: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(false) }
    var newValue by remember { mutableStateOf("") }

    val filtered = items.filter {
        it.name.contains(searchText, ignoreCase = true) ||
            it.subtitle.contains(searchText, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showCreateField && allowCreateNew) {
                    OutlinedTextField(
                        value = newValue,
                        onValueChange = { newValue = it },
                        singleLine = true,
                        placeholder = { Text(newHint) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppSpacing.sm))
                                .clickable { onSelect(item.id) }
                                .padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (item.icon != null) {
                                Icon(
                                    item.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(AppSpacing.sm))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (item.subtitle.isNotBlank()) {
                                    Text(
                                        item.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_results_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(AppSpacing.md)
                            )
                        }
                    }
                }
                if (allowCreateNew && !showCreateField) {
                    TextButton(onClick = { showCreateField = true }) {
                        Text(stringResource(R.string.move_to_new_author))
                    }
                }
            }
        },
        confirmButton = {
            if (showCreateField && allowCreateNew && newValue.isNotBlank()) {
                TextButton(onClick = { onCreateNew?.invoke(newValue.trim()) }) {
                    Text(stringResource(R.string.collection_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
