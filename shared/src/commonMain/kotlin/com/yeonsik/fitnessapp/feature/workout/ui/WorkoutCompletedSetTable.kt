package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.exercise.model.LoadState
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitness.shared.feature.workout.ui.WorkoutDisplayNumbers
import com.yeonsik.fitnessapp.core.ui.*

const val WORKOUT_HISTORY_SET_COLUMNS = 7
data class WorkoutSetTableCell(
    val visibleValue: String,
    val spokenValue: String,
    val isLoadValue: Boolean = false
)

data class WorkoutSetTableRows(
    val upperLabel: String,
    val upperValues: List<WorkoutSetTableCell>,
    val lowerLabel: String,
    val lowerValues: List<WorkoutSetTableCell>
)

fun workoutHistorySetBatches(
    sets: List<WorkoutSet>,
    batchSize: Int = WORKOUT_HISTORY_SET_COLUMNS
): List<List<WorkoutSet>> {
    require(batchSize > 0)
    return sets.chunked(batchSize)
}

fun workoutSetTableRows(
    recordType: String,
    sets: List<WorkoutSet>,
    unit: MassUnit
): WorkoutSetTableRows {
    val normalizedType = recordType.trim().lowercase()
    val isTimed = normalizedType == "time" ||
        normalizedType == "weight_time"
    val isTimeOnly = normalizedType == "time"
    val unitSymbol = MassUnit.orDefault(unit).symbol()
    val upperLabel = when {
        isTimeOnly -> "시간(초)"
        normalizedType == "reps_only" -> "부하"
        else -> "중량($unitSymbol)"
    }
    val lowerLabel = when {
        isTimeOnly -> "거리(m)"
        isTimed -> "시간(초)"
        else -> "횟수"
    }
    val upperValues = sets.map { set ->
        if (isTimeOnly) {
            val seconds = set.durationSeconds.takeIf { it > 0 }?.toString() ?: "—"
            WorkoutSetTableCell(seconds, "${set.setIndex}세트, 시간 ${seconds}초")
        } else {
            val load = setLoadLabel(set, normalizedType, unit)
            WorkoutSetTableCell(
                load,
                "${set.setIndex}세트, 부하 $load $unitSymbol",
                isLoadValue = true
            )
        }
    }
    val lowerValues = sets.map { set ->
        when {
            isTimeOnly -> {
                val distance = set.distanceMeters.takeIf { it > 0.0 }?.let {
                    WorkoutDisplayNumbers.fixed(it, 1)
                } ?: "—"
                WorkoutSetTableCell(distance, "${set.setIndex}세트, 거리 ${distance}미터")
            }
            isTimed -> {
                val seconds = set.durationSeconds.takeIf { it > 0 }?.toString() ?: "—"
                WorkoutSetTableCell(seconds, "${set.setIndex}세트, 시간 ${seconds}초")
            }
            else -> {
                val reps = set.actualReps.takeIf { it > 0 }?.toString() ?: "—"
                WorkoutSetTableCell(reps, "${set.setIndex}세트, 횟수 ${reps}회")
            }
        }
    }
    return WorkoutSetTableRows(upperLabel, upperValues, lowerLabel, lowerValues)
}

private fun setLoadLabel(set: WorkoutSet, recordType: String, unit: MassUnit): String = when {
    recordType == "reps_only" -> "체중"
    set.loadState == LoadState.BODYWEIGHT -> "체중"
    set.loadState == LoadState.ADDED_WEIGHT && set.addedWeightKg > 0.0 ->
        "+${WorkoutDisplayNumbers.mass(set.addedWeightKg, unit)}"
    set.loadState == LoadState.ASSISTED || set.loadState == LoadState.BAND_ASSISTED ->
        set.assistedWeightKg.takeIf { it > 0.0 }?.let { "보조 ${WorkoutDisplayNumbers.mass(it, unit)}" } ?: "보조"
    set.loadState == LoadState.BAND_RESISTED ->
        set.weightKg.takeIf { it > 0.0 }?.let { WorkoutDisplayNumbers.mass(it, unit) }
            ?: set.addedWeightKg.takeIf { it > 0.0 }?.let { WorkoutDisplayNumbers.mass(it, unit) }
            ?: "밴드"
    set.weightKg > 0.0 -> WorkoutDisplayNumbers.mass(set.weightKg, unit)
    else -> "—"
}

@Composable
fun WorkoutExerciseSetTable(
    sets: List<WorkoutSet>,
    recordType: String,
    unit: MassUnit,
    modifier: Modifier = Modifier
) {
    val completedSets = sets.filter { it.isCompleted }.sortedBy { it.setIndex }
    if (completedSets.isEmpty()) {
        Text("완료 세트 없음", modifier, style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(
            "${completedSets.size}세트",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        workoutHistorySetBatches(completedSets).forEachIndexed { batchIndex, batch ->
            if (batchIndex > 0) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            val rows = workoutSetTableRows(recordType, batch, unit)
            if (batch.any { it.setType != WorkoutSetType.NORMAL }) {
                WorkoutExerciseSetTableRow("유형", batch.map {
                    val marker = it.setType.marker.ifBlank { it.setIndex.toString() }
                    WorkoutSetTableCell(marker, "${it.setIndex}세트, ${it.setType.label}")
                })
            }
            WorkoutExerciseSetTableRow(rows.upperLabel, rows.upperValues,
                if (recordType.trim().lowercase() == "time")
                    MaterialTheme.colorScheme.onSurface else workoutWeightTextColor(MaterialTheme.colorScheme.surface))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            WorkoutExerciseSetTableRow(rows.lowerLabel, rows.lowerValues)
        }
    }
}

@Composable
private fun WorkoutExerciseSetTableRow(
    label: String,
    values: List<WorkoutSetTableCell>,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(50.dp),
            style = MaterialTheme.typography.labelSmall,
            color = valueColor,
            maxLines = 1
        )
        values.forEach { cell ->
            Text(
                cell.visibleValue,
                modifier = Modifier.weight(1f).semantics {
                    contentDescription = cell.spokenValue
                },
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontFeatureSettings = "tnum"),
                color = valueColor,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        repeat((WORKOUT_HISTORY_SET_COLUMNS - values.size).coerceAtLeast(0)) {
            Spacer(Modifier.weight(1f))
        }
    }
}

fun workoutWeightTextColor(surface: Color): Color =
    if (surface.luminance() > 0.5f) Color(0xFF9A6700) else Color(0xFFFACC15)
