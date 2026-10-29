package com.yeonsik.fitnessapp.feature.statistics.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.statistics.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

internal data class StatisticsChartEntry(
    val label: String,
    val value: Double?,
    val day: Long?,
    val fraction: Float?,
    val connectsToPrevious: Boolean
)

/** Keeps absent observations and elapsed time; never interpolates or changes an aggregate. */
internal fun statisticsChartEntries(
    points: List<StatisticsTrendPoint>,
    window: StatisticsPeriodWindow,
    weekly: Boolean,
    massUnit: MassUnit? = null
): List<StatisticsChartEntry> {
    val start = LocalDate.parse(window.startDate)
    val end = LocalDate.parse(window.endDate)
    val axisStart = if (weekly) start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else start
    val axisEnd = if (weekly) end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) else end
    val span = (axisEnd.toEpochDay() - axisStart.toEpochDay()).coerceAtLeast(1)
    var previousDay: Long? = null
    var previousValue: Double? = null
    return points.map { point ->
        val date = runCatching { LocalDate.parse(point.date) }.getOrNull()
        val day = date?.toEpochDay()
        val value = point.value?.takeIf { it.isFinite() }?.let {
            if (massUnit == null) it else MassUnit.fromKg(it, massUnit)
        }?.takeIf { it.isFinite() }
        val fraction = day?.takeIf { it in axisStart.toEpochDay()..axisEnd.toEpochDay() }
            ?.let { if (axisStart == axisEnd) .5f else ((it - axisStart.toEpochDay()).toDouble() / span).toFloat() }
        val label = if (weekly && date != null) {
            val first = maxOf(date, start)
            val last = minOf(date.plusDays(6), end)
            "$first ~ $last" + if (first != date || last != date.plusDays(6)) " · 일부 주" else ""
        } else date?.toString() ?: point.label
        val entry = StatisticsChartEntry(label, value, day, fraction,
            fraction != null && value != null && previousValue != null && previousDay != null &&
                day - previousDay == if (weekly) 7L else 1L)
        previousDay = day.takeIf { fraction != null }
        previousValue = value.takeIf { fraction != null }
        entry
    }
}

internal fun statisticsMetricUnit(metric: StatisticsComparisonMetric): String =
    if (metric.label.endsWith("일수")) "일" else "회"

internal fun statisticsMetricValue(value: Double?, metric: StatisticsComparisonMetric, unit: MassUnit): String {
    if (value == null || !value.isFinite()) return "미기록"
    return when (metric.kind) {
        StatisticsValueKind.COUNT -> "${value.roundToInt()}${statisticsMetricUnit(metric)}"
        StatisticsValueKind.MASS_KG -> MassFormatter.withUnit(value, unit)
    }
}

internal fun statisticsMetricDifference(metric: StatisticsComparisonMetric, unit: MassUnit): String {
    val value = metric.difference?.takeIf { it.isFinite() } ?: return "비교할 기록 없음"
    return when (metric.kind) {
        StatisticsValueKind.COUNT -> String.format(Locale.ROOT, "%+.0f%s", value, statisticsMetricUnit(metric))
        StatisticsValueKind.MASS_KG -> String.format(Locale.ROOT, "%+,.1f %s", MassUnit.fromKg(value, unit), unit.symbol())
    }
}
