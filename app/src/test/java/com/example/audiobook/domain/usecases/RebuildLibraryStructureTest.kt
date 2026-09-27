package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterCompletionEntity
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FavoriteBook
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SyncStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * «إعادة بناء بنية المكتبة» تحذف قشور الإصدارات الفارغة التي تركها تغيّر نموذج
 * التصنيف، فأي أثر مستخدم يجب أن يمنع الحذف. هذه الاختبارات تحرس ذلك الحد:
 * تقدّم استماع أو علامة أو إتمام فصل = القشرة تبقى، وإلا حُذفت مع كتابها اليتيم.
 */
@RunWith(RobolectricTestRunner::class)
class RebuildLibraryStructureTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var rebuild: RebuildLibraryStructure
    private lateinit var rootId: UUID

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        appSettings = AppSettings(context)
        rebuild = rebuildStructureFor(database, appSettings)
        val root = LibraryRootEntity(
            uri = "content://library",
            displayName = "Library",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        runBlocking {
            database.libraryRootDao().insert(root)
            rootId = root.id
        }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun emptyShellWithoutUserDataIsRemovedWithItsOrphanBook() = runBlocking {
        val book = insertBook("كتاب بلا أثر")
        insertEdition(book.id, "كتاب بلا أثر")

        val result = rebuild()

        assertEquals("قشرة فارغة بلا أثر مستخدم تُحذف", 1, result.shellsRemoved)
        assertEquals("ويُحذف كتابها اليتيم", 1, result.orphanBooksRemoved)
        assertEquals(0, database.editionDao().getByRoot(rootId).size)
        assertNull(database.bookDao().getById(book.id))
    }

    @Test
    fun shellWithListeningProgressSurvivesAndKeepsItsBook() = runBlocking {
        val book = insertBook("كتاب فيه تقدم")
        val edition = insertEdition(book.id, "كتاب فيه تقدم")
        database.progressDao().insert(
            ListeningProgressEntity(
                editionId = edition.id,
                currentPositionMs = 500L,
                lastPlayedAt = 1L,
                status = ProgressStatus.IN_PROGRESS,
                playbackSpeed = 1f,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )

        val result = rebuild()

        assertEquals("تقدّم الاستماع يمنع الحذف", 0, result.shellsRemoved)
        assertNotNull("الإصدار يبقى", database.editionDao().getById(edition.id))
        assertNotNull("والكتاب يبقى", database.bookDao().getById(book.id))
        assertNotNull("والتقدّم سليم", database.progressDao().getByParent(edition.id))
    }

    @Test
    fun shellWithCompletedChapterSurvives() = runBlocking {
        val book = insertBook("كتاب فصوله منجزة")
        val edition = insertEdition(book.id, "كتاب فصوله منجزة")
        val chapter = ChapterEntity(
            editionId = edition.id,
            title = "الفصل الأول",
            startPositionMs = 0L,
            orderIndex = 0,
            createdFrom = ChapterCreatedFrom.AUTO_SPLIT
        )
        database.chapterDao().insert(chapter)
        database.chapterCompletionDao().insert(
            ChapterCompletionEntity(
                chapterId = chapter.id,
                editionId = edition.id,
                completedAtMs = 42L
            )
        )

        val result = rebuild()

        assertEquals("إتمام الفصل أثر مستخدم فيمنع الحذف", 0, result.shellsRemoved)
        assertNotNull(database.editionDao().getById(edition.id))
        assertNotNull(database.bookDao().getById(book.id))
        assertNotNull("إتمام الفصل باقٍ", database.chapterCompletionDao().getByEdition(edition.id))
    }

    @Test
    fun shellOfFavoriteBookIsPrunedButFavoriteBookSurvivesWithValidDefault() = runBlocking {
        val book = insertBook("كتاب مفضّل")
        val edition = insertEdition(book.id, "كتاب مفضّل")
        database.bookDao().update(book.copy(defaultEditionId = edition.id))
        database.favoriteBookDao().insert(FavoriteBook(bookId = book.id, addedAt = 7L))

        val result = rebuild()

        // الكتاب المفضّل لا يُحذف أبدًا، لكن قشرته الفارغة تُنظَّف —
        // ويُمسح defaultEditionId حتى لا يشير إلى إصدار محذوف.
        assertEquals("قشرة المفضّل الفارغة تُنظَّف", 1, result.shellsRemoved)
        assertEquals("لكن الكتاب المفضّل لا يُحذف", 0, result.orphanBooksRemoved)
        val kept = database.bookDao().getById(book.id)
        assertNotNull("الكتاب المفضّل باقٍ", kept)
        assertNull("ولا يشير إلى إصدار محذوف", kept!!.defaultEditionId)
        assertNotNull("ويبقى في المفضّلة", database.favoriteBookDao().getById(book.id))
    }

    @Test
    fun shellOfUserConfirmedTitleSurvives() = runBlocking {
        val book = insertBook("عنوان أكّده المستخدم", confirmed = true)
        insertEdition(book.id, "عنوان أكّده المستخدم")

        val result = rebuild()

        assertEquals("تأكيد المستخدم للعنوان يمنع الحذف", 0, result.shellsRemoved)
        assertEquals(0, result.orphanBooksRemoved)
        assertNotNull(database.bookDao().getById(book.id))
    }

    @Test
    fun rebuildAlwaysRescansAfterPruning() = runBlocking {
        val book = insertBook("قشرة تُنظَّف")
        insertEdition(book.id, "قشرة تُنظَّف")

        val result = rebuild()

        // مصدر الملفات في هذا الاختبار فارغ فلا يجد الفحص شيئًا؛ المهم أنه
        // يجري بعد التنظيف لا بدلًا منه، ولا قشرة متبقية بعده.
        assertEquals(0, result.scan.filesSeen)
        assertEquals("لا قشرة بقيت", 0, database.editionDao().getAbandonedShells().size)
    }

    private suspend fun insertBook(title: String, confirmed: Boolean = false): BookEntity {
        val book = BookEntity(
            title = title,
            authorId = null,
            seriesId = null,
            orderInSeries = null,
            genre = null,
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            isTitleUserConfirmed = confirmed,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY,
            isDemo = false
        )
        database.bookDao().insert(book)
        return book
    }

    private suspend fun insertEdition(bookId: UUID, label: String): EditionEntity {
        val edition = EditionEntity(
            bookId = bookId,
            narratorName = null,
            label = label,
            totalDurationMs = 0L,
            fileFormat = "MP3",
            libraryRootId = rootId,
            sourceFolderPath = label,
            confidenceScore = 1f,
            isUserConfirmed = false,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.editionDao().insert(edition)
        return edition
    }
}
