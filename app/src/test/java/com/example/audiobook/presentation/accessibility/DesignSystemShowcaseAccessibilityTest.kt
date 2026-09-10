package com.example.audiobook.presentation.accessibility

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import com.example.audiobook.presentation.theme.DesignSystemShowcase
import com.example.audiobook.presentation.theme.ThemePreference
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-480dpi")
class DesignSystemShowcaseAccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<androidx.activity.ComponentActivity>()

    private lateinit var preference: ThemePreference

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        preference = ThemePreference(context)
    }

    private fun showShowcase(fontScale: Float = 1f) {
        composeRule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)
            ) {
                AudiobookTheme(mode = AppThemeMode.LIGHT) {
                    DesignSystemShowcase(preference = preference)
                }
            }
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("متابعة الاستماع").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun sliderTouchHeightDp(): Float {
        val bounds = composeRule.onNodeWithTag("slider-touch-target").getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test
    fun chipsAndPrimaryButtonAndSliderMeetMin48Dp() {
        showShowcase()

        for (text in listOf("فاتح", "داكن", "AMOLED", "متابعة الاستماع")) {
            val bounds = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
            assertTrue("'$text' height (${bounds.bottom - bounds.top}) < 48dp", bounds.bottom - bounds.top >= 47.9.dp)
        }

        val slider = sliderTouchHeightDp()
        assertTrue("slider touch height (${slider}dp) < 48dp", slider >= 47.9f)
    }

    @Test
    fun modeChipsExposeSelectedStateAndToggleIt() {
        showShowcase()

        composeRule.onAllNodesWithText("داكن").assertCountEquals(1)
        composeRule.onNodeWithText("داكن").assertIsSelected()
        composeRule.onNodeWithText("AMOLED").assertIsNotSelected()

        composeRule.onNodeWithText("AMOLED").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("AMOLED").assertIsSelected()
        composeRule.onNodeWithText("داكن").assertIsNotSelected()
        assertTrue("preference.mode لم يتغير إلى AMOLED", preference.mode == AppThemeMode.AMOLED)
    }

    @Test
    fun enlargedTextKeepsLabelsInsideScreenWithoutClipping() {
        showShowcase(fontScale = 2f)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        for (text in listOf("فاتح", "داكن", "AMOLED", "متابعة الاستماع", "رحلة تجريبية", "نظام تصميم مريح للقراءة والاستماع الطويل")) {
            val textBounds = composeRule.onAllNodesWithText(text).onFirst().getUnclippedBoundsInRoot()
            assertTrue("'$text' يعبر الحافة اليمنى", textBounds.right <= root.right)
            assertTrue("'$text' يعبر الحافة اليسرى", textBounds.left >= root.left)
        }
    }
}