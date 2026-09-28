package com.example.audiobook.domain.usecases

import android.net.Uri
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.repository.LocalOnlyLibraryRootRepository
import com.example.audiobook.data.room.AppDatabase

/**
 * Factory helpers for tests: builds a real [LibraryManagement] from an in-memory
 * [AppDatabase] (avoids repeating its multi-DAO constructor at every call site),
 * plus no-op scan/reclassify dependencies so tests can construct [ScanRoot],
 * [ScanLibraryNow] and [ReclassifyLibrary] with an empty library.
 */
fun libraryManagementFor(database: AppDatabase): LibraryManagement = LibraryManagement(
    authorDao = database.authorDao(),
    seriesDao = database.seriesDao(),
    bookDao = database.bookDao(),
    editionDao = database.editionDao(),
    audioFileDao = database.audioFileDao(),
    chapterDao = database.chapterDao(),
    bookmarkDao = database.bookmarkDao(),
    progressDao = database.progressDao(),
    collectionDao = database.collectionDao(),
    crossRefDao = database.collectionBookCrossRefDao(),
    favoriteBookDao = database.favoriteBookDao(),
    chapterCompletionDao = database.chapterCompletionDao(),
    libraryRootDao = database.libraryRootDao(),
    scanCheckpointDao = database.scanCheckpointDao(),
    pendingDiscoveryDao = database.pendingDiscoveryDao(),
    onboardingEditDao = database.onboardingEditDao()
)

/** File source that reports an empty library (used when a test never scans). */
class EmptyFileSource : LibraryFileSource {
    override fun listAudioFiles(rootUri: Uri): List<ScanFile> = emptyList()
}

/** Metadata reader that always returns a minimal metadata (used as a no-op). */
class NoopMetadataReader : AudioMetadataReader {
    override fun read(uri: Uri, fileName: String): AudioMetadata =
        AudioMetadata(0L, "audio/mpeg", null, null, null, emptyList())
}

/** ScanRoot with an empty file source (safe when the test never triggers a scan). */
fun scanRootFor(database: AppDatabase, appSettings: AppSettings): ScanRoot =
    ScanRoot(database, EmptyFileSource(), NoopMetadataReader(), appSettings, EditionMerge(database))

/** ScanLibraryNow backed by an empty root repository (safe when never invoked). */
fun scanLibraryNowFor(database: AppDatabase, appSettings: AppSettings): ScanLibraryNow =
    ScanLibraryNow(
        LocalOnlyLibraryRootRepository(database.libraryRootDao()),
        scanRootFor(database, appSettings),
        database
    )

/** LibraryClassificationPreview backed by an empty source + repository (safe when never invoked). */
fun classificationPreviewFor(database: AppDatabase, appSettings: AppSettings): LibraryClassificationPreview =
    LibraryClassificationPreview(
        EmptyFileSource(),
        LocalOnlyLibraryRootRepository(database.libraryRootDao()),
        appSettings
    )

/** RebuildLibraryStructure over an in-memory database and an empty library (safe when never invoked). */
fun rebuildStructureFor(database: AppDatabase, appSettings: AppSettings): RebuildLibraryStructure =
    RebuildLibraryStructure(database, scanLibraryNowFor(database, appSettings))