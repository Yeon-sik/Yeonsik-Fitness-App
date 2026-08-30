package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** Stable contract for how one actual dining-out meal was fulfilled. */
public enum DiningOutFulfillmentMode {
    DINE_IN("dine_in", "매장"),
    DELIVERY("delivery", "배달"),
    TAKEOUT("takeout", "포장");

    private final String value;
    private final String label;

    DiningOutFulfillmentMode(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** Returns null for legacy/unrecorded values so nullable meal snapshots stay nullable. */
    public static String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (DiningOutFulfillmentMode mode : values()) {
            if (mode.value.equals(normalized) || mode.label.equals(raw.trim())) {
                return mode.value;
            }
        }
        return null;
    }

    /** Validates a newly selected mode while retaining the nullable legacy contract. */
    public static String require(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            throw new IllegalArgumentException("외식 이용 방식을 선택하세요.");
        }
        return normalized;
    }

    public static String labelOf(String raw) {
        String normalized = normalize(raw);
        if (normalized == null) {
            return "미기록";
        }
        for (DiningOutFulfillmentMode mode : values()) {
            if (mode.value.equals(normalized)) {
                return mode.label;
            }
        }
        return "미기록";
    }

    public static String[] labels() {
        DiningOutFulfillmentMode[] modes = values();
        String[] labels = new String[modes.length];
        for (int index = 0; index < modes.length; index++) {
            labels[index] = modes[index].label;
        }
        return labels;
    }
}
