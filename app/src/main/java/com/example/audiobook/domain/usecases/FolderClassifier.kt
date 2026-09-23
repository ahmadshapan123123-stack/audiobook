package com.example.audiobook.domain.usecases

import com.example.audiobook.data.localfilesystem.ScanFile

/**
 * تصنيف هرمية المجلدات المكتشفة أثناء الفحص (نقٍّ خالص قابل للاختبار).
 *
 * البنية المرجعية (المنتظَرة من سيناريو VERIFY):
 *   root/
 *   ├── أحمد خالد توفيق/                  ← AUTHOR (حاوية)
 *   │   ├── فانتازيا/                     ← SERIES (اسم غير عام لابن المؤلف)
 *   │   │   └── 01.mp3                    ← كتاب (اصطناعي عند وجود ملفات مباشرة بداخلها)
 *   │   ├── ما وراء الطبيعة/              ← SERIES
 *   │   ├── سافاري/                       ← SERIES
 *   │   ├── paranormal/                   ← SERIES (حاوية بلا ملفات مباشرة)
 *   │   │   ├── book1/01.mp3              ← BOOK
 *   │   │   └── book2/01.mp3              ← BOOK
 *   │   └── standalone_book.mp3           ← كتاب مباشر للمؤلف (بلا سلسلة)
 *   ├── نبيل فاروق/                       ← AUTHOR
 *   │   └── ملف المستقبل/                 ← SERIES (اسم ذو ملفات مباشرة واسم غير عام)
 *   │       └── 01.mp3                    ← كتاب داخل السلسلة
 *   └── كريم قنديل/                       ← AUTHOR
 *       ├── book1/01.mp3                  ← BOOK (اسم عام → كتاب وليس سلسلة)
 *       └── book2/01.mp3                  ← BOOK (لا دمج بينهما)
 *
 * قواعد المواصفة:
 *  - EMPTY: مجلد بلا أي صوت (مباشر أو في أحفاد) → يُقصّ من الشجرة.
 *  - A: مجلد «مخلوط» (ملفات مباشرة + مجلدات فرعية تحوي صوتًا):
 *      · حاوية (Author في العمق 1 / Series في العمق ≥ 2) تلبس ملفاتها المباشرة
 *        كتابًا «اصطناعيًا» إذا المجلدات الفرعية ≥ 2 والعدد الكلي للملفات داخلها
 *        ≥ 3، أو إذا كان الاسم يطابق «سلسلة X» (C).
 *      · عمق 1 لم يستوفِ الشرط: اسم عام → SPLIT (BOOK)، وإلا AUTHOR (حاوية مؤلف
 *        تحوي كتابها الاصطناعي — مثل «أحمد خالد توفيق»).
 *      · عمق ≥ 2 لم يستوفِ الشرط: اسم غير عام → SERIES (D) — سلسلة «مجموعة» تلبس
 *        اسم المحتوى (مثل paranormal بملفات مباشرة)، اسم عام → SPLIT.
 *      («كتاب رئيسي» بأجزائه مثال على SPLIT.)
 *  - B: حاوية (بلا ملفات مباشرة، وأحفادها تحوي صوتًا):
 *      · عمقها 1 → AUTHOR.
 *      · عمقها ≥ 2 → SERIES (مستوى وسطي موثوق، ومنها ابنُ AUTHOR).
 *  - C: اسمٌ يبدأ بـ«سلسلة/Series» → SERIES (يتغلّب على AUTHOR بعمق 1).
 *  - F: مجلد يحوي ملفات مباشرة وهو ابن مباشر لمجلد AUTHOR:
 *      · اسم عام (رقم، «كتابN»، «جزء»، «ملف»، …) → BOOK.
 *      · اسم غير عام → SERIES — كتاب «مجموعة» يلبس اسم «مجموعة» (مثل «ملف المستقبل»).
 */
enum class FolderKind { AUTHOR, SERIES, BOOK, EMPTY }

/** سياق كتاب/إصدار مُستخرَج من بنية المجلد (دون لمس قاعدة البيانات). */
data class AuthorSeriesContext(
    val authorName: String,
    val seriesFolderName: String?
)

/**
 * عقدة مجلد بعد التصنيف: المسار الكامل (نسبيًا من الجذر)، ملفاته المباشرة
 * وأطفاله المصنَّفين. الحاويات الفارغة تُقصّ بالكامل.
 *
 * `synthetic = true` تعني أن النود وُلدت من ملفات مباشرة داخل حاوية
 * (Author/Series) أو من نود SPLIT: فهي «كتاب» يحمل مسار الحاوية نفسها
 * ويُضاف كابن لها. النود الاصطناعية لا تُنتج مجلدًا شقيقًا ولا تُعدّ أبناء.
 */
data class FolderNode(
    val path: String,
    val name: String,
    val depth: Int,
    val kind: FolderKind,
    val directFiles: List<ScanFile>,
    val children: List<FolderNode>,
    val synthetic: Boolean = false
) {
    fun hasDirectAudio(): Boolean = directFiles.isNotEmpty()
}

object FolderClassifier {

    /**
     * يبني شجرة المجلدات المصنَّفة من قائمة الملفات المسطحة. تُرجع العقد من العمق 1.
     *
     * `autoSeries` (إعداد «تصنيف تلقائي للسلاسل»): عند إيقافه لا تُنتج أي دور
     * SERIES إطلاقًا — مجلدات العمق ٢ (السلاسل المحتملة) تصبح BOOK بدلًا من
     * SERIES، فتبقى السلاسل حصرًا نتيجة إجراء المستخدم اليدوي. القواعد نفسها
     * لم تتغيّر؛ الإيقاف يحوّل مخرجاتها من SERIES إلى BOOK فقط.
     */
    fun classify(files: List<ScanFile>, autoSeries: Boolean = true): List<FolderNode> {
        if (files.isEmpty()) return emptyList()

        val filesByPath = LinkedHashMap<String, MutableList<ScanFile>>()
        files.forEach { file -> filesByPath.getOrPut(file.folderPath, ::mutableListOf).add(file) }

        val allPaths = filesByPath.keys.toMutableSet()
        filesByPath.keys.forEach { path ->
            var parent = path.substringBeforeLast('/', "")
            while (parent.isNotEmpty()) {
                allPaths += parent
                parent = parent.substringBeforeLast('/', "")
            }
        }

        val childrenByPath = allPaths.groupBy { it.substringBeforeLast('/', "") }
        val depthOf = { path: String -> path.split('/').filter(String::isNotBlank).size }

        // 1) عدد الملفات الصوتية في كل شجرة فرعية (مباشر + أحفاد) — للتجميع من الأسفل.
        val subtreeAudioFiles = HashMap<String, Int>()
        allPaths.sortedBy(String::length).reversed().forEach { path ->
            val own = filesByPath[path].orEmpty().size
            val descendants = childrenByPath[path].orEmpty().sumOf { subtreeAudioFiles[it] ?: 0 }
            subtreeAudioFiles[path] = own + descendants
        }

        // 2) أدوار هذه الجولة (مستقلة عن أدوار الأبناء): كل مجلد يحمل دورًا واحدًا
        //    بواسطة قاعدة A/B/C/D/F حسب وظيفته المباشرة وموضعه واسمه فقط.
        val roleOf = HashMap<String, FolderKind>()
        allPaths.sortedBy(String::length).forEach { path ->
            if (path.isEmpty()) return@forEach
            val directAudio = filesByPath[path].orEmpty().isNotEmpty()
            val audioChildren = childrenByPath[path].orEmpty()
                .filter { (subtreeAudioFiles[it] ?: 0) > 0 }
            val hasAudioChildren = audioChildren.isNotEmpty()
            if (!directAudio && !hasAudioChildren) return@forEach
            val name = path.substringAfterLast('/')
            val parentKind = if (path.indexOf('/') == -1) null else roleOf[path.substringBeforeLast('/', "")]
            val role = when {
                directAudio && hasAudioChildren -> classifyMixed(
                    depth = depthOf(path),
                    parentKind = parentKind,
                    name = name,
                    subfolders = audioChildren.size,
                    filesInSubfolders = audioChildren.sumOf { subtreeAudioFiles[it] ?: 0 },
                    autoSeries = autoSeries
                ) // A: حاوية/SPLIT أو D: سلسلة مجموعة
                directAudio -> when {
                    isSeriesName(name) -> if (autoSeries) FolderKind.SERIES else FolderKind.BOOK  // C مع ملفات مباشرة
                    parentKind == FolderKind.AUTHOR && !isGenericBookName(name) ->
                        if (autoSeries) FolderKind.SERIES else FolderKind.BOOK                      // F
                    parentKind == FolderKind.AUTHOR -> FolderKind.BOOK                        // F: اسم عام
                    else -> FolderKind.BOOK
                }
                hasAudioChildren -> containerRole(depthOf(path), parentKind, name, autoSeries)      // B + C
                else -> FolderKind.EMPTY
            }
            roleOf[path] = role
        }

        // 3) تجميع الشجرة من الأسفل للأعلى (الحفيد قبل الأب) مع إدخال «الكتب الاصطناعية».
        val nodes = HashMap<String, FolderNode>()
        allPaths.sortedBy(String::length).reversed().forEach { path ->
            if (path.isEmpty()) return@forEach
            val role = roleOf[path] ?: return@forEach
            val realChildren = childrenByPath[path].orEmpty()
                .mapNotNull { nodes[it] }
                .sortedWith(NODE_ORDER)
            val directFiles = filesByPath[path].orEmpty()
            val producesSyntheticBook =
                (role == FolderKind.AUTHOR || role == FolderKind.SERIES) && directFiles.isNotEmpty()
            val children = if (producesSyntheticBook) {
                (realChildren + FolderNode(
                    path = path,
                    name = path.substringAfterLast('/'),
                    depth = depthOf(path),
                    kind = FolderKind.BOOK,
                    directFiles = directFiles,
                    children = emptyList(),
                    synthetic = true
                )).sortedWith(NODE_ORDER)
            } else {
                realChildren
            }
            nodes[path] = FolderNode(
                path = path,
                name = path.substringAfterLast('/'),
                depth = depthOf(path),
                kind = role,
                directFiles = if (producesSyntheticBook) emptyList() else directFiles,
                children = children,
                synthetic = false
            )
        }

        return allPaths
            .filter { depthOf(it) == 1 && nodes.containsKey(it) }
            .sortedBy(String::length)
            .mapNotNull { nodes[it] }
            .sortedWith(NODE_ORDER)
    }

    /**
     * قاعدة A للمجلد المخلوط (ملفات مباشرة + مجلدات فرعية تحوي صوتًا).
     *  - «سلسلة/Series X» → SERIES (C) مهما كان العمق.
     *  - استوفى شرط الحاوية (subfolders ≥ 2 والملفات في الأحفاد ≥ 3) →
     *    دور الحاوية حسب العمق: عمق 1 → AUTHOR، وإلا SERIES.
     *  - عمق 1 ولم يُستوفَ الشرط: اسم عام → SPLIT (BOOK)، وإلا AUTHOR
     *    (حاوية مؤلف تحوي كتابها الاصطناعي — مثل «أحمد خالد توفيق»).
     *  - عمق ≥ 2 ولم يُستوفَ الشرط: اسم غير عام → D (SERIES — سلسلة مجموعة
     *    تلبس اسم «المجموعة» مثل paranormal بملفات مباشرة)، اسم عام → SPLIT.
     *
     * مع `autoSeries = false` تُحوَّل كل مخرجات SERIES إلى BOOK (التصنيف المحافظ).
     */
    private fun classifyMixed(
        depth: Int,
        parentKind: FolderKind?,
        name: String,
        subfolders: Int,
        filesInSubfolders: Int,
        autoSeries: Boolean
    ): FolderKind = when {
        isSeriesName(name) -> if (autoSeries) FolderKind.SERIES else FolderKind.BOOK          // C
        subfolders >= 2 && filesInSubfolders >= 3 -> containerRole(depth, parentKind, name, autoSeries)  // A
        depth == 1 -> if (isGenericBookName(name)) FolderKind.BOOK else FolderKind.AUTHOR
        !isGenericBookName(name) -> if (autoSeries) FolderKind.SERIES else FolderKind.BOOK     // D (عمق ≥ 2)
        else -> FolderKind.BOOK                                                                // A-else: SPLIT
    }

    /** قاعدة B + C للحاوية الخالصة: العمق 1 → AUTHOR، وإلا SERIES، و»سلسلة X» تضبط دائمًا. */
    private fun containerRole(depth: Int, parentKind: FolderKind?, name: String, autoSeries: Boolean): FolderKind = when {
        isSeriesName(name) -> if (autoSeries) FolderKind.SERIES else FolderKind.BOOK
        depth == 1 -> FolderKind.AUTHOR
        else -> if (autoSeries) FolderKind.SERIES else FolderKind.BOOK
    }

    /** كل عقد الكتاب التي تُنتج كتابًا (سواء كانت عادية أو اصطناعية). */
    fun flattenBookNodes(nodes: List<FolderNode>, out: MutableList<FolderNode> = mutableListOf()): List<FolderNode> {
        nodes.forEach { node ->
            if (node.kind == FolderKind.BOOK) out += node
            flattenBookNodes(node.children, out)
        }
        return out
    }

    /**
     * سياق (المؤلف/السلسلة) لكل كتاب في الشجرة — تجوّل من الأعلى:
     * عند الدخول إلى AUTHOR يتحول الاسم إلى مؤلف، وعند الدخول إلى SERIES
     * يتحول الاسم إلى سلسلة؛ الكتب (العادية أو الاصطناعية) ترث السياق الجاري
     * وتُسجَّل بمسارها، فتحصل الكتب الموجودة أعلى مجلد الحاوية مباشرة على
     * سياق الحاوية (= المسار الذي تشاركه نود الكتاب الاصطناعية).
     *
     * ملاحظة: في SPLIT (مجموعة كتب) الكتاب الأب يحمل سياقه من مساره، وأبناؤه
     * يرثون مؤلف «اسم المجموعة» (لأن دور الأب BOOK لا يغيّر السياق) — نفس
     * سلوك old contextForPath لعمق العنصر الفرعي.
     */
    fun contextsByPath(nodes: List<FolderNode>, fallbackAuthor: String): Map<String, AuthorSeriesContext> {
        val contexts = LinkedHashMap<String, AuthorSeriesContext>()
        fun visit(node: FolderNode, authorName: String?, seriesName: String?) {
            if (node.kind == FolderKind.BOOK) {
                val segments = node.path.split('/').filter(String::isNotBlank)
                val author = authorName
                    ?: if (segments.size >= 2) segments[0]
                    else fallbackAuthor
                contexts[node.path] = AuthorSeriesContext(author, seriesName)
            }
            val childAuthor = if (node.kind == FolderKind.AUTHOR) node.name else authorName
            val childSeries = if (node.kind == FolderKind.SERIES) node.name else seriesName
            node.children.forEach { visit(it, childAuthor, childSeries) }
        }
        nodes.forEach { visit(it, null, null) }
        return contexts
    }

    /**
     * اشتقاق سياق (المؤلف/السلسلة) لمجلد من مساره مباشرة — يُستخدم عندما لا
     * تتوفر قائمة الملفات (إعادة التصنيف بلا فحص). يعادل [contextsByPath]
     * في حظيرة العمق لأنفس المسارات:
     *  - المؤلف = المقطع الأول (عمق ≥ 2) أو الاسم الاحتياطي.
     *  - السلسلة = المقطع قبل الأخير للعمق ≥ 3؛ ولعمق 2 = المقطع الأخير فقط
     *    إذا كان اسمًا غير عام (اختصار قاعدة F — «فانتازيا» سلسلة، ولا سلسلة
     *    لـ«كريم قنديل/book1»).
     *
     * مع `autoSeries = false` لا تُستخرج سلسلة إطلاقًا (التصنيف المحافظ).
     */
    fun contextForPath(path: String, fallbackAuthor: String, autoSeries: Boolean = true): AuthorSeriesContext {
        val segments = path.split('/').filter(String::isNotBlank)
        val author = if (segments.size >= 2) segments[0] else fallbackAuthor
        val series = when {
            !autoSeries -> null
            segments.size == 2 -> if (!isGenericBookName(segments[1])) segments[1] else null
            segments.size >= 3 -> segments[segments.size - 2]
            else -> null
        }
        return AuthorSeriesContext(author, series)
    }

    /** اسم «عام» لكتاب: رقم صرف، أو كلمة كتاب/جزء/ملف… (عربي أو لاتيني) مع رقم/ترتيب. */
    fun isGenericBookName(name: String): Boolean {
        val n = name.trim()
        if (n.isEmpty()) return true
        return GENERIC_BOOK_NAME_REGEX.matches(n)
    }

    /** اسم «سلسلة» صريح: يبدأ بـ «سلسلة» أو «Series». */
    fun isSeriesName(name: String): Boolean = SERIES_NAME_REGEX.matches(name.trim())

    /** ترتيب مستقر للأشقاء حسب المسار (بلا اعتماد على Locale في المقارنة). */
    private val NODE_ORDER = compareBy<FolderNode> { it.path }

    private val GENERIC_BOOK_NAME_REGEX = Regex(
        // لاتيني: book/part/ep/episode/track/chapter/ch/cd/disc/disk/vol/volume (+ رقم اختياري)
        """(?i)^(?:book|part|episode|ep|track|chapter|ch|cd|disc|disk|vol|volume)(?:[\s\-_.:]*\d{0,4})?$""" +
            // أرقام صرفة (عربية أو لاتينية)
            """|^[\d٠-٩]{1,4}$""" +
            // عربي: كلمة كتاب/جزء/حلقة/… مع رقم أو ترتيب اختياري (لا سوى ذلك حتى النهاية)
            """|^(?:ال)?(?:كتاب|جزء|حلقة|فصل|جلد|ملف|عدد|مجلد)\s*(?:\d{1,4}|[٠-٩]{1,4}|الأول|الثاني|الثالث|الرابع|""" +
            """الخامس|السادس|السابع|الثامن|التاسع|العاشر|أول|ثاني|ثالث|رابع|خامس|سادس|سابع|ثامن|تاسع|عاشر|رئيسي|فرعي)?$"""
    )

    private val SERIES_NAME_REGEX = Regex("""(?i)^\s*(?:سلسلة|series)\b.*""")
}