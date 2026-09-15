package com.example.audiobook.presentation.home

import android.graphics.Color
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import java.util.UUID

/**
 * محوّل مشترك بين الرئيسية ومركز الاستماع: يحوّل البيانات الخام من المكتبة
 * إلى [HomeBook] موحّد، فيتجنب هذا دوّامة منطق مكرر بين الشاشتين.
 */
internal object HomeMapper {

    /** رسم كل الكتب إلى [HomeBook] مع اعتماد النسخة الفعّالة والتقدم المرتبط بها. */
    fun toHomeBooks(
        books: List<BookEntity>,
        authors: List<AuthorEntity>,
        editions: List<EditionEntity>,
        progressList: List<ListeningProgressEntity>,
        series: List<SeriesEntity>
    ): List<HomeBook> {
        val authorById = authors.associateBy { it.id }
        val seriesById = series.associateBy { it.id }
        val progressById = progressList.associateBy { it.editionId }
        val byAddedIndex = books.withIndex().associate { it.value.id to it.index }
        return books.mapIndexed { index, book ->
            val edition = effectiveEdition(book, editions, progressById)
            val progress = edition?.let { progressById[it.id] }
            val played = progress?.currentPositionMs ?: 0L
            val total = edition?.totalDurationMs ?: 0L
            val fraction = if (total > 0L) (played.toFloat() / total).coerceIn(0f, 1f) else 0f
            HomeBook(
                bookId = book.id,
                seriesId = book.seriesId,
                editionId = edition?.id,
                title = book.title,
                authorName = authorById[book.authorId]?.name ?: "",
                seriesName = book.seriesId?.let { seriesById[it]?.name },
                coverColor = parseColor(
                    book.seriesId?.let { seriesById[it]?.colorTheme }
                        ?: authorById[book.authorId]?.colorTheme,
                    0xFF356B68
                ),
                genre = book.genre,
                progressFraction = fraction,
                remainingMs = (total - played).coerceAtLeast(0L),
                hasProgress = progress != null && played > 0L,
                addedOrder = byAddedIndex[book.id] ?: index,
                lastPlayedAt = progress?.lastPlayedAt ?: 0L
            )
        }
    }

    /** عنوان الفصل الحالي عند موضع معيّن (أو أول فصل إن لم يُحدَّد الموضع). */
    fun chapterTitleAt(
        chapters: List<ChapterEntity>,
        editionId: UUID?,
        positionMs: Long
    ): String? {
        if (editionId == null) return null
        val chapter = chapters
            .filter { it.editionId == editionId }
            .sortedBy { it.startPositionMs }
            .lastOrNull { it.startPositionMs <= positionMs }
            ?: chapters.firstOrNull { it.editionId == editionId }
        return chapter?.title
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

    private fun parseColor(hex: String?, default: Long): Long {
        if (hex == null) return default
        return runCatching { Color.parseColor(hex).toLong() }.getOrDefault(default)
    }
}