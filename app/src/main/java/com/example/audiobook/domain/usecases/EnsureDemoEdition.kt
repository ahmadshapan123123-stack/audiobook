package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject

/**
 * Development-only seed that populates an empty database with a single demo book
 * and edition so the app has data to render during local debugging.
 *
 * This is NOT part of the real user path: it is invoked only under `BuildConfig.DEBUG`
 * in MainActivity. In release builds it is never called, and scanned library data
 * comes exclusively from the user's actual LibraryRoots via the Scanner.
 */
class EnsureDemoEdition @Inject constructor(private val database: AppDatabase) {
    suspend operator fun invoke() {
        val rootId = stable("demo-root")
        val authorId = stable("demo-author")
        val bookId = stable("demo-book")
        val editionId = stable("demo-edition")
        if (database.libraryRootDao().getById(rootId) == null) database.libraryRootDao().insert(LibraryRootEntity(rootId, "demo://library", "Demo Library", true, true, null, ScanStatus.IDLE))
        if (database.authorDao().getById(authorId) == null) database.authorDao().insert(AuthorEntity(authorId, "أحمد خالد توفيق", null))
        if (database.bookDao().getById(bookId) == null) database.bookDao().insert(BookEntity(id = bookId, title = "ما وراء الطبيعة", authorId = authorId, seriesId = null, orderInSeries = null, genre = "خيال", coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false, isTitleUserConfirmed = false, defaultEditionId = editionId, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
        if (database.editionDao().getById(editionId) == null) database.editionDao().insert(EditionEntity(id = editionId, bookId = bookId, narratorName = "محمد خضير", label = "الإصدار التجريبي", totalDurationMs = 3_600_000, fileFormat = "M4B", libraryRootId = rootId, sourceFolderPath = "/demo", confidenceScore = 1f, isUserConfirmed = true, isNarratorUserConfirmed = false, isLabelUserConfirmed = false, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
    }

    companion object { fun stable(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray()) }
}