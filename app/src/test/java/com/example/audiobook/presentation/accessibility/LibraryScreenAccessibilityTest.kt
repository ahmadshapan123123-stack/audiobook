package com.example.audiobook.presentation.accessibility

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.presentation.library.LibraryScreen
import com.example.audiobook.presentation.library.LibraryViewModel
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
class LibraryScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
            }
        }
        seedBook(root.id, author.id, "ما وراء الطبيعة")
        seedBook(root.id, author.id, "موسم الهجرة إلى الشمال")
    }

    private fun seedBook(rootId: UUID, authorId: UUID, title: String) {
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = title,
            authorId = authorId,
            seriesId = null,
            orderInSeries = null,
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
            narratorName = "راوٍ",
            label = "إصدار $title",
            totalDurationMs = 3_600_000,
            fileFormat = "M4B",
            libraryRootId = rootId,
            sourceFolderPath = "/$title",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        runBlocking {
            withContext(Dispatchers.IO) {
                database.bookDao().insert(book)
                database.editionDao().insert(edition)
            }
        }
    }

    private fun showLibrary() {
        val viewModel = LibraryViewModel(
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            favoriteBookDao = database.favoriteBookDao(),
            collectionDao = database.collectionDao(),
            crossRefDao = database.collectionBookCrossRefDao()
        )
        composeRule.setContent {
            AudiobookTheme(mode = AppThemeMode.LIGHT) {
                LibraryScreen(
                    onBookSelected = {},
                    viewModel = viewModel
                )
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("عرض شبكي").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun iconButtonsExposeDescriptiveContentDescriptionsInSemanticsTree() {
        showLibrary()

        composeRule.onNodeWithContentDescription("عرض شبكي").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithContentDescription("عرض قائمة").assertIsDisplayed().assertHasClickAction()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("إضافة إلى المفضلة"))
        val favoriteButton = hasContentDescription("إضافة إلى المفضلة").and(hasClickAction())
        composeRule.onNode(useUnmergedTree = true, matcher = favoriteButton).assertIsDisplayed()
        composeRule.onAllNodes(useUnmergedTree = true, matcher = hasContentDescription("إزالة من المفضلة").and(hasClickAction())).assertCountEquals(0)

        composeRule.onNode(useUnmergedTree = true, matcher = favoriteButton).performClick()
        composeRule.waitForIdle()

        composeRule.onAllNodes(useUnmergedTree = true, matcher = hasContentDescription("إزالة من المفضلة").and(hasClickAction())).assertCountEquals(1)
        composeRule.onAllNodes(useUnmergedTree = true, matcher = favoriteButton).assertCountEquals(1)
        composeRule.onNodeWithContentDescription("عرض قائمة").assertIsDisplayed().assertHasClickAction()
    }

    @Test
    fun iconButtonsMeetMin48DpTouchTarget() {
        showLibrary()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription("إضافة إلى المفضلة"))

        val gridIcon = composeRule.onNodeWithContentDescription("عرض شبكي").getUnclippedBoundsInRoot()
        val listIcon = composeRule.onNodeWithContentDescription("عرض قائمة").getUnclippedBoundsInRoot()
        val favoriteIcon = composeRule.onNode(useUnmergedTree = true, matcher = hasContentDescription("إضافة إلى المفضلة").and(hasClickAction())).getUnclippedBoundsInRoot()

        assertTrue("عرض شبكي width < 48dp", gridIcon.right - gridIcon.left >= 48.dp)
        assertTrue("عرض شبكي height < 48dp", gridIcon.bottom - gridIcon.top >= 48.dp)
        assertTrue("عرض قائمة width < 48dp", listIcon.right - listIcon.left >= 48.dp)
        assertTrue("عرض قائمة height < 48dp", listIcon.bottom - listIcon.top >= 48.dp)
        assertTrue("المفضلة width < 48dp", favoriteIcon.right - favoriteIcon.left >= 48.dp)
        assertTrue("المفضلة height < 48dp", favoriteIcon.bottom - favoriteIcon.top >= 48.dp)

        composeRule.onNodeWithContentDescription("عرض قائمة").performClick()
        composeRule.waitForIdle()

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("ما وراء الطبيعة"))
        val listRow = composeRule.onNodeWithText("ما وراء الطبيعة").getUnclippedBoundsInRoot()
        assertTrue("قائمة row height < 48dp", listRow.bottom - listRow.top >= 48.dp)
        composeRule.onNodeWithText("ما وراء الطبيعة").assertHasClickAction()
    }

    @Test
    fun textButtonsChipsAndOutlinedButtonsMeetMin48Dp() {
        showLibrary()

        val history = composeRule.onNodeWithText("السجل").getUnclippedBoundsInRoot()
        val stats = composeRule.onNodeWithText("الإحصائيات").getUnclippedBoundsInRoot()
        val roots = composeRule.onNodeWithText("مجلدات المكتبة").getUnclippedBoundsInRoot()
        assertTrue("السجل height < 48dp", history.bottom - history.top >= 48.dp)
        assertTrue("الإحصائيات height < 48dp", stats.bottom - stats.top >= 48.dp)
        assertTrue("مجلدات المكتبة height < 48dp", roots.bottom - roots.top >= 48.dp)

        val sectionChip = composeRule.onNodeWithText("كل الكتب").getUnclippedBoundsInRoot()
        val statusChip = composeRule.onNodeWithText("الكل").getUnclippedBoundsInRoot()
        assertTrue("chip كل الكتب height < 48dp", sectionChip.bottom - sectionChip.top >= 48.dp)
        assertTrue("chip الكل height < 48dp", statusChip.bottom - statusChip.top >= 48.dp)
    }

    @Test
    fun layoutStaysSeparatedForEnlargedTextWithoutClipping() {
        var fontScale by mutableStateOf(2.0f)
        val viewModel = LibraryViewModel(
            bookDao = database.bookDao(),
            authorDao = database.authorDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            favoriteBookDao = database.favoriteBookDao(),
            collectionDao = database.collectionDao(),
            crossRefDao = database.collectionBookCrossRefDao()
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    LibraryScreen(onBookSelected = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("عرض شبكي").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("ما وراء الطبيعة"))
        composeRule.waitForIdle()

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val search = composeRule.onNodeWithText("ابحث في مكتبتك").getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("ما وراء الطبيعة", useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertTrue("search field ${search} outside root", search.left >= root.left && search.right <= root.right)
        assertTrue("book title missing from root bounds: top=${title.top} bottom=${title.bottom} root=${root}", title.top >= 0.dp && title.bottom <= root.bottom)
        assertTrue("title overlaps search field (flexible layout should place them apart)", title.top >= search.bottom)
    }
}