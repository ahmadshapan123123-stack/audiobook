package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.*
import java.util.UUID
import javax.inject.Inject

/**
 * Development-only seeder that fills an empty database with a rich, realistic
 * Arabic audiobook library so every screen can be verified visually.
 *
 * Gate: called once under [com.example.audiobook.BuildConfig.DEBUG] in MainActivity,
 * and it no-ops as soon as any book already exists. It is NOT part of the real user
 * path — production data comes exclusively from the user's LibraryRoots via the Scanner.
 *
 * Coverage per the emulator-verification protocol:
 *  - 4 authors with distinct hex colorTheme values (some null-tolerant consumers rely on it).
 *  - 2 series (each with 3 books ordered via orderInSeries).
 *  - 10 books spanning NOT_STARTED / IN_PROGRESS (10%, 25%, 50%, 90%) / FINISHED.
 *  - 2 books with two editions each (low/unconfirmed confidence on the secondary
 *    edition so they surface as Review-Matches suspect cases).
 *  - 1 audio file marked MISSING for the missing-file visual indicator.
 *  - 2 collections + 3 favorites.
 *  - The featured edition carries 8 chapters, 3 bookmarks and 2 notes for the Timeline.
 *  - Deliberately similar Arabic titles: "ما وراء الطبيعة" and "ماوراء الطبيعه".
 *  - Listening sessions spread across recent days so History/Statistics have real rows.
 *  - Covers are colored placeholders (coverSource = PLACEHOLDER, no network images).
 */
class DatabaseSeeder @Inject constructor(private val database: AppDatabase) {

    suspend operator fun invoke() {
        if (database.bookDao().getAll().isNotEmpty()) return
        val now = System.currentTimeMillis()
        val day = 24 * 60 * 60 * 1000L

        val rootDemo = LibraryRootEntity(
            id = uid("root.demo"), uri = "content://com.example.audiobook.demo/demo",
            displayName = "مكتبة التجربة", isPriority = true, isEnabled = true,
            lastScanAt = now - day, scanStatus = ScanStatus.IDLE
        )
        val rootMobile = LibraryRootEntity(
            id = uid("root.mobile"), uri = "content://com.example.audiobook.demo/mobile",
            displayName = "الكتب المحمولة", isPriority = false, isEnabled = true,
            lastScanAt = now - day, scanStatus = ScanStatus.IDLE
        )
        database.libraryRootDao().insert(rootDemo)
        database.libraryRootDao().insert(rootMobile)

        val tawfiq = AuthorEntity(uid("author.tawfiq"), "أحمد خالد توفيق", "#4338CA")
        val mahfouz = AuthorEntity(uid("author.mahfouz"), "نجيب محفوظ", "#92400E")
        val zaidan = AuthorEntity(uid("author.zaidan"), "يوسف زيدان", "#0F766E")
        val samman = AuthorEntity(uid("author.samman"), "غادة السمان", "#BE185D")
        listOf(tawfiq, mahfouz, zaidan, samman).forEach { database.authorDao().insert(it) }

        val seriesAssateer = SeriesEntity(uid("series.assateer"), tawfiq.id, "ما وراء الطبيعة", "#312E81")
        val seriesThalathia = SeriesEntity(uid("series.thalathia"), mahfouz.id, "الثلاثية", "#78350F")
        database.seriesDao().insert(seriesAssateer)
        database.seriesDao().insert(seriesThalathia)

        val bMaWara = book("ما وراء الطبيعة", tawfiq.id, null, null, "رعب")
        val bMaWaraAlt = book("ماوراء الطبيعه", tawfiq.id, null, null, "رعب")
        val bKanz = book("أسطورة الكنز", tawfiq.id, seriesAssateer.id, 1, "رعب")
        val bBait = book("أسطورة البيت", tawfiq.id, seriesAssateer.id, 2, "رعب")
        val bInsan = book("أسطورة الإنسان", tawfiq.id, seriesAssateer.id, 3, "رعب")
        val bBayn = book("بين القصرين", mahfouz.id, seriesThalathia.id, 1, "رواية")
        val bQasr = book("قصر الشوق", mahfouz.id, seriesThalathia.id, 2, "رواية")
        val bSukaria = book("السكرية", mahfouz.id, seriesThalathia.id, 3, "رواية")
        val bAzazil = book("عزازيل", zaidan.id, null, null, "رواية")
        val bLeilAma = book("الليل الأعمى", samman.id, null, null, "خواطر")
        val books = listOf(bMaWara, bMaWaraAlt, bKanz, bBait, bInsan, bBayn, bQasr, bSukaria, bAzazil, bLeilAma)
        books.forEach { database.bookDao().insert(it) }

        // --- إصدارات ---
        val eMaWaraComplete = edition(bMaWara.id, rootDemo.id, "النسخة الكاملة", "محمد خضير", 2_700_000, "M4B", "ما-وراء-الطبيعة/كاملة", 0.96f, true)
        val eMaWaraShort = edition(bMaWara.id, rootDemo.id, "النسخة المختصرة", "صبري عبد المنعم", 1_680_000, "MP3", "ما-وراء-الطبيعة/مختصرة", 0.48f, false)
        val eMaWaraAlt = edition(bMaWaraAlt.id, rootDemo.id, "نسخة كاملة", "محمد خضير", 2_400_000, "M4B", "ماوراء-الطبيعه", 0.90f, true)
        val eKanz = edition(bKanz.id, rootDemo.id, "رواية كاملة", "مصطفى الراوي", 2_100_000, "MP3", "اسطورة-الكنز", 0.88f, true)
        val eBait = edition(bBait.id, rootDemo.id, "رواية كاملة", "مصطفى الراوي", 2_280_000, "MP3", "اسطورة-البيت", 0.90f, true)
        val eInsan = edition(bInsan.id, rootDemo.id, "رواية كاملة", "مصطفى الراوي", 1_980_000, "MP3", "اسطورة-الانسان", 0.92f, true)
        val eBaynComplete = edition(bBayn.id, rootDemo.id, "الرواية الكاملة", "منى الصغير", 3_600_000, "M4B", "بين-القصرين/كاملة", 0.90f, true)
        val eBaynShort = edition(bBayn.id, rootDemo.id, "النسخة المختصرة", "طارق النجار", 1_500_000, "MP3", "بين-القصرين/مختصرة", 0.55f, false)
        val eQasr = edition(bQasr.id, rootDemo.id, "رواية كاملة", "منى الصغير", 3_720_000, "M4B", "قصر-الشوق", 0.93f, true)
        val eSukaria = edition(bSukaria.id, rootDemo.id, "رواية كاملة", "منى الصغير", 3_480_000, "M4B", "السكرية", 0.91f, true)
        val eAzazil = edition(bAzazil.id, rootDemo.id, "النسخة الكاملة", "أيمن حزام", 3_000_000, "M4B", "عزازيل", 0.89f, true)
        val eLeilAma = edition(bLeilAma.id, rootMobile.id, "نسخة كاملة", "ليلى عبد الرحمن", 1_320_000, "MP3", "الليل-الاعمى", 0.86f, true)
        val editions = listOf(
            eMaWaraComplete, eMaWaraShort, eMaWaraAlt, eKanz, eBait, eInsan,
            eBaynComplete, eBaynShort, eQasr, eSukaria, eAzazil, eLeilAma
        )
        editions.forEach { database.editionDao().insert(it) }

        // --- الملفات الصوتية (مع ملف ناقص واحد على النسخة الكاملة لـ"ما وراء الطبيعة") ---
        audioFiles(eMaWaraComplete, rootDemo.id, "ما وراء الطبيعة", 8, 2_700_000, "audio/mp4", missingIndex = 5)
        audioFiles(eMaWaraShort, rootDemo.id, "ما وراء الطبيعة (مختصرة)", 4, 1_680_000, "audio/mpeg")
        audioFiles(eMaWaraAlt, rootDemo.id, "ماوراء الطبيعه", 5, 2_400_000, "audio/mp4")
        audioFiles(eKanz, rootDemo.id, "أسطورة الكنز", 4, 2_100_000, "audio/mpeg")
        audioFiles(eBait, rootDemo.id, "أسطورة البيت", 4, 2_280_000, "audio/mpeg")
        audioFiles(eInsan, rootDemo.id, "أسطورة الإنسان", 4, 1_980_000, "audio/mpeg")
        audioFiles(eBaynComplete, rootDemo.id, "بين القصرين", 8, 3_600_000, "audio/mp4")
        audioFiles(eBaynShort, rootDemo.id, "بين القصرين (مختصرة)", 3, 1_500_000, "audio/mpeg")
        audioFiles(eQasr, rootDemo.id, "قصر الشوق", 5, 3_720_000, "audio/mp4")
        audioFiles(eSukaria, rootDemo.id, "السكرية", 5, 3_480_000, "audio/mp4")
        audioFiles(eAzazil, rootDemo.id, "عزازيل", 6, 3_000_000, "audio/mp4")
        audioFiles(eLeilAma, rootMobile.id, "الليل الأعمى", 4, 1_320_000, "audio/mpeg")

        // --- الفصول: الشاشة البارزة "ما وراء الطبيعة" تحمل 8 فصول لاختبار الـTimeline ---
        chapters(eMaWaraComplete, 8)
        chapters(eMaWaraAlt, 4)
        chapters(eKanz, 4)
        chapters(eBait, 4)
        chapters(eInsan, 4)
        chapters(eBaynComplete, 6)
        chapters(eQasr, 5)
        chapters(eSukaria, 5)
        chapters(eAzazil, 6)
        chapters(eLeilAma, 4)

        // --- سجلات اكتمال الفصول (قاعدة الـ90% الحرفية: مرة واحدة لكل فصل) ---
        completeChapters(eBait, database.chapterDao().getByParent(eBait.id), now - 6 * day)
        completeChapters(eQasr, database.chapterDao().getByParent(eQasr.id), now - 10 * day)
        completeChapters(eAzazil, database.chapterDao().getByParent(eAzazil.id).take(4), now - 3 * 60 * 60 * 1000L)

        // --- إشارات مرجعية وملاحظات على النسخة الكاملة من "ما وراء الطبيعة" ---
        bookmark(eMaWaraComplete.id, 90_000, BookmarkType.BOOKMARK, null, now - 20 * day)
        bookmark(eMaWaraComplete.id, 540_000, BookmarkType.BOOKMARK, null, now - 12 * day)
        bookmark(eMaWaraComplete.id, 1_350_000, BookmarkType.NOTE, "إشارة مهمة: عودة بطل الرواية إلى البيت الغامض", now - 8 * day)
        bookmark(eMaWaraComplete.id, 2_000_000, BookmarkType.BOOKMARK, null, now - 4 * day)
        bookmark(eMaWaraComplete.id, 2_450_000, BookmarkType.NOTE, "تساؤل حول هوية الرجل الغريب في الممر", now - 2 * day)

        // --- تقدم الاستماع بدرجات مختلفة ---
        progress(eMaWaraComplete, (2_700_000L * 0.10).toLong(), now - 90 * 60 * 1000L, ProgressStatus.IN_PROGRESS, 1.25f)
        progress(eMaWaraAlt, (2_400_000L * 0.25).toLong(), now - day, ProgressStatus.IN_PROGRESS, 1.0f)
        progress(eKanz, 0, 0, ProgressStatus.NOT_STARTED, 1.0f)
        progress(eBait, 2_280_000L, now - 6 * day, ProgressStatus.FINISHED, 1.0f)
        progress(eInsan, 0, 0, ProgressStatus.NOT_STARTED, 1.0f)
        progress(eBaynComplete, (3_600_000L * 0.50).toLong(), now - 5 * 60 * 60 * 1000L, ProgressStatus.IN_PROGRESS, 1.0f)
        progress(eQasr, 3_720_000L, now - 10 * day, ProgressStatus.FINISHED, 1.25f)
        progress(eSukaria, 0, 0, ProgressStatus.NOT_STARTED, 1.0f)
        progress(eAzazil, (3_000_000L * 0.90).toLong(), now - 3 * 60 * 60 * 1000L, ProgressStatus.IN_PROGRESS, 1.5f)
        progress(eLeilAma, 0, 0, ProgressStatus.NOT_STARTED, 1.0f)

        // --- مجموعات ومفضلة ---
        val collectionNight = CollectionEntity(uid("coll.night"), "للاستماع الليلي", "🌙", null, SyncStatus.LOCAL_ONLY)
        val collectionQueue = CollectionEntity(uid("coll.queue"), "قائمة الانتظار", "📚", null, SyncStatus.LOCAL_ONLY)
        database.collectionDao().insert(collectionNight)
        database.collectionDao().insert(collectionQueue)
        addToCollection(collectionNight.id, bMaWara.id)
        addToCollection(collectionNight.id, bBayn.id)
        addToCollection(collectionNight.id, bAzazil.id)
        addToCollection(collectionQueue.id, bKanz.id)
        addToCollection(collectionQueue.id, bSukaria.id)
        addToCollection(collectionQueue.id, bLeilAma.id)

        favorite(bMaWara.id, now - 15 * day)
        favorite(bBayn.id, now - 9 * day)
        favorite(bAzazil.id, now - 3 * day)

        // --- جلسات استماع موزعة على أيام متتالية (شاشة السجل والإحصائيات) ---
        session(eMaWaraComplete.id, now - 110 * 60 * 1000L, now - 85 * 60 * 1000L, 1_500_000, SessionEndReason.MANUAL_PAUSE)
        session(eBaynComplete.id, now - 325 * 60 * 1000L, now - 300 * 60 * 1000L, 1_500_000, SessionEndReason.MANUAL_PAUSE)
        session(eAzazil.id, now - 190 * 60 * 1000L, now - 180 * 60 * 1000L, 600_000, SessionEndReason.APP_CLOSED)
        session(eMaWaraComplete.id, now - day - 40 * 60 * 1000L, now - day, 2_400_000, SessionEndReason.SLEEP_TIMER)
        session(eMaWaraAlt.id, now - day - 30 * 60 * 1000L, now - day - 3 * 60 * 1000L, 1_620_000, SessionEndReason.MANUAL_PAUSE)
        session(eBaynComplete.id, now - 2 * day - 30 * 60 * 1000L, now - 2 * day - 10 * 60 * 1000L, 1_200_000, SessionEndReason.SLEEP_TIMER)
        session(eMaWaraComplete.id, now - 3 * day - 20 * 60 * 1000L, now - 3 * day, 1_200_000, SessionEndReason.APP_CLOSED)
        session(eBait.id, now - 6 * day - 38 * 60 * 1000L, now - 6 * day, 2_280_000, SessionEndReason.FINISHED_BOOK)
        session(eQasr.id, now - 10 * day - 62 * 60 * 1000L, now - 10 * day - 2 * 60 * 1000L, 3_600_000, SessionEndReason.FINISHED_BOOK)
    }

    private fun book(
        title: String, authorId: UUID, seriesId: UUID?, orderInSeries: Int?, genre: String
    ) = BookEntity(
        id = uid("book.$title"), title = title, authorId = authorId, seriesId = seriesId,
        orderInSeries = orderInSeries, genre = genre, coverImagePath = null,
        coverSource = CoverSource.PLACEHOLDER, isCoverUserSelected = false,
        isTitleUserConfirmed = false, defaultEditionId = null, remoteId = null,
        syncStatus = SyncStatus.LOCAL_ONLY
    )

    private fun edition(
        bookId: UUID, rootId: UUID, label: String, narrator: String?, totalDurationMs: Long,
        fileFormat: String, folder: String, confidence: Float, confirmed: Boolean
    ) = EditionEntity(
        id = uid("edition.$bookId.$label"), bookId = bookId, narratorName = narrator, label = label,
        totalDurationMs = totalDurationMs, fileFormat = fileFormat, libraryRootId = rootId,
        sourceFolderPath = folder, confidenceScore = confidence, isUserConfirmed = confirmed,
        isNarratorUserConfirmed = false, isLabelUserConfirmed = false, remoteId = null,
        syncStatus = SyncStatus.LOCAL_ONLY
    )

    private suspend fun audioFiles(
        edition: EditionEntity, rootId: UUID, title: String, count: Int, totalMs: Long,
        mime: String, missingIndex: Int? = null
    ) {
        val perFile = totalMs / count
        val rootPrefix = rootId.toString().take(8)
        repeat(count) { i ->
            val pos = if (i == count - 1) totalMs - i * perFile else perFile
            val status = if (missingIndex != null && i == missingIndex) FileStatus.MISSING else FileStatus.AVAILABLE
            database.audioFileDao().insert(
                AudioFileEntity(
                    id = uid("audio.$title.$i"), editionId = edition.id,
                    fileUri = "content://com.example.audiobook.demo/${edition.sourceFolderPath}/${(i + 1).toString().padStart(2, '0')}.${if (mime == "audio/mp4") "m4b" else "mp3"}",
                    relativePath = "$title/${(i + 1).toString().padStart(2, '0')}.${if (mime == "audio/mp4") "m4b" else "mp3"}",
                    fileName = (i + 1).toString().padStart(2, '0'), orderIndex = i,
                    durationMs = pos, fileSizeBytes = pos * 16, lastModified = System.currentTimeMillis(),
                    contentFingerprint = "fp-$rootPrefix-$edition.id-$i", mimeType = mime, fileStatus = status
                )
            )
        }
    }

    private suspend fun chapters(edition: EditionEntity, count: Int) {
        val step = edition.totalDurationMs / count
        repeat(count) { i ->
            database.chapterDao().insert(
                ChapterEntity(
                    id = uid("chapter.$edition.id.$i"), editionId = edition.id,
                    title = "الفصل ${arabicNumber(i + 1)}", startPositionMs = i * step,
                    orderIndex = i, createdFrom = ChapterCreatedFrom.AUTO_SPLIT
                )
            )
        }
    }

    private suspend fun completeChapters(edition: EditionEntity, list: List<ChapterEntity>, atMs: Long) {
        list.forEach { c -> database.chapterCompletionDao().insert(ChapterCompletionEntity(c.id, edition.id, atMs)) }
    }

    private suspend fun bookmark(
        editionId: UUID, positionMs: Long, type: BookmarkType, note: String?, createdAt: Long
    ) = database.bookmarkDao().insert(
        BookmarkEntity(
            id = UUID.randomUUID(), editionId = editionId, positionMs = positionMs, createdAt = createdAt,
            type = type, noteText = note, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
    )

    private suspend fun progress(
        edition: EditionEntity, positionMs: Long, lastPlayedAt: Long, status: ProgressStatus, speed: Float
    ) = database.progressDao().insert(
        ListeningProgressEntity(
            id = UUID.randomUUID(), editionId = edition.id, currentPositionMs = positionMs,
            lastPlayedAt = lastPlayedAt, status = status, playbackSpeed = speed,
            remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
        )
    )

    private suspend fun addToCollection(collectionId: UUID, bookId: UUID) =
        database.collectionBookCrossRefDao().insert(CollectionBookCrossRef(collectionId, bookId))

    private suspend fun favorite(bookId: UUID, addedAt: Long) =
        database.favoriteBookDao().insert(FavoriteBook(bookId, addedAt))

    private suspend fun session(
        editionId: UUID, startedAt: Long, endedAt: Long, durationMs: Long, reason: SessionEndReason
    ) = database.listeningSessionDao().insert(
        ListeningSessionEntity(
            id = UUID.randomUUID(), editionId = editionId, startedAt = startedAt, endedAt = endedAt,
            durationListenedMs = durationMs, endReason = reason, sessionState = SessionState.COMPLETED
        )
    )

    private fun arabicNumber(value: Int): String = when (value) {
        1 -> "الأول"; 2 -> "الثاني"; 3 -> "الثالث"; 4 -> "الرابع"; 5 -> "الخامس"
        6 -> "السادس"; 7 -> "السابع"; 8 -> "الثامن"; else -> value.toString()
    }

    private fun uid(tag: String): UUID = UUID.nameUUIDFromBytes(tag.toByteArray())

    companion object { fun stable(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray()) }
}