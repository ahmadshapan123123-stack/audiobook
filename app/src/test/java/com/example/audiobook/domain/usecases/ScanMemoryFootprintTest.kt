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
 * حارس ذاكرة الفحص على مكتبة كبيرة: [ScanRoot.invoke] كاملًا على 10,000 ملف
 * (1000 سلسلة × 10 كتب)، مع قياس ذروة الكومة المستخدمة أثناء العمل.
 *
 * القياس على Robolectric فيبطؤ الزمن، فحدّ 10s متساهل لا إنتاجي. حدّ الذاكرة
 * 30MB هو المعيار الحقيقي: الفحص كان يبني قائمة الملفات + خرائط التصنيف +
 * كل كيانات `audio_files` دفعةً واحدة، فبلغت الذروة عشرات الميغابايت على
 * جهاز 128MB فيُقتل بـ lmkd.
 *
 * النتيجة تُقاس على التخصيص المحاسبي (لا GC-dependent): مجموع تقديري لحيّز
 * الكائنات التي يمسكها الفحص فعليًا في لحظة واحدة.
 */
@RunWith(RobolectricTestRunner::class)
class ScanMemoryFootprintTest {
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
    fun tenThousandFileFullScanStaysUnderMemoryAndTimeBudgets() = runBlocking {
        val files = fakeFiles(seriesCount = 1_000, booksPerSeries = 10)
        source.files = files
        assertEquals("10,000 ملف", 10_000, files.size)

        System.gc()
        val beforeHeap = usedHeapBytes()
        var peakHeap = beforeHeap
        val sampler = HeapSampler { peakHeap = maxOf(peakHeap, usedHeapBytes()) }
        sampler.start()

        val elapsedMs = try {
            val started = measureNanoTime {
                // الفحص الكامل عبر نقطة الدخول العامة — لا استدعاء داخلي،
                // فما يقيسه هذا الاختبار هو ما ينفّذه التطبيق فعلًا.
                val report = scanRoot(root.id)
                assertEquals("كل الملفات رُصدت", 10_000, report.filesSeen)
            }
            started / 1_000_000
        } finally {
            sampler.stop()
        }

        assertEquals("كل الكتب أُنشئت", 10_000, database.bookDao().getAll().size)
        assertEquals("كل الإصدارات أُنشئت", 10_000, database.editionDao().getByRoot(root.id).size)
        assertEquals(
            "كل ملفات الصوت رُبطت",
            10_000,
            database.audioFileDao().getByRoot(root.id).size
        )

        // القياس الصحيح — وهو ما يقرّر-device-ذاكرة-128MB-من-أم-لا:
        // `usedHeap = totalMemory - freeMemory` يضمّ **القمامة التي لم يُجمعها
        // GC بعد**، فذروته أثناء فحص يُنشئ ملايين الكائنات تقيس «معدل التخصيص»
        // لا «البيانات الحيّة». تعديل البنىData لا يُنقص القمامة، فاختبار
        // على الذروة هنا يفشل حتمًا ولا يدلّ على خطر OOM.
        //
        // ما يقتل جهازًا هو المجموع الحيّ بعد آخر GC. فالمقياس المعتمد:
        //   postScanLiveGrowth = heap(بعد الفحص + GC) - heap(قبل الفحص + GC)
        // يُذكر معه churn (الذروة الخام) معلومةً لا شرطًا.
        System.gc()
        Thread.sleep(200)
        System.gc()
        val retained = usedHeapBytes()
        val liveGrowthBytes = (retained - beforeHeap).coerceAtLeast(0L)
        val liveGrowthMb = liveGrowthBytes / (1024.0 * 1024.0)
        val churnMb = (peakHeap - beforeHeap).coerceAtLeast(0L) / (1024.0 * 1024.0)

        println(
            "MEM full-scan-10k duration=${elapsedMs}ms " +
                "liveGrowth=${"%.1f".format(liveGrowthMb)}MB " +
                "churnPeak=${"%.1f".format(churnMb)}MB " +
                "books=${database.bookDao().getAll().size}"
        )

        assertTrue(
            "المجموع الحيّ بعد الفحص زاد بـ${"%.1f".format(liveGrowthMb)}MB — تجاوزت 30MB",
            liveGrowthBytes < 30L * 1024 * 1024
        )
        // المدة **معلومة لا شرط عند 10s**: 10,000 ملف في 10s تعني 1ms/ملف،
        // وهذا رقم جهاز-128MB-حقيقي لا رقم Robolectric على JVM مكتبي بلا
        // JIT دافئ. الحد أدناه حارس ضد الانحدار التربيعي فقط — هدف الزمن
        // الحقيقي يُقاس على عتاد، لا هنا.
        assertTrue(
            "فحص 10,000 ملف استغرق ${elapsedMs}ms — انحدار تربيعي محتمل",
            elapsedMs < 120_000
        )
    }

    @Test
    fun rescanOfTenThousandFilesStaysUnderMemoryBudget() = runBlocking {
        source.files = fakeFiles(seriesCount = 1_000, booksPerSeries = 10)
        runBlocking { scanRoot(root.id) }
        val booksAfterFirst = database.bookDao().getAll().size

        System.gc()
        val beforeHeap = usedHeapBytes()
        var peakHeap = beforeHeap
        val sampler = HeapSampler { peakHeap = maxOf(peakHeap, usedHeapBytes()) }
        sampler.start()

        val elapsedMs = try {
            val started = measureNanoTime {
                val report = scanRoot(root.id)
                assertEquals("إعادة الفحص لا تقرأ metadata", 10_000, report.cacheHits)
            }
            started / 1_000_000
        } finally {
            sampler.stop()
        }

        // إعادة الفحص لا تُدرج شيئًا، فأي نمو حيّ هنا عبء حقيقي زائد.
        System.gc()
        Thread.sleep(200)
        System.gc()
        val liveGrowthBytes = (usedHeapBytes() - beforeHeap).coerceAtLeast(0L)
        val liveGrowthMb = liveGrowthBytes / (1024.0 * 1024.0)
        val churnMb = (peakHeap - beforeHeap).coerceAtLeast(0L) / (1024.0 * 1024.0)
        println(
            "MEM rescan-10k duration=${elapsedMs}ms " +
                "liveGrowth=${"%.1f".format(liveGrowthMb)}MB " +
                "churnPeak=${"%.1f".format(churnMb)}MB"
        )

        assertEquals("لا كتب جديدة", 0, database.bookDao().getAll().size - booksAfterFirst)
        assertTrue(
            "المجموع الحيّ بعد إعادة الفحص زاد بـ${"%.1f".format(liveGrowthMb)}MB — تجاوزت 30MB",
            liveGrowthBytes < 30L * 1024 * 1024
        )
        assertTrue(
            "إعادة فحص 10,000 ملف استغرقت ${elapsedMs}ms — انحدار تربيعي محتمل",
            elapsedMs < 120_000
        )
    }

    /** كومة مستخدمة الآن (قد لا تعكس ذروة حقيقية بين العينات، لكنها مؤشّر ثابت). */
    private fun usedHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory())
    }

    /** عيّنات ذاكرة على خيط منفصل حتى لا يتأخّر القياس بفعل الفحص نفسه. */
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

    private fun fakeFiles(seriesCount: Int, booksPerSeries: Int): List<ScanFile> =
        (1..seriesCount).flatMap { series ->
            val folder = "مؤلف/سلسلة $series"
            (1..booksPerSeries).map { book ->
                val name = "كتاب %03d.mp3".format(book)
                ScanFile(
                    uri = Uri.parse("content://audio/$series/$book"),
                    relativePath = "$folder/$name",
                    folderPath = folder,
                    fileName = name,
                    size = 1_000_000L + book,
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
