package com.example.audiobook.presentation.settings

import com.example.audiobook.testing.FakeScanServiceLauncher
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.background.reclassify.ReclassifyScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import com.example.audiobook.domain.usecases.classificationPreviewFor
import com.example.audiobook.domain.usecases.libraryManagementFor
import com.example.audiobook.domain.usecases.scanLibraryNowFor
import com.example.audiobook.domain.usecases.rebuildStructureFor
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
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        AppSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
        AppSettings(context).setThemeMode(AppThemeMode.DARK)
    }

    @After
    fun tearDown() {
        database.close()
        AppSettings(context).setIntelligenceLevel(IntelligenceLevel.BALANCED)
        AppSettings(context).setThemeMode(AppThemeMode.DARK)
    }

    private fun viewModel(): SettingsViewModel = SettingsViewModel(
        AppSettings(context),
        ReminderScheduler(context, AppSettings(context)),
        libraryManagementFor(database),
            database.libraryRootDao(),
            database.bookDao(),
        FakeScanServiceLauncher.running(
            database,
            scanLibraryNowFor(database, AppSettings(context)),
            rebuildStructureFor(database, AppSettings(context))
        ),
        ReclassifyLibrary(database, AppSettings(context)),
        classificationPreviewFor(database, AppSettings(context)),
        ReclassifyScheduler(context),
        context
    )

    @Test
    fun chosenAutoSeriesModeThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = viewModel()

        viewModel.setAutoSeriesClassification(false)

        assertEquals(false, viewModel.autoSeriesClassification.value)
        assertEquals(false, AppSettings(context).currentAutoSeriesClassification())
    }

    @Test
    fun chosenLevelThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = viewModel()

        viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE)

        assertEquals(IntelligenceLevel.CONSERVATIVE, viewModel.intelligenceLevel.value)
        assertEquals(IntelligenceLevel.CONSERVATIVE, AppSettings(context).currentIntelligenceLevel())
    }

    @Test
    fun selectingEveryLevelAppliesPersistentlyThroughAppSettings() {
        val viewModel = viewModel()

        for (level in IntelligenceLevel.entries) {
            viewModel.selectIntelligenceLevel(level)
            assertEquals(level, viewModel.intelligenceLevel.value)
            assertEquals(level, AppSettings(context).currentIntelligenceLevel())
        }
    }

    @Test
    fun chosenThemeThroughViewModelIsPersistedAcrossNewInstance() {
        val viewModel = viewModel()

        viewModel.selectThemeMode(AppThemeMode.AMOLED)

        assertEquals(AppThemeMode.AMOLED, viewModel.themeMode.value)
        assertEquals(AppThemeMode.AMOLED, AppSettings(context).currentThemeMode())
    }
}
