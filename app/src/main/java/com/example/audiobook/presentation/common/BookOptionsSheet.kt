package com.example.audiobook.presentation.common

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.cosmicGlassStyle
import com.example.audiobook.presentation.theme.minTouchTarget
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.util.UUID

/** ورقة خيارات الكتاب: تُفتح بالضغط المطوّل على أي بطاقة كتاب في الشاشات السبع. */
@Composable
fun BookOptionsSheet(
    viewModel: BookManagerViewModel,
    haze: HazeState?,
    onOpenBookDetails: (UUID) -> Unit
) {
    val ctx by viewModel.context.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val context = ctx ?: return

    var moveDialog by remember(context.bookId) { mutableStateOf(false) }
    var collectionsDialog by remember(context.bookId) { mutableStateOf(false) }
    var mergeDialog by remember(context.bookId) { mutableStateOf(false) }
    var pickEditionForFile by remember(context.bookId) { mutableStateOf(false) }
    var pickEditionForDefault by remember(context.bookId) { mutableStateOf(false) }
    // FIX-MERGE-UX: حالة منتقي دمج الإصدارات (المحتفَظ بها = الافتراضية أولًا).
    var mergeEditionsPicker by remember(context.bookId) { mutableStateOf(false) }
    var mergeEditionsRetained by remember(context.bookId) { mutableStateOf<UUID?>(null) }
    var confirmedMergeEditions by remember(context.bookId) { mutableStateOf<Pair<UUID, UUID>?>(null) }
    var removeSeriesDialog by remember(context.bookId) { mutableStateOf(false) }
    var deleteDialog by remember(context.bookId) { mutableStateOf(false) }
    var pendingAddEdition by remember(context.bookId) { mutableStateOf<UUID?>(null) }
    var confirmedMerge by remember(context.bookId) { mutableStateOf<Pair<UUID, String>?>(null) }

    val addFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val editionId = pendingAddEdition
        pendingAddEdition = null
        if (uri != null && editionId != null) viewModel.addAudioFile(context, editionId, uri)
    }

    val launchAddFile: () -> Unit = {
        when {
            context.editions.isEmpty() -> viewModel.noEditionMessage()
            context.editions.size == 1 -> {
                pendingAddEdition = context.editions.first().id
                addFileLauncher.launch(arrayOf("audio/*"))
            }
            else -> pickEditionForFile = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { viewModel.closeOptions() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .then(
                    if (haze != null) {
                        Modifier.hazeChild(
                            haze,
                            cosmicGlassStyle(
                                backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                tintAlpha = 0.15f
                            )
                        )
                    } else Modifier
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    .align(Alignment.CenterHorizontally)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = context.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(context.authorName, context.seriesName).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                BookOptionRow(
                    icon = Icons.Outlined.DriveFileMove,
                    title = stringResource(R.string.book_options_move),
                    description = "",
                    onClick = { moveDialog = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                BookOptionRow(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.book_options_edit),
                    description = stringResource(R.string.book_options_edit_desc),
                    onClick = {
                        viewModel.closeOptions()
                        onOpenBookDetails(context.bookId)
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
                BookOptionRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = stringResource(R.string.book_options_collections),
                    description = "",
                    onClick = { collectionsDialog = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                BookOptionRow(
                    icon = Icons.Outlined.UploadFile,
                    title = stringResource(R.string.book_options_add_file),
                    description = stringResource(R.string.book_options_add_file_desc),
                    onClick = launchAddFile,
                    tint = MaterialTheme.colorScheme.primary
                )
                BookOptionRow(
                    icon = Icons.Outlined.MergeType,
                    title = stringResource(R.string.book_options_merge),
                    description = stringResource(R.string.book_options_merge_desc),
                    onClick = { mergeDialog = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                // FIX-MERGE-UX: دمج الإصدارات هنا أيضًا (لا في تبويب الإصدارات فقط).
                if (context.editions.size > 1) {
                    BookOptionRow(
                        icon = Icons.Outlined.MergeType,
                        title = stringResource(R.string.book_options_merge_editions),
                        description = stringResource(R.string.book_options_merge_editions_desc),
                        onClick = {
                            mergeEditionsRetained = context.defaultEditionId
                                ?: context.editions.first().id
                            mergeEditionsPicker = true
                        },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (context.editions.size > 1) {
                    BookOptionRow(
                        icon = Icons.Outlined.Star,
                        title = stringResource(R.string.book_options_default_edition),
                        description = "",
                        onClick = { pickEditionForDefault = true },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (context.seriesId != null) {
                    BookOptionRow(
                        icon = Icons.Outlined.LinkOff,
                        title = stringResource(R.string.book_options_remove_series),
                        description = "",
                        onClick = { removeSeriesDialog = true },
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                BookOptionRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.book_options_delete),
                    description = "",
                    onClick = { deleteDialog = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // ── النوافذ الفرعية ──
    if (moveDialog) {
        MoveBookDialog(
            catalog = catalog,
            onSelectAuthor = { id -> moveDialog = false; viewModel.moveBookToAuthor(context.bookId, id) },
            onCreateAuthor = { name -> moveDialog = false; viewModel.createAuthorAndMove(context.bookId, name) },
            onSelectSeries = { id -> moveDialog = false; viewModel.moveBookToSeries(context.bookId, id) },
            onCreateSeries = { name -> moveDialog = false; viewModel.createSeriesAndMove(context.bookId, name) },
            onSelectCollection = { id -> moveDialog = false; viewModel.addBookToCollections(context.bookId, listOf(id)) },
            onCreateCollection = { name -> moveDialog = false; viewModel.createCollectionAndAdd(context.bookId, name) },
            onDismiss = { moveDialog = false }
        )
    }

    if (collectionsDialog) {
        CollectionMultiSelectDialog(
            catalog = catalog,
            currentBookId = context.bookId,
            onConfirm = { ids -> collectionsDialog = false; viewModel.addBookToCollections(context.bookId, ids) },
            onCreate = { name -> viewModel.createCollectionAndAdd(context.bookId, name) },
            onDismiss = { collectionsDialog = false }
        )
    }

    if (mergeDialog) {
        MergeBookDialog(
            targets = BookManagerViewModel.mergeTargets(catalog, context.bookId),
            onSelect = { target -> mergeDialog = false; confirmedMerge = target },
            onDismiss = { mergeDialog = false }
        )
    }

    val pendingMerge = confirmedMerge
    if (pendingMerge != null) {
        ConfirmMergeDialog(
            title = stringResource(R.string.merge_book_title),
            message = stringResource(R.string.merge_book_confirm, context.title, pendingMerge.second),
            onConfirm = { viewModel.mergeBookInto(context.bookId, pendingMerge.first); confirmedMerge = null },
            onDismiss = { confirmedMerge = null }
        )
    }

    if (pickEditionForFile) {
        EditionPickerDialog(
            title = stringResource(R.string.add_file_launcher),
            editions = context.editions,
            onSelect = { id -> pickEditionForFile = false; pendingAddEdition = id; addFileLauncher.launch(arrayOf("audio/*")) },
            onDismiss = { pickEditionForFile = false }
        )
    }

    if (pickEditionForDefault) {
        EditionPickerDialog(
            title = stringResource(R.string.set_default_edition_title),
            editions = context.editions,
            onSelect = { id -> pickEditionForDefault = false; viewModel.setDefaultEdition(context.bookId, id) },
            onDismiss = { pickEditionForDefault = false }
        )
    }

    // FIX-MERGE-UX: اختيار النسخة المرشحة (تُحذف وتُنقل ملفاتها للمحتفَظ بها) ثم تأكيد.
    if (mergeEditionsPicker) {
        val retainedId = mergeEditionsRetained
        EditionPickerDialog(
            title = stringResource(R.string.merge_editions_pick),
            editions = context.editions.filter { it.id != retainedId },
            onSelect = { id ->
                mergeEditionsPicker = false
                retainedId?.let { confirmedMergeEditions = it to id }
            },
            onDismiss = { mergeEditionsPicker = false }
        )
    }

    val pendingEditionsMerge = confirmedMergeEditions
    if (pendingEditionsMerge != null) {
        val retainedLabel = context.editions.firstOrNull { it.id == pendingEditionsMerge.first }?.label.orEmpty()
        val candidateLabel = context.editions.firstOrNull { it.id == pendingEditionsMerge.second }?.label.orEmpty()
        ConfirmMergeDialog(
            title = stringResource(R.string.merge_editions_confirm_title),
            message = stringResource(R.string.merge_editions_confirm, retainedLabel, candidateLabel),
            onConfirm = {
                viewModel.mergeEditionsInto(pendingEditionsMerge.first, pendingEditionsMerge.second)
                confirmedMergeEditions = null
            },
            onDismiss = { confirmedMergeEditions = null }
        )
    }

    if (removeSeriesDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.remove_from_series_title),
            message = stringResource(R.string.remove_from_series_confirm, context.title),
            confirmText = stringResource(R.string.btn_confirm),
            onConfirm = { removeSeriesDialog = false; viewModel.removeBookFromSeries(context.bookId) },
            onDismiss = { removeSeriesDialog = false }
        )
    }

    if (deleteDialog) {
        ConfirmDeleteDialog(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.delete_book_confirm, context.title),
            onConfirm = { deleteDialog = false; viewModel.deleteBook(context.bookId) },
            onDismiss = { deleteDialog = false }
        )
    }
}

/** صف خيار زجاجي داخل ورقة الكتاب (نفس لغة الطلاءات في التطبيق). */
@Composable
private fun BookOptionRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    tint: Color
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), shape)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (description.isNotBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** اختيار نسخة من قائمة (تُستخدم للإصدار الافتراضي أو لاختيار نسخة إلحاق ملف). */
@Composable
private fun EditionPickerDialog(
    title: String,
    editions: List<com.example.audiobook.data.room.entity.EditionEntity>,
    onSelect: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                editions.forEach { edition ->
                    val label = listOfNotNull(
                        edition.label.ifBlank { null },
                        edition.narratorName
                    ).joinToString(" · ")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(AppSpacing.sm))
                            .minTouchTarget()
                            .clickable { onSelect(edition.id) }
                            .padding(vertical = AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = false,
                            onClick = { onSelect(edition.id) }
                        )
                        Column {
                            Text(label.ifBlank { edition.fileFormat }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                android.text.format.DateUtils.formatElapsedTime(edition.totalDurationMs / 1000L),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

private const val SERIES_NONE_ID = "__none__"

/** تبويبات نافذة "نقل إلى": مؤلف / سلسلة / مجموعات. */
internal enum class MoveBookTab(val labelRes: Int) {
    AUTHOR(R.string.move_book_tab_author),
    SERIES(R.string.move_book_tab_series),
    COLLECTION(R.string.move_book_tab_collections)
}

/** نافذة "نقل إلى" بثلاثة أقسام (مؤلف / سلسلة / مجموعات) مع إنشاء جديد فوري و"بدون سلسلة".
 * تُستخدم من ورقة خيارات الكتاب (كتاب واحد) ومن التحديد المتعدد في المكتبة. */
@Composable
internal fun MoveBookDialog(
    catalog: BookManagerCatalog,
    onSelectAuthor: (UUID) -> Unit,
    onCreateAuthor: (String) -> Unit,
    onSelectSeries: (UUID?) -> Unit,
    onCreateSeries: (String) -> Unit,
    onSelectCollection: (UUID) -> Unit,
    onCreateCollection: (String) -> Unit,
    onDismiss: () -> Unit,
    initialTab: MoveBookTab = MoveBookTab.AUTHOR
) {
    var tab by remember(initialTab) { mutableStateOf(initialTab) }
    var searchText by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    val authorName = { id: UUID -> catalog.authors.firstOrNull { it.id == id }?.name.orEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                TabRow(selectedTabIndex = tab.ordinal) {
                    MoveBookTab.entries.forEach { t ->
                        Tab(
                            selected = tab == t,
                            onClick = { tab = t; showCreate = false },
                            text = { Text(stringResource(t.labelRes)) }
                        )
                    }
                }
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it; showCreate = false },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showCreate) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                stringResource(
                                    when (tab) {
                                        MoveBookTab.AUTHOR -> R.string.move_book_new_author_hint
                                        MoveBookTab.SERIES -> R.string.move_book_new_series_hint
                                        MoveBookTab.COLLECTION -> R.string.move_book_new_collection_hint
                                    }
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val noSeriesLabel = stringResource(R.string.move_book_no_series)
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
                ) {
                    val items = when (tab) {
                        MoveBookTab.AUTHOR -> catalog.authors
                            .filter { it.name.contains(searchText, ignoreCase = true) }
                            .map { MoveToItem(it.id.toString(), it.name, icon = Icons.Outlined.DriveFileMove) }
                        MoveBookTab.SERIES -> buildList {
                            if (searchText.isBlank() || noSeriesLabel.contains(searchText, ignoreCase = true)) {
                                add(MoveToItem(SERIES_NONE_ID, noSeriesLabel, icon = Icons.Outlined.LinkOff))
                            }
                            catalog.series
                                .filter { it.name.contains(searchText, ignoreCase = true) }
                                .forEach { add(MoveToItem(it.id.toString(), it.name, authorName(it.authorId), icon = Icons.Outlined.DriveFileMove)) }
                        }
                        MoveBookTab.COLLECTION -> catalog.collections
                            .filter { it.name.contains(searchText, ignoreCase = true) }
                            .map { MoveToItem(it.id.toString(), it.name, icon = Icons.Outlined.CollectionsBookmark) }
                    }
                    items(items, key = { it.id }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppSpacing.sm))
                                .clickable {
                                    when (tab) {
                                        MoveBookTab.AUTHOR -> onSelectAuthor(UUID.fromString(item.id))
                                        MoveBookTab.SERIES -> onSelectSeries(if (item.id == SERIES_NONE_ID) null else UUID.fromString(item.id))
                                        MoveBookTab.COLLECTION -> onSelectCollection(UUID.fromString(item.id))
                                    }
                                }
                                .padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            item.icon?.let {
                                Icon(it, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                }
                if (!showCreate) {
                    TextButton(onClick = { showCreate = true }) {
                        Text(
                            stringResource(
                                when (tab) {
                                    MoveBookTab.AUTHOR -> R.string.move_book_new_author
                                    MoveBookTab.SERIES -> R.string.move_book_new_series
                                    MoveBookTab.COLLECTION -> R.string.move_book_new_collection
                                }
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (showCreate && newName.isNotBlank()) {
                TextButton(onClick = {
                    val name = newName.trim()
                    when (tab) {
                        MoveBookTab.AUTHOR -> onCreateAuthor(name)
                        MoveBookTab.SERIES -> onCreateSeries(name)
                        MoveBookTab.COLLECTION -> onCreateCollection(name)
                    }
                }) {
                    Text(stringResource(R.string.collection_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/** إضافة كتاب إلى عدة مجموعات مع إمكانية إنشاء مجموعة جديدة فورًا. */
@Composable
private fun CollectionMultiSelectDialog(
    catalog: BookManagerCatalog,
    currentBookId: UUID,
    onConfirm: (List<UUID>) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf<Set<UUID>>(catalog.collections.filter { currentBookId in catalog.memberIds(it.id) }.mapTo(HashSet()) { it.id }) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.book_options_collections)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                LazyColumn(modifier = Modifier.height(260.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    items(catalog.collections, key = { it.id }) { collection ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppSpacing.sm))
                                .clickable {
                                    selected = if (collection.id in selected) selected - collection.id else selected + collection.id
                                }
                                .padding(vertical = AppSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Checkbox(checked = collection.id in selected, onCheckedChange = null)
                            Text(collection.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.move_book_new_collection_hint)) },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { if (newName.isNotBlank()) onCreate(newName.trim()) }) {
                        Text(stringResource(R.string.collection_add))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text(stringResource(R.string.btn_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}

/** اختيار الكتاب الهدف للدمج مع بحث فوري. */
@Composable
private fun MergeBookDialog(
    targets: List<Pair<UUID, String>>,
    onSelect: (Pair<UUID, String>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }
    val filtered = targets.filter { it.second.contains(searchText, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.merge_book_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.merge_book_search_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.height(280.dp), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                    items(filtered, key = { it.first }) { target ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(AppSpacing.sm))
                                .clickable { onSelect(target) }
                                .padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(target.second, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        }
    )
}