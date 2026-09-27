package com.example.audiobook.presentation.onboarding

import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewAuthor
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree

/**
 * أنواع التعديل المعتمَدة (المرحلة 5) — تتطابق حرفيًا مع قيم editType المحفوظة
 * في الجدول onboarding_edits: يستخدم الاسم النصي في الحفظ/الاسترجاع.
 */
object ClassificationEditTypes {
    const val RENAME_AUTHOR = "RENAME_AUTHOR"
    const val RENAME_SERIES = "RENAME_SERIES"
    const val RENAME_BOOK = "RENAME_BOOK"
    const val MOVE_BOOK_AUTHOR = "MOVE_BOOK_AUTHOR"
    const val MOVE_BOOK_SERIES = "MOVE_BOOK_SERIES"
    const val SKIP_FOLDER = "SKIP_FOLDER"
}

/**
 * تعديل واحد على شجرة التصنيف المُعتمدَة. كل نوع يحمل `path` (مسار المجلد في
 * شجرة المعاينة: مؤلف = اسم مجلد العمق-1؛ سلسلة = "مؤلف/سلسلة"؛ كتاب = مجلد الكتاب).
 */
sealed class ClassificationEdit {
    abstract val path: String
    abstract val type: String
    abstract val newValueForStorage: String?

    data class RenameAuthor(val authorName: String, val newValue: String) : ClassificationEdit() {
        override val path: String = authorName
        override val type: String = ClassificationEditTypes.RENAME_AUTHOR
        override val newValueForStorage: String? = newValue
    }

    data class RenameSeries(
        // مسار التخزين الثابت (مفتاح فريد) = "مؤلف/سلسلة" كما تُشتق من المعاينة؛
        // المطابقة تتم بالاسم الجاري (يمتص سلسلة إعادة التسمية).
        val authorName: String,
        val seriesName: String,
        val newValue: String
    ) : ClassificationEdit() {
        override val path: String = "$authorName/$seriesName"
        override val type: String = ClassificationEditTypes.RENAME_SERIES
        override val newValueForStorage: String? = newValue
    }

    data class RenameBook(val folderPath: String, val newValue: String) : ClassificationEdit() {
        override val path: String = folderPath
        override val type: String = ClassificationEditTypes.RENAME_BOOK
        override val newValueForStorage: String? = newValue
    }

    data class MoveBookAuthor(val folderPath: String, val newValue: String) : ClassificationEdit() {
        override val path: String = folderPath
        override val type: String = ClassificationEditTypes.MOVE_BOOK_AUTHOR
        override val newValueForStorage: String? = newValue
    }

    /** newValue = null تعني «إزالة من السلسلة». */
    data class MoveBookSeries(val folderPath: String, val newValue: String?) : ClassificationEdit() {
        override val path: String = folderPath
        override val type: String = ClassificationEditTypes.MOVE_BOOK_SERIES
        override val newValueForStorage: String? = newValue
    }

    data class SkipFolder(val folderPath: String) : ClassificationEdit() {
        override val path: String = folderPath
        override val type: String = ClassificationEditTypes.SKIP_FOLDER
        override val newValueForStorage: String? = null
    }
}

/** صف عرض مسطّح لكل كتاب أثناء تحويل الشجرة — يسهّل تطبيق التعديلات ثم إعادة التجميع. */
data class PreviewBookEntry(
    var authorName: String?,
    var seriesName: String?,
    var book: PreviewBook
)

/**
 * تطبيق قائمة تعديلات على شجرة المعاينة الأصلية (حسب ترتيبها) وإنتاج الشجرة
 * المعاد تجميعها التي سيعتمدها المستخدم ويُستوردها الفحص. قراءة فقط — لا يمس القاعدة.
 */
fun applyEditsToTree(tree: PreviewTree, edits: List<ClassificationEdit>): PreviewTree {
    val entries = mutableListOf<PreviewBookEntry>()
    tree.authors.forEach { author ->
        author.books.forEach { entries += PreviewBookEntry(author.name, null, it) }
        author.series.forEach { series ->
            series.books.forEach { entries += PreviewBookEntry(author.name, series.name, it) }
        }
    }
    tree.unassignedBooks.forEach { entries += PreviewBookEntry(null, null, it) }

    edits.forEach { edit ->
        when (edit) {
            is ClassificationEdit.RenameAuthor -> entries.forEach {
                if (it.authorName == edit.authorName) it.authorName = edit.newValue
            }
            is ClassificationEdit.RenameSeries -> entries.forEach {
                if (it.authorName == edit.authorName && it.seriesName == edit.seriesName) {
                    it.seriesName = edit.newValue
                }
            }
            is ClassificationEdit.RenameBook -> entries.firstOrNull { it.book.folderPath == edit.path }?.let {
                it.book = it.book.copy(title = edit.newValue)
            }
            is ClassificationEdit.MoveBookAuthor -> entries.firstOrNull { it.book.folderPath == edit.path }?.let {
                it.authorName = edit.newValue
                it.seriesName = null
            }
            is ClassificationEdit.MoveBookSeries -> entries.firstOrNull { it.book.folderPath == edit.path }?.let {
                it.seriesName = edit.newValue?.takeIf(String::isNotBlank)
            }
            is ClassificationEdit.SkipFolder -> entries.removeAll { it.book.folderPath == edit.path }
        }
    }

    val grouped = LinkedHashMap<String?, MutableList<PreviewBookEntry>>()
    entries.forEach { grouped.getOrPut(it.authorName) { mutableListOf() } += it }

    val authors = grouped.entries
        .filter { it.key != null }
        .sortedBy { it.key }
        .map { (authorName, authorEntries) ->
            val bySeries = LinkedHashMap<String?, MutableList<PreviewBookEntry>>()
            authorEntries.forEach { bySeries.getOrPut(it.seriesName) { mutableListOf() } += it }
            PreviewAuthor(
                name = authorName ?: "",
                books = bySeries[null].orEmpty().map { it.book },
                series = bySeries.entries
                    .filter { it.key != null }
                    .sortedBy { it.key }
                    .map { (seriesName, seriesEntries) ->
                        val first = seriesEntries.first().book.folderPath.split('/').filter(String::isNotBlank)
                        com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewSeries(
                            name = seriesName ?: "",
                            folderPath = first.take(2).joinToString("/"),
                            books = seriesEntries.map { it.book }
                        )
                    }
            )
        }

    return PreviewTree(
        rootName = tree.rootName,
        authors = authors,
        unassignedBooks = grouped[null].orEmpty().map { it.book },
        totalFiles = entries.sumOf { it.book.fileCount }
    )
}