package com.example.audiobook.presentation.theme

import com.example.audiobook.domain.model.AppThemeMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun DesignSystemShowcase(mode: AppThemeMode, onModeChange: (AppThemeMode) -> Unit) {
    var sliderValue by remember { mutableFloatStateOf(0.42f) }
    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.lg).padding(bottom = bottomContentInset()),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Text("مساحة الاستماع", style = MaterialTheme.typography.displaySmall)
            Text("نظام تصميم مريح للقراءة والاستماع الطويل", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppBookCard("رحلة تجريبية", "كتاب صوتي محلي · ٤٢٪ مكتمل", Modifier.fillMaxWidth())
            Text("المظهر", style = MaterialTheme.typography.titleLarge)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                AppModeChip("فاتح", mode == AppThemeMode.LIGHT) { onModeChange(AppThemeMode.LIGHT) }
                AppModeChip("داكن", mode == AppThemeMode.DARK) { onModeChange(AppThemeMode.DARK) }
                AppModeChip("AMOLED", mode == AppThemeMode.AMOLED) { onModeChange(AppThemeMode.AMOLED) }
            }
            Text("موضع الاستماع", style = MaterialTheme.typography.titleMedium)
            AppProgressSlider(sliderValue, onValueChange = { sliderValue = it })
            AppPrimaryButton("متابعة الاستماع", onClick = {}, modifier = Modifier.fillMaxWidth())
            Text("النص العربي يستخدم اتجاه RTL وتباعد أسطر مريح للملاحظات والفصول.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}