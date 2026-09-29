package com.example.audiobook.presentation.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audiobook.R
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.domain.model.LogoColorMode
import com.example.audiobook.presentation.theme.AtherAccent
import kotlinx.coroutines.delay

@Composable
fun AtherSplash(
    onFinished: () -> Unit,
    durationMs: Long = 1_600L,
    themeMode: AppThemeMode = AppThemeMode.DARK,
    logoColorMode: LogoColorMode = LogoColorMode.AUTO
) {
    LaunchedEffect(Unit) {
        delay(durationMs)
        onFinished()
    }
    val overlay = logoOverlayColor(themeMode, logoColorMode)
    val glowColor = overlay ?: Color(0xFF0B132B)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = 0.22f),
                                        glowColor.copy(alpha = 0.08f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    radius = size.width * 0.62f
                                )
                            )
                        }
                )
                Image(
                    painter = painterResource(id = R.drawable.app_logo_source),
                    contentDescription = null,
                    // FIX 4: بلا أي drawRect فوق الصورة — المستطيل كان يرسم
                    // على كامل حدود الـ160dp *بما فيها البكسلات الشفافة*
                    // فيظهر مربع داكن حول الشعار الشفاف. الـPNG شفاف أصلًا
                    // (الزوايا A=0) فلا حاجة لأي تغطية؛ لون اللون المختار
                    // ينعكس على التوهج الخلفي فقط.
                    modifier = Modifier.size(160.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            // FIX 5: سطر "أثير" العربي محذوف — موجود أصلًا داخل رسمة الشعار،
            // فيبقى "ATHER" اللاتيني عنوانًا فرعيًا وحيدًا بلا تكرار ثلاثي.
            Text(
                text = "ATHER",
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** لون صبغ الشعار بحسب الإعداد؛ [null] يعني الإبقاء على ألوان الشعار الأصلية. */
@Composable
private fun logoOverlayColor(themeMode: AppThemeMode, mode: LogoColorMode): Color? = when (mode) {
    LogoColorMode.AUTO -> null
    LogoColorMode.LIGHT -> Color(0xFFF7EDE1)
    LogoColorMode.DARK -> Color(0xFF101A36)
    LogoColorMode.ACCENT -> Color(AtherAccent.accentFor(themeMode, null, null, null))
}