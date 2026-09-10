package com.example.audiobook.presentation.accessibility

import android.app.Application
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsToggleable
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.example.audiobook.background.scanworker.ScanScheduler
import com.example.audiobook.data.repository.LocalOnlyLibraryRootRepository
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.presentation.libraryroots.LibraryRootsScreen
import com.example.audiobook.presentation.libraryroots.LibraryRootsViewModel
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import java.util.UUID
import kotlinx.coroutines.Dispatchers
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

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-480dpi")
class LibraryRootsScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var podcastsRoot: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
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
        val sounds = LibraryRootEntity(uri = "content://sounds", displayName = "Sounds", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val podcasts = LibraryRootEntity(uri = "content://podcasts", displayName = "Podcasts", isPriority = false, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(sounds)
                database.libraryRootDao().insert(podcasts)
            }
        }
        podcastsRoot = podcasts
    }

    private fun showScreen(fontScale: Float = 1f) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dao = database.libraryRootDao()
        val repository = LocalOnlyLibraryRootRepository(dao)
        val viewModel = LibraryRootsViewModel(
            application = Application(),
            repository = repository,
            rootDao = dao,
            scanScheduler = ScanScheduler(context, repository)
        )
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    LibraryRootsScreen(viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Sounds").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun labeledToggle(contentDescription: String) =
        composeRule.onAllNodes(
            useUnmergedTree = false,
            matcher = hasContentDescription(contentDescription) and hasClickAction()
        ).onFirst()

    @Test
    fun switchRowsExposeLabeledToggleSemantics() {
        showScreen()

        for (cd in listOf(
            "Priority toggle for Sounds",
            "Enabled toggle for Sounds",
            "Priority toggle for Podcasts",
            "Enabled toggle for Podcasts"
        )) {
            composeRule.onAllNodes(
                useUnmergedTree = false,
                matcher = hasContentDescription(cd) and hasClickAction()
            ).assertCountEquals(1)
            labeledToggle(cd).assertIsToggleable()
        }
    }

    @Test
    fun togglingPriorityReflectsInSemanticsAndDatabase() {
        showScreen()

        val podcastsPriority = labeledToggle("Priority toggle for Podcasts")
        podcastsPriority.assertIsOff()
        podcastsPriority.performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(
                useUnmergedTree = false,
                matcher = hasContentDescription("Priority toggle for Podcasts") and hasClickAction()
            ).onFirst().let { node ->
                runCatching { node.assertIsOn() }.isSuccess
            }
        }
        labeledToggle("Priority toggle for Podcasts").assertIsOn()
        val dbPriority = runBlocking {
            database.libraryRootDao().observeAll().first().first { it.id == podcastsRoot.id }.isPriority
        }
        assertTrue("قاعدة البيانات لم تسجل تغيير الأولوية", dbPriority)
    }

    @Test
    fun controlsMeetMin48Dp() {
        showScreen()

        val addBounds = composeRule.onNodeWithText("Add folder").getUnclippedBoundsInRoot()
        assertTrue("'Add folder' height (${addBounds.bottom - addBounds.top}) < 48dp", addBounds.bottom - addBounds.top >= 47.9.dp)
        val refreshBounds = composeRule.onAllNodesWithText("Refresh").onFirst().getUnclippedBoundsInRoot()
        assertTrue("'Refresh' height (${refreshBounds.bottom - refreshBounds.top}) < 48dp", refreshBounds.bottom - refreshBounds.top >= 47.9.dp)
        for (cd in listOf(
            "Priority toggle for Sounds",
            "Enabled toggle for Sounds",
            "Priority toggle for Podcasts",
            "Enabled toggle for Podcasts"
        )) {
            val bounds = labeledToggle(cd).getUnclippedBoundsInRoot()
            assertTrue("'$cd' height (${bounds.bottom - bounds.top}) < 48dp", bounds.bottom - bounds.top >= 47.9.dp)
        }
    }

    @Test
    fun enlargedTextKeepsLabelsAndRowsInsideScreenWithoutClipping() {
        showScreen(fontScale = 2f)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (text in listOf("Library folders", "Add folder", "Sounds", "Priority", "Enabled", "Refresh")) {
            val bounds = composeRule.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot()
            assertTrue("'$text' يعبر الحافة اليمنى", bounds.right <= root.right)
            assertTrue("'$text' يعبر الحافة اليسرى", bounds.left >= root.left)
        }
    }
}