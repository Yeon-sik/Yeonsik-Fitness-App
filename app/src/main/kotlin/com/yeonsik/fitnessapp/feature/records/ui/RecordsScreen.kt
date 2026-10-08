package com.yeonsik.fitnessapp.feature.records.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppDataRow
import com.yeonsik.fitnessapp.core.ui.AppHeader
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.core.ui.FitnessStatusBadge
import com.yeonsik.fitnessapp.core.ui.ThinkingOrb
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessCalendarDayCell
import com.yeonsik.fitnessapp.core.ui.FitnessCalendarMarker
import com.yeonsik.fitnessapp.core.ui.FitnessRecordMarkerColors
import com.yeonsik.fitnessapp.core.ui.FitnessMonthHeader
import com.yeonsik.fitnessapp.core.ui.fitnessCalendarDayPresentation
import com.yeonsik.fitnessapp.core.ui.fitnessWeekdayLabels
import com.yeonsik.fitnessapp.core.ui.TopLevelEntranceContent
import com.yeonsik.fitnessapp.core.ui.TopLevelEntranceMotion
import com.yeonsik.fitnessapp.core.ui.TopLevelEntranceState
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceMotion
import com.yeonsik.fitnessapp.core.ui.rememberTopLevelEntranceState
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitness.shared.feature.meal.model.MealReadSummary
import com.yeonsik.fitness.shared.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsWorkoutSummary
import java.time.LocalDate
import java.time.YearMonth
import java.text.NumberFormat
import java.util.Locale

private val recordsLoadingMessages = listOf("기록을 불러오는 중")
interface RecordsScreenActions {
    fun selectDate(date: String)
    fun previousMonth()
    fun nextMonth()
    fun today()
    fun openRecord(recordId: String)
    fun deleteRecord(recordId: String)
    fun showBodyMetric(date: String, recordId: String?)
    fun addMeal(date: String)
}

@Composable
internal fun RecordsScreen(
    state: RecordsUiState,
    ownerId: String,
    today: String,
    unit: MassUnit,
    selectedDate: String,
    actions: RecordsScreenActions,
    entranceState: TopLevelEntranceState = rememberTopLevelEntranceState("RECORDS"),
    entranceToken: Long? = null,
    isActualActive: Boolean = true
) {
    var selectedMealId by rememberSaveable(ownerId, selectedDate, isActualActive) {
        mutableStateOf<String?>(null)
    }
    val ready = state as? RecordsUiState.Ready
    val snapshot = ready?.snapshot?.takeIf { it.ownerId == ownerId }
    val entrance = rememberTopLevelEntranceMotion(
        entranceState, entranceToken, isActualActive, contentReady = snapshot != null
    )
    AppHeader("기록", selectedDate)
    val monthText = snapshot?.displayedMonth ?: when (state) {
        is RecordsUiState.Loading -> state.displayedMonth
        is RecordsUiState.Error -> state.displayedMonth
        else -> selectedDate.take(7)
    }
    val displayedMonth = runCatching { YearMonth.parse(monthText) }
        .getOrElse { YearMonth.from(LocalDate.parse(today)) }
    val selected = runCatching { LocalDate.parse(selectedDate) }.getOrNull()
    val currentDay = runCatching { LocalDate.parse(snapshot?.today ?: today) }
        .getOrElse { LocalDate.parse(today) }

    FitnessMonthHeader(
        month = displayedMonth,
        onPrevious = actions::previousMonth,
        onNext = actions::nextMonth
    )
    AppOutlinedButton(
        onClick = actions::today,
        enabled = selectedDate != today,
        modifier = Modifier.fillMaxWidth()
    ) { Text("오늘로 이동") }
    if (snapshot == null) {
        Box(
            Modifier.fillMaxWidth().heightIn(min = 360.dp)
                .testTag("records-calendar-loading-region"),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                is RecordsUiState.Error -> AppCard(Modifier.fillMaxWidth()) {
                    Text(state.message, Modifier.padding(AppSpacing.card))
                }
                is RecordsUiState.Loading -> RecordsCalendarLoading()
                else -> Text("기록을 준비하고 있습니다.")
            }
        }
        return
    }
    TopLevelEntranceContent(entrance, order = 0) { RecordsCalendarLegend() }
    TopLevelEntranceContent(entrance, order = 1) {
        RecordsCalendar(displayedMonth, selected, currentDay, snapshot.calendarDays, actions)
    }
    Spacer(Modifier.height(AppSpacing.small))
    snapshot.dayDetailsByDate[selectedDate]?.let { detail ->
        RecordsDayDetailSection(detail, unit, actions, entrance) { mealId ->
            if (isActualActive) selectedMealId = mealId
        }
        detail.meals.firstOrNull { it.id == selectedMealId }?.takeIf { isActualActive }?.let { meal ->
            RecordsMealSummaryDialog(meal, onDismiss = { selectedMealId = null })
        }
    }
}

@Composable
private fun RecordsCalendarLoading() {
    val message = recordsLoadingMessages.first()
    Column(
        modifier = Modifier.fillMaxWidth()
            .semantics { stateDescription = message }
            .testTag("records-calendar-loading-content"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ThinkingOrb(size = 104.dp)
        Spacer(Modifier.height(FitnessSpacing.gap))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecordsCalendar(
    displayedMonth: YearMonth,
    selectedDate: LocalDate?,
    today: LocalDate,
    calendarDays: List<RecordsCalendarDay>,
    actions: RecordsScreenActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
        ) {
            fitnessWeekdayLabels(Locale.KOREA).forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(min = FitnessSpacing.touch)
                        .heightIn(min = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        calendarDays.chunked(7).forEach { week ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
            ) {
                week.forEach { day ->
                    val date = LocalDate.parse(day.date)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        FitnessCalendarDayCell(
                            day = fitnessCalendarDayPresentation(
                                date = date,
                                displayedMonth = displayedMonth,
                                selectedDate = selectedDate,
                                today = today,
                                markers = day.markers()
                            ),
                            markerColors = FitnessRecordMarkerColors.byKey,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { actions.selectDate(day.date) }
                        )
                        if (day.muscleLabels.isNotEmpty()) {
                            Text(
                                text = day.muscleLabels.joinToString("·"),
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecordsCalendarLegend(modifier: Modifier = Modifier) {
    val entries = listOf(
        "workout" to "운동",
        "meal" to "식단",
        "body" to "체중"
    )
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEach { (key, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(8.dp)
                        .background(FitnessRecordMarkerColors.byKey.getValue(key), CircleShape)
                        .testTag("records-legend-$key")
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
    actions: RecordsScreenActions,
    entrance: TopLevelEntranceMotion,
    onSelectMeal: (String) -> Unit
) {
    var order = 2
    var showAddMenu by rememberSaveable(detail.date) { mutableStateOf(false) }
    TopLevelEntranceContent(entrance, order++) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${detail.date} 상세", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Box {
                TextButton(onClick = { showAddMenu = true }, Modifier.testTag("records-add")) {
                    Text("기록 추가")
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(text = { Text("체중") }, onClick = {
                        showAddMenu = false
                        actions.showBodyMetric(detail.date, null)
                    })
                    DropdownMenuItem(text = { Text("식단") }, onClick = {
                        showAddMenu = false
                        actions.addMeal(detail.date)
                    })
                }
            }
        }
    }
    if (!detail.hasAnyRecord) {
        TopLevelEntranceContent(entrance, order++) {
            AppCard(Modifier.fillMaxWidth()) {
                Text("선택한 날짜에 저장된 기록이 없습니다.", Modifier.padding(AppSpacing.card))
            }
        }
    }
    if (detail.workouts.isNotEmpty()) {
        TopLevelEntranceContent(entrance, order++) {
            Text("운동 기록", style = MaterialTheme.typography.titleLarge)
        }
        detail.workouts.forEach { workout ->
            TopLevelEntranceContent(entrance, order++) {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(AppSpacing.card),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.gap),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f).clickable { actions.openRecord(workout.id) }
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
                            onClick = { actions.deleteRecord(workout.id) }, destructive = true
                        ) { Text("삭제") }
                    }
                }
            }
        }
    }
    if (detail.bodyMetrics.isNotEmpty()) {
        TopLevelEntranceContent(entrance, order++) {
            Text("체중 기록", style = MaterialTheme.typography.titleLarge)
        }
        detail.bodyMetrics.forEach { metric ->
            TopLevelEntranceContent(entrance, order++) {
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
                        }
                        Text("수정", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    if (detail.meals.isNotEmpty()) {
        TopLevelEntranceContent(entrance, order++) {
            Text("식단 기록", style = MaterialTheme.typography.titleLarge)
        }
        detail.meals.forEach { meal ->
            TopLevelEntranceContent(entrance, order++) {
                AppDataRow(
                    meal.mealLabel,
                    meal.previewTitle,
                    Modifier.testTag("records-meal-${meal.id}")
                        .clickable(role = Role.Button) { onSelectMeal(meal.id) }
                )
            }
        }
    }
}

@Composable
private fun RecordsMealSummaryDialog(meal: MealReadSummary, onDismiss: () -> Unit) {
    val nutritionRecorded = meal.nutritionStatus != "unknown"
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("records-meal-dialog"),
        title = { Text("${meal.mealLabel} 정보") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                Text(
                    meal.previewTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RecordsMealInfoRow("먹은 시각", meal.mealTime.ifBlank { "시간 미기록" }, "time")
                if (meal.nutritionStatus == "estimated") {
                    Text("추정 영양 정보", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RecordsMealInfoRow("칼로리", if (nutritionRecorded) "${meal.calories} kcal" else "미기록", "calories")
                RecordsMealInfoRow("탄수화물", recordsMealGrams(meal.recordedCarbsGrams.takeIf { nutritionRecorded }), "carbs")
                RecordsMealInfoRow("단백질", recordsMealGrams(meal.recordedProteinGrams.takeIf { nutritionRecorded }), "protein")
                RecordsMealInfoRow("지방", recordsMealGrams(meal.recordedFatGrams.takeIf { nutritionRecorded }), "fat")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("records-meal-dialog-close")) {
                Text("닫기")
            }
        }
    )
}

@Composable
private fun RecordsMealInfoRow(label: String, value: String, key: String) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("records-meal-$key"),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

internal fun recordsMealGrams(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0.0) return "미기록"
    val number = NumberFormat.getNumberInstance(Locale.KOREA).apply {
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(value)
    return "${number}g"
}

private fun RecordsCalendarDay.markers(): List<FitnessCalendarMarker> = buildList {
    if (hasWorkout) add(FitnessCalendarMarker("workout", "운동"))
    if (hasMeal) add(FitnessCalendarMarker("meal", "식단"))
    if (hasBodyMetric) add(FitnessCalendarMarker("body", "체중"))
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
