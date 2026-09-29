package com.example.audiobook.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PHASE B7 — مولّدات largeIcon: أبعاد صحيحة وكائنات صالحة.
 * (ملاحظة: ظلال Robolectric لا تُنقّط عمليات Canvas، ففحص البكسلات
 * المضبوطة يُترك للجهاز — هنا العقد البنيوي فقط.)
 */
@RunWith(RobolectricTestRunner::class)
class NotificationLargeIconsTest {

    @Test
    fun letterArtworkIsValid128Square() {
        val bmp = NotificationLargeIcons.letterArtwork("A", 0xFF131A38.toInt())
        assertEquals(128, bmp.width)
        assertEquals(128, bmp.height)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun letterArtworkHandlesBlankLetter() {
        val bmp = NotificationLargeIcons.letterArtwork("   ", 0xFF131A38.toInt())
        assertNotNull(bmp)
        assertEquals(128, bmp.width)
    }

    @Test
    fun moonArtworkIsValid128Square() {
        val bmp = NotificationLargeIcons.moonArtwork()
        assertEquals(128, bmp.width)
        assertEquals(128, bmp.height)
        assertFalse(bmp.isRecycled)
    }

    @Test
    fun vectorArtworkRendersScanGlyph() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bmp = NotificationLargeIcons.vectorArtwork(context, R.drawable.ic_scan, 0xFF131A38.toInt())
        assertNotNull(bmp)
        assertEquals(128, bmp.width)
        assertEquals(128, bmp.height)
    }
}
