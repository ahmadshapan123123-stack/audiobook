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
    /**
     * PART 1: يفضّل تشغيل الفحص عبر [com.example.audiobook.background.scan.ScanForegroundService]
     * حتى لا يُقتل التطبيق تحت ضغط الذاكرة. هذا المسار احتياطي: متى كان التطبيق
     * في المقدمة والخدمة غير متاحة (restricted context) يعمل الفحص مباشرة،
     * و[scanRoot] يحمي نفسه بحارس [ScanProgressBus] على أي حال.
     */
    suspend operator fun invoke(): ScanNowResult {
        val roots = libraryRoots.getEnabledBackgroundRoots() + libraryRoots.getEnabledPriorityRoots()
        if (roots.isEmpty()) return ScanNowResult(0, 0, 0)
        var filesSeen = 0
        roots.forEach { root ->
            // PART 11: حارس الجلسة يُفتح ويُغلق داخل [ScanRoot] لنفسه لكل خطأ
            // (بما فيه [ScanAlreadyRunningException])، فلا تسرّب هنا: الجذر التالي
            // يفتح حارسًا نظيفًا.
            val report = scanRoot(root.id)
            filesSeen += report.filesSeen + report.filesDeduped
        }
        val rootIds: List<UUID> = roots.map { it.id }
        val booksFound = database.editionDao().countDistinctBooksForRoots(rootIds)
        return ScanNowResult(roots.size, filesSeen, booksFound)
    }
}