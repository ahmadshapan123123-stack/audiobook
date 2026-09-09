package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.UserDecision

/**
 * مستويات الذكاء في Smart Edition Detection.
 * يختارها المستخدم من شاشة Settings وتُقرأ فعلًا في منطق القرار أثناء الفحص.
 *
 *  - CONSERVATIVE: لا دمج تلقائي إطلاقًا؛ كل تجميع يمر عبر تأكيد المستخدم.
 *  - BALANCED (الافتراضي): دمج تلقائي فقط عند ثقة شديدة الارتفاع
 *    (كل الإشارات القوية متطابقة: العنوان + المؤلف + مدة متقاربة جدًا).
 *  - AGGRESSIVE: اقتراحات أوسع تُعرَض في شاشة Review، لكن لا دمج تلقائي صامت أبدًا.
 */
enum class IntelligenceLevel { CONSERVATIVE, BALANCED, AGGRESSIVE }

/**
 * سجل قرار مسبق (يفُكَّ من EditionMatchDecisionEntity ثم يُمرَّر هنا)
 * يُستخدم لتعديل أوزان المطابقة لهذا المستخدم تحديدًا.
 */
data class PatternConfirmation(
    val patternKey: String,
    val userDecision: UserDecision
)

/**
 * خوارزمية Confidence Score ومستويات الذكاء والقيد الصارم.
 * كلها دوال نقية (بيانات ← قيم) قابلة للاختبار بمفردها دون قاعدة بيانات.
 */
object EditionIntelligence {

    /** القيد الصارم المعياري: فرق المدة الجوهري = أكثر من 15%. */
    const val STRICT_DURATION_DELTA = 0.15f

    /** عتبة الدمج التلقائي في Balanced: كل الإشارات القوية متطابقة معًا. */
    const val BALANCED_AUTO_MERGE_THRESHOLD = 0.85f

    /** عتبة عرض الاقتراح في شاشة Review (تستخدم لكل المستويات عدا استبعاد القوي). */
    const val REVIEW_LOW_CONFIDENCE_MAX = 0.60f

    /**
     * ثقة اكتشاف إصدار واحد (ضبط ذاتي، 0..1). أوزان موثّقة:
     *  +0.30 عنوان مضمّن في الـMetadata الداخلية (أقوى إشارة تحديد هوية)
     *  +0.20 راوٍ محدد (من tag أو من نمط نصي في الاسم)
     *  +0.15 اسم مجلد "شبيه بالعنوان" (غير عام، طوله > 1)
     *  +0.10 نمط سلسلة مكتشف (بنية تنظيمية مؤكدة)
     *  +0.10 مدة إجمالية معلومة (> 0)
     *  +0.10 ملفات موجودة
     *  +0.05 مجلد مؤلف معروف
     *  السقف 1.0. لا تُرجع الثابت 1f أبدًا إلا إذا توفرت كل الإشارات الأقوى.
     */
    fun calculateConfidence(signals: EditionSignals): Float {
        var score = 0f
        if (signals.embeddedTags?.title?.isNotBlank() == true) score += 0.30f
        if (!signals.narrator.isNullOrBlank()) score += 0.20f
        val folderStem = signals.folderName.substringAfterLast('/')
        if (folderStem.isNotBlank() && folderStem.length > 1 && !isGenericFolderName(folderStem)) score += 0.15f
        if (signals.seriesPart != null) score += 0.10f
        if (signals.hasKnownDuration()) score += 0.10f
        if (signals.fileCount > 0) score += 0.10f
        if (!signals.authorFolderName.isNullOrBlank()) score += 0.05f
        return score.coerceIn(0f, 1f)
    }

    /**
     * ثقة المطابقة بين إصدارين مكتشفين (0..1). أوزان موثّقة:
     *  +0.30 تطابق الراوي (متساويان بعد التطبيع = 0.30؛ أحد المجهولين فقط = 0.15؛
     *         راويان مختلفان = 0.00)
     *  +0.25 تطابق العنوان الداخلي (متساويان = 0.25؛ أحد المجهولين = 0.10؛
     *         مختلفان = 0.00)
     *  +0.20 تطابق اسم المجلد بعد التطبيع
     *  +0.10 تطابق السلسلة (النمط والرقم)
     *  +0.10 تقارب المدة (فرق ≤ 15%)
     *  +0.05 تطابق الصيغة
     */
    fun mergeConfidence(subject: EditionSignals, candidate: EditionSignals): Float {
        var score = 0f
        score += narratorAgreement(subject.narrator, candidate.narrator)
        score += embeddedTitleAgreement(subject.embeddedTags?.title, candidate.embeddedTags?.title)
        score += if (subject.normalizedTitle().isNotBlank() && subject.normalizedTitle() == candidate.normalizedTitle()) {
            0.20f
        } else if (subject.normalizedTitle().isBlank() || candidate.normalizedTitle().isBlank()) {
            0.05f
        } else {
            0.00f
        }
        score += seriesAgreement(subject.seriesPart, candidate.seriesPart)
        score += durationAgreement(subject.totalDurationMs, candidate.totalDurationMs)
        score += if (subject.format != null && subject.format == candidate.format) 0.05f else 0.00f
        return score.coerceIn(0f, 1f)
    }

    /**
     * [النقطة الأهم] قرار الدمج التلقائي الصامت (بوزن قياسي = 1f).
     *
     * يُطبَّق القيد الصارم أولًا وبشكل مستقل عن level:
     *   - اختلاف راوٍ واضح (مُحدَّدان ومختلفان) → false دائمًا.
     *   - فرق المدة الإجمالية > 15% (حين تكون كلتاهما معلومتين) → false دائمًا.
     *
     * ثم مستوى الذكاء:
     *   - CONSERVATIVE → false دائمًا (لا دمج تلقائي إطلاقًا).
     *   - AGGRESSIVE → false دائمًا أيضًا: السقف الأعلى للوضع العدواني هو
     *     "اقتراح يعرض للمراجعة" وليس دمجًا بلا علم المستخدم.
     *   - BALANCED → دمج تلقائي فقط عند ثقة شديدة الارتفاع.
     */
    fun canAutoMerge(subject: EditionSignals, candidate: EditionSignals, level: IntelligenceLevel): Boolean =
        mergeDecision(subject, candidate, level, boost = 1f)

    /**
     * نفس قرار canAutoMerge مع إمكانية تعديل الوزن بقرارات المستخدم السابقة
     * (confirmationBoost). القيد الصارم يبقى سابقًا وأعلى من أي boost.
     */
    fun mergeDecision(subject: EditionSignals, candidate: EditionSignals, level: IntelligenceLevel, boost: Float): Boolean {
        if (narratorsClearlyDistinct(subject.narrator, candidate.narrator)) return false
        if (durationsDivergeBeyondStrictLimit(subject.totalDurationMs, candidate.totalDurationMs)) return false
        return when (level) {
            IntelligenceLevel.CONSERVATIVE -> false
            IntelligenceLevel.AGGRESSIVE -> false
            IntelligenceLevel.BALANCED -> adjustedConfidence(mergeConfidence(subject, candidate), boost) >= BALANCED_AUTO_MERGE_THRESHOLD
        }
    }

    /** راويان "مختلفان بوضوح": كلاهما محدد وبعد التطبيع غير متساويين. */
    fun narratorsClearlyDistinct(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        return ArabicSearchNormalizer.normalize(a) != ArabicSearchNormalizer.normalize(b)
    }

    /** فرق مدة جوهري (> 15%) حين تكون كلتا المدد معلومة (لا نمنع عند الجهل). */
    fun durationsDivergeBeyondStrictLimit(aMs: Long, bMs: Long): Boolean {
        if (aMs <= 0L || bMs <= 0L) return false
        return durationDelta(aMs, bMs) > STRICT_DURATION_DELTA
    }

    fun durationDelta(aMs: Long, bMs: Long): Float =
        kotlin.math.abs(aMs - bMs).toFloat() / maxOf(aMs, bMs).toFloat()

    /**
     * تعديل وزن النمط من القرارات السابقة: لو أكّد المستخدم سابقًا أن هذا
     * النمط/الزوج = نفس الإصدار، زِد وزن ذلك النمط في القرارات التالية.
     * كل تأكيد +5% حتى سقف +25%. لا يتجاوز التعديل القيد الصارم أبدًا
     * لأنه يُطبَّق خارج canAutoMerge وبعد اجتياز القيد.
     */
    fun confirmationBoost(
        subject: EditionSignals,
        candidate: EditionSignals,
        confirmations: List<PatternConfirmation>
    ): Float {
        val pairKey = pairPatternKey(subject, candidate)
        val sameEditionCount = confirmations.count {
            it.userDecision == UserDecision.SAME_EDITION && it.patternKey == pairKey
        }
        return (1f + 0.05f * sameEditionCount).coerceAtMost(1.25f)
    }

    fun adjustedConfidence(base: Float, boost: Float): Float = (base * boost).coerceIn(0f, 1f)

    /** مفتاح الزوج لتطبيق القرارات السابقة (ترتيب محايد لكل من الإصدارين). */
    fun pairPatternKey(subject: EditionSignals, candidate: EditionSignals): String =
        listOf(subject.patternKey(), candidate.patternKey()).sorted().joinToString("<>")

    private fun narratorAgreement(a: String?, b: String?): Float = when {
        a.isNullOrBlank() || b.isNullOrBlank() -> 0.15f
        ArabicSearchNormalizer.normalize(a) == ArabicSearchNormalizer.normalize(b) -> 0.30f
        else -> 0.00f
    }

    private fun embeddedTitleAgreement(a: String?, b: String?): Float = when {
        a.isNullOrBlank() || b.isNullOrBlank() -> 0.10f
        ArabicSearchNormalizer.normalize(a) == ArabicSearchNormalizer.normalize(b) -> 0.25f
        else -> 0.00f
    }

    private fun seriesAgreement(a: SeriesPart?, b: SeriesPart?): Float = when {
        a == null || b == null -> 0.05f
        a.pattern == b.pattern && a.partNumber == b.partNumber -> 0.10f
        else -> 0.00f
    }

    private fun durationAgreement(aMs: Long, bMs: Long): Float = when {
        aMs <= 0L || bMs <= 0L -> 0.05f
        durationDelta(aMs, bMs) <= STRICT_DURATION_DELTA -> 0.10f
        else -> 0.00f
    }

    private fun isGenericFolderName(name: String): Boolean {
        val normalized = ArabicSearchNormalizer.normalize(name)
        return normalized in GENERIC_NAMES || normalized.length < 2
    }

    private val GENERIC_NAMES = setOf(
        "unknown", "untitled", "audiobook", "audible", "books", "audios",
        "غيرمعروف", "بدونعنوان", "مكتبه", "كتب", "صوتيات"
    )
}