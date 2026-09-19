package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.dao.ListeningSessionDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.PlaybackState
import com.example.audiobook.playback.SleepTimerClock
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.presentation.player.PlayerScreen
import com.example.audiobook.presentation.player.PlayerViewModel
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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

class FakePlaybackController : PlaybackController {
    private val _state = MutableStateFlow(PlaybackState(durationMs = 3_600_000L, positionMs = 60_000L))
    override val state: StateFlow<PlaybackState> = _state
    override fun play() { _state.value = _state.value.copy(isPlaying = true) }
    override fun pause() { _state.value = _state.value.copy(isPlaying = false) }
    override fun seekTo(positionMs: Long) { _state.value = _state.value.copy(positionMs = positionMs) }
    override fun skipForward15Seconds() { _state.value = _state.value.copy(positionMs = (_state.value.positionMs + 15_000L).coerceAtMost(_state.value.durationMs)) }
    override fun skipBack15Seconds() { _state.value = _state.value.copy(positionMs = (_state.value.positionMs - 15_000L).coerceAtLeast(0L)) }
    override suspend fun openEdition(editionId: UUID) {}
    override suspend fun previousChapter() {}
    override suspend fun nextChapter() {}
    override fun setSpeed(speed: Float) { _state.value = _state.value.copy(speed = speed) }
    override fun getVolume(): Float = 1f
    override fun setVolume(volume: Float) {}
    override fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?): Boolean = true
    override fun release() {}
}

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-480dpi")
class PlayerScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var editionId: UUID
    private lateinit var bookTitle: String
    private val controller = FakePlaybackController()

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
        val author = AuthorEntity(name = "طارق إبراهيم", colorTheme = null)
        val series = SeriesEntity(authorId = author.id, name = "سلسلة تجريبية", colorTheme = null)
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
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
            }
        }
        editionId = edition.id
        bookTitle = book.title
    }

    private fun showPlayer(fontScale: Float = 1f, marks: MarksCoordinator? = null) {
        val sleepTimer = SleepTimerController(
            clock = object : SleepTimerClock { override fun nowMillis(): Long = 0L },
            playback = controller,
            sessionDao = database.listeningSessionDao(),
            appSettings = AppSettings(ApplicationProvider.getApplicationContext())
        )
        val viewModel = PlayerViewModel(
            savedStateHandle = SavedStateHandle(mapOf("editionId" to editionId.toString())),
            editionDao = database.editionDao(),
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao()
        )
        composeRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    AudiobookTheme(mode = AppThemeMode.LIGHT) {
                        PlayerScreen(
                            controller = controller,
                            themeMode = AppThemeMode.LIGHT,
                            marks = marks,
                            sleepTimer = sleepTimer,
                            onBack = {},
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("تشغيل").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
    }

    private fun assertMinTouchTarget(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        assertTrue("'$text' height (${bounds.bottom - bounds.top}) < 48dp", bounds.bottom - bounds.top >= 47.9.dp)
    }

    private fun assertIconTouchTarget(contentDescription: String) {
        val bounds = composeRule.onNodeWithContentDescription(contentDescription).getUnclippedBoundsInRoot()
        assertTrue("icon '$contentDescription' height (${bounds.bottom - bounds.top}) < 48dp", bounds.bottom - bounds.top >= 47.9.dp)
        assertTrue("icon '$contentDescription' width (${bounds.right - bounds.left}) < 48dp", bounds.right - bounds.left >= 47.9.dp)
    }

    private fun assertCurrentChapterVisible() {
        composeRule.onAllNodes(hasText("الفصل 1", substring = true)).assertCountEquals(1)
    }

    private fun openChaptersPanel() {
        composeRule.onNodeWithContentDescription("الفصل الحالي").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun playerControlsMeetMin48DpTouchTarget() {
        showPlayer()

        assertIconTouchTarget("رجوع")

        assertCurrentChapterVisible()

        assertIconTouchTarget("السابق")
        assertIconTouchTarget("-15 ثا")
        assertIconTouchTarget("تشغيل")
        assertIconTouchTarget("+15 ثا")
        assertIconTouchTarget("التالي")

        assertMinTouchTarget("حفظ اللحظة")
        assertMinTouchTarget("السرعة")
        assertMinTouchTarget("النوم")

        openChaptersPanel()
        assertMinTouchTarget("نظرة عامة")
        assertMinTouchTarget("تكبير ٣٠–٦٠ دقيقة")
        assertMinTouchTarget("فصل جديد")
        assertMinTouchTarget("تحرير الفصول")
    }

    @Test
    fun transportTogglesPlayPause() {
        showPlayer()

        composeRule.onNodeWithContentDescription("تشغيل").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("إيقاف").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("تشغيل").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("إيقاف").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithContentDescription("تشغيل").assertCountEquals(1)
        composeRule.onAllNodesWithContentDescription("إيقاف").assertCountEquals(0)
    }

    @Test
    fun enlargedTextKeepsTitlesInsideScreenWithoutClipping() {
        showPlayer(fontScale = 2f)

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().right - composeRule.onRoot().getUnclippedBoundsInRoot().left

        val headerTitle = composeRule.onAllNodesWithText(bookTitle).onFirst().getUnclippedBoundsInRoot()
        assertTrue("cover title يعبر الحافة اليمنى", headerTitle.right <= rootWidth)

        val playButton = composeRule.onNodeWithContentDescription("تشغيل").getUnclippedBoundsInRoot()
        assertTrue("play button يعبر الحافة اليمنى", playButton.right <= rootWidth)

        val toolLabel = composeRule.onNodeWithText("السرعة").getUnclippedBoundsInRoot()
        assertTrue("tool label يعبر الحافة اليمنى", toolLabel.right <= rootWidth)
    }

    @Test
    fun saveMomentOpensIntentChooser() {
        showPlayer()

        composeRule.onNodeWithText("حفظ اللحظة").performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("حفظ اللحظة").assertCountEquals(2)
        composeRule.onNodeWithText("إشارة").assertIsDisplayed()
        composeRule.onNodeWithText("ملاحظة").assertIsDisplayed()
        composeRule.onNodeWithText("فصل").assertIsDisplayed()
    }

    private fun seedChapters() {
        runBlocking {
            withContext(Dispatchers.IO) {
                val durations = listOf(0L, 900_000L, 1_800_000L, 2_700_000L)
                durations.forEachIndexed { index, start ->
                    database.chapterDao().insert(
                        ChapterEntity(
                            editionId = editionId,
                            title = "فصل ${index + 1}",
                            startPositionMs = start,
                            orderIndex = index,
                            createdFrom = ChapterCreatedFrom.IMPORTED
                        )
                    )
                }
            }
        }
    }

    private fun chapterStarts(): List<Long> = runBlocking {
        database.chapterDao().getByParent(editionId).sortedBy { it.startPositionMs }.map { it.startPositionMs }
    }

    private fun handleXFor(fraction: Float, bounds: androidx.compose.ui.geometry.Rect): Float {
        val density = composeRule.density
        val inset = with(density) { 12.dp.toPx() }
        val span = bounds.width - inset * 2
        return bounds.right - inset - span * fraction
    }

    @Test
    fun draggingChapterHandleMovesChapterBoundary() {
        seedChapters()
        val marks = MarksCoordinator(database)
        showPlayer(marks = marks)

        val starts = chapterStarts()
        org.junit.Assert.assertEquals("chapters must be seeded", listOf(0L, 900_000L, 1_800_000L, 2_700_000L), starts)

        val node = composeRule.onNodeWithTag("chapter-handle-strip").fetchSemanticsNode()
        val strip = node.boundsInRoot
        val startX = handleXFor(0.25f, strip)
        val endX = handleXFor(0.65f, strip)
        val yMidPx = with(composeRule.density) { 24.dp.toPx() }
        println("TAGDEBUG strip=$strip startX=$startX endX=$endX y=$yMidPx")

        composeRule.onNodeWithTag("chapter-handle-strip").performTouchInput {
            val localStart = Offset(startX - strip.left, yMidPx)
            val localEnd = Offset(endX - strip.left, yMidPx)
            down(localStart)
            moveTo(localEnd, delayMillis = 50)
            moveTo(localEnd, delayMillis = 50)
            up()
        }

        composeRule.waitUntil(5_000) { runBlocking { chapterStarts() } != starts }
        val after = chapterStarts()
        org.junit.Assert.assertNotEquals("chapter boundary should move after handle drag", starts, after)
    }

    @Test
    fun sliderScrubCommitsOnceAndKeepsNewPosition() {
        showPlayer()
        composeRule.waitForIdle()

        val zone = composeRule.onNodeWithTag("seek-track-zone").fetchSemanticsNode().boundsInRoot
        val startX = handleXFor(controller.state.value.positionMs / 3_600_000f, zone)
        val endX = handleXFor(0.5f, zone)
        val yMidPx = (zone.top + zone.bottom) / 2 - zone.top

        composeRule.onNodeWithTag("seek-track-zone").performTouchInput {
            val localStart = Offset(startX - zone.left, yMidPx)
            val localEnd = Offset(endX - zone.left, yMidPx)
            swipe(localStart, localEnd, durationMillis = 600)
        }
        composeRule.waitForIdle()

        val posMs = controller.state.value.positionMs
        org.junit.Assert.assertTrue(
            "scrub should commit once to ~midpoint and stay (got $posMs, original was 60000)",
            kotlin.math.abs(posMs - 1_800_000L) < 300_000L
        )
    }
}