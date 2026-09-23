package com.example.audiobook.background.reclassify

import com.example.audiobook.domain.usecases.ReclassifyPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * نتيجة «إعادة تصنيف المكتبة» من Worker الخلفية إلى الواجهة (Snackbar).
 * كائن ثابت لأن ReclassifyWorker يعمل خارج نطاق أي ViewModel —
 * نفس نمط DiscoveryNotifier. يطبّق ReclassifyWorker القيمة ثم تستهلكها
 * SettingsViewModel لعرض «تم إعادة تصنيف N كتابًا».
 */
object ReclassifyResultNotifier {
    private val _result = MutableStateFlow<ReclassifyPreview?>(null)
    val result: StateFlow<ReclassifyPreview?> = _result.asStateFlow()

    fun notify(preview: ReclassifyPreview) {
        _result.value = preview
    }

    fun reset() {
        _result.value = null
    }
}