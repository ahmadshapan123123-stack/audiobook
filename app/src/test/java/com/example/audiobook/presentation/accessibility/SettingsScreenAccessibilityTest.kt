package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.preferences.ScanSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.presentation.settings.SettingsScreen
import com.example.audiobook.presentation.settings.SettingsViewModel
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [R8-النقطة 2] اختبار الوصولية لشاشة Settings: الخيارات الثلاثة ظاهرة بنصوصها
 * الحقيقية، شرح القيد الصارم ظاهر، اختيار "محافظ" يغيّر القيمة الفعلية عبر الـViewModel،
 * وزر الرجوع ≥ 48dp، والرأس لا يُقصف بخط مكبّر.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-480dpi")
class SettingsScreenAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var scanSettings: ScanSettings
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        scanSettings = ScanSettings(context)
        scanSettings.setIntelligenceLevel(IntelligenceLevel.BALANCED)
        viewModel = SettingsViewModel(scanSettings)
    }

    @After
    fun tearDown() {
        scanSettings.setIntelligenceLevel(IntelligenceLevel.BALANCED)
    }

    private fun showScreen(fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    SettingsScreen(onBack = {}, viewModel = viewModel)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("الإعدادات").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertMinTouchTargetHeight(text: String) {
        val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
        val actual = bounds.bottom - bounds.top
        assertTrue("'$text' height ($actual) < 48dp", actual >= 47.9.dp)
    }

    @Test
    fun showsThreeOptionsAndStrictRuleNoteThenChoosingConservativeWritesRealValue() {
        showScreen()

        composeRule.onAllNodesWithText("مستوى الذكاء في كشف الإصدارات").assertCountEquals(1)
        composeRule.onAllNodesWithText("محافظ").assertCountEquals(1)
        composeRule.onAllNodesWithText("متوازن").assertCountEquals(1)
        composeRule.onAllNodesWithText("ذكي").assertCountEquals(1)
        composeRule.onAllNodesWithText("لا دمج تلقائي إطلاقًا؛ كل تجميع مقترح يُعرض عليك في شاشة المراجعة.").assertCountEquals(1)
        composeRule.onAllNodesWithText("اقتراحات أوسع تُعرض في شاشة المراجعة، لكن لا دمج تلقائي صامت إطلاقًا.").assertCountEquals(1)
        composeRule.onAllNodesWithText("فرق مدة أكبر من 15% يمنع أي دمج مهما كانت الثقة", substring = true).assertCountEquals(1)

        composeRule.onNodeWithText("محافظ").performClick()

        assertEquals(IntelligenceLevel.CONSERVATIVE, scanSettings.currentIntelligenceLevel())
        assertEquals(IntelligenceLevel.CONSERVATIVE, viewModel.intelligenceLevel.value)
    }

    @Test
    fun backButtonMeetsMin48Dp() {
        showScreen()

        assertMinTouchTargetHeight("رجوع")
    }

    @Test
    fun enlargedTextKeepsHeaderAndOptionsInsideScreenWithoutClipping() {
        showScreen(fontScale = 2f)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (text in listOf("رجوع", "الإعدادات", "محافظ", "متوازن", "ذكي")) {
            val bounds = composeRule.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot()
            assertTrue("'$text' يعبر الحافة اليمنى", bounds.right <= root.right + 0.5.dp)
            assertTrue("'$text' يعبر الحافة اليسرى", bounds.left >= root.left - 0.5.dp)
        }
    }
}