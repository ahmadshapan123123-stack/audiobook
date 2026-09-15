package com.example.audiobook.presentation.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.BuildConfig
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.ThemePreference
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed

private val SPEED_OPTIONS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SLEEP_DURATIONS = listOf(5, 10, 15, 30, 45, 60)

/**
 * شاشة الإعدادات الشاملة: المظهر، التشغيل، مؤقت النوم، مستوى الذكاء في كشف
 * الإصدارات، المكتبة، الإشعارات، البيانات والتخزين، وعن أثير.
 *
 * كل الخيارات تحكُم داخل الشاشة نفسها (Radios/Switches) — وليست مجرد لوحة تنقل.
 * [R8-النقطة 2] تحافظ على عقد الوصولية: نصوص الخيارات الثلاثة لمستوى الذكاء
 * وشرح القيد الصارم وزر الرجوع ≥ 48dp وعدم القصّ مع خط مكبّر.
 */
@Composable
fun SettingsScreen(
    themePreference: ThemePreference,
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenLibraryRoots: (() -> Unit)? = null,
    onScanNow: (() -> Unit)? = null
) {
    val level by viewModel.intelligenceLevel.collectAsStateWithLifecycle()
    val defaultSpeed by viewModel.defaultSpeed.collectAsStateWithLifecycle()
    val autoResume by viewModel.autoResume.collectAsStateWithLifecycle()
    val defaultSleep by viewModel.defaultSleepMinutes.collectAsStateWithLifecycle()
    val autoExtend by viewModel.autoExtendSleep.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
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

            // ── المظهر ──
            SettingsSectionLabel(stringResource(R.string.settings_appearance))
            ThemeOption(
                title = stringResource(R.string.settings_theme_light),
                description = stringResource(R.string.settings_theme_light_desc),
                selected = themePreference.mode == AppThemeMode.LIGHT,
                onSelect = { themePreference.updateMode(AppThemeMode.LIGHT) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_dark),
                description = stringResource(R.string.settings_theme_dark_desc),
                selected = themePreference.mode == AppThemeMode.DARK,
                onSelect = { themePreference.updateMode(AppThemeMode.DARK) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_amoled),
                description = stringResource(R.string.settings_theme_amoled_desc),
                selected = themePreference.mode == AppThemeMode.AMOLED,
                onSelect = { themePreference.updateMode(AppThemeMode.AMOLED) }
            )

            // ── مستوى الذكاء في كشف الإصدارات (عقد الوصولية R8) ──
            SettingsSectionLabel(stringResource(R.string.settings_intelligence))
            Text(
                stringResource(R.string.settings_intelligence_desc),
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

            // ── التشغيل ──
            SettingsSectionLabel(stringResource(R.string.settings_playback))
            SettingsCardGroup {
                SettingsOptionGrid(
                    label = stringResource(R.string.settings_default_speed),
                    options = SPEED_OPTIONS.map { speedLabel(it) },
                    selectedIndex = SPEED_OPTIONS.indexOf(defaultSpeed).coerceAtLeast(0),
                    onSelect = { viewModel.setDefaultSpeed(SPEED_OPTIONS[it]) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_resume),
                    subtitle = stringResource(R.string.settings_auto_resume_desc),
                    checked = autoResume,
                    onCheckedChange = { viewModel.setAutoResume(it) }
                )
            }

            // ── مؤقت النوم ──
            SettingsSectionLabel(stringResource(R.string.settings_sleep_timer))
            SettingsCardGroup {
                SettingsOptionGrid(
                    label = stringResource(R.string.settings_default_duration),
                    options = SLEEP_DURATIONS.map { stringResource(R.string.listen_now_time_minutes, it) },
                    selectedIndex = SLEEP_DURATIONS.indexOf(defaultSleep).coerceAtLeast(0),
                    onSelect = { viewModel.setDefaultSleepMinutes(SLEEP_DURATIONS[it]) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_extend),
                    subtitle = stringResource(R.string.settings_auto_extend_desc),
                    checked = autoExtend,
                    onCheckedChange = { viewModel.setAutoExtendSleep(it) }
                )
            }

            // ── المكتبة ──
            if (onOpenLibraryRoots != null || onScanNow != null) {
                SettingsSectionLabel(stringResource(R.string.settings_library))
                SettingsCardGroup {
                    if (onOpenLibraryRoots != null) {
                        SettingsNavRow(
                            title = stringResource(R.string.settings_library_roots),
                            subtitle = stringResource(R.string.settings_library_roots_desc),
                            onClick = onOpenLibraryRoots
                        )
                    }
                    if (onOpenLibraryRoots != null && onScanNow != null) {
                        SettingsDivider()
                    }
                    if (onScanNow != null) {
                        SettingsActionRow(
                            title = stringResource(R.string.settings_scan_now),
                            subtitle = stringResource(R.string.settings_scan_now_desc),
                            onClick = onScanNow
                        )
                    }
                }
            }

            // ── الإشعارات ──
            SettingsSectionLabel(stringResource(R.string.settings_notifications))
            SettingsCardGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = notifications,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
            }

            // ── البيانات والتخزين ──
            SettingsSectionLabel(stringResource(R.string.settings_storage))
            SettingsCardGroup {
                val context = LocalContext.current
                var cacheCleared by remember { mutableStateOf(false) }
                SettingsActionRow(
                    title = stringResource(R.string.settings_clear_cache),
                    subtitle = stringResource(
                        if (cacheCleared) R.string.settings_clear_cache_confirm else R.string.settings_clear_cache_desc
                    ),
                    onClick = {
                        clearCacheInternal(context)
                        cacheCleared = true
                    }
                )
            }

            // ── عن أثير ──
            SettingsSectionLabel(stringResource(R.string.settings_about))
            SettingsCardGroup {
                SettingsRowContent(title = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) {
                    Text(
                        stringResource(R.string.settings_about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun clearCacheInternal(context: Context) {
    runCatching {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

private fun speedLabel(speed: Float): String {
    val trimmed = if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()
    return "$trimmed×"
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

@Composable
private fun SettingsCardGroup(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppSpacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.xs)) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = AppSpacing.md),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    )
}

/** شبكة خيارات 3 أعمدة (آمنة مع الخط المكبّر — لا لفّ أفقي ولا قصّ). */
@Composable
private fun SettingsOptionGrid(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    SettingsRowContent(title = label)
    options.chunked(3).forEach { rowOptions ->
        val startIndex = options.indexOf(rowOptions.first())
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            rowOptions.forEachIndexed { index, option ->
                val optionIndex = startIndex + index
                SettingsOptionCell(
                    label = option,
                    selected = optionIndex == selectedIndex,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(optionIndex) }
                )
            }
        }
    }
}

@Composable
private fun SettingsOptionCell(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(container)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowText(title, subtitle, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = onClick).padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowText(title, subtitle, modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun SettingsActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    SettingsNavRow(title = title, subtitle = subtitle, onClick = onClick)
}

@Composable
private fun SettingsRowContent(title: String, modifier: Modifier = Modifier, subtitle: @Composable () -> Unit = {}) {
    Column(
        modifier = modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        subtitle()
    }
}

@Composable
private fun SettingsRowText(title: String, subtitle: String, modifier: Modifier = Modifier) {
    SettingsRowContent(title = title, modifier = modifier) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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