package com.example.audiobook.data.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Playback ──
    private val _defaultSpeed = MutableStateFlow(prefs.getFloat(KEY_DEFAULT_SPEED, DEFAULT_SPEED))
    val defaultSpeed: StateFlow<Float> = _defaultSpeed.asStateFlow()

    private val _autoResume = MutableStateFlow(prefs.getBoolean(KEY_AUTO_RESUME, true))
    val autoResume: StateFlow<Boolean> = _autoResume.asStateFlow()

    // ── Sleep timer ──
    private val _defaultSleepMinutes = MutableStateFlow(prefs.getInt(KEY_DEFAULT_SLEEP, DEFAULT_SLEEP))
    val defaultSleepMinutes: StateFlow<Int> = _defaultSleepMinutes.asStateFlow()

    private val _autoExtendSleep = MutableStateFlow(prefs.getBoolean(KEY_AUTO_EXTEND, true))
    val autoExtendSleep: StateFlow<Boolean> = _autoExtendSleep.asStateFlow()

    // ── Notifications ──
    private val _notificationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_NOTIFICATIONS, true))
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    // ── Setters ──

    fun setDefaultSpeed(value: Float) {
        _defaultSpeed.value = value
        prefs.edit().putFloat(KEY_DEFAULT_SPEED, value).apply()
    }

    fun setAutoResume(value: Boolean) {
        _autoResume.value = value
        prefs.edit().putBoolean(KEY_AUTO_RESUME, value).apply()
    }

    fun setDefaultSleepMinutes(value: Int) {
        _defaultSleepMinutes.value = value
        prefs.edit().putInt(KEY_DEFAULT_SLEEP, value).apply()
    }

    fun setAutoExtendSleep(value: Boolean) {
        _autoExtendSleep.value = value
        prefs.edit().putBoolean(KEY_AUTO_EXTEND, value).apply()
    }

    fun setNotificationsEnabled(value: Boolean) {
        _notificationsEnabled.value = value
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
    }

    private companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_DEFAULT_SPEED = "default_speed"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_DEFAULT_SLEEP = "default_sleep_minutes"
        const val KEY_AUTO_EXTEND = "auto_extend_sleep"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_SLEEP = 30
    }
}
