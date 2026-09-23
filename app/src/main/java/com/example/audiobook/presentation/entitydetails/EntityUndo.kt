package com.example.audiobook.presentation.entitydetails

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import com.example.audiobook.R
import com.example.audiobook.presentation.common.OpMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** نافذة التراجع الثابتة: 5 ثوانٍ بالضبط قبل اعتبار العملية نهائية. */
internal const val ENTITY_UNDO_WINDOW_MS = 5_000L

/**
 * يعرض رسالة العملية مع زر "تراجع" فوق الشاشة (نفس ستايل الزجاج العام في التطبيق):
 * - لو نقر المستخدم "تراجع" قبل انتهاء النافذة → [onUndo] (يستعيد الكيان ويبقى).
 * - لو انتهت الـ5 ثوانٍ دون نقرة → [onTimedOut] (الرجوع إلى القائمة مثل السلوك السابق).
 * لا ينتظر أي وقت حقيقي سوى نافذة الـ5 ثوانٍ المطلوبة (delay محدد لا استطلاع).
 */
@Composable
internal fun EntityUndoEffect(
    message: OpMessage?,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit,
    onTimedOut: () -> Unit,
    onConsumed: () -> Unit
) {
    val messageRes = message?.messageRes ?: return
    val text = stringResource(messageRes)
    val undoLabel = stringResource(R.string.btn_undo)
    LaunchedEffect(message) {
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
        if (done.await() == SnackbarResult.ActionPerformed) onUndo() else onTimedOut()
        onConsumed()
    }
}