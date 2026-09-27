package com.example.audiobook.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CallSplit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Merge
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewAuthor
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewSeries
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree

/** نافذة تعديل التصنيف اليدوي (المرحلة 5): إعادة تسمية/نقل/تخطي لكل عقدة،
 *  ودمج/تقسيم المؤلفين. كل فعل يُعرض فورًا في الشجرة عبر [onApplyEdit]. */
@Composable
fun EditClassificationScreen(
    tree: PreviewTree,
    onDiscard: () -> Unit,
    onSave: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    var authorMenu by remember { mutableStateOf<PreviewAuthor?>(null) }
    var seriesMenu by remember { mutableStateOf<PreviewSeries?>(null) }
    var bookMenu by remember { mutableStateOf<PreviewBook?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.edit_screen_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            PreviewTreeList(
                tree = tree,
                initiallyExpanded = true,
                onAuthorClick = { authorMenu = it },
                onSeriesClick = { _, series -> seriesMenu = series },
                onBookClick = { bookMenu = it }
            )
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onDiscard, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.edit_discard))
            }
            Button(onClick = onSave, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.edit_save))
            }
        }
    }

    authorMenu?.let { author ->
        AuthorMenuDialog(
            author = author,
            tree = tree,
            onDismiss = { authorMenu = null },
            onApplyEdit = onApplyEdit
        )
    }
    seriesMenu?.let { series ->
        NameEditDialog(
            title = stringResource(R.string.edit_rename_series),
            initial = series.name,
            onDismiss = { seriesMenu = null },
            onConfirm = { newName ->
                if (newName.isNotBlank() && newName != series.name) {
                    val authorName = tree.authors.firstOrNull { it.series.any { s -> s.name == series.name } }?.name ?: return@NameEditDialog
                    onApplyEdit(ClassificationEdit.RenameSeries(authorName, series.name, newName.trim()))
                }
                seriesMenu = null
            }
        )
    }
    bookMenu?.let { book -> BookMenuDialog(book, tree, onDismiss = { bookMenu = null }, onApplyEdit) }
}

@Composable
private fun AuthorMenuDialog(
    author: PreviewAuthor,
    tree: PreviewTree,
    onDismiss: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    var mode by remember { mutableStateOf<AuthorMenuMode>(AuthorMenuMode.ROOT) }
    val others = tree.authors.filter { it.name != author.name }
    when (mode) {
        AuthorMenuMode.ROOT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(author.name) },
            text = {
                Column {
                    OptionRow(Icons.Outlined.Edit, stringResource(R.string.edit_rename_author)) {
                        mode = AuthorMenuMode.RENAME
                    }
                    OptionRow(Icons.Outlined.Merge, stringResource(R.string.edit_merge_authors)) { mode = AuthorMenuMode.MERGE }
                    OptionRow(Icons.Outlined.CallSplit, stringResource(R.string.edit_split_author)) { mode = AuthorMenuMode.SPLIT }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
        )
        AuthorMenuMode.RENAME -> NameEditDialog(
            title = stringResource(R.string.edit_rename_author),
            initial = author.name,
            onDismiss = { mode = AuthorMenuMode.ROOT },
            onConfirm = { newName ->
                if (newName.isNotBlank() && newName != author.name) {
                    onApplyEdit(ClassificationEdit.RenameAuthor(author.name, newName.trim()))
                }
                onDismiss()
            }
        )
        AuthorMenuMode.MERGE -> MergeAuthorDialog(
            author = author,
            others = others,
            onDismiss = { mode = AuthorMenuMode.ROOT },
            onMerge = { target ->
                onApplyEdit(ClassificationEdit.RenameAuthor(author.name, target.name))
                onDismiss()
            }
        )
        AuthorMenuMode.SPLIT -> SplitAuthorDialog(
            author = author,
            onDismiss = { mode = AuthorMenuMode.ROOT },
            onApplyEdit = onApplyEdit
        )
    }
}

private enum class AuthorMenuMode { ROOT, RENAME, MERGE, SPLIT }

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 12.dp), color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun NameEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                placeholder = { Text(stringResource(R.string.edit_new_name_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

@Composable
private fun MergeAuthorDialog(
    author: PreviewAuthor,
    others: List<PreviewAuthor>,
    onDismiss: () -> Unit,
    onMerge: (PreviewAuthor) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_merge_dialog_title, author.name)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.edit_merge_confirm, author.name, "…"),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                others.forEach { target ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onMerge(target) }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(target.name, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun SplitAuthorDialog(
    author: PreviewAuthor,
    onDismiss: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    val allBooks = remember(author) { author.books + author.series.flatMap { it.books } }
    var checked by remember { mutableStateOf(setOf<String>()) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_split_dialog_title, author.name)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                allBooks.forEach { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            checked = if (book.folderPath in checked) checked - book.folderPath else checked + book.folderPath
                        }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = book.folderPath in checked, onCheckedChange = null)
                        Text(book.title, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(stringResource(R.string.edit_split_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val name = newName.trim()
                    if (name.isNotBlank()) {
                        checked.forEach { folderPath ->
                            onApplyEdit(ClassificationEdit.MoveBookAuthor(folderPath, name))
                        }
                    }
                    onDismiss()
                }
            ) { Text(stringResource(R.string.edit_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun BookMenuDialog(
    book: PreviewBook,
    tree: PreviewTree,
    onDismiss: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    var mode by remember { mutableStateOf<BookMenuMode>(BookMenuMode.ROOT) }
    when (mode) {
        BookMenuMode.ROOT -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(book.title, fontWeight = FontWeight.Medium) },
            text = {
                Column {
                    OptionRow(Icons.Outlined.Edit, stringResource(R.string.edit_rename_book)) { mode = BookMenuMode.RENAME }
                    OptionRow(Icons.Outlined.SwapHoriz, stringResource(R.string.edit_move_author)) { mode = BookMenuMode.MOVE_AUTHOR }
                    OptionRow(Icons.Outlined.SwapHoriz, stringResource(R.string.edit_move_series)) { mode = BookMenuMode.MOVE_SERIES }
                    OptionRow(Icons.Outlined.Delete, stringResource(R.string.edit_skip_folder)) {
                        onApplyEdit(ClassificationEdit.SkipFolder(book.folderPath))
                        onDismiss()
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
        )
        BookMenuMode.RENAME -> NameEditDialog(
            title = stringResource(R.string.edit_rename_book),
            initial = book.title,
            onDismiss = { mode = BookMenuMode.ROOT },
            onConfirm = { newName ->
                if (newName.isNotBlank() && newName != book.title) {
                    onApplyEdit(ClassificationEdit.RenameBook(book.folderPath, newName.trim()))
                }
                onDismiss()
            }
        )
        BookMenuMode.MOVE_AUTHOR -> MoveAuthorDialog(
            book = book,
            tree = tree,
            onDismiss = { mode = BookMenuMode.ROOT },
            onApplyEdit = onApplyEdit
        )
        BookMenuMode.MOVE_SERIES -> MoveSeriesDialog(
            book = book,
            tree = tree,
            onDismiss = { mode = BookMenuMode.ROOT },
            onApplyEdit = onApplyEdit
        )
    }
}

private enum class BookMenuMode { ROOT, RENAME, MOVE_AUTHOR, MOVE_SERIES }

@Composable
private fun MoveAuthorDialog(
    book: PreviewBook,
    tree: PreviewTree,
    onDismiss: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameEditDialog(
            title = stringResource(R.string.edit_move_author),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                if (name.isNotBlank()) onApplyEdit(ClassificationEdit.MoveBookAuthor(book.folderPath, name.trim()))
                onDismiss()
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column {
                tree.authors.forEach { author ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onApplyEdit(ClassificationEdit.MoveBookAuthor(book.folderPath, author.name))
                            onDismiss()
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(author.name, modifier = Modifier.weight(1f))
                    }
                }
                OptionRow(Icons.Outlined.Add, stringResource(R.string.move_book_new_author)) { creating = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun MoveSeriesDialog(
    book: PreviewBook,
    tree: PreviewTree,
    onDismiss: () -> Unit,
    onApplyEdit: (ClassificationEdit) -> Unit
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        NameEditDialog(
            title = stringResource(R.string.edit_move_series),
            initial = "",
            onDismiss = { creating = false },
            onConfirm = { name ->
                if (name.isNotBlank()) onApplyEdit(ClassificationEdit.MoveBookSeries(book.folderPath, name.trim()))
                onDismiss()
            }
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(book.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // بدون سلسلة — إزالة من السلسلة (تبقى بنفس المؤلف).
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onApplyEdit(ClassificationEdit.MoveBookSeries(book.folderPath, null))
                        onDismiss()
                    }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.move_book_no_series), modifier = Modifier.weight(1f))
                }
                tree.authors.forEach { author ->
                    author.series.forEach { series ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onApplyEdit(ClassificationEdit.MoveBookAuthor(book.folderPath, author.name))
                                onApplyEdit(ClassificationEdit.MoveBookSeries(book.folderPath, series.name))
                                onDismiss()
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.move_book_series_of_author, author.name) + " — " + series.name,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                OptionRow(Icons.Outlined.Add, stringResource(R.string.move_book_new_series)) { creating = true }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

/** لا تنفَّذ — تُستخدم لنقل نوع من تصنيفات الدمج المبسطة إن طُلب (مرجعية). */
@Suppress("unused")
private fun PreviewTree.countSeries(): Int = authors.sumOf { it.series.size }