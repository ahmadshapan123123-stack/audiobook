package com.example.audiobook.domain.usecases

import android.util.Log
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.domain.config.StrictModeFlags

/**
 * الإشارة 4+5 في النسخة المُحصّنة: نمط السلسلة + رقم الكتاب داخل السلسلة.
 * مثال ناجح: "الاسم - الجزء 3" → pattern = "juz", partNumber = 3.
 * مثال ناجح: "Book 3" → pattern = "book", partNumber = 3.
 */
data class SeriesPart(val pattern: String, val partNumber: Int?)

/**
 * الإشارة 6: الـMetadata الداخلية الفعلية المقروءة من tags الملف الصوتي
 * (العنوان، الراوي، التصنيف) — مصدرها AudioMetadataReader الحقيقي.
 */
data class EmbeddedTags(val title: String?, val narrator: String?, val genre: String?)

/**
 * الإشارات العشر كاملة لكتاب/إصدار واحد مكتشَف من مجلد:
 *  1. primaryFileName    — اسم الملف (أول ملف، بدون الامتداد)
 *  2. folderName         — اسم المجلد
 *  3. authorFolderName   — اسم مجلد المؤلف (أول مقطع من مسار المجلد، إن وُجد)
 *  3b. seriesFolderName  — اسم مجلد السلسلة الحاوي مباشرة فوق الكتاب (إن وُجد)
 *  4. seriesPart.pattern — نمط السلسلة المكتشَف (book/juz/kitab/numbered)
 *  5. seriesPart.partNumber — رقم الكتاب في السلسلة (إن وُجد)
 *  6. embeddedTags       — الـMetadata الداخلية الفعلية (tags)
 *  7. totalDurationMs    — المدة الإجمالية (مجموع مدد الملفات)
 *  8. fileCount + filesOrdered — عدد الملفات ووجود ترتيب مكتشَف
 *  9. narrator           — الراوي (من tag أو من نمط نصي شائع في الاسم)
 * 10. format             — الصيغة/الجودة (MP3/M4B/FLAC/...)
 */
data class EditionSignals(
    val primaryFileName: String = "",
    val folderName: String = "",
    val authorFolderName: String? = null,
    val seriesFolderName: String? = null,
    val seriesPart: SeriesPart? = null,
    val embeddedTags: EmbeddedTags? = null,
    val totalDurationMs: Long = 0L,
    val fileCount: Int = 0,
    val filesOrdered: Boolean = false,
    val narrator: String? = null,
    val format: String? = null,
    /** تلميح سلسلة من وسم الألبوم (album) في الـMetadata — يُستعمل فقط إن لم يحسم المجلد سلسلة. */
    val seriesAlbumHint: String? = null
) {
    /** العنوان المحلول: من الـMetadata الداخلية إن وُجدت، وإلا اسم المجلد. */
    fun resolvedTitle(): String? =
        embeddedTags?.title?.takeIf { it.isNotBlank() } ?: folderName.substringAfterLast('/').takeIf { it.isNotBlank() }

    /** العنوان البنيوي المستقر: اسم مجلد الكتاب من بنية المجلدات (يُفضَّل على الـmetadata عند التجميع). */
    fun structuralTitle(): String = folderName.substringAfterLast('/').takeIf { it.isNotBlank() } ?: ""

    fun normalizedTitle(): String = ArabicSearchNormalizer.normalize(resolvedTitle().orEmpty())

    fun normalizedStructuralTitle(): String = ArabicSearchNormalizer.normalize(structuralTitle())

    /** مفتاح الهوية الكامل لبناء المجلد بنيويًا (مؤلف ← سلسلة ← كتاب). */
    fun structuralIdentityKey(): String =
        "${ArabicSearchNormalizer.normalize(authorFolderName.orEmpty())}|" +
            "${ArabicSearchNormalizer.normalize(seriesFolderName.orEmpty())}|" +
            normalizedStructuralTitle()

    fun hasKnownDuration(): Boolean = totalDurationMs > 0L

    /** مفتاح نمط التسمية المستخدم لتعديل الأوزان من القرارات السابقة. */
    fun patternKey(): String =
        "${ArabicSearchNormalizer.normalize(folderName)}|${seriesPart?.pattern ?: "-"}|${format ?: "-"}"
}

/**
 * استخراج الإشارات العشر كلها كدوال نقية قابلة للاختبار بمفردها.
 * لا تتصل بقاعدة البيانات إطلاقًا — تأخذ بيانات وتُرجع قيمًا.
 */
object EditionSignalExtractor {

    /** الحد الأقصى المعقول لمدة ملف واحد (12 ساعة) — أي قيمة أعلى تُعد فاسدة. */
    private const val MAX_FILE_DURATION_MS = 12L * 60 * 60 * 1000

    private const val TAG = "EditionSignals"

    /**
     * مجموع مدد آمن: أي ملف بمدة خارج 0 < m ≤ 12 ساعة يُتجاهَل (صفر في المجموع)
     * مع تسجيل تحذير؛ والمجموع نفسه محمي من الفيضان الطافي (Long) بالقفل على القيمة القصوى.
     */
    private fun safeDurationSum(metadataList: List<AudioMetadata>): Long {
        var total = 0L
        metadataList.forEach { meta ->
            val d = meta.durationMs
            when {
                d <= 0L -> {}
                d > MAX_FILE_DURATION_MS -> Log.w(TAG, "edition-signals: implausible file duration $d ms; treating as 0")
                total > Long.MAX_VALUE - d -> {
                    total = Long.MAX_VALUE
                    Log.w(TAG, "edition-signals: duration total overflow clamped to Long.MAX_VALUE")
                }
                else -> total += d
            }
        }
        return total
    }

    fun withoutExtension(fileName: String): String = fileName.substringBeforeLast('.', fileName)

    /** الإشارة 4+5: اكتشاف نمط السلسلة ورقم الكتاب فيها (بدون افتراض ثابت). */
    fun extractSeriesPart(text: String): SeriesPart? {
        if (text.isBlank()) return null
        SERIES_PATTERNS.forEach { patternDef ->
            val match = patternDef.regex.find(text) ?: return@forEach
            val suffix = match.groupValues[1]
            val number = ARABIC_ORDINALS[suffix] ?: arabicDigitToIntOrNull(suffix) ?: return@forEach
            return SeriesPart(patternDef.label, number)
        }
        return null
    }

    /** الإشارة 9: الراوي من نمط نصي شائع في الاسم ("narrated by", "قراءة", "برواية", "روى"). */
    fun extractNarratorFromName(name: String): String? {
        if (name.isBlank()) return null
        NARRATOR_PATTERNS.forEach { regex ->
            val match = regex.find(name) ?: return@forEach
            val candidate = match.groupValues[1].trim().trimEnd('.', ' ', '،', ',').takeIf { it.length >= 2 }
            if (candidate != null) return candidate
        }
        return null
    }

    /** الإشارة 8b: ترتيب الملفات مكتشَف بترقيم لاحق في أغلب الأسماء وبأرقام متصاعدة. */
    fun detectFileOrder(fileNames: List<String>): Boolean {
        if (fileNames.size < 2) return false
        val numbered = fileNames.mapNotNull { fileName ->
            val match = SUFFIX_NUMBER.find(fileName) ?: return@mapNotNull null
            arabicDigitToIntOrNull(match.groupValues[1]) ?: return@mapNotNull null
        }
        if (numbered.size < fileNames.size - 1) return false
        return numbered == numbered.sorted()
    }

    /** الإشارة 10: الصيغة/الجودة من امتداد الملف. */
    fun formatLabelFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "mp3" -> "MP3"
        "m4b" -> "M4B"
        "m4a" -> "M4A"
        "flac" -> "FLAC"
        "aac" -> "AAC"
        "opus" -> "OPUS"
        else -> null
    }

    /** تجميع الإشارات العشر من بيانات مجلد فعلية. */
    fun build(
        folderName: String,
        authorFolderName: String?,
        fileNames: List<String>,
        metadataList: List<AudioMetadata>,
        seriesFolderName: String? = null
    ): EditionSignals {
        val first = fileNames.firstOrNull().orEmpty()
        val firstStem = withoutExtension(first)
        val title = metadataList.mapNotNull { it.title?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
        val narratorTag = metadataList.mapNotNull { it.narratorName?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
        val genre = metadataList.mapNotNull { it.genre?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
        val embedded = if (title != null || narratorTag != null || genre != null) {
            EmbeddedTags(title, narratorTag, genre)
        } else {
            null
        }
        val series = extractSeriesPart(folderName) ?: extractSeriesPart(firstStem)
        val narrator = narratorTag ?: extractNarratorFromName(folderName) ?: extractNarratorFromName(firstStem)
        val albumHint = if (StrictModeFlags.ENABLE_METADATA_HINTS) {
            metadataList.mapNotNull { it.album?.takeIf { s -> s.isNotBlank() } }.firstOrNull()
        } else {
            null
        }
        return EditionSignals(
            primaryFileName = firstStem,
            folderName = folderName,
            authorFolderName = authorFolderName,
            seriesFolderName = seriesFolderName,
            seriesPart = series,
            embeddedTags = embedded,
            totalDurationMs = safeDurationSum(metadataList),
            fileCount = metadataList.size,
            filesOrdered = detectFileOrder(fileNames),
            narrator = narrator,
            format = fileNames.mapNotNull { formatLabelFor(it) }.firstOrNull(),
            seriesAlbumHint = albumHint
        )
    }

    private fun arabicDigitToIntOrNull(raw: String): Int? {
        val latin = raw.map { ch ->
            if (ch in '٠'..'٩') (ch.code - '٠'.code + '0'.code).toChar() else ch
        }.toCharArray().let(::String)
        return latin.toIntOrNull()
    }

    private data class SeriesPatternDef(val label: String, val regex: Regex)

    private val SERIES_PATTERNS = listOf(
        SeriesPatternDef("book", Regex("""(?i)\b(?:book|part|vol(?:ume)?)\s*[:._-]?\s*(\d{1,4}|[٠-٩]{1,4})\b""")),
        SeriesPatternDef("juz", Regex("""(?:الجزء|جزء)\s*(?:ال)?\s*[:._-]?\s*(الأول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|\d{1,4}|[٠-٩]{1,4})""")),
        SeriesPatternDef("kitab", Regex("""(?:الكتاب|كتاب)\s*(?:ال)?\s*[:._-]?\s*(الأول|الثاني|الثالث|الرابع|الخامس|السادس|السابع|الثامن|التاسع|العاشر|\d{1,4}|[٠-٩]{1,4})""")),
        SeriesPatternDef("numbered", Regex("""(?:^|\s)[-_]?\s*(\d{1,4}|[٠-٩]{1,4})\s*$"""))
    )

    private val ARABIC_ORDINALS = mapOf(
        "الأول" to 1, "الثاني" to 2, "الثالث" to 3, "الرابع" to 4,
        "الخامس" to 5, "السادس" to 6, "السابع" to 7, "الثامن" to 8,
        "التاسع" to 9, "العاشر" to 10
    )

    private val NARRATOR_PATTERNS = listOf(
        Regex("""(?i)\bnarrated\s+by\s*[:：]?\s*([^,،\-—|]{2,40})"""),
        Regex("""(?i)\bread\s+by\s*[:：]?\s*([^,،\-—|]{2,40})"""),
        Regex("""\bقراءة\s*(?:(?:ال)?(?:فنان|راوي|أستاذ))?\s*[:：]?\s*([^,،\-—|]{2,40})"""),
        Regex("""\bبرواية\s*[:：]?\s*([^,،\-—|]{2,40})"""),
        Regex("""\b(?:روى|يروي|رويت)\s*[:：]?\s*([^,،\-—|]{2,40})""")
    )

    private val SUFFIX_NUMBER = Regex("""^.+[ _.\-](\d{1,4}|[٠-٩]{1,4})(?:\.\w+)?$""")
}