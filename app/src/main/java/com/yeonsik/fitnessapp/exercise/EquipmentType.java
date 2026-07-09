package com.yeonsik.fitnessapp.exercise;

public enum EquipmentType {
    BODYWEIGHT("bodyweight", "맨몸"),
    MACHINE("machine", "머신"),
    DUMBBELL("dumbbell", "덤벨"),
    BARBELL("barbell", "바벨"),
    SMITH_MACHINE("smith_machine", "스미스 머신"),
    CABLE("cable", "케이블"),
    BAND("band", "밴드"),
    KETTLEBELL("kettlebell", "케틀벨"),
    PLATE("plate", "플레이트"),
    OTHER("other", "기타");

    private final String id;
    private final String labelKo;

    EquipmentType(String id, String labelKo) {
        this.id = id;
        this.labelKo = labelKo;
    }

    public String id() {
        return id;
    }

    public String labelKo() {
        return labelKo;
    }

    public static EquipmentType fromId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase();
        for (EquipmentType equipmentType : values()) {
            if (equipmentType.id.equals(normalized)) {
                return equipmentType;
            }
        }
        return null;
    }
}
