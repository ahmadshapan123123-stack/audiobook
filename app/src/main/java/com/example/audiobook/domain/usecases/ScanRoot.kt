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
import com.example.audiobook.domain.config.StrictModeFlags
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
 * نشرة تقدّم متدفّقة (المرحلة 4): تُنشر في نقاط الخطرية من الفحص على كلٍّ من
 * مستمع onProgress الخاص بالمتصل وللناقل المشترك ScanProgressBus للواجهة.
 */
data class ScanProgress(
    val phase: ScanPhase,
    val processed: Int,
    val total: Int,
    val currentFolder: String
)

/** أطوار الفحص بترتيبها: اكتشاف الملفات ← قراءة الوسائط ← التصنيف ← إنشاء الكتب ← تم. */
enum class ScanPhase { DISCOVERING, PARSING, CLASSIFYING, CREATING, DONE }

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
    suspend operator fun invoke(
        rootId: UUID,
        onProgress: (ScanProgress) -> Unit = {}
    ): ScanReport = withContext(Dispatchers.IO) {
        val root = database.libraryRootDao().getById(rootId) ?: error("LibraryRoot not found: $rootId")
        database.libraryRootDao().setScanStatus(root.id, ScanStatus.SCANNING)
        val checkpointDao = database.scanCheckpointDao()
        ScanProgressBus.begin()
        var cancelled = false
        try {
            val existing = database.audioFileDao().getByRoot(root.id).associateBy { it.fileUri }
            val foundUris = mutableSetOf<String>()
            val report = MutableScanReport(root.id)
            // الفحص الأول = لا ملف مُتتبَّع لهذا الجذر بعد (لا كتب) — فيُتخطى markMissingFiles
            // كليًا، حتى لا يعلّم فحص أول/جزئي عن قائمة ناقصة شيئًا موجودًا على أنه مفقود.
            val isFirstScan = existing.isEmpty()

            val files = fileSource.listAudioFiles(Uri.parse(root.uri))
            publish(ScanProgress(ScanPhase.DISCOVERING, files.size, files.size, root.displayName), onProgress)

            // Phase 2: التصنيف الصارم يُحسب مرة واحدة هنا فيغذّي عدّ النشرة وربط
            // الوحدات بالملفات لاحقًا — لا يُصنَّف مرة ثانية ولا يُطوى في خريطة سياق.
            val classified = if (StrictModeFlags.USE_STRICT_CLASSIFIER) classifyFiles(files, root.displayName) else null
            val classifiedCount = classified?.size ?: buildContextByPath(files, root.displayName).size
            publish(ScanProgress(ScanPhase.CLASSIFYING, classifiedCount, files.size, root.displayName), onProgress)

            // قراءة metadata تدفقية — يُنشر كل 100 ملف — مع إعادة استخدام كاش الفحص السابق
            // (نفس uri + نفس lastModified) بلا قراءة فعلية.
            val prepared = prepareFiles(root.id, files, existing, foundUris, report) { parsed, total ->
                publish(ScanProgress(ScanPhase.PARSING, parsed, total, ""), onProgress)
            }

            val resumeIndex = resumeIndexFor(root.id, checkpointDao, prepared, System.currentTimeMillis())
            // كل ClassifiedBook كتاب مستقل بهويته البنيوية (author, series, title)؛
            // المسار القديم (إصدار لكل مجلد) باقٍ للمسار غير الصارم فقط.
            val signalsByFolder = if (classified != null) {
                resolveClassifiedBooks(root, bookUnits(classified, prepared, root.displayName), report, resumeIndex) { done, total, folderPath ->
                    publish(ScanProgress(ScanPhase.CREATING, done, total, folderPath), onProgress)
                }
            } else {
                val contexts = buildContextByPath(files, root.displayName)
                resolveEditions(root, prepared, contexts, report, resumeIndex) { done, total, folderPath ->
                    publish(ScanProgress(ScanPhase.CREATING, done, total, folderPath), onProgress)
                }
            }

            cancelled = ScanProgressBus.isCancelRequested
            if (!cancelled && !isFirstScan) {
                markMissingFiles(root.id, existing, foundUris, report)
            }
            if (!cancelled && StrictModeFlags.ENABLE_AUTO_MERGE) {
                reconcileAutoMerges(root.id, signalsByFolder, report)
            }
            // عند الإنجاز التام يُقطع checkpoint؛ أما عند الإيقاف فيبقى لاستئناف الفحص التالي.
            if (!cancelled) checkpointDao.deleteForRoot(root.id)

            ScanProgressBus.finish()
            database.libraryRootDao().markScanFinished(root.id, System.currentTimeMillis(), ScanStatus.IDLE)
            publish(ScanProgress(ScanPhase.DONE, report.filesSeen, report.filesSeen, ""), onProgress)
            Log.i(TAG, "scan-complete root=${root.id} files=${report.filesSeen} metadataReads=${report.metadataReads} cacheHits=${report.cacheHits} missing=${report.missingMarked} restored=${report.restored} created=${report.editionsCreated} refined=${report.editionsRefined} autoMerged=${report.editionsAutoMerged} deduped=${report.filesDeduped} cancelled=$cancelled")
            report.toReport()
        } catch (error: Throwable) {
            // أي فشل في دفعة/مرحلة: تبقى الدفعات السابقة (معاملات منفصلة) ويبقى
            // checkpoint (لم يُحذف) فيستأنف الفحص التالي من حيث توقف.
            ScanProgressBus.finish()
            database.libraryRootDao().setScanStatus(root.id, ScanStatus.ERROR)
            throw error
        }
    }

    // الأنواع التالية `internal` لا `private`: اختبار الأداء في نفس الوحدة يقيس
    // المرحلتين (prepareFiles / resolveClassifiedBooks) على حدة. `internal` لا
    // يُتاح خارج هذه الوحدة، فليست واجهة عامة.
    internal data class PreparedFolder(val folderPath: String, val files: List<PreparedFile>, val hasFreshRead: Boolean)

    internal data class PreparedFile(
        val scanFile: ScanFile,
        val previous: AudioFileEntity?,
        val freshMetadata: AudioMetadata?,
        val durationMs: Long,
        val orderIndex: Int
    )

    /**
     * Phase 2: وحدة الحفظ = **كتاب** من [StrictFolderClassifier] لا مجلد. المجلد
     * الواحد قد يُنتج عدة كتب (ملفات مباشرة تحت مؤلف، ملفات على الجذر، وعمق-2 صار
     * سلسلة وكتابًا)، وكتب المجلد الواحد كانت تُطوى كلها في إصدار واحد.
     * `files` هي حصّة الكتاب من ملفات المجلد (بمطابقة uri) لا كل ملفات المجلد.
     */
    internal data class BookUnit(
        val classified: StrictFolderClassifier.ClassifiedBook,
        val folderPath: String,
        val files: List<PreparedFile>
    )

    /** هوية كتاب محسومة داخل القاعدة: (authorId, seriesId, title) — لا مسار. */
    private data class StructuralIdentity(
        val authorId: UUID?,
        val seriesId: UUID?,
        val title: String,
        val authorName: String?,
        val seriesName: String?
    )

    /** pass 1: تجميع الملفات حسب المجلد، وقراءة metadata فقط للملفات المتغيرة/الجديدة (Metadata Cache). */
    internal fun prepareFiles(
        rootId: UUID,
        files: List<ScanFile>,
        existing: Map<String, AudioFileEntity>,
        foundUris: MutableSet<String>,
        report: MutableScanReport,
        onParsed: (parsed: Int, total: Int) -> Unit = { _, _ -> }
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
            if ((index + 1) % PROGRESS_INTERVAL == 0) onParsed(index + 1, files.size)
        }
        if (files.isNotEmpty()) onParsed(files.size, files.size)
        return byFolder.mapValues { (folderPath, files) ->
            PreparedFolder(folderPath, files, hasFreshRead = files.any { it.freshMetadata != null })
        }
    }

    /** تصنيف صارم واحد لمجلدات الجذر — نفس مدخلات [buildContextByPath]. */
    private fun classifyFiles(files: List<ScanFile>, rootName: String): List<StrictFolderClassifier.ClassifiedBook> =
        StrictFolderClassifier.classify(
            files.map {
                StrictFolderClassifier.InputFile(
                    uri = it.uri.toString(),
                    filename = it.fileName,
                    folderPath = it.folderPath,
                    sizeBytes = it.size,
                    durationMs = 0L
                )
            },
            rootName
        )

    /**
     * Phase 2: ربط كل [StrictFolderClassifier.ClassifiedBook] بحصّته من الملفات
     * المحضَّرة عبر مطابقة `uri` (لا عبر المجلد) — فمجلد واحد قد يحمل كتبًا
     * متعدّدة، وملف الجذر كتاب مستقل بمفرده.
     *
     * حاويات بلا ملفات مباشرة تنتج ClassifiedBook بـ `files` فارغة ولا تُحفظ
     * (كما كان المجلد الفارغ لا يُنشئ إصدارًا) — وكتب عمق-3 تحمل كتب أبنائها.
     * شبكة أمان: أي ملف لم ينسبه المصنِّف يُضاف كتابًا احتياطيًا باسمه بدل أن
     * يضيع (وإلا لعلّمه الفحص التالي مفقودًا).
     */
    internal fun bookUnits(
        classified: List<StrictFolderClassifier.ClassifiedBook>,
        prepared: Map<String, PreparedFolder>,
        rootName: String
    ): List<BookUnit> {
        val byUri = HashMap<String, PreparedFile>()
        prepared.values.forEach { folder -> folder.files.forEach { byUri[it.scanFile.uri.toString()] = it } }
        val claimed = HashSet<String>()
        val units = classified.mapNotNullTo(mutableListOf()) { book ->
            val owned = book.files.mapNotNull { file -> byUri[file.uri]?.also { claimed += file.uri } }
            if (owned.isEmpty()) null else BookUnit(book, book.folderPath, owned)
        }
        byUri.values.filter { it.scanFile.uri.toString() !in claimed }
            .groupBy { it.scanFile.folderPath }
            .forEach { (folderPath, orphans) ->
                units += BookUnit(
                    classified = StrictFolderClassifier.ClassifiedBook(
                        authorName = null,
                        seriesName = null,
                        bookTitle = orphans.minByOrNull { it.orderIndex }?.scanFile?.fileName?.substringBeforeLast('.').orEmpty()
                            .ifBlank { rootName },
                        folderPath = folderPath,
                        files = orphans.map { prepared ->
                            StrictFolderClassifier.InputFile(
                                uri = prepared.scanFile.uri.toString(),
                                filename = prepared.scanFile.fileName,
                                folderPath = folderPath,
                                sizeBytes = prepared.scanFile.size,
                                durationMs = 0L
                            )
                        }
                    ),
                    folderPath = folderPath,
                    files = orphans
                )
            }
        return units
    }

    /**
     * Phase 2 pass 2: حفظ كتب [BookUnit] بدل المجلدات — بنفس دلالات المعاملات
     * والدفعات وcheckpoint (المفتاح يبقى مسار المجلد الأول في الدفعة).
     */
    internal suspend fun resolveClassifiedBooks(
        root: LibraryRootEntity,
        units: List<BookUnit>,
        report: MutableScanReport,
        resumeIndex: Int,
        onCreated: (done: Int, total: Int, folderPath: String) -> Unit
    ): Map<String, EditionSignals> {
        val result = LinkedHashMap<String, EditionSignals>()
        val unitsByFolder = units.groupBy { it.folderPath }
        val orderedFolders = unitsByFolder.keys.toList()
        val totalFolders = orderedFolders.size
        val checkpointDao = database.scanCheckpointDao()
        var done = 0

        orderedFolders.drop(resumeIndex).chunked(BATCH_SIZE).forEach { batch ->
            // توقف تعاوني: ننهي ونُبقي checkpoint بلا رمي.
            if (ScanProgressBus.isCancelRequested) return result
            checkpointDao.upsert(ScanCheckpointEntity(root.id, batch.first(), System.currentTimeMillis()))
            database.withTransaction {
                batch.forEach { folderPath ->
                    unitsByFolder[folderPath].orEmpty().forEach { unit ->
                        val signals = resolveUnit(root, unit, report,
                            onCreated = { report.editionsCreated++ },
                            onRefined = { report.editionsRefined++ }
                        ) ?: return@forEach
                        // خريطة الإشارات للدمج التلقائي تبقى بمفتاح المجلد (أول كتاب فيه)
                        // — ودمج السيارات معطّل (ENABLE_AUTO_MERGE=false).
                        result.putIfAbsent(folderPath, signals)
                        done++
                        onCreated(done, totalFolders, folderPath)
                    }
                }
            }
        }
        return result
    }

    /**
     * Phase 2: إنشاء/تحديث إصدار كتاب واحد بهويته البنيوية
     * (libraryRootId, authorId, seriesId, title) — لا بمفتاح المجلد — ثم إرفاق
     * ملفاته. «تم تجاهله» يبقى بمفتاح المجلد كما كان.
     */
    private suspend fun resolveUnit(
        root: LibraryRootEntity,
        unit: BookUnit,
        report: MutableScanReport,
        onCreated: () -> Unit,
        onRefined: () -> Unit
    ): EditionSignals? {
        val book = unit.classified
        // مجلد «تم تجاهله» مسبقًا: لا كتاب ولا اكتشاف جديد.
        if (database.pendingDiscoveryDao().isIgnored(root.id, unit.folderPath)) return null

        val authorName = book.authorName?.takeIf { it.isNotBlank() }
        val author = authorName?.let { name ->
            database.authorDao().getByName(name)
                ?: AuthorEntity(name = name, colorTheme = null).also { database.authorDao().insert(it) }
        }
        val seriesName = book.seriesName?.takeIf { it.isNotBlank() }
        val series = if (author != null && seriesName != null) {
            database.seriesDao().getByParent(author.id).firstOrNull { it.name == seriesName }
                ?: SeriesEntity(authorId = author.id, name = seriesName, colorTheme = null)
                    .also { database.seriesDao().insert(it) }
        } else null
        val title = book.bookTitle.takeIf { it.isNotBlank() } ?: return null
        val identity = StructuralIdentity(author?.id, series?.id, title, authorName, seriesName)

        val folder = PreparedFolder(unit.folderPath, unit.files, unit.files.any { it.freshMetadata != null })
        val context = AuthorSeriesContext(book.authorName, book.seriesName)
        val signals = signalsFor(root, folder, context, signalFolderName(unit, book))

        val existing = database.editionDao().getByStructuralKey(root.id, identity.authorId, identity.seriesId, identity.title)
        val target = existing ?: createStructuralEdition(root, unit, identity, signals).also { onCreated() }

        // قاعدة User Override Wins نفسها المطبَّقة في resolveEdition: العنوان
        // المكتشف من الوسائط يُكتب على الكتاب ما لم يكن المستخدم قد أكّده. العنوان
        // البنيوي (اسم المجلد/الكتاب من المصنِّف) يبقى في عمود bookTitle للإصدار
        // وهوية البحث — فلا يتغيّر مفتاح الهوية لمجرّد تغيّر العنوان المعروض.
        val bookRow = database.bookDao().getById(target.bookId)
        if (bookRow != null && !bookRow.isTitleUserConfirmed) {
            val detected = signals.resolvedTitle()?.takeIf { it.isNotBlank() } ?: bookRow.title
            if (detected != bookRow.title) database.bookDao().update(bookRow.copy(title = detected))
        }
        if (target.isUserConfirmed) return signals

        val refreshed = target.copy(
            narratorName = if (target.isNarratorUserConfirmed) target.narratorName else signals.narrator,
            label = if (target.isLabelUserConfirmed) target.label else signals.folderName.substringAfterLast('/').ifBlank { target.label },
            totalDurationMs = if (signals.hasKnownDuration()) signals.totalDurationMs else target.totalDurationMs,
            fileFormat = signals.format ?: target.fileFormat,
            confidenceScore = EditionIntelligence.calculateConfidence(signals)
        )
        if (refreshed != target && signals.haveMoreInfoThan(target)) {
            database.editionDao().update(refreshed)
            onRefined()
        }

        attachFiles(root, unit, refreshed, report)
        return signals
    }

    /** إرفاق ملفات الكتاب بوحدته — نفس منطق الملفات القديم، لكن بحصة الكتاب لا المجلد. */
    private suspend fun attachFiles(
        root: LibraryRootEntity,
        unit: BookUnit,
        edition: EditionEntity,
        report: MutableScanReport
    ) {
        val importedChapters = mutableListOf<ChapterEntity>()
        var runningOffsetMs = 0L
        unit.files.forEach { file ->
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
    }

    /**
     * إنشاء إصدار الكتاب بهويته البنيوية. يُكتب مفتاح الهوية على صفّ الإصدار نفسه
     * (authorId/seriesId/bookTitle) ليبقى قابلًا للفهرسة والاستعلام بلا join.
     */
    private suspend fun createStructuralEdition(
        root: LibraryRootEntity,
        unit: BookUnit,
        identity: StructuralIdentity,
        signals: EditionSignals
    ): EditionEntity {
        val bookRow = BookEntity(
            title = identity.title,
            authorId = identity.authorId,
            seriesId = identity.seriesId,
            orderInSeries = if (identity.seriesId != null) signals.seriesPart?.partNumber else null,
            genre = signals.embeddedTags?.genre,
            coverImagePath = null,
            coverSource = CoverSource.PLACEHOLDER,
            isCoverUserSelected = false,
            defaultEditionId = null,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY
        ).also { database.bookDao().insert(it) }
        val edition = EditionEntity(
            bookId = bookRow.id,
            narratorName = signals.narrator,
            label = signals.folderName.substringAfterLast('/').ifBlank { unit.folderPath },
            totalDurationMs = signals.totalDurationMs,
            fileFormat = signals.format ?: "UNKNOWN",
            libraryRootId = root.id,
            sourceFolderPath = unit.folderPath,
            confidenceScore = EditionIntelligence.calculateConfidence(signals),
            isUserConfirmed = false,
            remoteId = null,
            syncStatus = SyncStatus.LOCAL_ONLY,
            authorId = identity.authorId,
            seriesId = identity.seriesId,
            bookTitle = identity.title
        ).also { database.editionDao().insert(it) }
        // تسجيل الاكتشاف بالتوازي مع الاستيراد التلقائي. المفتاح الفريد
        // (rootId, folderPath) مع IGNORE = سجل واحد لكل مجلد مهما أنتج كتبًا.
        database.pendingDiscoveryDao().insert(
            PendingDiscoveryEntity(
                rootId = root.id,
                folderPath = unit.folderPath,
                detectedTitle = identity.title,
                authorName = identity.authorName.orEmpty(),
                seriesName = identity.seriesName,
                discoveredAt = System.currentTimeMillis(),
                status = DiscoveryStatus.PENDING
            )
        )
        return edition
    }

    /**
     * pass 1b: خريطة سياق (المؤلف/السلسلة) لكل مجلد يحوي ملفات — من تصنيف
     * FolderClassifier (contextsByPath: كل مجلد ذي ملفات يظهر كعقدة كتاب عادية
     * أو اصطناعية داخل حاوية/سلسلة أو كتاب مجموعة SPLIT)، مع احتياطي مسار مباشر
     * لأي مجلد غير متوقع (بما في ذلك الملفات المبعثرة في جذر المصدر folderPath="").
     * تُنقل قيمة إعداد «تصنيف تلقائي للسلاسل» الحالي إلى المصنِّف.
     */
    private fun buildContextByPath(files: List<ScanFile>, fallbackAuthor: String): Map<String, AuthorSeriesContext> {
        val autoSeries = appSettings.currentAutoSeriesClassification()
        val contexts = LinkedHashMap<String, AuthorSeriesContext>()
        if (StrictModeFlags.USE_STRICT_CLASSIFIER) {
            // التصنيف الصارم (المرحلة 3): سياقات بالعمق فقط — الجذر حاوية، العمق 1 مؤلف،
            // العمق 2 سلسلة دائمًا، العمق ≥ 3 كتب تكراريًا. يحافظ على المسار القديم سليمًا دونه.
            StrictFolderClassifier.classify(
                files.map {
                    StrictFolderClassifier.InputFile(
                        uri = it.uri.toString(),
                        filename = it.fileName,
                        folderPath = it.folderPath,
                        sizeBytes = it.size,
                        durationMs = 0L
                    )
                },
                fallbackAuthor
            ).forEach { contexts[it.folderPath] = AuthorSeriesContext(it.authorName, it.seriesName) }
        } else {
            contexts.putAll(FolderClassifier.contextsByPath(FolderClassifier.classify(files, autoSeries), fallbackAuthor))
        }
        files.forEach { file ->
            contexts.getOrPut(file.folderPath) { pathContext(file.folderPath, fallbackAuthor, autoSeries) }
        }
        return contexts
    }

    /** احتياطي: اشتقاق المؤلف/السلسلة من مسار المجلد مباشرة (قاعدة العمق نفسها). */
    private fun pathContext(path: String, fallbackAuthor: String, autoSeries: Boolean = appSettings.currentAutoSeriesClassification()): AuthorSeriesContext =
        if (path.isEmpty()) AuthorSeriesContext(null, null, isRoot = true)
        else FolderClassifier.contextForPath(path, fallbackAuthor, autoSeries)

    /**
     * pass 2 (تدفقي — المرحلة 4): بناء الإشارات ثم حلّ الإصدار وكتابة الملفات.
     * تعمل بمجاميع من 50 مجلدًا لكل withTransaction: أي فشل في دفعة يترك الدفعات
     * السابقة متمسكة (معلَّمة تباعًا). وقبل كل دفعة يُحدث checkpoint (أول مجلد فيها)
     * فإذا أُوقف الفحص استأنف التالي من تلك الدفعة تحديدًا. يُنشر CREATING بعد كل
     * كتاب، ويتوقف تعاونيًا بلطف عند طلب الإلغاء (يُترك checkpoint قائمًا).
     */
    private suspend fun resolveEditions(
        root: LibraryRootEntity,
        byFolder: Map<String, PreparedFolder>,
        contexts: Map<String, AuthorSeriesContext>,
        report: MutableScanReport,
        resumeIndex: Int,
        onCreated: (done: Int, total: Int, folderPath: String) -> Unit
    ): Map<String, EditionSignals> {
        val result = LinkedHashMap<String, EditionSignals>()
        val orderedFolders = byFolder.entries.toList()
        val totalFolders = orderedFolders.size
        val checkpointDao = database.scanCheckpointDao()
        var done = 0

        orderedFolders.drop(resumeIndex).chunked(BATCH_SIZE).forEach { batch ->
            // توقف تعاوني (زر إلغاء/إيقاف): نُنهي ولا نرمي استثناء؛ checkpoint يبقى للاستئناف.
            if (ScanProgressBus.isCancelRequested) return result
            checkpointDao.upsert(ScanCheckpointEntity(root.id, batch.first().key, System.currentTimeMillis()))
            database.withTransaction {
                batch.forEach { (folderPath, folder) ->
                    val context = contexts[folderPath] ?: pathContext(folderPath, root.displayName)
                    val signals = signalsFor(root, folder, context)
                    val edition = resolveEdition(root, folderPath, signals, context,
                        onCreated = { report.editionsCreated++ },
                        onRefined = { report.editionsRefined++ }
                    ) ?: return@forEach
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
                    done++
                    onCreated(done, totalFolders, folderPath)
                }
            }
        }
        return result
    }

    private fun signalsFor(
        root: LibraryRootEntity,
        folder: PreparedFolder,
        context: AuthorSeriesContext,
        signalFolderName: String = folder.folderPath
    ): EditionSignals {
        val allMetadata = folder.files.map { file ->
            file.freshMetadata ?: AudioMetadata(file.durationMs, file.previous?.mimeType ?: "", null, null, null, emptyList())
        }
        return EditionSignalExtractor.build(
            folderName = signalFolderName,
            authorFolderName = context.authorName,
            seriesFolderName = context.seriesFolderName,
            fileNames = folder.files.map { it.scanFile.fileName },
            metadataList = allMetadata
        )
    }

    /**
     * اسم المجلد المستعمل في الإشارات — ليس بالضرورة مسار المجلد الحقيقي.
     *
     * الكتاب المصنَّف من ملف واحد (عمق 0 أو 1 أو 2: كتاب لكل ملف) عنوانُه اسمُ
     * الملف بلا امتداد، فمرورُ مسار المجلد وحده إلى [EditionSignals] كان يجعل
     * `resolvedTitle()` يُرجع اسم المجلد (اسم المؤلف عند العمق-1، واسم السلسلة
     * عند العمق-2) فيكتبه فوق عنوان كل كتاب من كتب ذلك المجلد. لذلك يُستخدم هنا
     * مسار الملف بلا امتداد، فيطابق العنوانَ البنيويَّ الذي قرّره المصنِّف.
     *
     * أما الكتب التي عنوانها اسم مجلدها (عمق ≥ 3) أو ذات ملفات متعددة فتبقى
     * على مسار المجلد كما هو.
     */
    private fun signalFolderName(unit: BookUnit, book: StrictFolderClassifier.ClassifiedBook): String {
        val single = unit.files.singleOrNull() ?: return unit.folderPath
        val fileName = single.scanFile.fileName
        val stem = fileName.substringBeforeLast('.', fileName)
        if (stem.isBlank() || book.bookTitle != stem) return unit.folderPath
        return if (unit.folderPath.isEmpty()) stem else "${unit.folderPath}/$stem"
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
    ): EditionEntity? {
        val existingEdition = database.editionDao().getByRootAndFolder(root.id, folderPath)
        if (existingEdition == null && database.pendingDiscoveryDao().isIgnored(root.id, folderPath)) {
            // مجلد «تم تجاهله» مسبقًا: لا ننشئ له كتابًا ولا نسجّل اكتشافًا جديدًا.
            return null
        }
        val target = existingEdition
            ?: createEdition(root, folderPath, signals, context).also { onCreated() }
        val book = database.bookDao().getById(target.bookId) ?: return target

        if (!book.isTitleUserConfirmed) {
            val detected = signals.resolvedTitle()?.takeIf { it.isNotBlank() } ?: book.title
            if (detected != book.title) database.bookDao().update(book.copy(title = detected))
        }

        if (target.isUserConfirmed) {
            return target
        }

        val refreshed = target.copy(
            narratorName = if (target.isNarratorUserConfirmed) target.narratorName else signals.narrator,
            label = if (target.isLabelUserConfirmed) target.label else signals.folderName.substringAfterLast('/').ifBlank { target.label },
            totalDurationMs = if (signals.hasKnownDuration()) signals.totalDurationMs else target.totalDurationMs,
            fileFormat = signals.format ?: target.fileFormat,
            confidenceScore = EditionIntelligence.calculateConfidence(signals)
        )
        if (refreshed != target && signals.haveMoreInfoThan(target)) {
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
     *
     *  ملاحظة مقصودة (لا تغيير): هوية الكتاب في الفحص هي «مسار المجلد» (libraryRootId +
     *  sourceFolderPath) وليست المؤلف+العنوان. لذلك إعادة تسمية مجلد = مسار جديد = إصدار
     *  وكتاب جديدان دائمًا؛ أما الصف القديم فتبقى ملفاته دون فحص فتتحول إلى MISSING (الفحص
     *  يعلِّم ولا يحذف)، ويختفي من بطاقات المكتبة عبر فلتر «مفقود بالكامل» في الشاشة.
     *  هذا سلوك مقصود ولا يوجد تتبّع لهوية المجلد عبر إعادة التسمية.
     */
    private suspend fun createEdition(root: LibraryRootEntity, folderPath: String, signals: EditionSignals, context: AuthorSeriesContext): EditionEntity = database.withTransaction {
        database.editionDao().getByRootAndFolder(root.id, folderPath) ?: run {
            // المؤلف فارغ = كتاب مستقل غير مُصنَّف (authorId=null) — لا يُنسب اسم الجذر كمؤلف أبدًا.
            val authorName = context.authorName?.takeIf { it.isNotBlank() }
            val author = authorName?.let { name ->
                database.authorDao().getByName(name)
                    ?: AuthorEntity(name = name, colorTheme = null).also { database.authorDao().insert(it) }
            }
            val embeddedTitle = if (StrictModeFlags.ENABLE_EMBEDDED_TITLE_OVERRIDE) signals.resolvedTitle() else null
            val title = (embeddedTitle ?: signals.structuralTitle())
                .takeIf { it.isNotBlank() }
                ?: signals.primaryFileName.takeIf { it.isNotBlank() }
                ?: root.displayName
            val autoSeries = appSettings.currentAutoSeriesClassification()
            // السلسلة من مجلد الحاوية؛ وإن لم يحسم المجلد سلسلة، يُؤخذ تلميح الألبوم
            // من الـMetadata (ميزة اختيارية تُدار بعلم ENABLE_METADATA_HINTS) — فقط عند
            // الإنشاء، ولا يتجاوز قرار المجلد ولا قرار المستخدم ولا يعمل ضمن التصنيف المحافظ.
            val seriesFolder = context.seriesFolderName?.takeIf { it.isNotBlank() }
                ?: if (StrictModeFlags.ENABLE_METADATA_HINTS && autoSeries) signals.seriesAlbumHint?.takeIf { it.isNotBlank() } else null
            val seriesId: UUID?
            val orderInSeries: Int?
            if (author != null && seriesFolder != null) {
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
                authorId = author?.id,
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
            val edition = EditionEntity(
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
            // Part 4: تسجيل الاكتشاف بالتوازي مع الاستيراد التلقائي — قائمة انتظار
            // مفاتَشة في بوب-أب (الجذور ذات الأولوية) وفي الإعدادات، دون تغيير سلوك الفحص.
            // IGNORE يمتص إعادة الفحص (المفتاح الفريد (rootId, folderPath)).
            database.pendingDiscoveryDao().insert(
                PendingDiscoveryEntity(
                    rootId = root.id,
                    folderPath = folderPath,
                    detectedTitle = title,
                    authorName = authorName.orEmpty(),
                    seriesName = seriesFolder,
                    discoveredAt = System.currentTimeMillis(),
                    status = DiscoveryStatus.PENDING
                )
            )
            edition
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
                    if (StrictModeFlags.ENABLE_AUTO_MERGE &&
                        EditionIntelligence.mergeDecision(subjectSignals, candidateSignals, level, boost)
                    ) {
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

    /** ينشر نشرة تقدّم على الطرفين: مستمع المتصل الفوري + الناقل المشترك للواجهة. */
    private fun publish(progress: ScanProgress, onProgress: (ScanProgress) -> Unit) {
        onProgress(progress)
        ScanProgressBus.publish(progress)
    }

    /**
     * تحديد نقطة الاستئناف: checkpoint حديث (< 24 ساعة) يعيد الفحص من مجلده؛
     * ومنقضي/مفقود المجلد يُحذف ويبدأ فحص كامل من أول الدفعات.
     */
    private suspend fun resumeIndexFor(
        rootId: UUID,
        checkpointDao: com.example.audiobook.data.room.dao.ScanCheckpointDao,
        prepared: Map<String, PreparedFolder>,
        now: Long
    ): Int {
        val checkpoint = checkpointDao.getForRoot(rootId) ?: return 0
        if (now - checkpoint.scannedAt > CHECKPOINT_TTL_MS) {
            checkpointDao.deleteForRoot(rootId)
            return 0
        }
        val index = prepared.keys.toList().indexOf(checkpoint.lastProcessedFolderPath)
        if (index < 0) {
            checkpointDao.deleteForRoot(rootId)
            return 0
        }
        return index
    }

    internal class MutableScanReport(val rootId: UUID) {
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
        private const val BATCH_SIZE = 50
        private const val PROGRESS_INTERVAL = 100
        private const val CHECKPOINT_TTL_MS = 24L * 60L * 60L * 1000L
    }
}

/** التحقق من أن الإشارات الجديدة "أغنى معلومة" من المخزن قبل أي تحديث (يمنع تجريد الثقة بلا داعٍ). */
private fun EditionSignals.haveMoreInfoThan(edition: EditionEntity): Boolean {
    val currentConfidence = edition.confidenceScore
    val newConfidence = EditionIntelligence.calculateConfidence(this)
    return if (this.embeddedTags?.title != null) true else newConfidence >= currentConfidence
}