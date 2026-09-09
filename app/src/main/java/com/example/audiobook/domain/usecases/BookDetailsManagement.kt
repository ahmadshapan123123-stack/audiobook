package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import java.util.UUID

enum class CoverCandidateSource { USER, EMBEDDED, FOLDER_COVER, FOLDER_IMAGE, PLACEHOLDER }

data class CoverCandidate(val path: String, val source: CoverCandidateSource)

object CoverPolicy {
    fun choose(current: BookEntity, candidates: List<CoverCandidate>): BookEntity {
        if (current.isCoverUserSelected) return current
        val selected = candidates.firstOrNull() ?: return current
        return current.copy(
            coverImagePath = selected.path,
            coverSource = selected.source.toCoverSource(),
            isCoverUserSelected = selected.source == CoverCandidateSource.USER
        )
    }

    fun userSelected(book: BookEntity, path: String): BookEntity = book.copy(
        coverImagePath = path,
        coverSource = CoverSource.FOLDER_COVER,
        isCoverUserSelected = true
    )

    private fun CoverCandidateSource.toCoverSource() = when (this) {
        CoverCandidateSource.USER, CoverCandidateSource.FOLDER_COVER -> CoverSource.FOLDER_COVER
        CoverCandidateSource.EMBEDDED -> CoverSource.EMBEDDED
        CoverCandidateSource.FOLDER_IMAGE -> CoverSource.FOLDER_IMAGE
        CoverCandidateSource.PLACEHOLDER -> CoverSource.PLACEHOLDER
    }
}

data class EditionManagementState(
    val editions: List<EditionEntity>,
    val defaultEditionId: UUID?
)

class BookDetailsManagement {
    /** تعديل يدوي من المستخدم → يُعلَّم العنوان isTitleUserConfirmed (User Override Wins). */
    fun updateMetadata(book: BookEntity, title: String, genre: String?, seriesId: UUID?): BookEntity =
        book.copy(title = title, genre = genre, seriesId = seriesId, isTitleUserConfirmed = true)

    /** إعادة تسمية يدوية → تُعلَّم isLabelUserConfirmed فلا يكتب عليها Scan لاحق. */
    fun renameEdition(edition: EditionEntity, label: String): EditionEntity = edition.copy(label = label, isLabelUserConfirmed = true)

    /** تغيير الراوي يدويًا → يُعلَّم isNarratorUserConfirmed. */
    fun changeNarrator(edition: EditionEntity, narrator: String?): EditionEntity = edition.copy(narratorName = narrator, isNarratorUserConfirmed = true)

    fun moveEdition(edition: EditionEntity, libraryRootId: UUID, folderPath: String): EditionEntity =
        edition.copy(libraryRootId = libraryRootId, sourceFolderPath = folderPath)

    fun setDefaultEdition(state: EditionManagementState, editionId: UUID): EditionManagementState =
        state.copy(defaultEditionId = editionId)

    /** تقسيم/نسخ إصدار → قرار مستخدم نهائي. */
    fun splitEdition(state: EditionManagementState, editionId: UUID): EditionManagementState {
        val original = state.editions.firstOrNull { it.id == editionId } ?: return state
        val split = original.copy(id = UUID.randomUUID(), label = "${original.label} - مفصول", isUserConfirmed = true)
        return state.copy(editions = state.editions + split)
    }

    /** دمج إصدارين → قرار مستخدم نهائي على المحتفظ به. */
    fun mergeEditions(state: EditionManagementState, subjectId: UUID, comparedId: UUID): EditionManagementState {
        val subject = state.editions.firstOrNull { it.id == subjectId }
        val kept = subject?.copy(isUserConfirmed = true)?.let { kept -> state.editions.map { if (it.id == kept.id) kept else it } } ?: state.editions
        return state.copy(editions = kept.filterNot { it.id == comparedId }, defaultEditionId = if (state.defaultEditionId == comparedId) subjectId else state.defaultEditionId)
    }

    /** Reset Metadata / إعادة الاكتشاف: يرفع حماية User Override Wins ليعيد الفحص الاكتشاف. */
    fun resetMetadata(book: BookEntity): BookEntity = book.copy(
        isTitleUserConfirmed = false,
        isCoverUserSelected = false
    )

    fun resetEditionMetadata(edition: EditionEntity): EditionEntity = edition.copy(
        isNarratorUserConfirmed = false,
        isLabelUserConfirmed = false,
        isUserConfirmed = false
    )
}