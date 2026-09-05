package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.exercise.LoadState;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class WorkoutExerciseDetailScreenTest {
    @Test
    public void sumsAllEnteredSetVolumesBeforeComparing() {
        assertEquals(
                700d,
                WorkoutExerciseDetailScreen.sumVolumeKg(Arrays.asList(200d, 500d)),
                0.001d
        );
    }

    @Test
    public void comparisonMessageUsesTheWholeSetDelta() {
        assertEquals(
                "전체 세트 기준, 지난 운동보다 120 KG 덜 들었어요",
                WorkoutExerciseDetailScreen.totalVolumeComparisonMessage(700d, 820d)
        );
    }

    @Test
    public void comparisonMessageHandlesEqualVolumeWithoutAFalseDirection() {
        assertEquals(
                "전체 세트 기준, 지난 운동과 같은 볼륨이에요",
                WorkoutExerciseDetailScreen.totalVolumeComparisonMessage(820d, 820d)
        );
    }

    @Test
    public void bodyweightPullUpUsesRepInputAndOmitsUnusedSecondaryInput() {
        String recordType = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;

        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.BODYWEIGHT)
        );
        assertEquals(
                "",
                WorkoutExerciseDetailScreen.secondaryInputLabel(recordType, LoadState.BODYWEIGHT)
        );
        assertFalse(WorkoutExerciseDetailScreen.hasSecondaryInput(recordType, LoadState.BODYWEIGHT));
    }

    @Test
    public void addedWeightPullUpUsesAddedWeightAndRepInputs() {
        String recordType = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;

        assertEquals(
                "추가 kg",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.ADDED_WEIGHT)
        );
        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.secondaryInputLabel(recordType, LoadState.ADDED_WEIGHT)
        );
        assertTrue(WorkoutExerciseDetailScreen.hasSecondaryInput(recordType, LoadState.ADDED_WEIGHT));
    }

    @Test
    public void bodyweightAndAddedWeightTransitionKeepsInputMeaningInEachColumn() {
        String recordType = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;

        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.BODYWEIGHT)
        );
        assertEquals(
                "추가 kg",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.ADDED_WEIGHT)
        );
        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.secondaryInputLabel(recordType, LoadState.ADDED_WEIGHT)
        );
    }

    @Test
    public void dipAndPushUpUseTheSameStateAwareInputRules() {
        String recordType = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;

        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.BODYWEIGHT)
        );
        assertEquals(
                "추가 kg",
                WorkoutExerciseDetailScreen.primaryInputLabel(recordType, LoadState.ADDED_WEIGHT)
        );
        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(
                        FitnessRecordContract.REPS_ONLY,
                        LoadState.BODYWEIGHT
                )
        );
        assertFalse(WorkoutExerciseDetailScreen.hasSecondaryInput(
                FitnessRecordContract.REPS_ONLY,
                LoadState.BODYWEIGHT
        ));
    }

    @Test
    public void assistedExternalBandAndTimeInputsKeepTheirExistingSemantics() {
        String repsRecordType = FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS;
        assertEquals(
                "보조 kg",
                WorkoutExerciseDetailScreen.primaryInputLabel(repsRecordType, LoadState.ASSISTED)
        );
        assertEquals(
                "중량 kg",
                WorkoutExerciseDetailScreen.primaryInputLabel(repsRecordType, LoadState.EXTERNAL_LOAD)
        );
        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(repsRecordType, LoadState.BAND_ASSISTED)
        );
        assertEquals(
                "횟수",
                WorkoutExerciseDetailScreen.primaryInputLabel(repsRecordType, LoadState.BAND_RESISTED)
        );
        assertFalse(WorkoutExerciseDetailScreen.hasSecondaryInput(
                repsRecordType,
                LoadState.BAND_ASSISTED
        ));
        assertFalse(WorkoutExerciseDetailScreen.hasSecondaryInput(
                repsRecordType,
                LoadState.BAND_RESISTED
        ));

        assertEquals(
                "초",
                WorkoutExerciseDetailScreen.primaryInputLabel(
                        FitnessRecordContract.TIME,
                        LoadState.BODYWEIGHT
                )
        );
        assertEquals(
                "초",
                WorkoutExerciseDetailScreen.secondaryInputLabel(
                        FitnessRecordContract.WEIGHT_TIME,
                        LoadState.EXTERNAL_LOAD
                )
        );
    }

    @Test
    public void numericHeadersDoNotUseTheGlobalMassUnit() {
        assertEquals(
                "중량",
                WorkoutExerciseDetailScreen.primaryColumnHeaderLabel(
                        FitnessRecordContract.WEIGHT_REPS,
                        LoadState.EXTERNAL_LOAD
                )
        );
        assertEquals(
                "추가",
                WorkoutExerciseDetailScreen.primaryColumnHeaderLabel(
                        FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS,
                        LoadState.ADDED_WEIGHT
                )
        );
        assertEquals(
                "보조",
                WorkoutExerciseDetailScreen.primaryColumnHeaderLabel(
                        FitnessRecordContract.ASSISTED_WEIGHT_REPS,
                        LoadState.ASSISTED
                )
        );
    }
}
