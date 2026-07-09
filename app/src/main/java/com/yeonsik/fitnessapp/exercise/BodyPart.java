package com.yeonsik.fitnessapp.exercise;

public enum BodyPart {
    CHEST("chest", "가슴"),
    BACK("back", "등"),
    LEGS("legs", "하체"),
    SHOULDERS("shoulders", "어깨"),
    ARMS("arms", "팔"),
    ABS("abs", "복근");

    private final String id;
    private final String labelKo;

    BodyPart(String id, String labelKo) {
        this.id = id;
        this.labelKo = labelKo;
    }

    public String id() {
        return id;
    }

    public String labelKo() {
        return labelKo;
    }

    public static BodyPart fromId(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toLowerCase();
        for (BodyPart bodyPart : values()) {
            if (bodyPart.id.equals(normalized)) {
                return bodyPart;
            }
        }
        return null;
    }
}
