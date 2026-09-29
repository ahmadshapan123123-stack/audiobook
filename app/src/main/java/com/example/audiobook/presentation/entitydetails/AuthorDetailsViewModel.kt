package com.example.audiobook.presentation.entitydetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.R
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.ProgressDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.presentation.common.OpMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AuthorBookGroup(
    val seriesId: UUID?,
    val seriesName: String?,
    val seriesColorTheme: String?,
    val books: List<EntityBookRow>
)

/**
 * FIX 5.1 — بطاقة سلسلة للمؤلف: تُشتق من جدول السلاسل مباشرة
 * (authorId == X) لا من روابط الكتب — فسلسلة بلا كتب مربوطة تظهر
 * أيضًا (بعدّ صفر) بدل أن تختفي تمامًا كما كان.
 */
data class AuthorSeriesCard(
    val seriesId: UUID,
    val seriesName: String,
    val bookCount: Int
)

data class AuthorDetailsUiState(
    val author: AuthorEntity? = null,
    val groups: List<AuthorBookGroup> = emptyList(),
    val seriesCards: List<AuthorSeriesCard> = emptyList(),
    val totalBooks: Int = 0,
    /** REDESIGN: عدد الكتب قيد الاستماع + آخر كتاب مستمع + أول نسخة قابلة للتشغيل. */
    val inProgressCount: Int = 0,
    val continueRow: EntityBookRow? = null,
    val firstEditionId: UUID? = null,
    val candidateBooks: List<EntityBookRow> = emptyList(),
    val coverColor: Long = 0xFF356B68,
    val allAuthors: List<AuthorEntity> = emptyList()
)

@HiltViewModel
class AuthorDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao,
    private val bookDao: BookDao,
    private val editionDao: EditionDao,
    private val progressDao: ProgressDao,
    private val management: LibraryManagement
) : ViewModel() {

    private val authorId: UUID = UUID.fromString(
        savedStateHandle.get<String>("id") ?: throw IllegalArgumentException("author id navigation argument missing")
    )

    /** أثر قابل للتراجع ضمن نافذة السناكبار (5 ثوانٍ): حذف أو دمج. */
    private sealed interface AuthorUndo {
        data class Deleted(val snapshot: LibraryManagement.AuthorSnapshot) : AuthorUndo
        data class Merged(val snapshot: LibraryManagement.AuthorMergeSnapshot) : AuthorUndo
    }

    private val pendingUndo = MutableStateFlow<AuthorUndo?>(null)
    private val _messages = MutableStateFlow<OpMessage?>(null)
    val messages: StateFlow<OpMessage?> = _messages

    fun consumeMessage() {
        _messages.value = null
    }

    val uiState: StateFlow<AuthorDetailsUiState> = combine(
        authorDao.observeAll(),
        seriesDao.observeAll(),
        bookDao.observeAll(),
        editionDao.observeAll(),
        progressDao.observeAll()
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val authors = values[0] as List<AuthorEntity>
        val allSeries = values[1] as List<SeriesEntity>
        val books = values[2] as List<BookEntity>
        val editions = values[3] as List<EditionEntity>
        val progressList = values[4] as List<ListeningProgressEntity>

        val author = authors.firstOrNull { it.id == authorId }
        val rows = buildEntityBookRows(
            books = books.filter { it.authorId == authorId },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        )
        val candidateRows = buildEntityBookRows(
            books = books.filter { it.authorId != authorId },
            authors = authors,
            editions = editions,
            progressList = progressList,
            series = allSeries
        ).sortedBy { it.title }

        fun sortRows(list: List<EntityBookRow>): List<EntityBookRow> =
            list.sortedWith(compareBy<EntityBookRow> { it.orderInSeries ?: Int.MAX_VALUE }.thenBy { it.title })

        val bySeries = rows.filter { it.seriesId != null }.groupBy { it.seriesId }
        val groups = bySeries.entries
            .map { (sid, list) ->
                AuthorBookGroup(
                    seriesId = sid,
                    seriesName = allSeries.firstOrNull { it.id == sid }?.name,
                    seriesColorTheme = allSeries.firstOrNull { it.id == sid }?.colorTheme,
                    books = sortRows(list)
                )
            }
            .sortedBy { it.seriesName }
        val standalone = rows.filter { it.seriesId == null }
        val allGroups = if (standalone.isEmpty()) groups else groups + AuthorBookGroup(null, null, null, sortRows(standalone))
        // FIX 5.1: بطاقات السلاسل من جدول السلاسل (authorId == author) —
        // لا تعتمد على روابط الكتب، فتظهر السلسلة حتى لو فقدت كتبها الرابط.
        val seriesCards = allSeries
            .filter { it.authorId == authorId }
            .sortedBy { it.name }
            .map { series ->
                AuthorSeriesCard(
                    seriesId = series.id,
                    seriesName = series.name,
                    bookCount = rows.count { it.seriesId == series.id }
                )
            }

        AuthorDetailsUiState(
            author = author,
            groups = allGroups,
            seriesCards = seriesCards,
            totalBooks = rows.size,
            inProgressCount = rows.count { it.hasProgress },
            continueRow = rows.filter { it.hasProgress }.maxByOrNull { it.progressFraction },
            firstEditionId = rows.firstNotNullOfOrNull { it.editionId },
            candidateBooks = candidateRows,
            coverColor = parseColor(author?.colorTheme, 0xFF356B68),
            allAuthors = authors.filter { it.id != authorId }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthorDetailsUiState())

    fun saveAuthor(name: String, description: String?, imagePath: String?) {
        viewModelScope.launch {
            authorDao.getById(authorId)?.let { current ->
                authorDao.update(current.copy(name = name, description = description, imagePath = imagePath))
            }
        }
    }

    /** إنشاء سلسلة جديدة ضمن المؤلف الحالي (تظهر فورًا كمجموعة في الصفحة). */
    fun createSeries(name: String) {
        viewModelScope.launch {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return@launch
            seriesDao.insert(
                SeriesEntity(
                    id = UUID.randomUUID(),
                    authorId = authorId,
                    name = trimmed,
                    colorTheme = null
                )
            )
        }
    }

    fun addBookToAuthor(bookId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == authorId) return@launch
            management.moveBookToAuthor(bookId, authorId)
        }
    }

    fun moveBookToOtherAuthor(bookId: UUID, targetId: UUID) {
        viewModelScope.launch {
            val book = bookDao.getById(bookId) ?: return@launch
            if (book.authorId == targetId) return@launch
            management.moveBookToAuthor(bookId, targetId)
        }
    }

    fun deleteAuthor() {
        viewModelScope.launch {
            val snapshot = management.snapshotAuthor(authorId)
            management.deleteAuthor(authorId)
            pendingUndo.value = AuthorUndo.Deleted(snapshot)
            _messages.value = OpMessage(R.string.author_deleted_undo)
        }
    }

    fun mergeAuthors(targetId: UUID) {
        viewModelScope.launch {
            if (targetId == authorId) return@launch
            val snapshot = management.mergeAuthors(authorId, targetId)
            pendingUndo.value = AuthorUndo.Merged(snapshot)
            _messages.value = OpMessage(R.string.author_merged_undo)
        }
    }

    fun undo() {
        val action = pendingUndo.value ?: return
        viewModelScope.launch {
            when (action) {
                is AuthorUndo.Deleted -> management.restoreAuthor(action.snapshot)
                is AuthorUndo.Merged -> management.undoMergeAuthors(action.snapshot)
            }
            pendingUndo.value = null
            _messages.value = null
        }
    }
}
