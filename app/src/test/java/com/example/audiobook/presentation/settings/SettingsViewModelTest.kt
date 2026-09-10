package com.example.audiobook.presentation.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.ScanSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [R8-النقطة 1+3] اختبار قطعة إعدادات الذكاء:
 *  - [ScanSettings] يخزّن مستوى الذكاء بآلية دائمة (SharedPreferences بالاسم `scan`)
 *    ويُستخدم كما هو دون أي توسيع.
 *  - [SettingsViewModel] يقرأ/يكتب القيمة عبر [ScanSettings] نفسه.
 *  - اختيار عبر الـViewModel ينجو من "إعادة التشغيل" (kنشاء كائن [ScanSettings] جديد
 *    = قراءة من القرص مرة ثانية) وليس قيمة افتراضية ثابتة.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // قيمة قاعدية حتمية بغضّ النظر عن أي اختبار سابق كتب في نفس الـSharedPreferences.
        ScanSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
    }

    @After
    fun tearDown() {
        ScanSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
    }

    @Test
    fun chosenLevelThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = SettingsViewModel(ScanSettings(context))

        viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE)

        assertEquals(IntelligenceLevel.CONSERVATIVE, viewModel.intelligenceLevel.value)
        assertEquals(IntelligenceLevel.CONSERVATIVE, ScanSettings(context).currentIntelligenceLevel())
    }

    @Test
    fun selectingEveryLevelAppliesPersistentlyThroughScanSettings() {
        val viewModel = SettingsViewModel(ScanSettings(context))

        for (level in IntelligenceLevel.entries) {
            viewModel.selectIntelligenceLevel(level)
            assertEquals(level, viewModel.intelligenceLevel.value)
            assertEquals(level, ScanSettings(context).currentIntelligenceLevel())
        }
    }
}