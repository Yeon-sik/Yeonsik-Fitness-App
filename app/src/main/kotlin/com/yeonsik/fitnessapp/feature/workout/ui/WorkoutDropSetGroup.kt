package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetGroup
import com.yeonsik.fitnessapp.core.ui.FitnessShape
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing

/** A single outline groups input rows without shifting the existing table columns. */
@Composable
internal fun WorkoutDropSetGroup(
    group: WorkoutSetGroup,
    label: String,
    onAdd: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val outline = if (group.isDropGroup) Modifier
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), FitnessShape.input)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, FitnessShape.input)
        .testTag("workout-drop-group-${group.root.id}")
        .semantics { contentDescription = "$label 드롭세트 그룹" }
    else Modifier
    // This root and the caller's row slots remain stable when a normal set becomes D.
    Column(modifier.fillMaxWidth().then(outline), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
        if (group.isDropGroup) {
            Row(Modifier.fillMaxWidth().padding(horizontal = FitnessSpacing.small, vertical = FitnessSpacing.small),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                Text("$label · 드롭세트", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold)
                Text("하위 세트 ${group.sets.size}개", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
        if (group.isDropGroup) TextButton(onAdd, enabled = enabled,
            modifier = Modifier.fillMaxWidth().heightIn(min = FitnessSpacing.touch)
                .testTag("workout-drop-add-${group.root.id}")
                .semantics { contentDescription = "$label 하위 세트 추가" }) {
            Text("+ 하위 세트 추가")
        }
    }
}
