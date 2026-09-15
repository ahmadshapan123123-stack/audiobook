package com.example.audiobook.presentation.theme

import android.content.Context
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
import com.example.audiobook.R

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

/** لوحة "أثير الكوني" الكنسية — تُشاركها الأغلفة، خلفيات الكتب، والـPlayer. */
object Cosmic {
    val InkTop = Color(0xFF05060F)
    val InkBottom = Color(0xFF0B0F24)
    val NavBarBlue = Color(0xFF16306B)
    val Teal = Color(0xFF2DD4BF)
    val TealBright = Color(0xFF45E0CC)
    val StardustViolet = Color(0xFF7C3AED)
    val StardustMagenta = Color(0xFFD946EF)
    val StardustAmber = Color(0xFFF59E0B)
    val MoonIce = Color(0xFFEDF2FF)
    val DawnTop = Color(0xFFF3EFFC)
    val DawnBottom = Color(0xFFE3EEF7)
}

// ===== الخطوط الثلاثة المضمّنة =====
private val ElMessiriFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.elmessiri_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.elmessiri_medium, FontWeight.Medium),
    androidx.compose.ui.text.font.Font(R.font.elmessiri_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.elmessiri_bold, FontWeight.Bold)
)

val CairoFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.cairo_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.cairo_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.cairo_bold, FontWeight.Bold)
)

/** أرقام الوقت والعلامة اللاتينية "ATHER". */
val SpaceGroteskFamily = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.spacegrotesk_regular, FontWeight.Normal),
    androidx.compose.ui.text.font.Font(R.font.spacegrotesk_semibold, FontWeight.SemiBold),
    androidx.compose.ui.text.font.Font(R.font.spacegrotesk_bold, FontWeight.Bold)
)

// ===== سديم (فاتح كوني) =====
private fun nebulaScheme() = lightColorScheme(
    primary = Color(0xFF0B6E63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB7F0E3),
    onPrimaryContainer = Color(0xFF003831),
    secondary = Color(0xFF945E00),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA5),
    onSecondaryContainer = Color(0xFF2F1B00),
    tertiary = Color(0xFF6D28D9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8DAFF),
    onTertiaryContainer = Color(0xFF25075A),
    background = Cosmic.DawnTop,
    onBackground = Color(0xFF1D1B3B),
    surface = Color(0xFFFBFAFF),
    onSurface = Color(0xFF1D1B3B),
    surfaceVariant = Color(0xFFE4E1F2),
    onSurfaceVariant = Color(0xFF57537A),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF31304C),
    inverseOnSurface = Color(0xFFF0EEFF),
    outline = Color(0xFF7A7399)
)

// ===== ليل (الافتراضي، كوني) =====
private fun nightScheme(amoled: Boolean) = darkColorScheme(
    primary = Cosmic.Teal,
    onPrimary = Color(0xFF043A34),
    primaryContainer = Color(0xFF0D4B44),
    onPrimaryContainer = Color(0xFF9FF0E2),
    secondary = Color(0xFFF5B942),
    onSecondary = Color(0xFF3E2A00),
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = Color(0xFFFFDEA5),
    tertiary = Color(0xFFB98CFF),
    onTertiary = Color(0xFF381566),
    tertiaryContainer = Color(0xFF57318A),
    onTertiaryContainer = Color(0xFFE9DBFF),
    background = if (amoled) Color.Black else Cosmic.InkBottom,
    onBackground = Cosmic.MoonIce,
    surface = if (amoled) Color(0xFF07070C) else Color(0xFF131A38),
    onSurface = Cosmic.MoonIce,
    surfaceVariant = if (amoled) Color(0xFF1A1B24) else Color(0xFF232C4F),
    onSurfaceVariant = Color(0xFFA9B2CC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFE3E4FF),
    inverseOnSurface = Color(0xFF31304C),
    outline = Color(0xFF71788F)
)

// ===== المقياس الكتابي الكوني: El Messiri للعناوين، Cairo للنصوص، Space Grotesk للأرقام =====
private val CosmicTypography = Typography(
    displayLarge = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 42.sp),
    displaySmall = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 38.sp),
    headlineLarge = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 30.sp),
    titleMedium = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 26.sp),
    titleSmall = TextStyle(fontFamily = ElMessiriFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = CairoFamily, fontSize = 16.sp, lineHeight = 28.sp),
    bodyMedium = TextStyle(fontFamily = CairoFamily, fontSize = 14.sp, lineHeight = 24.sp),
    bodySmall = TextStyle(fontFamily = CairoFamily, fontSize = 12.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = CairoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = CairoFamily, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = CairoFamily, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp)
)

@Composable
fun AudiobookTheme(mode: AppThemeMode, content: @Composable () -> Unit) {
    val scheme = appColorScheme(mode)
    MaterialTheme(colorScheme = scheme, typography = CosmicTypography, content = content)
}

fun appColorScheme(mode: AppThemeMode): ColorScheme = when (mode) {
        AppThemeMode.LIGHT -> nebulaScheme()
        AppThemeMode.DARK -> nightScheme(amoled = false)
        AppThemeMode.AMOLED -> nightScheme(amoled = true)
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