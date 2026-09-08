package com.yeonsik.fitnessapp.feature.home.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.api.HomeRepositoryApi
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import java.time.DayOfWeek
import java.time.LocalDate

/** Composes feature read ports into the Home snapshot; it never writes through another feature. */
class HomeReadRepository(
    private val reads: HomeReadSources,
    private val routines: RoutineRepositoryApi
) : HomeRepositoryApi {
    override fun load(scope: AccountScope, today: String): HomeSnapshot {
        val todayDate = LocalDate.parse(today)
        val currentWeek = todayDate.with(DayOfWeek.MONDAY)
        val dates = (0..13).map { currentWeek.minusWeeks(1).plusDays(it.toLong()) }
        val dayMetrics = dates.associate { date -> date.toString() to reads.dayMetrics(scope, date.toString()) }
        val mealCounts = dates.associate { date -> date.toString() to reads.mealCount(scope, date.toString()) }
        val nutritionTotals = dates.associate { date -> date.toString() to reads.mealTotals(scope, date.toString()) }
        val routineRows = routines.routines(scope)
        val routineExercises = routineRows.associate { routine ->
            routine.id to routines.routineExercises(scope, routine.id)
        }
        val latestRoutineDates = routineRows.associate { routine ->
            routine.id to reads.latestRoutineDate(scope, routine.id, routine.name)
        }
        return HomeSnapshot(
            scope.ownerId,
            today,
            reads.sessionsForDate(scope, today),
            routines.activeRoutineId(scope),
            routineRows,
            routineExercises,
            latestRoutineDates,
            reads.latestInProgress(scope),
            dayMetrics,
            mealCounts,
            nutritionTotals,
            reads.nutritionGoal(scope),
            reads.bodyMetric(scope, today),
            reads.bodyMetrics(scope, today),
            reads.meals(scope, today)
        )
    }

}
