package com.example.audiobook.presentation.library

enum class LibrarySort { NAME, ADDED_DATE, LAST_PLAYED, PROGRESS }
enum class LibraryStatusFilter { ALL, IN_PROGRESS, FINISHED, NOT_STARTED }

data class LibraryQuery(
    val search: String = "",
    val sort: LibrarySort = LibrarySort.NAME,
    val status: LibraryStatusFilter = LibraryStatusFilter.ALL,
    val genre: String? = null,
    val series: String? = null
)