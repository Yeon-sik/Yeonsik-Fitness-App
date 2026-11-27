package com.yeonsik.fitness.shared.feature.body.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit

class BodyMetricsEditorScreenTest {
    @Test
    fun poundsAreConvertedOnceAndRoundedDisplayKeepsTheOriginalKilograms() {
        val kg = checkNotNull(bodyWeightKilograms("225", MassUnit.LB, "", null))
        assertEquals(102.05828325, kg, 1e-10)
        assertEquals(kg, bodyWeightKilograms("102.06", MassUnit.KG, "102.06", kg))
        assertEquals(103.0, bodyWeightKilograms("103", MassUnit.KG, "102.06", kg))
        assertNull(bodyWeightKilograms("NaN", MassUnit.LB, "", null))
    }

    @Test
    fun commaDecimalShowsInlineCorrection() {
        assertEquals(
            "소수점은 쉼표(,) 대신 마침표(.)를 사용해 주세요. 예: 90.9",
            bodyMetricsWeightErrorMessage("90,9", saveAttempted = false)
        )
    }

    @Test
    fun dotDecimalHasNoValidationError() {
        assertNull(bodyMetricsWeightErrorMessage("90.9", saveAttempted = false))
    }

    @Test
    fun emptyWeightIsOnlyMarkedAfterSaveAttempt() {
        assertNull(bodyMetricsWeightErrorMessage("", saveAttempted = false))
        assertEquals(
            "체중을 입력하세요.",
            bodyMetricsWeightErrorMessage("", saveAttempted = true)
        )
    }

    @Test
    fun calendarDateRoundTripsThroughUtcWithoutChangingLocalDate() {
        val selectedDate = "2026-09-30"
        val selectedDateMillis = checkNotNull(dateToUtcMillis(selectedDate))

        assertEquals(selectedDate, dateFromUtcMillis(selectedDateMillis))
    }
}
