package com.example.audiobook.data

import android.net.Uri
import com.example.audiobook.data.localfilesystem.StorageAccess
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * اسم العرض لجذر SAF (Phase 2 / FIX A) — مصدر واحد لكل الشاشات.
 *
 * المطلوب: من `primary:المكتبة الصوتية/أحمد خالد توفيق` يُعرض «المكتبة الصوتية»
 * لا المسار الكامل. فنقتطع أول مقطع بعد إسقاط بادئة الحجم.
 *
 * ملاحظة: شجرة SAF تُرسِل المسار الفرعي مُرمَّزًا `%2F` داخل معرّف المستند، فآخر
 * مقطع في الـURI يحمل الشرطات كلها بعد فكّ الترميز — وهو ما يجعل `substringAfter(':')`
 * وحده يُظهر «المكتبة الصوتية/أحمد خالد توفيق».
 */
@RunWith(RobolectricTestRunner::class)
class StorageAccessDisplayNameTest {

    private fun display(vararg pathSegments: String): String =
        StorageAccess.displayNameOf(treeUri(pathSegments.joinToString("%2F")))

    private fun treeUri(encodedDocumentId: String): Uri =
        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$encodedDocumentId")

    @Test
    fun rootFolderWithNestedAuthorPathShowsRootFolderOnly() {
        assertEquals("المكتبة الصوتية", display("المكتبة الصوتية", "أحمد خالد توفيق"))
    }

    @Test
    fun plainRootFolderShowsItsOwnName() {
        assertEquals("المكتبة الصوتية", display("المكتبة الصوتية"))
    }

    @Test
    fun volumePrefixIsAlwaysStripped() {
        assertEquals("Audiobooks", StorageAccess.displayNameOf(Uri.parse("content://tree/primary%3AAudiobooks")))
        assertEquals("Audiobooks", display("Audiobooks", "Author", "Series", "Book"))
    }

    @Test
    fun deepPathStopsAtFirstSegment() {
        assertEquals("Library", display("Library", "Author", "Series", "Book", "chapter1"))
    }

    @Test
    fun blankDocumentIdFallsBackToFullUri() {
        val uri = treeUri("")
        assertEquals(uri.toString(), StorageAccess.displayNameOf(uri))
    }
}
