package com.yeonsik.fitnessapp.exercise;

import java.util.Locale;

/** UI-only equipment filter category. It is never part of exercise identity. */
public enum UiEquipmentCategory {
    BODYWEIGHT("bodyweight", "맨몸"),
    FREE_WEIGHT("free_weight", "프리웨이트"),
    MACHINE("machine", "머신"),
    CABLE("cable", "케이블"),
    BAND("band", "밴드"),
    OTHER("other", "기타");

    private final String id;
    private final String labelKo;

    UiEquipmentCategory(String id, String labelKo) {
        this.id = id;
        this.labelKo = labelKo;
    }

    public String id() {
        return id;
    }

    public String labelKo() {
        return labelKo;
    }

    public static UiEquipmentCategory fromEquipmentVariant(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "bodyweight":
            case "gymnastic_rings":
                return BODYWEIGHT;
            case "dumbbell":
            case "barbell":
            case "kettlebell":
            case "plate":
            case "trap_bar":
                return FREE_WEIGHT;
            case "machine":
            case "smith_machine":
                return MACHINE;
            case "cable":
                return CABLE;
            case "band":
                return BAND;
            default:
                return OTHER;
        }
    }
}
