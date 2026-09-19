package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.domain.model.AppThemeMode
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
    private val libraryRootDao: LibraryRootDao
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = appSettings.themeMode
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

    fun selectThemeMode(mode: AppThemeMode) = appSettings.setThemeMode(mode)
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