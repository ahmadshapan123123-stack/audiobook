package com.example.audiobook.domain.usecases

/**
 * المصنِّف الصارم لهرمية المجلدات (المرحلة 3) — بلا أي قواعد استدلالية على الأسماء.
 *
 * البنية مُحدَّدة بمحتوى المجلد وعمقه فقط (لا دمج، لا كتب اصطناعية، لا SPLIT، لا
 * تلميحات ألبوم، لا تجاوز عنوان مضمّن، لا كشف أسماء عامة، لا سمع بإعداد «تصنيف
 * تلقائي للسلاسل»):
 *
 *  - العمق 0 (الجذر) = حاوية فقط: ملفاته المباشرة كتب مستقلة غير مصنَّفة، كل ملف
 *    كتاب مستقل (بلا مؤلف وبلا سلسلة) عنوانه اسم الملف بلا امتداد، وإلا سقوطًا
 *    إلى اسم الجذر الوارد (rootName).
 *  - العمق 1 = AUTHOR دائمًا وبصرف النظر عن الاسم (حاوية مؤلف تحوي كتبًا):
 *    ملفاته المباشرة كتب مستقلة لكل ملف (بمؤلف المجلد نفسه وبلا سلسلة)
 *    بعناوين أسماء الملفات بلا امتداد.
 *  - العمق 2 (ابن المؤلف) دائمًا سلسلة تحت مؤلفه:
 *      · يحوي ملفات مباشرة → SERIES + BOOK لكل ملف (كل ملف كتاب مستقل، عنوانه اسم الملف بلا امتداد).
 *      · يحوي مجلداتٍ حاوية صوتًا → SERIES (حاوية) وكتبها أبناؤها في العمق ≥ 3.
 *  - العمق ≥ 3:
 *      · يحوي ملفات مباشرة → BOOK (بملفاته).
 *      · بلا ملفات مباشرة ويحوي مجلداتٍ حاوية صوتًا → BOOK حاوية (يتكرر
 *        الغوص لأحفاده التي تنتج كتبًا). عنوانه اسم مجلده، مؤلفه المقطع الأول،
 *        وسلسلته جدُّه العمق-2 إن كان حاوية SERIES، وإلا بلا سلسلة.
 */
object StrictFolderClassifier {

    /** ملف صوتي مُدخل كما يراه المصنِّف (مستقل عن [com.example.audiobook.data.localfilesystem.ScanFile]). */
    data class InputFile(
        val uri: String,
        val filename: String,
        val folderPath: String,
        val sizeBytes: Long,
        val durationMs: Long
    )

    /** كتاب مُصنَّف: اسم المؤلف/السلسلة كما يظهران في قاعدة البيانات، عنوانه ومساره وملفاته. */
    data class ClassifiedBook(
        val authorName: String?,
        val seriesName: String?,
        val bookTitle: String,
        val folderPath: String,
        val files: List<InputFile>
    )

    /** كتاب في شجرة المعاينة (المرحلة 5) — عرض فقط، بلا أي كتابة في القاعدة. */
    data class PreviewBook(
        val title: String,
        val folderPath: String,
        val fileCount: Int,
        val totalDurationMs: Long
    )

    /** سلسلة في شجرة المعاينة: كتب المتكلم ضمن حاوية عمق ≥ 2. */
    data class PreviewSeries(
        val name: String,
        val folderPath: String,
        val books: List<PreviewBook>
    ) {
        val fileCount: Int get() = books.sumOf { it.fileCount }
        val totalDurationMs: Long get() = books.sumOf { it.totalDurationMs }
    }

    /** مؤلف في شجرة المعاينة: كتبه المباشرة + سلاسله. */
    data class PreviewAuthor(
        val name: String,
        val books: List<PreviewBook>,
        val series: List<PreviewSeries>
    ) {
        val fileCount: Int get() = books.sumOf { it.fileCount } + series.sumOf { it.fileCount }
        val totalDurationMs: Long get() = books.sumOf { it.totalDurationMs } + series.sumOf { it.totalDurationMs }
    }

    /**
     * شجرة التصنيف كاملة كما ستراها للاعتماد (المرحلة 5): مؤلفون ← (سلاسل) ← كتب
     * + الكتب المستقلة غير المصنَّفة أعلى الشجرة. بلا أي كتابة في قاعدة البيانات.
     */
    data class PreviewTree(
        val rootName: String,
        val authors: List<PreviewAuthor>,
        val unassignedBooks: List<PreviewBook>,
        val totalFiles: Int
    )

    private const val AUTHOR = "AUTHOR"
    private const val SERIES = "SERIES"
    private const val BOOK = "BOOK"

    /**
     * يصنّف قائمة الملفات المسطحة إلى كتب. `rootName` اسم الجذر للاحتياط عند
     * غياب امتداد اسمي لملف مباشر على الجذر. يُرجع كتبًا بترتيب مستقر
     * (بالعمق فالحرف للمسارات، وترتيب الإدخال لملفات الحاويات المباشرة).
     */
    fun classify(files: List<InputFile>, rootName: String): List<ClassifiedBook> {
        if (files.isEmpty()) return emptyList()

        val byFolder = LinkedHashMap<String, MutableList<InputFile>>()
        files.forEach { file -> byFolder.getOrPut(file.folderPath, ::mutableListOf).add(file) }

        // كل المسارات (الحاملة صوتًا + أجدادها) لبناء شجرة كاملة.
        val allPaths = LinkedHashSet<String>()
        byFolder.keys.forEach { path ->
            var current = path
            while (current.isNotEmpty()) {
                allPaths += current
                current = current.substringBeforeLast('/', "")
            }
        }

        val depthOf = { path: String -> path.split('/').filter(String::isNotBlank).size }
        val childrenOf = allPaths.groupBy { it.substringBeforeLast('/', "") }

        // هل شجرة المجلد تحوي صوتًا؟ (مباشر أو في أحفاد) — من الأسفل للأعلى.
        val subtreeHasAudio = HashMap<String, Boolean>()
        allPaths.sortedBy(String::length).reversed().forEach { path ->
            subtreeHasAudio[path] = byFolder[path].orEmpty().isNotEmpty() ||
                childrenOf[path].orEmpty().any { subtreeHasAudio[it] == true }
        }

        // دور المجلد بحسب محتواه وعمقه فقط، بلا نظر للأسماء.
        val kindCache = HashMap<String, String>()
        fun kindOf(path: String): String {
            kindCache[path]?.let { return it }
            val segments = path.split('/').filter(String::isNotBlank)
            val kind = when {
                segments.size == 1 -> AUTHOR
                segments.size == 2 ->
                    if (byFolder[path].orEmpty().isNotEmpty()) BOOK
                    else if (childrenOf[path].orEmpty().any { subtreeHasAudio[it] == true }) SERIES
                    else BOOK // لا يصل إليها إطلاقًا (بلا صوت في شجرتها)
                else -> BOOK
            }
            kindCache[path] = kind
            return kind
        }

        val stemFor = { filename: String, fallback: String ->
            filename.substringBeforeLast('.', filename).takeIf(String::isNotBlank) ?: fallback
        }

        val books = mutableListOf<ClassifiedBook>()

        // 1) ملفات الجذر المباشرة → كتاب مستقل لكل ملف، بلا مؤلف ولا سلسلة.
        byFolder[""].orEmpty().forEach { file ->
            books += ClassifiedBook(null, null, stemFor(file.filename, rootName), "", listOf(file))
        }

        // 2) بقية العقد بترتيب العمق فالحرف — قرار الدور بالعمق والمحتوى فقط.
        allPaths
            .filter { it.isNotEmpty() && subtreeHasAudio[it] == true }
            .sortedWith(compareBy({ depthOf(it) }, { it }))
            .forEach { path ->
                val segments = path.split('/').filter(String::isNotBlank)
                val direct = byFolder[path].orEmpty()
                when (kindOf(path)) {
                    // العمق 1 = AUTHOR: ملفاته المباشرة كتب مستقلة بمؤلف المجلد.
                    AUTHOR -> direct.forEach { file ->
                        books += ClassifiedBook(
                            authorName = segments[0],
                            seriesName = null,
                            bookTitle = stemFor(file.filename, segments[0]),
                            folderPath = path,
                            files = listOf(file)
                        )
                    }
                    // العمق 2 = BOOK إن كان ذا ملفات مباشرة (سلسلة إن كان يحوي
                    // مجلدات فقط، ولا يُنتج كتابًا عندئذٍ).
                    BOOK -> when (segments.size) {
                        // العمق 2 ذو الملفات المباشرة = سلسلة + كتاب لكل ملف: المجلد
                        // سلسلةٌ تحت مؤلفه، وكل ملف يُنتج كتابًا مستقلاً بعنوان اسم الملف.
                        2 -> direct.forEach { file ->
                            books += ClassifiedBook(
                                segments[0],
                                segments[1],
                                stemFor(file.filename, segments[1]),
                                file.folderPath,
                                listOf(file)
                            )
                        }
                        else -> {
                            // العمق ≥ 3 = BOOK تكراريًا؛ سلسلته حاوية العمق-2 إن كانت SERIES.
                            val parentSeries = listOf(segments[0], segments[1]).joinToString("/")
                            val seriesName = if (kindOf(parentSeries) == SERIES) segments[1] else null
                            books += ClassifiedBook(segments[0], seriesName, segments.last(), path, direct)
                        }
                    }
                    // SERIES: حاوية لا كتاب لنفسها — كتبها عمق ≥ 3 في النطاق أعلاه.
                }
            }

        return books
    }

    /**
     * معاينة (المرحلة 5): تشغّل [classify] نفسه (نفس المنطق حرفيًا) ثم تجمّع ناتجه
     * في شجرة عرض جاهزة للاعتماد/التعديل. قراءة فقط — لا يمس القاعدة ولا أي حالة.
     * الترتيب مستقر: المؤلفون أبجديًا، والسلاسل أبجديًا، والكتب بترتيب التصنيف.
     */
    fun preview(files: List<InputFile>, rootName: String): PreviewTree {
        val classified = classify(files, rootName)
        fun toPreview(book: ClassifiedBook) = PreviewBook(
            title = book.bookTitle,
            folderPath = book.folderPath,
            fileCount = book.files.size,
            totalDurationMs = book.files.sumOf { it.durationMs }
        )

        val authorBooks = LinkedHashMap<String, MutableList<ClassifiedBook>>()
        val unassigned = mutableListOf<ClassifiedBook>()
        classified.forEach { book ->
            if (book.authorName.isNullOrBlank()) unassigned += book
            else authorBooks.getOrPut(book.authorName) { mutableListOf() } += book
        }

        val authors = authorBooks.entries.sortedBy { it.key }.map { (name, books) ->
            val bySeries = books.groupBy { it.seriesName }
            PreviewAuthor(
                name = name,
                books = bySeries[null].orEmpty().map { toPreview(it) },
                series = bySeries.entries
                    .filter { it.key != null }
                    .sortedBy { it.key }
                    .map { (seriesName, seriesBooks) ->
                        // مجلد السلسلة = المقطعان الأولان من مسار كتاب (مؤلف/سلسلة)،
                        // ثابت حتى مع كتب الحاويات الأعمق.
                        val first = seriesBooks.first().folderPath.split('/').filter(String::isNotBlank)
                        PreviewSeries(
                            name = seriesName ?: "",
                            folderPath = first.take(2).joinToString("/"),
                            books = seriesBooks.map { toPreview(it) }
                        )
                    }
            )
        }

        return PreviewTree(
            rootName = rootName,
            authors = authors,
            unassignedBooks = unassigned.map { toPreview(it) },
            totalFiles = files.size
        )
    }
}