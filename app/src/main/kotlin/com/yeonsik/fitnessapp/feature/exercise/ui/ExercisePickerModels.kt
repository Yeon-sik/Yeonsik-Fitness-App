package com.yeonsik.fitnessapp.feature.exercise.ui

import com.yeonsik.fitnessapp.state.FitnessScreen

/** The picker target is explicit even though the existing navigation route is shared. */
enum class ExercisePickerSelectionMode {
    ROUTINE_ADD,
    WORKOUT_ADD,
    WORKOUT_REPLACE;

    companion object {
        fun forTarget(screen: FitnessScreen, replacementId: String?): ExercisePickerSelectionMode =
            when (screen) {
                FitnessScreen.ROUTINE_ADD -> ROUTINE_ADD
                FitnessScreen.WORKOUT_EXERCISE_ADD ->
                    if (replacementId.isNullOrBlank()) WORKOUT_ADD else WORKOUT_REPLACE
                else -> error("Exercise picker cannot be entered from $screen")
            }
    }
}

data class ExercisePickerSubPartOption(
    val id: String,
    val label: String
)

/** Immutable request identity used to reject stale account, target, and mode results. */
internal data class ExercisePickerRequestKey(
    val ownerId: String,
    val screen: FitnessScreen,
    val selectionMode: ExercisePickerSelectionMode,
    val recordId: String?,
    val replacementId: String?,
    val routineId: String?
)

/** Small, pure request gate; asynchronous owners can share the same stale-result rule. */
internal class ExercisePickerRequestGate {
    private var generation = 0L
    private var current: ExercisePickerRequestKey? = null

    @Synchronized
    fun begin(key: ExercisePickerRequestKey): Long {
        generation += 1L
        current = key
        return generation
    }

    @Synchronized
    fun accepts(token: Long, key: ExercisePickerRequestKey): Boolean =
        token == generation && current == key
}
