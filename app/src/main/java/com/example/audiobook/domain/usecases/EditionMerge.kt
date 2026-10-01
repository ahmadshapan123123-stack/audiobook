package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.EditionMatchDecisionEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.data.room.entity.UserDecision
import java.util.UUID
import javax.inject.Inject

/**
 * الدمج الحقيقي (وليس الشكلي): ملفات وتقدم وBookmarks الإصدار المرشح
 * تُنقل إلى الإصدار المحتفظ به ثم يُحذف المرشح. يسجل القرار فعلًا في
 * EditionMatchDecision بمرجعين صريحين (subjectEditionId, comparedAgainstEditionId)
 * ولقطة إشارات فعلية. User Override Wins: عند الدمج اليدوي يُعلَّم الإصدار
 * المحتفظ به isUserConfirmed = true فلا يكتب عليه أي Scan لاحق.
 */
class EditionMerge @Inject constructor(private val database: AppDatabase) {

    suspend fun merge(subjectId: UUID, candidateId: UUID, userInitiated: Boolean): UUID {
        if (subjectId == candidateId) return subjectId
        val subject = database.editionDao().getById(subjectId)
            ?: error("subject edition not found: $subjectId")
        val candidate = database.editionDao().getById(candidateId)
            ?: error("candidate edition not found: $candidateId")

        database.audioFileDao().getByParent(candidate.id).forEach { file ->
            database.audioFileDao().update(file.copy(editionId = subject.id))
        }

        val subjectProgress = database.progressDao().getByParent(subject.id)
        database.progressDao().getByParent(candidate.id)?.let { progress ->
            if (subjectProgress == null) {
                database.progressDao().insert(
                    progress.copy(id = UUID.randomUUID(), editionId = subject.id, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
                )
            }
            // FIX-HOME-CRASH: حذف صريح لتقدّم المرشح فور نسخه — الاعتماد على
            // CASCADE عند حذف النسخة وحده كان يترك نافذة بصفّي IN_PROGRESS
            // لكتاب واحد (مفاتيح مكررة في قائمة المتابعة بالرئيسية).
            database.progressDao().delete(progress)
        }
        database.bookmarkDao().getByParent(candidate.id).forEach { bookmark ->
            database.bookmarkDao().insert(
                bookmark.copy(id = UUID.randomUUID(), editionId = subject.id, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
            )
        }

        persistDecision(subject, candidate)
        if (userInitiated) database.editionDao().update(subject.copy(isUserConfirmed = true))
        // FIX-COVER: وراثة الغلاف — الغلاف على مستوى الكتاب، وحذف المرشح
        // (وكتابه اليتيم) كان يفقده. الأولوية: مختار-المستخدم > مضمّن > لا شيء.
        // يُنسخ غلاف المرشح إلى كتاب المحتفَظ به فقط إن كان بلا غلاف أصلًا.
        inheritCoverIfMissing(subject.bookId, candidate.bookId)
        database.editionDao().delete(candidate)
        cleanupOrphanBook(candidate)
        return subject.id
    }

    private suspend fun persistDecision(subject: EditionEntity, candidate: EditionEntity) {
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
                userDecision = UserDecision.SAME_EDITION,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * FIX-COVER: نسخ غلاف كتاب المرشح إلى كتاب المحتفَظ به عند الحاجة —
     * لا شيء يُكتب فوق اختيار المستخدم أبدًا (كتاب المحتفَظ به إما فارغ
     * الغلاف فيُملأ، أو له غلاف فيُحترم).
     */
    private suspend fun inheritCoverIfMissing(subjectBookId: UUID, candidateBookId: UUID) {
        if (subjectBookId == candidateBookId) return
        val subjectBook = database.bookDao().getById(subjectBookId) ?: return
        if (!subjectBook.coverImagePath.isNullOrBlank() || subjectBook.isCoverUserSelected) return
        val candidateBook = database.bookDao().getById(candidateBookId) ?: return
        if (candidateBook.coverImagePath.isNullOrBlank()) return
        database.bookDao().update(
            subjectBook.copy(
                coverImagePath = candidateBook.coverImagePath,
                coverSource = candidateBook.coverSource,
                isCoverUserSelected = candidateBook.isCoverUserSelected
            )
        )
    }

    private suspend fun cleanupOrphanBook(candidate: EditionEntity) {
        if (database.editionDao().getByParent(candidate.bookId).isNotEmpty()) return
        database.bookDao().getById(candidate.bookId)?.let { database.bookDao().delete(it) }
    }
}