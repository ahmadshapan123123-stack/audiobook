package com.example.audiobook.domain.usecases

import androidx.room.withTransaction
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import javax.inject.Inject

/** معاينة إعادة التصنيف: كم كتابًا سيتأثر وكم مجلدًا سيُصحَّح. */
data class ReclassifyPreview(
    val affectedBooks: Int,
    val foldersToFix: Int
)

/**
 * إعادة تصنيف المكتبة بلا إعادة فحص ملفات: يعيد اشتقاق المؤلف/السلسلة من
 * مسار مجلد كل إصدار (sourceFolderPath) ويصحّح ربط الكتاب بهما. لا يُنشئ/يحذف
 * ملفات ولا يلمس الإصدارات، ولا يكتب فوق كتاب عنوانه مؤكَّد من المستخدم أو
 * كتاب له أكثر من إصدار (مُدمج/مُوحَّد) لأن مالكه غير محسوم من مجلد واحد.
 * يحترم إعداد «تصنيف تلقائي للسلاسل»: عند إيقافه لا يُنسب أي كتاب لسلسلة.
 */
class ReclassifyLibrary @Inject constructor(
    private val database: AppDatabase,
    private val appSettings: AppSettings
) {

    suspend operator fun invoke(dryRun: Boolean = false): ReclassifyPreview = database.withTransaction {
        val autoSeries = appSettings.currentAutoSeriesClassification()
        val roots = database.libraryRootDao().getAll()
        val editions = roots.flatMap { root ->
            database.editionDao().getByRoot(root.id).map { edition -> root to edition }
        }
        val affectedBooks = mutableSetOf<java.util.UUID>()
        var foldersToFix = 0

        editions.forEach { (root, edition) ->
            val book = database.bookDao().getById(edition.bookId) ?: return@forEach
            if (book.isTitleUserConfirmed) return@forEach
            val bookEditions = database.editionDao().getByParent(book.id)
            if (bookEditions.size > 1) return@forEach

            val context = FolderClassifier.contextForPath(edition.sourceFolderPath, root.displayName, autoSeries)
            val targetAuthor = database.authorDao().getByName(context.authorName)
            val authorMatches = targetAuthor?.id == book.authorId

            val targetSeries = resolveSeries(targetAuthor?.id, context.seriesFolderName)
            val seriesMatches = targetSeries?.id == book.seriesId

            if (!authorMatches || !seriesMatches) {
                foldersToFix++
                affectedBooks += book.id
                if (!dryRun) {
                    val author = targetAuthor
                        ?: AuthorEntity(name = context.authorName, colorTheme = null)
                            .also { database.authorDao().insert(it) }
                    val seriesId = context.seriesFolderName?.takeIf { it.isNotBlank() }?.let { seriesName ->
                        database.seriesDao().getByParent(author.id).firstOrNull { it.name == seriesName }
                            ?: SeriesEntity(authorId = author.id, name = seriesName, colorTheme = null)
                                .also { database.seriesDao().insert(it) }
                    }?.id
                    database.bookDao().update(
                        book.copy(
                            authorId = author.id,
                            seriesId = seriesId,
                            orderInSeries = book.orderInSeries
                        )
                    )
                }
            }
        }
        ReclassifyPreview(affectedBooks = affectedBooks.size, foldersToFix = foldersToFix)
    }

    private suspend fun resolveSeries(authorId: java.util.UUID?, seriesName: String?): SeriesEntity? {
        if (authorId == null || seriesName.isNullOrBlank()) return null
        return database.seriesDao().getByParent(authorId).firstOrNull { it.name == seriesName }
    }
}