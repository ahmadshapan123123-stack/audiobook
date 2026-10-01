package com.example.audiobook.presentation.player

import androidx.compose.ui.graphics.Color
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlayerEditionUiState(
    val edition: EditionEntity? = null,
    val book: BookEntity? = null,
    val title: String = "",
    val authorName: String = "",
    val seriesName: String = "",
    val seriesColor: Color? = null,
    val authorColor: Color? = null,
    val coverColor: Color? = null
)

private fun parseColor(hex: String?): Color? {
    if (hex == null) return null
    return try { Color(android.graphics.Color.parseColor(hex)) } catch (e: IllegalArgumentException) { null }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val editionDao: EditionDao,
    private val bookDao: BookDao,
    private val authorDao: AuthorDao,
    private val seriesDao: SeriesDao
) : ViewModel() {
    private val editionId: UUID = UUID.fromString(
        savedStateHandle.get<String>("editionId") ?: throw IllegalArgumentException("editionId navigation argument missing")
    )

    /**
     * FIX 2 (Phase 7): تشخيص "غلاف الكتاب" — يُسجَّل مرة واحدة لكل نسخة
     * يصل فيها edition موجود بلا كتاب مطابق (وسيط تنقل قديم بعد إعادة
     * فحص ولّدت صفوف نسخ جديدة، أو صف كتاب محذوف).
     */
    @Volatile private var lastNullBookLoggedFor: UUID? = null

    private val editionFlow = editionDao.observeById(editionId)

    private val bookFlow = editionFlow.let { ef ->
        combine(ef, bookDao.observeAll()) { edition, books ->
            edition?.let { books.firstOrNull { b -> b.id == it.bookId } }
        }
    }

    val uiState: StateFlow<PlayerEditionUiState> = combine(
        editionFlow,
        bookFlow,
        authorDao.observeAll(),
        seriesDao.observeAll()
    ) { edition, book, authors, series ->
        if (edition != null && book == null && lastNullBookLoggedFor != edition.id) {
            lastNullBookLoggedFor = edition.id
            Log.w(TAG, "book null for editionId=${edition.id} bookId=${edition.bookId} — stale nav arg or missing book row; header falls back to placeholder")
        } else if (book != null) {
            lastNullBookLoggedFor = null
        }
        val author = book?.let { authors.firstOrNull { a -> a.id == it.authorId } }
        val series = book?.seriesId?.let { sid -> series.firstOrNull { it.id == sid } }
        val seriesColor = series?.colorTheme
        // FIX 2 (REAL / Phase 7): احتياطي النسخة — عند غياب الكتاب (وسيط
        // قديم/صف محذوف) يُعرض عنوان النسخة (label ثم اسم المجلد) والراوي،
        // فلا يظهر "غلاف الكتاب" إلا عند غياب الاثنين معًا (title فارغ).
        // لا زر إعجاب ولا تنقل مؤلف في شاشة المشغّل أصلًا — لا شيء يُعطَّل.
        val editionFallbackTitle = edition?.label?.takeIf { it.isNotBlank() }
            ?: edition?.sourceFolderPath?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        PlayerEditionUiState(
            edition = edition,
            book = book,
            title = book?.title?.takeIf { it.isNotBlank() } ?: editionFallbackTitle ?: "",
            authorName = author?.name ?: if (book == null) edition?.narratorName.orEmpty() else "",
            seriesName = series?.name ?: "",
            seriesColor = parseColor(seriesColor),
            authorColor = parseColor(author?.colorTheme),
            coverColor = book?.let { deterministicColor(it.id) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlayerEditionUiState())

    private fun deterministicColor(id: UUID): Color {
        val hue = (id.hashCode() and 0xFF) / 255f
        return Color.hsv(hue * 360f, 0.45f, 0.55f)
    }

    private companion object {
        const val TAG = "PlayerVM"
    }
}
