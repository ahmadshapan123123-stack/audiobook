package com.example.audiobook.domain.usecases

import android.net.Uri
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.repository.LibraryRootRepository
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** سطر واحد في معاينة شجرة التصنيف: مستوى الإزاحة + النص (بلا أي لمس لقاعدة البيانات). */
data class ClassificationPreviewLine(
    val indent: Int,
    val text: String
)

/** معاينة مصدر واحد: الاسم الظاهر + أسطر الشجرة التي سيولّدها الفحص القادم. */
data class ClassificationPreviewPerRoot(
    val displayName: String,
    val lines: List<ClassificationPreviewLine>
)

/**
 * معاينة تصنيف المكتبة (ميزة «معاينة تصنيف المكتبة» في الإعدادات):
 * يقرأ الملفات من مجلدات الفحص الممكّنة (الخلفية فالأولوية — كـ [ScanLibraryNow])،
 * يشغّل [FolderClassifier.classify] ويولّد أسطر شجرة Author → Series → Book ليعرضها
 * المستخدم قبل أي فحص، دون إنشاء/تعديل أي صف في قاعدة البيانات.
 *
 * `buildTreeLines` نقية (بلا Android) وتُختبَر مباشرة في الاختبارات.
 */
class LibraryClassificationPreview @Inject constructor(
    private val fileSource: LibraryFileSource,
    private val libraryRoots: LibraryRootRepository,
    private val appSettings: AppSettings
) {
    suspend fun invoke(): List<ClassificationPreviewPerRoot> = withContext(Dispatchers.IO) {
        val autoSeries = appSettings.currentAutoSeriesClassification()
        val roots = libraryRoots.getEnabledBackgroundRoots() + libraryRoots.getEnabledPriorityRoots()
        roots.map { root ->
            val files = fileSource.listAudioFiles(Uri.parse(root.uri))
            ClassificationPreviewPerRoot(
                displayName = root.displayName,
                lines = buildTreeLines(FolderClassifier.classify(files, autoSeries), root.displayName)
            )
        }
    }

    /** أسطر عرض الشجرة المصنَّفة: ▼ Author / ▣ Series / • Book (الكتاب الاصطناعي برمز هو). */
    fun buildTreeLines(nodes: List<FolderNode>, fallbackAuthor: String): List<ClassificationPreviewLine> {
        val lines = mutableListOf<ClassificationPreviewLine>()
        val contexts = FolderClassifier.contextsByPath(nodes, fallbackAuthor)
        fun visit(node: FolderNode, indent: Int) {
            val marker = when (node.kind) {
                FolderKind.AUTHOR -> "▼"
                FolderKind.SERIES -> "▣"
                else -> "•"
            }
            val context = contexts[node.path]
            val suffix = if (node.kind == FolderKind.BOOK) {
                val seriesNote = context?.seriesFolderName?.let { " ضمن سلسلة: $it" }.orEmpty()
                " (${node.directFiles.size} ملفًا)$seriesNote"
            } else if (node.directFiles.isNotEmpty()) {
                " + ملفات مباشرة"
            } else {
                ""
            }
            lines += ClassificationPreviewLine(indent, "$marker ${node.name}$suffix")
            node.children.forEach { visit(it, indent + 1) }
        }
        nodes.forEach { visit(it, 0) }
        return lines
    }
}