package com.yeonsik.fitnessapp.feature.home.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.yeonsik.fitnessapp.core.ui.FitnessButton
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessFactCard
import com.yeonsik.fitnessapp.core.ui.FitnessFactRow
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessHeader
import com.yeonsik.fitnessapp.core.ui.FitnessSection
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.data.MassUnit
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.routine.model.RoutineSummary
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitnessapp.feature.routine.ui.RoutineEntryUiState
import com.yeonsik.fitnessapp.state.FitnessScreen
import com.yeonsik.fitnessapp.ui.FitnessUi
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

interface HomeScreenActions {
    fun continueWorkout()
    fun navigate(screen: FitnessScreen)
    fun startEmptyWorkout()
    fun selectRoutine(routineId: String)
    fun startRoutineWorkout(exercises: List<RoutineExerciseInstance>)
    fun showBodyMetric()
    fun openMealManagement(date: String, returnScreen: FitnessScreen)
}

@Composable
internal fun HomeDestination(
    homeState: HomeUiState,
    routineState: RoutineEntryUiState,
    ownerId: String,
    today: String,
    displayUnit: MassUnit,
    actions: HomeScreenActions
) {
    val ready = homeState as? HomeUiState.Ready
    val routinesReady = routineState as? RoutineEntryUiState.Ready
    if (ready == null || ready.snapshot.ownerId != ownerId || ready.snapshot.today != today
        || routinesReady == null || routinesReady.ownerId != ownerId) {
        LoadingHome()
        return
    }
    val snapshot = ready.snapshot
    val metrics = snapshot.dayMetrics[today] ?: HomeDayWorkoutMetrics(0, 0, 0.0, 0)
    val dates = weekDates(today)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        FitnessHeader("오늘의 훈련", today)
        FitnessCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                Text(
                    when {
                        snapshot.inProgressSessionId != null -> "운동이 진행 중입니다"
                        snapshot.todaySessions.isEmpty() -> "오늘 운동 전입니다"
                        else -> "오늘 ${snapshot.todaySessions.size}회 운동했습니다"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                FitnessButton(
                    onClick = {
                        if (snapshot.inProgressSessionId != null) actions.continueWorkout()
                        else actions.navigate(FitnessScreen.WORKOUT)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (snapshot.inProgressSessionId != null) "운동 이어가기" else "피트니스에서 시작") }
            }
        }
        FitnessFactRow(
            first = { FitnessFactCard("오늘 볼륨", MassFormatter.format(metrics.totalVolumeKg, displayUnit), displayUnit.symbol()) },
            second = { FitnessFactCard("완료 세트", "${metrics.totalSetCount}개", "오늘 기록") }
        )
        FitnessFactRow(
            first = { FitnessFactCard("세션", "${metrics.sessionCount}회", "오늘") },
            second = { FitnessFactCard(
                "시간", if (metrics.totalDurationSeconds > 0) formatDuration(metrics.totalDurationSeconds) else "—", "오늘"
            ) }
        )

        FitnessSection("루틴 빠른 시작") {
            if (snapshot.routines.isEmpty()) {
                Text("만들어진 루틴이 없습니다.", style = MaterialTheme.typography.bodyMedium)
                FitnessButton(onClick = { actions.startEmptyWorkout() }, Modifier.fillMaxWidth()) { Text("루틴 없이 운동 시작") }
            } else {
                quickStartRoutines(snapshot.routines, snapshot.activeRoutineId).forEach { routine ->
                    FitnessCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                "${routine.exerciseCount}개 종목" +
                                    (snapshot.latestRoutineDates[routine.id]?.let { " · 최근 $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
                                FitnessOutlinedButton(onClick = {
                                    actions.selectRoutine(routine.id)
                                    actions.startRoutineWorkout(snapshot.routineExercises[routine.id].orEmpty())
                                }, Modifier.weight(1f)) { Text("시작") }
                                FitnessOutlinedButton(onClick = {
                                    actions.selectRoutine(routine.id)
                                    actions.navigate(FitnessScreen.ROUTINE_DETAIL)
                                }, Modifier.weight(1f)) { Text("상세 보기") }
                            }
                        }
                    }
                }
                FitnessOutlinedButton(onClick = { actions.navigate(FitnessScreen.WORKOUT) }, Modifier.fillMaxWidth()) {
                    Text("전체 루틴 보기")
                }
            }
        }

        FitnessSection("이번 주") {
            WeeklyVolumeCard(snapshot, dates, displayUnit)
            WeeklyMealCard(snapshot, dates)
            WeeklyNutritionCard(actions, snapshot, dates)
        }

        FitnessSection("빠른 기록") {
            FitnessFactRow(
                first = { FitnessFactCard("체중", snapshot.todayWeight?.let { MassFormatter.withUnit(it.weightKg, displayUnit) } ?: "미입력", "오늘") },
                second = { FitnessFactCard("식사", "${snapshot.mealCounts[today] ?: 0}끼", "오늘") }
            )
            FitnessOutlinedButton(onClick = { actions.showBodyMetric() }, Modifier.fillMaxWidth()) { Text("체중 기록") }
            FitnessOutlinedButton(
                onClick = { actions.openMealManagement(today, FitnessScreen.HOME) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("식사 기록") }
        }

        FitnessSection("오늘 기록") {
            val recordCount = snapshot.todaySessions.size + snapshot.todayBodyMetrics.size + snapshot.todayMeals.size
            if (recordCount == 0) Text("오늘 기록이 없습니다. 운동, 체중, 식사를 기록해 보세요.")
            var shown = 0
            snapshot.todaySessions.forEach { session ->
                if (shown < 3) {
                    FitnessCard(Modifier.fillMaxWidth()) { Text(stripLeadingDate(session), Modifier.padding(FitnessSpacing.card), fontWeight = FontWeight.Bold) }
                    shown++
                }
            }
            snapshot.todayBodyMetrics.forEach { metric ->
                if (shown < 3) {
                    BodyMetricRow(metric, displayUnit)
                    shown++
                }
            }
            snapshot.todayMeals.forEach { meal ->
                if (shown < 3) {
                    FitnessCard(Modifier.fillMaxWidth().clickable {
                        actions.openMealManagement(today, FitnessScreen.HOME)
                    }) {
                        Column(Modifier.padding(FitnessSpacing.card)) {
                            Text(meal.previewTitle, fontWeight = FontWeight.Bold)
                            Text(meal.previewSubtitle(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    shown++
                }
            }
            if (recordCount > 3) {
                FitnessOutlinedButton(onClick = { actions.navigate(FitnessScreen.RECORDS) }, Modifier.fillMaxWidth()) {
                    Text("${recordCount - 3}개 기록 더 보기")
                }
            }
        }
    }
}

@Composable
private fun BodyMetricRow(metric: HomeBodyMetric, displayUnit: MassUnit) {
    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card)) {
            Text(MassFormatter.withUnit(metric.weightKg, displayUnit), fontWeight = FontWeight.Bold)
            Text(if (metric.memo.isBlank()) "체중" else "체중 · ${metric.memo}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun weekDates(today: String): List<LocalDate> {
    val monday = LocalDate.parse(today).with(DayOfWeek.MONDAY)
    return (0..6).map { monday.plusDays(it.toLong()) }
}

private fun quickStartRoutines(routines: List<RoutineSummary>, activeRoutineId: String?): List<RoutineSummary> {
    val result = mutableListOf<RoutineSummary>()
    routines.firstOrNull { it.id == activeRoutineId }?.let { result += it }
    routines.forEach { routine -> if (result.size < 2 && result.none { it.id == routine.id }) result += routine }
    return result
}

@Composable
private fun WeeklyVolumeCard(snapshot: HomeSnapshot, dates: List<LocalDate>, displayUnit: MassUnit) {
    val current = dates.map { snapshot.dayMetrics[it.toString()] ?: HomeDayWorkoutMetrics(0, 0, 0.0, 0) }
    val previous = dates.map { date -> snapshot.dayMetrics[date.minusWeeks(1).toString()] ?: HomeDayWorkoutMetrics(0, 0, 0.0, 0) }
    val total = current.sumOf { it.totalVolumeKg }
    val prior = previous.sumOf { it.totalVolumeKg }
    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text("주간 볼륨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${MassFormatter.withUnit(total, displayUnit)} · 운동 ${current.count { it.sessionCount > 0 }}일 · 세트 ${current.sumOf { it.totalSetCount }}개")
            Text(weeklyComparison(total, prior, displayUnit), style = MaterialTheme.typography.bodySmall)
            WeeklyRows(dates.mapIndexed { index, date ->
                "${dayLabel(date)}  ${MassFormatter.formatValue(current[index].totalVolumeKg, displayUnit)}${displayUnit.symbol()} · 지난주 ${MassFormatter.formatValue(previous[index].totalVolumeKg, displayUnit)}${displayUnit.symbol()}"
            })
        }
    }
}

@Composable
private fun WeeklyMealCard(snapshot: HomeSnapshot, dates: List<LocalDate>) {
    val current = dates.map { snapshot.mealCounts[it.toString()] ?: 0 }
    val previous = dates.map { date -> snapshot.mealCounts[date.minusWeeks(1).toString()] ?: 0 }
    val total = current.sum()
    val prior = previous.sum()
    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text("주간 식사", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("${total}끼 · 기록 ${current.count { it > 0 }}일")
            Text(mealComparison(total, prior), style = MaterialTheme.typography.bodySmall)
            WeeklyRows(dates.mapIndexed { index, date -> "${dayLabel(date)}  ${current[index]}끼 · 지난주 ${previous[index]}끼" })
        }
    }
}

@Composable
private fun WeeklyNutritionCard(
    actions: HomeScreenActions,
    snapshot: HomeSnapshot,
    dates: List<LocalDate>
) {
    val goal = snapshot.nutritionGoal
    val totals = dates.map { snapshot.mealNutritionTotals[it.toString()] ?: emptyNutritionTotals() }
    val calories = totals.map { it.total(NutritionProfile.CALORIES_KCAL) }
    val carbs = totals.map { it.total(NutritionProfile.CARBS_GRAMS) }
    val protein = totals.map { it.total(NutritionProfile.PROTEIN_GRAMS) }
    val fat = totals.map { it.total(NutritionProfile.FAT_GRAMS) }
    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(FitnessSpacing.card), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
            Text("칼로리 / 탄단지 변화 추이", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("일평균 · kcal ${average(calories, "kcal")} · C ${average(carbs, "g")} · P ${average(protein, "g")} · F ${average(fat, "g")}", style = MaterialTheme.typography.bodySmall)
            Text(nutritionReference(goal), style = MaterialTheme.typography.bodySmall)
            if (goal == null) {
                Text("영양소별 목표를 설정하면 7일 달성률이 표시됩니다.")
                FitnessButton(onClick = {
                    actions.openMealManagement(snapshot.today, FitnessScreen.HOME)
                }) { Text("영양 목표 설정") }
            } else {
                dates.forEachIndexed { index, date ->
                    NutritionDayRow(dayLabel(date), calories[index], carbs[index], protein[index], fat[index], goal)
                }
            }
        }
    }
}

@Composable
private fun NutritionDayRow(label: String, calories: HomeNutritionTotal, carbs: HomeNutritionTotal,
                            protein: HomeNutritionTotal, fat: HomeNutritionTotal, goal: HomeNutritionGoal) {
    Column(Modifier.fillMaxWidth().padding(top = FitnessSpacing.micro), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        Text(label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        NutritionProgress("kcal", calories, goal.caloriesKcal, "kcal")
        NutritionProgress("C", carbs, goal.carbsGrams, "g")
        NutritionProgress("P", protein, goal.proteinGrams, "g")
        NutritionProgress("F", fat, goal.fatGrams, "g")
    }
}

@Composable
private fun NutritionProgress(label: String, total: HomeNutritionTotal, target: Double, unit: String) {
    val hasTarget = target > 0
    val ratio = if (total.isComplete() && hasTarget) (total.knownSum() / target).coerceIn(0.0, 1.25) else 0.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small)) {
        Text(label, Modifier.width(30.dp), style = MaterialTheme.typography.bodySmall)
        LinearProgressIndicator(progress = (ratio / 1.25).toFloat(), Modifier.weight(1f))
        Text(
            if (total.isComplete() && hasTarget) {
                "${total.describedValue()}$unit / ${NutritionCalculator.trim(target)}$unit (${NutritionCalculator.trim(total.knownSum() / target * 100)}%)"
            } else {
                "${total.describedValue()}$unit / 목표 ${NutritionCalculator.trim(target)}$unit"
            },
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WeeklyRows(rows: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) { rows.forEach { Text(it, style = MaterialTheme.typography.bodySmall) } }
}

private fun emptyNutritionTotals() = HomeNutritionTotals(0, emptyMap())

private fun dayLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("E", Locale.KOREAN))

private fun average(values: List<HomeNutritionTotal>, unit: String): String {
    val known = values.filter { it.isComplete() }
    if (known.isEmpty()) return "?"
    return NutritionCalculator.trim(known.sumOf { it.knownSum() } / known.size) + unit
}

private fun nutritionReference(goal: HomeNutritionGoal?): String = goal?.let {
    "기준 · kcal ${NutritionCalculator.trim(it.caloriesKcal)} · C ${NutritionCalculator.trim(it.carbsGrams)}g · P ${NutritionCalculator.trim(it.proteinGrams)}g · F ${NutritionCalculator.trim(it.fatGrams)}g"
} ?: "기준 미설정 · 영양 목표에서 영양소별 기준값을 설정하세요"

private fun weeklyComparison(current: Double, previous: Double, unit: MassUnit): String {
    val difference = current - previous
    if (kotlin.math.abs(difference) < 0.01) return "지난주와 동일한 볼륨"
    val direction = if (difference > 0) "증가" else "감소"
    val amount = MassFormatter.withUnit(kotlin.math.abs(difference), unit)
    if (previous <= 0.01) return "지난주 대비 $amount $direction"
    return "지난주 대비 $amount $direction (${FitnessUi.formatVolume(kotlin.math.abs(difference) / previous * 100)}%)"
}

private fun mealComparison(current: Int, previous: Int): String = when {
    current == previous -> "지난주와 동일한 식사 기록"
    current > previous -> "지난주 대비 ${current - previous}끼 증가"
    else -> "지난주 대비 ${previous - current}끼 감소"
}

private fun stripLeadingDate(value: String): String {
    val split = value.indexOf("  ")
    return if (split > 0) value.substring(split + 2) else value
}

private fun formatDuration(seconds: Int): String = FitnessUi.formatDuration(seconds)

@Composable
private fun LoadingHome() {
    Column(Modifier.fillMaxWidth().padding(vertical = FitnessSpacing.section)) {
        Text("오늘의 훈련", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("오늘의 기록과 루틴을 불러오는 중입니다.", modifier = Modifier.padding(top = FitnessSpacing.small))
    }
}
