package com.example.audiobook.data.preferences

import android.content.Context
import com.example.audiobook.domain.usecases.IntelligenceLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * إعدادات الفحص المحفوظة محليًا (SharedPreferences — نفس نمط ThemePreference).
 * مستويات الذكاء تُختار من شاشة Settings وتُقرأ فعلًا في منطق القرار أثناء الفحص.
 */
class ScanSettings(context: Context) {
    private val preferences = context.getSharedPreferences("scan", Context.MODE_PRIVATE)

    private val _intelligenceLevel = MutableStateFlow(loadIntelligenceLevel())
    val intelligenceLevel: StateFlow<IntelligenceLevel> = _intelligenceLevel.asStateFlow()

    fun currentIntelligenceLevel(): IntelligenceLevel = _intelligenceLevel.value

    fun setIntelligenceLevel(value: IntelligenceLevel) {
        _intelligenceLevel.value = value
        preferences.edit().putString(KEY_INTELLIGENCE_LEVEL, value.name).apply()
    }

    private fun loadIntelligenceLevel(): IntelligenceLevel =
        preferences.getString(KEY_INTELLIGENCE_LEVEL, IntelligenceLevel.BALANCED.name)
            ?.let { runCatching { IntelligenceLevel.valueOf(it) }.getOrDefault(IntelligenceLevel.BALANCED) }
            ?: IntelligenceLevel.BALANCED

    private companion object {
        const val KEY_INTELLIGENCE_LEVEL = "intelligence_level"
    }
}