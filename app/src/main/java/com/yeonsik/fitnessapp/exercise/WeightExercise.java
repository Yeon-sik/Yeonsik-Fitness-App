package com.yeonsik.fitnessapp.exercise;

import java.util.Collections;
import java.util.List;

public final class WeightExercise {
    public final String id;
    public final String nameKo;
    public final String nameEn;
    public final BodyPart bodyPart;
    public final String primarySubPart;
    public final String primarySubPartNameKo;
    public final List<String> secondarySubParts;
    public final List<String> secondarySubPartNamesKo;
    public final EquipmentType equipmentType;
    public final String equipmentNameKo;
    public final String movementPattern;
    public final String movementPatternNameKo;
    public final String mechanicType;
    public final String mechanicTypeNameKo;
    public final String laterality;
    public final String lateralityNameKo;
    public final String resistanceType;
    public final String resistanceTypeNameKo;
    public final String recordType;
    public final String recordTypeNameKo;
    public final String motionType;
    public final String motionTypeNameKo;
    public final String notes;
    public final ExerciseFamilyIdentity familyIdentity;

    public WeightExercise(
            String id,
            String nameKo,
            String nameEn,
            BodyPart bodyPart,
            String primarySubPart,
            String primarySubPartNameKo,
            List<String> secondarySubParts,
            List<String> secondarySubPartNamesKo,
            EquipmentType equipmentType,
            String equipmentNameKo,
            String movementPattern,
            String movementPatternNameKo,
            String mechanicType,
            String mechanicTypeNameKo,
            String laterality,
            String lateralityNameKo,
            String resistanceType,
            String resistanceTypeNameKo,
            String recordType,
            String recordTypeNameKo,
            String motionType,
            String motionTypeNameKo,
            String notes
    ) {
        this(
                id,
                nameKo,
                nameEn,
                bodyPart,
                primarySubPart,
                primarySubPartNameKo,
                secondarySubParts,
                secondarySubPartNamesKo,
                equipmentType,
                equipmentNameKo,
                movementPattern,
                movementPatternNameKo,
                mechanicType,
                mechanicTypeNameKo,
                laterality,
                lateralityNameKo,
                resistanceType,
                resistanceTypeNameKo,
                recordType,
                recordTypeNameKo,
                motionType,
                motionTypeNameKo,
                notes,
                null
        );
    }

    public WeightExercise(
            String id,
            String nameKo,
            String nameEn,
            BodyPart bodyPart,
            String primarySubPart,
            String primarySubPartNameKo,
            List<String> secondarySubParts,
            List<String> secondarySubPartNamesKo,
            EquipmentType equipmentType,
            String equipmentNameKo,
            String movementPattern,
            String movementPatternNameKo,
            String mechanicType,
            String mechanicTypeNameKo,
            String laterality,
            String lateralityNameKo,
            String resistanceType,
            String resistanceTypeNameKo,
            String recordType,
            String recordTypeNameKo,
            String motionType,
            String motionTypeNameKo,
            String notes,
            ExerciseFamilyIdentity familyIdentity
    ) {
        this.id = id;
        this.nameKo = nameKo;
        this.nameEn = nameEn;
        this.bodyPart = bodyPart;
        this.primarySubPart = primarySubPart;
        this.primarySubPartNameKo = primarySubPartNameKo;
        this.secondarySubParts = Collections.unmodifiableList(secondarySubParts);
        this.secondarySubPartNamesKo = Collections.unmodifiableList(secondarySubPartNamesKo);
        this.equipmentType = equipmentType;
        this.equipmentNameKo = equipmentNameKo;
        this.movementPattern = movementPattern;
        this.movementPatternNameKo = movementPatternNameKo;
        this.mechanicType = mechanicType;
        this.mechanicTypeNameKo = mechanicTypeNameKo;
        this.laterality = laterality;
        this.lateralityNameKo = lateralityNameKo;
        this.resistanceType = resistanceType;
        this.resistanceTypeNameKo = resistanceTypeNameKo;
        this.recordType = recordType;
        this.recordTypeNameKo = recordTypeNameKo;
        this.motionType = motionType;
        this.motionTypeNameKo = motionTypeNameKo;
        this.notes = notes;
        this.familyIdentity = familyIdentity;
    }

    public String displayName() {
        if (nameKo != null && !nameKo.isEmpty()) {
            return nameKo;
        }
        return nameEn;
    }
}
