package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject

class EnsureDemoEdition @Inject constructor(private val database: AppDatabase) {
    suspend operator fun invoke() {
        val rootId = stable("demo-root")
        val authorId = stable("demo-author")
        val bookId = stable("demo-book")
        val editionId = stable("demo-edition")
        if (database.libraryRootDao().getById(rootId) == null) database.libraryRootDao().insert(LibraryRootEntity(rootId, "demo://library", "Demo Library", true, true, null, ScanStatus.IDLE))
        if (database.authorDao().getById(authorId) == null) database.authorDao().insert(AuthorEntity(authorId, "أحمد خالد توفيق", null))
        if (database.bookDao().getById(bookId) == null) database.bookDao().insert(BookEntity(bookId, "ما وراء الطبيعة", authorId, null, null, "خيال", null, CoverSource.PLACEHOLDER, false, editionId, null, SyncStatus.LOCAL_ONLY))
        if (database.editionDao().getById(editionId) == null) database.editionDao().insert(EditionEntity(editionId, bookId, "محمد خضير", "الإصدار التجريبي", 3_600_000, "M4B", rootId, "/demo", 1f, true, null, SyncStatus.LOCAL_ONLY))
    }

    companion object { fun stable(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray()) }
}