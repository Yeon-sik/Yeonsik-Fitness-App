package com.yeonsik.fitnessapp.feature.statistics.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.yeonsik.fitnessapp.core.ui.AppCard
import com.yeonsik.fitnessapp.core.ui.AppHeader
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessTrendChart
import com.yeonsik.fitnessapp.core.ui.FitnessTrendPoint
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.fitnessTrendPresentation
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsComparisonMetric
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPerformanceMetric
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPerformanceTrend
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSnapshot
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSufficiencyLevel
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsTrendPoint
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsValueKind
import kotlin.math.roundToInt

interface StatisticsScreenActions {
    fun selectPeriod(period: StatisticsPeriod)
}

@Composable
internal fun StatisticsScreen(
    state: StatisticsUiState,
    ownerId: String,
    unit: MassUnit,
    actions: StatisticsScreenActions
) {
    val snapshot = (state as? StatisticsUiState.Ready)
        ?.snapshot
        ?.takeIf { it.ownerId == ownerId }
    AppHeader(
        title = "통계",
        subtitle = snapshot?.let { "기준일 ${it.referenceDate}" } ?: "기록에서 계산한 읽기 전용 화면"
    )
    PeriodSelector(snapshot?.window?.period ?: StatisticsPeriod.THIRTY_DAYS, actions)
    when {
        state is StatisticsUiState.Error && state.ownerId == ownerId -> {
            AppCard(Modifier.fillMaxWidth()) {
                Text(state.message, Modifier.padding(AppSpacing.card))
            }
        }
        snapshot == null -> {
            Text(
                if (state is StatisticsUiState.Loading) "통계를 불러오는 중입니다."
                else "통계를 준비하고 있습니다."
            )
        }
        else -> StatisticsContent(snapshot, unit)
    }
}

@Composable
private fun PeriodSelector(
    selected: StatisticsPeriod,
    actions: StatisticsScreenActions
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
    ) {
        StatisticsPeriod.values().forEach { period ->
            AppOutlinedButton(
                onClick = { actions.selectPeriod(period) },
                selected = period == selected,
                modifier = Modifier.weight(1f)
            ) { Text(period.label) }
        }
    }
}

@Composable
private fun StatisticsContent(
    snapshot: StatisticsSnapshot,
    unit: MassUnit
) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        SufficiencyCard(snapshot)
        TrendCard(
            title = "운동 볼륨 추이",
            points = snapshot.workoutVolumeTrend,
            displayUnit = unit,
            chartUnit = unit.symbol(),
            mass = true,
            emptyLabel = "완료된 운동 기록이 없어 볼륨을 표시할 수 없습니다."
        )
        TrendCard(
            title = "운동 빈도",
            points = snapshot.workoutFrequencyTrend,
            displayUnit = null,
            chartUnit = "회",
            mass = false,
            emptyLabel = "완료된 운동 기록이 없어 빈도를 표시할 수 없습니다."
        )
        PerformanceSection(snapshot.exercisePerformanceTrends, unit)
        TrendCard(
            title = "체중 추이",
            points = snapshot.bodyWeightTrend,
            displayUnit = unit,
            chartUnit = unit.symbol(),
            mass = true,
            emptyLabel = "체중 기록이 없어 추이를 표시할 수 없습니다."
        )
        ComparisonCard(snapshot, unit)
        ObservedPatternsCard(snapshot)
        AppCard(Modifier.fillMaxWidth()) {
            Text(
                "Workout · Body · Meal의 소유 기록을 읽어 계산했습니다. " +
                    "이 화면은 원본 기록을 저장·수정·삭제하지 않습니다.",
                Modifier.padding(AppSpacing.card),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun SufficiencyCard(snapshot: StatisticsSnapshot) {
    val sufficiency = snapshot.sufficiency
    val levelLabel = when (sufficiency.level) {
        StatisticsSufficiencyLevel.NONE -> "데이터 없음"
        StatisticsSufficiencyLevel.LIMITED -> "데이터 부족"
        StatisticsSufficiencyLevel.READY -> "기본 추세 확인 가능"
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("데이터 충분성", style = MaterialTheme.typography.titleMedium)
            Text(levelLabel, fontWeight = FontWeight.Bold)
            Text(
                "운동 ${sufficiency.workoutDays}일 · 세션 ${sufficiency.workoutSessions}회 · " +
                    "체중 ${sufficiency.weightDays}일 · 식사 ${sufficiency.mealDays}일 / " +
                    "${sufficiency.periodDays}일",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                when (sufficiency.level) {
                    StatisticsSufficiencyLevel.NONE ->
                        "원본 기록이 없어 추세와 비교를 계산할 수 없습니다."
                    StatisticsSufficiencyLevel.LIMITED ->
                        "일부 기록만 있어 추세 해석이 제한됩니다. 빈 카드의 미기록 상태를 0으로 보지 마세요."
                    StatisticsSufficiencyLevel.READY ->
                        "표시된 값은 기록된 사실에서만 계산했습니다."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrendCard(
    title: String,
    points: List<StatisticsTrendPoint>,
    displayUnit: MassUnit?,
    chartUnit: String,
    mass: Boolean,
    emptyLabel: String
) {
    val model = fitnessTrendPresentation(
        points.map { point ->
            FitnessTrendPoint(
                label = point.label,
                value = point.value?.let { value ->
                    if (mass) displayUnit?.let { MassUnit.fromKg(value, it) } else value
                }
            )
        },
        minimumPoints = 2
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            FitnessTrendChart(
                model = model,
                modifier = Modifier.fillMaxWidth(),
                unit = chartUnit,
                emptyLabel = emptyLabel,
                insufficientLabel = "추세를 표시하려면 서로 다른 시점의 기록이 더 필요합니다."
            )
        }
    }
}

@Composable
private fun PerformanceSection(
    trends: List<StatisticsPerformanceTrend>,
    unit: MassUnit
) {
    if (trends.isEmpty()) {
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(AppSpacing.card)) {
                Text("종목 수행 추세", style = MaterialTheme.typography.titleMedium)
                Text(
                    "비교 가능한 완료 세트가 없어 종목 수행 추세를 표시할 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FitnessSpacing.gap)
    ) {
        Text("종목 수행 추세", style = MaterialTheme.typography.titleMedium)
        trends.forEach { trend ->
            PerformanceCard(trend, unit)
        }
    }
}

@Composable
private fun PerformanceCard(
    trend: StatisticsPerformanceTrend,
    unit: MassUnit
) {
    val isE1rm = trend.metric == StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX
    val model = fitnessTrendPresentation(
        trend.points.map { point ->
            FitnessTrendPoint(
                label = point.label,
                value = point.value?.let { value -> MassUnit.fromKg(value, unit) }
            )
        },
        minimumPoints = 2
    )
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpacing.card)) {
            Text(trend.exerciseName, style = MaterialTheme.typography.titleMedium)
            Text(
                if (isE1rm) "추정 1RM · 기존 Workout 수행 산식"
                else "세션 볼륨 · 기존 Workout 완료 세트",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.padding(FitnessSpacing.micro))
            FitnessTrendChart(
                model = model,
                modifier = Modifier.fillMaxWidth(),
                unit = unit.symbol(),
                emptyLabel = "비교 가능한 수행 기록이 없습니다.",
                insufficientLabel = "이 종목의 서로 다른 수행 시점이 더 필요합니다."
            )
        }
    }
}

@Composable
private fun ComparisonCard(
    snapshot: StatisticsSnapshot,
    unit: MassUnit
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("기간 비교", style = MaterialTheme.typography.titleMedium)
            Text(
                "현재 ${snapshot.window.startDate} ~ ${snapshot.window.endDate}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "이전 동일 길이 ${snapshot.window.previousStartDate} ~ " +
                    "${snapshot.window.previousEndDate}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            snapshot.comparison.forEach { metric ->
                ComparisonRow(metric, unit)
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    metric: StatisticsComparisonMetric,
    unit: MassUnit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)
    ) {
        Column(Modifier.weight(1f)) {
            Text(metric.label, fontWeight = FontWeight.Bold)
            Text(
                "현재 ${formatValue(metric.current, metric.kind, unit)} · " +
                    "이전 ${formatValue(metric.previous, metric.kind, unit)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
        metric.difference?.let { difference ->
            Text(
                formatDifference(difference, metric.kind, unit),
                style = MaterialTheme.typography.bodySmall,
                color = if (difference >= 0.0) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun ObservedPatternsCard(snapshot: StatisticsSnapshot) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AppSpacing.card),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
        ) {
            Text("관찰 신호", style = MaterialTheme.typography.titleMedium)
            Text(
                "기록된 숫자만 보여 주는 참고 신호입니다. 선호·성향으로 확정하거나 저장하지 않습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (snapshot.observedPatterns.isEmpty()) {
                Text("현재 기간에 표시할 관찰 신호가 없습니다.")
            } else {
                snapshot.observedPatterns.forEach { pattern -> Text("· $pattern") }
            }
        }
    }
}

private fun formatValue(
    value: Double?,
    kind: StatisticsValueKind,
    unit: MassUnit
): String {
    if (value == null || !value.isFinite()) return "미기록"
    return when (kind) {
        StatisticsValueKind.COUNT -> "${value.roundToInt()}회"
        StatisticsValueKind.MASS_KG -> MassFormatter.withUnit(value, unit)
    }
}

@Composable
private fun formatDifference(
    value: Double,
    kind: StatisticsValueKind,
    unit: MassUnit
): String =
    when (kind) {
        StatisticsValueKind.COUNT ->
            String.format(java.util.Locale.ROOT, "%+.0f회", value)
        StatisticsValueKind.MASS_KG ->
            String.format(
                java.util.Locale.ROOT,
                "%+.1f %s",
                MassUnit.fromKg(value, unit),
                unit.symbol()
            )
    }
