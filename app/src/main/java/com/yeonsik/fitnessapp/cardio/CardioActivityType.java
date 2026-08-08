package com.yeonsik.fitnessapp.cardio;

import java.util.Locale;

/** GPS로 추적할 수 있는 최소 유산소 운동 유형. */
public enum CardioActivityType {
    WALKING("walking", "걷기", 5.0),
    RUNNING("running", "달리기", 12.0),
    CYCLING("cycling", "자전거", 35.0);

    private final String id;
    private final String labelKo;
    private final double maxPlausibleSpeedMetersPerSecond;

    CardioActivityType(String id, String labelKo, double maxPlausibleSpeedMetersPerSecond) {
        this.id = id;
        this.labelKo = labelKo;
        this.maxPlausibleSpeedMetersPerSecond = maxPlausibleSpeedMetersPerSecond;
    }

    public String id() {
        return id;
    }

    public String labelKo() {
        return labelKo;
    }

    public double maxPlausibleSpeedMetersPerSecond() {
        return maxPlausibleSpeedMetersPerSecond;
    }

    public static CardioActivityType fromId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        for (CardioActivityType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 유산소 유형입니다: " + value);
    }
}
