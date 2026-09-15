package com.example.audiobook.data.repository

import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import com.example.audiobook.domain.statistics.Achievements
import com.example.audiobook.domain.statistics.BookListeningStat
import com.example.audiobook.domain.statistics.HabitStat
import com.example.audiobook.domain.statistics.InProgressStat
import com.example.audiobook.domain.statistics.ListeningBar
import com.example.audiobook.domain.statistics.PeriodOverview
import com.example.audiobook.domain.statistics.SeriesAchievement
import com.example.audiobook.domain.statistics.SpeedBucket
import com.example.audiobook.domain.statistics.StatisticsDates
import com.example.audiobook.domain.statistics.StatisticsRanges
import com.example.audiobook.domain.statistics.StatisticsRules
import com.example.audiobook.domain.statistics.arabicDayName
import com.example.audiobook.playback.SleepTimerClock
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.example.audiobook.domain.statistics.ARABIC_MONTH_NAMES

/** تسمية مضغوطة للقيمة (1.0 → "1"، 1.25 → "1.25" بدون أصفار زائدة). */
private fun trimSpeedLabel(value: Float): String =
    DecimalFormat("#.##", DecimalFormatSymbols(Locale.US)).format(value)

interface BookRepository {
    suspend fun insert(book: BookEntity)
    suspend fun update(book: BookEntity)
    suspend fun delete(book: BookEntity)
    suspend fun getById(id: UUID): BookEntity?
    suspend fun getByAuthor(authorId: UUID): List<BookEntity>
}

interface EditionRepository {
    suspend fun insert(edition: EditionEntity)
    suspend fun update(edition: EditionEntity)
    suspend fun delete(edition: EditionEntity)
    suspend fun getById(id: UUID): EditionEntity?
    suspend fun getByBook(bookId: UUID): List<EditionEntity>
}

interface ChapterRepository {
    suspend fun insert(chapter: ChapterEntity)
    suspend fun update(chapter: ChapterEntity)
    suspend fun delete(chapter: ChapterEntity)
    suspend fun getById(id: UUID): ChapterEntity?
    suspend fun getByEdition(editionId: UUID): List<ChapterEntity>
}

interface BookmarkRepository {
    suspend fun insert(bookmark: BookmarkEntity)
    suspend fun update(bookmark: BookmarkEntity)
    suspend fun delete(bookmark: BookmarkEntity)
    suspend fun getById(id: UUID): BookmarkEntity?
    suspend fun getByEdition(editionId: UUID): List<BookmarkEntity>
}

interface ProgressRepository {
    suspend fun insert(progress: ListeningProgressEntity)
    suspend fun update(progress: ListeningProgressEntity)
    suspend fun getByEdition(editionId: UUID): ListeningProgressEntity?
}

interface CollectionRepository {
    suspend fun insert(collection: CollectionEntity)
    suspend fun update(collection: CollectionEntity)
    suspend fun delete(collection: CollectionEntity)
    suspend fun getById(id: UUID): CollectionEntity?
    suspend fun addBook(collectionId: UUID, bookId: UUID)
    suspend fun getBooks(collectionId: UUID): List<CollectionBookCrossRef>
}

/**
 * نطاقات تقرير وقت الاستماع:
 * TODAY → اليوم الحالي من منتصف ليله حتى الآن.
 * WEEK  → الأسبوع المتدحرج (آخر 7 أيام حتى الآن).
 * MONTH → الشهر التقويمي الحالي حتى الآن.
 * YEAR  → السنة التقويمية الحالية حتى الآن.
 * ALL   → كل السجل (منذ أول جلسة).
 */
enum class DateRange { TODAY, WEEK, MONTH, YEAR, ALL }

interface StatisticsRepository {
    suspend fun completedSessions(): List<ListeningSessionEntity>

    /** مجموع [ListeningSessionEntity.durationListenedMs] للجلسات داخل النطاق (حسب [ListeningSessionEntity.startedAt]). */
    suspend fun listeningTimeForRange(range: DateRange): Long

    /** عدد النسخ التي بلغت نهايتها فعليًا (status = FINISHED في listening_progress). */
    suspend fun completedBooksCount(): Int

    /** عدد سجلات اكتمال الفصول المسجَّلة فعليًا (قاعدة الـ90% الحرفية، مرة لكل فصل). */
    suspend fun completedChaptersCount(): Int

    /** أيام متتالية حتى اليوم بها جلسة استماع واحدة على الأقل — خوارزمية صريحة على التواريخ. */
    suspend fun currentStreak(): Int

    /** متوسط [ListeningProgressEntity.playbackSpeed] عبر النسخ المسجَّلة (0f عند غياب بيانات). */
    suspend fun averageSpeed(): Float

    /** أحدث الجلسات بترتيب زمني تنازلي مع اسم الكتاب (شاشة History). */
    suspend fun history(): List<ListeningHistoryRow>

    /** ملخص فترة كامل: وقت الفترة + وقت الفترة السابقة + عدد الجلسات/الكتب/الفصول/الأيام. */
    suspend fun periodOverview(range: DateRange): PeriodOverview

    /** أعمدة الرسم البسيط حسب نوع النطاق (أيام الأسبوع / أسابيع الشهر / شهور السنة / سنوات الكل). */
    suspend fun listeningBars(range: DateRange): List<ListeningBar>

    /** الكتب الأكثر استماعًا داخل النطاق بترتيب إجمالي جلساتها. */
    suspend fun topListenedBooks(range: DateRange, limit: Int): List<BookListeningStat>

    /** الكتب قيد الاستماع (status = IN_PROGRESS) مع المدة والموضع الحالي. */
    suspend fun inProgressBooks(): List<InProgressStat>

    /** إنجازات صامتة حقيقية: أول كتاب، أطول جلسة، أكثر يوم، سلسلة قطعت فيها شوطًا. */
    suspend fun achievements(): Achievements

    /** عادات الاستماع حسب أوقات اليوم (صباحًا/ظهرًا/مساءً/ليلًا) مجمعة من كل الجلسات. */
    suspend fun listeningHabits(): List<HabitStat>

    /** توزيع سرعات الاستماع على المقادير القياسية 1× / 1.25× / 1.5×. */
    suspend fun speedBuckets(): List<SpeedBucket>

    /** عدد عينات سرعة الاستماع المسجَّلة (لملاحظة كفاية البيانات). */
    suspend fun speedSampleCount(): Int
}

interface LibraryRootRepository {
    suspend fun insert(root: LibraryRootEntity)
    suspend fun update(root: LibraryRootEntity)
    suspend fun delete(root: LibraryRootEntity)
    suspend fun getById(id: UUID): LibraryRootEntity?
        suspend fun clearPriorityExcept(id: UUID): Unit
        suspend fun setPriority(id: UUID, isPriority: Boolean): Unit
        suspend fun setEnabled(id: UUID, isEnabled: Boolean): Unit
        suspend fun setScanStatus(id: UUID, status: ScanStatus): Unit
        suspend fun markScanFinished(id: UUID, timestamp: Long, status: ScanStatus): Unit
        suspend fun getEnabledBackgroundRoots(): List<LibraryRootEntity>
        suspend fun getEnabledPriorityRoots(): List<LibraryRootEntity>
}

interface RemoteDataSource

class LocalOnlyBookRepository(private val dao: BookDao) : BookRepository {
    override suspend fun insert(book: BookEntity) = dao.insert(book)
    override suspend fun update(book: BookEntity) = dao.update(book)
    override suspend fun delete(book: BookEntity) = dao.delete(book)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByAuthor(authorId: UUID) = dao.getByParent(authorId)
}

class LocalOnlyEditionRepository(private val dao: EditionDao) : EditionRepository {
    override suspend fun insert(edition: EditionEntity) = dao.insert(edition)
    override suspend fun update(edition: EditionEntity) = dao.update(edition)
    override suspend fun delete(edition: EditionEntity) = dao.delete(edition)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByBook(bookId: UUID) = dao.getByParent(bookId)
}

class LocalOnlyChapterRepository(private val dao: ChapterDao) : ChapterRepository {
    override suspend fun insert(chapter: ChapterEntity) = dao.insert(chapter)
    override suspend fun update(chapter: ChapterEntity) = dao.update(chapter)
    override suspend fun delete(chapter: ChapterEntity) = dao.delete(chapter)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyBookmarkRepository(private val dao: BookmarkDao) : BookmarkRepository {
    override suspend fun insert(bookmark: BookmarkEntity) = dao.insert(bookmark)
    override suspend fun update(bookmark: BookmarkEntity) = dao.update(bookmark)
    override suspend fun delete(bookmark: BookmarkEntity) = dao.delete(bookmark)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyProgressRepository(private val dao: ProgressDao) : ProgressRepository {
    override suspend fun insert(progress: ListeningProgressEntity) = dao.insert(progress)
    override suspend fun update(progress: ListeningProgressEntity) = dao.update(progress)
    override suspend fun getByEdition(editionId: UUID) = dao.getByParent(editionId)
}

class LocalOnlyCollectionRepository(private val dao: CollectionDao, private val crossRefDao: CollectionBookCrossRefDao) : CollectionRepository {
    override suspend fun insert(collection: CollectionEntity) = dao.insert(collection)
    override suspend fun update(collection: CollectionEntity) = dao.update(collection)
    override suspend fun delete(collection: CollectionEntity) = dao.delete(collection)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun addBook(collectionId: UUID, bookId: UUID) = crossRefDao.insert(CollectionBookCrossRef(collectionId, bookId))
    override suspend fun getBooks(collectionId: UUID) = crossRefDao.getByParent(collectionId)
}

class LocalOnlyStatisticsRepository(
    private val dao: StatisticsDao,
    private val clock: SleepTimerClock
) : StatisticsRepository {
    override suspend fun completedSessions() = dao.getCompletedSessions()

    override suspend fun listeningTimeForRange(range: DateRange): Long {
        val now = clock.nowMillis()
        val rangeStart = StatisticsRanges.rangeStartMillis(range, now)
        return dao.getSessionsBetween(rangeStart, now).sumOf { it.durationListenedMs }
    }

    override suspend fun completedBooksCount(): Int = dao.getCompletedBooks().size

    override suspend fun completedChaptersCount(): Int = dao.countCompletedChapters()

    override suspend fun currentStreak(): Int {
        val sessionDays = dao.getAllSessions().map { StatisticsDates.dayNumber(it.startedAt) }
        val today = StatisticsDates.dayNumber(clock.nowMillis())
        return StatisticsRules.computeStreak(sessionDays, today)
    }

    override suspend fun averageSpeed(): Float {
        val speeds = dao.getPlaybackSpeeds()
        return if (speeds.isEmpty()) 0f else speeds.sum() / speeds.size
    }

    override suspend fun history(): List<ListeningHistoryRow> = dao.getHistory()

    override suspend fun periodOverview(range: DateRange): PeriodOverview {
        val now = clock.nowMillis()
        val start = StatisticsRanges.rangeStartMillis(range, now)
        val previousStart = StatisticsRanges.previousRangeStartMillis(range, now)
        val rows = dao.getSessionsWithBookBetween(start, now)
        val previousRows = dao.getSessionsWithBookBetween(previousStart, start)
        return PeriodOverview(
            range = range,
            listenedMs = rows.sumOf { it.durationListenedMs },
            previousPeriodMs = previousRows.sumOf { it.durationListenedMs },
            sessionsCount = rows.size,
            booksCount = rows.map { it.bookId }.distinct().size,
            chaptersCount = dao.countCompletedChaptersBetween(start, now),
            daysCount = rows.map { StatisticsDates.dayNumber(it.startedAt) }.distinct().size
        )
    }

    override suspend fun listeningBars(range: DateRange): List<ListeningBar> {
        val now = clock.nowMillis()
        val start = StatisticsRanges.rangeStartMillis(range, now)
        val rows = dao.getSessionsWithBookBetween(start, now)
        return when (range) {
            DateRange.WEEK -> {
                val today = StatisticsDates.dayNumber(now)
                (today - 6..today).map { day ->
                    ListeningBar(
                        label = arabicDayName(day),
                        valueMs = rows.filter { StatisticsDates.dayNumber(it.startedAt) == day }.sumOf { it.durationListenedMs }
                    )
                }
            }
            DateRange.MONTH -> {
                val monthStartDay = StatisticsDates.dayNumber(StatisticsRanges.rangeStartMillis(DateRange.MONTH, now))
                val today = StatisticsDates.dayNumber(now)
                val weekCount = ((today - monthStartDay) / 7) + 1
                (1..weekCount).map { week ->
                    ListeningBar(
                        label = "أسبوع $week",
                        valueMs = rows.filter {
                            ((StatisticsDates.dayNumber(it.startedAt) - monthStartDay) / 7) + 1 == week
                        }.sumOf { it.durationListenedMs }
                    )
                }
            }
            DateRange.YEAR -> {
                val calendar = Calendar.getInstance()
                (0 until 12).map { month ->
                    ListeningBar(
                        label = ARABIC_MONTH_NAMES[month],
                        valueMs = rows.filter {
                            calendar.timeInMillis = it.startedAt
                            calendar.get(Calendar.MONTH) == month
                        }.sumOf { it.durationListenedMs }
                    )
                }
            }
            DateRange.ALL -> {
                val yearFormat = SimpleDateFormat("yyyy", Locale.US)
                rows.groupBy { yearFormat.format(Date(it.startedAt)) }
                    .toSortedMap()
                    .map { (year, yearRows) -> ListeningBar(year, yearRows.sumOf { it.durationListenedMs }) }
            }
            DateRange.TODAY -> emptyList()
        }
    }

    override suspend fun topListenedBooks(range: DateRange, limit: Int): List<BookListeningStat> {
        val now = clock.nowMillis()
        val start = StatisticsRanges.rangeStartMillis(range, now)
        return dao.getSessionsWithBookBetween(start, now)
            .groupBy { it.bookId }
            .map { (bookId, rows) ->
                val first = rows.first()
                BookListeningStat(
                    bookId = bookId,
                    editionId = first.editionId,
                    title = first.bookTitle,
                    authorName = first.authorName,
                    seriesName = first.seriesName,
                    coverColorTheme = first.seriesColorTheme ?: first.authorColorTheme,
                    sessionCount = rows.size
                )
            }
            .sortedByDescending { it.sessionCount }
            .take(limit)
    }

    override suspend fun inProgressBooks(): List<InProgressStat> =
        dao.getInProgressRows().map { row ->
            InProgressStat(
                editionId = row.editionId,
                bookId = row.bookId,
                title = row.bookTitle,
                authorName = row.authorName,
                seriesName = row.seriesName,
                coverColorTheme = row.seriesColorTheme ?: row.authorColorTheme,
                totalDurationMs = row.totalDurationMs,
                currentPositionMs = row.currentPositionMs
            )
        }

    override suspend fun achievements(): Achievements {
        val now = clock.nowMillis()
        val allRows = dao.getSessionsWithBookBetween(0L, now)
        val longest = allRows.maxByOrNull { it.durationListenedMs }
        val dayTotals = allRows.groupBy { StatisticsDates.dayNumber(it.startedAt) }
            .mapValues { (_, rows) -> rows.sumOf { it.durationListenedMs } }
        val bestDay = dayTotals.maxByOrNull { it.value }

        val seriesListening = dao.getSeriesListening()
        val listenedPerSeries = seriesListening
            .groupBy { it.seriesId }
            .mapValues { (_, rows) -> rows.map { it.bookId }.distinct() }
        val totalsPerSeries = dao.getTotalBooksPerSeries().associate { it.seriesId to it.totalBooks }
        val topSeries = listenedPerSeries
            .mapNotNull { (seriesId, books) ->
                val name = seriesListening.firstOrNull { it.seriesId == seriesId }?.seriesName ?: return@mapNotNull null
                SeriesAchievement(
                    seriesName = name,
                    listenedBooks = books.size,
                    totalBooks = totalsPerSeries[seriesId] ?: books.size
                )
            }
            .filter { it.listenedBooks > 0 }
            .maxWithOrNull(
                compareBy<com.example.audiobook.domain.statistics.SeriesAchievement> { it.listenedBooks }
                    .thenBy { it.listenedBooks * 1.0 / (it.totalBooks + 1) }
            )

        return Achievements(
            firstCompletedBookTitle = dao.getFirstCompletedBook()?.bookTitle,
            longestSessionMs = longest?.durationListenedMs ?: 0L,
            longestSessionBookTitle = longest?.bookTitle,
            mostListenedDayLabel = bestDay?.let { arabicDayName(it.key) },
            mostListenedDayMs = bestDay?.value ?: 0L,
            topSeries = topSeries
        )
    }

    override suspend fun listeningHabits(): List<HabitStat> {
        val now = clock.nowMillis()
        val rows = dao.getSessionsWithBookBetween(0L, now)
        val calendar = Calendar.getInstance()
        val buckets = listOf("صباحًا", "ظهرًا", "مساءً", "ليلًا")
        val totals = LongArray(buckets.size)
        for (row in rows) {
            calendar.timeInMillis = row.startedAt
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val index = when (hour) {
                in 5..11 -> 0
                in 12..16 -> 1
                in 17..20 -> 2
                else -> 3
            }
            totals[index] += row.durationListenedMs
        }
        return buckets.mapIndexed { index, label -> HabitStat(label, totals[index]) }
    }

    override suspend fun speedBuckets(): List<SpeedBucket> {
        val presets = listOf(1f, 1.25f, 1.5f)
        val speeds = dao.getPlaybackSpeeds()
        return presets.map { preset ->
            SpeedBucket(
                label = "${trimSpeedLabel(preset)}×",
                count = speeds.count { kotlin.math.abs(it - preset) < 0.001f }
            )
        }
    }

    override suspend fun speedSampleCount(): Int = dao.getPlaybackSpeeds().size
}

class LocalOnlyLibraryRootRepository(private val dao: LibraryRootDao) : LibraryRootRepository {
    override suspend fun insert(root: LibraryRootEntity) = dao.insert(root)
    override suspend fun update(root: LibraryRootEntity) = dao.update(root)
    override suspend fun delete(root: LibraryRootEntity) = dao.delete(root)
    override suspend fun getById(id: UUID) = dao.getById(id)
    override suspend fun clearPriorityExcept(id: UUID) = dao.clearPriorityExcept(id)
    override suspend fun setPriority(id: UUID, isPriority: Boolean) = dao.setPriority(id, isPriority)
    override suspend fun setEnabled(id: UUID, isEnabled: Boolean) = dao.setEnabled(id, isEnabled)
    override suspend fun setScanStatus(id: UUID, status: ScanStatus) = dao.setScanStatus(id, status)
    override suspend fun markScanFinished(id: UUID, timestamp: Long, status: ScanStatus) = dao.markScanFinished(id, timestamp, status)
    override suspend fun getEnabledBackgroundRoots() = dao.getEnabledBackgroundRoots()
    override suspend fun getEnabledPriorityRoots() = dao.getEnabledPriorityRoots()
}