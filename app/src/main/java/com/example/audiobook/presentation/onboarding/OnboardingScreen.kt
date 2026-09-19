package com.example.audiobook.presentation.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.LocalAppAccent

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    onAddFolder: () -> Unit
) {
    val steps = listOf(
        Triple(
            stringResource(R.string.onboarding_step1_title),
            stringResource(R.string.onboarding_step1_desc),
            null
        ),
        Triple(
            stringResource(R.string.onboarding_step2_title),
            stringResource(R.string.onboarding_step2_desc),
            null
        ),
        Triple(
            stringResource(R.string.onboarding_step3_title),
            stringResource(R.string.onboarding_step3_desc),
            null
        )
    )

    var currentStep by remember { mutableIntStateOf(0) }
    val appAccent = LocalAppAccent.current

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(48.dp))

        // Step indicators
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.indices.forEach { index ->
                val isSelected = index == currentStep
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .size(if (isSelected) 12.dp else 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    drawRect(
                        color = if (isSelected) appAccent.accent
                        else appAccent.accent.copy(alpha = 0.3f)
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Step content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = steps[currentStep].first,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = steps[currentStep].second,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (currentStep > 0) {
                TextButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.back))
                }
            }

            if (currentStep < steps.lastIndex) {
                TextButton(
                    onClick = { currentStep++ },
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(appAccent.accent.copy(alpha = 0.12f))
                ) {
                    Text(stringResource(R.string.next), color = appAccent.accent)
                }
            } else {
                TextButton(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(appAccent.accent)
                ) {
                    Text(stringResource(R.string.onboarding_start), color = appAccent.onAccent)
                }
            }
        }
    }
}
