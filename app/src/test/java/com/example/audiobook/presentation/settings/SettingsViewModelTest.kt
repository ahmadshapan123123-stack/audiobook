package com.example.audiobook.presentation.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.model.AppThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * [ØªÙˆØ­ÙŠØ¯ Ø§Ù„Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª] Ø§Ø®ØªØ¨Ø§Ø± Ù‚Ø·Ø¹Ø© Ø¥Ø¹Ø¯Ø§Ø¯Ø§Øª Ø§Ù„Ø°ÙƒØ§Ø¡ Ø¹Ø¨Ø± Ø§Ù„ÙˆØ§Ø¬Ù‡Ø© Ø§Ù„Ù…ÙˆØ­Ù‘Ø¯Ø©:
 *  - [AppSettings] ÙŠØ®Ø²Ù‘Ù† Ù…Ø³ØªÙˆÙ‰ Ø§Ù„Ø°ÙƒØ§Ø¡ Ø¨Ø¢Ù„ÙŠØ© Ø¯Ø§Ø¦Ù…Ø© (SharedPreferences Ø¨Ø§Ù„Ø§Ø³Ù… `scan`)
 *    ÙˆÙˆØ¶Ø¹ Ø§Ù„Ù…Ø¸Ù‡Ø± (SharedPreferences Ø¨Ø§Ù„Ø§Ø³Ù… `appearance`) Ø¯ÙˆÙ† ÙƒØ³Ø± Ø§Ù„Ù…ÙØ§ØªÙŠØ­ Ø§Ù„Ù‚Ø¯ÙŠÙ…Ø©.
 *  - [SettingsViewModel] ÙŠÙ‚Ø±Ø£/ÙŠÙƒØªØ¨ Ø§Ù„Ù‚ÙŠÙ… Ø¹Ø¨Ø± [AppSettings] ÙÙ‚Ø·.
 *  - Ø§Ø®ØªÙŠØ§Ø± Ø¹Ø¨Ø± Ø§Ù„Ù€ViewModel ÙŠÙ†Ø¬Ùˆ Ù…Ù† "Ø¥Ø¹Ø§Ø¯Ø© Ø§Ù„ØªØ´ØºÙŠÙ„" (Ø¥Ù†Ø´Ø§Ø¡ ÙƒØ§Ø¦Ù† [AppSettings] Ø¬Ø¯ÙŠØ¯
 *    = Ù‚Ø±Ø§Ø¡Ø© Ù…Ù† Ø§Ù„Ù‚Ø±Øµ Ù…Ø±Ø© Ø«Ø§Ù†ÙŠØ©) ÙˆÙ„ÙŠØ³ Ù‚ÙŠÙ…Ø© Ø§ÙØªØ±Ø§Ø¶ÙŠØ© Ø«Ø§Ø¨ØªØ©.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        // Ù‚ÙŠÙ…Ø© Ù‚Ø§Ø¹Ø¯ÙŠØ© Ø­ØªÙ…ÙŠØ© Ø¨ØºØ¶Ù‘ Ø§Ù„Ù†Ø¸Ø± Ø¹Ù† Ø£ÙŠ Ø§Ø®ØªØ¨Ø§Ø± Ø³Ø§Ø¨Ù‚ ÙƒØªØ¨ ÙÙŠ Ù†ÙØ³ Ø§Ù„Ù€SharedPreferences.
        AppSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
        AppSettings(context).setThemeMode(AppThemeMode.DARK)
    }

    @After
    fun tearDown() {
        AppSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
        AppSettings(context).setThemeMode(AppThemeMode.DARK)
    }

    @Test
    fun chosenLevelThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = SettingsViewModel(AppSettings(context), ReminderScheduler(context, AppSettings(context)))

        viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE)

        assertEquals(IntelligenceLevel.CONSERVATIVE, viewModel.intelligenceLevel.value)
        assertEquals(IntelligenceLevel.CONSERVATIVE, AppSettings(context).currentIntelligenceLevel())
    }

    @Test
    fun selectingEveryLevelAppliesPersistentlyThroughAppSettings() {
        val viewModel = SettingsViewModel(AppSettings(context), ReminderScheduler(context, AppSettings(context)))

        for (level in IntelligenceLevel.entries) {
            viewModel.selectIntelligenceLevel(level)
            assertEquals(level, viewModel.intelligenceLevel.value)
            assertEquals(level, AppSettings(context).currentIntelligenceLevel())
        }
    }

    @Test
    fun chosenThemeThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = SettingsViewModel(AppSettings(context), ReminderScheduler(context, AppSettings(context)))

        viewModel.selectThemeMode(AppThemeMode.AMOLED)

        assertEquals(AppThemeMode.AMOLED, viewModel.themeMode.value)
        assertEquals(AppThemeMode.AMOLED, AppSettings(context).currentThemeMode())
    }
}
