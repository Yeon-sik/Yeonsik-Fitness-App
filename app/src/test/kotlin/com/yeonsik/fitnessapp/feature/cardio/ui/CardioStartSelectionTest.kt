package com.yeonsik.fitnessapp.feature.cardio.ui

import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioEnvironment
import org.junit.Assert.*
import org.junit.Test

class CardioStartSelectionTest {
    @Test fun latestUsageSortsFirstAndUnusedActivitiesRemainAlphabetical() {
        val state = CardioStartUiState(loading = false, lastStartedAt = mapOf("cycling" to 10L, "rowing" to 20L))
        assertEquals(listOf("rowing", "cycling", "walking", "running", "stair_stepper", "elliptical"),
            state.activities.map { it.id })
        assertEquals(CardioActivityType.entries.sortedBy { it.labelKo }, CardioStartUiState().activities)
    }
    @Test fun searchFindsKoreanAliasesAndCaseInsensitiveIdsWithoutChangingSelection() {
        val state = CardioStartUiState(loading = false).select(CardioActivityType.RUNNING)
            .copy(environment = CardioEnvironment.INDOOR)
        assertEquals(listOf(CardioActivityType.STAIR_STEPPER), state.copy(query = "계단").activities)
        assertEquals(listOf(CardioActivityType.CYCLING), state.copy(query = " CYCLE ").activities)
        assertEquals(CardioActivityType.RUNNING, state.copy(query = "없음").selectedActivity)
        assertTrue(state.copy(query = "없음").activities.isEmpty())
    }
    @Test fun gpsActivitiesNeedAnExplicitEnvironmentWhileEquipmentHasAFixedIndoorEnvironment() {
        val initial = CardioStartUiState(loading = false)
        assertFalse(initial.canStart)
        for (type in CardioActivityType.entries) {
            val selected = initial.select(type)
            assertEquals(!type.supportsOutdoorGps, selected.canStart)
            assertTrue(selected.copy(environment = CardioEnvironment.INDOOR).canStart)
            assertEquals(type.supportsOutdoorGps, selected.copy(environment = CardioEnvironment.OUTDOOR).canStart)
            assertFalse(type.usesGps(CardioEnvironment.INDOOR))
        }
        assertFalse(initial.select(CardioActivityType.RUNNING).copy(loading = true,
            environment = CardioEnvironment.OUTDOOR).canStart)
    }
    @Test fun optionalEquipmentDistanceKeepsMissingSeparateFromZeroAndRejectsNonFiniteValues() {
        assertNull(CardioManualDistanceInput.parseKilometers(" "))
        assertEquals(1250.0, CardioManualDistanceInput.parseKilometers(" 1.25 ")!!, 0.0)
        assertEquals(1500.0, CardioManualDistanceInput.parseKilometers("1,5")!!, 0.0)
        for (input in listOf("0", "-2", "NaN", "Infinity", "1e309", "abc")) {
            assertThrows(IllegalArgumentException::class.java) { CardioManualDistanceInput.parseKilometers(input) }
        }
    }
}
