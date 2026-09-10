package com.example.audiobook.presentation.reviewmatches

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.data.room.entity.UserDecision
import com.example.audiobook.domain.usecases.EditionMerge
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewMatchesViewModelTest {

    private lateinit var database: AppDatabase
    private lateinit var viewModel: ReviewMatchesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        viewModel = ReviewMatchesViewModel(database, EditionMerge(database))
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private data class Seed(
        val rootId: UUID,
        val mediumBookId: UUID,
        val mediumSubjectId: UUID,
        val mediumCandidateId: UUID,
        val lowBookId: UUID,
        val lowSubjectId: UUID,
        val lowCandidateId: UUID
    )

    private fun seedThreeCases(): Seed {
        val root = LibraryRootEntity(
            uri = "content://root",
            displayName = "Books",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        val author = AuthorEntity(name = "أحمد خالد توفيق", colorTheme = null)
        val seriesA = SeriesEntity(authorId = author.id, name = "السلسلة أ", colorTheme = null)
        val seriesB = SeriesEntity(authorId = author.id, name = "السلسلة ب", colorTheme = null)
        runBlocking {
            database.libraryRootDao().insert(root)
            database.authorDao().insert(author)
            database.seriesDao().insert(seriesA)
            database.seriesDao().insert(seriesB)
        }

        fun book(title: String, seriesId: UUID?): UUID {
            val book = BookEntity(
                id = UUID.randomUUID(),
                title = title,
                authorId = author.id,
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
            runBlocking { database.bookDao().insert(book) }
            return book.id
        }

        fun edition(bookId: UUID, label: String, confidence: Float, confirmed: Boolean): UUID {
            val edition = EditionEntity(
                id = UUID.randomUUID(),
                bookId = bookId,
                narratorName = null,
                label = label,
                totalDurationMs = 3_600_000L,
                fileFormat = "M4B",
                libraryRootId = root.id,
                sourceFolderPath = label,
                confidenceScore = confidence,
                isUserConfirmed = confirmed,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            runBlocking {
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
            }
            return edition.id
        }

        val highBook = book("كتاب عالٍ", seriesA.id)
        edition(highBook, "إصدار عالٍ محفوظ", 0.90f, confirmed = true)
        edition(highBook, "إصدار عالٍ", 0.92f, confirmed = true)

        val mediumBook = book("كتاب متوسط", seriesB.id)
        val mediumSubject = edition(mediumBook, "المجلد المتوسط", 0.55f, confirmed = false)
        val mediumCandidate = edition(mediumBook, "الإصدار المرجعي المتوسط", 0.90f, confirmed = false)

        val lowBook = book("كتاب منخفض", null)
        val lowSubject = edition(lowBook, "المجلد المنخفض", 0.25f, confirmed = false)
        val lowCandidate = edition(lowBook, "الإصدار المرجعي المنخفض", 0.90f, confirmed = false)

        val aloneBook = book("كتاب منفرد", null)
        edition(aloneBook, "المجلد المنفرد", 0.30f, confirmed = false)

        return Seed(
            rootId = root.id,
            mediumBookId = mediumBook,
            mediumSubjectId = mediumSubject,
            mediumCandidateId = mediumCandidate,
            lowBookId = lowBook,
            lowSubjectId = lowSubject,
            lowCandidateId = lowCandidate
        )
    }

    @Test
    fun showsOnlyMediumAndLowConfidenceCasesFromRealData() = runBlocking {
        seedThreeCases()

        val state = viewModel.load()

        assertEquals("الحالات المعروضة = المتوسطة + المنخفضة فقط", 2, state.cases.size)
        val subjects = state.cases.map { it.subjectEditionId }.toSet()

        val allEditions = database.editionDao().observeAll().first()
        val aloneEdition = allEditions.first { it.label == "المجلد المنفرد" }
        val highConfirmed = allEditions.first { it.label == "إصدار عالٍ محفوظ" }
        assertTrue("الإصدار المنفرد بلا شريك لا يُعرض", aloneEdition.id !in subjects)
        assertTrue("الثقة العالية المؤكدة لا تُعرض", highConfirmed.id !in subjects)

        assertEquals("الملفات = عدد حقيقي من قاعدة البيانات", 7, state.summary.files)
        assertEquals("الكتب = 4 كتب حقيقية", 4, state.summary.books)
        assertEquals("السلاسل = سلسلتان حقيقيتان", 2, state.summary.series)
        assertEquals("المؤلفون = مؤلف واحد حقيقي", 1, state.summary.authors)
        assertEquals("الحالات المشكوك فيها = 2", 2, state.summary.suspectCases)

        val mediumSubject = allEditions.first { it.label == "المجلد المتوسط" }
        val lowSubject = allEditions.first { it.label == "المجلد المنخفض" }
        assertEquals(setOf(mediumSubject.id, lowSubject.id), subjects)
        assertTrue("الإشارات المعروضة ليست خالية (تُبنى من البيانات المخزنة)", state.cases.all { it.subjectSignals.folderName.isNotBlank() })
    }

    @Test
    fun differentEditionWritesDecisionAndKeepsEditionsActuallySplitInDb() = runBlocking {
        val seed = seedThreeCases()

        viewModel.applyDifferentEdition(seed.mediumSubjectId, seed.mediumCandidateId)

        val decision = database.editionMatchDecisionDao().getAll().single()
        assertEquals(UserDecision.DIFFERENT_EDITION, decision.userDecision)
        assertEquals("مرجع صريح للإصدار المشكوك فيه", seed.mediumSubjectId, decision.subjectEditionId)
        assertEquals("مرجع صريح للإصدار المقارن", seed.mediumCandidateId, decision.comparedAgainstEditionId)
        assertTrue("لقطة الإشارات تحمل data الـsignals الحقيقية", decision.signalsSnapshot.contains("المجلد المتوسط"))

        val siblings = database.editionDao().getByParent(seed.mediumBookId)
        assertEquals("الإصداران يبقيان منفصلين فعليًا في قاعدة البيانات", 2, siblings.size)
        assertEquals(setOf(seed.mediumSubjectId, seed.mediumCandidateId), siblings.map { it.id }.toSet())
        assertTrue("قرار المستخدم نهائي (isUserConfirmed)", siblings.all { it.isUserConfirmed })

        val refreshed = viewModel.load()
        assertEquals("الحالة المحسوبة بعد القرار = المنخفضة فقط", 1, refreshed.cases.size)
        assertEquals(setOf(seed.lowSubjectId), refreshed.cases.map { it.subjectEditionId }.toSet())
        assertEquals("الحالات المشكوك فيها = 1", 1, refreshed.summary.suspectCases)
    }

    @Test
    fun sameEditionDecisionMergesThroughExistingEditionMerge() = runBlocking {
        val seed = seedThreeCases()

        viewModel.applySameEdition(seed.lowSubjectId, seed.lowCandidateId)

        val decision = database.editionMatchDecisionDao().getAll().single()
        assertEquals(UserDecision.SAME_EDITION, decision.userDecision)
        assertEquals(seed.lowSubjectId, decision.subjectEditionId)
        assertEquals(
            "قرار الدمج موثَّق دائمًا بهوية الناجي؛ مرجع المرشح يُصفَّر عبر FK SET_NULL " +
                "عند حذفه في الدمج الحالي (سلوك مخطط موجود مسبقًا في EditionMerge)",
            null,
            decision.comparedAgainstEditionId
        )

        val siblings = database.editionDao().getByParent(seed.lowBookId)
        assertEquals("دمج فعلي: إصدار واحد ناجٍ", 1, siblings.size)
        assertEquals(seed.lowSubjectId, siblings.single().id)
        assertTrue("User Override Wins: الناجي مؤكد", siblings.single().isUserConfirmed)
        assertEquals("ملفات المرشح انتقلت للناجي", 2, database.audioFileDao().getByParent(seed.lowSubjectId).size)
    }

    @Test
    fun notSameBookDecisionSplitsSubjectIntoItsOwnRealBook() = runBlocking {
        val seed = seedThreeCases()

        viewModel.applyNotSameBook(seed.lowSubjectId, seed.lowCandidateId)

        val decision = database.editionMatchDecisionDao().getAll().single()
        assertEquals(UserDecision.NOT_SAME_BOOK, decision.userDecision)
        assertEquals(seed.lowSubjectId, decision.subjectEditionId)
        assertEquals(seed.lowCandidateId, decision.comparedAgainstEditionId)

        val subjectEdition = database.editionDao().getById(seed.lowSubjectId)!!
        assertTrue("الإصدار المشكوك فيه انتقل لكتاب جديد مستقل", subjectEdition.bookId != seed.lowBookId)
        assertEquals("المرشح بقي في الكتاب الأصلي", seed.lowBookId, database.editionDao().getById(seed.lowCandidateId)?.bookId)
        assertTrue("الكتاب الجديد مستقل فعليًا", database.editionDao().getByParent(subjectEdition.bookId).single().id == seed.lowSubjectId)
        assertNotNull("الكتابان أصبحا كتابين", database.bookDao().getById(subjectEdition.bookId))
        assertTrue("المستخدم أكد القرار على الإصدارين", subjectEdition.isUserConfirmed && database.editionDao().getById(seed.lowCandidateId)!!.isUserConfirmed)
    }
}