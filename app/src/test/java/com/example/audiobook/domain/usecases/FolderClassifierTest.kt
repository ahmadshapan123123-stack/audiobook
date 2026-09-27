package com.example.audiobook.domain.usecases

import android.net.Uri
import com.example.audiobook.data.localfilesystem.ScanFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * اختبارات قواعد تصنيف الهرمية (FolderClassifier) — سيناريو VERIFY + قواعد A/B/C/D/F
 * + SPLIT + الأسماء العامة، كلها نقية بلا قاعدة بيانات.
 */
@RunWith(RobolectricTestRunner::class)
class FolderClassifierTest {

    private val fallback = "Library"

    private fun file(relative: String): ScanFile {
        val segments = relative.split('/').filter(String::isNotBlank)
        val folder = segments.dropLast(1).joinToString("/")
        val name = segments.last()
        return ScanFile(Uri.parse("content://audio/${name.hashCode()}"), relative, folder, name, 100, 10)
    }

    private fun files(vararg relative: String): List<ScanFile> = relative.map(::file)

    private fun rootsOf(vararg relative: String) = FolderClassifier.classify(files(*relative))

    private fun findNode(nodes: List<FolderNode>, path: String): FolderNode? {
        nodes.forEach { node ->
            if (node.path == path) return node
            findNode(node.children, path)?.let { return it }
        }
        return null
    }

    // ── VERIFY: البنية المرجعية للعميل ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (كتب اصطناعية داخل حاويات المؤلف/السلسلة) — معطّل بعلم StrictModeFlags")
    fun verifyReferenceHierarchyClassifiesAuthorsSeriesAndBooks() {
        val roots = rootsOf(
            "أحمد خالد توفيق/فانتازيا/01.mp3",
            "أحمد خالد توفيق/فانتازيا/02.mp3",
            "أحمد خالد توفيق/ما وراء الطبيعة/01.mp3",
            "أحمد خالد توفيق/سافاري/01.mp3",
            "أحمد خالد توفيق/paranormal/book1/01.mp3",
            "أحمد خالد توفيق/paranormal/book2/02.mp3",
            "أحمد خالد توفيق/standalone_book.mp3",
            "نبيل فاروق/ملف المستقبل/01.mp3",
            "كريم قنديل/book1/01.mp3",
            "كريم قنديل/book2/01.mp3"
        )

        assertEquals(
            setOf("أحمد خالد توفيق", "نبيل فاروق", "كريم قنديل"),
            roots.map { it.name }.toSet()
        )
        val ahmed = findNode(roots, "أحمد خالد توفيق")!!
        assertEquals(FolderKind.AUTHOR, ahmed.kind)
        assertEquals(
            setOf("فانتازيا", "ما وراء الطبيعة", "سافاري", "paranormal"),
            ahmed.children.filter { !it.synthetic }.map { it.name }.toSet()
        )

        // فانتازيا / ما وراء الطبيعة / سافاري: سلسلة تحوي كتابًا اصطناعيًا لملفاتها.
        listOf("فانتازيا", "ما وراء الطبيعة", "سافاري").forEach { name ->
            val series = findNode(roots, "أحمد خالد توفيق/$name")!!
            assertEquals("$name سلسلة (ابن المؤلف بغير اسم كتاب عام)", FolderKind.SERIES, series.kind)
            assertTrue(
                "$name تحوي كتابها الاصطناعي",
                series.children.any { it.kind == FolderKind.BOOK && it.synthetic && it.directFiles.isNotEmpty() }
            )
        }

        // paranormal: حاوية بلا ملفات مباشرة → سلسلة بكتابين.
        val paranormal = findNode(roots, "أحمد خالد توفيق/paranormal")!!
        assertEquals(FolderKind.SERIES, paranormal.kind)
        assertEquals(setOf("book1", "book2"), paranormal.children.map { it.name }.toSet())
        paranormal.children.forEach { assertEquals(FolderKind.BOOK, it.kind) }

        // كريم قنديل: ابنان بأسماء عامة → كتابان بلا سلسلة ولا دمج.
        val kareem = findNode(roots, "كريم قنديل")!!
        assertEquals(FolderKind.AUTHOR, kareem.kind)
        assertEquals(setOf("book1", "book2"), kareem.children.map { it.name }.toSet())
        kareem.children.forEach { assertEquals("الاسم العام لابن المؤلف = كتاب لا سلسلة", FolderKind.BOOK, it.kind) }
        assertNull("كريم بلا كتاب اصطناعي (بلا ملفات مباشرة)", findNode(kareem.children, "كريم قنديل"))

        // نبيل فاروق: سلسلة مجموعة تحوي كتابها الاصطناعي مباشرة.
        val mustaqbal = findNode(roots, "نبيل فاروق/ملف المستقبل")!!
        assertEquals(FolderKind.SERIES, mustaqbal.kind)
        assertTrue(mustaqbal.children.any { it.kind == FolderKind.BOOK && it.synthetic })

        // سياق (مؤلف/سلسلة) كل كتاب.
        val contexts = FolderClassifier.contextsByPath(roots, fallback)
        assertEquals("أحمد خالد توفيق", contexts.getValue("أحمد خالد توفيق/فانتازيا").authorName)
        assertEquals("فانتازيا", contexts.getValue("أحمد خالد توفيق/فانتازيا").seriesFolderName)
        assertEquals("أحمد خالد توفيق", contexts.getValue("أحمد خالد توفيق/paranormal/book1").authorName)
        assertEquals("paranormal", contexts.getValue("أحمد خالد توفيق/paranormal/book1").seriesFolderName)
        assertEquals("أحمد خالد توفيق", contexts.getValue("أحمد خالد توفيق").authorName)
        assertNull(contexts.getValue("أحمد خالد توفيق").seriesFolderName)
        assertEquals("نبيل فاروق", contexts.getValue("نبيل فاروق/ملف المستقبل").authorName)
        assertEquals("ملف المستقبل", contexts.getValue("نبيل فاروق/ملف المستقبل").seriesFolderName)
        assertEquals("كريم قنديل", contexts.getValue("كريم قنديل/book1").authorName)
        assertNull("كريم قنديل/book1 بلا سلسلة", contexts.getValue("كريم قنديل/book1").seriesFolderName)
    }

    // ── A: حاوية عمق 1 تستوفي الشرط (مجلدان فرعيان + 3 ملفات) = AUTHOR + كتاب اصطناعي ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (حاوية عمق 1 تصنّف AUTHOR مع كتابها الاصطناعي) — معطّل بعلم StrictModeFlags")
    fun depthOneMixedContainerWithEnoughSubfoldersAndFilesBecomesAuthorWithSyntheticBook() {
        val roots = rootsOf(
            "Variety/01.mp3",
            "Variety/Series A/01.mp3",
            "Variety/Series A/02.mp3",
            "Variety/Series B/01.mp3"
        )

        val variety = findNode(roots, "Variety")!!
        assertEquals(FolderKind.AUTHOR, variety.kind)
        val synthetic = variety.children.single { it.synthetic }
        assertEquals(FolderKind.BOOK, synthetic.kind)
        assertEquals("Variety", synthetic.path)
        assertEquals(1, synthetic.directFiles.size)
        assertEquals(setOf("Series A", "Series B"), variety.children.filter { !it.synthetic }.map { it.name }.toSet())
        assertEquals(FolderKind.SERIES, findNode(roots, "Variety/Series A")!!.kind)
    }

    // ── A-else: SPLIT — «كتاب رئيسي» بأجزائه كتاب + كتب فرعية بلا سلسلة ──

    @Test
    fun splitMixedFolderWithGenericNameProducesGroupBookAndSubfolderBooks() {
        val roots = rootsOf(
            "كتاب رئيسي/01.mp3",
            "كتاب رئيسي/جزء فرعي/02.mp3"
        )

        val main = findNode(roots, "كتاب رئيسي")!!
        assertEquals("SPLIT: عمق 1 مخلوط بشرط حاوية غير مستوف واسم عام → كتاب مجموعة", FolderKind.BOOK, main.kind)
        assertEquals(1, main.directFiles.size)
        val sub = findNode(roots, "كتاب رئيسي/جزء فرعي")!!
        assertEquals(FolderKind.BOOK, sub.kind)
        assertNull("لا كتاب اصطناعي داخل كتاب المجموعة", findNode(main.children, "كتاب رئيسي"))

        val contexts = FolderClassifier.contextsByPath(roots, fallback)
        assertNull("كتاب المجموعة (SPLIT) بلا مؤلف — الجذر حاوية فقط", contexts.getValue("كتاب رئيسي").authorName)
        assertNull(contexts.getValue("كتاب رئيسي").seriesFolderName)
        assertEquals("كتاب رئيسي", contexts.getValue("كتاب رئيسي/جزء فرعي").authorName)
        assertNull(contexts.getValue("كتاب رئيسي/جزء فرعي").seriesFolderName)
    }

    // ── D: عمق ≥ 2 مخلوط باسم غير عام لم يستوفِ شرط الحاوية → SERIES + كتاب شقيق + كتب أحفاده ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (سلسلة عمق 2 بملفات مباشرة + كتابها الشقيق الاصطناعي) — معطّل بعلم StrictModeFlags")
    fun depthTwoMixedNonGenericNameBecomesSeriesWithSiblingAndSubfolderBooks() {
        val roots = rootsOf(
            "أحمد خالد توفيق/paranormal/direct.mp3",
            "أحمد خالد توفيق/paranormal/book1/01.mp3",
            "أحمد خالد توفيق/paranormal/book2/02.mp3"
        )

        val paranormal = findNode(roots, "أحمد خالد توفيق/paranormal")!!
        assertEquals("D: مخلوط عمق 2 باسم غير عام بلا شرط حاوية → سلسلة", FolderKind.SERIES, paranormal.kind)
        assertEquals(setOf("book1", "book2"), paranormal.children.filter { !it.synthetic }.map { it.name }.toSet())
        val sibling = paranormal.children.single { it.synthetic }
        assertEquals(FolderKind.BOOK, sibling.kind)
        assertEquals("paranormal", sibling.name)

        val contexts = FolderClassifier.contextsByPath(roots, fallback)
        assertEquals("paranormal", contexts.getValue("أحمد خالد توفيق/paranormal").seriesFolderName)
        assertEquals("paranormal", contexts.getValue("أحمد خالد توفيق/paranormal/book1").seriesFolderName)
        assertEquals("أحمد خالد توفيق", contexts.getValue("أحمد خالد توفيق/paranormal/book1").authorName)
    }

    // ── B: حاوية عمق 1 خالصة = AUTHOR؛ عمق 2 = مسلسلة؛ «سلسلة X» تتغلب على AUTHOR ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (أبناء المؤلف المباشرون بملفات يحملون دور SERIES — يتقلّصون إلى BOOK تحت العلم) — معطّل بعلم StrictModeFlags")
    fun pureContainerRolesFollowDepthAndSeriesHint() {
        val roots = rootsOf(
            "أحمد خالد توفيق/فانتازيا/01.mp3",
            "روائع/سلسلة ذهبية/01.mp3",
            "روائع/كتاب صغير/01.mp3"
        )
        assertEquals(FolderKind.AUTHOR, findNode(roots, "أحمد خالد توفيق")!!.kind)
        assertEquals(FolderKind.SERIES, findNode(roots, "أحمد خالد توفيق/فانتازيا")!!.kind)
        assertEquals("روائع", findNode(roots, "روائع")!!.name)
        assertEquals(FolderKind.AUTHOR, findNode(roots, "روائع")!!.kind)
        assertEquals(FolderKind.SERIES, findNode(roots, "روائع/سلسلة ذهبية")!!.kind)
        assertEquals("الكتاب الصغير ابن المؤلف الاسم غير عام → سلسلة", FolderKind.SERIES, findNode(roots, "روائع/كتاب صغير")!!.kind)
    }

    // ── C: اسم يبدأ بـ «سلسلة/Series» حتى في العمق 1 → SERIES ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (سلسلة على ملفات مباشرة بكتابها الاصطناعي) — معطّل بعلم StrictModeFlags")
    fun seriesHintForcesSeriesEvenAtDepthOneAndOnFiles() {
        val roots = rootsOf(
            "سلسلة أفلام النار/01.mp3",
            "Series A/01.mp3"
        )
        val nile = findNode(roots, "سلسلة أفلام النار")!!
        assertEquals(FolderKind.SERIES, nile.kind)
        assertTrue(nile.children.any { it.synthetic })
        assertEquals(FolderKind.SERIES, findNode(roots, "Series A")!!.kind)
        assertTrue(findNode(roots, "Series A")!!.children.any { it.synthetic })
    }

    // ── F: ابن المؤلف المباشر — اسم غير عام → سلسلة، اسم عام → كتاب ──

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (ابنُ المؤلف ذو الاسم غير العام والملفات مباشرة يصنّف SERIES) — معطّل بعلم StrictModeFlags")
    fun directChildOfAuthorIsSeriesUnlessGenericBookName() {
        val roots = rootsOf(
            "نبيل فاروق/ملف المستقبل/01.mp3",
            "كريم قنديل/book1/01.mp3",
            "كريم قنديل/book2/01.mp3"
        )
        assertEquals(FolderKind.SERIES, findNode(roots, "نبيل فاروق/ملف المستقبل")!!.kind)
        assertEquals(FolderKind.BOOK, findNode(roots, "كريم قنديل/book1")!!.kind)
        assertEquals(FolderKind.BOOK, findNode(roots, "كريم قنديل/book2")!!.kind)
    }

    // ── أسماء الكتب العامة/غير العامة ──

    @Test
    fun genericBookNamesAreRecognized() {
        listOf(
            "book1", "Book 3", "Book 10", "part 2", "ep 5", "episode 7", "track 01",
            "chapter 3", "ch 1", "cd 2", "disc 1", "disk 2", "vol 3", "volume 1",
            "01", "12", "٣", "الجزء الثالث", "الكتاب الأول", "جزء 3", "ملف 5",
            "كتاب رئيسي", "جزء فرعي", "الجزء", "ملف"
        ).forEach { name ->
            assertTrue("يجب أن يكون اسمًا عامًا: $name", FolderClassifier.isGenericBookName(name))
        }
    }

    @Test
    fun nonGenericNamesAreNotMistakenForBooks() {
        listOf(
            "فانتازيا", "ما وراء الطبيعة", "سافاري", "paranormal", "ملف المستقبل",
            "أحمد خالد توفيق", "روائع", "كتاب صغير", "Novel Series"
        ).forEach { name ->
            assertTrue("لن يعد اسمًا عامًا: $name", !FolderClassifier.isGenericBookName(name))
        }
    }

    @Test
    fun seriesNamesAreRecognizedExplicitly() {
        assertTrue(FolderClassifier.isSeriesName("سلسلة أفلام النار"))
        assertTrue(FolderClassifier.isSeriesName("Series A"))
        assertTrue(!FolderClassifier.isSeriesName("فانتازيا"))
        assertTrue(!FolderClassifier.isSeriesName("ما وراء الطبيعة"))
    }

    // ── contextForPath: قاعدة العمق الاصطناعية (إعادة التصنيف بلا فحص) ──

    @Test
    fun contextForPathFollowsSegmentRules() {
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", "فانتازيا"),
            FolderClassifier.contextForPath("أحمد خالد توفيق/فانتازيا", fallback)
        )
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", null),
            FolderClassifier.contextForPath("أحمد خالد توفيق/book1", fallback)
        )
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", "paranormal"),
            FolderClassifier.contextForPath("أحمد خالد توفيق/paranormal/book1", fallback)
        )
        assertEquals("الكتاب العميق-1 بلا مؤلف (الجذر حاوية فقط، لا يُنسب اسم الجذر)", AuthorSeriesContext(null, null), FolderClassifier.contextForPath("Book", fallback))
    }

    // ── Bug 5: إيقاف التصنيف التلقائي للسلاسل (autoSeries = false) ──

    private fun classify(autoSeries: Boolean, vararg relative: String): List<FolderNode> =
        FolderClassifier.classify(files(*relative), autoSeries)

    @Test
    @Ignore("سلوك قديم: يتطلب ENABLE_SYNTHETIC_BOOKS=true (مجلد عمق 2 بملفات مباشرة يبقى SERIES بكتابه الاصطناعي) — معطّل بعلم StrictModeFlags")
    fun autoSeriesOnKeepsDepthTwoFolderAsSeries() {
        val roots = classify(true, "أحمد خالد توفيق/فانتازيا/01.mp3")
        assertEquals(FolderKind.SERIES, findNode(roots, "أحمد خالد توفيق/فانتازيا")!!.kind)
        assertTrue(findNode(roots, "أحمد خالد توفيق/فانتازيا")!!.children.any { it.synthetic })
    }

    @Test
    fun autoSeriesOffMapsDepthTwoFolderToBookInsteadOfSeries() {
        val roots = classify(false, "أحمد خالد توفيق/فانتازيا/01.mp3")
        val fabtazia = findNode(roots, "أحمد خالد توفيق/فانتازيا")!!
        assertEquals("مع التصنيف المحافظ يصبح مجلد العمق ٢ كتابًا لا سلسلة", FolderKind.BOOK, fabtazia.kind)
        val contexts = FolderClassifier.contextsByPath(roots, fallback)
        assertEquals("أحمد خالد توفيق", contexts.getValue("أحمد خالد توفيق/فانتازيا").authorName)
        assertNull("بلا سلسلة في التصنيف المحافظ", contexts.getValue("أحمد خالد توفيق/فانتازيا").seriesFolderName)
    }

    @Test
    fun autoSeriesOffNeverProducesSeriesNode() {
        val roots = classify(
            false,
            "أحمد خالد توفيق/paranormal/direct.mp3",
            "أحمد خالد توفيق/paranormal/book1/01.mp3",
            "أحمد خالد توفيق/paranormal/book2/02.mp3",
            "نبيل فاروق/ملف المستقبل/01.mp3"
        )
        fun assertNoSeries(nodes: List<FolderNode>) {
            nodes.forEach { node ->
                assertTrue("لا سلسلة في التصنيف المحافظ: ${node.path}", node.kind != FolderKind.SERIES)
                assertNoSeries(node.children)
            }
        }
        assertNoSeries(roots)
        assertEquals(
            "paranormal مخلوط عمق ٢ → كتاب مجموعة، وملف المستقبل ابن المؤلف غير العام → كتاب",
            FolderKind.BOOK, findNode(roots, "أحمد خالد توفيق/paranormal")!!.kind
        )
        assertEquals(FolderKind.BOOK, findNode(roots, "نبيل فاروق/ملف المستقبل")!!.kind)
    }

    @Test
    fun autoSeriesOffIgnoresSeriesNameHint() {
        val roots = classify(false, "روائع/سلسلة ذهبية/01.mp3")
        assertEquals(FolderKind.BOOK, findNode(roots, "روائع/سلسلة ذهبية")!!.kind)
        assertEquals("روائع تبقى مؤلفًا (ليست سلسلة)", FolderKind.AUTHOR, findNode(roots, "روائع")!!.kind)
    }

    @Test
    fun autoSeriesOffKeepsAuthorContainers() {
        val roots = classify(false, "أحمد خالد توفيق/فانتازيا/01.mp3", "كريم قنديل/book1/01.mp3")
        assertEquals("عمق 1 يبقى حاوية مؤلف", FolderKind.AUTHOR, findNode(roots, "أحمد خالد توفيق")!!.kind)
        assertEquals(FolderKind.AUTHOR, findNode(roots, "كريم قنديل")!!.kind)
        assertEquals(FolderKind.BOOK, findNode(roots, "كريم قنديل/book1")!!.kind)
    }

    @Test
    fun contextForPathAutoSeriesOffHasNoSeries() {
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", null),
            FolderClassifier.contextForPath("أحمد خالد توفيق/فانتازيا", fallback, autoSeries = false)
        )
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", "paranormal"),
            FolderClassifier.contextForPath("أحمد خالد توفيق/paranormal/book1", fallback, autoSeries = true)
        )
        assertEquals(
            AuthorSeriesContext("أحمد خالد توفيق", null),
            FolderClassifier.contextForPath("أحمد خالد توفيق/paranormal/book1", fallback, autoSeries = false)
        )
    }

    // ── EMPTY: حاويات بلا صوت مباشر تبقى في الهرمية، والمدخل الخالي يُرجع شجرة فارغة ──

    @Test
    fun containersWithoutDirectAudioRemainHierarchyAndEmptyInputIsEmpty() {
        val roots = rootsOf("أحمد خالد توفيق/فانتازيا/01.mp3")
        assertEquals("أحمد حاوية بلا صوت مباشر تبقى AUTHOR", FolderKind.AUTHOR, findNode(roots, "أحمد خالد توفيق")!!.kind)
        assertEquals(
            "فانتازيا بملفات مباشرة تتقلّص تحت ENABLE_SYNTHETIC_BOOKS=false إلى كتاب (لا سلسلة ولا كتاب اصطناعي)",
            FolderKind.BOOK, findNode(roots, "أحمد خالد توفيق/فانتازيا")!!.kind
        )
        assertEquals(0, FolderClassifier.classify(emptyList()).size)
    }

    // ── StrictModeFlags (ENABLE_SYNTHETIC_BOOKS=false): عقد المصنِّف الصارمة ──

    @Test
    fun strictFlagsKeepPureContainerRolesAndCollapseDirectFileContainersToBook() {
        val roots = rootsOf(
            "أحمد خالد توفيق/فانتازيا/01.mp3",
            "روائع/سلسلة ذهبية/01.mp3",
            "Variety/01.mp3",
            "Variety/Series A/01.mp3",
            "Variety/Series B/01.mp3",
            "كريم قنديل/book1/01.mp3"
        )

        fun assertNoSynthetic(nodes: List<FolderNode>) {
            nodes.forEach { node ->
                assertTrue("لا كتب اصطناعية تحت العلم المعطّل: ${node.path}", !node.synthetic)
                assertNoSynthetic(node.children)
            }
        }
        assertNoSynthetic(roots)

        assertEquals("حاوية خالصة عمق 1 = AUTHOR", FolderKind.AUTHOR, findNode(roots, "أحمد خالد توفيق")!!.kind)
        assertEquals("حاوية خالصة عمق 1 = AUTHOR", FolderKind.AUTHOR, findNode(roots, "روائع")!!.kind)
        assertEquals("حاوية خالصة عمق 1 = AUTHOR", FolderKind.AUTHOR, findNode(roots, "كريم قنديل")!!.kind)
        assertEquals(
            "مجلد بعمق 2 وملفات مباشرة (كان سلسلة اصطناعية) يتقلّص إلى BOOK",
            FolderKind.BOOK, findNode(roots, "أحمد خالد توفيق/فانتازيا")!!.kind
        )
        assertEquals(
            "مجلد بعمق 2 وملفات مباشرة (تلميح «سلسلة») يتقلّص إلى BOOK",
            FolderKind.BOOK, findNode(roots, "روائع/سلسلة ذهبية")!!.kind
        )
        assertEquals(
            "حاوية مخلوطة عمقها 1 بملفات مباشرة تنهار إلى كتاب عادي بملفاتها (لا AUTHOR)",
            FolderKind.BOOK, findNode(roots, "Variety")!!.kind
        )
        assertEquals(FolderKind.BOOK, findNode(roots, "Variety/Series A")!!.kind)
        assertEquals(FolderKind.BOOK, findNode(roots, "Variety/Series B")!!.kind)
        assertEquals("الاسم العام لابن المؤلف = كتاب", FolderKind.BOOK, findNode(roots, "كريم قنديل/book1")!!.kind)

        val books = FolderClassifier.flattenBookNodes(roots)
        assertEquals(
            "كل مجلد يحوي ملفات يبقى ناتجًا ككتاب عادي — لا تُفقد أي ملفات (variety, series a, series b, فانتازيا, سلسلة ذهبية, book1)",
            6, books.size
        )
    }
}