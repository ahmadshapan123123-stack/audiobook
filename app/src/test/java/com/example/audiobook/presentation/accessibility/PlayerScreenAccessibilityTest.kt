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
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.dao.ListeningSessionDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
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
import com.example.audiobook.presentation.player.PlayerScreen
import com.example.audiobook.presentation.player.PlayerViewModel
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

    private fun showPlayer(fontScale: Float = 1f) {
        val sleepTimer = SleepTimerController(
            clock = object : SleepTimerClock { override fun nowMillis(): Long = 0L },
            playback = controller,
            sessionDao = database.listeningSessionDao()
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
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    PlayerScreen(
                        controller = controller,
                        themeMode = AppThemeMode.LIGHT,
                        sleepTimer = sleepTimer,
                        onBack = {},
                        viewModel = viewModel
                    )
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.waitForIdle()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("نظرة عامة").fetchSemanticsNodes().isNotEmpty()
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

@Test
    fun playerControlsMeetMin48DpTouchTarget() {
        showPlayer()

        assertIconTouchTarget("رجوع")
        assertIconTouchTarget("المزيد")

        assertMinTouchTarget("نظرة عامة")
        assertMinTouchTarget("تكبير ٣٠–٦٠ دقيقة")
        assertMinTouchTarget("تحرير الفصول")

        assertMinTouchTarget("Mark")

        assertMinTouchTarget("السابق")
        assertMinTouchTarget("-15s")
        assertMinTouchTarget("تشغيل")
        assertMinTouchTarget("+15s")
        assertMinTouchTarget("التالي")

        assertMinTouchTarget("Sleep")

        composeRule.onNodeWithText("Mark").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Bookmark").assertCountEquals(1)
        composeRule.onAllNodesWithText("Chapter").assertCountEquals(1)
        assertMinTouchTarget("Bookmark")
        assertMinTouchTarget("Chapter")
    }

    @Test
    fun transportTogglesPlayAndMarksReveal() {
        showPlayer()

        composeRule.onNodeWithText("تشغيل").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("إيقاف").assertCountEquals(1)

        composeRule.onNodeWithText("Mark").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("Bookmark").assertCountEquals(1)
        composeRule.onAllNodesWithText("Chapter").assertCountEquals(1)

        composeRule.onNodeWithText("Bookmark").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("تم التقاط").assertCountEquals(0)
        composeRule.onAllNodesWithText("Bookmark").assertCountEquals(0)
        composeRule.onAllNodesWithText("Chapter").assertCountEquals(0)
    }

    @Test
    fun enlargedTextKeepsTitlesInsideScreenWithoutClipping() {
        showPlayer(fontScale = 2f)

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().right - composeRule.onRoot().getUnclippedBoundsInRoot().left

        val headerTitle = composeRule.onAllNodesWithText(bookTitle).onFirst().getUnclippedBoundsInRoot()
        assertTrue("cover title يعبر الحافة اليمنى", headerTitle.right <= rootWidth)

        val timelineLabel = composeRule.onNodeWithText("Timeline · الإصدار بالكامل").getUnclippedBoundsInRoot()
        assertTrue("timeline label يعبر الحافة اليمنى", timelineLabel.right <= rootWidth)

        val playButton = composeRule.onNodeWithText("تشغيل").getUnclippedBoundsInRoot()
        assertTrue("play button يعبر الحافة اليمنى", playButton.right <= rootWidth)
    }
}