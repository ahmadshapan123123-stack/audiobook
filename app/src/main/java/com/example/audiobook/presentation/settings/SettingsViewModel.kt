package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.preferences.ScanSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanSettings: ScanSettings,
    private val appSettings: AppSettings
) : ViewModel() {

    val intelligenceLevel: StateFlow<IntelligenceLevel> = scanSettings.intelligenceLevel
    val defaultSpeed: StateFlow<Float> = appSettings.defaultSpeed
    val autoResume: StateFlow<Boolean> = appSettings.autoResume
    val defaultSleepMinutes: StateFlow<Int> = appSettings.defaultSleepMinutes
    val autoExtendSleep: StateFlow<Boolean> = appSettings.autoExtendSleep
    val notificationsEnabled: StateFlow<Boolean> = appSettings.notificationsEnabled

    fun selectIntelligenceLevel(level: IntelligenceLevel) = scanSettings.setIntelligenceLevel(level)
    fun setDefaultSpeed(speed: Float) = appSettings.setDefaultSpeed(speed)
    fun setAutoResume(enabled: Boolean) = appSettings.setAutoResume(enabled)
    fun setDefaultSleepMinutes(minutes: Int) = appSettings.setDefaultSleepMinutes(minutes)
    fun setAutoExtendSleep(enabled: Boolean) = appSettings.setAutoExtendSleep(enabled)
    fun setNotificationsEnabled(enabled: Boolean) = appSettings.setNotificationsEnabled(enabled)
}
