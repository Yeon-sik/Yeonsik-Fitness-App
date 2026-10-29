package com.yeonsik.fitnessapp.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** A nutrient's included amounts follow its total, without implying another addition. */
@Composable
internal fun FitnessNutrientSubgroup(
    parentLabel: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .padding(start = AppSpacing.small)
            .testTag("nutrition-included-$parentLabel"),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        Text(
            "총량에 포함",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}
