package com.example.audiobook.presentation.bookmarks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.dao.BookmarkDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.entity.BookmarkEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarksUiState(
    val bookmarks: List<BookmarkEntity> = emptyList(),
    val chapters: List<ChapterEntity> = emptyList()
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookmarkDao: BookmarkDao,
    private val chapterDao: ChapterDao
) : ViewModel() {
    private val editionId: UUID = UUID.fromString(
        savedStateHandle.get<String>("editionId") ?: throw IllegalArgumentException("editionId navigation argument missing")
    )

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarkDao.observeByParent(editionId),
        chapterDao.observeByParent(editionId)
    ) { bookmarks, chapters ->
        BookmarksUiState(bookmarks = bookmarks, chapters = chapters)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    fun addNote(positionMs: Long, note: String) {
        viewModelScope.launch {
            bookmarkDao.insert(
                BookmarkEntity(
                    editionId = editionId,
                    positionMs = positionMs,
                    createdAt = System.currentTimeMillis(),
                    type = com.example.audiobook.data.room.entity.BookmarkType.NOTE,
                    noteText = note,
                    remoteId = null,
                    syncStatus = SyncStatus.LOCAL_ONLY
                )
            )
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch { bookmarkDao.delete(bookmark) }
    }
}
