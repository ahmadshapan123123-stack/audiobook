package com.example.audiobook.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.CollectionBookCrossRefDao
import com.example.audiobook.data.room.dao.CollectionDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.FavoriteBookDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.CollectionBookCrossRef
import com.example.audiobook.data.room.entity.CollectionEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.FavoriteBook
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** بطاقة كتاب حقيقية من بيانات المكتبة، جاهزة للعرض في أي قسم بالرئيسية. */
data class HomeBook(
    val bookId: UUID,
    val seriesId: UUID?,
    val editionId: UUID?,
    val title: String,
    val authorName: String,
    val seriesName: String?,
    val coverColor: Long,
    /** FIX-C4: مسار الغلاف للتمرير إلى AtherCoverBlock. */
    val coverImagePath: String? = null,
    val genre: String?,
    val progressFraction: Float,
    val remainingMs: Long,
    val hasProgress: Boolean,
    val addedOrder: Int,
    val lastPlayedAt: Long,
    /** PART 1: ترتيب الكتاب في سلسلته (لقاعدة "التالي في السلسلة"). */
    val orderInSeries: Int? = null
)

/** بطاقة "أكمل استماعك" المميّزة. */
data class HomeContinue(
    val book: HomeBook,
    val totalMs: Long,
    val playedMs: Long,
    val currentChapterTitle: String?
)

/** سلسلة حقيقية من المكتبة مع كتبها لعرض أغلفة حقيقية داخلها. */
data class HomeSeries(
    val seriesId: UUID,
    val name: String,
    val authorName: String,
    val colorTheme: String?,
    val books: List<HomeBook>
)

/** مؤلف حقيقي من المكتبة مع كتبه. */
data class HomeAuthor(
    val authorId: UUID,
    val name: String,
    val colorTheme: String?,
    val books: List<HomeBook>
)

/** مجموعة (رف) حقيقية من المكتبة مع كتبها الحقيقية. */
data class HomeCollection(
    val collectionId: UUID,
    val name: String,
    val books: List<HomeBook>
)

data class HomeUiState(
    val continueListening: HomeContinue? = null,
    /** PART 3: حتى 3 كتب قيد الاستماع (الأحدث أولًا) — بطاقة مميزة للواحد، صف للاثنين فأكثر. */
    val continueList: List<HomeContinue> = emptyList(),
    val nextUp: List<HomeBook> = emptyList(),
    val recentlyListened: List<HomeBook> = emptyList(),
    val series: List<HomeSeries> = emptyList(),
    val authors: List<HomeAuthor> = emptyList(),
    val collections: List<HomeCollection> = emptyList(),
    val favorites: List<HomeBook> = emptyList(),
    val totalBooks: Int = 0,
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val seriesDao: SeriesDao,
    private val collectionDao: CollectionDao,
    private val crossRefDao: CollectionBookCrossRefDao,
    private val chapterDao: ChapterDao,
    private val favoriteBookDao: FavoriteBookDao
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        bookDao.observeAll(),
        authorDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll(),
        seriesDao.observeAll(),
        collectionDao.observeAll(),
        crossRefDao.observeAll(),
        chapterDao.observeAll(),
        favoriteBookDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val books = values[0] as List<BookEntity>
        val authors = values[1] as List<AuthorEntity>
        val editions = values[2] as List<EditionEntity>
        val progressList = values[3] as List<ListeningProgressEntity>
        val series = values[4] as List<SeriesEntity>
        val collections = values[5] as List<CollectionEntity>
        val crossRefs = values[6] as List<CollectionBookCrossRef>
        val chapters = values[7] as List<ChapterEntity>
        val favorites = values[8] as List<FavoriteBook>

        val authorById = authors.associateBy { it.id }
        val homeBooks = HomeMapper.toHomeBooks(books, authors, editions, progressList, series)
        val homeById = homeBooks.associateBy { it.bookId }

        val continuing = progressList
            .filter { it.status == ProgressStatus.IN_PROGRESS }
            .maxByOrNull { it.lastPlayedAt }

        val continueUnit = continuing?.let { progress ->
            val edition = editions.firstOrNull { it.id == progress.editionId }
            val book = edition?.let { homeById[it.bookId] } ?: return@let null
            val chapterTitle = HomeMapper.chapterTitleAt(chapters, edition?.id, progress.currentPositionMs)
            HomeContinue(
                book = book,
                totalMs = edition?.totalDurationMs ?: 0L,
                playedMs = progress.currentPositionMs,
                currentChapterTitle = chapterTitle
            )
        }

        // PART 3: حتى 3 كتب IN_PROGRESS (الأحدث أولًا) لقسم المتابعة الموسّع.
        // FIX-HOME-CRASH: عنصر واحد لكل كتاب — نسختان قيد الاستماع لكتاب واحد
        // (بعد الدمج) كانتا تنتجان مفتاحين متطابقين في LazyRow فيتعطل التطبيق.
        // الترتيب تنازلي مسبقًا فيُبقي distinctBy الأحدث.
        val continueList = progressList
            .filter { it.status == ProgressStatus.IN_PROGRESS }
            .sortedByDescending { it.lastPlayedAt }
            .mapNotNull { progress ->
                val edition = editions.firstOrNull { it.id == progress.editionId } ?: return@mapNotNull null
                val book = homeById[edition.bookId] ?: return@mapNotNull null
                HomeContinue(
                    book = book,
                    totalMs = edition.totalDurationMs,
                    playedMs = progress.currentPositionMs,
                    currentChapterTitle = HomeMapper.chapterTitleAt(chapters, edition.id, progress.currentPositionMs)
                )
            }
            .distinctBy { it.book.bookId }
            .take(3)

        val recentlyListened = homeBooks
            .filter { it.hasProgress && it.editionId != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(8)

        // PART 1 — "ماذا بعد؟" مبسّطة لقاعدتين متوقعتين بدل 5 استدلالات:
        // RULE 1: الكتاب التالي في سلسلة آخر كتاب مستمع (by orderInSeries).
        // RULE 2: إكمال حتى 5 بأحدث الكتب غير المستمعة (addedOrder تنازلي).
        // RULE 3: لا شيء منهما = إخفاء القسم.
        val nextUp = mutableListOf<HomeBook>()
        val lastPlayed = continueUnit?.book
        val lastPlayedOrder = lastPlayed?.let { lp -> books.firstOrNull { it.id == lp.bookId }?.orderInSeries }
        if (lastPlayed?.seriesId != null && lastPlayedOrder != null) {
            homeBooks.firstOrNull { candidate ->
                candidate.seriesId == lastPlayed.seriesId &&
                    candidate.bookId != lastPlayed.bookId &&
                    candidate.editionId != null &&
                    books.firstOrNull { it.id == candidate.bookId }?.orderInSeries == lastPlayedOrder + 1
            }?.let { nextUp += it }
        }
        homeBooks
            .filter { it.editionId != null && !it.hasProgress && it.bookId !in nextUp.map { b -> b.bookId } }
            .sortedByDescending { it.addedOrder }
            .take(5 - nextUp.size)
            .forEach { nextUp += it }

        // PART 2: سقف 6 صفوف لكل قسم أفقي — كان بلا حد فينفجر مع المكتبات الكبيرة.
        val seriesSection = series
            .map { s ->
                HomeSeries(
                    seriesId = s.id,
                    name = s.name,
                    authorName = authorById[s.authorId]?.name ?: "",
                    colorTheme = s.colorTheme,
                    books = homeBooks.filter { it.seriesId == s.id }
                )
            }
            .filter { it.books.isNotEmpty() }
            .take(6)

        val authorsSection = authors
            .map { a ->
                HomeAuthor(
                    authorId = a.id,
                    name = a.name,
                    colorTheme = a.colorTheme,
                    books = homeBooks.filter { b ->
                        books.firstOrNull { it.id == b.bookId }?.authorId == a.id
                    }
                )
            }
            .filter { it.books.isNotEmpty() }
            .take(6)

        val collectionsSection = collections
            .map { collection ->
                val ids = crossRefs
                    .filter { it.collectionId == collection.id }
                    .mapTo(HashSet()) { it.bookId }
                HomeCollection(
                    collectionId = collection.id,
                    name = collection.name,
                    books = homeBooks.filter { it.bookId in ids }
                )
            }
            .filter { it.books.isNotEmpty() }
            .take(6)

        val favoritesSection = favorites
            .sortedByDescending { it.addedAt }
            .mapNotNull { favorite -> homeById[favorite.bookId] }
            .take(6)

        HomeUiState(
            continueListening = continueUnit,
            continueList = continueList,
            nextUp = nextUp,
            recentlyListened = recentlyListened,
            series = seriesSection,
            authors = authorsSection,
            collections = collectionsSection,
            favorites = favoritesSection,
            totalBooks = books.size,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}