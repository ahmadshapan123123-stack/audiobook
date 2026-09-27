package com.example.audiobook.domain.usecases

import com.example.audiobook.domain.usecases.StrictFolderClassifier.ClassifiedBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.InputFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * اختبارات StrictFolderClassifier (المرحلة 3) — مصنِّف صارم خالص بلا قاعدة بيانات.
 *
 * النموذج الصحيح:
 *  - العمق 1 = AUTHOR دائمًا؛ ملفاته المباشرة كتب لكل ملف بمؤلف المجلد وبلا سلسلة.
 *  - العمق 2: سلسلة تحت مؤلفه وكتابٌ هو المجلد نفسه — ملفات مباشرة → SERIES + BOOK
 *    بنفس الاسم؛ مجلدات فقط → SERIES (حاوية) وكتبها أبناؤها في العمق ≥ 3.
 *  - العمق ≥ 3: ملفات مباشرة → BOOK؛ مجلدات فقط → BOOK حاوية (تكرار للأحفاد).
 */
class StrictFolderClassifierTest {

    private fun file(folder: String, name: String, size: Long = 100): InputFile =
        InputFile(uri = "content://audio/$folder/$name", filename = name, folderPath = folder, sizeBytes = size, durationMs = 0)

    private fun classify(vararg files: InputFile, rootName: String = "Library"): List<ClassifiedBook> =
        StrictFolderClassifier.classify(files.toList(), rootName)

    private fun bookByPath(books: List<ClassifiedBook>, folderPath: String): ClassifiedBook? =
        books.firstOrNull { it.folderPath == folderPath }

    // 1) مدخل فارغ → صفر كتب.

    @Test
    fun emptyInputProducesNoBooks() {
        assertEquals(emptyList<ClassifiedBook>(), StrictFolderClassifier.classify(emptyList(), "Library"))
    }

    // 2) ملفات الجذر المباشرة → كتاب لكل ملف، بلا مؤلف ولا سلسلة، العنوان = اسم الملف.

    @Test
    fun rootDirectFilesBecomePerFileStandaloneUnclassifiedBooks() {
        val books = classify(
            file("", "01.mp3", size = 10),
            file("", "Chapter Two.mp3", size = 20)
        )
        assertEquals(2, books.size)
        books.forEach { book ->
            assertNull("ملف جذر بلا مؤلف", book.authorName)
            assertNull("ملف جذر بلا سلسلة", book.seriesName)
            assertEquals("", book.folderPath)
            assertEquals("لكل ملف جذر كتابه المستقل", 1, book.files.size)
        }
        assertEquals("01", books[0].bookTitle)
        assertEquals("Chapter Two", books[1].bookTitle)
        assertEquals(10L, books[0].files[0].sizeBytes)
    }

    // 3) جذر يحوي ثلاثة مجلدات مؤلفين → كتاب لكل ملف مباشر تحت كل مؤلف، بلا كتب جذر.

    @Test
    fun rootWithThreeAuthorsClassifiesAuthorBooksWithNoRootBooks() {
        val books = classify(
            file("أحمد خالد توفيق", "01.mp3"),
            file("نبيل فاروق", "01.mp3"),
            file("كريم قنديل", "01.mp3")
        )
        assertEquals(3, books.size)
        assertEquals(
            setOf("أحمد خالد توفيق", "نبيل فاروق", "كريم قنديل"),
            books.map { it.folderPath }.toSet()
        )
        books.forEach { book ->
            assertEquals("كتاب المؤلف المباشر يُنسب لمؤلفه", book.folderPath, book.authorName)
            assertNull(book.seriesName)
            assertEquals("عنوانه اسم الملف المباشر", "01", book.bookTitle)
        }
        assertTrue("لا كتب على الجذر مباشرة", books.none { it.folderPath.isEmpty() })
    }

    // 4) العمق 1 = AUTHOR حاوية دائمًا حتى للاسم الذي يبدو اسم كتاب؛ ملفاته كتب بمؤلفه.

    @Test
    fun depthOneFolderIsAlwaysAuthorContainerEvenForGenericBookNames() {
        val books = classify(file("كتاب رئيسي", "01.mp3"))
        assertEquals(1, books.size)
        val main = books[0]
        assertEquals("كتاب رئيسي", main.folderPath)
        assertEquals("الكتاب المباشر لمؤلفٍ اسمه كالمجلد نفسه", "كتاب رئيسي", main.authorName)
        assertEquals("عنوانه اسم الملف", "01", main.bookTitle)
        assertNull(main.seriesName)
    }

    // 5) ملفات مباشرة في مجلد المؤلف → كتاب لكل ملف، كلها بمؤلف المجلد.

    @Test
    fun authorDirectFilesBecomePerFileStandaloneBooksAuthoredByAuthor() {
        val books = classify(
            file("Author", "01.mp3", size = 10),
            file("Author", "02.mp3", size = 20)
        )
        assertEquals(2, books.size)
        books.forEach { book ->
            assertEquals("Author", book.authorName)
            assertEquals("Author", book.folderPath)
            assertNull(book.seriesName)
            assertEquals(1, book.files.size)
        }
        assertEquals(setOf("01", "02"), books.map { it.bookTitle }.toSet())
    }

    // ── اختبار 1: مؤلف يحوي مجلدين ذوي ملفات مباشرة → كتاب لكل ملف، وسلسلة لكل مجلد ──

    @Test
    fun authorWithTwoAudioFoldersProducesTwoBooksEachItsOwnSeries() {
        val books = classify(
            file("أحمد خالد توفيق/فانتازيا", "01.mp3"),
            file("أحمد خالد توفيق/ما وراء الطبيعة", "01.mp3")
        )
        assertEquals(2, books.size)
        assertEquals(setOf("01"), books.map { it.bookTitle }.toSet())
        books.forEach { book ->
            assertEquals("العمق-2: عنوان الكتاب اسم الملف بلا امتداد", book.bookTitle, "01")
            assertEquals("أحمد خالد توفيق", book.authorName)
            assertEquals(1, book.files.size)
        }
        assertEquals(setOf("فانتازيا", "ما وراء الطبيعة"), books.mapNotNull { it.seriesName }.toSet())
    }

    // ── اختبار 2: مجلد عمق-2 فيه 3 ملفات → 3 كتب مستقلة، كلٌّ بملف واحد ──

    @Test
    fun bookWithThreeFilesAtDepthTwoBecomesThreeSeparateBooks() {
        val books = classify(
            file("أحمد خالد توفيق/سافاري", "01.mp3", size = 10),
            file("أحمد خالد توفيق/سافاري", "02.mp3", size = 20),
            file("أحمد خالد توفيق/سافاري", "03.mp3", size = 30)
        )
        assertEquals("كل ملف في مجلد عمق-2 كتاب مستقل", 3, books.size)
        assertEquals(setOf("01", "02", "03"), books.map { it.bookTitle }.toSet())
        books.forEach { book ->
            assertEquals("أحمد خالد توفيق", book.authorName)
            assertEquals("المجلد سلسلة تحت المؤلف", "سافاري", book.seriesName)
            assertEquals("لا دمج: كتاب واحد لكل ملف", 1, book.files.size)
        }
        assertNull("لا كتاب باسم المجلد نفسه", books.firstOrNull { it.bookTitle == "سافاري" })
        assertNull("لا كتاب يجمع ملفات السلسلة كلها", books.firstOrNull { it.files.size == 3 })
    }

    // 8) الاسم العام عند العمق-2 لا يقلب القرار: سلسلة باسم المجلد وكتاب باسم الملف.

    @Test
    fun genericNameAtDepthTwoIsBookInItsOwnSeries() {
        val books = classify(file("كريم قنديل/book1", "01.mp3"))
        assertEquals(1, books.size)
        val book = books[0]
        assertEquals("كريم قنديل", book.authorName)
        assertEquals("سلسلة باسمه هو", "book1", book.seriesName)
        assertEquals("كتاب باسم ملفه", "01", book.bookTitle)
    }

    // ── اختبار D: نبيل فاروق/ملف المستقبل/01.mp3 → سلسلة باسم المجلد وكتاب باسم الملف ──

    @Test
    fun authorWithSingleAudioFolderProducesSeriesAndBookWithFileName() {
        val books = classify(file("نبيل فاروق/ملف المستقبل", "01.mp3"))
        assertEquals(1, books.size)
        val mustaqbal = books[0]
        assertEquals("نبيل فاروق", mustaqbal.authorName)
        assertEquals("ملف المستقبل", mustaqbal.seriesName)
        assertEquals("01", mustaqbal.bookTitle)
    }

    // ── اختبار B: مجلدٌ لمؤلف يحوي مجلدًا يحوي مجلدات → سلاسل وكتب عميقة ──

    @Test
    fun authorWithSubfolderOfSubfoldersClassifiesSeriesWithBooks() {
        val books = classify(
            file("أحمد خالد توفيق/paranormal/book1", "01.mp3"),
            file("أحمد خالد توفيق/paranormal/book2", "02.mp3")
        )
        assertEquals(2, books.size)
        books.forEach { book ->
            assertEquals("أحمد خالد توفيق", book.authorName)
            assertEquals("كتب عمق-3 تنتسب لسلسلة جدّها العمق-2", "paranormal", book.seriesName)
            assertEquals(1, book.files.size)
        }
        assertEquals(setOf("book1", "book2"), books.map { it.bookTitle }.toSet())
        assertNull("الحاوية العمق-2 بلا ملفات مباشرة لا كتاب لنفسها", bookByPath(books, "أحمد خالد توفيق/paranormal"))
        assertNull("الحاوية العمق-1 لا كتاب لنفسها", bookByPath(books, "أحمد خالد توفيق"))
    }

    // ── اختبار A: مؤلف يحوي ملفًا مباشرًا ومجلدًا ذي ملفات → كتابان: مباشر بلا سلسلة + ملف السلسلة ──

    @Test
    fun authorWithDirectFilesAndAudioSubfolderProducesBooksNoSeries() {
        val books = classify(
            file("أحمد خالد توفيق", "standalone.mp3"),
            file("أحمد خالد توفيق/فانتازيا", "01.mp3")
        )
        assertEquals(2, books.size)
        val standalone = books.first { it.bookTitle == "standalone" }
        val fantasia = books.first { it.bookTitle == "01" }
        assertEquals("standalone", standalone.bookTitle)
        assertEquals("أحمد خالد توفيق", standalone.authorName)
        assertNull("الملف المباشر تحت المؤلف كتاب بلا سلسلة", standalone.seriesName)
        assertEquals("01", fantasia.bookTitle)
        assertEquals("أحمد خالد توفيق", fantasia.authorName)
        assertEquals("مجلد العمق-2 سلسلة تحت المؤلف", "فانتازيا", fantasia.seriesName)
    }

    // ── اختبار C: مؤلف مخلوط (ملف مباشر + مجلد ذي ملفات + مجلد سلسلة) ──

    @Test
    fun mixedAuthorFolderProducesBooksAndTwoSeries() {
        val books = classify(
            file("أحمد خالد توفيق", "standalone.mp3"),
            file("أحمد خالد توفيق/فانتازيا", "01.mp3"),
            file("أحمد خالد توفيق/paranormal/book1", "01.mp3")
        )
        assertEquals(3, books.size)
        assertEquals(
            "سلسلتان: فانتازيا (عمق-2 بملفات) و paranormal (عمق-2 حاوية)",
            setOf("فانتازيا", "paranormal"),
            books.mapNotNull { it.seriesName }.toSet()
        )
        assertEquals(
            setOf("standalone"),
            books.filter { it.seriesName == null }.map { it.bookTitle }.toSet()
        )
        assertEquals("01", books.first { it.seriesName == "فانتازيا" }.bookTitle)
        assertEquals("book1", bookByPath(books, "أحمد خالد توفيق/paranormal/book1")?.bookTitle)
        books.forEach { assertEquals("كل الكتب بمؤلف أحمد", "أحمد خالد توفيق", it.authorName) }
    }

    // 14) كتاب تحت كتاب: العقدة العميقة الحاوية لأحفاد تحمل صوتًا تبقى كتابًا (ملفاتها فارغة).

    @Test
    fun nestedBookUnderBookProducesBothBooks() {
        val books = classify(file("Author/Series/Book1/Nested", "01.mp3"))
        assertEquals(2, books.size)
        val book1 = bookByPath(books, "Author/Series/Book1")!!
        val nested = bookByPath(books, "Author/Series/Book1/Nested")!!
        assertEquals("Book1", book1.bookTitle)
        assertTrue("عقدة عمق-3 حاوية بلا ملفات مباشرة: كتابٌ بملفات فارغة", book1.files.isEmpty())
        assertEquals("Nested", nested.bookTitle)
        assertEquals(1, nested.files.size)
        listOf(book1, nested).forEach {
            assertEquals("Author", it.authorName)
            assertEquals("Series", it.seriesName)
        }
    }

    // 15) كتابا شقيقان تحت السلسلة نفسها كيانان مستقلان — لا دمج.

    @Test
    fun siblingBooksUnderSameSeriesStaySeparate() {
        val books = classify(
            file("Author/SeriesX/book1", "01.mp3"),
            file("Author/SeriesX/book2", "02.mp3")
        )
        assertEquals(2, books.size)
        assertEquals(2, books.map { it.folderPath }.toSet().size)
        books.forEach {
            assertEquals("Author", it.authorName)
            assertEquals("SeriesX", it.seriesName)
        }
        assertEquals(setOf("book1", "book2"), books.map { it.bookTitle }.toSet())
    }

    // 16) سلسلتان مختلفتان بنفس اسم كتابهما → كتابان منفصلان بلا أي اتحاد.

    @Test
    fun differentSeriesNeverMergeIntoOneBook() {
        val books = classify(
            file("Author/SeriesA/book", "01.mp3"),
            file("Author/SeriesB/book", "02.mp3")
        )
        assertEquals(2, books.size)
        assertEquals(setOf("SeriesA", "SeriesB"), books.map { it.seriesName }.toSet())
        assertEquals(2, books.map { it.folderPath }.toSet().size)
        books.forEach { assertEquals("Author", it.authorName) }
    }

    // 17) عدد الكتب يتبع المجلدات الحاملة الصوت فقط — الحاويات لا تُعدّ كتبًا.

    @Test
    fun containersAreNeverEmittedAsBooksWhenTheyHoldOnlySubfolders() {
        val books = classify(
            file("أحمد/سلسلة/كتاب1", "01.mp3"),
            file("كريم/كتاب2", "02.mp3")
        )
        assertEquals(2, books.size)
        assertEquals(
            setOf("أحمد/سلسلة/كتاب1", "كريم/كتاب2"),
            books.map { it.folderPath }.toSet()
        )
    }

    // 18) ملف جذر بلا اسم نافع → عنوانه اسم الجذر (fallback).

    @Test
    fun rootLevelTitleFallsBackToRootNameWhenFilenameHasNoStem() {
        val books = classify(file("", ".mp3"), rootName = "Library")
        assertEquals(1, books.size)
        assertEquals("Library", books[0].bookTitle)
        assertNull(books[0].authorName)
    }

    // ══════════════════════════════════════════════════════════════════════════
    // النموذج الصارم (مرجع الفحص) — كل ملف في مجلد سلسلة العمق-2 كتاب مستقل
    //
    // Library Root/
    // ├── أحمد خالد توفيق/          AUTHOR
    // │   ├── standalone.mp3        BOOK "standalone" بلا سلسلة
    // │   ├── فانتازيا/             SERIES، ولكل ملف فيه كتاب
    // │   │   ├── 01.mp3            → BOOK "01"
    // │   │   └── 02.mp3            → BOOK "02"
    // │   └── paranormal/           SERIES حاوية (لا كتاب لها)
    // │       ├── book1/01.mp3      → BOOK "book1"
    // │       └── book2/01.mp3      → BOOK "book2"
    // ├── نبيل فاروق/               AUTHOR
    // │   └── ملف المستقبل/01.mp3   SERIES + BOOK "01"
    // └── random.mp3                BOOK "random" بلا مؤلف
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun strictExpectedStructureIsProducedExactly() {
        val books = classify(
            file("أحمد خالد توفيق", "standalone.mp3"),
            file("أحمد خالد توفيق/فانتازيا", "01.mp3", size = 10),
            file("أحمد خالد توفيق/فانتازيا", "02.mp3", size = 20),
            file("أحمد خالد توفيق/paranormal/book1", "01.mp3", size = 30),
            file("أحمد خالد توفيق/paranormal/book2", "01.mp3", size = 40),
            file("نبيل فاروق/ملف المستقبل", "01.mp3", size = 50),
            file("", "random.mp3", size = 60)
        )

        // سبعة كتب: 6 من الشجرة + كتاب الجذر المستقل.
        assertEquals(7, books.size)

        // 1) ملف مباشر تحت المؤلف: كتاب بمؤلف المجلد وبلا سلسلة.
        val standalone = books.first { it.bookTitle == "standalone" }
        assertEquals("أحمد خالد توفيق", standalone.authorName)
        assertNull(standalone.seriesName)
        assertEquals("أحمد خالد توفيق", standalone.folderPath)
        assertEquals(listOf("standalone.mp3"), standalone.files.map { it.filename })

        // 2) عمق-2 بملفات مباشرة: كتاب مستقل لكل ملف، وسلسلة باسم المجلد.
        val fantasia = books.filter { it.seriesName == "فانتازيا" }
        assertEquals("كتاب لكل ملف في مجلد السلسلة", 2, fantasia.size)
        fantasia.forEach {
            assertEquals("أحمد خالد توفيق", it.authorName)
            assertEquals(1, it.files.size)
        }
        assertEquals(setOf("01", "02"), fantasia.map { it.bookTitle }.toSet())
        assertNull("لا كتاب باسم مجلد السلسلة نفسه", books.firstOrNull { it.bookTitle == "فانتازيا" })

        // 3) عمق-2 حاوية: لا كتاب لها، وكتابان في عمق-3 ينتميان لسلسلتها.
        assertNull("الحاوية بلا ملفات مباشرة لا تُنتج كتابًا", bookByPath(books, "أحمد خالد توفيق/paranormal"))
        val book1 = bookByPath(books, "أحمد خالد توفيق/paranormal/book1")!!
        val book2 = bookByPath(books, "أحمد خالد توفيق/paranormal/book2")!!
        listOf(book1, book2).forEach {
            assertEquals("أحمد خالد توفيق", it.authorName)
            assertEquals("paranormal", it.seriesName)
        }
        assertEquals("book1", book1.bookTitle)
        assertEquals("book2", book2.bookTitle)

        // 4) مؤلف آخر: سلسلة باسم المجلد وكتاب باسم الملف.
        val mustaqbal = books.first { it.seriesName == "ملف المستقبل" }
        assertEquals("نبيل فاروق", mustaqbal.authorName)
        assertEquals("01", mustaqbal.bookTitle)

        // 5) ملف على الجذر: كتاب بلا مؤلف وبلا سلسلة، عنوانه stem الملف.
        val random = bookByPath(books, "")!!
        assertNull(random.authorName)
        assertNull(random.seriesName)
        assertEquals("random", random.bookTitle)
    }

    // ── مقياس 1: سلسلة العمق-2 بـ 162 ملفًا → 162 كتابًا مستقلًا (لا كتاب مجمَّع) ──

    @Test
    fun depthTwoFolderWith162FilesBecomes162DistinctBooks() {
        val files = (1..162).map { file("نبيل فاروق/رجل المستحيل", "%03d.mp3".format(it), size = it.toLong()) }
        val books = classify(*files.toTypedArray())

        assertEquals("لا دمج: كتاب لكل ملف", 162, books.size)
        assertEquals("عناوين فريدة", 162, books.map { it.bookTitle }.toSet().size)
        assertEquals(162, books.map { it.files.single().uri }.toSet().size)
        books.forEach { book ->
            assertEquals("نبيل فاروق", book.authorName)
            assertEquals("رجل المستحيل", book.seriesName)
            assertEquals("كتاب بملف واحد", 1, book.files.size)
        }
        assertEquals("162 كتابًا من 162 ملفًا", 162, books.size)
        assertEquals(
            (1..162).map { "%03d".format(it) }.toSet(),
            books.map { it.bookTitle }.toSet()
        )
    }

    // ── مقياس 2: سلسلة العمق-2 بـ 100 ملفًا → 100 كتاب مستقل ──

    @Test
    fun depthTwoFolderWith100FilesBecomes100DistinctBooks() {
        val files = (1..100).map { file("نبيل فاروق/ملف المستقبل", "%03d.mp3".format(it), size = it.toLong()) }
        val books = classify(*files.toTypedArray())

        assertEquals("لا دمج: كتاب لكل ملف", 100, books.size)
        assertEquals(100, books.map { it.bookTitle }.toSet().size)
        assertEquals(100, books.map { it.files.single().uri }.toSet().size)
        books.forEach {
            assertEquals("ملف المستقبل", it.seriesName)
            assertEquals("نبيل فاروق", it.authorName)
        }
    }

    // ── مقياس 3: عمق-2 يخلط ملفات ومجلدات → ملفات كتب مستقلة + كتب المجلدات، بلا دمج ──

    @Test
    fun depthTwoFolderMixingFilesAndSubfoldersKeepsThemSeparate() {
        val books = classify(
            file("أحمد خالد توفيق/paranormal", "01.mp3"),
            file("أحمد خالد توفيق/paranormal", "02.mp3"),
            file("أحمد خالد توفيق/paranormal/book1", "01.mp3"),
            file("أحمد خالد توفيق/paranormal/book2", "01.mp3")
        )
        assertEquals("ملفان + كتابان من مجلدين", 4, books.size)
        val perFile = books.filter { it.bookTitle in setOf("01", "02") }
        assertEquals(2, perFile.size)
        perFile.forEach {
            assertEquals("ملف السلسلة كتاب مستقل", "paranormal", it.seriesName)
            assertEquals(1, it.files.size)
        }
        assertEquals(
            setOf("book1", "book2"),
            books.filter { it.bookTitle.startsWith("book") }.map { it.bookTitle }.toSet()
        )
    }

    // ── مقياس 4: المجلد الحقيقي على الجهاز = كتاب واحد لكل ملف (سابقة السلوك الخاطئة) ──

    @Test
    fun realDeviceTreeYieldsOneBookPerAudioFile() {
        val files = buildList {
            add(file("", "random.mp3"))
            add(file("أحمد خالد توفيق", "standalone.mp3"))
            (1..6).forEach { add(file("أحمد خالد توفيق/فانتازيا", "%02d.mp3".format(it))) }
            add(file("أحمد خالد توفيق/paranormal/book1", "01.mp3"))
            add(file("أحمد خالد توفيق/paranormal/book2", "01.mp3"))
            (1..162).forEach { add(file("نبيل فاروق/رجل المستحيل", "%03d.mp3".format(it))) }
            (1..100).forEach { add(file("نبيل فاروق/ملف المستقبل", "%03d.mp3".format(it))) }
        }
        val books = classify(*files.toTypedArray())

        // 1 جذر + 1 مباشر + 6 فانتازيا + 2 paranormal + 162 + 100
        assertEquals("كتاب لكل ملف صوتي", files.size, books.size)
        assertEquals(272, books.size)
        assertEquals(162, books.count { it.seriesName == "رجل المستحيل" })
        assertEquals(100, books.count { it.seriesName == "ملف المستقبل" })
        assertEquals(6, books.count { it.seriesName == "فانتازيا" })
        // كل ملف يُنسب لكتاب واحد بالضبط — لا تكرار ولا ضياع.
        val claimed = books.flatMap { book -> book.files.map { it.uri } }
        assertEquals(files.size, claimed.size)
        assertEquals(files.size, claimed.toSet().size)
    }

    // كل كتاب يحمل uri ملفاته — أساس ربط ClassifiedBook بملفاته في الفحص.

    @Test
    fun everyClassifiedBookCarriesItsOwnFileUris() {
        val books = classify(
            file("أحمد خالد توفيق/فانتازيا", "01.mp3", size = 10),
            file("أحمد خالد توفيق/فانتازيا", "02.mp3", size = 20),
            file("", "random.mp3", size = 60)
        )
        assertEquals(3, books.size)
        val claimed = books.flatMap { book -> book.files.map { it.uri } }
        assertEquals("كل ملف يُنسب لكتاب واحد فقط", claimed.size, claimed.toSet().size)
        val fantasia = books.filter { it.seriesName == "فانتازيا" }
        assertEquals(2, fantasia.size)
        assertEquals(
            setOf("content://audio/أحمد خالد توفيق/فانتازيا/01.mp3", "content://audio/أحمد خالد توفيق/فانتازيا/02.mp3"),
            fantasia.map { it.files.single().uri }.toSet()
        )
        assertEquals(setOf("01", "02"), fantasia.map { it.bookTitle }.toSet())
    }
}