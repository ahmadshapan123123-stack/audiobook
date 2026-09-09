package com.example.audiobook.playback

import com.example.audiobook.data.room.entity.ChapterCompletionEntity
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.ChapterEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * [R4-النقطة 2] اختبارات مراقب اكتمال الفصل بمعزل عن ExoPlayer:
 * كل تحديث موضع يفحص الفصل الحالي، والتسجيل يحدث مرة واحدة فقط عند 90%.
 */
class ChapterCompletionObserverTest {

    private val edition = UUID.randomUUID()

    @Test
    fun recordsChapterWhenCrossingExactlyNinetyPercentAndNeverRepeats() = runBlocking {
        val chapterA = UUID.randomUUID()
        val chapterB = UUID.randomUUID()
        val chapters = listOf(
            chapter(id = chapterA, start = 0L),
            chapter(id = chapterB, start = 10_000L)
        )
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 100L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 8_999L, chapters = chapters, editionEndMs = 20_000L)
        assertEquals("قبل 90% لا تسجيل", 0, written.size)

        observer.onPositionUpdate(edition, positionMs = 9_000L, chapters = chapters, editionEndMs = 20_000L)
        observer.onPositionUpdate(edition, positionMs = 9_999L, chapters = chapters, editionEndMs = 20_000L)
        assertEquals("أول فصل سُجّل مرة واحدة فقط", 1, written.size)
        assertEquals(chapterA, written.single().chapterId)

        observer.onPositionUpdate(edition, positionMs = 19_000L, chapters = chapters, editionEndMs = 20_000L)
        observer.onPositionUpdate(edition, positionMs = 19_999L, chapters = chapters, editionEndMs = 20_000L)
        assertEquals("الفصل الثاني سُجّل أيضًا، بلا تكرار", 2, written.size)
        assertEquals(listOf(chapterA, chapterB), written.map { it.chapterId })
    }

    @Test
    fun atEightyNinePercentNothingIsRecorded() = runBlocking {
        val chapters = listOf(chapter(id = UUID.randomUUID(), start = 0L))
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 100L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 8_900L, chapters = chapters, editionEndMs = 10_000L)
        observer.onPositionUpdate(edition, positionMs = 8_999L, chapters = chapters, editionEndMs = 10_000L)

        assertEquals("89% فقط لا يُسجَّل كإكمال", 0, written.size)
    }

    @Test
    fun lastChapterCompletesWhenReachingEditionEnd() = runBlocking {
        val chapterA = UUID.randomUUID()
        val chapterB = UUID.randomUUID()
        val chapters = listOf(
            chapter(id = chapterA, start = 0L),
            chapter(id = chapterB, start = 1_000L)
        )
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 200L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 950L, chapters = chapters, editionEndMs = 2_000L)
        observer.onPositionUpdate(edition, positionMs = 1_920L, chapters = chapters, editionEndMs = 2_000L)
        observer.onPositionUpdate(edition, positionMs = 2_000L, chapters = chapters, editionEndMs = 2_000L)

        assertEquals(listOf(chapterA, chapterB), written.map { it.chapterId })
        assertTrue(written.all { it.completedAtMs == 200L })
    }

    @Test
    fun completionsAreTrackedPerEditionIndependently() = runBlocking {
        val otherEdition = UUID.randomUUID()
        val chapter1 = UUID.randomUUID()
        val chapter2 = UUID.randomUUID()
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 1L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 950L, chapters = listOf(chapter(id = chapter1, start = 0L)), editionEndMs = 1_000L)
        observer.onPositionUpdate(otherEdition, positionMs = 950L, chapters = listOf(chapter(id = chapter2, start = 0L)), editionEndMs = 1_000L)
        observer.onPositionUpdate(edition, positionMs = 999L, chapters = listOf(chapter(id = chapter1, start = 0L)), editionEndMs = 1_000L)

        assertEquals("فصل لكل نسخة، والنسخة الأولى لا تتكرر", 2, written.size)
        assertEquals(setOf(chapter1, chapter2), written.map { it.chapterId }.toSet())
    }

    @Test
    fun preludeBeforeFirstChapterBelongsToFirstChapter() = runBlocking {
        val chapterA = UUID.randomUUID()
        val chapters = listOf(chapter(id = chapterA, start = 1_000L), chapter(id = UUID.randomUUID(), start = 2_000L))
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 5L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 1_900L, chapters = chapters, editionEndMs = 3_000L)

        assertEquals(1, written.size)
        assertEquals(chapterA, written.single().chapterId)
    }

    @Test
    fun noChaptersOrZeroDurationProducesNoWrites() = runBlocking {
        val written = mutableListOf<ChapterCompletionEntity>()
        val observer = ChapterCompletionObserver(clock = { 1L }, writer = { written.add(it) })

        observer.onPositionUpdate(edition, positionMs = 5_000L, chapters = emptyList(), editionEndMs = 10_000L)
        observer.onPositionUpdate(edition, positionMs = 5_000L, chapters = listOf(chapter(id = UUID.randomUUID(), start = 0L)), editionEndMs = 0L)

        assertEquals(0, written.size)
    }

    private fun chapter(id: UUID, start: Long) = ChapterEntity(
        id = id,
        editionId = edition,
        title = "فصل",
        startPositionMs = start,
        orderIndex = 0,
        createdFrom = ChapterCreatedFrom.AUTO_SPLIT
    )
}