package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import com.example.audiobook.domain.usecases.ReclassifyPreview
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.ScanNowResult
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.domain.model.LogoColorMode
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.room.dao.LibraryRootDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val reminderScheduler: ReminderScheduler,
    private val libraryManagement: LibraryManagement,
    private val libraryRootDao: LibraryRootDao,
    private val scanLibraryNow: ScanLibraryNow,
    private val reclassifyLibrary: ReclassifyLibrary
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = appSettings.themeMode
    val logoColor: StateFlow<LogoColorMode> = appSettings.logoColor
    val intelligenceLevel: StateFlow<IntelligenceLevel> = appSettings.intelligenceLevel
    val defaultSpeed: StateFlow<Float> = appSettings.defaultSpeed
    val autoResume: StateFlow<Boolean> = appSettings.autoResume
    val defaultSleepMinutes: StateFlow<Int> = appSettings.defaultSleepMinutes
    val autoExtendSleep: StateFlow<Boolean> = appSettings.autoExtendSleep
    val notificationsEnabled: StateFlow<Boolean> = appSettings.notificationsEnabled
    val mediaNotificationMinimal: StateFlow<Boolean> = appSettings.mediaNotificationMinimal
    val sleepTimerNotificationsEnabled: StateFlow<Boolean> = appSettings.sleepTimerNotificationsEnabled
    val saveMomentNotificationsEnabled: StateFlow<Boolean> = appSettings.saveMomentNotificationsEnabled
    val bookCompletionNotificationsEnabled: StateFlow<Boolean> = appSettings.bookCompletionNotificationsEnabled
    val dailyReminderEnabled: StateFlow<Boolean> = appSettings.dailyReminderEnabled
    val dailyReminderHour: StateFlow<Int> = appSettings.dailyReminderHour
    val dailyReminderMinute: StateFlow<Int> = appSettings.dailyReminderMinute
    val resumeReminderEnabled: StateFlow<Boolean> = appSettings.resumeReminderEnabled
    val hasSeededDemoData: StateFlow<Boolean> = appSettings.hasSeededDemoData

    private val _hasLibraryRoots = MutableStateFlow(false)
    val hasLibraryRoots: StateFlow<Boolean> = _hasLibraryRoots.asStateFlow()

    private val _isRemovingDemoData = MutableStateFlow(false)
    val isRemovingDemoData: StateFlow<Boolean> = _isRemovingDemoData.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** حصيلة آخر فحص فوري (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _scanResult = MutableStateFlow<ScanNowResult?>(null)
    val scanResult: StateFlow<ScanNowResult?> = _scanResult.asStateFlow()

    private val _isReclassifying = MutableStateFlow(false)
    val isReclassifying: StateFlow<Boolean> = _isReclassifying.asStateFlow()

    /** معاينة إعادة التصنيف (غير null = يوجد تغييرات تنتظر تأكيد المستخدم). */
    private val _reclassifyPreview = MutableStateFlow<ReclassifyPreview?>(null)
    val reclassifyPreview: StateFlow<ReclassifyPreview?> = _reclassifyPreview.asStateFlow()

    /** حصيلة آخر إعادة تصنيف (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _reclassifyApplied = MutableStateFlow<ReclassifyPreview?>(null)
    val reclassifyApplied: StateFlow<ReclassifyPreview?> = _reclassifyApplied.asStateFlow()

    private val _scanFailed = MutableStateFlow(false)
    val scanFailed: StateFlow<Boolean> = _scanFailed.asStateFlow()

    init {
        viewModelScope.launch {
            _hasLibraryRoots.value = libraryRootDao.countAll() > 0
        }
    }

    fun refreshRootsCount() {
        viewModelScope.launch {
            _hasLibraryRoots.value = libraryRootDao.countAll() > 0
        }
    }

    fun removeDemoData() {
        viewModelScope.launch {
            _isRemovingDemoData.value = true
            libraryManagement.clearDemoData()
            appSettings.setHasSeededDemoData(false)
            _isRemovingDemoData.value = false
        }
    }

    /** الفحص الفوري لكل المجلدات الممكّنة (وليس جدولة خلفية). */
    fun scanNow() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            runCatching { scanLibraryNow() }
                .onSuccess { result ->
                    _scanResult.value = result
                    refreshRootsCount()
                }
                .onFailure { _scanFailed.value = true }
            _isScanning.value = false
        }
    }

    fun consumeScanResult() {
        _scanResult.value = null
    }

    fun consumeScanFailed() {
        _scanFailed.value = false
    }

    /** الخطوة 1: حساب ما الذي ستغيّره إعادة التصنيف وعرضه للتأكيد (بلا كتابة). */
    fun requestReclassify() {
        if (_isReclassifying.value) return
        viewModelScope.launch {
            _isReclassifying.value = true
            _reclassifyPreview.value = runCatching { reclassifyLibrary(dryRun = true) }.getOrNull()
            _isReclassifying.value = false
        }
    }

    fun cancelReclassify() {
        _reclassifyPreview.value = null
    }

    /** الخطوة 2: تطبيق إعادة التصنيف بعد تأكيد المستخدم. */
    fun confirmReclassify() {
        if (_isReclassifying.value) return
        viewModelScope.launch {
            _isReclassifying.value = true
            val applied = runCatching { reclassifyLibrary(dryRun = false) }.getOrNull()
            _reclassifyPreview.value = null
            _reclassifyApplied.value = applied
            _isReclassifying.value = false
        }
    }

    fun consumeReclassifyApplied() {
        _reclassifyApplied.value = null
    }

    fun selectThemeMode(mode: AppThemeMode) = appSettings.setThemeMode(mode)
    fun selectLogoColor(mode: LogoColorMode) = appSettings.setLogoColor(mode)
    fun selectIntelligenceLevel(level: IntelligenceLevel) = appSettings.setIntelligenceLevel(level)
    fun setDefaultSpeed(speed: Float) = appSettings.setDefaultSpeed(speed)
    fun setAutoResume(enabled: Boolean) = appSettings.setAutoResume(enabled)
    fun setDefaultSleepMinutes(minutes: Int) = appSettings.setDefaultSleepMinutes(minutes)
    fun setAutoExtendSleep(enabled: Boolean) = appSettings.setAutoExtendSleep(enabled)
    fun setNotificationsEnabled(enabled: Boolean) {
        appSettings.setNotificationsEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }

    fun setMediaNotificationMinimal(minimal: Boolean) = appSettings.setMediaNotificationMinimal(minimal)
    fun setSleepTimerNotificationsEnabled(enabled: Boolean) = appSettings.setSleepTimerNotificationsEnabled(enabled)
    fun setSaveMomentNotificationsEnabled(enabled: Boolean) = appSettings.setSaveMomentNotificationsEnabled(enabled)
    fun setBookCompletionNotificationsEnabled(enabled: Boolean) = appSettings.setBookCompletionNotificationsEnabled(enabled)
    fun setDailyReminderEnabled(enabled: Boolean) {
        appSettings.setDailyReminderEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        appSettings.setDailyReminderHour(hour)
        appSettings.setDailyReminderMinute(minute)
        reminderScheduler.syncWithSettings()
    }

    fun setResumeReminderEnabled(enabled: Boolean) {
        appSettings.setResumeReminderEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }
}