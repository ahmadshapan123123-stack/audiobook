package com.example.audiobook.presentation.pendingdiscoveries

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Part 4 — إشارة «هناك اكتشافات معلّقة تستحق البوب-أب»: يُشعلها ScanWorker بعد
 * اكتمال فحص جذر ذي أولوية ووجود اكتشافات لم يُبتَّ فيها، وتلتقطها MainActivity
 * لتعرض البوب-أب. [dismiss] تُستدعى عند إغلاق البوب-أب (بعد اتخاذ قرار أو تجاوز).
 * كائن ثابت لأن Worker يعمل خارج نطاق أي ViewModel؛ القيمة هي rootId موصول بالبوب-أب.
 */
object DiscoveryNotifier {
    private val _pendingRoot = MutableStateFlow<UUID?>(null)
    val pendingRoot: StateFlow<UUID?> = _pendingRoot.asStateFlow()

    fun notify(rootId: UUID) {
        _pendingRoot.value = rootId
    }

    fun dismiss() {
        _pendingRoot.value = null
    }
}