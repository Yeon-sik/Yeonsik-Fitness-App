package com.yeonsik.fitness.shared.feature.workout.ui

import java.util.Locale

internal actual fun fixedWorkoutDecimal(value: Double, digits: Int, grouped: Boolean): String =
    String.format(Locale.ROOT, if (grouped) "%,.${digits}f" else "%.${digits}f", value)
