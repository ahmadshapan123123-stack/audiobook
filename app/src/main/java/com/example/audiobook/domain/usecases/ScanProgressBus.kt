package com.example.audiobook.domain.usecases

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ناقل تقدم الفحص الوحيد للواجهة (المرحلة 4). كل ScanRoot ينشر تقدمه هنا مهما
 * كان المتصل (Worker خلفي أو فحص فوري من الإعدادات)، فتشترك شاشات الواجهة في
 * الحالة وتعرض شريط التقدم، وزر الإلغاء «التعاوني» (تُفحَّص قيمة isCancelRequested
 * في نقط خطرية من الفحص ليتوقف الفحص بصمت ويُبقى checkpoint لاستئنافه).
 */
object ScanProgressBus {
    private val _state = MutableStateFlow<ScanProgress?>(null)
    val state: StateFlow<ScanProgress?> = _state.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    @Volatile
    private var cancelRequested = false
    val isCancelRequested: Boolean get() = cancelRequested

    /** يُستدعى قبل بدء فحص جذر: يصفّر طلب الإلغاء السابق ويفتح شريط التقدم. */
    fun begin() {
        cancelRequested = false
        _active.value = true
    }

    fun publish(progress: ScanProgress) {
        _state.value = progress
    }

    fun requestCancel() {
        cancelRequested = true
    }

    /** يُستدعى عند انتهاء الفحص أو إيقافه أو فشله: يُغلق شريط التقدم. */
    fun finish() {
        _active.value = false
    }
}