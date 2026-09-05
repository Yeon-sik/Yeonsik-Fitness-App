package com.yeonsik.fitnessapp.state;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MassUnit;
import com.yeonsik.fitnessapp.exercise.LoadState;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WorkoutSessionStateTest {
    @Test
    public void sessionDefaultStartsFromPreferredUnit() {
        WorkoutSessionState state = new WorkoutSessionState();

        state.startSession(MassUnit.LB);

        assertEquals(MassUnit.LB, state.sessionInputMassUnit());
        assertEquals(
                MassUnit.LB,
                state.inputMassUnitForNewSet(null, MassUnit.KG)
        );
    }

    @Test
    public void previousSetProvenanceOverridesSessionDefault() {
        WorkoutSessionState state = new WorkoutSessionState();
        state.startSession(MassUnit.LB);
        FitnessRepository.SessionSetEntry previous = new FitnessRepository.SessionSetEntry(
                "set-1",
                1,
                MassUnit.toKg(60d, MassUnit.KG),
                8,
                null,
                90,
                false,
                0,
                0d,
                0d,
                0d,
                LoadState.EXTERNAL_LOAD,
                60d,
                MassUnit.KG
        );

        assertEquals(
                MassUnit.KG,
                state.inputMassUnitForNewSet(previous, MassUnit.LB)
        );
    }

    @Test
    public void clearingTheActiveSessionDropsTheTransientDefault() {
        WorkoutSessionState state = new WorkoutSessionState();
        state.startSession(MassUnit.LB);
        state.setActiveRecordId("record-1");

        state.clearIfMatches("record-1");

        assertNull(state.sessionInputMassUnit());
        assertEquals(
                MassUnit.KG,
                state.inputMassUnitForNewSet(null, MassUnit.KG)
        );
    }
}
