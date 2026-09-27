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
        InputFile(uri = "content://audio/$name", filename = name, folderPath = folder, sizeBytes = size, durationMs = 0)

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

    // ── اختبار 1 المصحَّح: مؤلف يحوي مجلدين ذوي ملفات مباشرة → كتابان بلا سلسلتين ──

    @Test
    fun authorWithTwoAudioFoldersProducesTwoBooksEachItsOwnSeries() {
        val books = classify(
            file("أحمد خالد توفيق/فانتازيا", "01.mp3"),
            file("أحمد خالد توفيق/ما وراء الطبيعة", "01.mp3")
        )
        assertEquals(2, books.size)
        assertEquals(setOf("فانتازيا", "ما وراء الطبيعة"), books.map { it.bookTitle }.toSet())
        books.forEach { book ->
            assertEquals("مجلد العمق-2 ذو ملفات مباشرة سلسلة وكتاب بنفس الاسم", book.bookTitle, book.seriesName)
            assertEquals("أحمد خالد توفيق", book.authorName)
        }
        assertEquals(setOf("فانتازيا", "ما وراء الطبيعة"), books.mapNotNull { it.seriesName }.toSet())
    }

    // ── اختبار 2 المصحَّح: كتابٌ له 3 ملفات عند العمق-2 → كتاب واحد باسم المجلد ──

    @Test
    fun bookWithThreeFilesAtDepthTwoIsOneBook() {
        val books = classify(
            file("أحمد خالد توفيق/سافاري", "01.mp3", size = 10),
            file("أحمد خالد توفيق/سافاري", "02.mp3", size = 20),
            file("أحمد خالد توفيق/سافاري", "03.mp3", size = 30)
        )
        assertEquals(1, books.size)
        val safari = books[0]
        assertEquals("أحمد خالد توفيق/سافاري", safari.folderPath)
        assertEquals("سافاري", safari.bookTitle)
        assertEquals("سلسلة وكتاب بنفس الاسم", "سافاري", safari.seriesName)
        assertEquals("أحمد خالد توفيق", safari.authorName)
        assertEquals(3, safari.files.size)
    }

    // 8) الاسم العام عند العمق-2 لا يقلب القرار: سلسلة + كتاب باسم المجلد نفسه (بلا كشف أسماء).

    @Test
    fun genericNameAtDepthTwoIsBookInItsOwnSeries() {
        val books = classify(file("كريم قنديل/book1", "01.mp3"))
        assertEquals(1, books.size)
        val book = books[0]
        assertEquals("كريم قنديل", book.authorName)
        assertEquals("سلسلة باسمه هو", "book1", book.seriesName)
        assertEquals("book1", book.bookTitle)
    }

    // ── اختبار D: نبيل فاروق/ملف المستقبل/01.mp3 → سلسلة + كتاب باسم المجلد ──

    @Test
    fun authorWithSingleAudioFolderProducesSeriesAndBookWithSameName() {
        val books = classify(file("نبيل فاروق/ملف المستقبل", "01.mp3"))
        assertEquals(1, books.size)
        val mustaqbal = books[0]
        assertEquals("نبيل فاروق", mustaqbal.authorName)
        assertEquals("ملف المستقبل", mustaqbal.seriesName)
        assertEquals("ملف المستقبل", mustaqbal.bookTitle)
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

    // ── اختبار A: مؤلف يحوي ملفًا مباشرًا ومجلدًا ذي ملفات → كتابان بلا سلسلتين ──

    @Test
    fun authorWithDirectFilesAndAudioSubfolderProducesBooksNoSeries() {
        val books = classify(
            file("أحمد خالد توفيق", "standalone.mp3"),
            file("أحمد خالد توفيق/فانتازيا", "01.mp3")
        )
        assertEquals(2, books.size)
        val standalone = bookByPath(books, "أحمد خالد توفيق")!!
        val fantasia = bookByPath(books, "أحمد خالد توفيق/فانتازيا")!!
        assertEquals("standalone", standalone.bookTitle)
        assertEquals("أحمد خالد توفيق", standalone.authorName)
        assertNull("الملف المباشر تحت المؤلف كتاب بلا سلسلة", standalone.seriesName)
        assertEquals("فانتازيا", fantasia.bookTitle)
        assertEquals("أحمد خالد توفيق", fantasia.authorName)
        assertEquals("مجلد العمق-2 سلسلة وكتاب بنفس الاسم", "فانتازيا", fantasia.seriesName)
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
        assertEquals(setOf("standalone"), bookByPath(books, "أحمد خالد توفيق")?.let { setOf(it.bookTitle) })
        assertEquals("فانتازيا", bookByPath(books, "أحمد خالد توفيق/فانتازيا")?.bookTitle)
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
    //Phase 2 — تحقق البنية المتوقعة كاملةً (مرجع الفحص)
    //
    // Library Root/
    // ├── أحمد خالد توفيق/          AUTHOR
    // │   ├── standalone.mp3        BOOK بلا سلسلة
    // │   ├── فانتازيا/             SERIES + BOOK "فانتازيا"
    // │   │   ├── 01.mp3
    // │   │   └── 02.mp3
    // │   └── paranormal/           SERIES + كتابان
    // │       ├── book1/01.mp3
    // │       └── book2/01.mp3
    // ├── نبيل فاروق/
    // │   └── ملف المستقبل/01.mp3   SERIES + BOOK "ملف المستقبل"
    // └── random.mp3                BOOK بلا مؤلف
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    fun phase2ExpectedStructureIsProducedExactly() {
        val books = classify(
            file("أحمد خالد توفيق", "standalone.mp3"),
            file("أحمد خالد توفيق/فانتازيا", "01.mp3", size = 10),
            file("أحمد خالد توفيق/فانتازيا", "02.mp3", size = 20),
            file("أحمد خالد توفيق/paranormal/book1", "01.mp3", size = 30),
            file("أحمد خالد توفيق/paranormal/book2", "01.mp3", size = 40),
            file("نبيل فاروق/ملف المستقبل", "01.mp3", size = 50),
            file("", "random.mp3", size = 60)
        )

        // ستّة كتب: 5 من الشجرة + كتاب الجذر المستقل.
        assertEquals(6, books.size)

        // 1) ملف مباشر تحت المؤلف: كتاب بمؤلف المجلد وبلا سلسلة.
        val standalone = bookByPath(books, "أحمد خالد توفيق")!!
        assertEquals("أحمد خالد توفيق", standalone.authorName)
        assertNull(standalone.seriesName)
        assertEquals("standalone", standalone.bookTitle)
        assertEquals(listOf("standalone.mp3"), standalone.files.map { it.filename })

        // 2) عمق-2 بملفات مباشرة: سلسلة وكتاب بنفس الاسم، بملفاته الاثنين.
        val fantasia = bookByPath(books, "أحمد خالد توفيق/فانتازيا")!!
        assertEquals("أحمد خالد توفيق", fantasia.authorName)
        assertEquals("فانتازيا", fantasia.seriesName)
        assertEquals("فانتازيا", fantasia.bookTitle)
        assertEquals(listOf("01.mp3", "02.mp3"), fantasia.files.map { it.filename })

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

        // 4) مؤلف آخر: سلسلة وكتاب باسم المجلد.
        val mustaqbal = bookByPath(books, "نبيل فاروق/ملف المستقبل")!!
        assertEquals("نبيل فاروق", mustaqbal.authorName)
        assertEquals("ملف المستقبل", mustaqbal.seriesName)
        assertEquals("ملف المستقبل", mustaqbal.bookTitle)

        // 5) ملف على الجذر: كتاب بلا مؤلف وبلا سلسلة، عنوانه stem الملف.
        val random = bookByPath(books, "")!!
        assertNull(random.authorName)
        assertNull(random.seriesName)
        assertEquals("random", random.bookTitle)
    }

    // كل كتاب يحمل uri ملفاته — أساس ربط ClassifiedBook بملفاته في الفحص.

    @Test
    fun everyClassifiedBookCarriesItsOwnFileUris() {
        val books = classify(
            file("أحمد خالد توفيق/فانتازيا", "01.mp3", size = 10),
            file("أحمد خالد توفيق/فانتازيا", "02.mp3", size = 20),
            file("", "random.mp3", size = 60)
        )
        assertEquals(2, books.size)
        val claimed = books.flatMap { book -> book.files.map { it.uri } }
        assertEquals("كل ملف يُنسب لكتاب واحد فقط", claimed.size, claimed.toSet().size)
        val fantasia = books.first { it.bookTitle == "فانتازيا" }
        assertEquals(
            setOf("content://audio/01.mp3", "content://audio/02.mp3"),
            fantasia.files.map { it.uri }.toSet()
        )
    }
}