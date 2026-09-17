package com.example.audiobook.presentation.theme

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * حالة الرأس الكوني — تُملي على الـShell أي شريط زجاجي علوي يُرسم فوق الشاشة.
 * تُحدَّث من كل شاشة عبر [CosmicScreenHeader] ثم يقرأها MainActivity لرسم [CosmicTopBar].
 */
class CosmicHeaderState {
    var title by mutableStateOf("")
    var subtitle by mutableStateOf<String?>(null)
    var collapsed by mutableStateOf(false)
    var onBack by mutableStateOf<(() -> Unit)?>(null)

    fun reset() {
        title = ""
        subtitle = null
        collapsed = false
        onBack = null
    }
}

val LocalCosmicHeader = staticCompositionLocalOf { CosmicHeaderState() }

/** هل انضمّ المحور بعد تجاوز حد معيّن؟ تُستخدم لتفعيل الرأس المنهار. */
@Composable
fun rememberHeaderCollapsed(scroll: ScrollState, threshold: Dp = 64.dp): Boolean {
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    return remember(scroll) { derivedStateOf { scroll.value >= thresholdPx } }.value
}

/** ستايل الزجاج الكوني الموحّد (حبر + سديم خافت) — شفاف بما يكفي ليظهر البلور خلفه. */
@Composable
fun cosmicGlassStyle(
    backgroundColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
    tintAlpha: Float = 0.12f,
    blurRadius: Dp = 30.dp
): HazeStyle = HazeStyle(
    backgroundColor = backgroundColor,
    tint = HazeTint(Cosmic.StardustViolet.copy(alpha = tintAlpha)),
    blurRadius = blurRadius
)

/**
 * ستايل شريط التنقل السفلي والمشغّل المصغّر: زجاج بتقنية Haze حقيقية،
 * سطحه يتبع الوضع اللوني (فاتح/داكن/AMOLED) مع عمق حبري خافت.
 */
@Composable
fun navBarGlassStyle(
    mode: AppThemeMode = AppThemeMode.DARK,
    tintAlpha: Float? = null,
    blurRadius: Dp = 30.dp
): HazeStyle {
    val surface = when (mode) {
        AppThemeMode.LIGHT -> Color(0xFFF2EEE3)
        AppThemeMode.DARK -> Color(0xFF0B0F24)
        AppThemeMode.AMOLED -> Color(0xFF06070C)
    }
    val tintAlphaResolved = tintAlpha ?: if (mode == AppThemeMode.LIGHT) 0.20f else 0.35f
    return HazeStyle(
        backgroundColor = surface.copy(alpha = 0.90f),
        tint = HazeTint(Cosmic.InkBottom.copy(alpha = tintAlphaResolved)),
        blurRadius = blurRadius
    )
}

/**
 * ستايل زجاجي للوجو الدائري في شريط التنقل السفلي: نفس منطق الزجاج لكن بشفافية أقل
 * (0.55-0.65) ليظهر كعنصر تفاعلي بارز، ومعه توهج تيل يُرسم على المكوّن.
 */
@Composable
fun navLogoGlassStyle(
    tintAlpha: Float = 0.60f,
    blurRadius: Dp = 22.dp
): HazeStyle = HazeStyle(
    backgroundColor = Cosmic.InkBottom.copy(alpha = 0.55f),
    tint = HazeTint(Cosmic.TealBright.copy(alpha = tintAlpha)),
    blurRadius = blurRadius
)

/** العنوان الكبير داخل محتوى التمرير (يختفي تحت الشريط الزجاجي عند الانضغاط). */
@Composable
fun CosmicLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text(
            title,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * الرأس القابل لإعادة الاستخدام: يُرسم العنوان الكبير داخل المحتوى، ويُبلّغ الـShell
 * بالحالة (collapsed + onBack) ليُعرض الشريط الزجاجي العلوي عند الالتصاق بالأعلى.
 */
@Composable
fun CosmicScreenHeader(
    title: String,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backAsTextButton: Boolean = false,
    backContentDescription: String = "رجوع",
    compact: Boolean = false
) {
    val header = LocalCosmicHeader.current
    SideEffect {
        header.title = title
        header.subtitle = subtitle
        header.collapsed = collapsed
        header.onBack = onBack
    }
    if (onBack != null) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            if (backAsTextButton) {
                TextButton(onClick = onBack, modifier = Modifier.minTouchTarget()) { Text(backContentDescription) }
            } else {
                IconButton(onClick = onBack, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backContentDescription)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (compact) CosmicCompactTitle(title) else CosmicLargeTitle(title = title)
                if (!compact && subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    } else {
        if (compact) CosmicCompactTitle(title, modifier = modifier) else CosmicLargeTitle(title = title, subtitle = subtitle, modifier = modifier)
    }
}

@Composable
private fun CosmicCompactTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier = modifier,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** شريط زجاجي علوي مضغوط يُرسم على مستوى الـShell فوق الشاشة. */
@Composable
fun CosmicTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "رجوع"
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = AppSpacing.md, bottomEnd = AppSpacing.md))
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.minTouchTarget()) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = backContentDescription, tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}