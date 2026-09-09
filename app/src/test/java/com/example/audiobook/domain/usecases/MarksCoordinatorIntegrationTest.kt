package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class MarksCoordinatorIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var coordinator: MarksCoordinator
    private val editionId = UUID.nameUUIDFromBytes("marks-integration-edition".toByteArray())

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).allowMainThreadQueries().build()
        val rootId = UUID.randomUUID()
        val authorId = UUID.randomUUID()
        val bookId = UUID.randomUUID()
        database.libraryRootDao().insert(LibraryRootEntity(rootId, "content://integration", "Integration", true, true, null, ScanStatus.IDLE))
        database.authorDao().insert(AuthorEntity(authorId, "Author", null))
        database.bookDao().insert(BookEntity(id = bookId, title = "Book", authorId = authorId, seriesId = null, orderInSeries = null, genre = null, coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false, isTitleUserConfirmed = false, defaultEditionId = editionId, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
        database.editionDao().insert(EditionEntity(id = editionId, bookId = bookId, narratorName = "Narrator", label = "Edition", totalDurationMs = 120_000, fileFormat = "M4B", libraryRootId = rootId, sourceFolderPath = "/book", confidenceScore = 1f, isUserConfirmed = true, isNarratorUserConfirmed = false, isLabelUserConfirmed = false, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
        coordinator = MarksCoordinator(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun markEditDeleteJumpAndReopenKeepOneSourceOfTruth() = runBlocking {
        coordinator.addBookmark(editionId, 12_345L)
        coordinator.addBookmark(editionId, 23_456L, "ملاحظة")
        coordinator.addChapter(editionId, 10_000L, "الأول")
        val secondChapterId = coordinator.addChapter(editionId, 30_000L, "الثالث")
        var chapters = database.chapterDao().getByParent(editionId)
        assertEquals(listOf(0, 1), chapters.map { it.orderIndex })

        coordinator.updateChapter(chapters.first { it.id == secondChapterId }, 20_000L, "الثاني")
        chapters = database.chapterDao().getByParent(editionId)
        assertEquals(listOf("الأول", "الثاني"), chapters.map { it.title })
        assertEquals(listOf(0, 1), chapters.map { it.orderIndex })

        val bookmark = database.bookmarkDao().getByParent(editionId).first()
        val controller = RecordingController()
        coordinator.seekToBookmark(controller, bookmark)
        assertEquals(12_345L, controller.lastSeek)

        coordinator.deleteChapter(chapters.first())
        assertEquals(1, database.chapterDao().getByParent(editionId).size)
        assertEquals(2, database.bookmarkDao().getByParent(editionId).size)

        database.close()
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME).allowMainThreadQueries().build()
        assertEquals(1, reopened.chapterDao().getByParent(editionId).size)
        assertEquals(2, reopened.bookmarkDao().getByParent(editionId).size)
        reopened.close()
    }

    private class RecordingController : PlaybackController {
        override val state: StateFlow<PlaybackState> = MutableStateFlow(PlaybackState())
        var lastSeek = -1L
        override suspend fun openEdition(editionId: UUID) = Unit
        override fun play() = Unit
        override fun pause() = Unit
        override fun seekTo(positionMs: Long) { lastSeek = positionMs }
        override fun skipForward15Seconds() = Unit
        override fun skipBack15Seconds() = Unit
        override suspend fun previousChapter() = Unit
        override suspend fun nextChapter() = Unit
        override fun setSpeed(speed: Float) = Unit
        override fun getVolume(): Float = 1f
        override fun setVolume(volume: Float) = Unit
        override fun release() = Unit
    }

    companion object { private const val DATABASE_NAME = "marks-integration.db" }
}