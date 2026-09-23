package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.PendingDiscoveryEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import java.util.UUID
import javax.inject.Inject

/**
 * Part 4 — قرارات «الاكتشافات المعلّقة»: تُطبَّق إما على كل قائمة جذر ما
 * (البوب-أب) أو على اكتشاف واحد (الشاشة قائمة الإعدادات). لا تلمس سلوك الفحص
 * نفسه؛ إنها تُستدعى بعد أن استورد الفحص المجلدات فعلًا (استيراد موازٍ للقائمة):
 *  - إنشاء كتاب لكل مجلد: الوضع الافتراضي — كل مجلد قد ولّده الفحص فعلًا، نعلّمه منجزًا.
 *  - دمج ككتاب واحد: ندمج إصدارات كل المجلدات في إصدار أول مجلد عبر EditionMerge
 *    (منقول الملفات والتقدم والمراجع)، ونعلّم المجلدات المندمجة «متجاهَلًا» كي لا
 *    يعيد الفحص اللاحق استيرادها منفردة تحت كتاب جديد.
 *  - إسناد إلى سلسلة/مؤلف: ننشئ أو نعيد استخدام الكيان ونحرّك الكتاب إليه.
 *  - تجاهل: يمنع استيراد المجلد من الفحوصات التالية (اقرأ ScanRoot.isIgnored).
 */
enum class DiscoveryDecision { CREATE_BOOKS, MERGE_AS_ONE, ASSIGN_SERIES, ASSIGN_AUTHOR, IGNORE_ALL }

class ApplyDiscoveryDecision @Inject constructor(
    private val database: AppDatabase,
    private val editionMerge: EditionMerge
) {
    suspend operator fun invoke(rootId: UUID, decision: DiscoveryDecision, targetName: String? = null) {
        apply(database.pendingDiscoveryDao().getPendingByRoot(rootId), decision, targetName)
    }

    suspend fun invokeForDiscovery(discoveryId: UUID, decision: DiscoveryDecision, targetName: String? = null) {
        apply(listOfNotNull(database.pendingDiscoveryDao().getById(discoveryId)), decision, targetName)
    }

    private suspend fun apply(items: List<PendingDiscoveryEntity>, decision: DiscoveryDecision, targetName: String?) {
        if (items.isEmpty()) return
        when (decision) {
            DiscoveryDecision.CREATE_BOOKS -> items.forEach { database.pendingDiscoveryDao().markResolved(it.id) }
            DiscoveryDecision.IGNORE_ALL -> items.forEach { database.pendingDiscoveryDao().markIgnored(it.id) }
            DiscoveryDecision.MERGE_AS_ONE -> mergeAsOne(items)
            DiscoveryDecision.ASSIGN_SERIES -> items.forEach { assignSeries(it, targetName) }
            DiscoveryDecision.ASSIGN_AUTHOR -> items.forEach { assignAuthor(it, targetName) }
        }
    }

    /** دمج كل الاكتشافات في كتاب أول مجلد؛ المندمجة تُعلَّم «متجاهَلًا» لاستقرار إعادة الفحص. */
    private suspend fun mergeAsOne(items: List<PendingDiscoveryEntity>) {
        val folders = items.sortedBy { it.discoveredAt }
        val first = folders.first()
        val firstEdition = database.editionDao().getByRootAndFolder(first.rootId, first.folderPath)
        if (firstEdition == null) {
            folders.forEach { database.pendingDiscoveryDao().markResolved(it.id) }
            return
        }
        folders.drop(1).forEach { other ->
            val otherEdition = database.editionDao().getByRootAndFolder(other.rootId, other.folderPath)
            if (otherEdition != null && otherEdition.bookId != firstEdition.bookId) {
                editionMerge.merge(firstEdition.id, otherEdition.id, userInitiated = true)
            }
            database.pendingDiscoveryDao().markIgnored(other.id)
        }
        database.pendingDiscoveryDao().markResolved(first.id)
    }

    private suspend fun assignSeries(item: PendingDiscoveryEntity, name: String?) {
        val target = name?.takeIf { it.isNotBlank() } ?: return
        val edition = database.editionDao().getByRootAndFolder(item.rootId, item.folderPath) ?: return
        val book = database.bookDao().getById(edition.bookId) ?: return
        val authorId = book.authorId
        val series = database.seriesDao().getByParent(authorId).firstOrNull { it.name == target }
            ?: SeriesEntity(authorId = authorId, name = target, colorTheme = null)
                .also { database.seriesDao().insert(it) }
        val nextOrder = (database.bookDao().getBySeries(series.id).maxOfOrNull { it.orderInSeries ?: 0 } ?: 0) + 1
        database.bookDao().update(
            book.copy(seriesId = series.id, orderInSeries = nextOrder)
        )
        database.pendingDiscoveryDao().markResolved(item.id)
    }

    private suspend fun assignAuthor(item: PendingDiscoveryEntity, name: String?) {
        val target = name?.takeIf { it.isNotBlank() } ?: return
        val edition = database.editionDao().getByRootAndFolder(item.rootId, item.folderPath) ?: return
        val book = database.bookDao().getById(edition.bookId) ?: return
        val author = database.authorDao().getByName(target)
            ?: AuthorEntity(name = target, colorTheme = null).also { database.authorDao().insert(it) }
        // تغيير المؤلف يحرر السلسلة (ذات المؤلف القديم) — FK في books هو SET_NULL،
        // لكننا نُحدّث صراحةً لتفادي ربطة عالقة؛ السلسلة نفسها تبقى تحت مؤلفها.
        val keptSeries = if (book.authorId == author.id) book.seriesId else null
        val keptOrder = if (keptSeries != null) book.orderInSeries else null
        database.bookDao().update(book.copy(authorId = author.id, seriesId = keptSeries, orderInSeries = keptOrder))
        database.pendingDiscoveryDao().markResolved(item.id)
    }
}