package com.example.audiobook.domain.usecases

import com.example.audiobook.data.localfilesystem.ScanFile

/**
 * تصنيف هرمية المجلدات المكتشفة أثناء الفحص (نقٍّ خالص قابل للاختبار).
 *
 * البنية المرجعية:
 *   root/
 *   ├── أحمد خالد توفيق/            ← AUTHOR (الحاوية ذات العمق 1)
 *   │   ├── فانتازيا/               ← SERIES (حاوية وسيطة لا تحوي ملفات مباشرة، عمق ≥ 2)
 *   │   │   ├── 01.mp3  …          ← كتاب/إصدار
 *   │   └── ما وراء الطبيعة/
 *   │       └── 01.mp3
 *   └── كتاب واحد.top/             ← BOOK (حاوية بعمق 1 تحوي ملفات مباشرة)
 *       └── 01.mp3
 *
 * القواعد:
 *  - مجلد خالٍ تمامًا من أي صوت (مباشر أو في أحفاد) → يُتجاهل (EMPTY).
 *  - مجلد يحوي ملفات مباشرة:
 *      · بلا مجلدات فرعية تحوي صوتًا → BOOK.
 *      · مع مجلدات فرعية تحوي صوتًا → MIXED_BOOK (كتاب + حاوية لأحفاده).
 *  - حاوية (بلا ملفات مباشرة) وأحفادها تحوي صوتًا:
 *      · عمقها 1 → AUTHOR.
 *      · عمقها ≥ 2 → SERIES.
 *
 * استخراج السياق لأي كتاب (دون الحاجة لأب/أبناء):
 *  - المؤلف = المقطع الأول من المسار (عمق ≥ 2) أو الاسم الاحتياطي root.displayName.
 *  - السلسلة = "المجلد الحاوي مباشرة فوق الكتاب" (المقطع قبل الأخير) عندما يوجد مستوى
 *     وسطي واحد على الأقل فوقه — أي لا سلسلة للكتب ذات المستويين (Author/Book).
 */
enum class FolderKind { AUTHOR, SERIES, BOOK, MIXED_BOOK, EMPTY }

/** سياق كتاب/إصدار مُستخرَج من بنية المجلد (دون لمس قاعدة البيانات). */
data class AuthorSeriesContext(
    val authorName: String,
    val seriesFolderName: String?
)

/**
 * عقدة مجلد بعد التصنيف: يملك المسار الكامل (نسبيًا من الجذر) وملفاته المباشرة
 * وأطفاله المصنَّفين. الحاويات الفارغة تُقصّ بالكامل من الشجرة.
 */
data class FolderNode(
    val path: String,
    val name: String,
    val depth: Int,
    val kind: FolderKind,
    val directFiles: List<ScanFile>,
    val children: List<FolderNode>
) {
    fun hasDirectAudio(): Boolean = directFiles.isNotEmpty()

    /** سياق المؤلف/السلسلة لهذا المجلد (اعتمادًا على عمق المسار فقط). */
    fun context(fallbackAuthor: String): AuthorSeriesContext {
        val segments = path.split('/').filter(String::isNotBlank)
        val author = if (segments.size >= 2) segments[0] else fallbackAuthor
        val series = if (segments.size >= 3) segments[segments.size - 2] else null
        return AuthorSeriesContext(author, series)
    }
}

object FolderClassifier {

    /**
     * يبني شجرة المجلدات المصنَّفة من قائمة الملفات المسطحة.
     * تُرجع العقد الجذرية (العمق 1) بعد قصّ الحاويات الفارغة.
     */
    fun classify(files: List<ScanFile>): List<FolderNode> {
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

        data class Builder(
            val path: String,
            val directFiles: List<ScanFile>,
            val children: MutableList<FolderNode> = mutableListOf()
        )

        val builders = allPaths.associateWith { path -> Builder(path, filesByPath[path].orEmpty()) }.toMutableMap()
        val depthOf = { path: String -> path.split('/').filter(String::isNotBlank).size }

        val sorted = allPaths.sortedBy(String::length).reversed()
        val classified = HashMap<String, FolderNode>()

        // التصنيف من الأسفل للأعلى (الحفيد قبل الأب).
        sorted.forEach { path ->
            val builder = builders.getValue(path)
            val children = builder.children + childrenByPath[path].orEmpty()
                .map { classified[it] }
                .filterNotNull()
            val kind = classifyNode(
                directFiles = builder.directFiles,
                hasAudioChildren = children.any { it.kind != FolderKind.EMPTY },
                depth = depthOf(path)
            )
            if (kind != FolderKind.EMPTY) {
                classified[path] = FolderNode(
                    path = path,
                    name = path.substringAfterLast('/'),
                    depth = depthOf(path),
                    kind = kind,
                    directFiles = builder.directFiles,
                    children = children.filter { it.kind != FolderKind.EMPTY }.sortedWith(NODE_ORDER)
                )
            }
        }

        return classified.filter { (path, _) -> depthOf(path) == 1 }
            .values
            .sortedWith(NODE_ORDER)
    }

    /** كل العقد التي تُنشئ إصدارًا/كتابًا (لها ملفات مباشرة = BOOK أو MIXED_BOOK). */
    fun flattenBookNodes(nodes: List<FolderNode>, out: MutableList<FolderNode> = mutableListOf()): List<FolderNode> {
        nodes.forEach { node ->
            if (node.kind == FolderKind.BOOK || node.kind == FolderKind.MIXED_BOOK) out += node
            flattenBookNodes(node.children, out)
        }
        return out
    }

    /** تصنيف عقدة واحدة حسب ملفاتها المباشرة وأحفادها الصوتية وقاعدتي AUTHOR/SERIES. */
    fun classifyNode(directFiles: List<ScanFile>, hasAudioChildren: Boolean, depth: Int): FolderKind = when {
        directFiles.isEmpty() && !hasAudioChildren -> FolderKind.EMPTY
        directFiles.isNotEmpty() && !hasAudioChildren -> FolderKind.BOOK
        directFiles.isNotEmpty() -> FolderKind.MIXED_BOOK
        depth == 1 -> FolderKind.AUTHOR
        else -> FolderKind.SERIES
    }

    /**
     * اشتقاق سياق (المؤلف/السلسلة) لمجلد من مساره مباشرة — نفس قاعدة العمق
     * الخاصة بـ [FolderNode.context] (تُستخدم عند إعادة التصنيف بلا فحص ملفات).
     */
    fun contextForPath(path: String, fallbackAuthor: String): AuthorSeriesContext {
        val segments = path.split('/').filter(String::isNotBlank)
        val author = if (segments.size >= 2) segments[0] else fallbackAuthor
        val series = if (segments.size >= 3) segments[segments.size - 2] else null
        return AuthorSeriesContext(author, series)
    }

    private val NODE_ORDER = compareBy<FolderNode> { it.path }
}