package com.example.audiobook.presentation.accessibility

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [R5] إثبات صِدق بنية الاختبار: هل يعمل Compose Testing APIs فعلاً على
 * مسار اختبارات الوحدة (Robolectric) في هذا المشروع؟ هذا الاختبار لا يثبت
 * أي شيء عن الشاشات — يثبت أن البنية نفسها تعمل، قبل ادعاء أي نتيجة semantics.
 */
@RunWith(RobolectricTestRunner::class)
class ComposeHarnessSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun harnessCanRenderAndQuerySemanticsTree() {
        compose.setContent { Text("بنية تعمل") }
        compose.onNodeWithText("بنية تعمل").assertIsDisplayed()
    }
}