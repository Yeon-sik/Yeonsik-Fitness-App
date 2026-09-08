package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.feature.workout.model.WorkoutExerciseReplacement;
import com.yeonsik.fitnessapp.feature.routine.model.RoutineExerciseDraft;

public final class ExerciseMasterAdapter {
    private ExerciseMasterAdapter() {
    }

    public static RoutineExercise toRoutineExercise(WeightExercise exercise) {
        if (exercise == null) {
            return null;
        }

        return new RoutineExercise(
                exercise.id,
                exercise.displayName(),
                exercise.nameEn,
                exercise.bodyPart,
                exercise.equipmentType,
                exercise.equipmentType == null ? null : exercise.equipmentType.id(),
                exercise.primarySubPartNameKo,
                exercise.recordType,
                exercise.familyIdentity
        );
    }

    public static RoutineExercise toRoutineExercise(RuntimeExercisePreset preset) {
        if (preset == null) {
            return null;
        }
        BodyPart bodyPart = BodyPart.fromId(preset.defaultUiPart);
        EquipmentType equipmentType = EquipmentType.fromId(preset.equipmentVariantId);
        ExerciseFamilyIdentity identity = ExerciseFamilyCatalog.empty().identityForPreset(preset);
        return new RoutineExercise(
                preset.storageExerciseId,
                preset.displayName(),
                preset.nameEn,
                bodyPart,
                equipmentType == null ? EquipmentType.OTHER : equipmentType,
                preset.equipmentVariantId,
                preset.primarySubPartNameKo,
                preset.recordType,
                identity
        );
    }

    /** Builds the feature boundary model without exposing the legacy routine DTO. */
    public static RoutineExerciseDraft toRoutineExerciseDraft(RuntimeExercisePreset preset) {
        if (preset == null) {
            return null;
        }
        return new RoutineExerciseDraft(
                preset.storageExerciseId,
                preset.displayName(),
                preset.nameEn,
                preset.defaultUiPart,
                preset.equipmentVariantId,
                preset.primarySubPartNameKo,
                preset.recordType,
                ExerciseFamilyCatalog.empty().identityForPreset(preset)
        );
    }

    public static WorkoutExerciseReplacement toWorkoutExerciseReplacement(
            RuntimeExercisePreset preset
    ) {
        if (preset == null) {
            return null;
        }
        BodyPart bodyPart = BodyPart.fromId(preset.defaultUiPart);
        EquipmentType equipmentType = EquipmentType.fromId(preset.equipmentVariantId);
        ExerciseFamilyIdentity identity = ExerciseFamilyCatalog.empty().identityForPreset(preset);
        return new WorkoutExerciseReplacement(
                preset.storageExerciseId,
                preset.displayName(),
                preset.nameEn,
                bodyPart,
                equipmentType == null ? EquipmentType.OTHER : equipmentType,
                preset.equipmentVariantId,
                preset.primarySubPartNameKo,
                preset.recordType,
                identity
        );
    }
}
