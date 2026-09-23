package com.example.audiobook.presentation.entitydetails

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.MergeType
import androidx.compose.material.icons.outlined.Reorder
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import com.example.audiobook.presentation.common.ConfirmDeleteDialog
import com.example.audiobook.presentation.common.InputDialog
import com.example.audiobook.presentation.common.OpMessage
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.cosmicGlassStyle
import com.example.audiobook.presentation.theme.minTouchTarget
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ورقة خيارات المؤلف: تُفتح بالضغطة المطوّلة على بطاقة/صف مؤلف من أي مكان.
 * تقدّم تعديل البيانات + نقل كتاب لمؤلف آخر + إضافة كتاب + الدمج + الحذف مع التراجع.
 */
@Composable
fun AuthorOptionsSheet(
    viewModel: AuthorOptionsViewModel,
    haze: HazeState?,
    onOpenAuthor: (UUID) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val entity = state?.entity

    if (entity == null && messages == null) return

    EntitySheetUndoHost(messages, snackbarHostState, viewModel::undo, viewModel::consumeMessage)

    var showEdit by remember(entity?.id) { mutableStateOf(false) }
    var showAddBook by remember(entity?.id) { mutableStateOf(false) }
    var showMerge by remember(entity?.id) { mutableStateOf(false) }
    var showDelete by remember(entity?.id) { mutableStateOf(false) }
    var pickMoveBook by remember(entity?.id) { mutableStateOf(false) }
    var moveBookRow by remember(entity?.id) { mutableStateOf<EntityBookRow?>(null) }

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
        if (entity != null) {
            EntityOptionsPanel(
                title = entity.name,
                subtitle = pluralStringResource(R.plurals.book_count, state?.books?.size ?: 0, state?.books?.size ?: 0),
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                EntityOptionRow(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.entity_options_edit),
                    onClick = { showEdit = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.entity_options_view_books),
                    onClick = {
                        viewModel.closeOptions()
                        onOpenAuthor(entity.id)
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.DriveFileMove,
                    title = stringResource(R.string.author_options_move_book),
                    onClick = { pickMoveBook = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = stringResource(R.string.entity_options_add_book),
                    onClick = { showAddBook = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.MergeType,
                    title = stringResource(R.string.author_menu_merge),
                    onClick = { showMerge = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.author_menu_delete),
                    onClick = { showDelete = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.xxl)
                .padding(horizontal = AppSpacing.md)
        )
    }

    if (entity != null) {
        if (showEdit) {
            EntityEditDialog(
                initialName = entity.name,
                initialDescription = entity.description.orEmpty(),
                initialImagePath = entity.imagePath,
                entityId = entity.id,
                onDismiss = { showEdit = false },
                onSave = { name, description, imagePath ->
                    viewModel.saveEntity(name, description, imagePath)
                    showEdit = false
                }
            )
        }
        if (showAddBook) {
            PickBookDialog(
                candidates = state?.candidateBooks.orEmpty(),
                titleRes = R.string.author_add_book_title,
                emptyMessage = stringResource(R.string.author_add_book_none),
                onSelect = { bookId ->
                    showAddBook = false
                    viewModel.addBookToAuthor(bookId)
                },
                onDismiss = { showAddBook = false }
            )
        }
        if (pickMoveBook) {
            EntityListPickerDialog(
                title = stringResource(R.string.author_options_pick_book),
                rows = state?.books.orEmpty().map { it.bookId to it.title },
                onSelect = { bookId ->
                    pickMoveBook = false
                    moveBookRow = state?.books?.firstOrNull { it.bookId == bookId }
                },
                onDismiss = { pickMoveBook = false }
            )
        }
        moveBookRow?.let { picked ->
            EntityListPickerDialog(
                title = stringResource(R.string.author_options_pick_author),
                rows = state?.otherAuthors.orEmpty().map { it.id to it.name },
                onSelect = { targetId ->
                    moveBookRow = null
                    viewModel.moveBookToOtherAuthor(picked.bookId, targetId)
                },
                onDismiss = { moveBookRow = null }
            )
        }
        if (showMerge) {
            EntityListPickerDialog(
                title = stringResource(R.string.author_merge_select),
                rows = state?.otherAuthors.orEmpty().map { it.id to it.name },
                onSelect = { targetId ->
                    showMerge = false
                    viewModel.mergeAuthors(targetId)
                },
                onDismiss = { showMerge = false }
            )
        }
        if (showDelete) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete_title),
                message = stringResource(R.string.confirm_delete_author, entity.name, state?.books?.size ?: 0),
                onConfirm = {
                    showDelete = false
                    viewModel.deleteAuthor()
                },
                onDismiss = { showDelete = false }
            )
        }
    }
}

/**
 * ورقة خيارات السلسلة: تعديل البيانات + عرض الكتب + إضافة كتاب + إعادة ترتيب +
 * دمج مع سلسلة أخرى + حذف مع التراجع.
 */
@Composable
fun SeriesOptionsSheet(
    viewModel: SeriesOptionsViewModel,
    haze: HazeState?,
    onOpenSeries: (UUID) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val entity = state?.entity

    if (entity == null && messages == null) return

    EntitySheetUndoHost(messages, snackbarHostState, viewModel::undo, viewModel::consumeMessage)

    var showEdit by remember(entity?.id) { mutableStateOf(false) }
    var showAddBook by remember(entity?.id) { mutableStateOf(false) }
    var showReorder by remember(entity?.id) { mutableStateOf(false) }
    var showMerge by remember(entity?.id) { mutableStateOf(false) }
    var showDelete by remember(entity?.id) { mutableStateOf(false) }

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
        if (entity != null) {
            val subtitle = listOfNotNull(
                state?.authorName?.takeIf { it.isNotBlank() },
                pluralStringResource(R.plurals.book_count, state?.books?.size ?: 0, state?.books?.size ?: 0)
            ).joinToString(" · ")
            EntityOptionsPanel(
                title = entity.name,
                subtitle = subtitle,
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                EntityOptionRow(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.entity_options_edit),
                    onClick = { showEdit = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.MenuBook,
                    title = stringResource(R.string.entity_options_view_books),
                    onClick = {
                        viewModel.closeOptions()
                        onOpenSeries(entity.id)
                    },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = stringResource(R.string.entity_options_add_book),
                    onClick = { showAddBook = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.Reorder,
                    title = stringResource(R.string.series_menu_reorder),
                    onClick = { showReorder = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.MergeType,
                    title = stringResource(R.string.series_menu_merge),
                    onClick = { showMerge = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.series_menu_delete),
                    onClick = { showDelete = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.xxl)
                .padding(horizontal = AppSpacing.md)
        )
    }

    if (entity != null) {
        if (showEdit) {
            EntityEditDialog(
                initialName = entity.name,
                initialDescription = entity.description.orEmpty(),
                initialImagePath = entity.imagePath,
                entityId = entity.id,
                onDismiss = { showEdit = false },
                onSave = { name, description, imagePath ->
                    viewModel.saveEntity(name, description, imagePath)
                    showEdit = false
                }
            )
        }
        if (showAddBook) {
            PickBookDialog(
                candidates = state?.candidateBooks.orEmpty(),
                titleRes = R.string.series_add_book_title,
                emptyMessage = stringResource(R.string.series_add_book_none),
                onSelect = { bookId ->
                    showAddBook = false
                    viewModel.addBookToSeries(bookId)
                },
                onDismiss = { showAddBook = false }
            )
        }
        if (showReorder) {
            ReorderBooksDialog(
                order = state?.books.orEmpty(),
                onDone = { orderedIds ->
                    showReorder = false
                    viewModel.applyBookOrder(orderedIds)
                },
                onDismiss = { showReorder = false }
            )
        }
        if (showMerge) {
            EntityListPickerDialog(
                title = stringResource(R.string.series_merge_select),
                rows = state?.allSeries.orEmpty().map { it.id to it.name },
                onSelect = { targetId ->
                    showMerge = false
                    viewModel.mergeSeries(targetId)
                },
                onDismiss = { showMerge = false }
            )
        }
        if (showDelete) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete_title),
                message = stringResource(R.string.confirm_delete_series, entity.name, state?.books?.size ?: 0),
                onConfirm = {
                    showDelete = false
                    viewModel.deleteSeries()
                },
                onDismiss = { showDelete = false }
            )
        }
    }
}

/**
 * ورقة خيارات المجموعة: تعديل الاسم + إضافة كتاب + إزالة كتاب + حذف مع التراجع.
 */
@Composable
fun CollectionOptionsSheet(
    viewModel: CollectionOptionsViewModel,
    haze: HazeState?,
    onOpenCollection: (UUID) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val entity = state?.entity

    if (entity == null && messages == null) return

    EntitySheetUndoHost(messages, snackbarHostState, viewModel::undo, viewModel::consumeMessage)

    var showEdit by remember(entity?.id) { mutableStateOf(false) }
    var showAddBook by remember(entity?.id) { mutableStateOf(false) }
    var showRemove by remember(entity?.id) { mutableStateOf(false) }
    var showDelete by remember(entity?.id) { mutableStateOf(false) }

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
        if (entity != null) {
            EntityOptionsPanel(
                title = entity.name,
                subtitle = pluralStringResource(R.plurals.book_count, state?.members?.size ?: 0, state?.members?.size ?: 0),
                haze = haze,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                EntityOptionRow(
                    icon = Icons.Outlined.Edit,
                    title = stringResource(R.string.entity_options_edit),
                    onClick = { showEdit = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.CollectionsBookmark,
                    title = stringResource(R.string.entity_options_add_book),
                    onClick = { showAddBook = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.Remove,
                    title = stringResource(R.string.collection_menu_remove_book),
                    onClick = { showRemove = true },
                    tint = MaterialTheme.colorScheme.primary
                )
                EntityOptionRow(
                    icon = Icons.Outlined.Delete,
                    title = stringResource(R.string.collection_menu_delete),
                    onClick = { showDelete = true },
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = AppSpacing.xxl)
                .padding(horizontal = AppSpacing.md)
        )
    }

    if (entity != null) {
        if (showEdit) {
            InputDialog(
                title = stringResource(R.string.collection_menu_edit),
                label = stringResource(R.string.collection_edit_name_label),
                initialValue = entity.name,
                onConfirm = { newName ->
                    showEdit = false
                    viewModel.updateName(newName)
                },
                onDismiss = { showEdit = false }
            )
        }
        if (showAddBook) {
            PickBookDialog(
                candidates = state?.candidateBooks.orEmpty(),
                titleRes = R.string.collection_options_add_title,
                emptyMessage = stringResource(R.string.collection_options_add_none),
                onSelect = { bookId ->
                    showAddBook = false
                    viewModel.addBookToCollection(bookId)
                },
                onDismiss = { showAddBook = false }
            )
        }
        if (showRemove) {
            CollectionRemoveDialog(
                members = state?.members.orEmpty(),
                onRemove = { bookId ->
                    showRemove = false
                    viewModel.removeBookFromCollection(bookId)
                },
                onDismiss = { showRemove = false }
            )
        }
        if (showDelete) {
            ConfirmDeleteDialog(
                title = stringResource(R.string.confirm_delete_title),
                message = stringResource(R.string.confirm_delete_collection, entity.name, state?.members?.size ?: 0),
                onConfirm = {
                    showDelete = false
                    viewModel.deleteCollection()
                },
                onDismiss = { showDelete = false }
            )
        }
    }
}

/** لوحة زجاجية سفلية موحّدة لخيارات الكيانات (نفس لغة ورقة خيارات الكتاب). */
@Composable
private fun EntityOptionsPanel(
    title: String,
    subtitle: String,
    haze: HazeState?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
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
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            content()
        }
    }
}

/** صف خيار زجاجي داخل ورقة الكيان. */
@Composable
private fun EntityOptionRow(
    icon: ImageVector,
    title: String,
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
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * يوزّع رسائل عمليات ورقة الكيان على السناكبار مع زر "تراجع" لنافذة 5 ثوانٍ
 * (نفس نافذة التراجع الموحّدة ENTITY_UNDO_WINDOW_MS) للعمليات القابلة للعكس.
 */
@Composable
private fun EntitySheetUndoHost(
    message: OpMessage?,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    onConsumed: () -> Unit
) {
    val text = message?.let { stringResource(it.messageRes) } ?: return
    val undoLabel = stringResource(R.string.btn_undo)
    LaunchedEffect(message) {
        if (!message.undolable) {
            snackbarHostState.showSnackbar(message = text)
            onConsumed()
        } else {
            val done = CompletableDeferred<SnackbarResult>()
            launch {
                done.complete(
                    snackbarHostState.showSnackbar(
                        message = text,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Indefinite
                    )
                )
            }
            launch {
                delay(ENTITY_UNDO_WINDOW_MS)
                if (!done.isCompleted) snackbarHostState.currentSnackbarData?.dismiss()
            }
            if (done.await() == SnackbarResult.ActionPerformed) onUndo()
            onConsumed()
        }
    }
}

/** قائمة اختيار كيان ببحث عربي محسّن: (id → label). */
@Composable
private fun EntityListPickerDialog(
    title: String,
    rows: List<Pair<UUID, String>>,
    onSelect: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = ArabicSearchNormalizer.normalize(query)
    val filtered = if (normalizedQuery.isBlank()) {
        rows
    } else {
        rows.filter { ArabicSearchNormalizer.normalize(it.second).contains(normalizedQuery) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppSpacing.sm))
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.no_results_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        filtered.forEach { (id, label) ->
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .minTouchTarget()
                                    .clickable { onSelect(id) }
                                    .padding(vertical = AppSpacing.sm)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

/** نافذة "إزالة من المجموعة": قائمة بأعضاء المجموعة مع زر إزالة لكل منها. */
@Composable
private fun CollectionRemoveDialog(
    members: List<EntityBookRow>,
    onRemove: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = ArabicSearchNormalizer.normalize(query)
    val filtered = if (normalizedQuery.isBlank()) {
        members
    } else {
        members.filter {
            ArabicSearchNormalizer.normalize(it.title).contains(normalizedQuery) ||
                ArabicSearchNormalizer.normalize(it.authorName).contains(normalizedQuery)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collection_menu_remove_book)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppSpacing.sm))
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.no_results_title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        filtered.forEach { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(AppSpacing.sm))
                                    .clickable { onRemove(row.bookId) }
                                    .padding(vertical = AppSpacing.xs),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                Text(
                                    row.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onRemove(row.bookId) }, modifier = Modifier.minTouchTarget()) {
                                    Icon(
                                        Icons.Outlined.Remove,
                                        contentDescription = stringResource(R.string.collection_menu_remove_book),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}