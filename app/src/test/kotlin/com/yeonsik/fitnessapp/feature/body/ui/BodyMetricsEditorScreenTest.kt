package com.yeonsik.fitnessapp.feature.body.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BodyMetricsEditorScreenTest {
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
