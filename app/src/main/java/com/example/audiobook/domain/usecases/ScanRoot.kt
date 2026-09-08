package com.example.audiobook.domain.usecases

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.EmbeddedChapter
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ScanReport(
    val rootId: UUID,
    val filesSeen: Int,
    val metadataReads: Int,
    val cacheHits: Int,
    val missingMarked: Int,
    val restored: Int,
    val importedChapters: Int
)

class ScanRoot @Inject constructor(
    private val database: AppDatabase,
    private val fileSource: LibraryFileSource,
    private val metadataReader: AudioMetadataReader
) {
    suspend operator fun invoke(rootId: UUID): ScanReport = withContext(Dispatchers.IO) {
        val root = database.libraryRootDao().getById(rootId) ?: error("LibraryRoot not found: $rootId")
        database.libraryRootDao().setScanStatus(root.id, ScanStatus.SCANNING)
        try {
            val existing = database.audioFileDao().getByRoot(root.id).associateBy { it.fileUri }
            val foundUris = mutableSetOf<String>()
            val report = MutableScanReport(root.id)
            val files = fileSource.listAudioFiles(Uri.parse(root.uri))
            files.forEachIndexed { index, file ->
                val uri = file.uri.toString()
                foundUris += uri
                report.filesSeen++
                val previous = existing[uri]
                val unchanged = previous != null && previous.fileSizeBytes == file.size && previous.lastModified == file.lastModified && previous.fileUri == uri
                val edition = ensureEdition(root, file.folderPath)
                if (unchanged) {
                    database.audioFileDao().update(previous!!.copy(orderIndex = index, fileStatus = FileStatus.AVAILABLE))
                    if (previous.fileStatus == FileStatus.MISSING) report.restored++
                    report.cacheHits++
                    Log.i(TAG, "metadata-cache-hit uri=$uri")
                } else {
                    val metadata = metadataReader.read(file.uri, file.fileName)
                    val entity = AudioFileEntity(
                        id = previous?.id ?: UUID.randomUUID(), editionId = edition.id, fileUri = uri,
                        relativePath = file.relativePath, fileName = file.fileName, orderIndex = index,
                        durationMs = metadata.durationMs, fileSizeBytes = file.size, lastModified = file.lastModified,
                        contentFingerprint = "${file.size}:${file.lastModified}:$uri", mimeType = metadata.mimeType,
                        fileStatus = FileStatus.AVAILABLE
                    )
                    if (previous == null) database.audioFileDao().insert(entity) else database.audioFileDao().update(entity)
                    importChaptersIfPresent(edition.id, metadata.embeddedChapters, report)
                    report.metadataReads++
                }
            }
            existing.values.filter { it.fileUri !in foundUris }.forEach {
                if (it.fileStatus != FileStatus.MISSING) {
                    database.audioFileDao().update(it.copy(fileStatus = FileStatus.MISSING))
                    report.missingMarked++
                }
            }
            database.libraryRootDao().markScanFinished(root.id, System.currentTimeMillis(), ScanStatus.IDLE)
            Log.i(TAG, "scan-complete root=${root.id} files=${report.filesSeen} metadataReads=${report.metadataReads} cacheHits=${report.cacheHits} missing=${report.missingMarked} restored=${report.restored}")
            report.toReport()
        } catch (error: Throwable) {
            database.libraryRootDao().setScanStatus(root.id, ScanStatus.ERROR)
            throw error
        }
    }

    private suspend fun ensureEdition(root: LibraryRootEntity, folderPath: String): EditionEntity = database.withTransaction {
        database.editionDao().getByRootAndFolder(root.id, folderPath) ?: run {
            val author = database.authorDao().getByName(root.displayName) ?: AuthorEntity(name = root.displayName, colorTheme = null).also { database.authorDao().insert(it) }
            val title = folderPath.substringAfterLast('/').ifBlank { root.displayName }
            val book = database.bookDao().getByAuthorAndTitle(author.id, title) ?: BookEntity(
                title = title, authorId = author.id, seriesId = null, orderInSeries = null, genre = null,
                coverImagePath = null, coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
                defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            ).also { database.bookDao().insert(it) }
            EditionEntity(
                bookId = book.id, narratorName = null, label = "Local", totalDurationMs = 0L,
                fileFormat = folderPath.substringAfterLast('.', "").uppercase(), libraryRootId = root.id,
                sourceFolderPath = folderPath, confidenceScore = 1f, isUserConfirmed = false,
                remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
            ).also { database.editionDao().insert(it) }
        }
    }

    private suspend fun importChaptersIfPresent(editionId: UUID, chapters: List<EmbeddedChapter>, report: MutableScanReport) {
        if (chapters.isEmpty()) return
        database.chapterDao().deleteImported(editionId)
        chapters.forEachIndexed { index, chapter ->
            database.chapterDao().insert(ChapterEntity(editionId = editionId, title = chapter.title, startPositionMs = chapter.startPositionMs, orderIndex = index, createdFrom = ChapterCreatedFrom.IMPORTED))
            report.importedChapters++
        }
    }

    private class MutableScanReport(val rootId: UUID) {
        var filesSeen = 0; var metadataReads = 0; var cacheHits = 0; var missingMarked = 0; var restored = 0; var importedChapters = 0
        fun toReport() = ScanReport(rootId, filesSeen, metadataReads, cacheHits, missingMarked, restored, importedChapters)
    }

    companion object {
        private const val TAG = "ScanRoot"
    }
}