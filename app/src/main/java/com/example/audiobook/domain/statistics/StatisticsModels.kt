package com.example.audiobook.domain.statistics

import com.example.audiobook.data.repository.DateRange
import java.util.UUID

/** ملخص فترة كامل: كل القيم محسوبة من جلسات الاستماع الحقيقية داخل النطاق. */
data class PeriodOverview(
    val range: DateRange = DateRange.WEEK,
    val listenedMs: Long = 0L,
    val previousPeriodMs: Long = 0L,
    val sessionsCount: Int = 0,
    val booksCount: Int = 0,
    val chaptersCount: Int = 0,
    val daysCount: Int = 0
) {
    /** فرق الفترة الحالية عن التي قبلها (موجب يعني استمعت أكثر). */
    val deltaMs: Long get() = listenedMs - previousPeriodMs
}

/** عمود واحد في الرسم البسيط: تسمية + مدة استماع بالميلي ثانية. */
data class ListeningBar(
    val label: String,
    val valueMs: Long = 0L
)

/** كتاب ضمن "الأكثر استماعًا" مع عدد جلسات الاستماع المسجّلة له في النطاق. */
data class BookListeningStat(
    val bookId: UUID,
    val editionId: UUID?,
    val title: String,
    val authorName: String?,
    val seriesName: String?,
    val coverColorTheme: String?,
    val sessionCount: Int
)

/** كتاب قيد الاستماع (تقدّمك): الموضع الحالي مقابل المدة الكلية. */
data class InProgressStat(
    val editionId: UUID,
    val bookId: UUID,
    val title: String,
    val authorName: String?,
    val seriesName: String?,
    val coverColorTheme: String?,
    val totalDurationMs: Long,
    val currentPositionMs: Long
) {
    val fraction: Float
        get() = if (totalDurationMs > 0L) (currentPositionMs.toFloat() / totalDurationMs).coerceIn(0f, 1f) else 0f

    val remainingMs: Long
        get() = (totalDurationMs - currentPositionMs).coerceAtLeast(0L)
}

/** إنجازات صامتة حقيقية. */
data class Achievements(
    val firstCompletedBookTitle: String? = null,
    val longestSessionMs: Long = 0L,
    val longestSessionBookTitle: String? = null,
    val mostListenedDayLabel: String? = null,
    val mostListenedDayMs: Long = 0L,
    val topSeries: SeriesAchievement? = null
)

/** سلسلة قطعت فيها شوطًا: عدد كتبها التي استمعت إليها من إجمالي كتبها. */
data class SeriesAchievement(
    val seriesName: String,
    val listenedBooks: Int,
    val totalBooks: Int
)

/** عادة استماع: تسمية فترة اليوم + إجمالي مدة الاستماع فيها. */
data class HabitStat(
    val label: String,
    val listenedMs: Long
)

/** توزيع سرعة استماع: تسمية القيمة + عدد الجلسات المسجَّلة بها. */
data class SpeedBucket(
    val label: String,
    val count: Int
)