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

    private val _rejectedScan = MutableStateFlow(false)
    /** رفعه حين يُرفض فحص ثانٍ لأن واحدًا يعمل — الواجهة تعرضه كرسالة. */
    val rejectedScan: StateFlow<Boolean> = _rejectedScan.asStateFlow()

    @Volatile
    private var cancelRequested = false
    val isCancelRequested: Boolean get() = cancelRequested

    /**
     * PART 11: حارس فحص واحد. كان `begin()` بلا شرط، فكان فحص يدوي + ScanWorker
     * يبدآن معًا: كلاهما ينشر على [state] ويلغي `cancelRequested` للآخر، فتتلاشى
     * حالة الفحص الأول ويصبح الإلغاء غير موثوق.
     *
     * CAS على [scanActive] يجعل `begin()` ذرّية:winner يبدأ، الباقي يُرفض.
     */
    private val scanActive = java.util.concurrent.atomic.AtomicBoolean(false)

    val isScanActive: Boolean get() = scanActive.get()

    /**
     * يفحص ذرّية: @return true إذا كانت هذه هي الجلسة الفريدة، false إن كان هناك
     * فحص جارٍ (يُرفض ولا يُبطل الأول).
     */
    fun tryBegin(): Boolean {
        if (!scanActive.compareAndSet(false, true)) {
            _rejectedScan.value = true
            return false
        }
        cancelRequested = false
        _rejectedScan.value = false
        _active.value = true
        return true
    }

    /** يُستدعى قبل بدء فحص جذر: يصفّر طلب الإلغاء السابق ويفتح شريط التقدم. */
    fun begin() {
        // سلوك قديم محفوظ: أول استدعاء يفتح، اللاحق لا يبطل الجارِي.
        tryBegin()
    }

    fun publish(progress: ScanProgress) {
        _state.value = progress
    }

    fun requestCancel() {
        cancelRequested = true
    }

    /**
     * GAP 2: يصفّر طلب الإلغاء دون التقاط حارس الفحص.
     *
     * يلزم لمرحلة الاستيراد: هي تنشر تقدّمها قبل أن تبدأ `scanRoot` (وهي
     * نفسها التي ستلتقط الحارس بـ`tryBegin` وتصفّر الإلغاء عندها)، فطلب
     * إلغاء باقٍ من فحص سابق كان سيُفسد الدفعة الأولى من الاستيراد فورًا.
     */
    fun resetCancel() {
        cancelRequested = false
    }

    fun clearRejectedScan() {
        _rejectedScan.value = false
    }

    /** يُستدعى عند انتهاء الفحص أو إيقافه أو فشله: يُغلق شريط التقدم. */
    fun finish() {
        scanActive.set(false)
        cancelRequested = false
        _active.value = false
    }
}