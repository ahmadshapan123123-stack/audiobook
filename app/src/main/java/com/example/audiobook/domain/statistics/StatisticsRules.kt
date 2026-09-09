package com.example.audiobook.domain.statistics

import com.example.audiobook.data.repository.DateRange
import java.util.Calendar
import java.util.TimeZone

/**
 * الحد المعتمد لتعرّف "Chapter Completed" من الـSpec حرفيًا:
 * playhead ≥ 90% من مدة الفصل أو تجاوز نهايته فعليًا (أيهما أسبق).
 *
 * هذا هو الرقم الوحيد المعتمد؛ أي تغيير في الـSpec يتطلب تعديل ثابت
 * [CHAPTER_COMPLETION_THRESHOLD] هنا أولًا قبل أي مكان آخر يستخدمه.
 */
const val CHAPTER_COMPLETION_THRESHOLD = 0.9f

/** عدد ميلي ثانية في اليوم الواحد. */
const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000L

/**
 * كل حسابات الإحصائيات الصافية — لا تقارب ولا تخمين:
 * 1) حد اكتمال الفصل (قاعدة الـ90% الحرفية).
 * 2) خوارزمية الـStreak الصريحة على قائمة أيام مرتبة.
 * 3) تعيين الطابع الزمني → رقم اليوم (بالتوقيت المحلي للجهاز).
 * 4) بداية النطاقات (اليوم/الأسبوع/الشهر).
 */
object StatisticsRules {

    /**
     * مدة الفصل: من بدايته حتى بداية الفصل التالي، وآخر فصل ينتهي
     * عند نهاية النسخة (editionEndMs).
     */
    fun chapterEndMs(chapterStartMs: Long, nextChapterStartMs: Long?, editionEndMs: Long): Long =
        nextChapterStartMs ?: editionEndMs

    fun chapterDurationMs(chapterStartMs: Long, nextChapterStartMs: Long?, editionEndMs: Long): Long {
        val end = chapterEndMs(chapterStartMs, nextChapterStartMs, editionEndMs)
        return (end - chapterStartMs).coerceAtLeast(0L)
    }

    /**
     * قاعدة "Chapter Completed" الحرفية: يُعتبر الفصل مكتملًا عندما
     * يكون موضع التشغيل ≥ 90% من مدة الفصل أو متجاوزًا لنهايته (أيهما أسبق).
     * عند المدة الصفرية يُكتفى ببلوغ الفصل نفسه.
     */
    fun isChapterCompleted(playheadMs: Long, chapterStartMs: Long, chapterEndMs: Long): Boolean {
        val duration = chapterEndMs - chapterStartMs
        if (duration <= 0L) return playheadMs >= chapterStartMs
        val ninetyPercentPosition = chapterStartMs + (duration * CHAPTER_COMPLETION_THRESHOLD).toLong()
        return playheadMs >= ninetyPercentPosition
    }

    /**
     * الـStreak: عدد الأيام المتتالية (حتى اليوم شامله) التي سُجِّلت فيها جلسة
     * استماع واحدة على الأقل، بغض النظر عن مدتها. نقاط الاحتكاك:
     * - لا يوم في القائمة = صفر.
     * - لا توجد جلسة اليوم = صفر (انكسر الستريك حتى لو كانت أمس موجودة).
     * - تمثل القائمة أيامًا صريحة مرتبة (بدون تكرار) قادمة من قاعدة البيانات.
     */
    fun computeStreak(sessionDays: List<Int>, today: Int): Int {
        val days = sessionDays.distinct().sorted()
        var streak = 0
        var expected = today
        for (index in days.indices.reversed()) {
            if (days[index] != expected) break
            streak += 1
            expected -= 1
        }
        return streak
    }
}

/** قسمة نزولية (Floor) صحيحة حتى للمضاعفات السالبة بالضبط. */
private fun floorDiv(value: Long, divisor: Long): Long {
    var result = value / divisor
    if (value % divisor != 0L && value < 0L) result -= 1
    return result
}

object StatisticsDates {

    /**
     * الطابع الزمني → رقم اليوم (أيام منذ العصر) حسب توقيت الجهاز:
     * إزاحة المنطقة تُضاف قبل القسمة حتى لا تزيّح المنطق عبر منتصف الليل.
     * يستخدمها الـStreak كلها بنفس الطريقة فلا تختلف "الية اليوم" بين الجلسات.
     */
    fun dayNumber(millis: Long, zone: TimeZone = TimeZone.getDefault()): Int =
        floorDiv(millis + zone.getOffset(millis), MILLIS_PER_DAY).toInt()

    /** بداية اليوم (الساعة 00:00 المحلية) لطابع زمني معيّن. */
    fun startOfDayMillis(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val offset = zone.getOffset(millis)
        val localDayStart = floorDiv(millis + offset, MILLIS_PER_DAY) * MILLIS_PER_DAY
        return localDayStart - offset
    }

    /** بداية الشهر التقويمي (الساعة 00:00 المحلية لأول يوم) لطابع زمني معيّن. */
    fun startOfMonthMillis(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long {
        val calendar = Calendar.getInstance(zone)
        calendar.timeInMillis = millis
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.clear(Calendar.MINUTE)
        calendar.clear(Calendar.SECOND)
        calendar.clear(Calendar.MILLISECOND)
        return calendar.timeInMillis
    }
}

object StatisticsRanges {

    /**
     * بداية نطاق "حتى الآن" لكل نوع:
     * TODAY  → بداية اليوم الحالي.
     * WEEK   → قبل 7 أيام متدحرجة (بداية اليوم الحالي ناقص 6 أيام) — تعريف الأسبوع هنا.
     * MONTH  → بداية الشهر التقويمي الحالي.
     * النهاية دائمًا "الآن"، والجلسة تُحسب حسب [ListeningSessionEntity.startedAt].
     */
    fun rangeStartMillis(range: DateRange, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Long = when (range) {
        DateRange.TODAY -> StatisticsDates.startOfDayMillis(nowMillis, zone)
        DateRange.WEEK -> StatisticsDates.startOfDayMillis(nowMillis, zone) - 6 * MILLIS_PER_DAY
        DateRange.MONTH -> StatisticsDates.startOfMonthMillis(nowMillis, zone)
    }
}