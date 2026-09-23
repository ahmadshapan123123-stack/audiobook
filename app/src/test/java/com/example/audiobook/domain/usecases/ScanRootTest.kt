package com.example.audiobook.domain.usecases

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ScanRootTest {
    private lateinit var database: AppDatabase
    private lateinit var source: FakeFileSource
    private lateinit var reader: CountingMetadataReader
    private lateinit var scanRoot: ScanRoot
    private lateinit var appSettings: AppSettings
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = FakeFileSource()
        reader = CountingMetadataReader()
        appSettings = AppSettings(context)
        scanRoot = ScanRoot(database, source, reader, appSettings, EditionMerge(database))
        root = LibraryRootEntity(uri = "content://library", displayName = "Library", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    private suspend fun allEditions(): List<EditionEntity> = database.editionDao().observeAll().first()

    @Test
    fun repeatedScanUsesCacheThenMissingAndRestorePreserveListeningData() = runBlocking {
        val file = ScanFile(Uri.parse("content://audio/1.m4b"), "Book/Part.m4b", "Book", "Part.m4b", 100, 10)
        source.files = listOf(file)

        val first = scanRoot(root.id)
        val second = scanRoot(root.id)
        assertEquals(1, first.metadataReads)
        assertEquals(0, first.cacheHits)
        assertEquals(0, second.metadataReads)
        assertEquals(1, second.cacheHits)
        assertEquals(1, reader.readCount)

        val edition = database.editionDao().getByRootAndFolder(root.id, "Book")!!
        val bookmark = BookmarkEntity(editionId = edition.id, positionMs = 50, createdAt = 1, type = BookmarkType.NOTE, noteText = "Keep", remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        val progress = ListeningProgressEntity(editionId = edition.id, currentPositionMs = 50, lastPlayedAt = 1, status = ProgressStatus.IN_PROGRESS, playbackSpeed = 1f, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
        database.bookmarkDao().insert(bookmark)
        database.progressDao().insert(progress)

        source.files = emptyList()
        val missing = scanRoot(root.id)
        assertEquals(1, missing.missingMarked)
        assertEquals(FileStatus.MISSING, database.audioFileDao().getByUri(file.uri.toString())?.fileStatus)
        assertNotNull(database.editionDao().getById(edition.id))
        assertNotNull(database.bookmarkDao().getById(bookmark.id))
        assertNotNull(database.progressDao().getByParent(edition.id))

        source.files = listOf(file)
        val restored = scanRoot(root.id)
        assertEquals(1, restored.restored)
        assertEquals(0, restored.metadataReads)
        assertEquals(FileStatus.AVAILABLE, database.audioFileDao().getByUri(file.uri.toString())?.fileStatus)
        assertNotNull(database.bookmarkDao().getById(bookmark.id))
        assertNotNull(database.progressDao().getByParent(edition.id))
        assertEquals(1, reader.readCount)
    }

    // ---- R1: بنية أحمد خالد توفيق — مؤلف تحته 3 مجلدات تحمل صوتًا مباشرًا ----
    // كل مجلد يحوي ملفات = كتاب مستقل؛ لا يُدمج أي منها مع الأخريين أبدًا.

    @Test
    fun authorWithSeriesFoldersProducesSeparateBooksAndNeverOneMergedBook() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/01.mp3"), "أحمد خالد توفيق/فانتازيا/01.mp3", "أحمد خالد توفيق/فانتازيا", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/02.mp3"), "أحمد خالد توفيق/فانتازيا/02.mp3", "أحمد خالد توفيق/فانتازيا", "02.mp3", 200, 10),
            ScanFile(Uri.parse("content://audio/03.mp3"), "أحمد خالد توفيق/ما وراء الطبيعة/01.mp3", "أحمد خالد توفيق/ما وراء الطبيعة", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/04.mp3"), "أحمد خالد توفيق/سافاري/01.mp3", "أحمد خالد توفيق/سافاري", "01.mp3", 100, 10)
        )

        val report = scanRoot(root.id)

        assertEquals("ثلاثة مجلدات تحمل صوتًا → ثلاثة كتب، لا كتاب واحد مدمج", 3, report.editionsCreated)
        assertEquals("لا دمج تلقائي بين سلسلات مختلفة", 0, report.editionsAutoMerged)
        assertEquals(3, allEditions().size)
        val books = database.bookDao().getAll()
        assertEquals("مجلد واحد ← كتاب واحد", 3, books.size)
        books.forEach { assertEquals("المؤلف = مجلد المستوى الأعلى", "أحمد خالد توفيق", database.authorDao().getById(it.authorId)?.name) }

        val fantasy = database.editionDao().getByRootAndFolder(root.id, "أحمد خالد توفيق/فانتازيا")!!
        assertEquals("ملفات فانتازيا كلها في كتاب فانتازيا", 2, database.audioFileDao().getByParent(fantasy.id).size)
        assertEquals("فانتازيا", database.bookDao().getById(fantasy.bookId)?.title)
        assertEquals("ما وراء الطبيعة", database.bookDao().getById(database.editionDao().getByRootAndFolder(root.id, "أحمد خالد توفيق/ما وراء الطبيعة")!!.bookId)?.title)
        assertEquals("سافاري", database.bookDao().getById(database.editionDao().getByRootAndFolder(root.id, "أحمد خالد توفيق/سافاري")!!.bookId)?.title)
    }

    // ---- R2: مجلدات مسطّحة بلا وسيط — كل مجلد مستقل = كتاب ----

    @Test
    fun flatBookFoldersEachBecomeTheirOwnBook() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/a.mp3"), "كتاب واحد/01.mp3", "كتاب واحد", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/b.mp3"), "كتاب ثان/01.mp3", "كتاب ثان", "01.mp3", 100, 10)
        )

        val report = scanRoot(root.id)

        assertEquals(2, report.editionsCreated)
        assertEquals(0, report.editionsAutoMerged)
        assertEquals("مجلد مسطح واحد = كتاب واحد", 2, database.bookDao().getAll().size)
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "كتاب واحد"))
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "كتاب ثان"))
    }

    // ---- R3: مجلد مختلط (صوت مباشر + مجلد فرعي) → كتاب + كتاب فرعي بلا دمج ----

    @Test
    fun mixedFolderBecomesBookAndItsSubfoldersBecomeSeparateBooks() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/c.mp3"), "كتاب رئيسي/01.mp3", "كتاب رئيسي", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/d.mp3"), "كتاب رئيسي/جزء فرعي/02.mp3", "كتاب رئيسي/جزء فرعي", "02.mp3", 100, 10)
        )

        val report = scanRoot(root.id)

        assertEquals("المجلد المختلط نفسه + مجلده الفرعي = كتابان", 2, report.editionsCreated)
        assertEquals(0, report.editionsAutoMerged)
        assertEquals(2, allEditions().size)
        val main = database.editionDao().getByRootAndFolder(root.id, "كتاب رئيسي")!!
        assertEquals(1, database.audioFileDao().getByParent(main.id).size)
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "كتاب رئيسي/جزء فرعي"))
    }

    // ---- R4: إعادة الفحص (نفس الجذر) لا تكرر الملفات أبدًا ----

    @Test
    fun rescanSameRootTwiceNeverDuplicatesFiles() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/x.mp3"), "أحمد خالد توفيق/فانتازيا/01.mp3", "أحمد خالد توفيق/فانتازيا", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/y.mp3"), "أحمد خالد توفيق/فانتازيا/02.mp3", "أحمد خالد توفيق/فانتازيا", "02.mp3", 200, 10)
        )

        scanRoot(root.id)
        val afterFirst = database.audioFileDao().observeAll().first().size
        scanRoot(root.id)
        val afterSecond = database.audioFileDao().observeAll().first().size

        assertEquals("مرّتان فوق نفس الجذر → نفس عدد الملفات تمامًا", afterFirst, afterSecond)
        assertEquals(2, afterSecond)
    }

    // ---- R5: إعادة الفحص بعد إعادة تسمية مجلد → القديم يُعلَّم مفقودًا والجديد يُنشأ ----

    @Test
    fun rescanAfterRenamingFolderMarksOldMissingAndCreatesNewEdition() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/z.mp3"), "اسم قديم/01.mp3", "اسم قديم", "01.mp3", 100, 10)
        )
        scanRoot(root.id)
        val oldEdition = database.editionDao().getByRootAndFolder(root.id, "اسم قديم")!!

        source.files = listOf(
            ScanFile(Uri.parse("content://audio/z.mp3"), "اسم جديد/01.mp3", "اسم جديد", "01.mp3", 100, 10)
        )
        val report = scanRoot(root.id)

        assertEquals("المجلد الجديد يُنشئ إصدارًا جديدًا", 1, report.editionsCreated)
        val newEdition = database.editionDao().getByRootAndFolder(root.id, "اسم جديد")!!
        assertEquals("الملف الفيزيائي نفسه يُعاد توجيهه للإصدار الجديد (لا نسخة مكررة)", listOf(newEdition.id), database.audioFileDao().getByRoot(root.id).map { it.editionId })
        assertEquals("الإصدار القديم يبقى قشرة فارغة محفوظة — لا يختفي تقدم المستمع", 0, database.audioFileDao().getByParent(oldEdition.id).size)
        assertEquals("الكتاب القديم يبقى بلا حذف (تقدمه محفوظ)", oldEdition.bookId, database.bookDao().getById(oldEdition.bookId)?.id)
        assertNull("لا ملف مفقود هنا — الملف الفيزيائي ما زال في المكتبة تحت المسار الجديد", database.audioFileDao().getByRoot(root.id).firstOrNull { it.fileStatus == FileStatus.MISSING })
    }

    // ---- P2: الإصدار يُنشأ بثقة حقيقية (وليست الثابت 1f) وإشارات فعلية ----

    @Test
    fun editionIsCreatedWithRealConfidenceAndSignals() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_800_000L, "audio/mp4", null, "فلان الراوي", "سيرة", emptyList())
        source.files = listOf(ScanFile(Uri.parse("content://audio/a.m4b"), "السيرة النبوية/Part 1.m4b", "السيرة النبوية", "Part 1.m4b", 100, 10))

        val report = scanRoot(root.id)

        assertEquals(1, report.editionsCreated)
        val edition = database.editionDao().getByRootAndFolder(root.id, "السيرة النبوية")!!
        assertEquals("فلان الراوي", edition.narratorName)
        assertEquals("السيرة النبوية", edition.label)
        assertEquals(1_800_000L, edition.totalDurationMs)
        assertEquals("M4B", edition.fileFormat)
        assertTrue("الثقة حقيقية في المدى [0,1] وليست واحدًا ثابتًا", edition.confidenceScore in 0f..1f)
        assertEquals("ثقة حقيقية محسوبة من الإشارات (0.30 راوٍ + 0.15 مجلد + 0.10 سلسلة + 0.10 مدة + 0.10 ملفات + 0.05 مؤلف)", 0.70f, edition.confidenceScore, 0.001f)
        assertEquals("السيرة النبوية", database.bookDao().getById(edition.bookId)?.title)
    }

    // ---- P4: القيد الصارم فعلي في الفحص الكامل — لا دمج صامت لراويين مختلفين ----

    @Test
    fun scanNeverAutoMergesFoldersWithClearlyDifferentNarrators() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", null, null, emptyList())
        reader.metadataByUri["content://audio/1.m4b"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Ali", null, emptyList())
        reader.metadataByUri["content://audio/2.m4b"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Omar", null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.m4b"), "Book A/Intro.m4b", "Book A", "Intro.m4b", 100, 10),
            ScanFile(Uri.parse("content://audio/2.m4b"), "Book B/Intro.m4b", "Book B", "Intro.m4b", 100, 10)
        )
        appSettings.setIntelligenceLevel(IntelligenceLevel.AGGRESSIVE)

        val report = scanRoot(root.id)

        assertEquals(0, report.editionsAutoMerged)
        assertEquals(2, allEditions().size)
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "Book A"))
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "Book B"))
    }

    // ---- P5: دمج تلقائي حقيقي يسجل EditionMatchDecision بمرجعين صريحين ----

    @Test
    fun balancedScanAutoMergesSameBookAcrossTwoFoldersAndRecordsDecision() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Same Narrator", null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.m4b"), "Book v1/Part 1.m4b", "Book v1", "Part 1.m4b", 100, 10),
            ScanFile(Uri.parse("content://audio/2.m4b"), "Book v2/Part 1.m4b", "Book v2", "Part 1.m4b", 100, 10)
        )
        appSettings.setIntelligenceLevel(IntelligenceLevel.BALANCED)

        val report = scanRoot(root.id)

        assertEquals("يجب إنشاء إصدارين قبل الدمج ثم يدمج أحدهما", 2, report.editionsCreated)
        assertEquals(1, report.editionsAutoMerged)
        val editions = allEditions()
        assertEquals("في نهاية الفحص يبقى إصدار واحد بعد الدمج", 1, editions.size)
        val survivor = editions.first()
        assertEquals(2, database.audioFileDao().getByParent(survivor.id).size)
        val decisions = database.editionMatchDecisionDao().getByParent(survivor.id)
        assertEquals(1, decisions.size)
        assertEquals("المرجع الصريح للإصدار المحتفظ به يبقى حتى بعد حذف المرشح", survivor.id, decisions[0].subjectEditionId)
        assertNull("المرشح حُذف بعد الدمج فيُخلي FK ب SET_NULL — والباقي في subjectEditionId + لقطة النمط", decisions[0].comparedAgainstEditionId)
        assertNotNull("لقطة الإشارات تحمل مفتاح النمط لإعادة الملاءمة/المراجعة لاحقًا", EditionSignalsCodec.parsePairPatternKey(decisions[0].signalsSnapshot))
        assertEquals(1, database.bookDao().getAll().size)
    }

    // ---- P5: القرارات السابقة تعدّل الأوزان فعليًا — تأكيدان سابقان يرفعان زوجًا هامشيًا ----

    @Test
    fun priorSameEditionConfirmationsBoostMarginalPairAcrossThreshold() = runBlocking {
        val uriA = "content://audio/1.m4b"
        val uriB = "content://audio/2.mp3"
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", null, null, null, emptyList())
        reader.metadataByUri[uriA] = AudioMetadata(1_000_000L, "audio/mp4", null, "Rawi", null, emptyList())
        reader.metadataByUri[uriB] = AudioMetadata(1_000_000L, "audio/mp3", null, "Rawi", null, emptyList())
        val fileA = ScanFile(Uri.parse(uriA), "Book/Book.m4b", "Book", "Book.m4b", 100, 10)
        val fileB = ScanFile(Uri.parse(uriB), "book/book.mp3", "book", "book.mp3", 100, 10)
        source.files = listOf(fileA, fileB)
        appSettings.setIntelligenceLevel(IntelligenceLevel.BALANCED)

        val first = scanRoot(root.id)
        assertEquals("بدون قرارات سابقة الزوج هامشي ولا يدمج", 0, first.editionsAutoMerged)
        assertEquals(2, allEditions().size)

        val editionA = database.editionDao().getByRootAndFolder(root.id, "Book")!!
        val editionB = database.editionDao().getByRootAndFolder(root.id, "book")!!
        val signalsA = EditionSignalExtractor.build("Book", root.displayName, listOf("Book.m4b"), listOf(AudioMetadata(1_000_000L, "audio/mp4", null, "Rawi", null, emptyList())))
        val signalsB = EditionSignalExtractor.build("book", root.displayName, listOf("book.mp3"), listOf(AudioMetadata(1_000_000L, "audio/mp3", null, "Rawi", null, emptyList())))
        val base = EditionIntelligence.mergeConfidence(signalsA, signalsB)
        assertTrue("الزوج هامشي فعليًا تحت العتبة (متوقع 0.80)", base < EditionIntelligence.BALANCED_AUTO_MERGE_THRESHOLD)

        fun seedConfirmation() = com.example.audiobook.data.room.entity.EditionMatchDecisionEntity(
            id = UUID.randomUUID(),
            subjectEditionId = editionA.id,
            comparedAgainstEditionId = editionB.id,
            signalsSnapshot = EditionSignalsCodec.toJson(signalsA, signalsB),
            userDecision = UserDecision.SAME_EDITION,
            createdAt = System.currentTimeMillis()
        )

        // تأكيد واحد: 0.80 * 1.05 = 0.84 → ما زال تحت 0.85.
        database.editionMatchDecisionDao().insert(seedConfirmation())
        source.files = listOf(fileA.copy(lastModified = 11), fileB.copy(lastModified = 11))
        val second = scanRoot(root.id)
        assertEquals("تأكيد سابق واحد يرفع 0.80 إلى 0.84 — لا يكفي للعتبة", 0, second.editionsAutoMerged)

        // تأكيدان: 0.80 * 1.10 = 0.88 ≥ 0.85 → تُدمج تلقائيًا.
        database.editionMatchDecisionDao().insert(seedConfirmation())
        source.files = listOf(fileA.copy(lastModified = 12), fileB.copy(lastModified = 12))
        val third = scanRoot(root.id)
        assertEquals("تأكيدان سابقان يرفعان 0.80 إلى 0.88 ≥ 0.85 فيُدمج", 1, third.editionsAutoMerged)
        assertEquals(1, allEditions().size)
    }

    // ---- P6: User Override Wins — التعديل اليدوي لا يُمس ثم Reset Metadata يعيد الاكتشاف ----

    @Test
    fun manualTitleEditSurvivesRescanAndResetMetadataAllowsRediscovery() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", "Detected Title", null, null, emptyList())
        val file = ScanFile(Uri.parse("content://audio/1.m4b"), "Folder/Part.m4b", "Folder", "Part.m4b", 100, 10)
        source.files = listOf(file)

        scanRoot(root.id)
        val edition = database.editionDao().getByRootAndFolder(root.id, "Folder")!!
        val book = database.bookDao().getById(edition.bookId)!!
        assertEquals("Detected Title", book.title)

        database.bookDao().update(book.copy(title = "My Custom Title", isTitleUserConfirmed = true))
        database.editionDao().update(edition.copy(narratorName = "My Narrator", isNarratorUserConfirmed = true))

        source.files = listOf(file.copy(lastModified = 11))
        scanRoot(root.id)

        val afterScan = database.bookDao().getById(book.id)!!
        val editionAfterScan = database.editionDao().getById(edition.id)!!
        assertEquals("العنوان اليدوي لا يُكتب فوقه إطلاقًا", "My Custom Title", afterScan.title)
        assertEquals("الراوي اليدوي لا يُكتب فوقه إطلاقًا", "My Narrator", editionAfterScan.narratorName)
        assertTrue(afterScan.isTitleUserConfirmed)
        assertTrue(editionAfterScan.isNarratorUserConfirmed)

        database.bookDao().update(BookDetailsManagement().resetMetadata(afterScan))
        database.editionDao().update(BookDetailsManagement().resetEditionMetadata(editionAfterScan))

        source.files = listOf(file.copy(lastModified = 12))
        scanRoot(root.id)

        val afterReset = database.bookDao().getById(book.id)!!
        val editionAfterReset = database.editionDao().getById(edition.id)!!
        assertEquals("Reset Metadata يتجاهل الأعلام فيُعاد الاكتشاف من الإشارات", "Detected Title", afterReset.title)
        assertNull("راوي المستخدم يُمسح مع Reset فتُعاد قراءته من الإشارات", editionAfterReset.narratorName)
    }

    // ---- P3: مستوى الذكاء يقرأ فعليًا من الإعدادات ----

    @Test
    fun conservedScanNeverAutoMergesEvenIdenticalEditions() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Same Narrator", null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.m4b"), "Book v1/Part 1.m4b", "Book v1", "Part 1.m4b", 100, 10),
            ScanFile(Uri.parse("content://audio/2.m4b"), "Book v2/Part 1.m4b", "Book v2", "Part 1.m4b", 100, 10)
        )
        appSettings.setIntelligenceLevel(IntelligenceLevel.CONSERVATIVE)

        val report = scanRoot(root.id)

        assertEquals(0, report.editionsAutoMerged)
        assertEquals(2, allEditions().size)
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "Book v1"))
        assertNotNull(database.editionDao().getByRootAndFolder(root.id, "Book v2"))
    }

    // ---- VERIFY (Bug 4): البنية المرجعية الكاملة — Author → Series → Book بلا دمج خاطئ ----

    @Test
    fun verifyReferenceHierarchyScansAuthorsSeriesAndBooksWithoutCrossMerges() = runBlocking {
        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp3", null, null, null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/f1.mp3"), "أحمد خالد توفيق/فانتازيا/01.mp3", "أحمد خالد توفيق/فانتازيا", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f2.mp3"), "أحمد خالد توفيق/ما وراء الطبيعة/01.mp3", "أحمد خالد توفيق/ما وراء الطبيعة", "01.mp3", 200, 10),
            ScanFile(Uri.parse("content://audio/f3.mp3"), "أحمد خالد توفيق/سافاري/01.mp3", "أحمد خالد توفيق/سافاري", "01.mp3", 300, 10),
            ScanFile(Uri.parse("content://audio/f4.mp3"), "أحمد خالد توفيق/paranormal/book1/01.mp3", "أحمد خالد توفيق/paranormal/book1", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f5.mp3"), "أحمد خالد توفيق/paranormal/book2/02.mp3", "أحمد خالد توفيق/paranormal/book2", "02.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f6.mp3"), "أحمد خالد توفيق/standalone_book.mp3", "أحمد خالد توفيق", "standalone_book.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f7.mp3"), "نبيل فاروق/ملف المستقبل/01.mp3", "نبيل فاروق/ملف المستقبل", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f8.mp3"), "كريم قنديل/book1/01.mp3", "كريم قنديل/book1", "01.mp3", 100, 10),
            ScanFile(Uri.parse("content://audio/f9.mp3"), "كريم قنديل/book2/01.mp3", "كريم قنديل/book2", "01.mp3", 100, 10)
        )

        val report = scanRoot(root.id)

        assertEquals("تسعة مجلدات تحوي ملفات → تسعة كتب (لا دمج خاطئ)", 9, report.editionsCreated)
        assertEquals("لا دمج تلقائي عبر السلاسل ولا بين كتابي كريم", 0, report.editionsAutoMerged)
        assertEquals(9, allEditions().size)
        assertEquals(9, database.bookDao().getAll().size)

        val ahmed = database.authorDao().getByName("أحمد خالد توفيق")!!
        val nabeel = database.authorDao().getByName("نبيل فاروق")!!
        val kareem = database.authorDao().getByName("كريم قنديل")!!

        val ahmedSeries = database.seriesDao().getByParent(ahmed.id)
        assertEquals("أربع سلاسل تحت أحمد (اسم غير عام لابن المؤلف)", setOf("فانتازيا", "ما وراء الطبيعة", "سافاري", "paranormal"), ahmedSeries.map { it.name }.toSet())
        assertEquals("سلسلة واحدة «ملف المستقبل» تحت نبيل", "ملف المستقبل", database.seriesDao().getByParent(nabeel.id).single().name)
        assertTrue("كتابا كريم أسماء عامة → مجلدات كتاب بلا سلسلة", database.seriesDao().getByParent(kareem.id).isEmpty())

        suspend fun bookAt(path: String) = database.bookDao().getById(database.editionDao().getByRootAndFolder(root.id, path)!!.bookId)!!

        val fantasy = bookAt("أحمد خالد توفيق/فانتازيا")
        assertEquals(ahmed.id, fantasy.authorId)
        assertEquals(ahmedSeries.single { it.name == "فانتازيا" }.id, fantasy.seriesId)

        val paranormalBook1 = bookAt("أحمد خالد توفيق/paranormal/book1")
        assertEquals(ahmed.id, paranormalBook1.authorId)
        assertEquals(ahmedSeries.single { it.name == "paranormal" }.id, paranormalBook1.seriesId)
        assertTrue("كتابا paranormal مجلدان مستقلان (ابدأان بلا دمج)", paranormalBook1.id != bookAt("أحمد خالد توفيق/paranormal/book2").id)

        val standalone = bookAt("أحمد خالد توفيق")
        assertEquals("كتاب الكعب المباشر للمؤلف", ahmed.id, standalone.authorId)
        assertNull("الكعب المباشر بلا سلسلة", standalone.seriesId)

        val mustaqbal = bookAt("نبيل فاروق/ملف المستقبل")
        assertEquals(nabeel.id, mustaqbal.authorId)
        assertEquals("ملف المستقبل", database.seriesDao().getById(mustaqbal.seriesId!!)?.name)

        val k1 = bookAt("كريم قنديل/book1")
        val k2 = bookAt("كريم قنديل/book2")
        assertEquals(kareem.id, k1.authorId)
        assertEquals(kareem.id, k2.authorId)
        assertNull(k1.seriesId)
        assertNull(k2.seriesId)
        assertTrue("كتابا كريم كيانان مختلفان (الاسم العام يمنع اعتبارهما سلسلةً)", k1.id != k2.id)
    }

    private class FakeFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class CountingMetadataReader : AudioMetadataReader {
        var readCount = 0
        var overrides = mutableMapOf<String, AudioMetadata>()
        var metadataByUri = mutableMapOf<String, AudioMetadata>()

        override fun read(uri: Uri, fileName: String): AudioMetadata {
            readCount++
            return metadataByUri[uri.toString()] ?: overrides["default"] ?: AudioMetadata(1000, "audio/mp4", "Book", null, null, emptyList())
        }
    }
}