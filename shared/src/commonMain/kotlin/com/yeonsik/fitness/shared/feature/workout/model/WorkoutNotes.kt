package com.yeonsik.fitness.shared.feature.workout.model

const val WORKOUT_NOTE_MAX_LENGTH = 1000

/** Catalog facts used for input guidance, never to reinterpret historical values. */
data class WorkoutInputBasis(
    val laterality: String? = null,
    val implementMultiplier: Int? = null
)

data class WorkoutNotes(
    val setup: String = "",
    val condition: String = "",
    val setupCarriesForward: Boolean = false
)
