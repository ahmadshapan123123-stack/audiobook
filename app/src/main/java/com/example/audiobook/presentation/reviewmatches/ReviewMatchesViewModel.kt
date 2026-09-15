package com.example.audiobook.presentation.reviewmatches

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.EditionMatchDecisionEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.data.room.entity.UserDecision
import com.example.audiobook.domain.usecases.EditionIntelligence
import com.example.audiobook.domain.usecases.EditionMerge
import com.example.audiobook.domain.usecases.EditionSignals
import com.example.audiobook.domain.usecases.EditionSignalsCodec
import com.example.audiobook.domain.usecases.EditionStoredSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * الحدّ الفاصل "حالة تحتاج مراجعة": أي إصدار ثقته <= هذه القيمة يُعتبر متوسطًا
 * أو منخفض الثقة ويُعرض في شاشة Review Matches.
 *
 * الإصدارات الأعلى ثقةً لا تُعرض هنا أبدًا، لأنها أحد حالتين لا تحتاجان مراجعة:
 *  - دُمجت تلقائيًا في وضع Balanced (الثقة >= [EditionIntelligence.BALANCED_AUTO_MERGE_THRESHOLD])
 *    وأُعلِم الإصدار الناجي تأكيدًا بدمجٍ صامت؛
 *  - أو بقيت منفصلة في وضع Conservative الذي لا يدمج تلقائيًا أصلًا.
 *  فترشيح المراجعة حاضرًا مبني على القيمة الفعلية [EditionEntity.confidenceScore]
 *  المخزّنة من آخر Scan، وليست قيمًا مختلقة وقت العرض.
 */
const val REVIEW_CASE_MAX_CONFIDENCE: Float = EditionIntelligence.REVIEW_LOW_CONFIDENCE_MAX

/** الملخص الرقمي الحقيقي المُحتسب من قاعدة البيانات بعد آخر Scan. */
data class ReviewSummaryUi(
    val files: Int = 0,
    val books: Int = 0,
    val series: Int = 0,
    val authors: Int = 0,
    val suspectCases: Int = 0
)

/**
 * حالة مراجعة واحدة: إصدار مشكوكٌ فيه (subject) يقارَن بإصدار مرجعي
 * (candidate) من نفس الكتاب. الإشارات المعروضة مبنية بـ[EditionStoredSignals]
 * — نفس الدالة المستخدمة عند لقط القرار — فأيٌّ يعرضه الشاشة هو ما سيتخزَّن
 * فعلًا في signalsSnapshot عند اتخاذ القرار.
 */
data class ReviewCaseUi(
    val subjectEditionId: UUID,
    val subjectLabel: String,
    val subjectConfidence: Float,
    val subjectSignals: EditionSignals,
    val candidateEditionId: UUID,
    val candidateLabel: String,
    val candidateConfidence: Float,
    val candidateSignals: EditionSignals
)

data class ReviewMatchesUiState(
    val summary: ReviewSummaryUi = ReviewSummaryUi(),
    val cases: List<ReviewCaseUi> = emptyList()
)

/**
 * [R7-النقطة 1] ViewModel شاشة Review Matches: يجلب فعلًا من قاعدة البيانات
 * كل حالات الثقة المتوسطة/المنخفضة المعلّقة، ويحتسب الملخص الرقمي الحقيقي،
 * ويطبّق قرارات المستخدم كتابةً حقيقية في EditionMatchDecisionEntity (نفس Dao)
 * وتأثيرًا حقيقيًا على البيانات (دمج عبر [EditionMerge] الحالي، وفصل كتب/إصدارات).
 */
@HiltViewModel
class ReviewMatchesViewModel @Inject constructor(
    private val database: AppDatabase,
    private val editionMerge: EditionMerge
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewMatchesUiState())
    val uiState: StateFlow<ReviewMatchesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { _uiState.value = load() }
    }

    /** حساب الحالة من البيانات الفعلية (يُستخدم مباشرةً في الاختبارات لتجنّب سباق الزمن). */
    internal suspend fun load(): ReviewMatchesUiState = withContext(Dispatchers.IO) { compute() }

    fun decideSameEdition(subjectEditionId: UUID, candidateEditionId: UUID) {
        viewModelScope.launch {
            applySameEdition(subjectEditionId, candidateEditionId)
            _uiState.value = load()
        }
    }

    fun decideDifferentEdition(subjectEditionId: UUID, candidateEditionId: UUID) {
        viewModelScope.launch {
            applyDifferentEdition(subjectEditionId, candidateEditionId)
            _uiState.value = load()
        }
    }

    fun decideNotSameBook(subjectEditionId: UUID, candidateEditionId: UUID) {
        viewModelScope.launch {
            applyNotSameBook(subjectEditionId, candidateEditionId)
            _uiState.value = load()
        }
    }

    /** نفس الإصدار → دمج حقيقي عبر [EditionMerge] الموجود (لا إعادة كتابة). */
    internal suspend fun applySameEdition(subjectEditionId: UUID, candidateEditionId: UUID) {
        withContext(Dispatchers.IO) {
            editionMerge.merge(subjectEditionId, candidateEditionId, userInitiated = true)
        }
    }

    /** إصدار مختلف → إبقاء الإصدارين منفصلين فعليًا (صفّان مستقلّان) وتسجيل القرار والتأكيد. */
    internal suspend fun applyDifferentEdition(subjectEditionId: UUID, candidateEditionId: UUID) {
        withContext(Dispatchers.IO) {
            val subject = requireEdition(subjectEditionId)
            val candidate = requireEdition(candidateEditionId)
            persistDecision(subject, candidate, UserDecision.DIFFERENT_EDITION)
            database.editionDao().update(subject.copy(isUserConfirmed = true))
            database.editionDao().update(candidate.copy(isUserConfirmed = true))
        }
    }

    /** ليس نفس الكتاب → فصل حقيقي: كتاب جديد مستقل للموضوع وتسجيل القرار. */
    internal suspend fun applyNotSameBook(subjectEditionId: UUID, candidateEditionId: UUID) {
        withContext(Dispatchers.IO) {
            val subject = requireEdition(subjectEditionId)
            val candidate = requireEdition(candidateEditionId)
            val parentBook = database.bookDao().getById(subject.bookId)
                ?: error("book not found: ${subject.bookId}")
            val newBook = BookEntity(
                id = UUID.randomUUID(),
                title = parentBook.title,
                authorId = parentBook.authorId,
                seriesId = parentBook.seriesId,
                orderInSeries = parentBook.orderInSeries,
                genre = parentBook.genre,
                coverImagePath = parentBook.coverImagePath,
                coverSource = parentBook.coverSource,
                isCoverUserSelected = parentBook.isCoverUserSelected,
                isTitleUserConfirmed = parentBook.isTitleUserConfirmed,
                defaultEditionId = subject.id,
                remoteId = null,
                syncStatus = SyncStatus.LOCAL_ONLY
            )
            database.bookDao().insert(newBook)
            database.editionDao().update(subject.copy(bookId = newBook.id, isUserConfirmed = true))
            if (parentBook.defaultEditionId == subject.id) {
                database.bookDao().update(parentBook.copy(defaultEditionId = null))
            }
            persistDecision(subject.copy(bookId = newBook.id), candidate, UserDecision.NOT_SAME_BOOK)
            database.editionDao().update(candidate.copy(isUserConfirmed = true))
        }
    }

    private suspend fun compute(): ReviewMatchesUiState {
        val books = database.bookDao().getAll()
        val editions = database.editionDao().observeAll().first()
        val bookTitles = books.associate { it.id to it.title }
        val roots = editions.map { it.libraryRootId }.distinct()
            .associateWith { database.libraryRootDao().getById(it) }
        val decisions = database.editionMatchDecisionDao().getAll()
        val cases = buildCases(editions, decisions, bookTitles, roots)
        return ReviewMatchesUiState(
            summary = ReviewSummaryUi(
                files = database.audioFileDao().countAll(),
                books = books.size,
                series = database.seriesDao().observeAll().first().size,
                authors = database.authorDao().observeAll().first().size,
                suspectCases = cases.size
            ),
            cases = cases
        )
    }

    private suspend fun buildCases(
        editions: List<EditionEntity>,
        decisions: List<EditionMatchDecisionEntity>,
        bookTitles: Map<UUID, String>,
        roots: Map<UUID, com.example.audiobook.data.room.entity.LibraryRootEntity?>
    ): List<ReviewCaseUi> {
        val groups = editions.groupBy { it.bookId }
        val cases = mutableListOf<ReviewCaseUi>()
        groups.values.forEach { siblings ->
            if (siblings.size < 2) return@forEach
            val anchor = siblings.maxByOrNull { it.confidenceScore } ?: return@forEach
            siblings.filter { it.id != anchor.id && isReviewable(it) }.forEach { suspect ->
                if (pairAlreadyDecidedAgainst(suspect.id, anchor.id, decisions)) return@forEach
                cases += ReviewCaseUi(
                    subjectEditionId = suspect.id,
                    subjectLabel = suspect.label,
                    subjectConfidence = suspect.confidenceScore,
                    subjectSignals = EditionStoredSignals.fromEdition(
                        suspect,
                        roots[suspect.libraryRootId],
                        bookTitles[suspect.bookId],
                        database.audioFileDao().getByParent(suspect.id).size
                    ),
                    candidateEditionId = anchor.id,
                    candidateLabel = anchor.label,
                    candidateConfidence = anchor.confidenceScore,
                    candidateSignals = EditionStoredSignals.fromEdition(
                        anchor,
                        roots[anchor.libraryRootId],
                        bookTitles[anchor.bookId],
                        database.audioFileDao().getByParent(anchor.id).size
                    )
                )
            }
        }
        return cases.sortedBy { it.subjectConfidence }
    }

    /** مرشَّح للمراجعة: لم يُؤكَّد من قبل وثقته ضمن نطاق المتوسط/المنخفض. */
    private fun isReviewable(edition: EditionEntity): Boolean =
        !edition.isUserConfirmed && edition.confidenceScore <= REVIEW_CASE_MAX_CONFIDENCE

    /** قرار سابق ضد هذا الزوج (بالاتجاهين) يمنع عرضه مجددًا. */
    private fun pairAlreadyDecidedAgainst(
        subjectId: UUID,
        candidateId: UUID,
        decisions: List<EditionMatchDecisionEntity>
    ): Boolean = decisions.any { decision ->
        val pairMatches = (decision.subjectEditionId == subjectId && decision.comparedAgainstEditionId == candidateId) ||
            (decision.subjectEditionId == candidateId && decision.comparedAgainstEditionId == subjectId)
        pairMatches && decision.userDecision != UserDecision.SAME_EDITION
    }

    private suspend fun requireEdition(editionId: UUID): EditionEntity =
        database.editionDao().getById(editionId) ?: error("edition not found: $editionId")

    /** لقطة القرار الفعلية: نفس بنية EditionMerge.persistDecision مع القرار المحدد. */
    private suspend fun persistDecision(subject: EditionEntity, candidate: EditionEntity, decision: UserDecision) {
        val subjectRoot = database.libraryRootDao().getById(subject.libraryRootId)
        val candidateRoot = database.libraryRootDao().getById(candidate.libraryRootId)
        val subjectBook = database.bookDao().getById(subject.bookId)
        val candidateBook = database.bookDao().getById(candidate.bookId)
        val subjectSignals = EditionStoredSignals.fromEdition(
            subject, subjectRoot, subjectBook?.title, database.audioFileDao().getByParent(subject.id).size
        )
        val candidateSignals = EditionStoredSignals.fromEdition(
            candidate, candidateRoot, candidateBook?.title, database.audioFileDao().getByParent(candidate.id).size
        )
        database.editionMatchDecisionDao().insert(
            EditionMatchDecisionEntity(
                id = UUID.randomUUID(),
                subjectEditionId = subject.id,
                comparedAgainstEditionId = candidate.id,
                signalsSnapshot = EditionSignalsCodec.toJson(subjectSignals, candidateSignals),
                userDecision = decision,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}