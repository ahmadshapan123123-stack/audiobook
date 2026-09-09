package com.example.audiobook.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterCompletionEntity
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.statistics.MILLIS_PER_DAY
import com.example.audiobook.domain.statistics.StatisticsDates
import com.example.audiobook.playback.SleepTimerClock
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * [R4-النقطة 1] تكامل حقيقي مع Room in-memory: تزرع جلسات/فصول/تقدم ثم تتحقق
 * من الحسابات الفعلية (وقت النطاقات، كتب مكتملة، فصول مكتملة، Streak، متوسط السرعة).
 * جميع "اليومات" تحسب بنفس أداة [StatisticsDates] فلا يتأثر الاختبار بمنطقة الجهاز.
 */
@RunWith(RobolectricTestRunner::class)
class StatisticsRepositoryIntegrationTest {

    private lateinit var database: AppDatabase
    private val clock = FakeClock(FAKE_NOW)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = database.close()

    private val todayStart = StatisticsDates.startOfDayMillis(FAKE_NOW)
    private val todayDay = StatisticsDates.dayNumber(FAKE_NOW)

    // ---- النطاقات: وقت الاستماع (اليوم/الأسبوع/الشهر) ----

    @Test
    fun listeningTimeForRangeSumsRealDurationsForTodayWeekAndMonth() = runBlocking {
        val edition = seedEditionChain()

        database.listeningSessionDao().insert(session(edition, startedAt = todayStart + 1_000L, listenedMs = 10_000L))
        database.listeningSessionDao().insert(session(edition, startedAt = todayStart + 2_000L, listenedMs = 5_000L))
        database.listeningSessionDao().insert(session(edition, startedAt = todayStart - 1 * MILLIS_PER_DAY + 3_000L, listenedMs = 2_000L))
        database.listeningSessionDao().insert(session(edition, startedAt = todayStart - 8 * MILLIS_PER_DAY + 4_000L, listenedMs = 60_000L))

        val repo = LocalOnlyStatisticsRepository(database.statisticsDao(), clock)

        assertEquals("اليوم: جلسات اليوم فقط", 15_000L, repo.listeningTimeForRange(DateRange.TODAY))
        assertEquals("الأسبوع المتدحرج: اليوم + أمس فقط (8 أيام خارج النطاق)", 17_000L, repo.listeningTimeForRange(DateRange.WEEK))
        assertEquals("الشهر: كل الجلسات داخل الشهر الحالي", 77_000L, repo.listeningTimeForRange(DateRange.MONTH))
    }

    // ---- الكتب المكتملة ----

    @Test
    fun completedBooksCountsOnlyEditionsReachingEnd() = runBlocking {
        val finished = seedEditionChain()
        val inProgress = seedEditionChain()

        database.progressDao().insert(progress(finished, status = ProgressStatus.FINISHED, speed = 2f))
        database.progressDao().insert(progress(inProgress, status = ProgressStatus.IN_PROGRESS, speed = 0.5f))

        assertEquals(1, LocalOnlyStatisticsRepository(database.statisticsDao(), clock).completedBooksCount())
    }

    // ---- الفصول المكتملة + منع التكرار ----

    @Test
    fun completedChaptersCountsDistinctChaptersOnlyAndIgnoresRepeats() = runBlocking {
        val edition = seedEditionChain()
        val chapterA = seedChapter(edition)
        val chapterB = seedChapter(edition)

        database.chapterCompletionDao().insert(ChapterCompletionEntity(chapterId = chapterA, editionId = edition, completedAtMs = 100L))
        database.chapterCompletionDao().insert(ChapterCompletionEntity(chapterId = chapterA, editionId = edition, completedAtMs = 5_000L))
        database.chapterCompletionDao().insert(ChapterCompletionEntity(chapterId = chapterB, editionId = edition, completedAtMs = 200L))

        assertEquals("الفصل نفسه لا يُعد مرتين", 2, LocalOnlyStatisticsRepository(database.statisticsDao(), clock).completedChaptersCount())
    }

    // ---- Streak متتالية بدون فجوة ----

    @Test
    fun currentStreakCountsConsecutiveDaysUpToToday() = runBlocking {
        val edition = seedEditionChain()
        insertSessionAt(edition, todayDay - 2)
        insertSessionAt(edition, todayDay - 1)
        insertSessionAt(edition, todayDay)

        assertEquals(3, LocalOnlyStatisticsRepository(database.statisticsDao(), clock).currentStreak())
    }

    // ---- Streak: فجوة يوم واحد تكسر الستريك ----

    @Test
    fun currentStreakBreaksAtOneDayGapAndRestartsFromLatestRun() = runBlocking {
        val repo = LocalOnlyStatisticsRepository(database.statisticsDao(), clock)
        val edition = seedEditionChain()

        // يبدأ ستريك قديم بـ3 أيام: T-5، T-4، T-3.
        insertSessionAt(edition, todayDay - 5)
        insertSessionAt(edition, todayDay - 4)
        insertSessionAt(edition, todayDay - 3)
        // فجوة يوم كامل T-2: لا جلسة إطلاقًا.
        // ستريك جديد: T-1 و T (يومان حتى اليوم).
        insertSessionAt(edition, todayDay - 1)
        insertSessionAt(edition, todayDay)

        assertEquals(
            "الفجوة تكسر الستريك القديم وتُبدأ الجديد من T-1 فقط",
            2, repo.currentStreak()
        )
    }

    @Test
    fun currentStreakIsZeroWhenNoSessionTodayEvenIfYesterdayListened() = runBlocking {
        val repo = LocalOnlyStatisticsRepository(database.statisticsDao(), clock)
        val edition = seedEditionChain()
        insertSessionAt(edition, todayDay - 1)

        assertEquals(0, repo.currentStreak())
    }

    // ---- متوسط السرعة ----

    @Test
    fun averageSpeedIsMeanOfRecordedPlaybackSpeeds() = runBlocking {
        val editionA = seedEditionChain()
        val editionB = seedEditionChain()
        database.progressDao().insert(progress(editionA, status = ProgressStatus.IN_PROGRESS, speed = 1.0f))
        database.progressDao().insert(progress(editionB, status = ProgressStatus.IN_PROGRESS, speed = 2.0f))

        assertEquals(1.5f, LocalOnlyStatisticsRepository(database.statisticsDao(), clock).averageSpeed(), 0.0001f)
    }

    @Test
    fun averageSpeedIsZeroWithoutAnyRecordedProgress() = runBlocking {
        assertEquals(0f, LocalOnlyStatisticsRepository(database.statisticsDao(), clock).averageSpeed(), 0.0001f)
    }

    // ---- مساعدات ----

    private suspend fun insertSessionAt(editionId: UUID, day: Int) {
        val at = todayStart + (day - todayDay) * MILLIS_PER_DAY + 5_000L
        database.listeningSessionDao().insert(session(editionId, startedAt = at, listenedMs = 1_000L))
    }

    private fun session(editionId: UUID, startedAt: Long, listenedMs: Long) = ListeningSessionEntity(
        id = UUID.randomUUID(),
        editionId = editionId,
        startedAt = startedAt,
        endedAt = startedAt + listenedMs,
        durationListenedMs = listenedMs,
        endReason = SessionEndReason.MANUAL_PAUSE,
        sessionState = SessionState.COMPLETED
    )

    private fun progress(editionId: UUID, status: ProgressStatus, speed: Float) = ListeningProgressEntity(
        id = UUID.randomUUID(),
        editionId = editionId,
        currentPositionMs = if (status == ProgressStatus.FINISHED) 60_000L else 10_000L,
        lastPlayedAt = FAKE_NOW,
        status = status,
        playbackSpeed = speed,
        remoteId = null,
        syncStatus = SyncStatus.LOCAL_ONLY
    )

    private suspend fun seedChapter(editionId: UUID): UUID {
        val chapter = ChapterEntity(editionId = editionId, title = "فصل", startPositionMs = 0L, orderIndex = 0, createdFrom = ChapterCreatedFrom.IMPORTED)
        database.chapterDao().insert(chapter)
        return chapter.id
    }

    private suspend fun seedEditionChain(): UUID {
        val root = LibraryRootEntity(uri = "content://test/root", displayName = "test", isPriority = false, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        database.libraryRootDao().insert(root)
        val author = AuthorEntity(name = "مؤلف", colorTheme = null)
        database.authorDao().insert(author)
        val book = BookEntity(title = "كتاب", authorId = author.id, seriesId = null, orderInSeries = null, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.bookDao().insert(book)
        val edition = EditionEntity(bookId = book.id, narratorName = null, label = "النسخة", totalDurationMs = 60_000L, fileFormat = "m4b", libraryRootId = root.id, sourceFolderPath = "test", confidenceScore = 1f, isUserConfirmed = false, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.editionDao().insert(edition)
        return edition.id
    }

    private class FakeClock(val nowMs: Long) : SleepTimerClock {
        override fun nowMillis(): Long = nowMs
    }

    private companion object {
        // 2020-01-10 12:00 UTC — تاريخ ثابت: 8 أيام قبلها داخل نفس الشهر حتى يظل MONTH محددًا.
        val FAKE_NOW = 1_577_836_800_000L + 9 * MILLIS_PER_DAY + 12 * 3_600_000L
    }
}