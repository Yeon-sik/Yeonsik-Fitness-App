package com.yeonsik.fitness.shared.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlin.math.abs

/** Both hosts display stored kilograms with the same grouping, precision and unit suffix. */
object WorkoutDisplayNumbers {
    fun fixed(value: Double, digits: Int): String = fixedWorkoutDecimal(value, digits, false)

    fun decimal(value: Double, grouped: Boolean = true, digits: Int = 1): String {
        if (!value.isFinite()) return "—"
        val text = fixedWorkoutDecimal(if (abs(value) < 0.0000001) 0.0 else value, digits, grouped)
        return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
    }

    fun mass(kilograms: Double, unit: MassUnit): String = decimal(unit.fromKg(kilograms))

    fun withUnit(kilograms: Double, unit: MassUnit): String = mass(kilograms, unit) + unit.symbol()

    fun signedPercent(value: Double): String =
        (if (value < 0) "-" else "+") + fixed(abs(value), 1) + "%"
}

internal expect fun fixedWorkoutDecimal(value: Double, digits: Int, grouped: Boolean): String
