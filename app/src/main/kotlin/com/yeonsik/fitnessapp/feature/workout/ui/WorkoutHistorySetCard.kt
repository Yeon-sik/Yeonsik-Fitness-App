package com.yeonsik.fitnessapp.feature.workout.ui

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
internal fun WorkoutHistorySetCard(history: WorkoutExerciseHistory, exerciseName: String, recordType: String, unit: MassUnit,
    modifier: Modifier = Modifier) {
    val sets = remember(history.sets) { history.sets.filter { it.isCompleted }.sortedBy { it.setIndex } }
    val rows = remember(sets, recordType, unit) { workoutSetTableRows(recordType, sets, unit) }
    var expanded by rememberSaveable(history.recordId, exerciseName) { mutableStateOf(false) }
    val date = history.date.ifBlank { "날짜 미상" }
    AppCard(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
            val columns = sets.indices.take(PREVIEW_LIMIT).chunked(ROWS_PER_COLUMN)
            val gap = 12.dp
            val groups = columns.size.coerceAtLeast(1)
            val availableWidth = (maxWidth - gap * (groups - 1)).coerceAtLeast(0.dp)
            val groupWidth = (86.dp * scale).coerceAtMost(availableWidth / groups)
            val details: @Composable () -> Unit = {
                Text(date, style = MaterialTheme.typography.titleSmall)
                Text("${sets.size}세트 · 총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sets.size > PREVIEW_LIMIT) TextButton({ expanded = true }) { Text("더보기") }
            }
            val table: @Composable () -> Unit = {
                if (sets.isEmpty()) Text("완료 세트 없음", style = MaterialTheme.typography.bodySmall)
                else Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    columns.forEach { column ->
                        Column(Modifier.width(groupWidth)) {
                            SetTableHeader(rows)
                            column.forEach { i -> HistorySetRow(sets[i], rows.upperValues[i], rows.lowerValues[i]) }
                        }
                    }
                }
            }
            if (maxWidth >= (108.dp + 86.dp * columns.size) * scale + gap * columns.size) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    Column(Modifier.weight(1f)) { details() }
                    table()
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column { details() }
                    table()
                }
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
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (numbered) Text("세트", Modifier.weight(0.75f), style = style)
        Text(rows.upperLabel, Modifier.weight(1.6f), style = style, textAlign = TextAlign.End)
        Text(rows.lowerLabel, Modifier.weight(1f), style = style)
    }
    HorizontalDivider()
}

@Composable
private fun HistorySetRow(set: WorkoutSet, upper: WorkoutSetTableCell, lower: WorkoutSetTableCell, numbered: Boolean = false) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val loadColor = if (upper.isLoadValue) workoutWeightTextColor(MaterialTheme.colorScheme.surface) else Color.Unspecified
    val marker = set.setType.marker.takeIf { set.setType != WorkoutSetType.NORMAL }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (numbered) Text(set.setIndex.toString(), Modifier.weight(0.75f), style = style)
        Text(if (marker.isEmpty()) upper.visibleValue else "$marker  ${upper.visibleValue}",
            Modifier.weight(1.6f).semantics {
                contentDescription = if (marker.isEmpty()) upper.spokenValue else "${upper.spokenValue}, ${set.setType.label}"
            }, color = loadColor, style = style, textAlign = TextAlign.End)
        Text(lower.visibleValue, Modifier.weight(1f).semantics { contentDescription = lower.spokenValue },
            style = style)
    }
}
