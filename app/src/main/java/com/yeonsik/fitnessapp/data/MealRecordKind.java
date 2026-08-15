package com.yeonsik.fitnessapp.data;

/** Stable meal-record categories shared by the local database and UI. */
public final class MealRecordKind {
    public static final String FOOD = "food";
    public static final String DINING_OUT = "dining_out";

    private MealRecordKind() {
    }

    public static String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase();
        return DINING_OUT.equals(normalized) ? DINING_OUT : FOOD;
    }

    public static boolean isDiningOut(String value) {
        return DINING_OUT.equals(normalize(value));
    }
}
