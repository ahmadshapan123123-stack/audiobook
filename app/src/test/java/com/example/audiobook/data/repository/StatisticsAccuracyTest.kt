package com.example.audiobook.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.playback.SleepTimerClock
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * FIX 6.4 — دقة الإحصائيات: 5 جلسات بمدد معلومة عبر inmemory DB —
 * المجموع = الجمع، والتفصيل اليومي صحيح، وتسميات الشهور كاملة.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsAccuracyTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: StatisticsRepository

    /** "الآن" الثابت: 15 يونيو 2026، 12:00 بالتوقيت المحلي. */
    private val fixedNow: Long = run {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.JUNE, 15, 12, 0, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }
    private val clock = object : SleepTimerClock {
        override fun nowMillis(): Long = fixedNow
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = LocalOnlyStatisticsRepository(database.statisticsDao(), clock)
    }

    @After
    fun tearDown() = database.close()

    private var cachedEditionId: UUID? = null

    /** سلسلة edition حقيقية واحدة (FK الجلسات تتطلبها). */
    private suspend fun editionId(): UUID {
        cachedEditionId?.let { return it }
        val root = LibraryRootEntity(
            uri = "content://stats", displayName = "stats", isPriority = false,
            isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE
        ).also { database.libraryRootDao().insert(it) }
        val author = AuthorEntity(name = "A", colorTheme = null).also { database.authorDao().insert(it) }
        val book = BookEntity(
            id = UUID.randomUUID(), title = "B", authorId = author.id, seriesId = null,
            orderInSeries = null, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        ).also { database.bookDao().insert(it) }
        val edition = EditionEntity(
            bookId = book.id, narratorName = null, label = "E", totalDurationMs = 3_600_000L,
            fileFormat = "mp3", libraryRootId = root.id, sourceFolderPath = "E",
            confidenceScore = 1f, isUserConfirmed = false, remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        ).also { database.editionDao().insert(it) }
        cachedEditionId = edition.id
        return edition.id
    }

    private suspend fun seedSession(dayOffset: Int, durationMs: Long, hourOffset: Int = 0) {
        // ملاحظة: الاستعلام `startedAt < now` حصري — جلسة تبدأ الآن تمامًا
        // تُستبعد، فجلسة "اليوم" تُزرع قبل ساعتين (نفس اليوم والشهر).
        val start = fixedNow - dayOffset * 24L * 60L * 60L * 1000L - hourOffset * 60L * 60L * 1000L
        database.listeningSessionDao().insert(
            ListeningSessionEntity(
                id = UUID.randomUUID(),
                editionId = editionId(),
                startedAt = start,
                endedAt = start + durationMs,
                durationListenedMs = durationMs,
                endReason = SessionEndReason.MANUAL_PAUSE,
                sessionState = SessionState.COMPLETED
            )
        )
    }

    @Test
    fun fiveSessionsSumToTotalWithDailyBreakdown() = runBlocking {
        // 5 جلسات: اليوم 10د، أمس 20د، قبل 3 أيام 30د، قبل 8 أيام 40د، قبل 40 يومًا 50د.
        seedSession(0, 10 * 60_000L, hourOffset = 2)
        seedSession(1, 20 * 60_000L)
        seedSession(3, 30 * 60_000L)
        seedSession(8, 40 * 60_000L)
        seedSession(40, 50 * 60_000L)

        val week = repository.periodOverview(DateRange.WEEK)
        assertEquals("أسبوع: 10+20+30=60 دقيقة", 60 * 60_000L, week.listenedMs)
        assertEquals("3 جلسات في الأسبوع", 3, week.sessionsCount)

        val month = repository.periodOverview(DateRange.MONTH)
        // الشهر يبدأ 1 يونيو: جلسات 0/1/3/8 يونيو = 10+20+30+40.
        assertEquals("شهر: 100 دقيقة", 100 * 60_000L, month.listenedMs)

        val all = repository.periodOverview(DateRange.ALL)
        assertEquals("الكل: 150 دقيقة", 150 * 60_000L, all.listenedMs)
        assertEquals("5 جلسات", 5, all.sessionsCount)

        // تفصيل يومي للأسبوع: 7 أعمدة، قيم الأيام المعروفة.
        val bars = repository.listeningBars(DateRange.WEEK)
        assertEquals("7 أعمدة أسبوعية", 7, bars.size)
        assertEquals("اليوم 10 دقائق", 10 * 60_000L, bars.last().valueMs)
    }

    @Test
    fun yearBarsCarryFullArabicMonthNames() = runBlocking {
        seedSession(0, 10 * 60_000L, hourOffset = 2)

        val bars = repository.listeningBars(DateRange.YEAR)
        assertEquals("12 عمودًا", 12, bars.size)
        val expected = listOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        assertEquals("تسميات الشهور كاملة", expected, bars.map { it.label })
        // يونيو (index 5) يحمل الجلسة؛ يناير فارغ.
        assertEquals(10 * 60_000L, bars[5].valueMs)
        assertEquals(0L, bars[0].valueMs)
        assertTrue("لا بتر في التسميات", bars.all { it.label.length >= 4 })
    }
}
