package com.example.audiobook.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryQueryTest {
    @Test
    fun sortOptionsAndStatusFiltersAreRepresented() {
        assertEquals(listOf(LibrarySort.NAME, LibrarySort.ADDED_DATE, LibrarySort.LAST_PLAYED, LibrarySort.PROGRESS), LibrarySort.entries)
        assertEquals(listOf(LibraryStatusFilter.ALL, LibraryStatusFilter.IN_PROGRESS, LibraryStatusFilter.FINISHED, LibraryStatusFilter.NOT_STARTED), LibraryStatusFilter.entries)
    }
}