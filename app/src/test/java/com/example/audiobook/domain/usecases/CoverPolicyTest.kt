package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CoverPolicyTest {
    private val authorId = UUID.randomUUID()
    private fun book(userSelected: Boolean = false) = BookEntity(
        title = "ما وراء الطبيعة", authorId = authorId, seriesId = null, orderInSeries = null,
        genre = "خيال", coverImagePath = "old.jpg", coverSource = CoverSource.FOLDER_COVER,
        isCoverUserSelected = userSelected, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY
    )

    @Test
    fun manualCoverWinsOverEveryLaterScanCandidate() {
        val manual = CoverPolicy.userSelected(book(), "manual.jpg")
        val afterScan = CoverPolicy.choose(manual, listOf(CoverCandidate("embedded.jpg", CoverCandidateSource.EMBEDDED), CoverCandidate("folder.jpg", CoverCandidateSource.FOLDER_IMAGE)))
        assertEquals("manual.jpg", afterScan.coverImagePath)
        assertTrue(afterScan.isCoverUserSelected)
    }

    @Test
    fun automaticCoverFollowsEmbeddedThenFolderPriority() {
        val selected = CoverPolicy.choose(book(), listOf(CoverCandidate("embedded.jpg", CoverCandidateSource.EMBEDDED), CoverCandidate("folder.jpg", CoverCandidateSource.FOLDER_IMAGE)))
        assertEquals("embedded.jpg", selected.coverImagePath)
        assertEquals(CoverSource.EMBEDDED, selected.coverSource)
    }
}