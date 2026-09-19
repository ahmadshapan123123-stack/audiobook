package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.dao.AudioFileAggregateDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import java.util.UUID
import javax.inject.Inject

/**
 * Resolves book-level progress from edition-level progress.
 *
 * Key rule: progress lives on Edition, but the UI shows book-level progress.
 * The effective edition for a book is: defaultEditionId > first IN_PROGRESS > first edition.
 * This class also provides helpers for computing aggregate progress across all editions.
 */
class ProgressResolver @Inject constructor(
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao
) {

    data class BookProgress(
        val bookId: UUID,
        val effectiveEditionId: UUID?,
        val currentPositionMs: Long,
        val totalDurationMs: Long,
        val progressFraction: Float,
        val remainingMs: Long,
        val status: ProgressStatus
    )

    suspend fun resolveBookProgress(bookId: UUID): BookProgress? {
        val book = bookDao.getById(bookId) ?: return null
        val editions = editionDao.getByParent(bookId)
        if (editions.isEmpty()) return null

        val edition = effectiveEdition(book, editions) ?: return null
        val progress = progressDao.getByParent(edition.id)
        val position = progress?.currentPositionMs ?: 0L
        val total = edition.totalDurationMs
        val fraction = if (total > 0L) (position.toFloat() / total).coerceIn(0f, 1f) else 0f

        return BookProgress(
            bookId = bookId,
            effectiveEditionId = edition.id,
            currentPositionMs = position,
            totalDurationMs = total,
            progressFraction = fraction,
            remainingMs = (total - position).coerceAtLeast(0L),
            status = progress?.status ?: ProgressStatus.NOT_STARTED
        )
    }

    suspend fun resolveAllBooksProgress(): List<BookProgress> {
        val books = bookDao.getAll()
        return books.mapNotNull { resolveBookProgress(it.id) }
    }

    suspend fun resolveInProgressBooks(): List<BookProgress> {
        return resolveAllBooksProgress().filter {
            it.status == ProgressStatus.IN_PROGRESS || (it.progressFraction in 0.01f..0.99f)
        }.sortedByDescending { it.currentPositionMs }
    }

    suspend fun resolveFinishedBooks(): List<BookProgress> {
        return resolveAllBooksProgress().filter {
            it.status == ProgressStatus.FINISHED || it.progressFraction >= 1f
        }
    }

    private fun effectiveEdition(
        book: BookEntity,
        editions: List<com.example.audiobook.data.room.entity.EditionEntity>
    ): com.example.audiobook.data.room.entity.EditionEntity? {
        val bookEditions = editions.filter { it.bookId == book.id }
        book.defaultEditionId?.let { id -> return bookEditions.firstOrNull { it.id == id } }
        return bookEditions.firstOrNull()
    }
}
