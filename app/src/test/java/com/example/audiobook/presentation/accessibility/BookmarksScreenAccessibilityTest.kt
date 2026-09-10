package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.presentation.bookmarks.BookmarksScreen
import com.example.audiobook.presentation.bookmarks.BookmarksViewModel
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
class BookmarksScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var editionId: UUID
    private lateinit var bookmark: BookmarkEntity
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
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        val series = SeriesEntity(authorId = author.id, name = "سلسلة", colorTheme = null)
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = "أسطورة نلسون",
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
            sourceFolderPath = "/اسطورة",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val bm = BookmarkEntity(id = UUID.randomUUID(), editionId = edition.id, positionMs = 120_000L, createdAt = 1L, type = BookmarkType.BOOKMARK, noteText = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
                database.bookmarkDao().insert(bm)
            }
        }
        editionId = edition.id
        bookmark = bm
    }

    private fun showBookmarks() {
        val viewModel = BookmarksViewModel(
            savedStateHandle = SavedStateHandle(mapOf("editionId" to editionId.toString())),
            bookmarkDao = database.bookmarkDao(),
            chapterDao = database.chapterDao()
        )
        composeRule.setContent {
            AudiobookTheme(mode = AppThemeMode.LIGHT) {
                BookmarksScreen(controller = controller, onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("120s · Bookmark").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTargetHeight(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = bounds.bottom - bounds.top
        assertTrue("'$text' height ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun bookmarkActionsMeetMin48Dp() {
        showBookmarks()

        assertMinTouchTargetHeight("رجوع")
        assertMinTouchTargetHeight("انتقال")
        assertMinTouchTargetHeight("حذف")
    }

    @Test
    fun seekTargetMovesPlaybackAndDeleteRemovesRowFromSemantics() {
        showBookmarks()

        composeRule.onAllNodesWithText("120s · Bookmark").assertCountEquals(1)
        composeRule.onNodeWithText("انتقال").performClick()
        composeRule.waitForIdle()
        assertTrue("seekTo لم يصل إلى موضع الإشارة", controller.state.value.positionMs == 120_000L)

        composeRule.onNodeWithText("حذف").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("120s · Bookmark").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onAllNodesWithText("120s · Bookmark").assertCountEquals(0)
    }
}