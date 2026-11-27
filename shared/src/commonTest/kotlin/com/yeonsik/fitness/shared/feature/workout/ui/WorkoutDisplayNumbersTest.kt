package com.yeonsik.fitness.shared.feature.workout.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutDisplayNumbersTest {
    @Test fun completedVolumeUsesTheSelectedUnitAndAndroidPrecision() {
        assertEquals("1,000lb", WorkoutDisplayNumbers.withUnit(453.59237, MassUnit.LB))
        assertEquals("453.6kg", WorkoutDisplayNumbers.withUnit(453.59237, MassUnit.KG))
        assertEquals("45.36", WorkoutDisplayNumbers.decimal(45.359237, grouped = false, digits = 2))
        assertEquals("0", WorkoutDisplayNumbers.decimal(-0.00000001))
        assertEquals("—", WorkoutDisplayNumbers.decimal(Double.NaN))
        assertEquals("-30.8%", WorkoutDisplayNumbers.signedPercent(-30.769))
    }
}
