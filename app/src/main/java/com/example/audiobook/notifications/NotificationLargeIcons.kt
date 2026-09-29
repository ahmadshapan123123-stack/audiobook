package com.example.audiobook.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/**
 * PHASE B7 — صور largeIcon مولّدة للإشعارات (لا smallIcon: تبقى أحادية).
 *
 * عند غياب غلاف الكتاب يعرض النظام أيقونة التطبيق — مولّد أنيق أفضل.
 * كل الدوال خالصة ومتزامنة: تُستدعى من IO (الغلاف) أو تُحسب مرة واحدة
 * وتُحفظ (القمر/الفحص ثابتان).
 */
internal object NotificationLargeIcons {
    const val SIZE_PX = 128

    /** مربع لهجة مدوّر + الحرف الأول أبيض — بديل غلاف الكتاب. */
    fun letterArtwork(letter: String, bgArgb: Int, sizePx: Int = SIZE_PX): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = sizePx * 0.24f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgArgb }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, bg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            textSize = sizePx * 0.5f
        }
        val y = sizePx / 2f - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        canvas.drawText(letter.trim().take(1).ifBlank { "؟" }, sizePx / 2f, y, paint)
        return bmp
    }

    /** vector أبيض مركّز على خلفية داكنة مدوّرة — أيقونة الفحص كبيرة. */
    fun vectorArtwork(context: Context, @DrawableRes resId: Int, bgArgb: Int, sizePx: Int = SIZE_PX): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = sizePx * 0.24f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgArgb }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, bg)
        val drawable = ContextCompat.getDrawable(context, resId) ?: return bmp
        val inset = (sizePx * 0.22f).toInt()
        drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
        drawable.draw(canvas)
        return bmp
    }

    /** هلال — largeIcon إشعار مؤقت النوم. */
    fun moonArtwork(
        sizePx: Int = SIZE_PX,
        bgArgb: Int = 0xFF131A38.toInt(),
        moonArgb: Int = 0xFFF5F0DC.toInt()
    ): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = sizePx * 0.24f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgArgb }
        canvas.drawRoundRect(RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()), radius, radius, bg)
        val moon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = moonArgb }
        val c = sizePx / 2f
        val r = sizePx * 0.26f
        canvas.drawCircle(c - r * 0.18f, c, r, moon)
        canvas.drawCircle(c + r * 0.32f, c - r * 0.12f, r * 0.86f, bg)
        return bmp
    }
}
