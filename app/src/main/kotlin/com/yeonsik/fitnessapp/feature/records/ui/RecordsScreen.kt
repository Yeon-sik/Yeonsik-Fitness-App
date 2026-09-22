package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppDataRow
import com.yeonsik.fitnessapp.core.ui.AppHeader
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessCalendarDayCell
import com.yeonsik.fitnessapp.core.ui.FitnessCalendarMarker
import com.yeonsik.fitnessapp.core.ui.FitnessMonthHeader
import com.yeonsik.fitnessapp.core.ui.fitnessCalendarDayPresentation
import com.yeonsik.fitnessapp.core.ui.fitnessWeekdayLabels
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsWorkoutSummary
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

interface RecordsScreenActions {
    fun selectDate(date: String)
    fun previousMonth()
    fun nextMonth()
    fun today()
    fun openRecord(recordId: String)
    fun deleteRecord(recordId: String)
    fun showBodyMetric(date: String, recordId: String?)
    fun openMeals(date: String)
}

@Composable
internal fun RecordsScreen(
    state: RecordsUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    selectedDate: String,
    actions: RecordsScreenActions
) {
    val ready = state as? RecordsUiState.Ready
    val snapshot = ready?.snapshot?.takeIf { it.ownerId == ownerId }
    AppHeader("기록", snapshot?.selectedDate ?: selectedDate)
    if (state is RecordsUiState.Error) {
        AppCard(Modifier.fillMaxWidth()) {
            Text(state.message, Modifier.padding(AppSpacing.card))
        }
        return
    }
    if (snapshot == null) {
        Text(
            if (state is RecordsUiState.Loading) "기록을 불러오는 중입니다."
            else "기록을 준비하고 있습니다."
        )
        return
    }

    val displayedMonth = runCatching { YearMonth.parse(snapshot.displayedMonth) }
        .getOrElse { YearMonth.from(LocalDate.parse(snapshot.selectedDate)) }
    val selected = runCatching { LocalDate.parse(snapshot.selectedDate) }.getOrNull()
    val currentDay = runCatching { LocalDate.parse(snapshot.today) }
        .getOrElse { LocalDate.parse(today) }

    FitnessMonthHeader(
        month = displayedMonth,
        onPrevious = actions::previousMonth,
        onNext = actions::nextMonth
    )
    AppOutlinedButton(
        onClick = actions::today,
        enabled = snapshot.selectedDate != snapshot.today,
        modifier = Modifier.fillMaxWidth()
    ) { Text("오늘로 이동") }
    RecordsCalendarLegend()
    RecordsCalendar(displayedMonth, selected, currentDay, snapshot.calendarDays, actions)
    Spacer(Modifier.height(AppSpacing.small))
    RecordsDayDetailSection(snapshot.selectedDay, unit, actions)
}

@Composable
private fun RecordsCalendar(
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    calendarDays: List<RecordsCalendarDay>,
    actions: RecordsScreenActions
) {
    val colors = mapOf(
        "workout" to MaterialTheme.colorScheme.primary,
        "body" to MaterialTheme.colorScheme.tertiary,
        "meal" to MaterialTheme.colorScheme.secondary
    )
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Row(Modifier.fillMaxWidth()) {
            fitnessWeekdayLabels(Locale.KOREA).forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        calendarDays.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
            ) {
                week.forEach { day ->
                    val date = LocalDate.parse(day.date)
                    FitnessCalendarDayCell(
                        day = fitnessCalendarDayPresentation(
                            date = date,
                            displayedMonth = displayedMonth,
                            selectedDate = selectedDate,
                            today = today,
                            markers = day.markers()
                        ),
                        markerColors = colors,
                        modifier = Modifier.weight(1f),
                        onClick = { actions.selectDate(day.date) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordsCalendarLegend() {
    val entries = listOf(
        "운동" to MaterialTheme.colorScheme.primary,
        "체중" to MaterialTheme.colorScheme.tertiary,
        "식사" to MaterialTheme.colorScheme.secondary
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .background(color, CircleShape)
                )
                Text(label, Modifier.padding(start = FitnessSpacing.micro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RecordsDayDetailSection(
    detail: RecordsDayDetail,
    unit: MassUnit,
    actions: RecordsScreenActions
) {
    Text("${detail.date} 상세", style = MaterialTheme.typography.titleMedium)
    if (!detail.hasAnyRecord) {
        AppCard(Modifier.fillMaxWidth()) {
            Text("선택한 날짜에 저장된 기록이 없습니다.", Modifier.padding(AppSpacing.card))
        }
    }
    if (detail.workouts.isNotEmpty()) {
        Text("운동 기록", style = MaterialTheme.typography.titleLarge)
        detail.workouts.forEach { workout ->
            AppCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(AppSpacing.card),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { actions.openRecord(workout.id) }
                    ) {
                        Text(workout.title.ifBlank { "운동 기록" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            recordsWorkoutDetail(workout, unit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (workout.muscleLabels.isNotEmpty()) {
                            Text(
                                "부위: ${workout.muscleLabels.joinToString(", ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AppOutlinedButton(
                        onClick = { actions.deleteRecord(workout.id) },
                        destructive = true
                    ) { Text("삭제") }
                }
            }
        }
    }
    if (detail.bodyMetrics.isNotEmpty()) {
        Text("체중 기록", style = MaterialTheme.typography.titleLarge)
        detail.bodyMetrics.forEach { metric ->
            AppCard(Modifier.fillMaxWidth().clickable {
                actions.showBodyMetric(metric.date, metric.id)
            }) {
                Row(
                    Modifier.padding(AppSpacing.card),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(MassFormatter.withUnit(metric.weightKg, unit), fontWeight = FontWeight.Bold)
                        if (metric.memo.isNotBlank()) Text(metric.memo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("수정", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (detail.meals.isNotEmpty()) {
        Text("식사 기록", style = MaterialTheme.typography.titleLarge)
        detail.meals.forEach { meal ->
            AppDataRow(meal.mealLabel, meal.previewTitle)
        }
    }
    AppOutlinedButton(
        onClick = { actions.showBodyMetric(detail.date, null) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("체중 기록") }
    AppOutlinedButton(
        onClick = { actions.openMeals(detail.date) },
        modifier = Modifier.fillMaxWidth()
    ) { Text("식사 기록 관리") }
}

private fun RecordsCalendarDay.markers(): List<FitnessCalendarMarker> = buildList {
    if (hasWorkout) add(FitnessCalendarMarker("workout", "운동"))
    if (hasBodyMetric) add(FitnessCalendarMarker("body", "체중"))
    if (hasMeal) add(FitnessCalendarMarker("meal", "식사"))
}

private fun recordsWorkoutDetail(workout: RecordsWorkoutSummary, unit: MassUnit): String {
    val duration = if (workout.durationSeconds > 0) {
        "${workout.durationSeconds / 60}분"
    } else {
        "시간 미기록"
    }
    val type = when (workout.workoutType) {
        "strength" -> "근력"
        "cardio" -> "유산소"
        else -> workout.workoutType.ifBlank { "운동" }
    }
    return "$type · $duration · 세트 ${workout.completedSetCount}개 · 볼륨 " +
        MassFormatter.withUnit(workout.totalVolumeKg, unit)
}
