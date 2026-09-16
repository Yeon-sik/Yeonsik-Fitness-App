package com.yeonsik.fitnessapp.feature.records.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.body.api.BodyMetricsReadApi
import com.yeonsik.fitnessapp.feature.meal.api.MealReadApi
import com.yeonsik.fitnessapp.feature.records.api.RecordsReadApi
import com.yeonsik.fitnessapp.feature.records.model.RecordsCalendarDay
import com.yeonsik.fitnessapp.feature.records.model.RecordsDayDetail
import com.yeonsik.fitnessapp.feature.records.model.RecordsSnapshot
import com.yeonsik.fitnessapp.feature.records.model.RecordsWeightPoint
import com.yeonsik.fitnessapp.feature.records.model.RecordsWorkoutSummary
import com.yeonsik.fitnessapp.feature.workout.api.WorkoutReadApi
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
        selectedDate: String,
        today: String
    ): RecordsSnapshot {
        require(scope.ownerId.isNotBlank()) { "Records owner is required." }
        val month = YearMonth.parse(displayedMonth.trim())
        val selected = LocalDate.parse(selectedDate.trim())
        val currentDay = LocalDate.parse(today.trim())
        val first = month.atDay(1)
        val last = month.atEndOfMonth()
        val calendarStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val calendarEnd = calendarStart.plusDays(CALENDAR_CELL_COUNT - 1L)
        val calendarStartText = calendarStart.toString()
        val calendarEndText = calendarEnd.toString()

        val workoutDates = workouts.completedDates(
            scope, calendarStartText, calendarEndText
        ).toSet()
        val bodyDates = body.dates(scope, calendarStartText, calendarEndText).toSet()
        val mealDates = meals.dates(scope, calendarStartText, calendarEndText).toSet()
        val sessions = workouts.completedSessionSummaries(
            scope, calendarStartText, calendarEndText
        )
        val musclesByDate = sessions
            .groupBy { it.date }
            .mapValues { (_, rows) ->
                rows.asSequence()
                    .flatMap { it.muscleLabels.asSequence() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .toList()
            }
        val calendarDays = (0 until CALENDAR_CELL_COUNT).map { offset ->
            val date = calendarStart.plusDays(offset.toLong()).toString()
            RecordsCalendarDay(
                date = date,
                hasWorkout = date in workoutDates,
                hasBodyMetric = date in bodyDates,
                hasMeal = date in mealDates,
                muscleLabels = musclesByDate[date].orEmpty()
            )
        }

        val selectedText = selected.toString()
        val selectedWorkouts = sessions
            .asSequence()
            .filter { it.date == selectedText }
            .map { session ->
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
            }
            .toList()
        val selectedDetail = RecordsDayDetail(
            date = selectedText,
            workouts = selectedWorkouts,
            bodyMetrics = body.bodyMetrics(scope, selectedText),
            meals = meals.meals(scope, selectedText)
        )

        val weightTrend = body.weightEntries(scope, first.toString(), last.toString())
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
            selectedDate = selectedText,
            today = currentDay.toString(),
            calendarDays = calendarDays,
            selectedDay = selectedDetail,
            weightTrend = weightTrend
        )
    }

    private companion object {
        const val CALENDAR_CELL_COUNT = 42
    }
}
