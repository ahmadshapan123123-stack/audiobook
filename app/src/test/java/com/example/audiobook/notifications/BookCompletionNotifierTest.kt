package com.example.audiobook.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BookCompletionNotifierTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var notificationCenter: AtherNotificationCenter
    private lateinit var notifier: BookCompletionNotifier
    private lateinit var root: LibraryRootEntity
    private lateinit var book: BookEntity
    private lateinit var edition: EditionEntity

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        appSettings = AppSettings(context)
        notificationCenter = AtherNotificationCenter(context, appSettings)
        notifier = BookCompletionNotifier(database, appSettings, notificationCenter)
        root = LibraryRootEntity(
            uri = "content://root", displayName = "Books", isPriority = true, isEnabled = true,
            lastScanAt = null, scanStatus = ScanStatus.IDLE
        )
        val author = AuthorEntity(name = "المؤلف", colorTheme = null)
        book = BookEntity(
            title = "الكتاب", authorId = author.id, seriesId = null, orderInSeries = null, genre = null,
            coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
            defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        edition = EditionEntity(
            bookId = book.id, narratorName = "الراوي", label = "النسخة", totalDurationMs = 600000,
            fileFormat = "M4B", libraryRootId = root.id, sourceFolderPath = "/books",
            confidenceScore = 1f, isUserConfirmed = true, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.libraryRootDao().insert(root)
        database.authorDao().insert(author)
        database.bookDao().insert(book)
        database.editionDao().insert(edition)
        // تفعيل الإشعارات افتراضيًا
        appSettings.setNotificationsEnabled(true)
        appSettings.setBookCompletionNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
    }

    private fun notifications(): List<android.app.Notification> =
        shadowOf(
            ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(NotificationManager::class.java)
        ).allNotifications

    private fun insertProgress(status: ProgressStatus) = runBlocking {
        database.progressDao().insert(
            ListeningProgressEntity(
                id = UUID.randomUUID(), editionId = edition.id, currentPositionMs = if (status == ProgressStatus.FINISHED) edition.totalDurationMs else 1000,
                lastPlayedAt = System.currentTimeMillis(), status = status, playbackSpeed = 1f,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
    }

    @Test
    fun emitsCompletionNotificationOnFirstFinishedProgress() = runBlocking {
        insertProgress(ProgressStatus.FINISHED)

        notifier.onPlaybackEnded(edition.id)

        val posted = notifications()
        assertEquals(1, posted.size)
        assertEquals(NotificationChannels.ACHIEVEMENTS, posted.single().channelId)
    }

    @Test
    fun emitsNothingWhenBookStillInProgress() = runBlocking {
        insertProgress(ProgressStatus.IN_PROGRESS)

        notifier.onPlaybackEnded(edition.id)

        assertTrue(notifications().isEmpty())
    }

    @Test
    fun throttlesUntilTwentyFourHoursEvenWithRepeatedEndEvents() = runBlocking {
        insertProgress(ProgressStatus.FINISHED)

        notifier.onPlaybackEnded(edition.id)
        notifier.onPlaybackEnded(edition.id)

        assertEquals(1, notifications().size)
    }
}