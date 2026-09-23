package com.example.audiobook.presentation.entitydetails

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CollectionBookCrossRef
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FavoriteBook
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.libraryManagementFor
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * العمليات المدمّرة على المؤلف/السلسلة/المجموعة تلتقط لقطة قبل الحذف،
 * وتعرض سناكبار "تراجع" لنافذة 5 ثوانٍ، و undo() يستعيد الحالة كاملة
 * من دون فقدان أي كتاب/نسخة/فصل/تقدم.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EntityDetailsUndoTest {

    private lateinit var database: AppDatabase
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private suspend fun seedRoot(): LibraryRootEntity {
        val root = LibraryRootEntity(uri = "content://root", displayName = "المكتبة", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        database.libraryRootDao().insert(root)
        return root
    }

    private suspend fun seedAuthor(name: String): AuthorEntity {
        val author = AuthorEntity(name = name, colorTheme = null)
        database.authorDao().insert(author)
        return author
    }

    private suspend fun seedSeries(authorId: UUID, name: String): SeriesEntity {
        val series = SeriesEntity(authorId = authorId, name = name, colorTheme = null)
        database.seriesDao().insert(series)
        return series
    }

    /** كتاب كامل بعمود فني كامل: نسخة + ملف صوتي + فصل + تقدم + علامة + مجموعة + مفضّل. */
    private suspend fun seedFullBook(
        root: LibraryRootEntity,
        authorId: UUID,
        seriesId: UUID?,
        title: String
    ): Pair<BookEntity, EditionEntity> {
        val book = BookEntity(
            id = UUID.randomUUID(),
            title = title,
            authorId = authorId,
            seriesId = seriesId,
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
            narratorName = "راوٍ",
            label = "إصدار $title",
            totalDurationMs = 60_000,
            fileFormat = "M4B",
            libraryRootId = root.id,
            sourceFolderPath = "/$title",
            confidenceScore = 1f,
            isUserConfirmed = true,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        database.bookDao().insert(book)
        database.editionDao().insert(edition)
        database.audioFileDao().insert(
            AudioFileEntity(
                id = UUID.randomUUID(),
                editionId = edition.id,
                fileUri = "content://audio/${edition.id}.m4b",
                relativePath = "$title.m4b",
                fileName = "$title.m4b",
                orderIndex = 0,
                durationMs = 60_000,
                fileSizeBytes = 100,
                lastModified = 1,
                contentFingerprint = "100:1:content://$title",
                mimeType = "audio/mp4",
                fileStatus = FileStatus.AVAILABLE
            )
        )
        val chapter = ChapterEntity(
            id = UUID.randomUUID(),
            editionId = edition.id,
            title = "الفصل الأول",
            startPositionMs = 0,
            orderIndex = 0,
            createdFrom = ChapterCreatedFrom.IMPORTED
        )
        database.chapterDao().insert(chapter)
        database.chapterCompletionDao().insert(
            com.example.audiobook.data.room.entity.ChapterCompletionEntity(
                chapterId = chapter.id,
                editionId = edition.id,
                completedAtMs = 100
            )
        )
        database.bookmarkDao().insert(
            BookmarkEntity(
                id = UUID.randomUUID(),
                editionId = edition.id,
                positionMs = 5_000,
                createdAt = 5,
                type = BookmarkType.BOOKMARK,
                noteText = null,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        database.progressDao().insert(
            ListeningProgressEntity(
                id = UUID.randomUUID(),
                editionId = edition.id,
                currentPositionMs = 5_000,
                lastPlayedAt = 10,
                status = ProgressStatus.IN_PROGRESS,
                playbackSpeed = 1f,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
        )
        val collection = CollectionEntity(name = "قراءات", icon = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.collectionDao().insert(collection)
        database.collectionBookCrossRefDao().insert(CollectionBookCrossRef(collection.id, book.id))
        database.favoriteBookDao().insert(FavoriteBook(book.id, System.currentTimeMillis()))
        return book to edition
    }

    @Test
    fun deleteAuthorThenUndoRestoresAuthorWithAllBooksAndFullCascade() = runTest(dispatcher) {
        val root = seedRoot()
        val author = seedAuthor("الكاتب")
        val series = seedSeries(author.id, "السلسلة")
        val (book1, edition1) = seedFullBook(root, author.id, series.id, "الكتاب الأول")
        val (book2, edition2) = seedFullBook(root, author.id, null, "الكتاب الثاني")

        val viewModel = AuthorDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to author.id.toString())),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            bookDao = database.bookDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            management = libraryManagementFor(database)
        )

        viewModel.deleteAuthor()
        viewModel.messages.first { it != null }
        advanceUntilIdle()
        assertNull("بعد الحذف لا يوجد المؤلف", database.authorDao().getById(author.id))
        assertEquals("الكتب حُذفت بالمؤلف", 0, database.bookDao().getByParent(author.id).size)
        assertEquals("النسخ حُذفت", 0, database.editionDao().getByParent(book1.id).size + database.editionDao().getByParent(book2.id).size)

        viewModel.undo()
        viewModel.messages.first { it == null }
        advanceUntilIdle()
        assertEquals("المؤلف عاد", author.name, database.authorDao().getById(author.id)?.name)
        assertEquals("السلسلة عادت", series.id, database.seriesDao().getById(series.id)?.id)
        assertEquals("الكتب كلها عادت", 2, database.bookDao().getByParent(author.id).size)
        assertEquals("الكتاب داخل السلسلة عادت سلسلته", series.id, database.bookDao().getById(book1.id)?.seriesId)
        assertNotNull("نسخة الكتاب الأول عادت", database.editionDao().getById(edition1.id))
        assertNotNull("نسخة الكتاب الثاني عادت", database.editionDao().getById(edition2.id))
        assertEquals("الملفات الصوتية عادت", 1, database.audioFileDao().getByParent(edition1.id).size)
        assertEquals("الفصول عادت", 1, database.chapterDao().getByParent(edition1.id).size)
        assertEquals("اكتمال الفصول عاد", 1, database.chapterCompletionDao().getByEdition(edition1.id).size)
        assertEquals("العلامات عادت", 1, database.bookmarkDao().getByParent(edition1.id).size)
        assertNotNull("التقدم عاد", database.progressDao().getByParent(edition1.id))
        assertNotNull("عضوية المجموعة عادت", database.collectionBookCrossRefDao().getById(database.collectionDao().getByName("قراءات")!!.id, book1.id))
        assertNotNull("المفضلة عادت", database.favoriteBookDao().getById(book1.id))
    }

    @Test
    fun mergeAuthorsThenUndoRestoresBothAuthorsAndBookOwnership() = runTest(dispatcher) {
        val root = seedRoot()
        val source = seedAuthor("المؤلف الأول")
        val target = seedAuthor("المؤلف الثاني")
        val (book, _) = seedFullBook(root, source.id, null, "كتاب المصدر")

        val viewModel = AuthorDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to source.id.toString())),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            bookDao = database.bookDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            management = libraryManagementFor(database)
        )

        viewModel.mergeAuthors(target.id)
        viewModel.messages.first { it != null }
        advanceUntilIdle()
        assertNull("الدمج يزيل المصدر", database.authorDao().getById(source.id))
        assertEquals("الكتاب انتقل للهدف", target.id, database.bookDao().getById(book.id)?.authorId)

        viewModel.undo()
        viewModel.messages.first { it == null }
        advanceUntilIdle()
        assertNotNull("المؤلفان عادا", database.authorDao().getById(source.id))
        assertNotNull(database.authorDao().getById(target.id))
        assertEquals("الكتاب عاد للمؤلف المصدر", source.id, database.bookDao().getById(book.id)?.authorId)
        assertNotNull("نسخة الكتاب سليمة", database.editionDao().getByParent(book.id).firstOrNull())
    }

    @Test
    fun deleteSeriesThenUndoRestoresSeriesAndBookLinks() = runTest(dispatcher) {
        val root = seedRoot()
        val author = seedAuthor("كبير الكتاب")
        val series = seedSeries(author.id, "السلسلة الكبرى")
        val (book, _) = seedFullBook(root, author.id, series.id, "كتاب السلسلة")

        val viewModel = SeriesDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to series.id.toString())),
            seriesDao = database.seriesDao(),
            authorDao = database.authorDao(),
            bookDao = database.bookDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            management = libraryManagementFor(database)
        )

        viewModel.deleteSeries()
        viewModel.messages.first { it != null }
        advanceUntilIdle()
        assertNull("السلسلة حُذفت", database.seriesDao().getById(series.id))
        assertNull("الكتاب انفصل عنها", database.bookDao().getById(book.id)?.seriesId)
        assertNotNull("الكتاب نفسه لم يُحذف", database.bookDao().getById(book.id))

        viewModel.undo()
        viewModel.messages.first { it == null }
        advanceUntilIdle()
        assertEquals("السلسلة عادت", series.name, database.seriesDao().getById(series.id)?.name)
        assertEquals("ربط الكتاب عاد", series.id, database.bookDao().getById(book.id)?.seriesId)
    }

    @Test
    fun deleteCollectionThenUndoRestoresCollectionAndMembership() = runTest(dispatcher) {
        val root = seedRoot()
        val author = seedAuthor("مؤلف المجموعة")
        val (book, _) = seedFullBook(root, author.id, null, "كتاب في مجموعة")
        val collection = CollectionEntity(name = "مجموعة محذوفة", icon = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.collectionDao().insert(collection)
        database.collectionBookCrossRefDao().insert(CollectionBookCrossRef(collection.id, book.id))

        val viewModel = CollectionDetailsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("id" to collection.id.toString())),
            collectionDao = database.collectionDao(),
            crossRefDao = database.collectionBookCrossRefDao(),
            authorDao = database.authorDao(),
            seriesDao = database.seriesDao(),
            bookDao = database.bookDao(),
            editionDao = database.editionDao(),
            progressDao = database.progressDao(),
            management = libraryManagementFor(database)
        )

        viewModel.deleteCollection()
        viewModel.messages.first { it != null }
        advanceUntilIdle()
        assertNull("المجموعة حُذفت", database.collectionDao().getById(collection.id))
        assertNull("العضوية حُذفت", database.collectionBookCrossRefDao().getById(collection.id, book.id))
        assertNotNull("الكتاب باقٍ", database.bookDao().getById(book.id))

        viewModel.undo()
        viewModel.messages.first { it == null }
        advanceUntilIdle()
        assertEquals("المجموعة عادت", "مجموعة محذوفة", database.collectionDao().getById(collection.id)?.name)
        assertNotNull("العضوية عادت", database.collectionBookCrossRefDao().getById(collection.id, book.id))
    }
}