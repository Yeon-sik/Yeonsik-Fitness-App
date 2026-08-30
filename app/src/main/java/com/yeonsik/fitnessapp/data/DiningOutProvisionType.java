package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** How a dining-out component was provided for one actual meal. */
public enum DiningOutProvisionType {
    INCLUDED("included", "기본 포함"),
    PAID("paid", "유료 추가"),
    REVIEW_EVENT("review_event", "리뷰 이벤트"),
    SERVICE("service", "서비스"),
    COUPON("coupon", "쿠폰"),
    PROMOTION("promotion", "프로모션");

    private final String value;
    private final String label;

    DiningOutProvisionType(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String value() {
        return value;
    }

    public String label() {
        return label;
    }

    /** Unknown or blank values default to ordinary included service. */
    public static DiningOutProvisionType from(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        for (DiningOutProvisionType type : values()) {
            if (type.value.equals(normalized)
                    || type.label.toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return INCLUDED;
    }

    public static String normalize(String raw) {
        return from(raw).value;
    }

    public static String labelOf(String raw) {
        return from(raw).label;
    }

    public static String[] labels() {
        DiningOutProvisionType[] types = values();
        String[] labels = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            labels[index] = types[index].label;
        }
        return labels;
    }
}
