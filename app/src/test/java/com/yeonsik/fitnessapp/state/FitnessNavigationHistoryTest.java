package com.yeonsik.fitnessapp.state;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class FitnessNavigationHistoryTest {
    @Test
    public void backUnwindsPushedScreensAndStopsAtHome() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);

        assertTrue(history.canBack());
        assertEquals(FitnessScreen.WORKOUT, history.back());
        assertEquals(FitnessScreen.HOME, history.back());
        assertNull(history.back());
        assertFalse(history.canBack());
    }

    @Test
    public void replaceRemovesTheSessionEntryBeforeSummary() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);
        history.push(FitnessScreen.WORKOUT_SESSION);
        history.replace(FitnessScreen.WORKOUT_SUMMARY);

        assertEquals(FitnessScreen.WORKOUT_SUMMARY, history.current());
        assertEquals(FitnessScreen.STRENGTH, history.back());
    }

    @Test
    public void strengthSessionBackReturnsToStrengthThenWorkout() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);
        history.push(FitnessScreen.WORKOUT_SESSION);

        assertEquals(FitnessScreen.STRENGTH, history.back());
        assertEquals(FitnessScreen.WORKOUT, history.back());
    }

    @Test
    public void pickerBackPopsAddScreenSoSystemBackCannotReenterIt() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);
        history.push(FitnessScreen.WORKOUT_SESSION);
        history.push(FitnessScreen.WORKOUT_EXERCISE_ADD);

        // UI back and Android system back both use the same pop operation.
        assertEquals(FitnessScreen.WORKOUT_SESSION, history.back());
        assertEquals(FitnessScreen.STRENGTH, history.back());
    }

    @Test
    public void detailBackReturnsToSession() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);
        history.push(FitnessScreen.WORKOUT_SESSION);
        history.push(FitnessScreen.WORKOUT_EXERCISE_DETAIL);

        assertEquals(FitnessScreen.WORKOUT_SESSION, history.back());
    }

    @Test
    public void summaryReplacementReturnsToStrength() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        history.push(FitnessScreen.STRENGTH);
        history.push(FitnessScreen.WORKOUT_SESSION);
        history.replace(FitnessScreen.WORKOUT_SUMMARY);

        assertEquals(FitnessScreen.STRENGTH, history.back());
    }

    @Test
    public void pushAndReplaceDoNotCreateConsecutiveDuplicateEntries() {
        FitnessNavigationHistory history = new FitnessNavigationHistory(FitnessScreen.HOME);
        history.push(FitnessScreen.WORKOUT);
        int afterFirstPush = history.size();
        history.push(FitnessScreen.WORKOUT);
        assertEquals(afterFirstPush, history.size());

        history.replace(FitnessScreen.STRENGTH);
        int afterFirstReplace = history.size();
        history.replace(FitnessScreen.STRENGTH);
        assertEquals(afterFirstReplace, history.size());
    }
}
