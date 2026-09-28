package com.example.audiobook.domain.usecases

import android.net.Uri
import android.util.Log
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.room.DEMO_AUTHOR_IDS
import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

private const val TAG = "LibraryManagement"

/** حصيلة تنظيف بيانات التجربة: عدد الكتب المحذوفة + وجود بقايا تجريبية يُعرض للمستخدم. */
data class ClearDemoDataResult(
    val removedBooks: Int,
    val hasRemainingDemoEntities: Boolean
)

class LibraryManagement @Inject constructor(
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val audioFileDao: AudioFileDao,
    private val chapterDao: ChapterDao,
    private val bookmarkDao: BookmarkDao,
    private val progressDao: ProgressDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val favoriteBookDao: FavoriteBookDao,
    private val chapterCompletionDao: ChapterCompletionDao,
    private val libraryRootDao: LibraryRootDao,
    /**
     * STAGE 4/5/6 — وصول مباشر لجداول دورة الفحص: نقاط التوقف (استئناف)،
     * الاكتشافات المعلقة (دمج/حذف جذور)، وتعديلات الاستهلال (تنظيف).
     */
    private val scanCheckpointDao: ScanCheckpointDao,
    private val pendingDiscoveryDao: PendingDiscoveryDao,
    private val onboardingEditDao: OnboardingEditDao
) {

    // ── Author Operations ──

    data class AuthorSnapshot(
        val author: AuthorEntity,
        val series: List<SeriesEntity>,
        val bookSnapshots: List<BookSnapshot>
    )

    suspend fun snapshotAuthor(authorId: UUID): AuthorSnapshot {
        val author = authorDao.getById(authorId) ?: throw IllegalStateException("Author not found")
        val books = bookDao.getByParent(authorId)
        val series = seriesDao.getByParent(authorId)
        // deleteAuthor يحذف الكتب كاملة عبر deleteBookCascade (إصدارات + فصول + إشارات +
        // تقدّم…) — لذا اللقطة يجب أن تحفظ كل كتاب بكامل أعمدته لاستعادته دون فقدان.
        val bookSnapshots = books.mapNotNull { snapshotBook(it.id) }
        return AuthorSnapshot(author, series, bookSnapshots)
    }

    suspend fun deleteAuthor(authorId: UUID) {
        val books = bookDao.getByParent(authorId)
        for (book in books) {
            deleteBookCascade(book.id)
        }
        val series = seriesDao.getByParent(authorId)
        for (s in series) {
            seriesDao.delete(s)
        }
        val author = authorDao.getById(authorId) ?: return
        authorDao.delete(author)
    }

    suspend fun restoreAuthor(snapshot: AuthorSnapshot) {
        authorDao.insert(snapshot.author)
        for (s in snapshot.series) {
            seriesDao.insert(s)
        }
        restoreBooks(DeletedBooksSnapshot(snapshot.bookSnapshots))
    }

    data class AuthorMergeSnapshot(
        val sourceAuthor: AuthorEntity,
        val targetAuthor: AuthorEntity,
        val movedBooks: List<BookEntity>,
        val deletedSeries: List<SeriesEntity>
    )

    suspend fun mergeAuthors(sourceId: UUID, targetId: UUID): AuthorMergeSnapshot {
        val source = authorDao.getById(sourceId) ?: throw IllegalStateException("Source author not found")
        val target = authorDao.getById(targetId) ?: throw IllegalStateException("Target author not found")

        val sourceBooks = bookDao.getByParent(sourceId)
        val sourceSeries = seriesDao.getByParent(sourceId)

        for (book in sourceBooks) {
            bookDao.update(book.copy(authorId = targetId))
        }
        for (s in sourceSeries) {
            seriesDao.delete(s)
        }
        authorDao.delete(source)

        return AuthorMergeSnapshot(source, target, sourceBooks, sourceSeries)
    }

    suspend fun undoMergeAuthors(snapshot: AuthorMergeSnapshot) {
        // الترتيب مهم: أعِد المؤلف المصدر أولًا ليكون FK authorId سليمًا عند تحديث الكتب.
        authorDao.insert(snapshot.sourceAuthor)
        for (s in snapshot.deletedSeries) {
            seriesDao.insert(s)
        }
        for (book in snapshot.movedBooks) {
            bookDao.update(book.copy(authorId = snapshot.sourceAuthor.id))
        }
    }

    // ── Series Operations ──

    data class SeriesSnapshot(val series: SeriesEntity, val books: List<BookEntity>)

    suspend fun snapshotSeries(seriesId: UUID): SeriesSnapshot {
        val series = seriesDao.getById(seriesId) ?: throw IllegalStateException("Series not found")
        val books = bookDao.getBySeries(seriesId)
        return SeriesSnapshot(series, books)
    }

    suspend fun deleteSeries(seriesId: UUID) {
        val books = bookDao.getBySeries(seriesId)
        for (book in books) {
            bookDao.update(book.copy(seriesId = null))
        }
        val series = seriesDao.getById(seriesId) ?: return
        seriesDao.delete(series)
    }

    suspend fun restoreSeries(snapshot: SeriesSnapshot) {
        seriesDao.insert(snapshot.series)
        for (book in snapshot.books) {
            bookDao.update(book.copy(seriesId = snapshot.series.id))
        }
    }

    data class SeriesMergeSnapshot(
        val sourceSeries: SeriesEntity,
        val targetSeries: SeriesEntity,
        val movedBooks: List<BookEntity>
    )

    suspend fun mergeSeries(sourceId: UUID, targetId: UUID): SeriesMergeSnapshot {
        val source = seriesDao.getById(sourceId) ?: throw IllegalStateException("Source series not found")
        val target = seriesDao.getById(targetId) ?: throw IllegalStateException("Target series not found")

        val sourceBooks = bookDao.getBySeries(sourceId)
        for (book in sourceBooks) {
            bookDao.update(book.copy(seriesId = targetId))
        }
        seriesDao.delete(source)

        return SeriesMergeSnapshot(source, target, sourceBooks)
    }

    suspend fun undoMergeSeries(snapshot: SeriesMergeSnapshot) {
        for (book in snapshot.movedBooks) {
            bookDao.update(book.copy(seriesId = snapshot.sourceSeries.id))
        }
        seriesDao.insert(snapshot.sourceSeries)
    }

    suspend fun moveBookToSeries(bookId: UUID, seriesId: UUID?) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(seriesId = seriesId))
    }

    suspend fun reorderBooksInSeries(seriesId: UUID, bookIds: List<UUID>) {
        bookIds.forEachIndexed { index, bookId ->
            bookDao.getById(bookId)?.let { book ->
                bookDao.update(book.copy(orderInSeries = index + 1))
            }
        }
    }

    // ── Book Operations ──

    suspend fun deleteBook(bookId: UUID) {
        val editions = editionDao.getByParent(bookId)
        for (edition in editions) {
            deleteEditionCascade(edition.id)
        }
        favoriteBookDao.delete(bookId)
        bookDao.getById(bookId)?.let { bookDao.delete(it) }
    }

    suspend fun deleteBookCascade(bookId: UUID) {
        deleteBook(bookId)
    }

    suspend fun moveBookToAuthor(bookId: UUID, authorId: UUID) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(authorId = authorId))
    }

    // ── Edition Operations ──

    private suspend fun deleteEditionCascade(editionId: UUID) {
        chapterCompletionDao.deleteForEdition(editionId)
        chapterDao.getByParent(editionId).forEach { chapterDao.delete(it) }
        bookmarkDao.getByParent(editionId).forEach { bookmarkDao.delete(it) }
        progressDao.getByParent(editionId)?.let { progressDao.delete(it) }
        audioFileDao.getByParent(editionId).forEach { audioFileDao.delete(it) }
        editionDao.getById(editionId)?.let { editionDao.delete(it) }
    }

    data class EditionSnapshot(
        val edition: EditionEntity,
        val audioFiles: List<AudioFileEntity>,
        val chapters: List<ChapterEntity>,
        val chapterCompletions: List<ChapterCompletionEntity>,
        val bookmarks: List<BookmarkEntity>,
        val progress: ListeningProgressEntity?
    )

    suspend fun snapshotEdition(editionId: UUID): EditionSnapshot {
        val edition = editionDao.getById(editionId) ?: throw IllegalStateException("Edition not found")
        val audioFiles = audioFileDao.getByParent(editionId)
        val chapters = chapterDao.getByParent(editionId)
        val bookmarks = bookmarkDao.getByParent(editionId)
        val progress = progressDao.getByParent(editionId)
        return EditionSnapshot(edition, audioFiles, chapters, chapterCompletionDao.getByEdition(editionId), bookmarks, progress)
    }

    suspend fun deleteEdition(editionId: UUID) {
        val edition = editionDao.getById(editionId) ?: return
        val bookId = edition.bookId
        deleteEditionCascade(editionId)
        val remaining = editionDao.getByParent(bookId)
        if (remaining.isEmpty()) {
            favoriteBookDao.delete(bookId)
            bookDao.getById(bookId)?.let { bookDao.delete(it) }
        } else {
            bookDao.getById(bookId)?.let { book ->
                if (book.defaultEditionId == editionId) {
                    bookDao.update(book.copy(defaultEditionId = remaining.firstOrNull()?.id))
                }
            }
        }
    }

    // ── Collection Operations ──

    data class CollectionSnapshot(val collection: CollectionEntity, val memberBookIds: List<UUID>)

    suspend fun snapshotCollection(collectionId: UUID): CollectionSnapshot {
        val collection = collectionDao.getById(collectionId) ?: throw IllegalStateException("Collection not found")
        val refs = crossRefDao.getByParent(collectionId)
        return CollectionSnapshot(collection, refs.map { it.bookId })
    }

    suspend fun deleteCollection(collectionId: UUID) {
        val refs = crossRefDao.getByParent(collectionId)
        for (ref in refs) {
            crossRefDao.delete(collectionId, ref.bookId)
        }
        val collection = collectionDao.getById(collectionId) ?: return
        collectionDao.delete(collection)
    }

    suspend fun restoreCollection(snapshot: CollectionSnapshot) {
        collectionDao.insert(snapshot.collection)
        for (bookId in snapshot.memberBookIds) {
            crossRefDao.insert(CollectionBookCrossRef(snapshot.collection.id, bookId))
        }
    }

    // ── Create helpers ──

    suspend fun getOrCreateAuthor(name: String): AuthorEntity {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Author name cannot be blank")
        val existing = authorDao.getByName(trimmed)
        if (existing != null) return existing
        val created = AuthorEntity(name = trimmed, colorTheme = null)
        authorDao.insert(created)
        return created
    }

    suspend fun getOrCreateCollection(name: String): CollectionEntity {
        val trimmed = name.trim()
        if (trimmed.isBlank()) throw IllegalArgumentException("Collection name cannot be blank")
        val existing = collectionDao.getByName(trimmed)
        if (existing != null) return existing
        val created = CollectionEntity(
            name = trimmed,
            icon = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        )
        collectionDao.insert(created)
        return created
    }

    suspend fun addBookToCollection(collectionId: UUID, bookId: UUID) {
        if (crossRefDao.getById(collectionId, bookId) == null) {
            crossRefDao.insert(CollectionBookCrossRef(collectionId, bookId))
        }
    }

    suspend fun removeBookFromCollection(collectionId: UUID, bookId: UUID) {
        crossRefDao.delete(collectionId, bookId)
    }

    suspend fun updateCollectionName(collectionId: UUID, newName: String) {
        val collection = collectionDao.getById(collectionId) ?: return
        collectionDao.update(collection.copy(name = newName.trim()))
    }

    // ── Book Management (قائمة خيارات الكتاب + التحديد المتعدد) ──

    /** لقطة كاملة لكتاب: تكفي لإعادة البناء بعد الحذف أو لإرجاع الدمج. */
    data class BookSnapshot(
        val book: BookEntity,
        val editions: List<EditionSnapshot>,
        val collectionIds: List<UUID>,
        val isFavorite: Boolean
    )

    suspend fun snapshotBook(bookId: UUID): BookSnapshot? {
        val book = bookDao.getById(bookId) ?: return null
        val editions = editionDao.getByParent(bookId).mapNotNull { snapshotEdition(it.id).takeIf { s -> s.edition.id == it.id } }
        val collectionIds = crossRefDao.observeAll().first().filter { it.bookId == bookId }.map { it.collectionId }
        return BookSnapshot(book, editions, collectionIds, favoriteBookDao.getById(bookId) != null)
    }

    /** نقل جميع إصدارات كتاب إلى كتاب آخر ثم حذف الكتاب المصدر. */
    data class MergeBooksSnapshot(
        val source: BookSnapshot,
        val targetBookId: UUID,
        val targetDefaultEditionId: UUID?
    )

    suspend fun mergeBooks(sourceId: UUID, targetId: UUID): MergeBooksSnapshot? {
        if (sourceId == targetId) throw IllegalArgumentException("Cannot merge book with itself")
        val source = bookDao.getById(sourceId) ?: return null
        val target = bookDao.getById(targetId) ?: throw IllegalStateException("Target book not found")
        val sourceSnapshot = snapshotBook(sourceId) ?: return null

        val editions = editionDao.getByParent(sourceId)
        var targetDefault = target.defaultEditionId
        for (edition in editions) {
            val moved = edition.copy(bookId = targetId)
            editionDao.update(moved)
            if (targetDefault == null) targetDefault = moved.id
        }

        val sourceRefs = crossRefDao.observeAll().first().filter { it.bookId == sourceId }
        for (ref in sourceRefs) {
            if (crossRefDao.getById(ref.collectionId, targetId) == null) {
                crossRefDao.insert(CollectionBookCrossRef(ref.collectionId, targetId))
            }
        }
        if (sourceSnapshot.isFavorite && favoriteBookDao.getById(targetId) == null) {
            favoriteBookDao.insert(FavoriteBook(targetId, System.currentTimeMillis()))
        }

        bookDao.update(target.copy(defaultEditionId = targetDefault))
        bookDao.delete(source)
        return MergeBooksSnapshot(sourceSnapshot, targetId, targetDefault)
    }

    suspend fun undoMergeBooks(snapshot: MergeBooksSnapshot) {
        for (edition in snapshot.source.editions) {
            editionDao.update(edition.edition.copy(bookId = snapshot.source.book.id))
        }
        val targetRefs = crossRefDao.observeAll().first().filter { it.bookId == snapshot.targetBookId }
        for (ref in targetRefs) {
            if (ref.collectionId in snapshot.source.collectionIds) {
                crossRefDao.delete(ref.collectionId, snapshot.targetBookId)
            }
        }
        for (collectionId in snapshot.source.collectionIds) {
            crossRefDao.insert(CollectionBookCrossRef(collectionId, snapshot.source.book.id))
        }
        if (snapshot.source.isFavorite) {
            favoriteBookDao.delete(snapshot.targetBookId)
            favoriteBookDao.insert(FavoriteBook(snapshot.source.book.id, System.currentTimeMillis()))
        }
        bookDao.getById(snapshot.targetBookId)?.let {
            bookDao.update(it.copy(defaultEditionId = snapshot.targetDefaultEditionId))
        }
        bookDao.insert(snapshot.source.book)
    }

    /** حذف عدة كتب مع لقطة قابلة للاستعادة. */
    data class DeletedBooksSnapshot(val books: List<BookSnapshot>)

    suspend fun deleteBooks(bookIds: List<UUID>): DeletedBooksSnapshot {
        val snapshots = bookIds.mapNotNull { snapshotBook(it) }
        for (book in snapshots) {
            deleteBook(book.book.id)
        }
        return DeletedBooksSnapshot(snapshots)
    }

    suspend fun restoreBooks(snapshot: DeletedBooksSnapshot) {
        for (book in snapshot.books) {
            bookDao.insert(book.book)
            for (edition in book.editions) {
                editionDao.insert(edition.edition)
                edition.audioFiles.forEach { audioFileDao.insert(it) }
                edition.chapters.forEach { chapterDao.insert(it) }
                edition.chapterCompletions.forEach { chapterCompletionDao.insert(it) }
                edition.bookmarks.forEach { bookmarkDao.insert(it) }
                edition.progress?.let { progressDao.insert(it) }
            }
            for (collectionId in book.collectionIds) {
                crossRefDao.insert(CollectionBookCrossRef(collectionId, book.book.id))
            }
            if (book.isFavorite) {
                favoriteBookDao.insert(FavoriteBook(book.book.id, System.currentTimeMillis()))
            }
        }
    }

    suspend fun moveBooksToAuthor(bookIds: List<UUID>, authorId: UUID) {
        for (id in bookIds) moveBookToAuthor(id, authorId)
    }

    suspend fun moveBooksToSeries(bookIds: List<UUID>, seriesId: UUID?) {
        for (id in bookIds) moveBookToSeries(id, seriesId)
    }

    suspend fun addBooksToCollection(collectionId: UUID, bookIds: List<UUID>) {
        for (id in bookIds) addBookToCollection(collectionId, id)
    }

    suspend fun setBooksFavorite(bookIds: List<UUID>, favorite: Boolean) {
        for (id in bookIds) {
            val exists = favoriteBookDao.getById(id) != null
            if (favorite && !exists) favoriteBookDao.insert(FavoriteBook(id, System.currentTimeMillis()))
            if (!favorite && exists) favoriteBookDao.delete(id)
        }
    }

    suspend fun removeBookFromSeries(bookId: UUID) = moveBookToSeries(bookId, null)

    suspend fun setDefaultEdition(bookId: UUID, editionId: UUID) {
        val book = bookDao.getById(bookId) ?: return
        bookDao.update(book.copy(defaultEditionId = editionId))
    }

    /**
     * إلحاق ملف صوتي بنسخة: بصمة بنفس نمط الفحص (\*size:lastModified:uri\*),
     * تحديث مدة النسخة، واستيراد فصول M4B المضمّنة بإزاحة = مجموع مدد الملفات السابقة.
     * يعيد عدد الفصول المستوردة.
     */
    suspend fun addAudioFileToEdition(
        editionId: UUID,
        uri: Uri,
        fileName: String,
        fileSizeBytes: Long,
        lastModified: Long,
        metadata: AudioMetadata
    ): Int {
        val edition = editionDao.getById(editionId) ?: throw IllegalStateException("Edition not found")
        val existing = audioFileDao.getByParent(editionId)
        val orderIndex = (existing.maxOfOrNull { it.orderIndex } ?: -1) + 1
        val baseMs = existing.sumOf { it.durationMs }

        audioFileDao.insert(
            AudioFileEntity(
                id = UUID.randomUUID(),
                editionId = editionId,
                fileUri = uri.toString(),
                relativePath = fileName,
                fileName = fileName,
                orderIndex = orderIndex,
                durationMs = metadata.durationMs,
                fileSizeBytes = fileSizeBytes,
                lastModified = lastModified,
                contentFingerprint = "$fileSizeBytes:$lastModified:$uri",
                mimeType = metadata.mimeType,
                fileStatus = FileStatus.AVAILABLE
            )
        )

        var importedChapters = 0
        if (metadata.embeddedChapters.isNotEmpty()) {
            val chapterOffset = chapterDao.getByParent(editionId).size
            metadata.embeddedChapters.forEachIndexed { index, chapter ->
                chapterDao.insert(
                    ChapterEntity(
                        id = UUID.randomUUID(),
                        editionId = editionId,
                        title = chapter.title,
                        startPositionMs = baseMs + chapter.startPositionMs,
                        orderIndex = chapterOffset + index,
                        createdFrom = ChapterCreatedFrom.IMPORTED
                    )
                )
                importedChapters++
            }
        }

        editionDao.update(edition.copy(totalDurationMs = edition.totalDurationMs + metadata.durationMs))
        return importedChapters
    }

    // ── Demo Data ──

    /**
     * إزالة كل بيانات التجربة (كتب + جذور + أي عناصر تجريبية يتيمة) دون المساس ببيانات المستخدم.
     *
     * الترتيب حاسم بسبب قيود المفاتيح الأجنبية:
     *  1) الكتب التجريبية أولًا (بـ clearOps Cascade كامل ليُحذف ما تحتها من إصدارات/فصول/ملفات…).
     *  2) الكتب التجريبية «القديمة» التي بُذرت قبل v4 فبقيت بلا isDemo=1 (قد تُعامل الآن أيضًا
     *     عبر Migration 6→7 عند الترقية): مؤلفُها معرف بذر ذاتي أو نسخةٌ تحت مجلد %demo% —
     *     ودائمًا نستثني الكتب التي أكّد المستخدم عنوانها يدويًا (isTitleUserConfirmed=1).
     *  3) جذور التجربة (isDemo=1) — تُحذف فقط ما لم يبقَ تحتها إصدار (إصدارات الكتب التجريبية
     *     كانت تشير إليها، وقد رُحِّلت في الخطوة 1؛ الإصدارات الحقيقية تشير لاحقًا لجذر مستخدم).
     *  4) المؤلفون/السلاسل/المجموعات التجريبية اليتيمة: isDemo=1 وبلا أي كتب/أعضاء حقيقية.
     *     قاعدة «يتيم = لا كتب حقيقية» تحمي المؤلف التجريبي الذي أعاد الفحص الحقيقي استخدامه
     *     (مثل «أحمد خالد توفيق»): يبقى إن كانت تحته كتب حقيقية، وتُرفع علامة isDemo عنه.
     *
     * ملاحظة: لا تُحذف العناصر إلا إذا كان isDemo=1، فأي مؤلف/سلسلة/مجموعة أنشأها المستخدم
     * (حتى لو أصبحت فارغة لاحقًا) تبقى بلا مساس مهما تكرر هذا التنظيف.
     *
     * @return عدد الكتب المحذوفة + ما إذا بقيت عناصر تجريبية (لرسالة التأكيد في الإعدادات).
     */
    suspend fun clearDemoData(): ClearDemoDataResult {
        val beforeFlagged = bookDao.countDemoBooks()
        val beforeByAuthor = bookDao.countBooksByDemoAuthors(DEMO_AUTHOR_IDS)
        Log.i(TAG, "clearDemoData: before → isDemo=1: $beforeFlagged, demoAuthor: $beforeByAuthor")

        val demoBooks = bookDao.getDemoBooks()
        for (book in demoBooks) {
            deleteBookCascade(book.id)
        }
        bookDao.deleteDemoBooks()

        // كتب تجريبية قديمة بلا وسم isDemo (احتياط لبيئات لم يمرّ بها Migration 6→7):
        // authorId أحد معرفات البذر الذاتية OR نسخة تحت مجلد %demo% — دون الكتب المؤكَّدة يدويًا.
        val legacyDemoBooks = bookDao.getLegacyDemoBooks(DEMO_AUTHOR_IDS)
        for (book in legacyDemoBooks) {
            deleteBookCascade(book.id)
        }

        for (root in libraryRootDao.getDemoRoots()) {
            if (editionDao.getByRoot(root.id).isEmpty()) {
                libraryRootDao.delete(root)
            }
        }

        authorDao.getDemoOrphans().forEach { authorDao.delete(it) }
        authorDao.clearDemoFlagForAuthorsWithBooks()

        seriesDao.getDemoOrphans().forEach { seriesDao.delete(it) }
        seriesDao.clearDemoFlagForSeriesWithBooks()

        collectionDao.getDemoOrphans().forEach { collectionDao.delete(it) }
        collectionDao.clearDemoFlagForCollectionsWithMembers()

        val afterFlagged = bookDao.countDemoBooks()
        val afterByAuthor = bookDao.countBooksByDemoAuthors(DEMO_AUTHOR_IDS)
        if (afterFlagged == 0 && afterByAuthor == 0) {
            Log.i(TAG, "clearDemoData: after → isDemo=1: 0, demoAuthor: 0 — verified clean")
        } else {
            Log.w(TAG, "clearDemoData: residual demo books remain (≈ كتب مؤكَّدة يدويًا) isDemo=1: $afterFlagged, demoAuthor: $afterByAuthor")
        }

        val removedIds = (demoBooks.map { it.id } + legacyDemoBooks.map { it.id }).toSet().size
        return ClearDemoDataResult(removedIds, afterFlagged + afterByAuthor > 0)
    }

    // ── STAGE 4: استئناف الفحص ──

    /**
     * أحدث checkpoint صالح: يُبنى [ScanResume.ResumeInfo] من الجدول مباشرة
     * (لا حاجة لكامل AppDatabase هنا).
     */
    suspend fun getScanResumeInfo(now: Long = System.currentTimeMillis()): ScanResume.ResumeInfo? {
        val all = scanCheckpointDao.getAll()
        all.filter { now - it.scannedAt > ScanResume.TTL_MS }.forEach { scanCheckpointDao.deleteForRoot(it.rootId) }
        val fresh = all.filter { now - it.scannedAt <= ScanResume.TTL_MS }.maxByOrNull { it.scannedAt } ?: return null
        val root = libraryRootDao.getById(fresh.rootId) ?: run {
            scanCheckpointDao.deleteForRoot(fresh.rootId)
            return null
        }
        if (!root.isEnabled) return null
        return ScanResume.ResumeInfo(fresh.rootId, root.displayName, fresh.lastProcessedFolderPath, fresh.scannedAt)
    }

    // ── STAGE 6B: حذف/إعادة تسمية الجذور ──

    data class RootDeleteResult(val rootName: String, val booksRemoved: Int, val editionsRemoved: Int)

    /**
     * حذف جذر مكتبة مع تتابع صريح كامل: كل إصدارات الجذر تُحذف بتتابع
     * الإصدار (ملفات/فصول/علامات/تقدم)، ثم الكتب اليتيمة (بلا إصدارات
     * باقية في أي جذر)، ثم الاكتشافات ونقاط التوقف وتعديلات الاستهلال،
     * وأخيرًا صف الجذر نفسه. التقدّمات والعلامات لإصدارات الجذر تُحذف
     * معها (هي ملك ذلك الجذر)؛ كتب لها إصدارات في جذور أخرى تبقى.
     */
    suspend fun deleteLibraryRoot(rootId: UUID): RootDeleteResult? {
        val root = libraryRootDao.getById(rootId) ?: return null
        val editions = editionDao.getByRoot(rootId)
        val touchedBookIds = editions.map { it.bookId }.toSet()
        var editionsRemoved = 0
        editions.forEach { edition ->
            deleteEditionCascade(edition.id)
            editionsRemoved++
        }
        var booksRemoved = 0
        touchedBookIds.forEach { bookId ->
            if (editionDao.getByParent(bookId).isEmpty()) {
                favoriteBookDao.delete(bookId)
                bookDao.getById(bookId)?.let { bookDao.delete(it) }
                booksRemoved++
            }
        }
        pendingDiscoveryDao.deleteForRoot(rootId)
        scanCheckpointDao.deleteForRoot(rootId)
        onboardingEditDao.deleteForRoot(root.uri)
        libraryRootDao.delete(root)
        Log.i(TAG, "deleteLibraryRoot name=${root.displayName} editions=$editionsRemoved books=$booksRemoved")
        return RootDeleteResult(root.displayName, booksRemoved, editionsRemoved)
    }

    /** إعادة تسمية عرضية فقط — لا تمسّ المسارات ولا الهوية البنيوية. */
    suspend fun renameLibraryRoot(rootId: UUID, newName: String): Boolean {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return false
        val root = libraryRootDao.getById(rootId) ?: return false
        libraryRootDao.update(root.copy(displayName = trimmed))
        return true
    }

    // ── STAGE 5: دمج الجذور أحادية المؤلف ──

    data class MergeCandidate(val rootId: UUID, val rootName: String, val authorName: String, val bookCount: Int)
    data class MergePreview(val candidates: List<MergeCandidate>, val totalBooks: Int, val totalEditions: Int)

    /**
     * كشف نمط «جذر لكل مؤلف»: جذور مفعّلة غير تجريبية، كلٌّ منها يحمل
     * إصدارات لمؤلف واحد غير فارغ، والمؤلف لا يظهر في أي جذر آخر.
     * الإصدارات بلا مؤلف (authorId=null) تُستبعد من الترشيح — دمجها قد
     * يغيّر تصنيفها، فيبقى دمجها يدويًا.
     */
    suspend fun detectMergeCandidates(): List<MergeCandidate> {
        val roots = libraryRootDao.getAll().filter { it.isEnabled && !it.isDemo }
        if (roots.size < 2) return emptyList()
        // مؤلف كل جذر: كل الإصدارات تشترك في authorId واحد غير null.
        val authorByRoot = LinkedHashMap<UUID, UUID?>()
        val editionsByRoot = LinkedHashMap<UUID, List<EditionEntity>>()
        roots.forEach { root ->
            val editions = editionDao.getByRoot(root.id)
            editionsByRoot[root.id] = editions
            authorByRoot[root.id] = editions.map { it.authorId }.toSet().singleOrNull()
        }
        // المؤلفون الحصريون: لا يظهرون في جذرين.
        val rootCountByAuthor = authorByRoot.values.filterNotNull().groupingBy { it }.eachCount()
        return roots.mapNotNull { root ->
            val authorId = authorByRoot[root.id] ?: return@mapNotNull null
            if (rootCountByAuthor[authorId] != 1) return@mapNotNull null
            val editions = editionsByRoot[root.id].orEmpty()
            if (editions.isEmpty()) return@mapNotNull null
            val authorName = authorDao.getById(authorId)?.name ?: return@mapNotNull null
            val bookCount = editions.map { it.bookId }.toSet().size
            MergeCandidate(root.id, root.displayName, authorName, bookCount)
        }
    }

    /** معاينة الدمج: المرشحون + إجمالي الكتب/الإصدارات المتأثرة. */
    suspend fun previewMerge(rootIds: List<UUID>): MergePreview {
        val candidates = detectMergeCandidates().filter { it.rootId in rootIds }
        val editions = candidates.sumOf { editionDao.getByRoot(it.rootId).size }
        return MergePreview(candidates, candidates.sumOf { it.bookCount }, editions)
    }

    /**
     * تطبيق الدمج تحت جذر أب جديد (`parentUri` يختاره المستخدم — المجلد
     * الحاوي لمجلدات المؤلفين):
     * - تُنقل كل إصدارات الجذور الأبناء إلى الأب مع بادئة مسار = اسم مجلد
     *   الابن (آخر مقطع من uri الابن)، فتحافظ `sourceFolderPath` على معناها
     *   ويبقى الفحص اللاحق مطابقًا للملفات نفسها.
     * - تُنقل الاكتشافات المعلقة بنفس البادئة، وتُنقل نقاط التوقف بحذفها
     *   (الفحص اللاحق للأب يعيد بناءها).
     * - تُحذف صفوف الجذور الأبناء.
     * - لا تُمسّ الكتب/الإصدارات/الملفات/الفصول/التقدم/العلامات: كلها
     *   مفاتيحها صفوف محفوظة، فيبقى تقدم الاستماع كما هو.
     * @return id الجذر الأب الجديد لجدولة فحصه.
     */
    suspend fun applyMerge(parentUri: String, parentName: String, childRootIds: List<UUID>): UUID {
        val preview = previewMerge(childRootIds)
        require(preview.candidates.isNotEmpty()) { "no-merge-candidates" }
        val parent = LibraryRootEntity(
            uri = parentUri,
            displayName = parentName.trim().ifBlank { parentUri.substringAfterLast('/') },
            isPriority = false,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        libraryRootDao.insert(parent)
        preview.candidates.forEach { candidate ->
            val child = libraryRootDao.getById(candidate.rootId) ?: return@forEach
            val prefix = child.uri.trimEnd('/').substringAfterLast('/').ifBlank { child.displayName }
            editionDao.getByRoot(child.id).forEach { edition ->
                editionDao.update(
                    edition.copy(
                        libraryRootId = parent.id,
                        sourceFolderPath = "$prefix/${edition.sourceFolderPath}".trim('/')
                    )
                )
            }
            pendingDiscoveryDao.reassignRoot(child.id, parent.id, prefix)
            scanCheckpointDao.deleteForRoot(child.id)
            onboardingEditDao.deleteForRoot(child.uri)
            libraryRootDao.delete(child)
        }
        Log.i(TAG, "mergeRoots parent=${parent.displayName} children=${preview.candidates.size} books=${preview.totalBooks}")
        return parent.id
    }
}
