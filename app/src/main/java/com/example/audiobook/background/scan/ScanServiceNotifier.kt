package com.example.audiobook.background.scan

import com.example.audiobook.domain.usecases.RebuildStructureResult
import com.example.audiobook.domain.usecases.ScanNowResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** أنواع الفحص التي تُنفَّذ داخل [ScanForegroundService]. */
enum class ScanJob {
    /** «فحص المكتبة الآن» — كل الجذور الممكّنة. */
    FULL_SCAN,

    /** إعادة بناء البنية: تنظيف القشور ثم فحص كامل. */
    REBUILD,

    /** فحص جذر واحد (ScanWorker). */
    SINGLE_ROOT,

    /**
     * استيراد الاستهلال — يموّه شجرة المعاينة المعتمدة فيكتب كتبها
     * وأصداراتها، ثم يفحص الجذر. كان ذلك يتم في ViewModel قبل تشغيل
     * الخدمة، أي بلا تغطية foreground على أثقل جزء من الاستيراد.
     * الدفعة المعلّقة نفسها في [PendingOnboardingImport]؛ الجذر في [ScanRequest.rootId].
     */
    ONBOARDING_IMPORT
}

/** طلب تنفيذ دفعة فحوص واحدة محمية بخدمة أمامية واحدة. */
data class ScanRequest(
    val job: ScanJob,
    /** مطلوب لـ [ScanJob.SINGLE_ROOT] فقط. */
    val rootId: String? = null
)

/** حصيلة [ScanForegroundService] كما تنتقل للواجهة. */
sealed interface ScanOutcome {
    /**
     * فحص واحد اكتمل. [outcome] هي الحصيلة المعروضة، و[rebuildResult] غير null
     * فقط لـ [ScanJob.REBUILD] فيحمل عدّادَي التنظيف (قشور/أيتام) الذي تعرضه
     * رسالة إعادة البناء — بلاه كان يتحوّل إلى صفر فتُظهر الرسالة رقمًا كاذبًا.
     */
    data class Completed(
        val outcome: ScanNowResult,
        val rebuildResult: RebuildStructureResult? = null
    ) : ScanOutcome

    /** فُشل الفحص برسالة. */
    data class Failed(val reason: String) : ScanOutcome

    /**
     * رُفض لأن فحصًا آخر يعمل — ليس خطأ. [com.example.audiobook.domain.usecases.ScanProgressBus]
     * هو من رفض، فلا حالة تُمسّ.
     */
    data object Rejected : ScanOutcome

    /** أُلغي من إشعار الخدمة أو من الواجهة. */
    data object Cancelled : ScanOutcome
}

/**
 * جسر بين الخدمة (خارج أي ViewModel scope) والواجهة — نفس نمط
 * [com.example.audiobook.background.reclassify.ReclassifyResultNotifier]
 * و[com.example.audiobook.presentation.pendingdiscoveries.DiscoveryNotifier].
 *
 * كائن ثابت: الخدمة تعمل بعد تدمير الـViewModel، فلا تصلح حالة فINSTANCE
 * يحملها معيد إنشاء الـViewModel.
 */
object ScanServiceNotifier {
    private val _result = MutableStateFlow<ScanOutcome?>(null)
    val result: StateFlow<ScanOutcome?> = _result.asStateFlow()

    fun notify(outcome: ScanOutcome) {
        _result.value = outcome
    }

    fun reset() {
        _result.value = null
    }
}
