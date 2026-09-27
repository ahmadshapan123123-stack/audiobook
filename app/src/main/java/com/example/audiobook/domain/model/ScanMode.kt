package com.example.audiobook.domain.model

/**
 * وضع الفحص — يوازن بين دقة البيانات واستهلاك الموارد على الأجهزة الضعيفة.
 *
 * [NORMAL] يقرأ بيانات الوسائط لكل ملف (سلوك الفحص الكامل السابق).
 * [ECONOMY] يتخطّى الملفات الأكبر من 100 م.ب لأن قراءتها مكلفة بالذاكرة
 * الأصلية والطابع الزمني، وكتب الصوت الضخمة غالبًا صوت متصل بلا فصول مضمّنة.
 * [FAST] يتخطّى قراءة بيانات الوسائط بالكامل: أسرع فحص ممكن، تبقى هوية
 * الكتب بنيويًا صحيحة (المؤلف/السلسلة/العنوان من شجرة المجلدات)، وتبقى
 * المدد صفرًا حتى تُقرأ لاحقًا.
 *
 * فوق [MAX_READABLE_BYTES] يُتخطّى في كل الأوضاع لحماية الذاكرة الأصلية.
 * الافتراضي [NORMAL] حفاظًا على السلوك السابق.
 */
enum class ScanMode {
    NORMAL,
    ECONOMY,
    FAST;

    /** حجم الملف الذي يتجاوزه الوضع فيُتخطّى عنده. [FAST] = 0 أي تخطّي كامل. */
    val readCapBytes: Long
        get() = when (this) {
            NORMAL -> MAX_READABLE_BYTES
            ECONOMY -> ECONOMY_SKIP_BYTES
            FAST -> 0L
        }

    /**
     * هل يُقرأ ملف بهذا الحجم في هذا الوضع؟
     * حجم غير معروف (سالب) يُعتبر مقبولًا لأن تعذّر التحقق منه.
     */
    fun allows(sizeBytes: Long): Boolean = sizeBytes < 0L || sizeBytes < readCapBytes

    companion object {
        /** حارس صلب لكل الأوضاع: فوقه يُتخطّى دائمًا. */
        const val MAX_READABLE_BYTES: Long = 500L * 1024 * 1024

        /** عتبة [ECONOMY]. */
        const val ECONOMY_SKIP_BYTES: Long = 100L * 1024 * 1024

        fun fromName(raw: String?): ScanMode = entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}
