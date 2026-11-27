package com.yeonsik.fitness.shared.feature.workout.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit

const val WORKOUT_SET_CONTENT_SCALE = 0.9f

class OrdinarySetFields(
    val weight: String, val reps: String, val rir: String, val unit: MassUnit,
    val editWeight: (String) -> Unit, val editReps: (String) -> Unit, val editRir: (String) -> Unit,
    val next: KeyboardActions, val done: KeyboardActions, val enabled: Boolean = true
)

data class WorkoutSetInputColumns(
    val mainLabel: String,
    val secondLabel: String?,
    val hasRir: Boolean,
    val hasLoadState: Boolean,
    val widthScale: Float = 1f
) {
    val minimumWidth get() = (206.dp +
        (if (secondLabel != null) 50.dp else 0.dp) + (if (hasRir) 50.dp else 0.dp) +
        (if (hasLoadState) 66.dp else 0.dp)) * WORKOUT_SET_CONTENT_SCALE
}

@Composable
fun WorkoutSetTableHeader(columns: WorkoutSetInputColumns) {
    Column(Modifier.fillMaxWidth().testTag("workout-set-table-header")) {
        WorkoutSetTableRow(
            columns = columns,
            setContent = { WorkoutSetHeaderLabel("세트") },
            mainContent = { WorkoutSetHeaderLabel(columns.mainLabel) },
            secondContent = { WorkoutSetHeaderLabel(columns.secondLabel.orEmpty()) },
            rirContent = { WorkoutSetHeaderLabel("RIR") },
            completedContent = { WorkoutSetHeaderLabel("완료") },
            loadContent = { WorkoutSetHeaderLabel("부하") },
            removeContent = { WorkoutSetHeaderLabel("제거") }
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Header and editable rows share the same fitted columns. */
@Composable
fun WorkoutSetTableRow(
    columns: WorkoutSetInputColumns,
    modifier: Modifier = Modifier,
    ordinary: OrdinarySetFields? = null,
    setContent: @Composable () -> Unit,
    mainContent: @Composable () -> Unit = {},
    secondContent: @Composable () -> Unit = {},
    rirContent: @Composable () -> Unit = {},
    completedContent: @Composable () -> Unit,
    loadContent: @Composable () -> Unit,
    removeContent: @Composable () -> Unit
) {
    val columnScale = LocalDensity.current.fontScale.coerceAtLeast(1f) * WORKOUT_SET_CONTENT_SCALE * columns.widthScale
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(2.dp * columnScale),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(48.dp * columnScale), contentAlignment = Alignment.Center) { setContent() }
        val next = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            if (ordinary == null) mainContent() else WorkoutSetValueField(
                ordinary.weight, ordinary.editWeight, "중량(${ordinary.unit.symbol()})",
                next.copy(keyboardType = KeyboardType.Decimal), ordinary.next, suffix = ordinary.unit.symbol(), enabled = ordinary.enabled)
        }
        if (columns.secondLabel != null) {
            Box(Modifier.width(48.dp * columnScale), contentAlignment = Alignment.Center) {
                if (ordinary == null) secondContent() else WorkoutSetValueField(
                    ordinary.reps, ordinary.editReps, columns.secondLabel, next, ordinary.next, enabled = ordinary.enabled)
            }
        }
        if (columns.hasRir) {
            Box(Modifier.width(48.dp * columnScale), contentAlignment = Alignment.Center) {
                if (ordinary == null) rirContent() else WorkoutSetValueField(
                    ordinary.rir, ordinary.editRir, "RIR", next.copy(imeAction = ImeAction.Done), ordinary.done, enabled = ordinary.enabled)
            }
        }
        Box(Modifier.width(48.dp * columnScale), contentAlignment = Alignment.Center) { completedContent() }
        if (columns.hasLoadState) {
            Box(Modifier.width(64.dp * columnScale), contentAlignment = Alignment.Center) { loadContent() }
        }
        Box(Modifier.width(48.dp * columnScale), contentAlignment = Alignment.Center) { removeContent() }
    }
}

@Composable
private fun WorkoutSetHeaderLabel(label: String) {
    val style = MaterialTheme.typography.labelMedium
    Text(
        label,
        modifier = Modifier.fillMaxWidth().padding(vertical = FitnessComposeTokens.FORM_ITEM_GAP_DP.dp)
            .semantics { heading() },
        style = style.copy(fontSize = style.fontSize * WORKOUT_SET_CONTENT_SCALE,
            lineHeight = style.lineHeight * WORKOUT_SET_CONTENT_SCALE),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

/** Workout-owned completion control; its appearance always follows the app palette. */
@Composable
fun WorkoutSetCompletionToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean,
    saving: Boolean,
    actionColor: Color,
    onActionColor: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(7.dp)
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Checkbox, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(shape)
            .background(if (checked) actionColor else MaterialTheme.colorScheme.surfaceVariant)
            .border(if (checked) 0.dp else 1.5.dp,
                if (checked) Color.Transparent else MaterialTheme.colorScheme.outline, shape),
            contentAlignment = Alignment.Center) {
            if (saving) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = actionColor)
            else if (checked) Canvas(Modifier.size(16.dp)) {
                drawLine(onActionColor, Offset(size.width * 0.15f, size.height * 0.5f),
                    Offset(size.width * 0.42f, size.height * 0.76f), strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(onActionColor, Offset(size.width * 0.42f, size.height * 0.76f),
                    Offset(size.width * 0.86f, size.height * 0.23f), strokeWidth = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
    }
}

@Composable
fun OrdinarySetOptions(selected: MassUnit, dirty: Boolean, onCancel: () -> Unit, onSelect: (MassUnit) -> Unit) {
    DropdownMenuItem(text = { Text("입력 취소") }, onClick = onCancel, enabled = dirty,
        modifier = Modifier.testTag("set-discard-draft"))
    MassUnit.entries.forEach { unit ->
        DropdownMenuItem(text = { Text(unit.symbol()) }, onClick = { onSelect(unit) }, modifier = Modifier.testTag("set-unit-${unit.id}"),
            leadingIcon = { RadioButton(selected = selected == unit, onClick = null) })
    }
}

@Composable
fun WorkoutSetRemoveButton(description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.size(48.dp).semantics { contentDescription = description }) {
        val color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        Canvas(Modifier.size(16.dp)) {
            drawLine(color, Offset(2.dp.toPx(), 2.dp.toPx()),
                Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
            drawLine(color, Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                Offset(2.dp.toPx(), size.height - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
        }
    }
}

@Composable
expect fun WorkoutSetValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    suffix: String? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
)
