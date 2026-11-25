package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.MassFormatter

private const val ROWS_PER_COLUMN = 7
private const val PREVIEW_LIMIT = ROWS_PER_COLUMN * 2

@Composable
internal fun WorkoutHistorySetCard(history: WorkoutExerciseHistory, exerciseName: String, recordType: String, unit: MassUnit,
    modifier: Modifier = Modifier) {
    val sets = remember(history.sets) { history.sets.filter { it.isCompleted }.sortedBy { it.setIndex } }
    val rows = remember(sets, recordType, unit) { workoutSetTableRows(recordType, sets, unit) }
    var expanded by rememberSaveable(history.recordId, exerciseName) { mutableStateOf(false) }
    val date = history.date.ifBlank { "날짜 미상" }
    // The mass formatter displays at most one fractional digit. Keep the decimal point
    // in the same position for whole and fractional loads within this visit.
    val upperValues = remember(rows) {
        val hasFractionalLoad = rows.upperValues.any { it.isLoadValue && '.' in it.visibleValue }
        if (!hasFractionalLoad) rows.upperValues else rows.upperValues.map { cell ->
            val wholeLoad = cell.isLoadValue && cell.visibleValue.lastOrNull()?.isDigit() == true && '.' !in cell.visibleValue
            if (wholeLoad) cell.copy(visibleValue = cell.visibleValue + ".0") else cell
        }
    }
    val columns = sets.indices.take(PREVIEW_LIMIT).chunked(ROWS_PER_COLUMN)
    val numberStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val labelStyle = MaterialTheme.typography.labelSmall
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val cellWidths = remember(sets, upperValues, rows, numberStyle, labelStyle, measurer, density) {
        val preview = sets.indices.take(PREVIEW_LIMIT)
        fun numberWidth(value: String) = measurer.measure(value, numberStyle, softWrap = false).size.width
        fun headerWidth(value: String) = measurer.measure(value, labelStyle, softWrap = false).size.width
        val upper = preview.maxOfOrNull { numberWidth(markedLoad(sets[it], upperValues[it])) } ?: 0
        val lower = preview.maxOfOrNull { numberWidth(rows.lowerValues[it].visibleValue) } ?: 0
        with(density) {
            if (columns.size == 1) maxOf(upper, headerWidth(rows.upperLabel)).toDp() to
                maxOf(lower, headerWidth(rows.lowerLabel)).toDp()
            else upper.toDp() to lower.toDp()
        }
    }
    AppCard(modifier) {
        Column(Modifier.width(IntrinsicSize.Max).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge)
            Text("${sets.size}세트", style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}",
                style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sets.isEmpty()) {
                Text("완료 세트 없음", style = numberStyle)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(rows.upperLabel, if (columns.size == 1) Modifier.width(cellWidths.first) else Modifier,
                        style = labelStyle, textAlign = TextAlign.End)
                    Text(rows.lowerLabel, style = labelStyle)
                }
                HorizontalDivider()
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    columns.forEach { column ->
                        Column {
                            column.forEach { i ->
                                HistorySetRow(sets[i], upperValues[i], rows.lowerValues[i], cellWidths = cellWidths)
                            }
                        }
                    }
                }
            }
            if (sets.size > PREVIEW_LIMIT) {
                Text("더보기", Modifier.clickable(role = Role.Button) { expanded = true }.padding(vertical = 3.dp),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
    if (expanded && sets.size > PREVIEW_LIMIT) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(exerciseName) },
        text = {
            Column {
                Text("$date · 전체 ${sets.size}세트", style = MaterialTheme.typography.labelLarge)
                FullSetTableHeader(rows)
                LazyColumn(Modifier.heightIn(max = 440.dp).testTag("all-workout-sets")) {
                    items(sets.size) { index ->
                        HistorySetRow(sets[index], upperValues[index], rows.lowerValues[index])
                    }
                }
            }
        },
        confirmButton = { TextButton({ expanded = false }) { Text("닫기") } }
    )
}

@Composable
private fun FullSetTableHeader(rows: WorkoutSetTableRows) {
    val style = MaterialTheme.typography.labelSmall
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("세트", Modifier.weight(0.75f), style = style)
        Text(rows.upperLabel, Modifier.weight(1.6f), style = style, textAlign = TextAlign.End)
        Text(rows.lowerLabel, Modifier.weight(1f), style = style)
    }
    HorizontalDivider()
}

private fun markedLoad(set: WorkoutSet, upper: WorkoutSetTableCell): String =
    if (set.setType == WorkoutSetType.NORMAL) upper.visibleValue else "${set.setType.marker}  ${upper.visibleValue}"

@Composable
private fun HistorySetRow(set: WorkoutSet, upper: WorkoutSetTableCell, lower: WorkoutSetTableCell,
    cellWidths: Pair<Dp, Dp>? = null) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val loadColor = if (upper.isLoadValue) workoutWeightTextColor(MaterialTheme.colorScheme.surface) else Color.Unspecified
    val numbered = cellWidths == null
    Row(if (numbered) Modifier.fillMaxWidth().padding(vertical = 2.dp) else Modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (numbered) Text(set.setIndex.toString(), Modifier.weight(0.75f), style = style)
        Text(markedLoad(set, upper),
            (cellWidths?.let { Modifier.width(it.first) } ?: Modifier.weight(1.6f)).semantics {
                contentDescription = if (set.setType == WorkoutSetType.NORMAL) upper.spokenValue else "${upper.spokenValue}, ${set.setType.label}"
            }, color = loadColor, style = style, textAlign = TextAlign.End)
        Text(lower.visibleValue, (cellWidths?.let { Modifier.width(it.second) } ?: Modifier.weight(1f)).semantics { contentDescription = lower.spokenValue },
            textAlign = if (numbered) TextAlign.Start else TextAlign.End, style = style)
    }
}
