package com.yeonsik.fitnessapp.state;

import com.yeonsik.fitnessapp.data.MassUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class WorkoutSessionStateTest {
    @Test
    public void sessionDefaultStartsFromPreferredUnit() {
        WorkoutSessionState state = new WorkoutSessionState();
        state.startSession(MassUnit.LB);
        assertEquals(MassUnit.LB, state.sessionInputMassUnit());
        assertEquals(MassUnit.LB,
                state.inputMassUnitForNewSet(null, null, MassUnit.KG));
    }

    @Test
    public void previousSetProvenanceOverridesSessionDefault() {
        WorkoutSessionState state = new WorkoutSessionState();
        state.startSession(MassUnit.LB);
        assertEquals(MassUnit.KG,
                state.inputMassUnitForNewSet(60d, MassUnit.KG, MassUnit.LB));
    }

    @Test
    public void clearingTheActiveSessionDropsTheTransientDefault() {
        WorkoutSessionState state = new WorkoutSessionState();
        state.startSession(MassUnit.LB);
        state.setActiveRecordId("record-1");
        state.clearIfMatches("record-1");
        assertNull(state.sessionInputMassUnit());
        assertEquals(MassUnit.KG,
                state.inputMassUnitForNewSet(null, null, MassUnit.KG));
    }
}
