package com.example.audiobook.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.ThemePreference
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed

/**
 * [R8-النقطة 2] شاشة الإعدادات: اختيار مستوى الذكاء في Smart Edition Detection
 * من ثلاثة خيارات (محافظ/متوازن/ذكي) كـRadio، مع سطر شرح تحت كل خيار يؤكد صراحةً
 * أن القيد الصارم (راوٍ مختلف أو فرق مدة > 15% يمنع الدمج) يظل ساريًا في المستويات
 * الثلاثة كلها وفق الـSpec.
 *
 * القيمة تُقرأ/تُكتب عبر [SettingsViewModel] ← [ScanSettings] (SharedPreferences)
 * فيقرأها [ScanRoot] فعلًا في الفحص التالي.
 */
@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val level by viewModel.intelligenceLevel.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = 168.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        CosmicScreenHeader(
            title = "الإعدادات",
            subtitle = "تخصيص أثير",
            collapsed = collapsed,
            onBack = if (showBack) onBack else null,
            backAsTextButton = true
        )

        Text("المظهر", style = MaterialTheme.typography.titleLarge)
        ThemeOption(
            title = "فاتح",
            description = "خلفية فاتحة مريحة للنهار.",
            selected = themePreference.mode == AppThemeMode.LIGHT,
            onSelect = { themePreference.updateMode(AppThemeMode.LIGHT) }
        )
        ThemeOption(
            title = "داكن",
            description = "خلفية داكنة لطيفة على العينين ليلًا.",
            selected = themePreference.mode == AppThemeMode.DARK,
            onSelect = { themePreference.updateMode(AppThemeMode.DARK) }
        )
        ThemeOption(
            title = "أموليد",
            description = "أسود كامل للشاشات OLED.",
            selected = themePreference.mode == AppThemeMode.AMOLED,
            onSelect = { themePreference.updateMode(AppThemeMode.AMOLED) }
        )

        Text("مستوى الذكاء في كشف الإصدارات", style = MaterialTheme.typography.titleLarge)
        Text(
            "كيف يتصرف الفحص عند رؤية مجلدين قد يكونان لنفس الكتاب.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SettingsRadioCard(
            title = "محافظ",
            description = "لا دمج تلقائي إطلاقًا؛ كل تجميع مقترح يُعرض عليك في شاشة المراجعة.",
            selected = level == IntelligenceLevel.CONSERVATIVE,
            onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE) }
        )
        SettingsRadioCard(
            title = "متوازن",
            description = "دمج تلقائي فقط عند تطابق إشارات قوية جدًا (ثقة شديدة الارتفاع)، وما دون ذلك يُعرض للمراجعة. الافتراضي.",
            selected = level == IntelligenceLevel.BALANCED,
            onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.BALANCED) }
        )
        SettingsRadioCard(
            title = "ذكي",
            description = "اقتراحات أوسع تُعرض في شاشة المراجعة، لكن لا دمج تلقائي صامت إطلاقًا.",
            selected = level == IntelligenceLevel.AGGRESSIVE,
            onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.AGGRESSIVE) }
        )

        Text(
            "ابتعد عن الروايات المتباينة: في المستويات الثلاثة يظل القيد الصارم ساريًا — راوٍ مختلف واضح أو فرق مدة أكبر من 15% يمنع أي دمج مهما كانت الثقة.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    }
}

@Composable
private fun ThemeOption(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    SettingsRadioCard(title = title, description = description, selected = selected, onSelect = onSelect)
}

@Composable
private fun SettingsRadioCard(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.xs),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton
            ).padding(AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}