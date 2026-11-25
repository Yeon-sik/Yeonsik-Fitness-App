package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yeonsik.fitness.shared.feature.workout.model.*
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.MassFormatter

private const val ROWS_PER_COLUMN = 7
private const val PREVIEW_LIMIT = ROWS_PER_COLUMN * 2

@Composable
internal fun WorkoutHistorySetCard(history: WorkoutExerciseHistory, exerciseName: String, recordType: String, unit: MassUnit) {
    val sets = remember(history.sets) { history.sets.filter { it.isCompleted }.sortedBy { it.setIndex } }
    val rows = remember(sets, recordType, unit) { workoutSetTableRows(recordType, sets, unit) }
    var expanded by rememberSaveable(history.recordId, exerciseName) { mutableStateOf(false) }
    val date = history.date.ifBlank { "날짜 미상" }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text(date, style = MaterialTheme.typography.titleSmall)
            Text("${sets.size}세트 · 총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (sets.isEmpty()) Text("완료 세트 없음", style = MaterialTheme.typography.bodySmall)
            else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                sets.indices.take(PREVIEW_LIMIT).chunked(ROWS_PER_COLUMN).forEach { column ->
                    Column(Modifier.weight(1f)) {
                        SetTableHeader(rows)
                        column.forEach { index -> HistorySetRow(sets[index], rows.upperValues[index], rows.lowerValues[index]) }
                    }
                }
                if (sets.size <= ROWS_PER_COLUMN) Spacer(Modifier.weight(1f))
            }
            if (sets.size > PREVIEW_LIMIT) TextButton({ expanded = true }, Modifier.align(Alignment.End)) {
                Text("더보기")
            }
        }
    }
    if (expanded && sets.size > PREVIEW_LIMIT) AlertDialog(
        onDismissRequest = { expanded = false },
        title = { Text(exerciseName) },
        text = {
            Column {
                Text("$date · 전체 ${sets.size}세트", style = MaterialTheme.typography.labelLarge)
                SetTableHeader(rows, numbered = true)
                LazyColumn(Modifier.heightIn(max = 440.dp).testTag("all-workout-sets")) {
                    items(sets.size) { index ->
                        HistorySetRow(sets[index], rows.upperValues[index], rows.lowerValues[index], numbered = true)
                    }
                }
            }
        },
        confirmButton = { TextButton({ expanded = false }) { Text("닫기") } }
    )
}

@Composable
private fun SetTableHeader(rows: WorkoutSetTableRows, numbered: Boolean = false) {
    val style = MaterialTheme.typography.labelSmall
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (numbered) Text("세트", Modifier.weight(0.75f), style = style)
        Text(rows.upperLabel, Modifier.weight(1.4f), style = style)
        Text(rows.lowerLabel, Modifier.weight(1f), style = style, textAlign = TextAlign.End)
    }
    HorizontalDivider()
}

@Composable
private fun HistorySetRow(set: WorkoutSet, upper: WorkoutSetTableCell, lower: WorkoutSetTableCell, numbered: Boolean = false) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val loadColor = if (upper.isLoadValue) workoutWeightTextColor(MaterialTheme.colorScheme.surface) else Color.Unspecified
    val marker = set.setType.marker.takeIf { set.setType != WorkoutSetType.NORMAL }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (numbered) Text(set.setIndex.toString(), Modifier.weight(0.75f), style = style)
        Text(if (marker.isEmpty()) upper.visibleValue else "$marker  ${upper.visibleValue}",
            Modifier.weight(1.4f).semantics {
                contentDescription = if (marker.isEmpty()) upper.spokenValue else "${upper.spokenValue}, ${set.setType.label}"
            }, color = loadColor, style = style)
        Text(lower.visibleValue, Modifier.weight(1f).semantics { contentDescription = lower.spokenValue },
            style = style, textAlign = TextAlign.End)
    }
}
