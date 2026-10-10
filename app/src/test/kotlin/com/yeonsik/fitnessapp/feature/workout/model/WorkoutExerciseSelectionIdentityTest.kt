package com.yeonsik.fitnessapp.feature.workout.model

import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import org.junit.Assert.*
import org.junit.Test

class WorkoutExerciseSelectionIdentityTest {
    private fun identity(id: String, variant: String, load: String = "external_load") = ExerciseFamilyIdentity(
        id, "bench_press", id, id, id, null, null, null, "chest", variant, variant,
        null, load, "weight_reps", null
    )

    @Test fun aliasesForOneCanonicalVariantHaveOneSelectionIdentity() {
        val canonical = WorkoutExerciseSelectionIdentity.from("canonical", "정식명", "weight_reps",
            identity("canonical", "incline-smith"))
        val alias = WorkoutExerciseSelectionIdentity.from("legacy-alias", "이전 이름", "weight_reps",
            identity("legacy-alias", "incline-smith"))
        assertEquals(canonical, alias)
    }

    @Test fun differentVariantsOfTheSameFamilyRemainSelectable() {
        assertNotEquals(
            WorkoutExerciseSelectionIdentity.from("incline", "인클라인", "weight_reps", identity("incline", "incline-smith")),
            WorkoutExerciseSelectionIdentity.from("flat", "플랫", "weight_reps", identity("flat", "flat-smith"))
        )
    }

    @Test fun setLoadStateDoesNotCreateAnotherExerciseIdentity() {
        assertEquals(
            WorkoutExerciseSelectionIdentity.from("preset", "운동", "weight_reps", identity("preset", "variant")),
            WorkoutExerciseSelectionIdentity.from("preset", "운동", "weight_reps", identity("preset", "variant", "band_resisted"))
        )
    }

    @Test fun manualNamesAreTrimmedAndDifferentNamesAndRecordTypesStaySeparate() {
        val first = WorkoutExerciseSelectionIdentity.from("manual", "수동 운동", "weight_reps", null)
        assertEquals(first, WorkoutExerciseSelectionIdentity.from("manual", "  수동 운동  ", "weight_reps", null))
        assertNotEquals(first, WorkoutExerciseSelectionIdentity.from("manual", "다른 운동", "weight_reps", null))
        assertNotEquals(first, WorkoutExerciseSelectionIdentity.from("manual", "수동 운동", "time", null))
    }

    @Test fun unresolvedLegacyExercisesUseTheirIdWithoutGuessingFromNames() {
        val first = WorkoutExerciseSelectionIdentity.from("legacy-a", "동일 이름", "weight_reps", null)
        assertEquals(first, WorkoutExerciseSelectionIdentity.from("legacy-a", "다른 표시 이름", "weight_reps", null))
        assertNotEquals(first, WorkoutExerciseSelectionIdentity.from("legacy-b", "동일 이름", "weight_reps", null))
    }
}
