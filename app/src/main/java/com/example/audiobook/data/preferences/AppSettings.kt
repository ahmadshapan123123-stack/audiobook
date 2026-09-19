package com.example.audiobook.data.preferences

import android.content.Context
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.model.AppThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * الواجهة الموحّدة الوحيدة لكل إعدادات التطبيق.
 *
 * داخليًا تُستخدم ثلاث ملفات SharedPreferences بنفس أسمائها ومفاتيحها القديمة
 * (`app_settings` / `scan` / `appearance`) لضمان عدم فقدان أي قيمة محفوظة،
 * لكن لا يُسمح لأي كود خارجي بقراءة/كتابة أي إعداد إلا عبر هذا الصف.
 * القيم الافتراضية مطابقة تمامًا لما كانت عليه قبل التوحيد.
 */
@Singleton
class AppSettings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scanPrefs = context.getSharedPreferences(SCAN_PREFS, Context.MODE_PRIVATE)
    private val appearancePrefs = context.getSharedPreferences(APPEARANCE_PREFS, Context.MODE_PRIVATE)

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

    private val _mediaMinimal = MutableStateFlow(prefs.getBoolean(KEY_MEDIA_MINIMAL, false))
    val mediaNotificationMinimal: StateFlow<Boolean> = _mediaMinimal.asStateFlow()

    private val _sleepTimerNotif = MutableStateFlow(prefs.getBoolean(KEY_SLEEP_TIMER_NOTIF, true))
    val sleepTimerNotificationsEnabled: StateFlow<Boolean> = _sleepTimerNotif.asStateFlow()

    private val _saveMomentNotif = MutableStateFlow(prefs.getBoolean(KEY_SAVE_MOMENT_NOTIF, true))
    val saveMomentNotificationsEnabled: StateFlow<Boolean> = _saveMomentNotif.asStateFlow()

    private val _bookCompletionNotif = MutableStateFlow(prefs.getBoolean(KEY_BOOK_COMPLETION_NOTIF, true))
    val bookCompletionNotificationsEnabled: StateFlow<Boolean> = _bookCompletionNotif.asStateFlow()

    private val _dailyReminder = MutableStateFlow(prefs.getBoolean(KEY_DAILY_REMINDER, false))
    val dailyReminderEnabled: StateFlow<Boolean> = _dailyReminder.asStateFlow()

    private val _dailyReminderHour = MutableStateFlow(prefs.getInt(KEY_DAILY_REMINDER_HOUR, 20))
    val dailyReminderHour: StateFlow<Int> = _dailyReminderHour.asStateFlow()

    private val _dailyReminderMinute = MutableStateFlow(prefs.getInt(KEY_DAILY_REMINDER_MINUTE, 0))
    val dailyReminderMinute: StateFlow<Int> = _dailyReminderMinute.asStateFlow()

    private val _resumeReminder = MutableStateFlow(prefs.getBoolean(KEY_RESUME_REMINDER, false))
    val resumeReminderEnabled: StateFlow<Boolean> = _resumeReminder.asStateFlow()

    // ── Appearance ──
    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    // ── Library scanning ──
    private val _intelligenceLevel = MutableStateFlow(loadIntelligenceLevel())
    val intelligenceLevel: StateFlow<IntelligenceLevel> = _intelligenceLevel.asStateFlow()

    // ── Onboarding / Demo ──
    private val _hasCompletedOnboarding = MutableStateFlow(prefs.getBoolean(KEY_HAS_COMPLETED_ONBOARDING, false))
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _hasSeededDemoData = MutableStateFlow(prefs.getBoolean(KEY_HAS_SEEDED_DEMO, false))
    val hasSeededDemoData: StateFlow<Boolean> = _hasSeededDemoData.asStateFlow()

    /** قراءة متزامنة لمستوى الذكاء — يستخدمها منطق الفحص أثناء العمل. */
    fun currentIntelligenceLevel(): IntelligenceLevel = _intelligenceLevel.value

    /** قراءة متزامنة لوضع المظهر — يستخدمها من يحتاج القيمة خارج Compose. */
    fun currentThemeMode(): AppThemeMode = _themeMode.value

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

    fun setMediaNotificationMinimal(value: Boolean) {
        _mediaMinimal.value = value
        prefs.edit().putBoolean(KEY_MEDIA_MINIMAL, value).apply()
    }

    fun setSleepTimerNotificationsEnabled(value: Boolean) {
        _sleepTimerNotif.value = value
        prefs.edit().putBoolean(KEY_SLEEP_TIMER_NOTIF, value).apply()
    }

    fun setSaveMomentNotificationsEnabled(value: Boolean) {
        _saveMomentNotif.value = value
        prefs.edit().putBoolean(KEY_SAVE_MOMENT_NOTIF, value).apply()
    }

    fun setBookCompletionNotificationsEnabled(value: Boolean) {
        _bookCompletionNotif.value = value
        prefs.edit().putBoolean(KEY_BOOK_COMPLETION_NOTIF, value).apply()
    }

    fun setDailyReminderEnabled(value: Boolean) {
        _dailyReminder.value = value
        prefs.edit().putBoolean(KEY_DAILY_REMINDER, value).apply()
    }

    fun setDailyReminderHour(hour: Int) {
        _dailyReminderHour.value = hour
        prefs.edit().putInt(KEY_DAILY_REMINDER_HOUR, hour).apply()
    }

    fun setDailyReminderMinute(minute: Int) {
        _dailyReminderMinute.value = minute
        prefs.edit().putInt(KEY_DAILY_REMINDER_MINUTE, minute).apply()
    }

    fun setResumeReminderEnabled(value: Boolean) {
        _resumeReminder.value = value
        prefs.edit().putBoolean(KEY_RESUME_REMINDER, value).apply()
    }

    fun setHasCompletedOnboarding(value: Boolean) {
        _hasCompletedOnboarding.value = value
        prefs.edit().putBoolean(KEY_HAS_COMPLETED_ONBOARDING, value).apply()
    }

    fun setHasSeededDemoData(value: Boolean) {
        _hasSeededDemoData.value = value
        prefs.edit().putBoolean(KEY_HAS_SEEDED_DEMO, value).apply()
    }

    /** التذكرالخانة: متى نُوِقظ إشعار إكمال الكتاب آخر مرة (للحدّ بـ 24 ساعة). */
    fun lastBookCompletionNotifiedAt(bookId: java.util.UUID): Long =
        prefs.getLong(completedKey(bookId), 0L)

    fun markBookCompletionNotified(bookId: java.util.UUID) {
        prefs.edit().putLong(completedKey(bookId), System.currentTimeMillis()).apply()
    }

    private fun completedKey(bookId: java.util.UUID): String = "book_completed_at_$bookId"

    fun setThemeMode(value: AppThemeMode) {
        if (_themeMode.value == value) return
        _themeMode.value = value
        appearancePrefs.edit().putString(KEY_THEME_MODE, value.name).apply()
    }

    fun setIntelligenceLevel(value: IntelligenceLevel) {
        if (_intelligenceLevel.value == value) return
        _intelligenceLevel.value = value
        scanPrefs.edit().putString(KEY_INTELLIGENCE_LEVEL, value.name).apply()
    }

    private fun loadThemeMode(): AppThemeMode =
        appearancePrefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)
            ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrDefault(AppThemeMode.DARK) }
            ?: AppThemeMode.DARK

    private fun loadIntelligenceLevel(): IntelligenceLevel =
        scanPrefs.getString(KEY_INTELLIGENCE_LEVEL, IntelligenceLevel.BALANCED.name)
            ?.let { runCatching { IntelligenceLevel.valueOf(it) }.getOrDefault(IntelligenceLevel.BALANCED) }
            ?: IntelligenceLevel.BALANCED

    private companion object {
        const val PREFS_NAME = "app_settings"
        const val SCAN_PREFS = "scan"
        const val APPEARANCE_PREFS = "appearance"

        const val KEY_DEFAULT_SPEED = "default_speed"
        const val KEY_AUTO_RESUME = "auto_resume"
        const val KEY_DEFAULT_SLEEP = "default_sleep_minutes"
        const val KEY_AUTO_EXTEND = "auto_extend_sleep"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_MEDIA_MINIMAL = "media_notification_minimal"
        const val KEY_SLEEP_TIMER_NOTIF = "sleep_timer_notifications"
        const val KEY_SAVE_MOMENT_NOTIF = "save_moment_notifications"
        const val KEY_BOOK_COMPLETION_NOTIF = "book_completion_notifications"
        const val KEY_DAILY_REMINDER = "daily_reminder_enabled"
        const val KEY_DAILY_REMINDER_HOUR = "daily_reminder_hour"
        const val KEY_DAILY_REMINDER_MINUTE = "daily_reminder_minute"
        const val KEY_RESUME_REMINDER = "resume_reminder_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_INTELLIGENCE_LEVEL = "intelligence_level"
        const val KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"
        const val KEY_HAS_SEEDED_DEMO = "has_seeded_demo"

        const val DEFAULT_SPEED = 1.0f
        const val DEFAULT_SLEEP = 30
    }
}
