package com.example.audiobook.presentation.library

import com.example.audiobook.R

enum class LibrarySort(val labelRes: Int) {
    NAME(R.string.sort_name),
    ADDED_DATE(R.string.sort_added),
    LAST_PLAYED(R.string.sort_last_played),
    PROGRESS(R.string.sort_progress)
}

enum class LibraryStatusFilter(val labelRes: Int) {
    ALL(R.string.filter_status_all),
    IN_PROGRESS(R.string.filter_status_in_progress),
    FINISHED(R.string.filter_status_finished),
    NOT_STARTED(R.string.filter_status_not_started)
}

data class LibraryQuery(
    val search: String = "",
    val sort: LibrarySort = LibrarySort.NAME,
    val status: LibraryStatusFilter = LibraryStatusFilter.ALL,
    val genre: String? = null,
    val series: String? = null
)

/** قيمة افتراضية تُعتبر "غير مفعّلة"، فلا تُحتسب ضمن عدّاد الفلاتر النشطة. */
val DEFAULT_LIBRARY_SORT = LibrarySort.NAME
val DEFAULT_LIBRARY_STATUS = LibraryStatusFilter.ALL

/**
 * عدد أبعاد التصفية التي تختلف عن الافتراضي (يُعرض كشارة على زر التصفية).
 * البحث النصّي لا يُحتسب: له حقله الخاص وزر مسح مستقل.
 */
val LibraryQuery.activeFilterCount: Int
    get() = listOf(
        sort != DEFAULT_LIBRARY_SORT,
        status != DEFAULT_LIBRARY_STATUS,
        genre != null,
        series != null
    ).count { it }

/** يعيد كل بُعد تصفية إلى قيمته الافتراضية مع الحفاظ على نص البحث. */
fun LibraryQuery.clearedFilters(): LibraryQuery = copy(
    sort = DEFAULT_LIBRARY_SORT,
    status = DEFAULT_LIBRARY_STATUS,
    genre = null,
    series = null
)