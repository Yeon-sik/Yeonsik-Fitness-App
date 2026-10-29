package com.yeonsik.fitnessapp.feature.statistics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.statistics.model.*

interface StatisticsScreenActions {
    fun selectPeriod(period: StatisticsPeriod)
    fun retry() {}
}

@Composable
internal fun StatisticsScreen(state: StatisticsUiState, ownerId: String, unit: MassUnit, actions: StatisticsScreenActions) {
    val snapshot = (state as? StatisticsUiState.Ready)?.snapshot?.takeIf { it.ownerId == ownerId }
    val period = when (state) {
        is StatisticsUiState.Loading -> state.period.takeIf { state.ownerId == ownerId }
        is StatisticsUiState.Error -> state.period.takeIf { state.ownerId == ownerId }
        else -> snapshot?.window?.period
    } ?: StatisticsPeriod.THIRTY_DAYS
    AppHeader("통계", snapshot?.let { "기준일 ${it.referenceDate}" } ?: "기록의 변화를 확인하세요")
    Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)) {
        StatisticsPeriod.values().forEach { item ->
            AppOutlinedButton(onClick = { actions.selectPeriod(item) }, selected = item == period,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("statistics-period-${item.days}")
                    .semantics { role = Role.RadioButton; selected = item == period }) { Text(item.label) }
        }
    }
    when {
        state is StatisticsUiState.Error && state.ownerId == ownerId -> AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("통계를 불러오지 못했습니다.", style = MaterialTheme.typography.bodyLarge)
                AppOutlinedButton(onClick = actions::retry, modifier = Modifier.heightIn(min = 48.dp).testTag("statistics-retry")) { Text("다시 시도") }
            }
        }
        snapshot == null -> Text("통계를 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
        else -> key(snapshot.ownerId, snapshot.referenceDate, snapshot.window.period) { StatisticsContent(snapshot, unit) }
    }
}

@Composable
private fun StatisticsContent(snapshot: StatisticsSnapshot, unit: MassUnit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)) {
        PeriodOverview(snapshot, unit)
        TrendCard("volume", "주별 운동 볼륨", "완료된 운동의 합계 · ${unit.symbol()}", snapshot.workoutVolumeTrend, snapshot.window, true, unit.symbol(), unit)
        TrendCard("frequency", "주별 운동 횟수", "완료된 세션 수 · 회", snapshot.workoutFrequencyTrend, snapshot.window, true, "회")
        TrendCard("weight", "체중 추이", "같은 날의 기록 평균 · ${unit.symbol()}", snapshot.bodyWeightTrend, snapshot.window, false, unit.symbol(), unit)
        PerformanceSection(snapshot, unit)
        StatisticsBasis(snapshot)
    }
}

@Composable
private fun PeriodOverview(snapshot: StatisticsSnapshot, unit: MassUnit) {
    var comparisonVisible by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("기간 요약")
            Text("${snapshot.window.startDate} ~ ${snapshot.window.endDate}", style = MaterialTheme.typography.bodyMedium)
            val metrics = snapshot.comparison.filter { it.label == "운동 횟수" || it.label == "운동 볼륨" }
            if (LocalDensity.current.fontScale > 1.3f) {
                metrics.forEach { OverviewMetric(it, unit, Modifier.fillMaxWidth()) }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    metrics.forEach { OverviewMetric(it, unit, Modifier.weight(1f)) }
                }
            }
            val availability = when (snapshot.sufficiency.level) {
                StatisticsSufficiencyLevel.NONE -> "아직 기록이 없습니다. 미기록은 0으로 계산하지 않습니다."
                StatisticsSufficiencyLevel.LIMITED -> "기록이 적습니다. 추세는 기록이 더 쌓이면 표시됩니다."
                StatisticsSufficiencyLevel.READY -> "기록이 있는 항목에서 추세를 확인할 수 있습니다."
            }
            Text(availability, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DisclosureButton("statistics-comparison", comparisonVisible, "이전 ${snapshot.window.period.days}일과 비교", "기간 비교 접기") { comparisonVisible = !comparisonVisible }
            if (comparisonVisible) {
                Text("이전 ${snapshot.window.previousStartDate} ~ ${snapshot.window.previousEndDate}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                snapshot.comparison.forEach { metric ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(metric.label, style = MaterialTheme.typography.titleSmall)
                        Text("현재 ${statisticsMetricValue(metric.current, metric, unit)} · 이전 ${statisticsMetricValue(metric.previous, metric, unit)}", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            metric.difference?.takeIf { it.isFinite() }?.let { difference ->
                                Box(Modifier.size(6.dp).background(if (difference >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, CircleShape))
                            }
                            Text(statisticsMetricDifference(metric, unit), style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("statistics-difference-${metric.label}"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewMetric(metric: StatisticsComparisonMetric, unit: MassUnit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(metric.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(statisticsMetricValue(metric.current, metric, unit), Modifier.testTag("statistics-overview-${metric.label}"),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TrendCard(id: String, title: String, subtitle: String, points: List<StatisticsTrendPoint>,
    window: StatisticsPeriodWindow, weekly: Boolean, unit: String, massUnit: MassUnit? = null) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(title)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (weekly) Text("월요일 시작 · 경계 주는 선택 기간만 집계", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatisticsTrendChart(id, points, window, weekly, unit, massUnit)
        }
    }
}

@Composable
private fun PerformanceSection(snapshot: StatisticsSnapshot, unit: MassUnit) {
    var expandedKey by remember { mutableStateOf<String?>(null) }
    SectionTitle("종목별 수행")
    if (snapshot.exercisePerformanceTrends.isEmpty()) Text("이 기간에 표시할 종목 기록이 없습니다.", style = MaterialTheme.typography.bodyMedium)
    snapshot.exercisePerformanceTrends.forEach { trend ->
        val expanded = expandedKey == trend.performanceKey
        val metric = if (trend.metric == StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX) "추정 1RM" else "세션 볼륨"
        val latest = trend.points.lastOrNull { it.value?.isFinite() == true }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag("statistics-exercise-${trend.performanceKey}")
                .clickable(role = Role.Button, onClickLabel = if (expanded) "추이 접기" else "추이 보기") { expandedKey = if (expanded) null else trend.performanceKey }
                .semantics { stateDescription = if (expanded) "펼침" else "접힘" }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(trend.exerciseName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("statistics-exercise-title-${trend.performanceKey}"))
                Text(latest?.let { MassFormatter.withUnit(it.value!!, unit) } ?: "미기록", style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp), fontWeight = FontWeight.Bold)
                Text("$metric · ${latest?.date ?: latest?.label ?: "미기록"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (expanded) "추이 접기 ∧" else "추이 보기 ∨", style = MaterialTheme.typography.bodyMedium)
            }
            if (expanded) Column(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatisticsTrendChart("exercise-${trend.performanceKey}", trend.points, snapshot.window, false, unit.symbol(), unit)
            }
        }
    }
}

@Composable
private fun StatisticsBasis(snapshot: StatisticsSnapshot) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DisclosureButton("statistics-basis", expanded, "기록 범위·계산 기준", "계산 기준 접기") { expanded = !expanded }
            if (expanded) {
                val data = snapshot.sufficiency
                Text("선택한 ${data.periodDays}일 중\n운동 ${data.workoutDays}일 · ${data.workoutSessions}회\n체중 ${data.weightDays}일 · 식단 ${data.mealDays}일", style = MaterialTheme.typography.bodyMedium)
                SectionTitle("관찰 신호")
                Text("기록된 숫자만 보여 주는 참고 신호입니다. 선호·성향으로 확정하거나 저장하지 않습니다.", style = MaterialTheme.typography.bodyMedium)
                if (snapshot.observedPatterns.isEmpty()) Text("표시할 관찰 신호가 없습니다.", style = MaterialTheme.typography.bodyMedium)
                snapshot.observedPatterns.forEach { Text("· $it", style = MaterialTheme.typography.bodyMedium) }
                Text("운동 볼륨과 횟수는 완료된 운동에서 집계합니다. 주간 합계의 경계 주에는 선택 기간에 포함된 날짜만 사용합니다. 추정 1RM과 세션 볼륨은 기존 운동 수행 산식을 사용합니다.", style = MaterialTheme.typography.bodyMedium)
                Text("미기록은 0이 아닙니다. 차트는 시간 간격을 유지하며 기록이 없는 구간을 연결하지 않습니다. 왼쪽 숫자는 차트의 축 범위이며 실제 최댓값·최솟값과 다를 수 있습니다.", style = MaterialTheme.typography.bodyMedium)
                Text("Workout · Body · Meal의 소유 기록을 읽어 계산했습니다. 이 화면은 원본 기록을 저장·수정·삭제하지 않습니다.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp), fontWeight = FontWeight.Bold)
}

@Composable
private fun DisclosureButton(tag: String, expanded: Boolean, collapsedLabel: String, expandedLabel: String, onClick: () -> Unit) {
    AppOutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(tag)
        .semantics { stateDescription = if (expanded) "펼침" else "접힘" }) { Text(if (expanded) expandedLabel else collapsedLabel) }
}
