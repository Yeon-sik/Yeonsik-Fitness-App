package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.colorControls
import com.kashif_e.backdrop.highlight.Highlight
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

/** Local, self-contained sampling source for the Meal overview's restrained frosted surfaces. */
@Composable
internal fun MealOverviewGlassSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val highContrast = Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(AccessibilityManager::class.java)
            ?.isHighContrastTextEnabled == true
    val useGlass = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !highContrast
    if (useGlass) {
        val backdrop = rememberLayerBackdrop()
        Box(Modifier.fillMaxWidth().clip(FitnessShape.card)) {
            Box(
                Modifier.matchParentSize()
                    .layerBackdrop(backdrop)
                    .background(
                        Brush.linearGradient(
                            listOf(colors.primaryContainer, colors.surfaceContainerHigh)
                        )
                    )
            )
            Column(
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { FitnessShape.card },
                    effects = {
                        colorControls(saturation = 1.03f)
                        blur(7.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = { drawRect(colors.surface.copy(alpha = 0.82f)) }
                ).fillMaxWidth().padding(AppSpacing.card),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
                content = content
            )
        }
    } else {
        Column(
            Modifier.fillMaxWidth().clip(FitnessShape.card)
                .background(colors.surfaceContainerHigh)
                .border(
                    1.dp,
                    if (highContrast) colors.onSurface else colors.outlineVariant,
                    FitnessShape.card
                )
                .padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
            content = content
        )
    }
}

@Composable
internal fun MealDailyMetrics(count: Int, calories: String) {
    if (LocalDensity.current.fontScale >= 1.35f) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            MealOverviewMetric("식사", "${count}끼")
            MealOverviewMetric("열량", calories, unit = "kcal")
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)
        ) {
            MealOverviewMetric("식사", "${count}끼", Modifier.weight(1f))
            MealOverviewMetric("열량", calories, Modifier.weight(1f), "kcal")
        }
    }
}

@Composable
private fun MealOverviewMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold)
        if (unit != null) Text(unit, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
