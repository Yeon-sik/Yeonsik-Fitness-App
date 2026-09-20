package com.yeonsik.fitness.shared.feature.workout.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.routine.model.RoutineExerciseInstance
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseDetail
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutExerciseReplacement
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSetInput
import com.yeonsik.fitness.shared.feature.workout.model.WorkoutSessionSnapshot

/**
 * Public workout boundary for new Kotlin application code.  It deliberately
 * exposes neither SQLite types nor legacy repository model objects.
 */
interface WorkoutRepositoryApi {
    fun latestInProgressSession(scope: AccountScope): String? = null

    /**
     * Read-only recent-use projection for exercise pickers.
     *
     * The map is derived from completed workout records and keyed by the same canonical preset
     * identity used by the runtime exercise catalog. No separate recent-use state is persisted.
     */
    fun lastPerformedAtByCanonicalPreset(scope: AccountScope): Map<String, String> = emptyMap()

    fun createEmptySession(scope: AccountScope, date: String): String = error("Session creation is not supported by this repository.")

    fun createSessionFromRoutine(scope: AccountScope, date: String, title: String,
                                 routineId: String?, exercises: List<RoutineExerciseInstance>): String = error("Routine session creation is not supported by this repository.")

    fun createManualPastSessionFromRoutine(scope: AccountScope, date: String, title: String,
                                            routineId: String?, exercises: List<RoutineExerciseInstance>,
                                            startedAt: String, endedAt: String): String = error("Past session creation is not supported by this repository.")

    fun deleteSession(scope: AccountScope, recordId: String): Boolean = false

    /** Creates the shared workout record and its cardio exercise shell. */
    fun createCardioSession(
        scope: AccountScope,
        date: String,
        activityId: String,
        activityLabel: String
    ): String = error("Cardio session creation is not supported by this repository.")

    /** Writes the shared workout record and cardio set completion summary. */
    fun completeCardioSession(
        scope: AccountScope,
        recordId: String,
        activityId: String,
        activityLabel: String,
        durationSeconds: Int,
        distanceMeters: Double,
        averageHeartRateBpm: Int?
    ): Boolean = false

    fun updateCardioAverageHeartRate(
        scope: AccountScope,
        recordId: String,
        averageHeartRateBpm: Int?
    ): Boolean = false

    fun loadSession(scope: AccountScope, recordId: String): WorkoutSessionSnapshot?

    fun loadExerciseDetail(
        scope: AccountScope,
        recordId: String,
        activeExerciseId: String?
    ): WorkoutExerciseDetail?

    /** Explicit entry initialization; never call this while rendering a view. */
    fun ensureInitialSet(scope: AccountScope, recordId: String, exerciseId: String): Boolean

    /** Completes only a session that has a persisted completed set. */
    fun completeIfEligible(scope: AccountScope, recordId: String): WorkoutCompletion

    /** Deletes an empty in-progress session using the existing soft-delete contract. */
    fun discard(scope: AccountScope, recordId: String)

    fun updateTypedSet(scope: AccountScope, recordId: String, setId: String, input: WorkoutSetInput): Boolean
    fun addTypedSet(scope: AccountScope, recordId: String, exerciseId: String, setIndex: Int,
                    input: WorkoutSetInput): Boolean
    fun deleteSet(scope: AccountScope, recordId: String, setId: String): Boolean
    fun deleteExercise(scope: AccountScope, recordId: String, exerciseId: String): Boolean
    fun addExercise(scope: AccountScope, recordId: String,
                    exercise: WorkoutExerciseReplacement): Boolean
    fun replaceExercise(scope: AccountScope, recordId: String, exerciseId: String,
                        replacement: WorkoutExerciseReplacement): Boolean
}

enum class WorkoutCompletion {
    COMPLETED,
    NO_COMPLETED_SETS
}
