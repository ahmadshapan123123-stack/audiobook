package com.example.audiobook.presentation.theme

import androidx.compose.ui.graphics.Color
import com.example.audiobook.domain.model.AppThemeMode

/**
 * لوحة "أثير" بنسخ ARGB خالصة (غير Compose) — تُطابق [Cosmic] حرفيًا لكنها قابلة
 * للاستعمال من الإشعارات والخدمات دون جلب Compose. أي تعديل هنا يجب أن يرافق تعديل [Cosmic].
 */
object AtherCosmic {
    const val InkTop: Int = 0xFF05060F.toInt()
    const val InkBottom: Int = 0xFF0B0F24.toInt()
    const val NavBarBlue: Int = 0xFF16306B.toInt()
    const val Teal: Int = 0xFF2DD4BF.toInt()
    const val TealDeep: Int = 0xFF0B6E63.toInt()
    const val TealBright: Int = 0xFF45E0CC.toInt()
    // FIX-BLUE: ثنائي اللهجة الزرقاء (عميق/فاتح) — يطابق Cosmic.
    const val StardustViolet: Int = 0xFF3B82F6.toInt()
    const val VioletDeep: Int = 0xFF2563EB.toInt()
    const val VioletSoft: Int = 0xFF93C5FD.toInt()
    const val StardustMagenta: Int = 0xFFD946EF.toInt()
    const val StardustAmber: Int = 0xFFF59E0B.toInt()
    const val MoonIce: Int = 0xFFEDF2FF.toInt()
    const val DawnTop: Int = 0xFFF3EFFC.toInt()
    const val DawnBottom: Int = 0xFFE3EEF7.toInt()
}

/**
 * الحساب الموحّد لـ"لهجة أثير الديناميكية" — نفس الرياضيات التي يطبقها المشغّل
 * في [com.example.audiobook.presentation.player.playerForeground]:
 *
 * 1. بداية التدرج: سلسلة ← مؤلف ← غلاف ← افتراضي بحسب الوضع.
 * 2. في الوضع الفاتح: تعتيم البدء إذا كانت لمعانها في المنطقة "ما قبل القابلية للقراءة"
 *    (نسبة تباين النص ≥4.5:1).
 * 3. تضخيم تشبّع Hue-Saturation-Value (المشبّع ×1.4) وتوهج/تعتيم القيمة بحسب
 *    خلفية الاستعمال (فاتحة/داكنة) — تمامًا كما في [com.example.audiobook.presentation.player.Color.playerAccent].
 *
 * الإشعارات وشاشة القفل تقرأ هذه القيمة نفسها فيتطابق لونها مع لهجة المشغّل الحية.
 */
object AtherAccent {
    /** بدء تدرج "هادئ" أفقي موازٍ لـ PlayerGradientResolver.defaultGradient بدون تعتيم الفاتح. */
    // FIX-BLUE: الافتراضيات الداكنة زرقاء الصبغة.
    private fun defaultStart(mode: AppThemeMode): Int = when (mode) {
        AppThemeMode.LIGHT -> 0xFFE4D7C6.toInt()
        AppThemeMode.DARK -> 0xFF1E2A4A.toInt()
        AppThemeMode.AMOLED -> 0xFF101A30.toInt()
    }

    /** أول لون متاح ضمن التدرج (سلسلة ← مؤلف ← غلاف) أو افتراضي الوضع. */
    fun gradientStartArgb(mode: AppThemeMode, series: Int?, author: Int?, cover: Int?): Int =
        series ?: author ?: cover ?: defaultStart(mode)

    /**
     * لهجة التشغيل الديناميكية (ARGB) بمصدر التدرج نفسه الذي يستخدمه المشغّل.
     * تُستدعى من [PlayerScreen.playerForeground] ومن خدمة التشغيل/الإشعارات.
     */
    fun accentFor(mode: AppThemeMode, series: Int?, author: Int?, cover: Int?): Int {
        val base = gradientStartArgb(mode, series, author, cover)
        var start = base
        if (mode == AppThemeMode.LIGHT) {
            val lum = luminance(base)
            // موازٍ لـ PlayerGradientResolver.adjustForLightReadability
            if (lum > READABLE_DARK_LUMINANCE && lum < DARK_INK_MIN_LUMINANCE) {
                start = scaleChannels(base, READABLE_DARK_LUMINANCE / lum)
            }
        }
        return accentArgb(start, onLightBackground = mode == AppThemeMode.LIGHT)
    }

    /**
     * قلب الخوارزمية: تضخيم تشبّع اللون + معالجة القيمة (سطوع) بحسب الخلفية —
     * مطابق حرفيًا لـ [com.example.audiobook.presentation.player.Color.playerAccent].
     */
    fun accentArgb(baseArgb: Int, onLightBackground: Boolean): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            red(baseArgb), green(baseArgb), blue(baseArgb), hsv
        )
        hsv[1] = (hsv[1] * 1.4f).coerceAtMost(1f)
        return if (onLightBackground) {
            android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], hsv[1], (hsv[2] * 0.4f).coerceAtLeast(0.16f))
            )
        } else {
            android.graphics.Color.HSVToColor(
                floatArrayOf(hsv[0], hsv[1], (hsv[2] * 1.35f).coerceIn(0.52f, 0.94f))
            )
        }
    }

    /** لهجة عامة للعناصر غير المشغّل (عداد مؤقت النوم، إشعارات عامة) بحسب الوضع. */
    // FIX-VIOLET: بنفسجي (عميق للفاتح، فاتح للداكن/AMOLED).
    fun ambientAccentArgb(mode: AppThemeMode): Int = when (mode) {
        AppThemeMode.LIGHT -> AtherCosmic.VioletDeep
        AppThemeMode.DARK, AppThemeMode.AMOLED -> AtherCosmic.VioletSoft
    }

    private fun luminance(argb: Int): Float {
        fun lin(c: Float): Float {
            val v = c
            return if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        }
        return 0.2126f * lin(red(argb) / 255f) + 0.7152f * lin(green(argb) / 255f) + 0.0722f * lin(blue(argb) / 255f)
    }

    private fun scaleChannels(argb: Int, factor: Float): Int {
        fun s(c: Int) = (c * factor).toInt().coerceIn(0, 255)
        return argb and 0xFF000000.toInt() or (s(red(argb)) shl 16) or (s(green(argb)) shl 8) or s(blue(argb))
    }

    private fun red(argb: Int) = (argb shr 16) and 0xFF
    private fun green(argb: Int) = (argb shr 8) and 0xFF
    private fun blue(argb: Int) = argb and 0xFF

    // عتبات مطابقة لمثيلات Player/PlayerVisuals
    private const val READABLE_DARK_LUMINANCE = 0.18f
    private const val DARK_INK_MIN_LUMINANCE = 0.29f
}

/** تحويل لون Compose إلى ARGB خام (لإطعام الحسابات المشتركة والإشعارات). */
fun Color.argbInt(): Int {
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}