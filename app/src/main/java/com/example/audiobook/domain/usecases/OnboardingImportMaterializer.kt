package com.example.audiobook.domain.usecases

import androidx.room.withTransaction
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AuthorEntity
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.PendingDiscoveryEntity
import com.example.audiobook.data.room.entity.DiscoveryStatus
import com.example.audiobook.data.room.entity.SeriesEntity
import com.example.audiobook.data.room.entity.SyncStatus
import kotlinx.coroutines.yield
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GAP 2 — كتابة شجرة الاستيراد داخل نطاق الخدمة الأمامية.
 *
 * كان `materializeTree` دالة خاصة في [com.example.audiobook.presentation.onboarding.OnboardingViewModel]
 * تُستدعى **قبل** تشغيل الخدمة، فتنفّذ آلاف الإدراجات في معاملات صغيرة بلا
 * أي تغطية foreground: خروج المستخدم من الشاشة أو ضغط النظام على العملية
 * يقتلها في منتصف شجرة 10,000 كتاب، فتضيع الدفعة الجارية ويُفقد الاستيراد.
 *
 * نقلها إلى use case في طبقة المجال يجعلها قابلة للاستدعاء من
 * [com.example.audiobook.background.scan.ScanForegroundService] بعد أن
 * يصير التطبيق في نطاق أمامي حقيقي.
 *
 * الدفعات (50 كتابًا) والتخلّي بين الدفعات والإلغاء التعاوني عبر
 * [ScanProgressBus] كلّها منقولة كما كانت — لم يتغيّر سلوك الاستيراد، بل
 * مَن ينفّذه فقط.
 */
@Singleton
class OnboardingImportMaterializer @Inject constructor(
    private val database: AppDatabase
) {

    /** كتاب واحد في شجرة الاستيراد مع سياقه — مسطّح كي يمرّ بين الطبقات. */
    data class BookSpec(
        val authorName: String?,
        val seriesName: String?,
        val title: String,
        val folderPath: String,
        val totalDurationMs: Long
    )

    /**
     * يطبّق [bookSpecs] على [root] بدفعات. يُبلّغ التقدّم عبر [onProgress]
     * ليبقى إشعار الخدمة حيًّا، ويحترم الإلغاء عند حدود الدفعات.
     *
     * يُرجع عدد الكتب المموّهة فعليًا (يتوقف مبكرًا عند الإلغاء).
     */
    suspend fun materialize(
        root: LibraryRootEntity,
        bookSpecs: List<BookSpec>,
        renamedPaths: Set<String>,
        skippedPaths: List<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): Int {
        val authorCache = HashMap<String, AuthorEntity>()
        val seriesCache = HashMap<String, SeriesEntity>()

        suspend fun authorFor(name: String?): AuthorEntity? {
            if (name.isNullOrBlank()) return null
            authorCache[name]?.let { return it }
            val author = database.authorDao().getByName(name)
                ?: AuthorEntity(name = name, colorTheme = null).also { database.authorDao().insert(it) }
            authorCache[name] = author
            return author
        }

        suspend fun seriesFor(author: AuthorEntity, name: String?): SeriesEntity? {
            if (name.isNullOrBlank()) return null
            val key = "${author.id}:$name"
            seriesCache[key]?.let { return it }
            val series = database.seriesDao().getByParent(author.id)
                .firstOrNull { it.name == name }
                ?: SeriesEntity(authorId = author.id, name = name, colorTheme = null)
                    .also { database.seriesDao().insert(it) }
            seriesCache[key] = series
            return series
        }

        var created = 0
        bookSpecs.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            database.withTransaction {
                batch.forEach { spec ->
                    val author = authorFor(spec.authorName)
                    val series = author?.let { seriesFor(it, spec.seriesName) }
                    // مجلد سُمّي يدويًا = المستخدم أكّد عنوانه، فلا يقترح عليه
                    // التصنيف بعد ذلك.
                    val titleConfirmed = spec.folderPath in renamedPaths
                    val bookRow = BookEntity(
                        title = spec.title,
                        authorId = author?.id,
                        seriesId = series?.id,
                        orderInSeries = null,
                        genre = null,
                        coverImagePath = null,
                        coverSource = CoverSource.PLACEHOLDER,
                        isCoverUserSelected = false,
                        isTitleUserConfirmed = titleConfirmed,
                        defaultEditionId = null,
                        remoteId = null,
                        syncStatus = SyncStatus.LOCAL_ONLY
                    ).also { database.bookDao().insert(it) }
                    database.editionDao().insert(
                        EditionEntity(
                            bookId = bookRow.id,
                            narratorName = null,
                            label = spec.folderPath.substringAfterLast('/').ifBlank { root.displayName },
                            totalDurationMs = spec.totalDurationMs,
                            fileFormat = "UNKNOWN",
                            libraryRootId = root.id,
                            sourceFolderPath = spec.folderPath,
                            confidenceScore = 1f,
                            isUserConfirmed = false,
                            remoteId = null,
                            syncStatus = SyncStatus.LOCAL_ONLY
                        )
                    )
                    created++
                }
            }
            onProgress((batchIndex + 1) * BATCH_SIZE, bookSpecs.size)
            // إفساح المجال بين الدفعات: مسحات WAL والـdirty buffers تكتمل بلا
            // دفع 10k INSERT دفعةً واحدة، كما كان.
            yield()
            if (ScanProgressBus.isCancelRequested) {
                return created
            }
        }

        // المجلدات المتخطاة: يتجاهلها الفحص فلا تُنشأ لها كتب خلاف المعاينة.
        skippedPaths.forEach { folderPath ->
            database.pendingDiscoveryDao().insert(
                PendingDiscoveryEntity(
                    rootId = root.id,
                    folderPath = folderPath,
                    detectedTitle = "",
                    authorName = "",
                    seriesName = null,
                    discoveredAt = System.currentTimeMillis(),
                    status = DiscoveryStatus.IGNORED
                )
            )
        }
        return created
    }

    companion object {
        /** حجم الدفعة — محفوظ من PHASE 9 (10k insert في دفعة واحدة تقتل العملية). */
        private const val BATCH_SIZE = 50
    }
}

/**
 * حالة «استيراد معلَّق» تنتظر الخدمة.
 *
 * لا يمكن تمرير شجرة كاملة عبر `Intent` (Binder transaction limit على
 * قوائم بعشرات آلاف العناصر)، فتُحفظ في الذاكرة ككائن أحادي النطاق في العملية
 * نفسها. القبول: إن ماتت العملية قبل أن تلتقط الخدمة الطلب، يفشل الاستيراد
 * كاملًا ولا يستورد نُصفه — وهو أفضل من استيراد ناقص صامت. عمدًا
 * `@Volatile` وبلا قفل: طرف واحد يكتب (ViewModel) وطرف واحد يقرأ (الخدمة)،
 * والكتابة تسبق القراءة دائمًا.
 */
@Singleton
class PendingOnboardingImport @Inject constructor() {

    data class Payload(
        val rootId: UUID,
        val books: List<OnboardingImportMaterializer.BookSpec>,
        val renamedPaths: Set<String>,
        val skippedPaths: List<String>
    )

    @Volatile
    private var payload: Payload? = null

    /** يحفظ الدفعة المعلّقة ثم يطلب تشغيل الخدمة. */
    fun enqueue(payload: Payload) {
        this.payload = payload
    }

    /** تلتقطها الخدمة مرّة واحدة ثم تُفرَّغ. */
    fun consume(): Payload? = payload.also { payload = null }

    fun clear() {
        payload = null
    }
}
