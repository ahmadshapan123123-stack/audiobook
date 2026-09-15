package com.example.audiobook.presentation.saved

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.BookmarkDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** علامة أو ملاحظة محفوظة مقترنة بكتاب حقيقي من المكتبة. */
data class SavedBookmark(
    val bookmarkId: UUID,
    val editionId: UUID,
    val bookId: UUID,
    val bookTitle: String,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    val positionMs: Long,
    val createdAt: Long,
    val noteText: String?,
    val chapterLabel: String?
)

/** مجموعة محفوظات (علامات/ملاحظات) لنفس الكتاب لترتيب الصفحة. */
data class SavedBookGroup(
    val bookId: UUID,
    val bookTitle: String,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    val items: List<SavedBookmark>
)

/** فصل أنشأه المستخدم نفسه. */
data class SavedChapter(
    val chapterId: UUID,
    val editionId: UUID,
    val bookId: UUID,
    val bookTitle: String,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    val title: String?,
    val startPositionMs: Long,
    val orderIndex: Int
)

data class SavedChapterGroup(
    val bookId: UUID,
    val bookTitle: String,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    val chapters: List<SavedChapter>
)

data class SavedUiState(
    val query: String = "",
    val bookmarks: List<SavedBookGroup> = emptyList(),
    val notes: List<SavedBookGroup> = emptyList(),
    val chapters: List<SavedChapterGroup> = emptyList(),
    val totalBookmarks: Int = 0,
    val totalNotes: Int = 0,
    val totalChapters: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val chapterDao: ChapterDao,
    private val editionDao: EditionDao,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao
) : ViewModel() {

    private val queryFlow = MutableStateFlow("")

    val uiState: StateFlow<SavedUiState> = combine(
        bookmarkDao.observeAll(),
        chapterDao.observeAll(),
        editionDao.observeAll(),
        bookDao.observeAll(),
        authorDao.observeAll(),
        seriesDao.observeAll(),
        queryFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val bookmarks = values[0] as List<BookmarkEntity>
        val chapters = values[1] as List<ChapterEntity>
        val editions = values[2] as List<EditionEntity>
        val books = values[3] as List<BookEntity>
        val authors = values[4] as List<AuthorEntity>
        val series = values[5] as List<SeriesEntity>
        val query = values[6] as String

        val authorById = authors.associateBy { it.id }
        val seriesById = series.associateBy { it.id }
        val editionById = editions.associateBy { it.id }
        val bookById = books.associateBy { it.id }
        val chaptersByEdition = chapters.groupBy { it.editionId }

        fun coverColor(book: BookEntity): Long {
            val theme = book.seriesId?.let { seriesById[it]?.colorTheme }
                ?: authorById[book.authorId]?.colorTheme
            return parseColor(theme, 0xFF356B68)
        }

        /** الفصل المنطبق على موضع (أقرب فصل يبدأ قبل الموضع). */
        fun chapterLabel(editionId: UUID?, positionMs: Long): String? {
            if (editionId == null) return null
            return chaptersByEdition[editionId]
                ?.sortedBy { it.startPositionMs }
                ?.lastOrNull { it.startPositionMs <= positionMs }
                ?.title
        }

        fun toBookmark(b: BookmarkEntity): SavedBookmark? {
            val edition = editionById[b.editionId] ?: return null
            val book = bookById[edition.bookId] ?: return null
            return SavedBookmark(
                bookmarkId = b.id,
                editionId = b.editionId,
                bookId = book.id,
                bookTitle = book.title,
                authorName = authorById[book.authorId]?.name ?: "",
                seriesName = book.seriesId?.let { seriesById[it]?.name },
                coverColor = coverColor(book),
                positionMs = b.positionMs,
                createdAt = b.createdAt,
                noteText = b.noteText,
                chapterLabel = chapterLabel(b.editionId, b.positionMs)
            )
        }

        fun groupByBook(items: List<SavedBookmark>): List<SavedBookGroup> =
            items.groupBy { it.bookId }.map { (_, groupItems) ->
                val first = groupItems.first()
                SavedBookGroup(
                    bookId = first.bookId,
                    bookTitle = first.bookTitle,
                    authorName = first.authorName,
                    seriesName = first.seriesName,
                    coverColor = first.coverColor,
                    items = groupItems.sortedByDescending { it.createdAt }
                )
            }.sortedByDescending { it.items.maxOf { it.createdAt } }

        val q = query.trim()
        fun matches(saved: SavedBookmark): Boolean {
            if (q.isBlank()) return true
            return saved.bookTitle.contains(q, ignoreCase = true) ||
                saved.authorName.contains(q, ignoreCase = true) ||
                saved.noteText.orEmpty().contains(q, ignoreCase = true) ||
                saved.chapterLabel.orEmpty().contains(q, ignoreCase = true)
        }

        val bookmarksAll = bookmarks
            .filter { it.type == BookmarkType.BOOKMARK }
            .mapNotNull { toBookmark(it) }
        val notesAll = bookmarks
            .filter { it.type == BookmarkType.NOTE && !it.noteText.isNullOrBlank() }
            .mapNotNull { toBookmark(it) }

        val userChapters = chapters
            .filter { it.createdFrom == ChapterCreatedFrom.USER_MARK }
            .mapNotNull { chapter ->
                val edition = editionById[chapter.editionId] ?: return@mapNotNull null
                val book = bookById[edition.bookId] ?: return@mapNotNull null
                SavedChapter(
                    chapterId = chapter.id,
                    editionId = chapter.editionId,
                    bookId = book.id,
                    bookTitle = book.title,
                    authorName = authorById[book.authorId]?.name ?: "",
                    seriesName = book.seriesId?.let { seriesById[it]?.name },
                    coverColor = coverColor(book),
                    title = chapter.title,
                    startPositionMs = chapter.startPositionMs,
                    orderIndex = chapter.orderIndex
                )
            }

        val filteredBookmarks = bookmarksAll.filter { matches(it) }
        val filteredNotes = notesAll.filter { matches(it) }
        val filteredChapters = userChapters.filter {
            q.isBlank() || it.bookTitle.contains(q, ignoreCase = true) ||
                it.authorName.contains(q, ignoreCase = true) ||
                it.seriesName.orEmpty().contains(q, ignoreCase = true) ||
                it.title.orEmpty().contains(q, ignoreCase = true)
        }

        SavedUiState(
            query = query,
            bookmarks = groupByBook(filteredBookmarks),
            notes = groupByBook(filteredNotes),
            chapters = filteredChapters.groupBy { it.bookId }
                .map { (_, groupItems) ->
                    val first = groupItems.first()
                    SavedChapterGroup(
                        bookId = first.bookId,
                        bookTitle = first.bookTitle,
                        authorName = first.authorName,
                        seriesName = first.seriesName,
                        coverColor = first.coverColor,
                        chapters = groupItems.sortedBy { it.orderIndex }
                    )
                }
                .sortedBy { it.bookTitle },
            totalBookmarks = bookmarksAll.size,
            totalNotes = notesAll.size,
            totalChapters = userChapters.size,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SavedUiState())

    fun updateQuery(query: String) {
        queryFlow.value = query
    }

    fun deleteBookmark(bookmarkId: UUID) {
        viewModelScope.launch {
            bookmarkDao.getById(bookmarkId)?.let { bookmarkDao.delete(it) }
        }
    }

    fun deleteChapter(chapterId: UUID) {
        viewModelScope.launch {
            chapterDao.getById(chapterId)?.let { chapterDao.delete(it) }
        }
    }

    fun renameChapter(chapterId: UUID, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            chapterDao.getById(chapterId)?.let { chapterDao.update(it.copy(title = newTitle)) }
        }
    }

    private fun parseColor(hex: String?, default: Long): Long {
        if (hex == null) return default
        return runCatching { Color.parseColor(hex).toLong() }.getOrDefault(default)
    }
}