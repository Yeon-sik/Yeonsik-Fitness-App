package com.yeonsik.fitnessapp.routine;

import com.yeonsik.fitnessapp.exercise.ExerciseFamilyIdentity;

public final class RoutineExerciseInstance {
    public final String id;
    public final String exerciseId;
    public final String nameKo;
    public final String uiPart;
    public final String primarySubPart;
    public final String equipment;
    public final String recordType;
    public final int order;
    public final ExerciseFamilyIdentity familyIdentity;

    public RoutineExerciseInstance(
            String id,
            String exerciseId,
            String nameKo,
            String uiPart,
            String primarySubPart,
            String equipment,
            String recordType,
            int order
    ) {
        this(
                id,
                exerciseId,
                nameKo,
                uiPart,
                primarySubPart,
                equipment,
                recordType,
                order,
                null
        );
    }

    public RoutineExerciseInstance(
            String id,
            String exerciseId,
            String nameKo,
            String uiPart,
            String primarySubPart,
            String equipment,
            String recordType,
            int order,
            ExerciseFamilyIdentity familyIdentity
    ) {
        this.id = id;
        this.exerciseId = exerciseId;
        this.nameKo = nameKo;
        this.uiPart = uiPart;
        this.primarySubPart = primarySubPart;
        this.equipment = equipment;
        this.recordType = recordType;
        this.order = order;
        this.familyIdentity = familyIdentity;
    }
}
