package com.example.audiobook.domain.usecases

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FIX 3 — جسر نتيجة المعاينة بين الخدمة الأمامية والاستهلال.
 *
 * المعاينة تعمل في [com.example.audiobook.background.scan.ScanForegroundService]
 * (خارج أي ViewModel scope)، وهذه القناة الوحيدة لعودة الشجرة — نفس نمط
 * `ScanServiceNotifier` لكن بحمولة `PreviewTree` لا حصيلة فحص.
 *
 * قناة واحدة `result` (لا سباق): إما `Ready` أو `Failed` أو `Cancelled`،
 * والأول غير-null يفوز. `clear()` قبل كل إطلاق حتى لا تلتقط شاشةٌ نتيجةً
 * معاينةٍ سابقة.
 */
object PreviewResultBus {
    sealed interface PreviewResult {
        data class Ready(val tree: StrictFolderClassifier.PreviewTree) : PreviewResult
        data class Failed(val reason: String) : PreviewResult
        data object Cancelled : PreviewResult
    }

    private val _result = MutableStateFlow<PreviewResult?>(null)
    val result: StateFlow<PreviewResult?> = _result.asStateFlow()

    fun publishReady(tree: StrictFolderClassifier.PreviewTree) {
        _result.value = PreviewResult.Ready(tree)
    }

    fun publishFailed(reason: String) {
        _result.value = PreviewResult.Failed(reason)
    }

    fun publishCancelled() {
        _result.value = PreviewResult.Cancelled
    }

    fun clear() {
        _result.value = null
    }
}
