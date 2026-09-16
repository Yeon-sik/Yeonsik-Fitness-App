package com.yeonsik.fitnessapp.feature.statistics.model

import java.time.LocalDate

enum class StatisticsPeriod(val days: Int, val label: String) {
    SEVEN_DAYS(7, "7일"),
    THIRTY_DAYS(30, "30일"),
    NINETY_DAYS(90, "90일");

    companion object {
        fun fromName(value: String?): StatisticsPeriod =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(THIRTY_DAYS)
    }
}

data class StatisticsPeriodWindow(
    val period: StatisticsPeriod,
    val startDate: String,
    val endDate: String,
    val previousStartDate: String,
    val previousEndDate: String
) {
    companion object {
        fun around(referenceDate: LocalDate, period: StatisticsPeriod): StatisticsPeriodWindow {
            val end = referenceDate
            val start = end.minusDays((period.days - 1).toLong())
            val previousEnd = start.minusDays(1)
            val previousStart = previousEnd.minusDays((period.days - 1).toLong())
            return StatisticsPeriodWindow(
                period = period,
                startDate = start.toString(),
                endDate = end.toString(),
                previousStartDate = previousStart.toString(),
                previousEndDate = previousEnd.toString()
            )
        }
    }
}

enum class StatisticsPerformanceMetric {
    ESTIMATED_ONE_REP_MAX,
    SESSION_VOLUME
}

data class StatisticsTrendPoint(
    val label: String,
    val value: Double?,
    val date: String? = null
)

data class StatisticsPerformanceTrend(
    val performanceKey: String,
    val exerciseName: String,
    val metric: StatisticsPerformanceMetric,
    val points: List<StatisticsTrendPoint>
)

enum class StatisticsValueKind {
    COUNT,
    MASS_KG
}

data class StatisticsComparisonMetric(
    val label: String,
    val current: Double?,
    val previous: Double?,
    val kind: StatisticsValueKind
) {
    val difference: Double?
        get() = if (current != null && previous != null) current - previous else null
}

enum class StatisticsSufficiencyLevel {
    NONE,
    LIMITED,
    READY
}

data class StatisticsDataSufficiency(
    val periodDays: Int,
    val workoutDays: Int,
    val workoutSessions: Int,
    val weightDays: Int,
    val mealDays: Int
) {
    val hasAnyData: Boolean
        get() = workoutSessions > 0 || weightDays > 0 || mealDays > 0

    val level: StatisticsSufficiencyLevel
        get() = when {
            !hasAnyData -> StatisticsSufficiencyLevel.NONE
            workoutSessions >= 2 || weightDays >= 2 || mealDays >= 2 ->
                StatisticsSufficiencyLevel.READY
            else -> StatisticsSufficiencyLevel.LIMITED
        }
}

data class StatisticsSnapshot(
    val ownerId: String,
    val referenceDate: String,
    val window: StatisticsPeriodWindow,
    val workoutVolumeTrend: List<StatisticsTrendPoint>,
    val workoutFrequencyTrend: List<StatisticsTrendPoint>,
    val exercisePerformanceTrends: List<StatisticsPerformanceTrend>,
    val bodyWeightTrend: List<StatisticsTrendPoint>,
    val comparison: List<StatisticsComparisonMetric>,
    val observedPatterns: List<String>,
    val sufficiency: StatisticsDataSufficiency
)
