package com.yeonsik.fitnessapp.data;

/** Common text normalization rules shared by persistence-facing policies. */
public final class TextValuePolicy {
    private TextValuePolicy() {
    }

    /** Null database/JSON values must not become the literal UI text "null". */
    public static boolean isMissing(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() || "null".equalsIgnoreCase(normalized);
    }
}
