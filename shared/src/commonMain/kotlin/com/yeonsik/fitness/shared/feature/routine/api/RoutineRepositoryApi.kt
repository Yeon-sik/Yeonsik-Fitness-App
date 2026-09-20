package com.yeonsik.fitness.shared.feature.routine.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseDraft
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.routine.model.RoutineSummary

/** Feature boundary for account-scoped routine reads and writes. */
interface RoutineRepositoryApi {
    fun ensureActiveRoutine(scope: AccountScope): String

    fun routines(scope: AccountScope): List<RoutineSummary> = emptyList()

    fun routineExercises(scope: AccountScope, routineId: String): List<RoutineExerciseInstance> = emptyList()

    fun activeRoutineId(scope: AccountScope): String = ensureActiveRoutine(scope)

    fun routineName(scope: AccountScope, routineId: String): String? = null

    fun createRoutine(scope: AccountScope, name: String): String? = null

    fun renameRoutine(scope: AccountScope, routineId: String, name: String): Boolean = false

    fun copyRoutine(scope: AccountScope, routineId: String, name: String): String? = null

    fun deleteRoutine(scope: AccountScope, routineId: String): Boolean = false

    fun selectRoutine(scope: AccountScope, routineId: String): Boolean = false

    fun addExercise(scope: AccountScope, routineId: String, exercise: RoutineExerciseDraft): Boolean = false
}
