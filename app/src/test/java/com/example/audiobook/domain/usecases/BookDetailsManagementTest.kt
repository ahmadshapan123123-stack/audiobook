package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class BookDetailsManagementTest {
    private val manager = BookDetailsManagement()
    private val first = EditionEntity(bookId = UUID.randomUUID(), narratorName = "راوي 1", label = "أصلي", totalDurationMs = 1000, fileFormat = "M4B", libraryRootId = UUID.randomUUID(), sourceFolderPath = "/one", confidenceScore = .8f, isUserConfirmed = false, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)
    private val second = first.copy(id = UUID.randomUUID(), label = "نسخة ثانية")

    @Test
    fun editionManagementSupportsAllActions() {
        val state = EditionManagementState(listOf(first, second), second.id)
        val renamed = manager.renameEdition(first, "اسم جديد")
        val narrated = manager.changeNarrator(renamed, "راوي جديد")
        val moved = manager.moveEdition(narrated, UUID.randomUUID(), "/new")
        val split = manager.splitEdition(state, first.id)
        val merged = manager.mergeEditions(state, first.id, second.id)

        assertEquals("اسم جديد", renamed.label)
        assertEquals("راوي جديد", narrated.narratorName)
        assertEquals("/new", moved.sourceFolderPath)
        assertEquals(3, split.editions.size)
        assertNotEquals(first.id, split.editions.last().id)
        assertEquals(1, merged.editions.size)
        assertEquals(first.id, merged.defaultEditionId)
        assertEquals(first.id, manager.setDefaultEdition(state, first.id).defaultEditionId)
    }
}