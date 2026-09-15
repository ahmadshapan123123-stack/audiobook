package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.EditionMerge
import com.example.audiobook.presentation.reviewmatches.ReviewMatchesScreen
import com.example.audiobook.presentation.reviewmatches.ReviewMatchesViewModel
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
class ReviewMatchesScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: AppDatabase
    private lateinit var viewModel: ReviewMatchesViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seed(context)
        viewModel = ReviewMatchesViewModel(database, EditionMerge(database))
    }

    @After
    fun tearDown() = database.close()

    /**
     * حالة داخل شاشة المراجعة (متوسطة الثقة) + حالة عالية الثقة مؤكدة (لا تُعرض)
     * حتى تُختبر "عرض المتوسطة/المنخفضة فقط" بنفس الدلالات.
     */
    private fun seed(context: Context) {
        val root = LibraryRootEntity(uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        val series = SeriesEntity(authorId = author.id, name = "سلسلة", colorTheme = null)
        runBlocking {
            withContext(Dispatchers.IO) {
                database.libraryRootDao().insert(root)
                database.authorDao().insert(author)
                database.seriesDao().insert(series)

                seedBookPair(root.id, author.id, series.id, "كتاب متوسط", "المجلد المتوسط", 0.55f, "الإصدار المرجعي المتوسط", 0.90f)
                seedBookPair(root.id, author.id, null, "كتاب عالٍ", "إصدار عالٍ محفوظ", 0.90f, "إصدار عالٍ", 0.92f, confirmed = true)
            }
        }
    }

    private suspend fun seedBookPair(
        rootId: UUID,
        authorId: UUID,
        seriesId: UUID?,
        bookTitle: String,
        subjectLabel: String,
        subjectConfidence: Float,
        candidateLabel: String,
        candidateConfidence: Float,
        confirmed: Boolean = false
    ) {
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = bookTitle,
            authorId = authorId,
            seriesId = seriesId,
            orderInSeries = null,
            genre = null,
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        insertEdition(rootId, book.id, subjectLabel, subjectConfidence, confirmed)
        insertEdition(rootId, book.id, candidateLabel, candidateConfidence, confirmed)
    }

    private suspend fun insertEdition(rootId: UUID, bookId: UUID, label: String, confidence: Float, confirmed: Boolean): UUID {
        val edition = EditionEntity(
            id = UUID.randomUUID(),
            bookId = bookId,
            narratorName = null,
            label = label,
            totalDurationMs = 3_600_000L,
            fileFormat = "M4B",
            libraryRootId = rootId,
            sourceFolderPath = label,
            confidenceScore = confidence,
            isUserConfirmed = confirmed,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.editionDao().insert(edition)
        database.audioFileDao().insert(
            AudioFileEntity(
                id = UUID.randomUUID(),
                editionId = edition.id,
                fileUri = "content://audio/$label",
                relativePath = "$label/1.m4b",
                fileName = "1.m4b",
                orderIndex = 0,
                durationMs = 1_800_000L,
                fileSizeBytes = 100,
                lastModified = 10,
                contentFingerprint = label,
                mimeType = "audio/mp4",
                fileStatus = FileStatus.AVAILABLE
            )
        )
        return edition.id
    }

    private fun showScreen() {
        composeRule.setContent {
            AudiobookTheme(mode = AppThemeMode.LIGHT) {
                ReviewMatchesScreen(onBack = {}, viewModel = viewModel)
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("مراجعة المطابقات").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTargetHeight(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = bounds.bottom - bounds.top
        assertTrue("'$text' height ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun reportsRealSummaryAndShowsOnlyMediumAndLowCases() {
        showScreen()

        composeRule.onAllNodesWithText("الملفات").assertCountEquals(1)
        composeRule.onAllNodesWithText("4").assertCountEquals(1)
        composeRule.onAllNodesWithText("2").assertCountEquals(1)
        composeRule.onAllNodesWithText("1").assertCountEquals(3)
        composeRule.onAllNodesWithText("الحالات المشكوك فيها").assertCountEquals(1)

        composeRule.onAllNodesWithText("المجلد المتوسط").assertCountEquals(1)
        composeRule.onAllNodesWithText("الثقة: 55٪").assertCountEquals(1)
        composeRule.onAllNodesWithText("الإصدار المرجعي المتوسط").assertCountEquals(1)
        composeRule.onAllNodesWithText("الثقة: 90٪").assertCountEquals(1)
        composeRule.onAllNodesWithText("المجلد: المجلد المتوسط").assertCountEquals(1)

        composeRule.onAllNodesWithText("إصدار عالٍ محفوظ").assertCountEquals(0)
        composeRule.onAllNodesWithText("إصدار عالٍ").assertCountEquals(0)
    }

    @Test
    fun decisionButtonsMeetMin48DpTouchTarget() {
        showScreen()

        assertMinTouchTargetHeight("نفس الإصدار")
        assertMinTouchTargetHeight("إصدار مختلف")
        assertMinTouchTargetHeight("ليس نفس الكتاب")
        assertMinTouchTargetHeight("رجوع")
    }

    @Test
    fun choosingSameEditionAppliesRealMergeAndRemovesCaseFromList() {
        showScreen()

        composeRule.onNodeWithText("نفس الإصدار").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("لا توجد حالات تحتاج مراجعة").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("المجلد المتوسط").assertCountEquals(0)
        composeRule.onAllNodesWithText("0").assertCountEquals(1)
    }
}