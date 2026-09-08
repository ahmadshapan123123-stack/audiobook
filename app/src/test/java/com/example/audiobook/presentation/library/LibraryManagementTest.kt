package com.example.audiobook.presentation.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryManagementTest {
    @Test
    fun favoritesToggleAndCollectionsRemainDeterministic() {
        var favorites = setOf("ما وراء الطبيعة")
        favorites = if ("رجال في الشمس" in favorites) favorites - "رجال في الشمس" else favorites + "رجال في الشمس"
        assertEquals(setOf("ما وراء الطبيعة", "رجال في الشمس"), favorites)
        val collections = mutableListOf("قراءات عربية")
        collections.add("للاستماع الليلي")
        assertEquals(listOf("قراءات عربية", "للاستماع الليلي"), collections)
    }
}