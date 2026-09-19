package com.example.audiobook.presentation.theme

import com.example.audiobook.domain.model.AppThemeMode
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

/**
 * شاشة تجريبية C1: خلفية كونية + نجوم ساكنة + الخطوط الثلاثة الظاهرة بوضوح.
 * مؤقتة لهذه المرحلة، ويُعاد استخدام عناصرها عند بناء الشاشات الفعلية.
 */
@Composable
fun CosmicSmokeScreen(mode: AppThemeMode, onModeChange: (AppThemeMode) -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        AudiobookTheme(mode = mode) {
            Box(modifier = Modifier.fillMaxSize()) {
                CosmicBackground(mode = mode, modifier = Modifier.fillMaxSize())
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.lg).padding(bottom = bottomContentInset()),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Spacer(Modifier.height(AppSpacing.md))
                    Text(
                        text = "أثير",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "هوية أثير الكوني — جلسة استماع تحت سماء سديمية",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Text(
                        text = "نص تجريبي بخط Cairo: هذا تطبيق استماع للكتب الصوتية العربية، يُصمَّم ليلًا ونهارًا ليحفظ موضعك ولا يشتّت انتباهك.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "سطر آخر للتأكد من قراءة الفقرات العربية كاملة، مع تهيئة تباعد الأسطر للاستماع الطويل المريح.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(
                            text = "ATHER",
                            fontFamily = SpaceGroteskFamily,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 4.sp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = " · 00:43:12 — 45:00",
                            fontFamily = SpaceGroteskFamily,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                    Text("المظهر", style = MaterialTheme.typography.titleLarge)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        AppModeChip("سديم", mode == AppThemeMode.LIGHT) { onModeChange(AppThemeMode.LIGHT) }
                        AppModeChip("ليل", mode == AppThemeMode.DARK) { onModeChange(AppThemeMode.DARK) }
                        AppModeChip("عمق الفضاء", mode == AppThemeMode.AMOLED) { onModeChange(AppThemeMode.AMOLED) }
                        }
                    Spacer(Modifier.height(AppSpacing.md))
                    Text("شرائح زجاجية (Haze) وحركة النجوم تبدأ من C2/C7", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}