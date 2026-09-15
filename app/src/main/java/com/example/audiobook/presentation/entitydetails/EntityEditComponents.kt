package com.example.audiobook.presentation.entitydetails

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.presentation.theme.AppSpacing
import java.io.File
import java.util.UUID

/** حفظ صورة مُختارة من الجهاز داخل مساحة التطبيق وإرجاع مسارها المحلي. */
internal fun copyPickedImageToInternalStorage(context: android.content.Context, uri: Uri, entityId: UUID): String? {
    return runCatching {
        val dir = File(context.filesDir, "entity_images").apply { mkdirs() }
        val ext = context.contentResolver.getType(uri)
            ?.substringAfterLast('/', "")
            ?.takeIf { it.isNotBlank() && it.length <= 10 }
            ?: "jpg"
        val out = File(dir, "${entityId}_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.absolutePath
    }.getOrNull()
}

/** فكّ صورة مخزّنة على القرص؛ null إن لم توجد أو تعذّر فكّها. */
internal fun decodeImageFile(path: String?): ImageBitmap? {
    if (path.isNullOrBlank()) return null
    return runCatching {
        val bytes = java.io.File(path).length().toInt()
        if (bytes <= 0) return null
        BitmapFactory.decodeFile(path, null)?.asImageBitmap()
    }.getOrNull()
}

/**
 * زر "تعديل" يُفتح نافذة تعديل بيانات (مؤلف/سلسلة): الاسم + نبذة وصفية +
 * اختيار صورة. الحفظ يستدعي [onSave] بقيم الحقول الـ(اسم، نبذة، مسار صورة).
 */
@Composable
internal fun EntityEditButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(AppSpacing.sm)) {
        Text(stringResource(R.string.entity_edit_button))
    }
}

/**
 * نافذة تعديل بيانات الكيان (مؤلف/سلسلة) — تسمح بتغيير الاسم وإضافة نبذة
 * واختيار صورة من الجهاز (تُنسخ إلى مساحة التطبيق لعرضها لاحقًا).
 */
@Composable
internal fun EntityEditDialog(
    initialName: String,
    initialDescription: String,
    initialImagePath: String?,
    entityId: UUID,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, imagePath: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    var imagePath by remember { mutableStateOf(initialImagePath) }
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { picked ->
        if (picked != null) {
            copyPickedImageToInternalStorage(context, picked, entityId)?.let { imagePath = it }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), description.trim(), imagePath) }) {
                Text(stringResource(R.string.entity_edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.entity_edit_cancel))
            }
        },
        title = { Text(stringResource(R.string.entity_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.entity_edit_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.entity_edit_description_label)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                val image = decodeImageFile(imagePath)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(AppSpacing.sm))
                            .background(Color(0xFF2A5B58))
                    ) {
                        val bitmap = image
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap,
                                contentDescription = stringResource(R.string.entity_edit_image_preview),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                        }
                    }
                    OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                        Text(stringResource(R.string.entity_edit_image_button))
                    }
                }
            }
        }
    )
}

