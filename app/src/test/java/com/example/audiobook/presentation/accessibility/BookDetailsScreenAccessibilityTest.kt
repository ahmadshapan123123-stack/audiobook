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
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.EditionMerge
import com.example.audiobook.presentation.bookdetails.BookDetailsScreen
import com.example.audiobook.presentation.bookdetails.BookDetailsViewModel
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
class BookDetailsScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var book: BookEntity
    private lateinit var edition: EditionEntity

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
        val series = SeriesEntity(authorId = author.id, name = "ما وراء الطبيعة", colorTheme = null)
        val b = BookEntity(
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
        val e = EditionEntity(
            id = UUID.randomUUID(),
            bookId = b.id,
            narratorName = "راو",
            label = "إصدار نيلسون الأصلي",
            totalDurationMs = 3_600_000,
            fileFormat = "M4B",
            libraryRootId = root.id,
            sourceFolderPath = "/اسطورة",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        val bWithDefault = b.copy(defaultEditionId = e.id)
        val chapter = ChapterEntity(id = UUID.randomUUID(), editionId = e.id, title = "الفصل الأول", startPositionMs = 0L, orderIndex = 0, createdFrom = ChapterCreatedFrom.IMPORTED)
        val bookmark = BookmarkEntity(id = UUID.randomUUID(), editionId = e.id, positionMs = 120_000L, createdAt = 1L, type = BookmarkType.BOOKMARK, noteText = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        val progress = ListeningProgressEntity(id = UUID.randomUUID(), editionId = e.id, currentPositionMs = 1_800_000L, lastPlayedAt = 1L, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)
                database.bookDao().insert(bWithDefault)
                database.editionDao().insert(e)
                database.chapterDao().insert(chapter)
                database.bookmarkDao().insert(bookmark)
                database.progressDao().insert(progress)
            }
        }
        book = b
        edition = e
    }

    private fun showBookDetails(fontScale: Float = 1f) {
        val viewModel = BookDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to book.id.toString())),
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            editionDao = database.editionDao(),
            chapterDao = database.chapterDao(),
            bookmarkDao = database.bookmarkDao(),
            progressDao = database.progressDao(),
            editionMerge = EditionMerge(database)
        )
        composeRule.setContent {
            val density = Density(LocalDensity.current.density, fontScale = fontScale)
            CompositionLocalProvider(LocalDensity provides density) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    BookDetailsScreen(
                        onPlay = {},
                        onBookmarks = {},
                        viewModel = viewModel
                    )
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("مركز إدارة الكتاب").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTarget(text: String, axis: String = "height") {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = if (axis == "height") bounds.bottom - bounds.top else bounds.right - bounds.left
        assertTrue("'$text' $axis ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun interactiveTargetsMeetMin48DpAcrossAllTabs() {
        showBookDetails()

        assertMinTouchTarget("رجوع", "height")
        assertMinTouchTarget("تشغيل الإصدار الافتراضي", "height")
        assertMinTouchTarget("البيانات", "height")
        assertMinTouchTarget("الإصدارات", "height")
        assertMinTouchTarget("المحتوى", "height")
        assertMinTouchTarget("حفظ البيانات", "height")
        assertMinTouchTarget("اختيار غلاف يدوي", "height")
        assertMinTouchTarget("إعادة الاكتشاف", "height")

        composeRule.onNodeWithText("الإصدارات").performClick()
        composeRule.waitForIdle()
        assertMinTouchTarget("إعادة تسمية", "height")
        assertMinTouchTarget("تغيير الراوي", "height")
        assertMinTouchTarget("افتراضي ✓", "height")
        assertMinTouchTarget("فصل", "height")
        assertMinTouchTarget("إنشاء نسخة", "height")

        composeRule.onNodeWithText("المحتوى").performClick()
        composeRule.waitForIdle()
        assertMinTouchTarget("فتح شاشة Bookmarks", "height")
    }

    @Test
    fun tabChipsSwitchContentAndRevealRelatedElements() {
        showBookDetails()

        composeRule.onNodeWithText("البيانات").assertIsDisplayed()
        composeRule.onAllNodesWithText("حفظ البيانات").assertCountEquals(1)

        composeRule.onNodeWithText("الإصدارات").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("إعادة تسمية").assertCountEquals(1)
        composeRule.onAllNodesWithText("فصل").assertCountEquals(1)
        composeRule.onAllNodesWithText("حفظ البيانات").assertCountEquals(0)
        composeRule.onAllNodesWithText("فتح شاشة Bookmarks").assertCountEquals(0)

        composeRule.onNodeWithText("المحتوى").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("إعادة تسمية").assertCountEquals(0)
        composeRule.onAllNodesWithText("الفصول (0)").assertCountEquals(1)
        composeRule.onAllNodesWithText("Bookmarks و Notes (0)").assertCountEquals(1)
        composeRule.onAllNodesWithText("فتح شاشة Bookmarks").assertCountEquals(1)
    }

    @Test
    fun enlargedTextKeepsTitleInsideScreenWithoutClipping() {
        showBookDetails(fontScale = 2f)

        val backButton = composeRule.onNodeWithText("رجوع").getUnclippedBoundsInRoot()
        val headerTitle = composeRule.onAllNodesWithText("مركز إدارة الكتاب").onFirst().getUnclippedBoundsInRoot()
        assertTrue("header و back يتداخلان أفقياً", backButton.right <= headerTitle.left || headerTitle.right <= backButton.left)

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().right - composeRule.onRoot().getUnclippedBoundsInRoot().left
        assertTrue("header title يعبر الحافة اليمنى", headerTitle.right <= rootWidth)
        assertTrue("header title يعبر الحافة اليسرى", headerTitle.left >= (composeRule.onRoot().getUnclippedBoundsInRoot().left))

        val bookTitle = composeRule.onAllNodesWithText("أسطورة نلسون").onFirst().getUnclippedBoundsInRoot()
        assertTrue("book title يعبر الحافة اليمنى", bookTitle.right <= rootWidth)
        val headerBottom = maxOf(backButton.bottom, headerTitle.bottom)
        assertTrue("book title يتراكب مع الرأس", bookTitle.top >= headerBottom)
    }
}