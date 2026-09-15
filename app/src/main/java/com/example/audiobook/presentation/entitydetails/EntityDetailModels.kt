package com.example.audiobook.presentation.entitydetails

import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import java.util.UUID

/** صف كتاب داخل صفحة تفاصيل سلسلة/مؤلف/مجموعة — بيانات حقيقية من المكتبة. */
data class EntityBookRow(
    val bookId: UUID,
    val seriesId: UUID?,
    val seriesName: String?,
    val authorId: UUID,
    val authorName: String,
    val title: String,
    val editionId: UUID?,
    val coverColor: Long,
    val progressFraction: Float,
    val remainingMs: Long,
    val hasProgress: Boolean,
    val orderInSeries: Int?
)

internal fun parseColor(hex: String?, default: Long): Long {
    if (hex.isNullOrBlank()) return default
    return runCatching { android.graphics.Color.parseColor(hex).toLong() }.getOrDefault(default)
}

/** يبني صفوف الكتب من بيانات المكتبة الحقيقية مع النسخة الفعّالة والتقدّم لكل كتاب. */
internal fun buildEntityBookRows(
    books: List<BookEntity>,
    authors: List<AuthorEntity>,
    editions: List<EditionEntity>,
    progressList: List<ListeningProgressEntity>,
    series: List<SeriesEntity>
): List<EntityBookRow> {
    val authorById = authors.associateBy { it.id }
    val seriesById = series.associateBy { it.id }
    val progressById = progressList.associateBy { it.editionId }
    return books.map { book ->
        val edition = effectiveEdition(book, editions, progressById)
        val progress = edition?.let { progressById[it.id] }
        val played = progress?.currentPositionMs ?: 0L
        val total = edition?.totalDurationMs ?: 0L
        val fraction = if (total > 0L) (played.toFloat() / total).coerceIn(0f, 1f) else 0f
        EntityBookRow(
            bookId = book.id,
            seriesId = book.seriesId,
            seriesName = book.seriesId?.let { seriesById[it]?.name },
            authorId = book.authorId,
            authorName = authorById[book.authorId]?.name ?: "",
            title = book.title,
            editionId = edition?.id,
            coverColor = parseColor(
                seriesById[book.seriesId]?.colorTheme ?: authorById[book.authorId]?.colorTheme,
                0xFF356B68
            ),
            progressFraction = fraction,
            remainingMs = (total - played).coerceAtLeast(0L),
            hasProgress = progress != null && played > 0L,
            orderInSeries = book.orderInSeries
        )
    }
}

private fun effectiveEdition(
    book: BookEntity,
    editions: List<EditionEntity>,
    progress: Map<UUID, ListeningProgressEntity>
): EditionEntity? {
    val bookEditions = editions.filter { it.bookId == book.id }
    book.defaultEditionId?.let { id -> return bookEditions.firstOrNull { it.id == id } }
    return bookEditions.firstOrNull { progress[it.id]?.status == ProgressStatus.IN_PROGRESS }
        ?: bookEditions.firstOrNull()
}