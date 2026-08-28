package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** Fixed domain values for the kind of a dining-out composition group. */
public enum CompositionGroupType {
    BANCHAN("banchan", "반찬"),
    SIDE("side", "사이드"),
    ADD_ON("add_on", "추가 구성"),
    BEVERAGE("beverage", "음료"),
    SAUCE("sauce", "소스·양념"),
    OTHER("other", "기타");

    private final String value;
    private final String label;

    CompositionGroupType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** Resolves both the persisted value and the fixed Korean display label. */
    public static CompositionGroupType from(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (CompositionGroupType type : values()) {
            if (type.value.equals(normalized) || type.label.toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return OTHER;
    }

    public static String normalize(String raw) {
        return from(raw).value;
    }

    public static String labelOf(String raw) {
        return from(raw).label;
    }

    public static String[] labels() {
        CompositionGroupType[] types = values();
        String[] labels = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            labels[index] = types[index].label;
        }
        return labels;
    }
}
