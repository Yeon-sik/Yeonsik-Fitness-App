package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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

private val CELL_GAP = 4.dp
private val GROUP_GAP = 6.dp
private val CARD_PADDING = 6.dp

private data class HistoryPreview(
    val history: WorkoutExerciseHistory,
    val sets: List<WorkoutSet>,
    val rows: WorkoutSetTableRows,
    val volumeLabel: String
) {
    val date = history.date.ifBlank { "날짜 미상" }
    val previewIndices = sets.indices.take(PREVIEW_LIMIT)
    val upperLabel = if (rows.upperLabel.startsWith("중량(")) "중량" else rows.upperLabel
}

@Composable
internal fun WorkoutHistoryCards(
    histories: List<WorkoutExerciseHistory>,
    exerciseName: String,
    recordType: String,
    unit: MassUnit,
    modifier: Modifier = Modifier
) {
    val previews = remember(histories, recordType, unit) {
        histories.map { history ->
            val sets = history.sets.filter { it.isCompleted }.sortedBy { it.setIndex }
            val rows = workoutSetTableRows(recordType, sets, unit)
            // A single fractional digit is the precision of the history mass formatter.
            val hasFractionalLoad = rows.upperValues.any { it.isLoadValue && '.' in it.visibleValue }
            val upperValues = if (!hasFractionalLoad) rows.upperValues else rows.upperValues.map { cell ->
                val wholeLoad = cell.isLoadValue && cell.visibleValue.lastOrNull()?.isDigit() == true &&
                    '.' !in cell.visibleValue
                if (wholeLoad) cell.copy(visibleValue = cell.visibleValue + ".0") else cell
            }
            HistoryPreview(
                history, sets, rows.copy(upperValues = upperValues),
                "총 볼륨 ${MassFormatter.withUnit(history.totalVolumeKg, unit)}"
            )
        }
    }
    val numberStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
    val labelStyle = MaterialTheme.typography.labelSmall
    val dateStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val (loadWidth, repsWidth, metadataWidth) = remember(previews, numberStyle, labelStyle, dateStyle, measurer, density) {
        var load = 0
        var reps = 0
        var metadata = 0
        for (preview in previews) {
            metadata = maxOf(
                metadata,
                measurer.measure(preview.date, dateStyle, softWrap = false).size.width,
                measurer.measure(preview.volumeLabel, labelStyle, softWrap = false).size.width
            )
            load = maxOf(load, measurer.measure(preview.upperLabel, labelStyle, softWrap = false).size.width)
            reps = maxOf(reps, measurer.measure(preview.rows.lowerLabel, labelStyle, softWrap = false).size.width)
            for (index in preview.previewIndices) {
                val loadText = markedLoad(preview.sets[index], preview.rows.upperValues[index])
                val repsText = preview.rows.lowerValues[index].visibleValue
                load = maxOf(load, measurer.measure(loadText, numberStyle, softWrap = false).size.width)
                reps = maxOf(reps, measurer.measure(repsText, numberStyle, softWrap = false).size.width)
            }
        }
        with(density) { Triple(load.toDp(), reps.toDp(), metadata.toDp()) }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Match the individual pixel rounding of the two Rows and the card padding.
        val groupWidth = with(density) {
            (loadWidth.roundToPx() + CELL_GAP.roundToPx() + repsWidth.roundToPx()).toDp()
        }
        val contentWidth = with(density) {
            val tableWidth = groupWidth.roundToPx() * 2 + GROUP_GAP.roundToPx()
            (maxOf(tableWidth, metadataWidth.roundToPx()) + CARD_PADDING.roundToPx() * 2).toDp()
        }
        val cardWidth = maxOf((maxWidth * 0.30f).coerceAtMost(128.dp), contentWidth)
        Row(
            Modifier.fillMaxWidth()
                .testTag("exercise-history-list")
                .horizontalScroll(rememberScrollState())
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            previews.forEachIndexed { index, preview ->
                key(preview.history.recordId.ifBlank { "history-$index" }) {
                    WorkoutHistorySetCard(
                        preview, exerciseName, loadWidth to repsWidth, groupWidth,
                        Modifier.width(cardWidth).fillMaxHeight()
                            .testTag("exercise-history-card-$index")
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistorySetCard(
    preview: HistoryPreview,
    exerciseName: String,
    cellWidths: Pair<Dp, Dp>,
    groupWidth: Dp,
    modifier: Modifier = Modifier
) {
    val (history, sets, rows) = preview
    var expanded by rememberSaveable(history.recordId, exerciseName) { mutableStateOf(false) }
    val date = preview.date
    val labelStyle = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AppCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(CARD_PADDING), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(date, style = MaterialTheme.typography.labelLarge)
            Text("${sets.size}세트", style = labelStyle, color = muted)
            Text(preview.volumeLabel, style = labelStyle, color = muted)
            if (sets.isEmpty()) {
                Text("완료 세트 없음", style = MaterialTheme.typography.bodySmall)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(GROUP_GAP)) {
                    preview.previewIndices.chunked(ROWS_PER_COLUMN).forEach { column ->
                        Column(Modifier.width(groupWidth)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                                Text(
                                    preview.upperLabel,
                                    Modifier.width(cellWidths.first).semantics {
                                        contentDescription = rows.upperLabel
                                    },
                                    style = labelStyle,
                                    textAlign = TextAlign.End,
                                    softWrap = false
                                )
                                Text(rows.lowerLabel, Modifier.width(cellWidths.second),
                                    style = labelStyle, textAlign = TextAlign.End, softWrap = false)
                            }
                            HorizontalDivider()
                            column.forEach { index ->
                                HistorySetRow(sets[index], rows.upperValues[index], rows.lowerValues[index], cellWidths)
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
                        HistorySetRow(sets[index], rows.upperValues[index], rows.lowerValues[index])
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
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(CELL_GAP)) {
        Text("세트", Modifier.weight(0.75f), style = style)
        Text(rows.upperLabel, Modifier.weight(1.6f), style = style, textAlign = TextAlign.End)
        Text(rows.lowerLabel, Modifier.weight(1f), style = style, textAlign = TextAlign.End)
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
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP), verticalAlignment = Alignment.CenterVertically) {
        if (numbered) Text(set.setIndex.toString(), Modifier.weight(0.75f), style = style)
        Text(markedLoad(set, upper),
            (cellWidths?.let { Modifier.width(it.first) } ?: Modifier.weight(1.6f)).semantics {
                contentDescription = if (set.setType == WorkoutSetType.NORMAL) upper.spokenValue else "${upper.spokenValue}, ${set.setType.label}"
            }, color = loadColor, style = style, textAlign = TextAlign.End, softWrap = numbered)
        Text(lower.visibleValue, (cellWidths?.let { Modifier.width(it.second) } ?: Modifier.weight(1f)).semantics { contentDescription = lower.spokenValue },
            textAlign = TextAlign.End, style = style, softWrap = numbered)
    }
}
