package com.example.audiobook.domain.usecases

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.system.measureNanoTime

/**
 * STAGE 1H — إثبات إصلاح الذاكرة على 30,000 ملف.
 *
 * يقيس اختباران:
 * 1) `legacySortedCopyCost`: تكلفة سطر Phase-11.5 القديم
 *    `files.withIndex().sortedBy { … }` وحده على نفس الملفات — نسخة كاملة
 *    + تغليف IndexedValue لكل ملف. هذا ما أزالته STAGE 1D.
 * 2) `streamingScan`: الفحص الكامل الجديد (بثّ + تحرير الأطوار) — يجب أن
 *    يبقى النموّ الحيّ بعد الفحص تحت 20MB.
 *
 * ملاحظة صدق: رقم «قبل» الكامل لخط الأنابيب القديم لا يمكن قياسه بعد تغيير
 * الكود، فالوكيل المستخدم هو تكلفة النسخة المرتبة الإضافية (وهي بالضبط ما
 * حُذف) + أرقام churn المسجلة سابقًا على 10k (full=86.2MB/rescan=92.4MB).
 */
@RunWith(RobolectricTestRunner::class)
class ScanStreamingMemoryTest {
    private lateinit var database: AppDatabase
    private lateinit var appSettings: AppSettings
    private lateinit var source: StaticFileSource
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        appSettings = AppSettings(context)
        source = StaticFileSource()
        scanRoot = ScanRoot(database, source, ConstantMetadataReader(), appSettings, EditionMerge(database))
        root = LibraryRootEntity(
            uri = "content://library",
            displayName = "Library",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun legacySortedCopyCostOnThirtyThousandFiles() {
        val files = fakeFiles(folderCount = 3_000, filesPerFolder = 10)
        assertEquals("30,000 ملف", 30_000, files.size)
        source.files = files

        System.gc()
        Thread.sleep(200)
        val baseHeap = usedHeapBytes()
        // السطر القديم حرفيًا: نسخة القائمة + IndexedValue لكل ملف.
        val legacyCopy = files.withIndex().sortedBy { it.value.folderPath }
        assertEquals("النسخة بنفس الحجم", 30_000, legacyCopy.size)
        val copyCostBytes = (usedHeapBytes() - baseHeap).coerceAtLeast(0L)

        // تُحرَّر النسخة فورًا كما لم تكن — القياس أعلاه هو تكلفتها الصافية.
        println(
            "MEM legacy-30k sortedCopyExtra=${"%.1f".format(copyCostBytes / (1024.0 * 1024.0))}MB " +
                "(eliminated by STAGE 1D: index-sort keeps one int list)"
        )
    }

    @Test
    fun streamingScanOfThirtyThousandFilesStaysUnderTwentyMb() = runBlocking {
        source.files = fakeFiles(folderCount = 3_000, filesPerFolder = 10)

        System.gc()
        Thread.sleep(200)
        val beforeHeap = usedHeapBytes()
        var peakHeap = beforeHeap
        val sampler = HeapSampler { peakHeap = maxOf(peakHeap, usedHeapBytes()) }
        sampler.start()

        val elapsedMs = try {
            measureNanoTime {
                val report = scanRoot(root.id)
                assertEquals("كل الملفات رُصدت", 30_000, report.filesSeen)
            } / 1_000_000
        } finally {
            sampler.stop()
        }

        assertEquals("كتاب لكل مجلد-ملف", 30_000, database.bookDao().getAll().size)

        System.gc()
        Thread.sleep(200)
        System.gc()
        val liveGrowthBytes = (usedHeapBytes() - beforeHeap).coerceAtLeast(0L)
        val liveGrowthMb = liveGrowthBytes / (1024.0 * 1024.0)
        val churnMb = (peakHeap - beforeHeap).coerceAtLeast(0L) / (1024.0 * 1024.0)
        println(
            "MEM stream-30k duration=${elapsedMs}ms " +
                "liveGrowth=${"%.1f".format(liveGrowthMb)}MB " +
                "churnPeak=${"%.1f".format(churnMb)}MB " +
                "books=${database.bookDao().getAll().size}"
        )
        assertTrue(
            "النموّ الحيّ بعد فحص 30,000 ملف ${"%.1f".format(liveGrowthMb)}MB — تجاوز 20MB",
            liveGrowthBytes < 20L * 1024 * 1024
        )
    }

    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory())
    }

    private class HeapSampler(private val onSample: () -> Unit) {
        private var running = false
        private var worker: Thread? = null

        fun start() {
            running = true
            worker = Thread {
                while (running) {
                    onSample()
                    try {
                        Thread.sleep(15)
                    } catch (interrupted: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return@Thread
                    }
                }
                onSample()
            }.apply {
                isDaemon = true
                name = "heap-sampler"
                start()
            }
        }

        fun stop() {
            running = false
            worker?.interrupt()
            worker = null
        }
    }

    private fun fakeFiles(folderCount: Int, filesPerFolder: Int): List<ScanFile> =
        (1..folderCount).flatMap { folder ->
            val path = "مؤلف/سلسلة $folder"
            (1..filesPerFolder).map { file ->
                val name = "كتاب %03d.mp3".format(file)
                ScanFile(
                    uri = Uri.parse("content://audio/$folder/$file"),
                    relativePath = "$path/$name",
                    folderPath = path,
                    fileName = name,
                    size = 1_000_000L + file,
                    lastModified = 10L
                )
            }
        }

    private class StaticFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class ConstantMetadataReader : AudioMetadataReader {
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            AudioMetadata(1_800_000L, "audio/mp3", null, null, null, emptyList())
    }
}
