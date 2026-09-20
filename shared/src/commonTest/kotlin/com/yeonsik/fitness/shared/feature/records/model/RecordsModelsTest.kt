package com.yeonsik.fitness.shared.feature.records.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordsModelsTest {
    @Test
    fun calendarAndDayDetailRemainReadCompositions() {
        assertFalse(RecordsCalendarDay("2026-09-20", false, false, false).hasAnyRecord)
        assertTrue(RecordsCalendarDay("2026-09-20", false, false, true).hasAnyRecord)

        assertFalse(
            RecordsDayDetail("2026-09-20", emptyList(), emptyList(), emptyList()).hasAnyRecord
        )
        assertTrue(
            RecordsDayDetail(
                "2026-09-20",
                listOf(RecordsWorkoutSummary("id", "2026-09-20", "운동", "strength", 0, 0.0, 0, emptyList())),
                emptyList(),
                emptyList()
            ).hasAnyRecord
        )
    }
}
