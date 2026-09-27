package com.example.audiobook.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryFiltersTest {

    @Test
    fun defaultQueryHasNoActiveFilters() {
        assertEquals(0, LibraryQuery().activeFilterCount)
    }

    @Test
    fun nonDefaultSortCountsAsOne() {
        assertEquals(1, LibraryQuery(sort = LibrarySort.ADDED_DATE).activeFilterCount)
        assertEquals(1, LibraryQuery(sort = LibrarySort.LAST_PLAYED).activeFilterCount)
        assertEquals(1, LibraryQuery(sort = LibrarySort.PROGRESS).activeFilterCount)
    }

    @Test
    fun seriesAndStatusTogetherCountAsTwo() {
        val q = LibraryQuery(status = LibraryStatusFilter.FINISHED, series = "فانتازيا")
        assertEquals(2, q.activeFilterCount)
    }

    @Test
    fun everyNonDefaultDimensionIsCounted() {
        val q = LibraryQuery(
            sort = LibrarySort.PROGRESS,
            status = LibraryStatusFilter.IN_PROGRESS,
            genre = "رواية",
            series = "paranormal"
        )
        assertEquals(4, q.activeFilterCount)
    }

    @Test
    fun searchIsNotCountedAsAFilter() {
        assertEquals(0, LibraryQuery(search = "عالم").activeFilterCount)
        assertEquals(1, LibraryQuery(search = "عالم", status = LibraryStatusFilter.NOT_STARTED).activeFilterCount)
    }

    @Test
    fun clearedFiltersResetsEveryDimensionButKeepsSearch() {
        val q = LibraryQuery(
            search = "خالد",
            sort = LibrarySort.PROGRESS,
            status = LibraryStatusFilter.FINISHED,
            genre = "رواية",
            series = "paranormal"
        )
        val cleared = q.clearedFilters()
        assertEquals(0, cleared.activeFilterCount)
        assertEquals("خالد", cleared.search)
        assertEquals(LibrarySort.NAME, cleared.sort)
        assertEquals(LibraryStatusFilter.ALL, cleared.status)
        assertNull(cleared.genre)
        assertNull(cleared.series)
    }

    @Test
    fun clearingAnAlreadyDefaultQueryIsANoOp() {
        val q = LibraryQuery(search = "abc")
        assertEquals(q, q.clearedFilters())
    }

    @Test
    fun recentlyAddedLimitIsTheSixBooksFromTheOldHardcode() {
        assertEquals(6, LibraryViewModel.RECENTLY_ADDED_LIMIT)
    }

    @Test
    fun everySortAndStatusOptionHasALabelResource() {
        LibrarySort.entries.forEach { assertEquals(true, it.labelRes != 0) }
        LibraryStatusFilter.entries.forEach { assertEquals(true, it.labelRes != 0) }
    }
}
