package com.example.audiobook.domain.model

import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.preferences.AppSettings
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * PART 10 + PART 12: حدود حجم الملف ووضع الفحص.
 *
 * الهدف من-protection: `MediaMetadataRetriever.setDataSource` على ملف M4B كبير
 * (حتى 4GB) يطلب ذاكرة أصلية خارج كومة Java، فينتج SIGABRT على أجهزة 128MB
 * ولا يمكن لأي catch في Java أن يمنعه. لذلك يُتخطّى الملف قبل setDataSource.
 */
@RunWith(RobolectricTestRunner::class)
class ScanModeSizeGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun normalModeSkipsOnlyAboveTheHardCap() {
        val mode = ScanMode.NORMAL
        assertTrue("ملف صغير يُقرأ", mode.allows(50L * 1024 * 1024))
        assertTrue("تحت 500MB يُقرأ", mode.allows(ScanMode.MAX_READABLE_BYTES - 1))
        assertFalse("فوق 500MB يُتخطّى", mode.allows(ScanMode.MAX_READABLE_BYTES))
        assertFalse("4GB يُتخطّى", mode.allows(4L * 1024 * 1024 * 1024))
    }

    @Test
    fun economyModeSkipsAboveOneHundredMegabytes() {
        val mode = ScanMode.ECONOMY
        assertTrue("50MB يُقرأ", mode.allows(50L * 1024 * 1024))
        assertTrue("تحت 100MB يُقرأ", mode.allows(ScanMode.ECONOMY_SKIP_BYTES - 1))
        assertFalse("200MB يُتخطّى", mode.allows(200L * 1024 * 1024))
    }

    @Test
    fun fastModeSkipsEveryFile() {
        val mode = ScanMode.FAST
        assertFalse("حتى ملف 1KB يُتخطّى", mode.allows(1024L))
        assertFalse("صفر بايت يُتخطّى", mode.allows(0L))
    }

    @Test
    fun unknownSizeIsAllowedSoProvidersWithoutSizeStillWork() {
        // SAF providers قد لا تُرجع SIZE؛ التخطّي على حجم غير معروف كان سيُسقط
        // ملفات كانت تُقرأ بنجاح قبل هذا الحارس (السريع يبقى تخطّيًا: وضعه
        // مقصودAnyway، لكن الحارس نفسه لا يفرضه على مجهول).
        assertTrue("حجم غير معروف (سالب) يُقرأ في العادي", ScanMode.NORMAL.allows(-1L))
    }

    @Test
    fun fromNameFallsBackToNormalForUnknownValues() {
        assertEquals(ScanMode.NORMAL, ScanMode.fromName(null))
        assertEquals(ScanMode.NORMAL, ScanMode.fromName("GARBAGE"))
        assertEquals(ScanMode.ECONOMY, ScanMode.fromName("ECONOMY"))
        assertEquals(ScanMode.FAST, ScanMode.fromName("FAST"))
    }

    @Test
    fun realReaderSkipsOversizedFileAndReturnsZeroDuration() {
        val settings = AppSettings(context)
        settings.setScanMode(ScanMode.NORMAL)
        val reader = OversizeAwareReader(settings)

        val metadata = reader.read(
            uri = Uri.parse("content://audio/huge.m4b"),
            fileName = "huge.m4b",
            sizeBytes = 4L * 1024 * 1024 * 1024
        )

        assertFalse("لم يُستدعَ setDataSource على ملف ضخم", reader.setDataSourceCalled)
        assertEquals("مدة صفر", 0L, metadata.durationMs)
    }

    @Test
    fun realReaderReadsNormalFile() {
        val settings = AppSettings(context)
        settings.setScanMode(ScanMode.NORMAL)
        val reader = OversizeAwareReader(settings)

        val metadata = reader.read(
            uri = Uri.parse("content://audio/small.mp3"),
            fileName = "small.mp3",
            sizeBytes = 5L * 1024 * 1024
        )

        assertTrue("الملف العادي يُقرأ", reader.setDataSourceCalled)
        assertEquals("مدة الملف", 1_800_000L, metadata.durationMs)
    }

    @Test
    fun fastModeSkipsEvenSmallFiles() {
        val settings = AppSettings(context)
        settings.setScanMode(ScanMode.FAST)
        val reader = OversizeAwareReader(settings)

        val metadata = reader.read(
            uri = Uri.parse("content://audio/small.mp3"),
            fileName = "small.mp3",
            sizeBytes = 5L * 1024 * 1024
        )

        assertFalse("السريع لا يقرأ أي ملف", reader.setDataSourceCalled)
        assertEquals(0L, metadata.durationMs)
    }

    /**
     * قارئ يحاكي منطق [com.example.audiobook.data.localfilesystem.MediaAudioMetadataReader]
     * بلا `MediaMetadataRetriever` أصلي (لا يعمل على محاكي Robolectric).
     */
    private class OversizeAwareReader(settings: AppSettings) : AudioMetadataReader {
        private val appSettings = settings
        var setDataSourceCalled = false
            private set

        override fun read(uri: Uri, fileName: String): AudioMetadata = read(uri, fileName, null)

        override fun read(uri: Uri, fileName: String, sizeBytes: Long?): AudioMetadata {
            val size = sizeBytes ?: -1L
            if (!appSettings.currentScanMode().allows(size)) return empty()
            setDataSourceCalled = true
            return AudioMetadata(1_800_000L, "audio/mp3", null, null, null, emptyList())
        }

        private fun empty() = AudioMetadata(
            0L, "application/octet-stream", null, null, null, emptyList()
        )
    }
}
