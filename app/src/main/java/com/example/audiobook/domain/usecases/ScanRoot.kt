package com.example.audiobook.domain.usecases

import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader

import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
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
    val importedChapters: Int,
    val editionsCreated: Int = 0,
    val editionsRefined: Int = 0,
    val editionsAutoMerged: Int = 0,
    val filesDeduped: Int = 0
)

/**
 * الفحص الحقيقي (R2): يستخرج الإشارات العشر فعليًا لكل مجلد، يحسب
 * Confidence Score حقيقيًا، يتخذ قرارات الدمج التلقائي تحت المستوى المختار
 * مع القيد الصارم، يسجل قرارات EditionMatchDecision، ويحترم User Override Wins.
 */
class ScanRoot @Inject constructor(
    private val database: AppDatabase,
    private val fileSource: LibraryFileSource,
    private val metadataReader: AudioMetadataReader,
    private val appSettings: AppSettings,
    private val editionMerge: EditionMerge
) {
    suspend operator fun invoke(rootId: UUID): ScanReport = withContext(Dispatchers.IO) {
        val root = database.libraryRootDao().getById(rootId) ?: error("LibraryRoot not found: $rootId")
        database.libraryRootDao().setScanStatus(root.id, ScanStatus.SCANNING)
        try {
            val existing = database.audioFileDao().getByRoot(root.id).associateBy { it.fileUri }
            val foundUris = mutableSetOf<String>()
            val report = MutableScanReport(root.id)
            val files = fileSource.listAudioFiles(Uri.parse(root.uri))

            val contexts = buildContextByPath(files, root.displayName)
            val prepared = prepareFiles(root.id, files, existing, foundUris, report)
            val signalsByFolder = resolveEditions(root, prepared, contexts, report)
            markMissingFiles(root.id, existing, foundUris, report)
            reconcileAutoMerges(root.id, signalsByFolder, report)

            database.libraryRootDao().markScanFinished(root.id, System.currentTimeMillis(), ScanStatus.IDLE)
            Log.i(TAG, "scan-complete root=${root.id} files=${report.filesSeen} metadataReads=${report.metadataReads} cacheHits=${report.cacheHits} missing=${report.missingMarked} restored=${report.restored} created=${report.editionsCreated} refined=${report.editionsRefined} autoMerged=${report.editionsAutoMerged} deduped=${report.filesDeduped}")
            report.toReport()
        } catch (error: Throwable) {
            database.libraryRootDao().setScanStatus(root.id, ScanStatus.ERROR)
            throw error
        }
    }

    private data class PreparedFolder(val folderPath: String, val files: List<PreparedFile>, val hasFreshRead: Boolean)

    private data class PreparedFile(
        val scanFile: ScanFile,
        val previous: AudioFileEntity?,
        val freshMetadata: AudioMetadata?,
        val durationMs: Long,
        val orderIndex: Int
    )

    /** pass 1: تجميع الملفات حسب المجلد، وقراءة metadata فقط للملفات المتغيرة/الجديدة (Metadata Cache). */
    private fun prepareFiles(
        rootId: UUID,
        files: List<ScanFile>,
        existing: Map<String, AudioFileEntity>,
        foundUris: MutableSet<String>,
        report: MutableScanReport
    ): Map<String, PreparedFolder> {
        val byFolder = LinkedHashMap<String, MutableList<PreparedFile>>()
        files.forEachIndexed { index, file ->
            val uri = file.uri.toString()
            foundUris += uri
            report.filesSeen++
            val previous = existing[uri]
            val unchanged = previous != null &&
                previous.fileSizeBytes == file.size &&
                previous.lastModified == file.lastModified
            val metadata = if (unchanged) null else metadataReader.read(file.uri, file.fileName).also { report.metadataReads++ }
            if (unchanged) report.cacheHits++
            val bucket = byFolder.getOrPut(file.folderPath, ::mutableListOf)
            val isDuplicate = bucket.any { it.scanFile.fileName == file.fileName && it.scanFile.size == file.size }
            if (isDuplicate) {
                report.filesDeduped++
            } else {
                bucket.add(
                    PreparedFile(file, previous, metadata, metadata?.durationMs ?: previous?.durationMs ?: 0L, index)
                )
            }
        }
        return byFolder.mapValues { (folderPath, files) ->
            PreparedFolder(folderPath, files, hasFreshRead = files.any { it.freshMetadata != null })
        }
    }

    /**
     * pass 1b: خريطة سياق (المؤلف/السلسلة) لكل مجلد يحوي ملفات — من تصنيف
     * FolderClassifier (المجلدات التي تحوي صوتًا تظهر كلها في flattenBookNodes)،
     * مع احتياطي مسار مباشر لأي مجلد غير متوقع (بما في ذلك الملفات المبعثرة
     * في جذر المصدر حيث folderPath = "").
     */
    private fun buildContextByPath(files: List<ScanFile>, fallbackAuthor: String): Map<String, AuthorSeriesContext> {
        val contexts = LinkedHashMap<String, AuthorSeriesContext>()
        FolderClassifier.flattenBookNodes(FolderClassifier.classify(files)).forEach { node ->
            contexts[node.path] = node.context(fallbackAuthor)
        }
        files.forEach { file ->
            contexts.getOrPut(file.folderPath) { pathContext(file.folderPath, fallbackAuthor) }
        }
        return contexts
    }

    /** احتياطي: اشتقاق المؤلف/السلسلة من مسار المجلد مباشرة (قاعدة العمق نفسها). */
    private fun pathContext(path: String, fallbackAuthor: String): AuthorSeriesContext =
        FolderClassifier.contextForPath(path, fallbackAuthor)

    /** pass 2: بناء الإشارات العشر لكل مجلد ثم حل الإصدار وكتابة الملفات. */
    private suspend fun resolveEditions(root: LibraryRootEntity, byFolder: Map<String, PreparedFolder>, contexts: Map<String, AuthorSeriesContext>, report: MutableScanReport): Map<String, EditionSignals> {
        val result = LinkedHashMap<String, EditionSignals>()
        byFolder.forEach { (folderPath, folder) ->
            val context = contexts[folderPath] ?: pathContext(folderPath, root.displayName)
            val signals = signalsFor(root, folder, context)
            val edition = resolveEdition(root, folderPath, signals, context,
                onCreated = { report.editionsCreated++ },
                onRefined = { report.editionsRefined++ }
            )
            val importedChapters = mutableListOf<ChapterEntity>()
            var runningOffsetMs = 0L
            folder.files.forEach { file ->
                val uri = file.scanFile.uri.toString()
                if (file.previous == null && database.audioFileDao()
                        .getByEditionNameSize(edition.id, file.scanFile.fileName, file.scanFile.size) != null
                ) {
                    report.filesDeduped++
                    return@forEach
                }
                val entity = AudioFileEntity(
                    id = file.previous?.id ?: UUID.randomUUID(),
                    editionId = edition.id,
                    fileUri = uri,
                    relativePath = file.scanFile.relativePath,
                    fileName = file.scanFile.fileName,
                    orderIndex = file.orderIndex,
                    durationMs = file.durationMs,
                    fileSizeBytes = file.scanFile.size,
                    lastModified = file.scanFile.lastModified,
                    contentFingerprint = "${file.scanFile.size}:${file.scanFile.lastModified}:$uri",
                    mimeType = file.freshMetadata?.mimeType ?: file.previous?.mimeType ?: "application/octet-stream",
                    fileStatus = FileStatus.AVAILABLE
                )
                if (file.previous == null) {
                    database.audioFileDao().insert(entity)
                    file.freshMetadata?.embeddedChapters?.forEachIndexed { index, chapter ->
                        importedChapters += ChapterEntity(
                            editionId = edition.id,
                            title = chapter.title,
                            startPositionMs = runningOffsetMs + chapter.startPositionMs,
                            orderIndex = index,
                            createdFrom = ChapterCreatedFrom.IMPORTED
                        )
                    }
                } else {
                    if (file.previous.fileStatus == FileStatus.MISSING) report.restored++
                    database.audioFileDao().update(entity)
                }
                runningOffsetMs += file.durationMs
            }
            if (importedChapters.isNotEmpty()) {
                database.chapterDao().deleteImported(edition.id)
                importedChapters.forEach { database.chapterDao().insert(it) }
                report.importedChapters += importedChapters.size
            }
            result[folderPath] = signals
        }
        return result
    }

    private fun signalsFor(root: LibraryRootEntity, folder: PreparedFolder, context: AuthorSeriesContext): EditionSignals {
        val allMetadata = folder.files.map { file ->
            file.freshMetadata ?: AudioMetadata(file.durationMs, file.previous?.mimeType ?: "", null, null, null, emptyList())
        }
        return EditionSignalExtractor.build(
            folderName = folder.folderPath,
            authorFolderName = context.authorName,
            seriesFolderName = context.seriesFolderName,
            fileNames = folder.files.map { it.scanFile.fileName },
            metadataList = allMetadata
        )
    }

    /**
     * إنشاء الإصدار عند غيابه أو تحديث الإصدار الموجود — مع قاعدة User Override Wins:
     * أي حقل مُعلَّم من المستخدم (عنوان/راوٍ/تسمية) يُمنع الكتابة فوقه؛ ولا يُحدَّث
     * الإصدار بعد أن "حسمه" المستخدم حتى لو بدا الفحص وكأنه يعرف أفضل.
     */
    private suspend fun resolveEdition(
        root: LibraryRootEntity,
        folderPath: String,
        signals: EditionSignals,
        context: AuthorSeriesContext,
        onCreated: () -> Unit,
        onRefined: () -> Unit
    ): EditionEntity {
        val existingEdition = database.editionDao().getByRootAndFolder(root.id, folderPath)
            ?: return createEdition(root, folderPath, signals, context).also { onCreated() }
        val book = database.bookDao().getById(existingEdition.bookId) ?: return existingEdition

        if (!book.isTitleUserConfirmed) {
            val detected = signals.resolvedTitle()?.takeIf { it.isNotBlank() } ?: book.title
            if (detected != book.title) database.bookDao().update(book.copy(title = detected))
        }

        if (existingEdition.isUserConfirmed) {
            return existingEdition
        }

        val refreshed = existingEdition.copy(
            narratorName = if (existingEdition.isNarratorUserConfirmed) existingEdition.narratorName else signals.narrator,
            label = if (existingEdition.isLabelUserConfirmed) existingEdition.label else signals.folderName.substringAfterLast('/').ifBlank { existingEdition.label },
            totalDurationMs = if (signals.hasKnownDuration()) signals.totalDurationMs else existingEdition.totalDurationMs,
            fileFormat = signals.format ?: existingEdition.fileFormat,
            confidenceScore = EditionIntelligence.calculateConfidence(signals)
        )
        if (refreshed != existingEdition && signals.haveMoreInfoThan(existingEdition)) {
            database.editionDao().update(refreshed)
            onRefined()
        }
        return refreshed
    }

    /**
     * إنشاء إصدار جديد لمجلد جديد — قاعدة "أول ظهور يُثبّت البنية":
     *  - المؤلف = مجلد المؤلف من التصنيف (signals.authorFolderName)، لا اسم جذر المكتبة.
     *  - كتاب جديد لكل مجلد (لا إعادة استخدام عبر getByAuthorAndTitle — تمنع دمج سلسلتين
     *    لهما اسم كتاب مضمّن واحد، مثل "01" الشائعة).
     *  - سلسلة = مجلد السلسلة الحاوي يُبحث أو يُنشأ تحت المؤلف، ويُثبت seriesId +
     *    orderInSeries وقت الإنشاء فقط؛ أي إعادة فحص لاحقة لا تكتب رأيًا جديدًا فوق
     *    التعديلات اليدوية.
     */
    private suspend fun createEdition(root: LibraryRootEntity, folderPath: String, signals: EditionSignals, context: AuthorSeriesContext): EditionEntity = database.withTransaction {
        database.editionDao().getByRootAndFolder(root.id, folderPath) ?: run {
            val authorName = context.authorName.takeIf { it.isNotBlank() } ?: root.displayName
            val author = database.authorDao().getByName(authorName)
                ?: AuthorEntity(name = authorName, colorTheme = null).also { database.authorDao().insert(it) }
            val title = signals.resolvedTitle()?.takeIf { it.isNotBlank() } ?: root.displayName
            val seriesFolder = context.seriesFolderName?.takeIf { it.isNotBlank() }
            val seriesId: UUID?
            val orderInSeries: Int?
            if (seriesFolder != null) {
                val series = database.seriesDao().getByParent(author.id)
                    .firstOrNull { it.name == seriesFolder }
                    ?: SeriesEntity(authorId = author.id, name = seriesFolder, colorTheme = null)
                        .also { database.seriesDao().insert(it) }
                seriesId = series.id
                orderInSeries = signals.seriesPart?.partNumber
            } else {
                seriesId = null
                orderInSeries = null
            }
            val book = BookEntity(
                title = title,
                authorId = author.id,
                seriesId = seriesId,
                orderInSeries = orderInSeries,
                genre = signals.embeddedTags?.genre,
                coverImagePath = null,
                coverSource = CoverSource.PLACEHOLDER,
                isCoverUserSelected = false,
                defaultEditionId = null,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            ).also { database.bookDao().insert(it) }
            EditionEntity(
                bookId = book.id,
                narratorName = signals.narrator,
                label = signals.folderName.substringAfterLast('/').ifBlank { folderPath },
                totalDurationMs = signals.totalDurationMs,
                fileFormat = signals.format ?: "UNKNOWN",
                libraryRootId = root.id,
                sourceFolderPath = folderPath,
                confidenceScore = EditionIntelligence.calculateConfidence(signals),
                isUserConfirmed = false,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            ).also { database.editionDao().insert(it) }
        }
    }

    /** دمج تلقائي (Balanced فقط، وباجتياز القيد الصارم) بين إصدارات مجلدات لنفس الكتاب. */
    private suspend fun reconcileAutoMerges(rootId: UUID, signalsByFolder: Map<String, EditionSignals>, report: MutableScanReport) {
        if (signalsByFolder.size < 2) return
        val level = appSettings.currentIntelligenceLevel()
        val groups = signalsByFolder.entries.groupBy { (_, signals) ->
            "${ArabicSearchNormalizer.normalize(signals.authorFolderName.orEmpty())}|" +
                "${ArabicSearchNormalizer.normalize(signals.seriesFolderName.orEmpty())}|" +
                signals.normalizedTitle()
        }
        groups.forEach { (_, entries) ->
            if (entries.size < 2) return@forEach
            val editions = entries.mapNotNull { (folder, _) -> database.editionDao().getByRootAndFolder(rootId, folder) }
            for (i in editions.indices) {
                for (j in i + 1 until editions.size) {
                    val subject = editions[i]
                    val candidate = editions[j]
                    if (database.editionDao().getById(subject.id) == null) continue
                    if (database.editionDao().getById(candidate.id) == null) continue
                    if (subject.isUserConfirmed || candidate.isUserConfirmed) continue
                    if (hasUserDecidedAgainst(subject.id, candidate.id)) continue
                    val subjectSignals = signalsByFolder[subject.sourceFolderPath]
                    val candidateSignals = signalsByFolder[candidate.sourceFolderPath]
                    if (subjectSignals == null || candidateSignals == null) continue
                    val boost = EditionIntelligence.confirmationBoost(subjectSignals, candidateSignals, priorConfirmations(subject.id, candidate.id))
                    if (EditionIntelligence.mergeDecision(subjectSignals, candidateSignals, level, boost)) {
                        editionMerge.merge(subject.id, candidate.id, userInitiated = false)
                        report.editionsAutoMerged++
                    }
                }
            }
        }
    }

    private suspend fun priorConfirmations(subjectId: UUID, candidateId: UUID): List<PatternConfirmation> {
        val decisions = database.editionMatchDecisionDao().getByParent(subjectId) +
            database.editionMatchDecisionDao().getByParent(candidateId)
        return decisions.mapNotNull { decision ->
            EditionSignalsCodec.parsePairPatternKey(decision.signalsSnapshot)?.let {
                PatternConfirmation(it, decision.userDecision)
            }
        }
    }

    private suspend fun hasUserDecidedAgainst(subjectId: UUID, candidateId: UUID): Boolean {
        val decisions = database.editionMatchDecisionDao().getByParent(subjectId) +
            database.editionMatchDecisionDao().getByParent(candidateId)
        return decisions.any { decision ->
            val pairMatches = (decision.subjectEditionId == subjectId && decision.comparedAgainstEditionId == candidateId) ||
                (decision.subjectEditionId == candidateId && decision.comparedAgainstEditionId == subjectId)
            pairMatches && decision.userDecision != UserDecision.SAME_EDITION
        }
    }

    private suspend fun markMissingFiles(rootId: UUID, existing: Map<String, AudioFileEntity>, foundUris: Set<String>, report: MutableScanReport) {
        existing.values.filter { it.fileUri !in foundUris }.forEach {
            if (it.fileStatus != FileStatus.MISSING) {
                database.audioFileDao().update(it.copy(fileStatus = FileStatus.MISSING))
                report.missingMarked++
            }
        }
    }

    private class MutableScanReport(val rootId: UUID) {
        var filesSeen = 0
        var metadataReads = 0
        var cacheHits = 0
        var missingMarked = 0
        var restored = 0
        var importedChapters = 0
        var editionsCreated = 0
        var editionsRefined = 0
        var editionsAutoMerged = 0
        var filesDeduped = 0
        fun toReport() = ScanReport(rootId, filesSeen, metadataReads, cacheHits, missingMarked, restored, importedChapters, editionsCreated, editionsRefined, editionsAutoMerged, filesDeduped)
    }

    companion object {
        private const val TAG = "ScanRoot"
    }
}

/** التحقق من أن الإشارات الجديدة "أغنى معلومة" من المخزن قبل أي تحديث (يمنع تجريد الثقة بلا داعٍ). */
private fun EditionSignals.haveMoreInfoThan(edition: EditionEntity): Boolean {
    val currentConfidence = edition.confidenceScore
    val newConfidence = EditionIntelligence.calculateConfidence(this)
    return if (this.embeddedTags?.title != null) true else newConfidence >= currentConfidence
}