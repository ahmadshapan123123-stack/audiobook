package com.example.audiobook.domain.usecases

import android.net.Uri
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.room.dao.*
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

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
    private val libraryRootDao: LibraryRootDao
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
     *  2) جذور التجربة (isDemo=1) — تُحذف فقط ما لم يبقَ تحتها إصدار (إصدارات الكتب التجريبية
     *     كانت تشير إليها، وقد رُحِّلت في الخطوة 1؛ الإصدارات الحقيقية تشير لاحقًا لجذر مستخدم).
     *  3) المؤلفون/السلاسل/المجموعات التجريبية اليتيمة: isDemo=1 وبلا أي كتب/أعضاء حقيقية.
     *     قاعدة «يتيم = لا كتب حقيقية» تحمي المؤلف التجريبي الذي أعاد الفحص الحقيقي استخدامه
     *     (مثل «أحمد خالد توفيق»): يبقى إن كانت تحته كتب حقيقية، وتُرفع علامة isDemo عنه.
     *
     * ملاحظة: لا تُحذف العناصر إلا إذا كان isDemo=1، فأي مؤلف/سلسلة/مجموعة أنشأها المستخدم
     * (حتى لو أصبحت فارغة لاحقًا) تبقى بلا مساس مهما تكرر هذا التنظيف.
     */
    suspend fun clearDemoData() {
        val demoBooks = bookDao.getDemoBooks()
        for (book in demoBooks) {
            deleteBookCascade(book.id)
        }
        bookDao.deleteDemoBooks()

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
    }
}
