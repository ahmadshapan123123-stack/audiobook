package com.example.audiobook.presentation.library

import android.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AudioFileDao
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.CollectionBookCrossRefDao
import com.example.audiobook.data.room.dao.CollectionDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.FavoriteBookDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FavoriteBook
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.domain.usecases.ArabicSearchNormalizer
import com.example.audiobook.domain.usecases.ScanProgress
import com.example.audiobook.domain.usecases.ScanProgressBus
import com.example.audiobook.presentation.common.DisplayLabels
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryBookUi(
    val book: BookEntity,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    val effectiveEditionId: UUID?,
    val hasMissingFile: Boolean,
    val progressFraction: Float,
    val remainingMs: Long,
    val isFavorite: Boolean,
    val addedOrder: Int,
    val lastPlayedAt: Long
)

data class LibraryUiState(
    val books: List<LibraryBookUi> = emptyList(),
    val query: LibraryQuery = LibraryQuery(),
    val collections: List<CollectionEntity> = emptyList(),
    val collectionMembers: Map<String, Set<UUID>> = emptyMap(),
    val seriesNames: List<String> = emptyList(),
    /** الأنواع الموجودة فعليًا في المكتبة (بدل نوع واحد مثبّت في الواجهة). */
    val genreNames: List<String> = emptyList()
) {
    val filtered: List<LibraryBookUi>
        get() {
            val base = when (query.status) {
                LibraryStatusFilter.ALL -> books
                LibraryStatusFilter.IN_PROGRESS -> books.filter { it.progressFraction in 0.01f..0.99f }
                LibraryStatusFilter.FINISHED -> books.filter { it.progressFraction >= 1f }
                LibraryStatusFilter.NOT_STARTED -> books.filter { it.progressFraction <= 0f }
            }
            val searchFiltered = base.filter { book ->
                ArabicSearchNormalizer.matches(query.search, book.book.title, book.authorName, book.seriesName)
            }
            val genreFiltered = searchFiltered.filter { query.genre == null || it.book.genre == query.genre }
            val seriesFiltered = genreFiltered.filter { query.series == null || it.seriesName == query.series }
            return seriesFiltered.sortedWith(
                when (query.sort) {
                    LibrarySort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER, { it.book.title })
                    LibrarySort.ADDED_DATE -> compareByDescending { it.addedOrder }
                    LibrarySort.LAST_PLAYED -> compareByDescending { it.lastPlayedAt }
                    LibrarySort.PROGRESS -> compareByDescending { it.progressFraction }
                }
            )
        }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val favoriteBookDao: FavoriteBookDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val seriesDao: SeriesDao,
    private val audioFileDao: AudioFileDao
) : ViewModel() {

    private val query = MutableStateFlow(LibraryQuery())

    val uiState: StateFlow<LibraryUiState> = combine(
        bookDao.observeAll(),
        authorDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll(),
        favoriteBookDao.observeAll(),
        collectionDao.observeAll(),
        crossRefDao.observeAll(),
        seriesDao.observeAll(),
        audioFileDao.observeAll(),
        query
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val books = values[0] as List<BookEntity>
        val authors = values[1] as List<AuthorEntity>
        val editions = values[2] as List<EditionEntity>
        val progressList = values[3] as List<ListeningProgressEntity>
        val favorites = values[4] as List<FavoriteBook>
        val collections = values[5] as List<CollectionEntity>
        val crossRefs = values[6] as List<com.example.audiobook.data.room.entity.CollectionBookCrossRef>
        val series = values[7] as List<SeriesEntity>
        val audioFiles = values[8] as List<AudioFileEntity>
        val q = values[9] as LibraryQuery
        val authorName = { id: UUID? -> id?.let { authors.firstOrNull { a -> a.id == it }?.name } ?: DisplayLabels.UNASSIGNED_AUTHOR }
        val favoriteIds = favorites.mapTo(HashSet()) { it.bookId }
        val progressById = progressList.associateBy { it.editionId }
        val byAddedIndex = books.withIndex().associate { it.value.id to it.index }
        val seriesById = series.associateBy { it.id }
        val seriesColor = { id: UUID? -> series.firstOrNull { it.id == id }?.colorTheme }
        val booksWithMissingFile = editions
            .filter { e -> audioFiles.any { it.editionId == e.id && it.fileStatus == FileStatus.MISSING } }
            .mapTo(HashSet()) { it.bookId }
        val collectionMembers = collections.associate { collection ->
            collection.name to crossRefs.filter { it.collectionId == collection.id }.mapTo(HashSet()) { it.bookId }
        }
        val audioByEdition = audioFiles.groupBy { it.editionId }
        val editionsByBook = editions.groupBy { it.bookId }

        /**
         * كتاب «مفقود بالكامل» = له ملفات (AudioFile) على الأقل، وجميعها MISSING
         * (لا ملف واحد متاح في أي إصدار). يُخفى من بطاقات المكتبة ولا يُحذف من القاعدة —
         * الفحص يعلِّم ولا يحذف، ويبقى الكتاب حاضرًا في الجداول/الإحصائيات إن استُمع إليه.
         * الكتاب بلا أي ملفات (ليس له إصدار صوتي) لا يُعتبر «مفقودًا» ويبقى ظاهرًا.
         */
        val isFullyMissing = { book: BookEntity ->
            val bookFiles = editionsByBook[book.id].orEmpty().flatMap { audioByEdition[it.id].orEmpty() }
            bookFiles.isNotEmpty() && bookFiles.none { it.fileStatus == FileStatus.AVAILABLE }
        }

        val mapped = books.mapNotNull { book ->
            val edition = effectiveEdition(book, editions, progressById)
            if (edition != null && isFullyMissing(book)) {
                null
            } else {
                val progress = edition?.let { progressById[it.id] }
                val played = progress?.currentPositionMs ?: 0L
                val total = edition?.totalDurationMs ?: 0L
                val fraction = if (total > 0L) (played.toFloat() / total).coerceIn(0f, 1f) else 0f
                LibraryBookUi(
                    book = book,
                    authorName = authorName(book.authorId),
                    seriesName = book.seriesId?.let { seriesById[it]?.name },
                    coverColor = parseColor(seriesColor(book.seriesId) ?: authors.firstOrNull { it.id == book.authorId }?.colorTheme, 0xFF2563EB),
                    effectiveEditionId = edition?.id,
                    hasMissingFile = book.id in booksWithMissingFile,
                    progressFraction = fraction,
                    remainingMs = (total - played).coerceAtLeast(0L),
                    isFavorite = book.id in favoriteIds,
                    addedOrder = byAddedIndex[book.id] ?: 0,
                    lastPlayedAt = progress?.lastPlayedAt ?: 0L
                )
            }
        }
        LibraryUiState(
            books = mapped,
            query = q,
            collections = collections,
            collectionMembers = collectionMembers,
            seriesNames = series.mapNotNull { it.name }.distinct().sorted(),
            genreNames = mapped.mapNotNull { it.book.genre?.trim()?.takeIf { g -> g.isNotEmpty() } }
                .distinct()
                .sorted()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    // المرحلة 4: تقدّم الفحص مكشوف عبر الناقل المشترك (دون تغيير البناء، فالبناة
    // تُنشأ يدويًا في الاختبارات). cancelScan يعطي إشارة توقف تعاوني للفحص.
    val scanProgress: StateFlow<ScanProgress?> = ScanProgressBus.state
    val scanActive: StateFlow<Boolean> = ScanProgressBus.active

    fun cancelScan() = ScanProgressBus.requestCancel()

    fun updateQuery(query: LibraryQuery) {
        this.query.value = query
    }

    fun updateSearch(search: String) {
        this.query.value = this.query.value.copy(search = search)
    }

    fun updateSort(sort: LibrarySort) {
        this.query.value = this.query.value.copy(sort = sort)
    }

    fun updateStatus(status: LibraryStatusFilter) {
        this.query.value = this.query.value.copy(status = status)
    }

    fun updateGenre(genre: String?) {
        this.query.value = this.query.value.copy(genre = genre)
    }

    fun updateSeries(series: String?) {
        this.query.value = this.query.value.copy(series = series)
    }

    /** إعادة تعيين كل أبعاد التصفية (يبقى نص البحث كما هو) — يُستدعى من ورقة التصفية. */
    fun resetFilters() {
        this.query.value = this.query.value.clearedFilters()
    }

    /** عدد الفلاتر النشطة حاليًا، لعرضه كشارة على زر التصفية. */
    val activeFilterCount: Int get() = query.value.activeFilterCount

    fun toggleFavorite(bookId: UUID) {
        viewModelScope.launch {
            if (favoriteBookDao.getById(bookId) != null) favoriteBookDao.delete(bookId)
            else favoriteBookDao.insert(FavoriteBook(bookId, System.currentTimeMillis()))
        }
    }

    fun createCollection(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            if (collectionDao.getByName(trimmed) == null) {
                collectionDao.insert(CollectionEntity(name = trimmed, icon = null, remoteId = null, syncStatus = com.example.audiobook.data.room.entity.SyncStatus.LOCAL_ONLY))
            }
        }
    }

    fun addBookToCollection(collectionName: String, bookId: UUID) {
        viewModelScope.launch {
            val collection = collectionDao.getByName(collectionName) ?: return@launch
            if (crossRefDao.getById(collection.id, bookId) == null) {
                crossRefDao.insert(com.example.audiobook.data.room.entity.CollectionBookCrossRef(collection.id, bookId))
            }
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

    private fun parseColor(hex: String?, default: Long): Long {
        if (hex == null) return default
        return runCatching { Color.parseColor(hex).toLong() }.getOrDefault(default)
    }

    companion object {
        /** أقصى عدد كتب في قسم «أضيفت حديثًا» (بدل الرقم السحري داخل الـUI). */
        const val RECENTLY_ADDED_LIMIT = 6
    }
}