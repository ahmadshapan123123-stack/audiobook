package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import com.example.audiobook.data.preferences.ScanSettings
import com.example.audiobook.domain.usecases.IntelligenceLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/**
 * [R8-النقطة 3] قراءة/كتابة مستوى الذكاء عبر [ScanSettings] الحقيقي (SharedPreferences —
 * نفس نمط ThemePreference) داخل كائن [ScanSettings] واحد Singleton يحفظ القيمة في
 * `scan` SharedPreferences فيُعاد قراءتها بعد كل إعادة تشغيل، وليس قيمة افتراضية ثابتة.
 *
 * القرار ينتقل فورًا إلى [ScanSettings.intelligenceLevel] فيقرأه [ScanRoot]
 * فعلًا أثناء الفحص التالي (انظر اختبار التكامل `SettingsChoiceDrivesScanIntegrationTest`).
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val scanSettings: ScanSettings
) : ViewModel() {

    /** المستوى الحالي كتدفق مباشر من [ScanSettings] (مصدر الحقيقة). */
    val intelligenceLevel: StateFlow<IntelligenceLevel> = scanSettings.intelligenceLevel

    /** اختيار مستوى؛ يُكتب فورًا وبشكل دائم عبر [ScanSettings.setIntelligenceLevel]. */
    fun selectIntelligenceLevel(level: IntelligenceLevel) {
        scanSettings.setIntelligenceLevel(level)
    }
}