package com.yeonsik.fitnessapp.state;

import com.yeonsik.fitnessapp.data.MassUnit;

/** Recoverable identifiers and input-unit context shared across workout destinations. */
public final class WorkoutSessionState {
    private String activeRecordId;
    private String activeExerciseId;
    private String replacementExerciseId;
    private MassUnit sessionInputMassUnit;
    private int generation;

    public void startSession(MassUnit preferredUnit) {
        sessionInputMassUnit = MassUnit.orDefault(preferredUnit);
    }

    public MassUnit sessionInputMassUnit() {
        return sessionInputMassUnit;
    }

    public void setSessionInputMassUnit(MassUnit unit) {
        sessionInputMassUnit = MassUnit.orDefault(unit);
    }

    public MassUnit inputMassUnitForNewSet(
            Double previousInputLoadValue,
            MassUnit previousInputLoadUnit,
            MassUnit preferredUnit
    ) {
        if (previousInputLoadValue != null && previousInputLoadUnit != null) {
            return previousInputLoadUnit;
        }
        return sessionInputMassUnit == null
                ? MassUnit.orDefault(preferredUnit)
                : sessionInputMassUnit;
    }

    public String activeRecordId() {
        return activeRecordId;
    }

    public void setActiveRecordId(String recordId) {
        activeRecordId = recordId;
    }

    public String activeExerciseId() {
        return activeExerciseId;
    }

    public void setActiveExerciseId(String exerciseId) {
        activeExerciseId = exerciseId;
    }

    public String replacementExerciseId() {
        return replacementExerciseId;
    }

    public void setReplacementExerciseId(String exerciseId) {
        replacementExerciseId = exerciseId;
    }

    public void clearExerciseReplacement() {
        replacementExerciseId = null;
    }

    public void clearIfMatches(String recordId) {
        if (recordId != null && recordId.equals(activeRecordId)) {
            activeRecordId = null;
            activeExerciseId = null;
            replacementExerciseId = null;
            sessionInputMassUnit = null;
        }
    }

    public int nextGeneration() {
        return ++generation;
    }

    public int generation() {
        return generation;
    }
}
