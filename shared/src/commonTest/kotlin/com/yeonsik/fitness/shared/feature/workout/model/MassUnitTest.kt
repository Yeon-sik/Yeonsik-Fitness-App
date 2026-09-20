package com.yeonsik.fitness.shared.feature.workout.model

import kotlin.test.Test
import kotlin.test.assertEquals

class MassUnitTest {
    @Test
    fun usesTheExactPoundToKilogramConversion() {
        assertEquals(0.45359237, MassUnit.KILOGRAMS_PER_POUND, 0.0)
        assertEquals(102.05828325, MassUnit.toKg(225.0, MassUnit.LB), 1e-12)
        assertEquals(225.0, MassUnit.fromKg(102.05828325, MassUnit.LB), 1e-12)
    }

    @Test
    fun convertsAndRoundTripsWithoutChangingThePhysicalLoad() {
        val pounds = 187.5
        val kilograms = MassUnit.toKg(pounds, MassUnit.LB)

        assertEquals(kilograms, MassUnit.convert(pounds, MassUnit.LB, MassUnit.KG), 0.0)
        assertEquals(pounds, MassUnit.convert(kilograms, MassUnit.KG, MassUnit.LB), 1e-12)
    }
}
