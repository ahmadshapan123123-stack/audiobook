package com.example.audiobook.presentation.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.BuildConfig
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.bottomContentInset
import com.example.audiobook.presentation.theme.LocalAppAccent
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.io.File
import java.util.Locale

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SLEEP_DURATIONS = listOf(5, 10, 15, 30, 45, 60)
private const val DATABASE_FILE_NAME = "audiobook.db"

private fun checkPostNotifications(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

/**
 * شاشة الإعدادات الشاملة، بترتيب أقسام ثابت:
 * التشغيل، مؤقت النوم، المظهر، المكتبة والفحص، الإشعارات، البيانات والتخزين، عن أثير.
 *
 * كل الخيارات تحكُم داخل الشاشة نفسها (Radios/Switches) — وليست مجرد لوحة تنقل.
 * القراءة والكتابة تمرّان حصرًا عبر [SettingsViewModel] → [com.example.audiobook.data.preferences.AppSettings].
 * [R8-النقطة 2] تحافظ على عقد الوصولية: نصوص الخيارات الثلاثة لمستوى الذكاء
 * وشرح القيد الصارم وزر الرجوع ≥ 48dp وعدم القصّ مع خط مكبّر.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenLibraryRoots: (() -> Unit)? = null,
    onScanNow: (() -> Unit)? = null
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val level by viewModel.intelligenceLevel.collectAsStateWithLifecycle()
    val defaultSpeed by viewModel.defaultSpeed.collectAsStateWithLifecycle()
    val autoResume by viewModel.autoResume.collectAsStateWithLifecycle()
    val defaultSleep by viewModel.defaultSleepMinutes.collectAsStateWithLifecycle()
    val autoExtend by viewModel.autoExtendSleep.collectAsStateWithLifecycle()
    val notifications by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val mediaMinimal by viewModel.mediaNotificationMinimal.collectAsStateWithLifecycle()
    val sleepTimerNotif by viewModel.sleepTimerNotificationsEnabled.collectAsStateWithLifecycle()
    val saveMomentNotif by viewModel.saveMomentNotificationsEnabled.collectAsStateWithLifecycle()
    val bookCompletionNotif by viewModel.bookCompletionNotificationsEnabled.collectAsStateWithLifecycle()
    val dailyReminder by viewModel.dailyReminderEnabled.collectAsStateWithLifecycle()
    val dailyHour by viewModel.dailyReminderHour.collectAsStateWithLifecycle()
    val dailyMinute by viewModel.dailyReminderMinute.collectAsStateWithLifecycle()
    val resumeReminder by viewModel.resumeReminderEnabled.collectAsStateWithLifecycle()
    val hasDemoData by viewModel.hasSeededDemoData.collectAsStateWithLifecycle()
    val hasLibraryRoots by viewModel.hasLibraryRoots.collectAsStateWithLifecycle()
    var showDailyTimePicker by remember { mutableStateOf(false) }
    var showNoRootsDialog by remember { mutableStateOf(false) }
    var showRemoveDemoDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val context = LocalContext.current
    var postNotificationsGranted by remember { mutableStateOf(checkPostNotifications(context)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) postNotificationsGranted = checkPostNotifications(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(24.dp).padding(bottom = bottomContentInset()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CosmicScreenHeader(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle),
                collapsed = collapsed,
                onBack = if (showBack) onBack else null,
                backAsTextButton = true
            )

            // ── 1. التشغيل ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_playback),
                description = stringResource(R.string.settings_playback_desc)
            )
            SettingsCardGroup {
                SettingsOptionGrid(
                    label = stringResource(R.string.settings_default_speed),
                    options = SPEED_OPTIONS.map { stringResource(R.string.settings_speed_value, speedValue(it)) },
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

            // ── 2. مؤقت النوم ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_sleep_timer),
                description = stringResource(R.string.settings_sleep_timer_desc)
            )
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

            // ── 3. المظهر ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_appearance),
                description = stringResource(R.string.settings_appearance_desc)
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_light),
                description = stringResource(R.string.settings_theme_light_desc),
                selected = themeMode == AppThemeMode.LIGHT,
                onSelect = { viewModel.selectThemeMode(AppThemeMode.LIGHT) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_dark),
                description = stringResource(R.string.settings_theme_dark_desc),
                selected = themeMode == AppThemeMode.DARK,
                onSelect = { viewModel.selectThemeMode(AppThemeMode.DARK) }
            )
            ThemeOption(
                title = stringResource(R.string.settings_theme_amoled),
                description = stringResource(R.string.settings_theme_amoled_desc),
                selected = themeMode == AppThemeMode.AMOLED,
                onSelect = { viewModel.selectThemeMode(AppThemeMode.AMOLED) }
            )

            // ── 4. المكتبة والفحص ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_library),
                description = stringResource(R.string.settings_library_desc)
            )
            if (onOpenLibraryRoots != null || onScanNow != null) {
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
                            onClick = {
                                viewModel.refreshRootsCount()
                                if (hasLibraryRoots) {
                                    onScanNow()
                                } else {
                                    showNoRootsDialog = true
                                }
                            }
                        )
                    }
                }
            }

            // مستوى الذكاء في كشف الإصدارات (عقد الوصولية R8) — داخل قسم المكتبة والفحص.
            SettingsRowContent(title = stringResource(R.string.settings_intelligence)) {
                Text(
                    stringResource(R.string.settings_intelligence_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingsRadioCard(
                title = stringResource(R.string.settings_intelligence_conservative_title),
                description = stringResource(R.string.settings_intelligence_conservative_desc),
                selected = level == IntelligenceLevel.CONSERVATIVE,
                onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.CONSERVATIVE) }
            )
            SettingsRadioCard(
                title = stringResource(R.string.settings_intelligence_balanced_title),
                description = stringResource(R.string.settings_intelligence_balanced_desc),
                selected = level == IntelligenceLevel.BALANCED,
                onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.BALANCED) }
            )
            SettingsRadioCard(
                title = stringResource(R.string.settings_intelligence_aggressive_title),
                description = stringResource(R.string.settings_intelligence_aggressive_desc),
                selected = level == IntelligenceLevel.AGGRESSIVE,
                onSelect = { viewModel.selectIntelligenceLevel(IntelligenceLevel.AGGRESSIVE) }
            )
            Text(
                stringResource(R.string.settings_intelligence_strict_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // ── 5. الإشعارات ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_notifications),
                description = stringResource(R.string.settings_notifications_desc)
            )
            SettingsCardGroup {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notifications_title),
                    subtitle = stringResource(R.string.settings_notifications_row_desc),
                    checked = notifications,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
                if (notifications && !postNotificationsGranted) {
                    SettingsDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = { openNotificationSettings(context) }).padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsRowText(
                            title = stringResource(R.string.settings_notif_permission_hint),
                            subtitle = stringResource(R.string.settings_notif_permission_hint_desc),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            SettingsCardGroup {
                SettingsOptionGrid(
                    label = stringResource(R.string.settings_notif_media_title),
                    options = listOf(
                        stringResource(R.string.settings_notif_media_full),
                        stringResource(R.string.settings_notif_media_minimal)
                    ),
                    selectedIndex = if (mediaMinimal) 1 else 0,
                    onSelect = { viewModel.setMediaNotificationMinimal(it == 1) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_sleep_timer),
                    subtitle = stringResource(R.string.settings_notif_sleep_timer_desc),
                    checked = sleepTimerNotif,
                    onCheckedChange = { viewModel.setSleepTimerNotificationsEnabled(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_save_moment),
                    subtitle = stringResource(R.string.settings_notif_save_moment_desc),
                    checked = saveMomentNotif,
                    onCheckedChange = { viewModel.setSaveMomentNotificationsEnabled(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_book_completed),
                    subtitle = stringResource(R.string.settings_notif_book_completed_desc),
                    checked = bookCompletionNotif,
                    onCheckedChange = { viewModel.setBookCompletionNotificationsEnabled(it) }
                )
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_daily_reminder),
                    subtitle = stringResource(R.string.settings_notif_daily_reminder_desc),
                    checked = dailyReminder,
                    onCheckedChange = { viewModel.setDailyReminderEnabled(it) }
                )
                if (dailyReminder) {
                    SettingsDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().minTouchTarget().clickable(onClick = { showDailyTimePicker = true }).padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsRowText(
                            title = stringResource(R.string.settings_notif_daily_time),
                            subtitle = String.format(Locale.US, "%02d:%02d", dailyHour, dailyMinute),
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                SettingsDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_notif_resume_reminder),
                    subtitle = stringResource(R.string.settings_notif_resume_reminder_desc),
                    checked = resumeReminder,
                    onCheckedChange = { viewModel.setResumeReminderEnabled(it) }
                )
            }
            if (showDailyTimePicker) {
                val timeState = rememberTimePickerState(
                    initialHour = dailyHour,
                    initialMinute = dailyMinute,
                    is24Hour = true
                )
                AlertDialog(
                    onDismissRequest = { showDailyTimePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.setDailyReminderTime(timeState.hour, timeState.minute)
                            showDailyTimePicker = false
                        }) { Text(stringResource(R.string.settings_time_save)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDailyTimePicker = false }) { Text(stringResource(R.string.settings_time_cancel)) }
                    },
                    title = { Text(stringResource(R.string.settings_notif_daily_time)) },
                    text = { TimePicker(state = timeState) }
                )
            }

            // ── 6. البيانات والتخزين ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_storage),
                description = stringResource(R.string.settings_storage_desc)
            )
            SettingsCardGroup {
                val context = LocalContext.current
                var cacheCleared by remember { mutableStateOf(false) }
                val databaseSize = remember { formatBytes(databaseSizeBytes(context)) }
                SettingsRowContent(title = stringResource(R.string.settings_storage_db_size)) {
                    Text(
                        databaseSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SettingsDivider()
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
                if (hasDemoData) {
                    SettingsDivider()
                    SettingsActionRow(
                        title = stringResource(R.string.settings_remove_demo),
                        subtitle = stringResource(R.string.settings_remove_demo_desc),
                        onClick = { showRemoveDemoDialog = true }
                    )
                }
            }

            // ── 7. عن أثير ──
            SettingsSectionLabel(
                text = stringResource(R.string.settings_about),
                description = stringResource(R.string.settings_about_section_desc)
            )
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

    if (showNoRootsDialog) {
        AlertDialog(
            onDismissRequest = { showNoRootsDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showNoRootsDialog = false
                    onOpenLibraryRoots?.invoke()
                }) { Text(stringResource(R.string.settings_add_folder)) }
            },
            dismissButton = {
                TextButton(onClick = { showNoRootsDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.settings_no_roots_title)) },
            text = { Text(stringResource(R.string.settings_no_roots_desc)) }
        )
    }

    if (showRemoveDemoDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDemoDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDemoDialog = false
                    viewModel.removeDemoData()
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDemoDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
            title = { Text(stringResource(R.string.settings_remove_demo)) },
            text = { Text(stringResource(R.string.settings_remove_demo_confirm)) }
        )
    }
}
}

/** حذف الملفات المؤقتة فقط؛ لا يمسّ قاعدة البيانات ولا الإعدادات ولا ملفات الكتب. */
private fun clearCacheInternal(context: Context) {
    runCatching {
        context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }
}

/** مجموع أحجام ملف قاعدة البيانات وملفات WAL/SHM المرافقة له. */
private fun databaseSizeBytes(context: Context): Long {
    val database = context.getDatabasePath(DATABASE_FILE_NAME)
    val candidates = listOf(
        database,
        File(database.path + "-wal"),
        File(database.path + "-shm")
    )
    return candidates.filter { it.exists() }.sumOf { it.length() }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kilobytes = bytes / 1024.0
    if (kilobytes < 1024.0) return String.format(Locale.US, "%.0f KB", kilobytes)
    val megabytes = kilobytes / 1024.0
    if (megabytes < 1024.0) return String.format(Locale.US, "%.1f MB", megabytes)
    return String.format(Locale.US, "%.2f GB", megabytes / 1024.0)
}

private fun speedValue(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()

@Composable
private fun SettingsSectionLabel(text: String, description: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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

/**
 * شبكة خيارات متناظرة بلا خلايا فارغة مشوّهة: أعمدة 3 للقوائم القصيرة و4 للأطول،
 * والحشوة الأخيرة تُملأ بفراغات بوزن متساوٍ للحفاظ على عرض الأعمدة (آمنة مع الخط المكبّر).
 */
@Composable
private fun SettingsOptionGrid(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    SettingsRowContent(title = label)
    val columns = if (options.size <= 6) 3 else 4
    options.chunked(columns).forEachIndexed { rowIndex, rowOptions ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            rowOptions.forEachIndexed { index, option ->
                val optionIndex = rowIndex * columns + index
                SettingsOptionCell(
                    label = option,
                    selected = optionIndex == selectedIndex,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(optionIndex) }
                )
            }
            repeat(columns - rowOptions.size) {
                Spacer(Modifier.weight(1f))
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
    val appAccent = LocalAppAccent.current
    val shape = RoundedCornerShape(AppSpacing.sm)
    val container = if (selected) appAccent.accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    val content = if (selected) appAccent.onAccent else MaterialTheme.colorScheme.onSurface
    val borderColor = if (selected) appAccent.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
    Box(
        modifier = modifier
            .minTouchTarget()
            .clip(shape)
            .background(container)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val appAccent = LocalAppAccent.current
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsRowText(title, subtitle, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = appAccent.onAccent,
                checkedTrackColor = appAccent.accent,
                checkedBorderColor = appAccent.accent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
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
            val appAccent = LocalAppAccent.current
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = appAccent.accent,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
