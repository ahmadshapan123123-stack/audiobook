package com.example.audiobook.presentation.common

/**
 * STAGE 2 — تقليص مسار/اسم للعرض في شريط تقدّم الفحص.
 *
 * القاعدة: الاحتفاظ بالمقطع الأول + آخر 3 مقاطع (`a/…/d/e/f`)، لأن ذيل
 * المسار (المجلد الحالي واسم الملف) هو المعلومة المفيدة أثناء الفحص.
 * الأسماء بلا شرطات تُقصّ من المنتصف. لا يتجاوز الناتج [maxLength].
 */
fun String.middleTruncated(maxLength: Int = 48): String {
    if (length <= maxLength) return this
    val segments = split('/')
    if (segments.size > 4) {
        val tail = segments.takeLast(3).joinToString("/")
        // الذيل مقدّس (المجلد والملف الجاريان)؛ التقليص يأكل من الرأس أولًا.
        val headBudget = maxLength - tail.length - 4 // "…/" + "/" حول الرأس
        if (headBudget > 0) {
            val head = segments.first().ifBlank { "…" }.take(headBudget)
            return "$head/…/$tail"
        }
        if (tail.length + 2 <= maxLength) return "…/$tail"
    }
    // احتياطي: قصّ من المنتصف مع ellipsis.
    val keep = (maxLength - 1) / 2
    return take(keep) + "…" + takeLast(maxLength - keep - 1)
}
