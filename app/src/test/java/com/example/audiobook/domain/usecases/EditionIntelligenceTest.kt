package com.example.audiobook.domain.usecases

import com.example.audiobook.data.room.entity.UserDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditionIntelligenceTest {

    // ---- P1: استخراج الإشارات فعليًا (دوال نقية) ----

    @Test
    fun extractSeriesPart_detectsEnglishBookNumber() {
        val part = EditionSignalExtractor.extractSeriesPart("The Name - Book 3")
        assertEquals("book", part?.pattern)
        assertEquals(3, part?.partNumber)
    }

    @Test
    fun extractSeriesPart_detectsArabicJuzWithEasternDigits() {
        val part = EditionSignalExtractor.extractSeriesPart("الاسم - الجزء ٣")
        assertEquals("juz", part?.pattern)
        assertEquals(3, part?.partNumber)
    }

    @Test
    fun extractSeriesPart_detectsArabicKitab() {
        val part = EditionSignalExtractor.extractSeriesPart("الكتاب الثاني")
        assertEquals("kitab", part?.pattern)
        assertEquals(2, part?.partNumber)
    }

    @Test
    fun extractSeriesPart_noSeriesReturnsNull() {
        assertNull(EditionSignalExtractor.extractSeriesPart("A Single Novel"))
    }

    @Test
    fun extractNarrator_detectsNarratedBy() {
        assertEquals("Fulan Narrator", EditionSignalExtractor.extractNarratorFromName("The Book - narrated by Fulan Narrator"))
    }

    @Test
    fun extractNarrator_detectsArabicQiraa() {
        assertEquals("فلان الراوي", EditionSignalExtractor.extractNarratorFromName("رواية - قراءة: فلان الراوي"))
    }

    @Test
    fun extractNarrator_detectsArabicRawi() {
        assertEquals("فلان", EditionSignalExtractor.extractNarratorFromName("الاسم - روى فلان"))
    }

    @Test
    fun detectFileOrder_trueForNumberedParts() {
        assertTrue(EditionSignalExtractor.detectFileOrder(listOf("Book - 1.mp3", "Book - 2.mp3", "Book - 3.mp3")))
    }

    @Test
    fun detectFileOrder_falseForSingleFile() {
        assertFalse(EditionSignalExtractor.detectFileOrder(listOf("Book.mp3")))
    }

    // ---- P2: Confidence Score حقيقية (وليست الثابت 1f) ----

    @Test
    fun calculateConfidence_returnsRealValuesNotConstant() {
        val empty = EditionSignals()
        val weak = EditionSignals(folderName = "Folder", fileCount = 1, totalDurationMs = 1000)
        val strong = EditionSignals(
            folderName = "اسم الكتاب الحقيقي",
            embeddedTags = EmbeddedTags("اسم الكتاب الحقيقي", "فلان الراوي", "رواية"),
            narrator = "فلان الراوي",
            totalDurationMs = 3_600_000L,
            fileCount = 1,
            authorFolderName = "فلان المؤلف"
        )
        assertEquals(0f, EditionIntelligence.calculateConfidence(empty), 0.0001f)
        assertTrue("الضبط الذاتي للإشارات الضعيفة يجب أن يكون أقل من 1", EditionIntelligence.calculateConfidence(weak) < 1f)
        assertTrue("الإشارات القوية يجب أن ترفع الثقة", EditionIntelligence.calculateConfidence(strong) > EditionIntelligence.calculateConfidence(weak))
        assertEquals(0.90f, EditionIntelligence.calculateConfidence(strong), 0.0001f)
    }

    // ---- P4 [النقطة الأهم]: القيد الصارم - راويان مختلفان → false في المستويات الثلاثة ----

    @Test
    fun canAutoMerge_returnsFalseForClearlyDifferentNarratorsInAllThreeLevels() {
        val subject = EditionSignals(
            folderName = "Book One",
            embeddedTags = EmbeddedTags("Book One", "Ali", "رواية"),
            narrator = "Ali",
            totalDurationMs = 3_000_000L,
            fileCount = 5,
            filesOrdered = true,
            format = "M4B"
        )
        val candidate = EditionSignals(
            folderName = "Book One",
            embeddedTags = EmbeddedTags("Book One", "Omar", "رواية"),
            narrator = "Omar",
            totalDurationMs = 3_000_000L,
            fileCount = 5,
            filesOrdered = true,
            format = "M4B"
        )
        assertTrue("الحالتان متطابقتان تمامًا باستثناء الراوي — كل الإشارات الأخرى متوافقة",
            EditionIntelligence.narratorsClearlyDistinct(subject.narrator, candidate.narrator))
        assertTrue("مطابقة العنوان/المدة/الصيغة كلها متوافقة", EditionIntelligence.mergeConfidence(subject, candidate) >= 0.60f)
        assertFalse(EditionIntelligence.canAutoMerge(subject, candidate, IntelligenceLevel.CONSERVATIVE))
        assertFalse(EditionIntelligence.canAutoMerge(subject, candidate, IntelligenceLevel.BALANCED))
        assertFalse(EditionIntelligence.canAutoMerge(subject, candidate, IntelligenceLevel.AGGRESSIVE))
    }

    @Test
    fun canAutoMerge_sameNarratorNotBlockedByStrictRule() {
        val subject = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 1_000_000L, fileCount = 1, format = "MP3")
        val candidate = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 1_000_000L, fileCount = 1, format = "MP3")
        assertTrue(EditionIntelligence.canAutoMerge(subject, candidate, IntelligenceLevel.BALANCED))
    }

    @Test
    fun canAutoMerge_durationDifferenceAbove15PercentBlocksAllLevels() {
        val subject = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 10_000_000L, fileCount = 1, format = "MP3")
        val longer = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 12_000_000L, fileCount = 1, format = "MP3")
        assertTrue(EditionIntelligence.durationDelta(10_000_000L, 12_000_000L) > 0.15f)
        assertTrue("باقي الإشارات تصل بالثقة إلى عتبة Balanced لو نُظر إليها وحدها",
            EditionIntelligence.mergeConfidence(subject, longer) >= EditionIntelligence.BALANCED_AUTO_MERGE_THRESHOLD)
        assertFalse(EditionIntelligence.canAutoMerge(subject, longer, IntelligenceLevel.CONSERVATIVE))
        assertFalse(EditionIntelligence.canAutoMerge(subject, longer, IntelligenceLevel.BALANCED))
        assertFalse(EditionIntelligence.canAutoMerge(subject, longer, IntelligenceLevel.AGGRESSIVE))
    }

    @Test
    fun canAutoMerge_durationDifferenceWithin15PercentMergeableInBalanced() {
        val a = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 10_000_000L, fileCount = 1, format = "MP3")
        val b = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 10_800_000L, fileCount = 1, format = "MP3")
        assertTrue(EditionIntelligence.durationDelta(10_000_000L, 10_800_000L) <= 0.15f)
        assertTrue(EditionIntelligence.canAutoMerge(a, b, IntelligenceLevel.BALANCED))
    }

    @Test
    fun canAutoMerge_conservativeNeverMergesEvenIdenticalSignals() {
        val signals = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 1_000_000L, fileCount = 1)
        assertFalse(EditionIntelligence.canAutoMerge(signals, signals.copy(), IntelligenceLevel.CONSERVATIVE))
    }

    @Test
    fun canAutoMerge_aggressiveNeverSilentlyMergesEvenIdenticalSignals() {
        val signals = EditionSignals(folderName = "Book", embeddedTags = EmbeddedTags("Book", "Ali", null), narrator = "Ali", totalDurationMs = 1_000_000L, fileCount = 1)
        assertEquals(0.90f, EditionIntelligence.mergeConfidence(signals, signals.copy()), 0.0001f)
        assertFalse("Aggressive يُعرض للمراجعة ولا يدمج صامتًا أبدًا", EditionIntelligence.canAutoMerge(signals, signals.copy(), IntelligenceLevel.AGGRESSIVE))
    }

    @Test
    fun canAutoMerge_balancedDeclinesWeakSignals() {
        val weakA = EditionSignals(folderName = "X", totalDurationMs = 1_000_000L)
        val weakB = EditionSignals(folderName = "X", totalDurationMs = 1_000_000L)
        assertTrue(EditionIntelligence.mergeConfidence(weakA, weakB) < EditionIntelligence.BALANCED_AUTO_MERGE_THRESHOLD)
        assertFalse(EditionIntelligence.canAutoMerge(weakA, weakB, IntelligenceLevel.BALANCED))
    }

    // ---- P5: تعديل الأوزان من القرارات السابقة (قابل للتحقق) ----

    @Test
    fun confirmationBoost_increasesWithSamePatternConfirmations() {
        val subject = EditionSignals(folderName = "الجزء 3", seriesPart = SeriesPart("juz", 3), format = "M4B")
        val candidate = EditionSignals(folderName = "الجزء 3", seriesPart = SeriesPart("juz", 3), format = "M4B")
        val key = EditionIntelligence.pairPatternKey(subject, candidate)

        val none = EditionIntelligence.confirmationBoost(subject, candidate, emptyList())
        val one = EditionIntelligence.confirmationBoost(subject, candidate, listOf(PatternConfirmation(key, UserDecision.SAME_EDITION)))
        val three = EditionIntelligence.confirmationBoost(
            subject,
            candidate,
            listOf(
                PatternConfirmation(key, UserDecision.SAME_EDITION),
                PatternConfirmation(key, UserDecision.SAME_EDITION),
                PatternConfirmation(key, UserDecision.SAME_EDITION)
            )
        )
        assertEquals(1f, none, 0.0001f)
        assertEquals(1.05f, one, 0.0001f)
        assertEquals(1.15f, three, 0.0001f)
    }

    @Test
    fun confirmationBoost_ignoresOtherPatternsAndNegativeDecisions() {
        val subject = EditionSignals(folderName = "A", format = "MP3")
        val candidate = EditionSignals(folderName = "A", format = "MP3")
        val subjectB = EditionSignals(folderName = "B", format = "MP3")
        val candidateB = EditionSignals(folderName = "B", format = "MP3")
        val keyA = EditionIntelligence.pairPatternKey(subject, candidate)
        val keyB = EditionIntelligence.pairPatternKey(subjectB, candidateB)

        val confirmations = listOf(
            PatternConfirmation(keyB, UserDecision.SAME_EDITION),
            PatternConfirmation(keyA, UserDecision.DIFFERENT_EDITION),
            PatternConfirmation(keyA, UserDecision.NOT_SAME_BOOK)
        )
        assertEquals(1f, EditionIntelligence.confirmationBoost(subject, candidate, confirmations), 0.0001f)
    }

    @Test
    fun adjustedConfidence_isCappedAndMultiplied() {
        assertEquals(0.5f, EditionIntelligence.adjustedConfidence(0.5f, 1f), 0.0001f)
        assertEquals(0.55f, EditionIntelligence.adjustedConfidence(0.5f, 1.1f), 0.0001f)
        assertEquals(1f, EditionIntelligence.adjustedConfidence(0.9f, 1.25f), 0.0001f)
    }
}