package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.repository.LocalOnlyStatisticsRepository
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
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.playback.SleepTimerClock
import com.example.audiobook.presentation.statistics.StatisticsScreen
import com.example.audiobook.presentation.statistics.StatisticsViewModel
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-480dpi")
class StatisticsScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private val nowMillis: Long = 1_700_000_000_000L
    private val fakeClock = object : SleepTimerClock {
        override fun nowMillis(): Long = nowMillis
    }

    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seed(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seed(context: Context) {
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        val series = SeriesEntity(authorId = author.id, name = "سلسلة", colorTheme = null)
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = "أسطورة نوماد",
            authorId = author.id,
            seriesId = series.id,
            orderInSeries = 1,
            genre = "رواية",
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val edition = EditionEntity(
            id = UUID.randomUUID(),
            bookId = book.id,
            narratorName = "راو",
            label = "الإصدار الأصلي",
            totalDurationMs = 3_600_000,
            fileFormat = "M4B",
            libraryRootId = root.id,
            sourceFolderPath = "/نوماد",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val startOfToday = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val session = ListeningSessionEntity(
            id = UUID.randomUUID(),
            editionId = edition.id,
            startedAt = startOfToday + 5 * 60_000L,
            endedAt = startOfToday + 5 * 60_000L + 1_500_000L,
            durationListenedMs = 1_500_000L,
            endReason = SessionEndReason.MANUAL_PAUSE,
            sessionState = SessionState.COMPLETED
        )
        val progress = ListeningProgressEntity(
            id = UUID.randomUUID(),
            editionId = edition.id,
            currentPositionMs = 1_800_000L,
            lastPlayedAt = nowMillis,
            status = ProgressStatus.FINISHED,
            playbackSpeed = 1.5f,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val completion1 = ChapterCompletionEntity(chapterId = UUID.randomUUID(), editionId = edition.id, completedAtMs = nowMillis - 2_000_000L)
        val completion2 = ChapterCompletionEntity(chapterId = UUID.randomUUID(), editionId = edition.id, completedAtMs = nowMillis - 1_000_000L)
        val chapter = ChapterEntity(id = completion1.chapterId, editionId = edition.id, title = "الفصل الأول", startPositionMs = 0L, orderIndex = 0, createdFrom = ChapterCreatedFrom.IMPORTED)
        val chapter2 = ChapterEntity(id = completion2.chapterId, editionId = edition.id, title = "الفصل الثاني", startPositionMs = 100_000L, orderIndex = 1, createdFrom = ChapterCreatedFrom.IMPORTED)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
                database.listeningSessionDao().insert(session)
                database.progressDao().insert(progress)
                database.chapterDao().insert(chapter)
                database.chapterDao().insert(chapter2)
                database.chapterCompletionDao().insert(completion1)
                database.chapterCompletionDao().insert(completion2)
            }
        }
    }

    private fun showScreen(fontScale: Float = 1f) {
        val viewModel = StatisticsViewModel(
            statisticsRepository = LocalOnlyStatisticsRepository(database.statisticsDao(), fakeClock)
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    StatisticsScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("الاستماع اليوم").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTargetHeight(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = bounds.bottom - bounds.top
        assertTrue("'$text' height ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun statsReportRealSeededData() {
        showScreen()

        composeRule.onAllNodesWithText("الاستماع اليوم").assertCountEquals(1)
        composeRule.onAllNodesWithText("الاستماع خلال الأسبوع").assertCountEquals(1)
        composeRule.onAllNodesWithText("الاستماع خلال الشهر").assertCountEquals(1)
        composeRule.onAllNodesWithText("25 د").assertCountEquals(3)
        composeRule.onAllNodesWithText("الكتب المكتملة").assertCountEquals(1)
        composeRule.onAllNodesWithText("الستريك (أيام متتالية)").assertCountEquals(1)
        composeRule.onAllNodesWithText("الفصول المكتملة").assertCountEquals(1)
        composeRule.onAllNodesWithText("2").assertCountEquals(1)
        composeRule.onAllNodesWithText("متوسط السرعة").assertCountEquals(1)
        composeRule.onAllNodesWithText("1.5×").assertCountEquals(1)
    }

    @Test
    fun backButtonMeetsMin48Dp() {
        showScreen()

        assertMinTouchTargetHeight("رجوع")
    }

    @Test
    fun enlargedTextKeepsHeaderInsideScreenWithoutClipping() {
        showScreen(fontScale = 2f)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (text in listOf("رجوع", "الإحصائيات", "الاستماع اليوم")) {
            val bounds = composeRule.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot()
            assertTrue("'$text' يعبر الحافة اليمنى", bounds.right <= root.right)
            assertTrue("'$text' يعبر الحافة اليسرى", bounds.left >= root.left)
        }
    }
}