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
}
