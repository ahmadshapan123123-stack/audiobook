package com.example.audiobook.domain.usecases

import androidx.room.withTransaction
import com.example.audiobook.data.room.AppDatabase
import javax.inject.Inject

/** حصيلة «إعادة بناء بنية المكتبة»: ما حُذف من قشور فارغة، ثم نتيجة الفحص. */
data class RebuildStructureResult(
    val shellsRemoved: Int,
    val orphanBooksRemoved: Int,
    val scan: ScanNowResult
)

/**
 * إعادة بناء بنية المكتبة بعد تغيّر نموذج التصنيف.
 *
 * عند تغيّر قاعدة التصنيف (مثل: مجلد سلسلة في العمق 2 صار كتابًا مستقلًا لكل
 * ملف بدل كتاب واحد يجمع ملفاته) تنشأ الكتب الجديدة وتنتقل ملفاتها إليها،
 * بينما يبقى الإصدار القديم «قشرة» بلا ملفات. تحذف هذه العملية القشور
 * التي لا تملك ملفًا ولا أثرًا لمستخدم (تقدّم أو علامات أو جلسات)، ثم الكتب
 * اليتيمة التي لم يتبقَّ لها أي إصدار، ثم تعيد فحص كل الجذور لتبني البنية الجديدة.
 *
 * لا يمسّ شيئًا من بيانات المستخدم: تُستثنى الكتب التجريبية، والعناوين التي أكّدها
 * المستخدم، والمفضّلة، وكل ما له تقدّم أو علامات أو جلسات أو فصولٌ منجزة. أما
 * الملفات على القرص فلا تُمسّ إطلاقًا، فهذه عملية إعادة فهرسة فقط.
 */
class RebuildLibraryStructure @Inject constructor(
    private val database: AppDatabase,
    private val scanLibraryNow: ScanLibraryNow
) {
    suspend operator fun invoke(): RebuildStructureResult {
        var shells = 0
        var orphans = 0
        database.withTransaction {
            database.editionDao().getAbandonedShells().forEach { edition ->
                val book = database.bookDao().getById(edition.bookId)
                // لا يُحذف إصدار إذا كان كتابه تجريبيًا أو أكّد المستخدم عنوانه.
                if (book != null && (book.isDemo || book.isTitleUserConfirmed)) return@forEach
                // `books.defaultEditionId` مرجعٌ بلا قيد FK، فحذف الإصدار يتركه
                // معلّقًا على صفّ محذوف. يُعاد توجيهه إلى إصدار باقٍ أو يُفرَّغ —
                // وإلا عرضت المكتبة كتابًا يشير إلى إصدار غير موجود.
                if (book != null && book.defaultEditionId == edition.id) {
                    // استثناء القشرة المحذوفة نفسها: هي ما زالت في القاعدة الآن،
                    // فلو أُخذت بلا استثناء لعاد المؤشر إلى الصفّ المحذوف.
                    val remaining = database.editionDao().getByParent(book.id).firstOrNull { it.id != edition.id }
                    database.bookDao().update(book.copy(defaultEditionId = remaining?.id))
                }
                database.editionDao().delete(edition)
                shells++
            }
            database.bookDao().getOrphanBooks().forEach { book ->
                database.bookDao().delete(book)
                orphans++
            }
        }
        return RebuildStructureResult(shells, orphans, scanLibraryNow())
    }
}
