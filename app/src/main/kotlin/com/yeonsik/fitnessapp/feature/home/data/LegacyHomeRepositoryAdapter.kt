package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.routine.RoutineRepository
import java.time.DayOfWeek
import java.time.LocalDate

class LegacyHomeRepositoryAdapter(
    private val fitness: FitnessRepository,
    private val routines: RoutineRepository
) : HomeRepositoryApi {
    override fun load(scope: AccountScope, today: String): HomeSnapshot {
        check(scope.ownerId == fitness.currentUserId()) {
            "The account changed while the home operation was pending."
        }
        val todayDate = LocalDate.parse(today)
        val currentWeek = todayDate.with(DayOfWeek.MONDAY)
        val previousWeek = currentWeek.minusWeeks(1)
        val dates = (0..13).map { currentWeek.minusWeeks(1).plusDays(it.toLong()) }
        val dayMetrics = dates.associate { date ->
            date.toString() to fitness.dayWorkoutMetrics(date.toString())
        }
        val mealCounts = dates.associate { date ->
            date.toString() to fitness.mealCountForDate(date.toString())
        }
        val nutritionTotals = dates.associate { date ->
            date.toString() to fitness.mealNutritionTotalsForDate(date.toString())
        }
        val routineRows = routines.routines()
        val routineExercises = routineRows.associate { routine ->
            routine.id to routines.routineExercises(routine.id)
        }
        val latestRoutineDates = routineRows.associate { routine ->
            routine.id to fitness.latestCompletedWorkoutDateForRoutine(routine.id, routine.name)
        }
        return HomeSnapshot(
            scope.ownerId,
            today,
            fitness.sessionsForDate(today),
            routines.activeRoutineId(),
            routineRows,
            routineExercises,
            latestRoutineDates,
            fitness.latestInProgressSessionId(),
            dayMetrics,
            mealCounts,
            nutritionTotals,
            fitness.nutritionGoal(),
            fitness.bodyMetricForDate(today),
            fitness.bodyMetricEntriesForDate(today),
            fitness.mealEntriesForDate(today)
        )
    }
}
