package com.yeonsik.fitnessapp.exercise;

public final class RoutineExercise {
    public final String masterExerciseId;
    public final String nameKo;
    public final String nameEn;
    public final BodyPart bodyPart;
    public final EquipmentType equipmentType;
    public final String equipmentVariantId;
    public final String primarySubPart;
    public final String recordType;
    public final ExerciseFamilyIdentity familyIdentity;

    public RoutineExercise(
            String masterExerciseId,
            String nameKo,
            String nameEn,
            BodyPart bodyPart,
            EquipmentType equipmentType,
            String primarySubPart,
            String recordType
    ) {
        this(
                masterExerciseId,
                nameKo,
                nameEn,
                bodyPart,
                equipmentType,
                null,
                primarySubPart,
                recordType,
                null
        );
    }

    public RoutineExercise(
            String masterExerciseId,
            String nameKo,
            String nameEn,
            BodyPart bodyPart,
            EquipmentType equipmentType,
            String equipmentVariantId,
            String primarySubPart,
            String recordType,
            ExerciseFamilyIdentity familyIdentity
    ) {
        this.masterExerciseId = masterExerciseId;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.bodyPart = bodyPart;
        this.equipmentType = equipmentType;
        this.equipmentVariantId = equipmentVariantId;
        this.primarySubPart = primarySubPart;
        this.recordType = recordType;
        this.familyIdentity = familyIdentity;
    }
}
