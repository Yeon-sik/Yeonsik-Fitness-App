package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.feature.meal.model.PhotoEstimateMeal
import org.junit.Assert.*
import org.junit.Test

class PhotoEstimateDraftTest {
    @Test fun caloriesOnlyHasNoMenuRequirementAndKeepsMacrosUnknown() {
        val draft = PhotoEstimateDraft(calories = " 2400 ", time = "12:30")
        assertNull(draft.error())
        assertEquals(2400.0, draft.input().calories, 0.0)
        assertNull(draft.input().proteinGrams)
        assertNull(draft.input().carbsGrams)
        assertNull(draft.input().fatGrams)
        assertEquals("20261021", PhotoEstimateMeal.title("2026-10-21"))
    }

    @Test fun explicitZeroIsDifferentFromMissingAndInvalidInputsCannotSave() {
        assertEquals(0.0, PhotoEstimateDraft("0", "12:30", protein = "0").input().proteinGrams!!, 0.0)
        listOf("", "NaN", "Infinity", "-1", "2147483648", "2400 kcal").forEach {
            assertNotNull(it, PhotoEstimateDraft(it, "12:30").error())
        }
        assertNotNull(PhotoEstimateDraft("2400", "25:00").error())
        assertNotNull(PhotoEstimateDraft("2400", "12:30", fat = "NaN").error())
    }

    @Test fun provenanceRestoresUnknownProteinWithoutChangingLegacyRecords() {
        val metadata = """{"recording_method":"photo_estimate","protein_known":false}"""
        assertTrue(PhotoEstimateMeal.isPhotoEstimate(metadata))
        assertNull(PhotoEstimateMeal.recordedProtein(metadata, 0.0))
        assertEquals(0.0, PhotoEstimateMeal.recordedProtein(metadata.replace("false", "true"), 0.0)!!, 0.0)
        assertEquals(0.0, PhotoEstimateMeal.recordedProtein("{}", 0.0)!!, 0.0)
        assertFalse(PhotoEstimateMeal.isPhotoEstimate("bad-json"))
    }
}
