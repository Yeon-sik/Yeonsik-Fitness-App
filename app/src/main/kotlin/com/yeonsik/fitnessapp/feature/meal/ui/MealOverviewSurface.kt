package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

/**
 * The Meal day overview is content, so it stays a flat card with a hairline: glass is for
 * floating controls only, and a card sampling its own gradient only cost RenderEffect work.
 */
@Composable
internal fun MealOverviewSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    FitnessCard(modifier.fillMaxWidth(), outlined = true) {
        Column(
            Modifier.fillMaxWidth().padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small),
            content = content
        )
    }
}

@Composable
internal fun MealDailyMetrics(count: Int, calories: String) {
    if (LocalDensity.current.fontScale >= 1.35f) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
            MealOverviewMetric("식단", count.toString(), unit = "끼")
            MealOverviewMetric("열량", calories, unit = "kcal")
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap)
        ) {
            MealOverviewMetric("식단", count.toString(), Modifier.weight(1f), "끼")
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
        MealOverviewValue(value, unit, MaterialTheme.typography.headlineSmall)
    }
}

/** Keep a measurement together, including when the system uses larger text. */
@Composable
internal fun MealOverviewValue(value: String, unit: String?, style: TextStyle) {
    val unitStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Normal)
    Text(buildAnnotatedString {
        append(value)
        if (unit != null) withStyle(unitStyle.toSpanStyle()) { append("\u00A0$unit") }
    }, style = style, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
}
