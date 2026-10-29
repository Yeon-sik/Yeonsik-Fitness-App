package com.yeonsik.fitnessapp.feature.statistics.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriodWindow
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsTrendPoint
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale

@Composable
internal fun StatisticsTrendChart(
    id: String,
    points: List<StatisticsTrendPoint>,
    window: StatisticsPeriodWindow,
    weekly: Boolean,
    unit: String,
    massUnit: MassUnit? = null
) {
    val entries = remember(points, window, weekly, massUnit) { statisticsChartEntries(points, window, weekly, massUnit) }
    val model = remember(entries) {
        fitnessTrendPresentation(entries.map { FitnessTrendPoint(it.label, it.value.takeIf { _ -> it.fraction != null }) }, minimumPoints = 2)
    }
    var selected by remember(entries) { mutableStateOf<Int?>(null) }
    var tableVisible by remember(entries) { mutableStateOf(false) }
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.KOREA).apply { maximumFractionDigits = 1 } }
    fun valueText(value: Double?) = value?.let { "${numberFormat.format(it)} $unit" } ?: "미기록"
    val valid = entries.indices.filter { entries[it].value != null && entries[it].fraction != null }
    val description = "${if (weekly) "주별" else "날짜별"} $unit. 기록 ${valid.size}개. 빈 구간은 미기록이며 0이 아닙니다."
    if (valid.size >= 2) {
        val range = model.range
        val density = LocalDensity.current
        val labelStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp)
        val measurer = rememberTextMeasurer()
        val maxLabel = numberFormat.format(range.max)
        val minLabel = numberFormat.format(range.min)
        val axisWidth = with(density) {
            maxOf(measurer.measure(AnnotatedString(maxLabel), labelStyle).size.width,
                measurer.measure(AnnotatedString(minLabel), labelStyle).size.width).toDp() + 8.dp
        }
        val height = 180.dp
        val lineColor = LocalFitnessColors.current.action
        val axisColor = MaterialTheme.colorScheme.outlineVariant
        val selectionColor = MaterialTheme.colorScheme.onSurface
        Row(Modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column(Modifier.width(axisWidth).fillMaxHeight().padding(vertical = 20.dp),
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                Text(maxLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(minLabel, style = labelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Canvas(Modifier.weight(1f).fillMaxHeight().testTag("statistics-chart-$id")
                .semantics {
                    contentDescription = description
                    stateDescription = selected?.let { "${entries[it].label}, ${valueText(entries[it].value)}" } ?: "기록을 선택할 수 있습니다"
                    customActions = valid.map { index ->
                        CustomAccessibilityAction("${entries[index].label}, ${valueText(entries[index].value)}") {
                            selected = index; true
                        }
                    }
                }
                .pointerInput(entries, range) {
                    detectTapGestures { tap ->
                        val padding = 24.dp.toPx()
                        fun offset(index: Int) = Offset(
                            padding + entries[index].fraction!! * (size.width - padding * 2).coerceAtLeast(0f),
                            padding + (1f - range.normalize(entries[index].value!!)) * (size.height - padding * 2))
                        val nearest = valid.minByOrNull { (offset(it) - tap).getDistance() }
                            ?.takeIf { (offset(it) - tap).getDistance() <= 24.dp.toPx() }
                        selected = if (nearest == selected) null else nearest
                    }
                }) {
                val padding = 24.dp.toPx()
                fun offset(index: Int) = Offset(
                    padding + entries[index].fraction!! * (size.width - padding * 2).coerceAtLeast(0f),
                    padding + (1f - range.normalize(entries[index].value!!)) * (size.height - padding * 2))
                listOf(padding, size.height - padding).forEach { y ->
                    drawLine(axisColor, Offset(padding, y), Offset(size.width - padding, y), 1.dp.toPx())
                }
                valid.forEach { index ->
                    if (entries[index].connectsToPrevious) drawLine(lineColor, offset(index - 1), offset(index), 2.dp.toPx(), StrokeCap.Round)
                    drawCircle(lineColor, 4.dp.toPx(), offset(index))
                    if (selected == index) drawCircle(selectionColor, 7.dp.toPx(), offset(index), style = Stroke(2.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = axisWidth + 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            fun axisDate(date: String): String {
                val day = LocalDate.parse(date).let { if (weekly) it.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else it }
                return "${day.monthValue}/${day.dayOfMonth}" + if (weekly) " 주" else ""
            }
            Text(axisDate(window.startDate), style = labelStyle)
            Text(axisDate(window.endDate), style = labelStyle)
        }
        Text(if (weekly) "주간 합계 · 빈 주는 미기록" else "기록한 날의 값 · 빈 날짜는 연결하지 않음",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(if (valid.isEmpty()) "이 기간에 표시할 기록이 없습니다."
            else "기록 1개 · 추세는 기록이 더 쌓이면 표시됩니다.", style = MaterialTheme.typography.bodyMedium)
        valid.singleOrNull()?.let { Text("${entries[it].label}\n${valueText(entries[it].value)}", style = MaterialTheme.typography.bodyLarge) }
    }
    selected?.let { index ->
        Text("${entries[index].label}\n${valueText(entries[index].value)}", Modifier.testTag("statistics-selected-$id"),
            style = MaterialTheme.typography.bodyMedium)
        AppOutlinedButton(onClick = { selected = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("선택 해제") }
    }
    if (entries.isNotEmpty()) {
        AppOutlinedButton(onClick = { tableVisible = !tableVisible },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("statistics-values-$id")
                .semantics { stateDescription = if (tableVisible) "펼침" else "접힘" }) {
            Text(if (tableVisible) "수치 목록 접기" else "날짜별 수치 보기")
        }
        if (tableVisible) {
            entries.forEachIndexed { index, entry ->
                Column(Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag("statistics-value-$id-$index")) {
                    Text(entry.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(valueText(entry.value), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
