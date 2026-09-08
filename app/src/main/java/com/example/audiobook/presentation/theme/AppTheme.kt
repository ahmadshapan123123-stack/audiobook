package com.example.audiobook.presentation.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class AppThemeMode { LIGHT, DARK, AMOLED }

object AppSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val xxl = 40.dp
}

private val LightBackground = Color(0xFFF5F1EA)
private val LightSurface = Color(0xFFFFFBF5)
private val LightText = Color(0xFF263238)
private val LightMuted = Color(0xFF58666B)
private val LightPrimary = Color(0xFF176B69)
private val LightSecondary = Color(0xFFB05A3C)

private val DarkBackground = Color(0xFF18242B)
private val DarkSurface = Color(0xFF223139)
private val DarkText = Color(0xFFE5E9E7)
private val DarkMuted = Color(0xFFB7C4C3)
private val DarkPrimary = Color(0xFF72D1C3)
private val DarkSecondary = Color(0xFFE29A78)

private val AmoledSurface = Color(0xFF0D0D0D)
private val AmoledText = Color(0xFFE5E9E7)
private val AmoledMuted = Color(0xFFB7C4C3)
private val AmoledPrimary = Color(0xFF72D1C3)
private val AmoledSecondary = Color(0xFFE29A78)

private fun lightScheme() = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    secondary = LightSecondary,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = Color(0xFFE5E0D8),
    onSurfaceVariant = LightMuted
)

private fun darkScheme(amoled: Boolean): ColorScheme = darkColorScheme(
    primary = if (amoled) AmoledPrimary else DarkPrimary,
    onPrimary = Color(0xFF073A36),
    secondary = if (amoled) AmoledSecondary else DarkSecondary,
    onSecondary = Color(0xFF35140B),
    background = if (amoled) Color.Black else DarkBackground,
    onBackground = if (amoled) AmoledText else DarkText,
    surface = if (amoled) AmoledSurface else DarkSurface,
    onSurface = if (amoled) AmoledText else DarkText,
    surfaceVariant = if (amoled) Color(0xFF1B1B1B) else Color(0xFF304049),
    onSurfaceVariant = if (amoled) AmoledMuted else DarkMuted
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 42.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 26.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
)

@Composable
fun AudiobookTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val scheme = appColorScheme(mode)
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

fun appColorScheme(mode: AppThemeMode): ColorScheme = when (mode) {
        AppThemeMode.LIGHT -> lightScheme()
        AppThemeMode.DARK -> darkScheme(amoled = false)
        AppThemeMode.AMOLED -> darkScheme(amoled = true)
    }

@Immutable
class ThemePreference(context: Context) {
    private val preferences = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var mode by mutableStateOf(load())
        private set

    fun updateMode(value: AppThemeMode) {
        mode = value
        preferences.edit().putString(KEY_MODE, value.name).apply()
    }

    private fun load() = preferences.getString(KEY_MODE, AppThemeMode.DARK.name)
        ?.let { runCatching { AppThemeMode.valueOf(it) }.getOrDefault(AppThemeMode.DARK) }
        ?: AppThemeMode.DARK

    private companion object { const val KEY_MODE = "theme_mode" }
}