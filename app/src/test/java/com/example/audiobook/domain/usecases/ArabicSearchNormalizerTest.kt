package com.example.audiobook.domain.usecases

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicSearchNormalizerTest {
    @Test
    fun normalizesSpacingAlefAndTaaMarbuta() {
        assertEquals("ماوراءالطبيعه", ArabicSearchNormalizer.normalize("مَا وراءَ الطَّبيعة"))
        assertTrue(ArabicSearchNormalizer.matches("ماوراء الطبيعه", "ما وراء الطبيعة"))
    }

    @Test
    fun treatsYaAndAlefMaqsuraAsEquivalent() {
        assertTrue(ArabicSearchNormalizer.matches("على", "علي"))
        assertTrue(ArabicSearchNormalizer.matches("إيمان", "ايمان"))
    }
}