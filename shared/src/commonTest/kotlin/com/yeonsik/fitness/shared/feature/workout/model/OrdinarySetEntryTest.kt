package com.yeonsik.fitness.shared.feature.workout.model

import kotlin.test.*

class OrdinarySetEntryTest {
    private fun read(weight: String, reps: String = "10", rir: String = "2") =
        OrdinarySetRules.read(weight, reps, rir, MassUnit.LB, "", null, null)

    @Test fun recordsInputAndCanonicalLoad() {
        val first = assertNotNull(read("95", "8", "3"))
        assertEquals(43.09127515, first.weightKg!!, 1e-8)
        val second = assertNotNull(read("100"))
        assertEquals(OrdinarySetEntry(100.0, MassUnit.LB, 45.359237, 10, 2), second)
        assertEquals(second, OrdinarySetRules.read("100", "10", "2", MassUnit.LB, "100", 100.0, second.weightKg))
    }

    @Test fun editsRepsWithoutRoundingImportedValues() {
        val raw = 1234.5678
        val kg = MassUnit.LB.toKg(raw)
        val result = OrdinarySetRules.read("1,234.6", "8", "", MassUnit.LB, "1,234.6", raw, kg)
        assertEquals(OrdinarySetEntry(raw, MassUnit.LB, kg, 8, null), result)
        val legacy = OrdinarySetRules.read("100", "8", "", MassUnit.LB, "100", null, 45.359237)
        assertEquals(OrdinarySetEntry(null, MassUnit.LB, 45.359237, 8, null), legacy)
        assertEquals(1.2, OrdinarySetRules.read("1.2", "", "", MassUnit.KG, "1.3", 1.29, 1.29)?.weightKg)
    }

    @Test fun distinguishesZeroEmptyAndInvalidCells() {
        assertEquals(OrdinarySetEntry(null, MassUnit.LB, null, null, null), read("", "", ""))
        assertEquals(OrdinarySetEntry(0.0, MassUnit.LB, 0.0, 0, 0), read("0", "0", "0"))
        for (bad in listOf("-2", "NaN", "Infinity", "foo")) assertNull(read(bad))
        assertNull(read("20", "2.5"))
        assertNull(read("20", "2", "-1"))
    }

    @Test fun protectsFirstAndOnlyRowIncludingSparseImports() {
        assertFalse(OrdinarySetRules.canRemove(1, 7, true))
        assertFalse(OrdinarySetRules.canRemove(2, 3, true))
        assertFalse(OrdinarySetRules.canRemove(2, 1, false))
        assertTrue(OrdinarySetRules.canRemove(2, 8, false))
    }
}
