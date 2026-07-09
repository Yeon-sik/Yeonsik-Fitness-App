package com.yeonsik.fitnessapp.exercise;

public final class RoutineExercise {
    public final String masterExerciseId;
    public final String nameKo;
    public final String nameEn;
    public final BodyPart bodyPart;
    public final EquipmentType equipmentType;
    public final String primarySubPart;
    public final String recordType;

    public RoutineExercise(
            String masterExerciseId,
            String nameKo,
            String nameEn,
            BodyPart bodyPart,
            EquipmentType equipmentType,
            String primarySubPart,
            String recordType
    ) {
        this.masterExerciseId = masterExerciseId;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.bodyPart = bodyPart;
        this.equipmentType = equipmentType;
        this.primarySubPart = primarySubPart;
        this.recordType = recordType;
    }
}
