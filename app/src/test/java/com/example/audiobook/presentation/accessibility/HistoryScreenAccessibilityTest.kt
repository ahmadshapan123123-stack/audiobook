package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.playback.SleepTimerClock
import com.example.audiobook.presentation.statistics.HistoryScreen
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
class HistoryScreenAccessibilityTest {

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
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seedSession(context: Context, title: String, startedAt: Long, durationMs: Long) {
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "راو", colorTheme = null)
        val series = SeriesEntity(authorId = author.id, name = "سلسلة", colorTheme = null)
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = title,
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
            sourceFolderPath = "/مصدر",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val session = ListeningSessionEntity(
            id = UUID.randomUUID(),
            editionId = edition.id,
            startedAt = startedAt,
            endedAt = startedAt + durationMs,
            durationListenedMs = durationMs,
            endReason = SessionEndReason.MANUAL_PAUSE,
            sessionState = SessionState.COMPLETED
        )
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
                database.listeningSessionDao().insert(session)
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
                    HistoryScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("السجل").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTargetHeight(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = bounds.bottom - bounds.top
        assertTrue("'$text' height ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun historyRowsShowSeededSessionsWithDurations() {
        seedSession(ApplicationProvider.getApplicationContext(), "أسطورة نوماد", nowMillis - 3_600_000L, 1_500_000L)
        showScreen()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("أسطورة نوماد").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("أسطورة نوماد").assertCountEquals(1)
        composeRule.onAllNodesWithText("25 د", substring = true).assertCountEquals(1)
        composeRule.onNodeWithText("رجوع").assertIsDisplayed()
    }

    @Test
    fun emptyStateWhenNoSessions() {
        showScreen()

        composeRule.onAllNodesWithText("لا توجد جلسات بعد").assertCountEquals(1)
        assertMinTouchTargetHeight("رجوع")
    }

    @Test
    fun enlargedTextKeepsRowsInsideScreenWithoutClipping() {
        seedSession(ApplicationProvider.getApplicationContext(), "أسطورة نوماد", nowMillis - 3_600_000L, 1_500_000L)
        showScreen(fontScale = 2f)

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("أسطورة نوماد").fetchSemanticsNodes().isNotEmpty()
        }
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (text in listOf("رجوع", "السجل", "أسطورة نوماد")) {
            val bounds = composeRule.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot()
            assertTrue("'$text' يعبر الحافة اليمنى", bounds.right <= root.right)
            assertTrue("'$text' يعبر الحافة اليسرى", bounds.left >= root.left)
        }
    }
}