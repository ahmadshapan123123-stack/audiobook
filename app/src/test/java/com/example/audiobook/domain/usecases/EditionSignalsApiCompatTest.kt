package com.example.audiobook.domain.usecases

import com.example.audiobook.data.localfilesystem.AudioMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG 1 regression: كان EditionSignalExtractor يقرأ المجموعات عبر الاسم
 * (groups["suffix"] وما شابه) فيُرجَم إلى Matcher.start(String) وهو API 26+،
 * فينهار على API 23-25 أثناء الفحص. الحل: قراءة المجموعة الوحيدة بالفهرس 1.
 *
 * هذه الاختبارات تجري على SDK 23 و24 و25 نفسها وتتحقق من بقاء القيم منطقية
 * (لا استثناء + قيم صحيحة) بعد التحويل من الوصول بالاسم إلى الوصول بالفهرس.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 24, 25])
class EditionSignalsApiCompatTest {

    @Test
    fun extractSeriesPart_readsLatinBookPartsByIndex() {
        assertEquals(SeriesPart("book", 3), EditionSignalExtractor.extractSeriesPart("Book 3"))
        assertEquals(SeriesPart("book", 10), EditionSignalExtractor.extractSeriesPart("Part 10"))
        assertEquals(SeriesPart("book", 1), EditionSignalExtractor.extractSeriesPart("Volume 1"))
    }

    @Test
    fun extractSeriesPart_readsArabicOrdinalPartsByIndex() {
        assertEquals(SeriesPart("juz", 2), EditionSignalExtractor.extractSeriesPart("الجزء الثاني"))
        assertEquals(SeriesPart("juz", 5), EditionSignalExtractor.extractSeriesPart("جزء الخامس"))
        assertEquals(SeriesPart("kitab", 1), EditionSignalExtractor.extractSeriesPart("الكتاب الأول"))
        assertEquals(SeriesPart("numbered", 7), EditionSignalExtractor.extractSeriesPart("فصل - 7"))
    }

    @Test
    fun extractSeriesPart_readsArabicDigitPartsByIndex() {
        assertEquals(SeriesPart("juz", 3), EditionSignalExtractor.extractSeriesPart("الجزء ٣"))
        assertEquals(SeriesPart("book", 4), EditionSignalExtractor.extractSeriesPart("Book ٤"))
    }

    @Test
    fun extractSeriesPart_returnsNullWhenAbsent() {
        assertNull(EditionSignalExtractor.extractSeriesPart("فانتازيا"))
        assertNull(EditionSignalExtractor.extractSeriesPart(""))
        assertNull(EditionSignalExtractor.extractSeriesPart("   "))
    }

    @Test
    fun extractNarratorFromName_readsNarratorByIndex() {
        assertEquals("فلان", EditionSignalExtractor.extractNarratorFromName("الكتاب برواية فلان"))
        assertEquals("Ali Hassan", EditionSignalExtractor.extractNarratorFromName("Book narrated by Ali Hassan"))
        assertEquals("خالد", EditionSignalExtractor.extractNarratorFromName("كتاب قراءة الأستاذ خالد"))
        assertEquals("سعاد", EditionSignalExtractor.extractNarratorFromName("قصص روى سعاد"))
        assertNull(EditionSignalExtractor.extractNarratorFromName("لا راوي هنا"))
    }

    @Test
    fun detectFileOrder_readsTrailingNumbersByIndex() {
        assertTrue(EditionSignalExtractor.detectFileOrder(listOf("Chapter 01.mp3", "Chapter 02.mp3", "Chapter 03.mp3")))
        assertFalse(EditionSignalExtractor.detectFileOrder(listOf("Chapter 01.mp3", "Chapter 03.mp3", "Chapter 02.mp3")))
        assertFalse(EditionSignalExtractor.detectFileOrder(emptyList()))
    }

    @Test
    fun build_producesSameSignalsAfterRefactor() {
        val metadata = AudioMetadata(1_800_000L, "audio/mp4", null, "فلان", "سيرة", emptyList())
        val signals = EditionSignalExtractor.build(
            folderName = "Book 3",
            authorFolderName = "Author",
            fileNames = listOf("Part 1.m4b", "Part 2.m4b"),
            metadataList = listOf(metadata, metadata),
            seriesFolderName = null
        )
        assertNotNull(signals)
        assertEquals(SeriesPart("book", 3), signals.seriesPart)
        assertEquals("فلان", signals.narrator)
        assertEquals("MP3", EditionSignalExtractor.formatLabelFor("part.mp3"))
    }
}