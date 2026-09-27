package com.example.audiobook.presentation.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** تنظيف عناوين العرض — للعرض فقط، بلا مساس بالهوية المخزَّنة. */
class DisplayLabelsTest {

    @Test
    fun stripsLeadingNumberAndDash() {
        assertEquals("رجل المستحيل", cleanDisplayTitle("01 - رجل المستحيل"))
        assertEquals("سافر", cleanDisplayTitle("007- سافر"))
        assertEquals("كتاب", cleanDisplayTitle("12_كتاب"))
    }

    @Test
    fun stripsExtensionsAndDownloadSuffixes() {
        assertEquals("عنوان", cleanDisplayTitle("عنوان.mp3"))
        assertEquals("عنوان", cleanDisplayTitle("عنوان.f100"))
        assertEquals("عنوان", cleanDisplayTitle("عنوان.f100.mp3"))
        assertEquals("عنوان", cleanDisplayTitle("عنوان.m4a"))
        assertEquals("عنوان", cleanDisplayTitle("عنوان.en"))
    }

    @Test
    fun underscoresBecomeSpaces() {
        assertEquals("The Book Of Gibran", cleanDisplayTitle("The_Book_Of_Gibran"))
    }

    @Test
    fun keepsPlainTitlesUntouched() {
        assertEquals("رجل المستحيل", cleanDisplayTitle("رجل المستحيل"))
        assertEquals("Book 01", cleanDisplayTitle("Book 01"))
        assertEquals("2020 في antigen", cleanDisplayTitle("2020 في antigen"))
    }

    @Test
    fun collapsesWhitespaceAndTrims() {
        assertEquals("عنوان الكتاب", cleanDisplayTitle("  عنوان   الكتاب  "))
        assertEquals("عنوان", cleanDisplayTitle("-- عنوان --"))
    }

    @Test
    fun neverReturnsBlankForNumericOnlyName() {
        // اسم كله أرقام: الترقيم البادئ يُزال فقط مع فاصل، فيبقى رقمًا صالحًا عنوانًا.
        assertEquals("01", cleanDisplayTitle("01"))
        assertEquals("2024", cleanDisplayTitle("2024"))
    }
}
