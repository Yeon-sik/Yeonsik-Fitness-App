package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.exercise.LoadState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LoadStateContractTest {
    @Test
    public void exposesExactlyTheSixContractStates() {
        assertEquals(6, LoadState.values().length);
        assertEquals("bodyweight", LoadState.BODYWEIGHT.id());
        assertEquals("external_load", LoadState.EXTERNAL_LOAD.id());
        assertEquals("added_weight", LoadState.ADDED_WEIGHT.id());
        assertEquals("assisted", LoadState.ASSISTED.id());
        assertEquals("band_assisted", LoadState.BAND_ASSISTED.id());
        assertEquals("band_resisted", LoadState.BAND_RESISTED.id());
    }

    @Test
    public void keepsBandStatesFreeOfSyntheticKgSemantics() {
        assertTrue(LoadState.BAND_ASSISTED.isBandSemantics());
        assertTrue(LoadState.BAND_RESISTED.isBandSemantics());
    }

    @Test(expected = IllegalArgumentException.class)
    public void completedExternalLoadStillRequiresPositiveLoad() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.WEIGHT_REPS,
                new FitnessRepository.SetInput(
                        null,
                        8,
                        null,
                        null,
                        null,
                        null,
                        null,
                        true,
                        LoadState.EXTERNAL_LOAD
                )
        );
    }

    @Test
    public void legacyRecordValidationRemainsAvailable() {
        FitnessRepository.validateSetInputForRecordType(
                FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS,
                new FitnessRepository.SetInput(
                        null,
                        8,
                        null,
                        null,
                        0d,
                        null,
                        null,
                        true
                )
        );
    }
}
