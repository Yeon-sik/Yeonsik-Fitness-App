package com.yeonsik.fitnessapp.feature.exercise.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseMuscleMapTest {
    private val groups = mapOf(
        "upper_chest" to listOf("pectoralis_major_clavicular"),
        "overall_chest" to listOf(
            "pectoralis_major_clavicular",
            "pectoralis_major_sternocostal_upper",
            "pectoralis_major_sternocostal_lower"
        )
    )

    @Test
    fun tappingLayerPrefersSpecificAvailableCatalogGroup() {
        assertEquals(
            "upper_chest",
            groupForMuscleLayer(
                "pectoralis_major_clavicular",
                groups,
                setOf("upper_chest", "overall_chest")
            )
        )
    }

    @Test
    fun unavailableOrUnmappedLayerCannotSelectAnExerciseFilter() {
        assertEquals(
            "overall_chest",
            groupForMuscleLayer(
                "pectoralis_major_clavicular",
                groups,
                setOf("overall_chest")
            )
        )
        assertNull(
            groupForMuscleLayer("achilles_tendon", groups, groups.keys)
        )
    }
}
