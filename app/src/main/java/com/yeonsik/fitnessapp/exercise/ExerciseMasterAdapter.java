package com.yeonsik.fitnessapp.exercise;

public final class ExerciseMasterAdapter {
    private ExerciseMasterAdapter() {
    }

    public static RoutineExercise toRoutineExercise(WeightExercise exercise) {
        if (exercise == null) {
            return null;
        }

        return new RoutineExercise(
                exercise.id,
                exercise.nameKo,
                exercise.nameEn,
                exercise.bodyPart,
                exercise.equipmentType,
                exercise.primarySubPartNameKo,
                exercise.recordType
        );
    }
}
