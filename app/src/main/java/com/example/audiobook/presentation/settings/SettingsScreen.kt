package com.example.audiobook.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.audiobook.presentation.theme.minTouchTarget

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
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val level by viewModel.intelligenceLevel.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text("رجوع") }
            Text("الإعدادات", style = MaterialTheme.typography.headlineSmall)
        }

        Text("مستوى الذكاء في كشف الإصدارات", style = MaterialTheme.typography.titleLarge)
        Text(
            "كيف يتصرف الفحص عند رؤية مجلدين قد يكونان لنفس الكتاب.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        IntelligenceLevelOption(
            option = IntelligenceLevel.CONSERVATIVE,
            title = "محافظ",
            description = "لا دمج تلقائي إطلاقًا؛ كل تجميع مقترح يُعرض عليك في شاشة المراجعة.",
            selected = level == IntelligenceLevel.CONSERVATIVE,
            onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE) }
        )
        IntelligenceLevelOption(
            option = IntelligenceLevel.BALANCED,
            title = "متوازن",
            description = "دمج تلقائي فقط عند تطابق إشارات قوية جدًا (ثقة شديدة الارتفاع)، وما دون ذلك يُعرض للمراجعة. الافتراضي.",
            selected = level == IntelligenceLevel.BALANCED,
            onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.BALANCED) }
        )
        IntelligenceLevelOption(
            option = IntelligenceLevel.AGGRESSIVE,
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

@Composable
private fun IntelligenceLevelOption(
    option: IntelligenceLevel,
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