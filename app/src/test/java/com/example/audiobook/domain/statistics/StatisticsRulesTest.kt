package com.example.audiobook.domain.statistics

import com.example.audiobook.data.repository.DateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * [R4-النقطة 1] اختبارات القواعد الصافية للإحصائيات:
 * خوارزمية الـStreak (متتالية / فجوة) + حد الـ90% الحرفي (إيجابي وسلبي صريح).
 */
class StatisticsRulesTest {

    // ---- Streak: خوارزمية صريحة على أيام مرتبة ----

    @Test
    fun streakWithConsecutiveDaysNoGapCountsAllDaysEndingToday() {
        assertEquals(3, StatisticsRules.computeStreak(listOf(5, 6, 7), today = 7))
    }

    @Test
    fun streakBreaksAtOneDayGapAndRestartsFromLatestRun() {
        // الفجوة يوم 4 (غاب الاستماع) توقّف العد عند 7-6-5 ثم يتوقف.
        assertEquals(3, StatisticsRules.computeStreak(listOf(3, 5, 6, 7), today = 7))
        // فجوة أمس بالذات → لا ستريك.
        assertEquals(0, StatisticsRules.computeStreak(listOf(6), today = 7))
        // يوم واحد فقط اليوم → 1.
        assertEquals(1, StatisticsRules.computeStreak(listOf(7), today = 7))
    }

    @Test
    fun streakIgnoresDurationAndDuplicateDates() {
        assertEquals(3, StatisticsRules.computeStreak(listOf(5, 5, 6, 7, 7), today = 7))
        assertEquals(0, StatisticsRules.computeStreak(emptyList(), today = 7))
    }

    // ---- Chapter Completed: قاعدة الـ90% الحرفية ----

    @Test
    fun chapterExactlyAtNinetyPercentIsMarkedCompleted() {
        // المدة 10000ms → عتبة 90% = 9000ms → playhead ≥ 9000 يُعتبر مكتملًا.
        assertTrue(StatisticsRules.isChapterCompleted(playheadMs = 9_000L, chapterStartMs = 0L, chapterEndMs = 10_000L))
        assertTrue(StatisticsRules.isChapterCompleted(playheadMs = 19_000L, chapterStartMs = 10_000L, chapterEndMs = 20_000L))
    }

    @Test
    fun chapterAtEightyNinePercentIsNotCompleted() {
        // 89% من 10000 = 8900 < عتبة 9000 → غير مكتمل (اختبار سلبي صريح).
        assertFalse(StatisticsRules.isChapterCompleted(playheadMs = 8_900L, chapterStartMs = 0L, chapterEndMs = 10_000L))
        assertFalse(StatisticsRules.isChapterCompleted(playheadMs = 8_999L, chapterStartMs = 0L, chapterEndMs = 10_000L))
    }

    @Test
    fun chapterPastItsEndIsCompletedEvenAtMinimum() {
        assertTrue(StatisticsRules.isChapterCompleted(playheadMs = 10_000L, chapterStartMs = 0L, chapterEndMs = 10_000L))
    }

    // ---- حدود الفصول: آخر فصل ينتهي عند نهاية النسخة ----

    @Test
    fun lastChapterEndsAtEditionEndAndMiddleChapterAtNextStart() {
        assertEquals(20_000L, StatisticsRules.chapterEndMs(chapterStartMs = 10_000L, nextChapterStartMs = 20_000L, editionEndMs = 90_000L))
        assertEquals(90_000L, StatisticsRules.chapterEndMs(chapterStartMs = 20_000L, nextChapterStartMs = null, editionEndMs = 90_000L))
        // امتداد: ثم بعد تجاوز نهاية النسخة يُعتبر مكتملًا.
        assertTrue(StatisticsRules.isChapterCompleted(playheadMs = 90_000L, chapterStartMs = 20_000L, chapterEndMs = 90_000L))
    }

    // ---- تحويل التواريخ: لم يعد "رقم اليوم" معتمدًا على المنطقة ----

    @Test
    fun dayNumberAndStartOfDayRespectTimeZoneOffset() {
        val gmt = TimeZone.getTimeZone("GMT")
        val plus3 = TimeZone.getTimeZone("GMT+3")
        val utcMidnightJan1_2020 = 1_577_836_800_000L
        val localMidnightPlus3 = utcMidnightJan1_2020 - 3 * 3_600_000L

        // منتصف ليل +3 هو نفس "رقم اليوم" في UTC لأن 00:00 محليًا و00:00 UTC يوم واحد.
        assertEquals(StatisticsDates.dayNumber(utcMidnightJan1_2020, gmt), StatisticsDates.dayNumber(localMidnightPlus3, plus3))
        // 23:59 في +3 لا تزال نفس اليوم.
        assertEquals(
            StatisticsDates.dayNumber(localMidnightPlus3, plus3),
            StatisticsDates.dayNumber(localMidnightPlus3 + 23 * 3_600_000L + 59 * 60_000L, plus3)
        )
        // بداية اليوم المحلي لقطة بعد الظهر تعيد إلى منتصف الليل.
        assertEquals(localMidnightPlus3, StatisticsDates.startOfDayMillis(localMidnightPlus3 + 5 * 3_600_000L, plus3))
    }

    @Test
    fun weekRangeSpansSixFullDaysBackFromTodayStart() {
        val gmt = TimeZone.getTimeZone("GMT")
        val now = 1_577_836_800_000L + 12 * 3_600_000L // 2020-01-01 ظهرًا UTC
        assertEquals(
            StatisticsDates.startOfDayMillis(now, gmt) - 6 * MILLIS_PER_DAY,
            StatisticsRanges.rangeStartMillis(DateRange.WEEK, now, gmt)
        )
        assertEquals(
            StatisticsDates.startOfDayMillis(now, gmt),
            StatisticsRanges.rangeStartMillis(DateRange.TODAY, now, gmt)
        )
        val monthStart = 1_577_836_800_000L // 2020-01-01 00:00 UTC
        assertEquals(monthStart, StatisticsRanges.rangeStartMillis(DateRange.MONTH, now, gmt))
    }
}