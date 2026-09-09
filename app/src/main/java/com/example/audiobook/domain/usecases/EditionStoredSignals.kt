package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity

/**
 * إعادة بناء إشارات الإصدار من الصفوف المخزنة (بلا إعادة قراءة metadata).
 * تستخدم عند اتخاذ قرار لاحق (دمج من شاشة Review/إدارة الإصدارات) ولقطة القرارات.
 */
object EditionStoredSignals {
    fun fromEdition(
        edition: EditionEntity,
        root: LibraryRootEntity?,
        bookTitle: String?,
        fileCount: Int
    ): EditionSignals {
        val folderName = edition.sourceFolderPath.substringAfterLast('/').ifBlank { edition.sourceFolderPath }
        val authorFolderName = when {
            edition.sourceFolderPath.contains('/') -> edition.sourceFolderPath.substringBefore('/')
            else -> root?.displayName
        }
        val narrator = edition.narratorName ?: EditionSignalExtractor.extractNarratorFromName(folderName)
        val embeddedTitle = bookTitle?.takeIf { it.isNotBlank() }?.let { t ->
            EmbeddedTags(t, edition.narratorName, null)
        }
        return EditionSignals(
            primaryFileName = folderName,
            folderName = folderName,
            authorFolderName = authorFolderName?.takeIf { it.isNotBlank() },
            seriesPart = EditionSignalExtractor.extractSeriesPart(folderName),
            embeddedTags = embeddedTitle,
            totalDurationMs = edition.totalDurationMs,
            fileCount = fileCount,
            narrator = narrator,
            format = edition.fileFormat
        )
    }
}