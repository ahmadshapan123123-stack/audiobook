package com.example.audiobook.domain.usecases

import com.example.audiobook.data.repository.LibraryRootRepository
import com.example.audiobook.data.room.AppDatabase
import java.util.UUID
import javax.inject.Inject

/** حصيلة الفحص الفوري: عدد الجذور المفحوصة + الملفات التي رآها + الكتب الناتجة. */
data class ScanNowResult(
    val rootsScanned: Int,
    val filesSeen: Int,
    val booksFound: Int
)

/**
 * الفحص الفوري عند ضغط "فحص المكتبة الآن": يفحص كل مجلدات المكتبة الممكّنة
 * (الخلفية فالأولوية) فورًا (وليس جدولة خلفية)، ويرجع حصيلة للرسالة.
 */
class ScanLibraryNow @Inject constructor(
    private val libraryRoots: LibraryRootRepository,
    private val scanRoot: ScanRoot,
    private val database: AppDatabase
) {
    suspend operator fun invoke(): ScanNowResult {
        val roots = libraryRoots.getEnabledBackgroundRoots() + libraryRoots.getEnabledPriorityRoots()
        if (roots.isEmpty()) return ScanNowResult(0, 0, 0)
        var filesSeen = 0
        roots.forEach { root ->
            val report = scanRoot(root.id)
            filesSeen += report.filesSeen + report.filesDeduped
        }
        val rootIds: List<UUID> = roots.map { it.id }
        val booksFound = database.editionDao().countDistinctBooksForRoots(rootIds)
        return ScanNowResult(roots.size, filesSeen, booksFound)
    }
}