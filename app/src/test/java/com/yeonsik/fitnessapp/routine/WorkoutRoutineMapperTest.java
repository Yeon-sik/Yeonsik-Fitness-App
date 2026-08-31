package com.yeonsik.fitnessapp.routine;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;
import com.yeonsik.fitnessapp.exercise.LoadState;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WorkoutRoutineMapperTest {
    @Test
    public void keepsCompletedExercisesInSessionOrderAndDoesNotCopySets() throws Exception {
        RuntimeExerciseCatalog catalog = RuntimeExerciseCatalog.empty();
        ExerciseFamilyIdentity canonicalIdentity = new ExerciseFamilyIdentity(
                "legacy_dumbbell", "family", "canonical_dumbbell", "canonical_dumbbell",
                "덤벨 운동", "Dumbbell Exercise", "덤벨 운동", "Dumbbell Exercise",
                "chest", "variant", "variant", null, "external_load",
                FitnessRecordContract.WEIGHT_REPS, null
        );
        FitnessRepository.SessionExerciseEntry canonical = new FitnessRepository.SessionExerciseEntry(
                "workout-1", "legacy_dumbbell", 1, "덤벨 운동", "가슴", "덤벨",
                FitnessRecordContract.WEIGHT_REPS, canonicalIdentity
        );
        FitnessRepository.SessionExerciseEntry manual = new FitnessRepository.SessionExerciseEntry(
                "workout-2", "manual", 2, "사용자 종목", "등", "덤벨",
                FitnessRecordContract.WEIGHT_REPS
        );
        FitnessRepository.SessionExerciseEntry incomplete = new FitnessRepository.SessionExerciseEntry(
                "workout-3", "legacy_dumbbell", 3, "미완료", "가슴", "덤벨",
                FitnessRecordContract.WEIGHT_REPS
        );
        Map<String, List<FitnessRepository.SessionSetEntry>> sets = new LinkedHashMap<>();
        sets.put(canonical.id, Collections.singletonList(set("set-1", true)));
        sets.put(manual.id, Collections.singletonList(set("set-2", true)));
        sets.put(incomplete.id, Collections.singletonList(set("set-3", false)));

        List<RoutineExercise> result = WorkoutRoutineMapper.mapCompletedExercises(
                Arrays.asList(canonical, manual, incomplete),
                sets,
                catalog
        );

        assertEquals(2, result.size());
        assertEquals("legacy_dumbbell", result.get(0).masterExerciseId);
        assertEquals("family", result.get(0).familyIdentity.familyId);
        assertEquals("사용자 종목", result.get(1).nameKo);
        assertNull(result.get(1).familyIdentity);
        assertEquals(FitnessRecordContract.WEIGHT_REPS, result.get(1).recordType);
    }

    private static FitnessRepository.SessionSetEntry set(String id, boolean completed) {
        return new FitnessRepository.SessionSetEntry(
                id, 1, 10d, 8, null, null, completed, 0, 0d, 0d, 0d,
                LoadState.EXTERNAL_LOAD
        );
    }

}
