package com.example.audiobook.domain.usecases

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.localfilesystem.AudioMetadata
import com.example.audiobook.data.localfilesystem.AudioMetadataReader
import com.example.audiobook.data.localfilesystem.LibraryFileSource
import com.example.audiobook.data.localfilesystem.ScanFile
import com.example.audiobook.data.preferences.ScanSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import com.example.audiobook.presentation.settings.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [R8-النقطة 4+6] تكامل حقيقي: اختيار المستخدم من واجهة الإعدادات (عبر
 * [SettingsViewModel]) يؤثر فعليًا على سلوك الفحص في [ScanRoot].
 *
 * بيانات تجريبية ذات ثقة مطابقة عالية جدًا (≥ عتبة Balanced):
 *  - Conservative (اختيار من الـViewModel): الفحص لا يدمج إطلاقًا رغم الثقة العالية.
 *  - Positive control: نفس البيانات بعد اختيار Balanced من الـViewModel تدمج تلقائيًا.
 * هذا يثبت أن القيمة ليست معروضة بلا أثر، وأن [ScanRoot] يقرأ القيمة المخزَّنة
 * الفعلية من [ScanSettings] (وليس افتراضية ثابتة) — [ScanRoot.kt:231].
 */
@RunWith(RobolectricTestRunner::class)
class SettingsChoiceDrivesScanIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var source: FakeFileSource
    private lateinit var reader: CountingMetadataReader
    private lateinit var scanSettings: ScanSettings
    private lateinit var viewModel: SettingsViewModel
    private lateinit var scanRoot: ScanRoot
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source = FakeFileSource()
        reader = CountingMetadataReader()
        scanSettings = ScanSettings(context)
        viewModel = SettingsViewModel(scanSettings)
        scanRoot = ScanRoot(database, source, reader, scanSettings, EditionMerge(database))
        root = LibraryRootEntity(uri = "content://library", displayName = "Library", isPriority = true, isEnabled = true, lastScanAt = null, scanStatus = ScanStatus.IDLE)
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() {
        database.close()
        scanSettings.setIntelligenceLevel(IntelligenceLevel.BALANCED)
    }

    private suspend fun allEditions(): List<EditionEntity> = database.editionDao().observeAll().first()

    @Test
    fun conservativeChoiceThroughSettingsViewModelBlocksHighConfidenceMergeThenBalancedMerges() = runBlocking {
        val level = viewModel.intelligenceLevel.value
        assertEquals(IntelligenceLevel.BALANCED, level)

        viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE)

        // P1/P3: القيمة المخزَّنة تنجو عبر كائن ScanSettings جديد (= إعادة تشغيل).
        assertEquals(IntelligenceLevel.CONSERVATIVE, ScanSettings(context).currentIntelligenceLevel())

        // إثبات أن البيانات تجريبيةً عالية الثقة فعلاً (كل القنوات تطابق):
        val signalA = EditionSignalExtractor.build("Book v1", root.displayName, listOf("Part 1.m4b"), listOf(AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Same Narrator", null, emptyList())))
        val signalB = EditionSignalExtractor.build("Book v2", root.displayName, listOf("Part 1.m4b"), listOf(AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Same Narrator", null, emptyList())))
        val highConfidence = EditionIntelligence.mergeConfidence(signalA, signalB)
        assertTrue("بيانات تجريبية عالية الثقة (>= العتبة)", highConfidence >= EditionIntelligence.BALANCED_AUTO_MERGE_THRESHOLD)
        assertTrue("في Balanced تدمج هذه البيانات", EditionIntelligence.canAutoMerge(signalA, signalB, IntelligenceLevel.BALANCED))
        assertFalse("في Conservative لا تدمج هذه البيانات رغم نفس الثقة", EditionIntelligence.canAutoMerge(signalA, signalB, IntelligenceLevel.CONSERVATIVE))

        reader.overrides["default"] = AudioMetadata(1_000_000L, "audio/mp4", "Same Book", "Same Narrator", null, emptyList())
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.m4b"), "Book v1/Part 1.m4b", "Book v1", "Part 1.m4b", 100, 10),
            ScanFile(Uri.parse("content://audio/2.m4b"), "Book v2/Part 1.m4b", "Book v2", "Part 1.m4b", 100, 10)
        )

        val conservativeReport = scanRoot(root.id)

        assertEquals("Conservative يمنع الدمج التلقائي رغم الثقة العالية", 0, conservativeReport.editionsAutoMerged)
        assertEquals("يبقى الإصداران منفصلين في قاعدة البيانات", 2, allEditions().size)

        // Positive control — نفس البيانات ونفس المجلدات، فقط تغيير المستوى عبر الـViewModel:
        viewModel.selectIntelligenceLevel(IntelligenceLevel.BALANCED)
        source.files = listOf(
            ScanFile(Uri.parse("content://audio/1.m4b"), "Book v1/Part 1.m4b", "Book v1", "Part 1.m4b", 100, 11),
            ScanFile(Uri.parse("content://audio/2.m4b"), "Book v2/Part 1.m4b", "Book v2", "Part 1.m4b", 100, 11)
        )

        val balancedReport = scanRoot(root.id)

        assertEquals("نفس البيانات تحت Balanced تُدمج تلقائيًا — الاختيار هو الأثر", 1, balancedReport.editionsAutoMerged)
        assertEquals("بعد الدمج يبقى إصدار واحد", 1, allEditions().size)
    }

    private class FakeFileSource : LibraryFileSource {
        var files: List<ScanFile> = emptyList()
        override fun listAudioFiles(rootUri: Uri): List<ScanFile> = files
    }

    private class CountingMetadataReader : AudioMetadataReader {
        var overrides = mutableMapOf<String, AudioMetadata>()
        override fun read(uri: Uri, fileName: String): AudioMetadata =
            overrides["default"] ?: AudioMetadata(1000, "audio/mp4", "Book", null, null, emptyList())
    }
}