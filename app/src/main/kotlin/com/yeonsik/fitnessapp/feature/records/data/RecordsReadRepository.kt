package com.yeonsik.fitnessapp.feature.records.data

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.records.api.RecordsReadApi
import com.yeonsik.fitness.shared.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitness.shared.feature.records.model.RecordsDayDetail
import com.yeonsik.fitness.shared.feature.records.model.RecordsSnapshot
import com.yeonsik.fitness.shared.feature.records.model.RecordsWeightPoint
import com.yeonsik.fitness.shared.feature.records.model.RecordsWorkoutSummary
import com.yeonsik.fitness.shared.feature.workout.api.WorkoutReadApi
import java.time.LocalDate
import java.time.YearMonth

/** Composes the Records read model from the owning feature read ports. */
class RecordsReadRepository(
    private val workouts: WorkoutReadApi,
    private val body: BodyMetricsReadApi,
    private val meals: MealReadApi
) : RecordsReadApi {
    override fun load(
        scope: AccountScope,
        displayedMonth: String,
        today: String
    ): RecordsSnapshot {
        require(scope.ownerId.isNotBlank()) { "Records owner is required." }
        val month = YearMonth.parse(displayedMonth.trim())
        val currentDay = LocalDate.parse(today.trim())
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val calendarStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val calendarEnd = calendarStart.plusDays(CALENDAR_CELL_COUNT - 1L)
        val calendarStartText = calendarStart.toString()
        val calendarEndText = calendarEnd.toString()

        val sessions = workouts.completedSessionSummaries(
            scope, calendarStartText, calendarEndText
        )
        val workoutsByDate = sessions.groupBy { it.date }
        val bodyByDate = body.weightEntries(scope, calendarStartText, calendarEndText)
            .groupBy { it.date }
        val mealsByDate = meals.mealSummaries(scope, calendarStartText, calendarEndText)
            .groupBy { it.date }
        val dayDetailsByDate = (0 until CALENDAR_CELL_COUNT).associate { offset ->
            val date = calendarStart.plusDays(offset.toLong()).toString()
            date to RecordsDayDetail(
                date = date,
                workouts = workoutsByDate[date].orEmpty().map { session ->
                    RecordsWorkoutSummary(
                        id = session.id,
                        date = session.date,
                        title = session.title,
                        workoutType = session.workoutType,
                        durationSeconds = session.durationSeconds,
                        totalVolumeKg = session.totalVolumeKg,
                        completedSetCount = session.completedSetCount,
                        muscleLabels = session.muscleLabels
                    )
                },
                bodyMetrics = bodyByDate[date].orEmpty(),
                meals = mealsByDate[date].orEmpty()
            )
        }
        val calendarDays = (0 until CALENDAR_CELL_COUNT).map { offset ->
            val date = calendarStart.plusDays(offset.toLong()).toString()
            val detail = dayDetailsByDate.getValue(date)
            RecordsCalendarDay(
                date = date,
                hasWorkout = detail.workouts.isNotEmpty(),
                hasBodyMetric = detail.bodyMetrics.isNotEmpty(),
                hasMeal = detail.meals.isNotEmpty(),
                muscleLabels = detail.workouts.asSequence()
                    .flatMap { it.muscleLabels.asSequence() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
            )
        }
        val weightTrend = bodyByDate.values.flatten()
            .filter { it.date >= first.toString() && it.date <= last.toString() }
            .groupBy { it.date }
            .toSortedMap()
            .mapNotNull { (date, entries) ->
                entries.map { it.weightKg }
                    .filter(Double::isFinite)
                    .takeIf { it.isNotEmpty() }
                    ?.let { values -> RecordsWeightPoint(date, values.average()) }
            }

        return RecordsSnapshot(
            ownerId = scope.ownerId,
            displayedMonth = month.toString(),
            today = currentDay.toString(),
            calendarDays = calendarDays,
            dayDetailsByDate = dayDetailsByDate,
            weightTrend = weightTrend
        )
    }

    private companion object {
        const val CALENDAR_CELL_COUNT = 42
    }
}
