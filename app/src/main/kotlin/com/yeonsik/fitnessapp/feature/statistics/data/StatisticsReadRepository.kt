package com.yeonsik.fitnessapp.feature.statistics.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.statistics.api.StatisticsReadApi
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsComparisonMetric
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPerformanceMetric
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPerformanceTrend
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriodWindow
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsSnapshot
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsTrendPoint
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsValueKind
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsDataSufficiency
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExercisePerformancePoint
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutReadSessionSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Composes statistics from owner-scoped source facts. It has no write or persistence path. */
class StatisticsReadRepository(
    private val workouts: WorkoutReadApi,
    private val body: BodyMetricsReadApi,
    private val meals: MealReadApi
) : StatisticsReadApi {
    override fun load(
        scope: AccountScope,
        referenceDate: String,
        period: StatisticsPeriod
    ): StatisticsSnapshot {
        require(scope.ownerId.isNotBlank()) { "Statistics owner is required." }
        val reference = LocalDate.parse(referenceDate.trim())
        val window = StatisticsPeriodWindow.around(reference, period)
        val previousStart = LocalDate.parse(window.previousStartDate)
        val previousEnd = LocalDate.parse(window.previousEndDate)
        val currentStart = LocalDate.parse(window.startDate)
        val currentEnd = LocalDate.parse(window.endDate)

        val allSessions = workouts.completedSessionSummaries(
            scope,
            window.previousStartDate,
            window.endDate
        )
        val currentSessions = allSessions.filter { it.date.isBetween(currentStart, currentEnd) }
        val previousSessions = allSessions.filter { it.date.isBetween(previousStart, previousEnd) }

        val allWeightEntries = body.weightEntries(
            scope,
            window.previousStartDate,
            window.endDate
        )
        val currentWeights = allWeightEntries.filter { it.date.isBetween(currentStart, currentEnd) }
        val previousWeights = allWeightEntries.filter { it.date.isBetween(previousStart, previousEnd) }

        val allMealDates = meals.dates(scope, window.previousStartDate, window.endDate)
            .mapNotNull { it.toLocalDateOrNull() }
        val currentMealDates = allMealDates.filter { it in currentStart..currentEnd }.toSet()
        val previousMealDates = allMealDates.filter { it in previousStart..previousEnd }.toSet()

        val currentPerformance = workouts.exercisePerformance(
            scope,
            window.startDate,
            window.endDate
        ).filter { it.date.isBetween(currentStart, currentEnd) }

        val workoutDays = currentSessions.mapNotNull { it.date.toLocalDateOrNull() }.toSet().size
        val currentWeightDays = currentWeights.mapNotNull { it.date.toLocalDateOrNull() }.toSet().size
        val sufficiency = StatisticsDataSufficiency(
            periodDays = period.days,
            workoutDays = workoutDays,
            workoutSessions = currentSessions.size,
            weightDays = currentWeightDays,
            mealDays = currentMealDates.size
        )

        return StatisticsSnapshot(
            ownerId = scope.ownerId,
            referenceDate = reference.toString(),
            window = window,
            workoutVolumeTrend = weeklyWorkoutTrend(currentSessions, currentStart, currentEnd) { it.totalVolumeKg },
            workoutFrequencyTrend = weeklyWorkoutTrend(currentSessions, currentStart, currentEnd) { 1.0 },
            exercisePerformanceTrends = performanceTrends(currentPerformance),
            bodyWeightTrend = weightTrend(currentWeights),
            comparison = comparison(
                currentSessions,
                previousSessions,
                currentWeights,
                previousWeights,
                currentMealDates.size,
                previousMealDates.size
            ),
            observedPatterns = observedPatterns(
                currentSessions,
                currentWeights,
                currentMealDates.size,
                period.days
            ),
            sufficiency = sufficiency
        )
    }

    private fun weeklyWorkoutTrend(
        sessions: List<WorkoutReadSessionSummary>,
        start: LocalDate,
        end: LocalDate,
        value: (WorkoutReadSessionSummary) -> Double
    ): List<StatisticsTrendPoint> {
        if (sessions.isEmpty()) return emptyList()
        val firstWeek = start.with(DayOfWeek.MONDAY)
        val lastWeek = end.with(DayOfWeek.MONDAY)
        val formatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
        val result = mutableListOf<StatisticsTrendPoint>()
        var cursor = firstWeek
        while (!cursor.isAfter(lastWeek)) {
            val bucketEnd = cursor.plusDays(6)
            val bucket = sessions.filter { session ->
                session.date.toLocalDateOrNull()?.let { it in cursor..bucketEnd } == true
            }
            val finiteValues = bucket.map(value).filter { it.isFinite() }
            result += StatisticsTrendPoint(
                label = cursor.format(formatter),
                value = finiteValues.takeIf { it.isNotEmpty() }?.sum(),
                date = cursor.toString()
            )
            cursor = cursor.plusWeeks(1)
        }
        return result
    }

    private fun weightTrend(
        entries: List<com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry>
    ): List<StatisticsTrendPoint> {
        val formatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
        return entries
            .mapNotNull { entry ->
                entry.date.toLocalDateOrNull()?.let { date -> date to entry.weightKg }
            }
            .filter { (_, value) -> value.isFinite() }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
            .map { (date, values) ->
                StatisticsTrendPoint(
                    label = date.format(formatter),
                    value = values.average().takeIf { it.isFinite() },
                    date = date.toString()
                )
            }
    }

    private fun performanceTrends(
        points: List<WorkoutExercisePerformancePoint>
    ): List<StatisticsPerformanceTrend> =
        points.groupBy { it.performanceKey }
            .mapNotNull { (key, rows) ->
                val hasE1rm = rows.any { it.estimatedOneRepMaxKg?.isFinite() == true }
                val hasVolume = rows.any { it.volumeKg?.isFinite() == true }
                val metric = when {
                    hasE1rm -> StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX
                    hasVolume -> StatisticsPerformanceMetric.SESSION_VOLUME
                    else -> return@mapNotNull null
                }
                val formatter = DateTimeFormatter.ofPattern("M/d", Locale.KOREA)
                val trendPoints = rows
                    .mapNotNull { row -> row.date.toLocalDateOrNull()?.let { it to row } }
                    .groupBy({ it.first }, { it.second })
                    .toSortedMap()
                    .map { (date, dayRows) ->
                        val value = when (metric) {
                            StatisticsPerformanceMetric.ESTIMATED_ONE_REP_MAX ->
                                dayRows.mapNotNull { it.estimatedOneRepMaxKg }
                                    .filter { it.isFinite() }
                                    .maxOrNull()
                            StatisticsPerformanceMetric.SESSION_VOLUME ->
                                dayRows.mapNotNull { it.volumeKg }
                                    .filter { it.isFinite() }
                                    .takeIf { it.isNotEmpty() }
                                    ?.sum()
                        }
                        StatisticsTrendPoint(
                            label = date.format(formatter),
                            value = value,
                            date = date.toString()
                        )
                    }
                if (trendPoints.isEmpty()) return@mapNotNull null
                StatisticsPerformanceTrend(
                    performanceKey = key,
                    exerciseName = rows.maxByOrNull { it.date }?.exerciseName
                        ?.ifBlank { "운동" } ?: "운동",
                    metric = metric,
                    points = trendPoints
                )
            }
            .sortedWith(compareByDescending<StatisticsPerformanceTrend> { it.points.size }
                .thenBy { it.exerciseName })
            .take(MAX_PERFORMANCE_TRENDS)

    private fun comparison(
        currentSessions: List<WorkoutReadSessionSummary>,
        previousSessions: List<WorkoutReadSessionSummary>,
        currentWeights: List<com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry>,
        previousWeights: List<com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry>,
        currentMealDays: Int,
        previousMealDays: Int
    ): List<StatisticsComparisonMetric> = listOf(
        StatisticsComparisonMetric(
            label = "운동 횟수",
            current = currentSessions.size.toDouble(),
            previous = previousSessions.size.toDouble(),
            kind = StatisticsValueKind.COUNT
        ),
        StatisticsComparisonMetric(
            label = "운동 기록 일수",
            current = currentSessions.map { it.date }.distinct().size.toDouble(),
            previous = previousSessions.map { it.date }.distinct().size.toDouble(),
            kind = StatisticsValueKind.COUNT
        ),
        StatisticsComparisonMetric(
            label = "운동 볼륨",
            current = finiteSum(currentSessions.map { it.totalVolumeKg }),
            previous = finiteSum(previousSessions.map { it.totalVolumeKg }),
            kind = StatisticsValueKind.MASS_KG
        ),
        StatisticsComparisonMetric(
            label = "평균 체중",
            current = averageWeight(currentWeights),
            previous = averageWeight(previousWeights),
            kind = StatisticsValueKind.MASS_KG
        ),
        StatisticsComparisonMetric(
            label = "식사 기록 일수",
            current = currentMealDays.toDouble(),
            previous = previousMealDays.toDouble(),
            kind = StatisticsValueKind.COUNT
        )
    )

    private fun observedPatterns(
        sessions: List<WorkoutReadSessionSummary>,
        weights: List<com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry>,
        mealDays: Int,
        periodDays: Int
    ): List<String> = buildList {
        val workoutDays = sessions.map { it.date }.distinct().size
        if (sessions.isNotEmpty()) add("운동 기록 \${workoutDays}일 · 세션 \${sessions.size}회 / \${periodDays}일")
        val weightDays = weights.map { it.date }.distinct().size
        if (weights.isNotEmpty()) add("체중 기록 \${weightDays}일")
        if (mealDays > 0) add("식사 기록 \${mealDays}일")
    }

    private fun finiteSum(values: List<Double>): Double? =
        values.filter { it.isFinite() }.takeIf { it.isNotEmpty() }?.sum()

    private fun averageWeight(
        entries: List<com.yeonsik.fitness.shared.feature.body.model.BodyReadEntry>
    ): Double? {
        val daily = entries.mapNotNull { entry ->
            entry.date.toLocalDateOrNull()?.let { it to entry.weightKg }
        }.filter { (_, value) -> value.isFinite() }
            .groupBy({ it.first }, { it.second })
            .values
            .map { it.average() }
        return daily.takeIf { it.isNotEmpty() }?.average()?.takeIf { it.isFinite() }
    }

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(trim()) }.getOrNull()

    private fun String.isBetween(start: LocalDate, end: LocalDate): Boolean =
        toLocalDateOrNull()?.let { it in start..end } == true

    private companion object {
        const val MAX_PERFORMANCE_TRENDS = 3
    }
}
