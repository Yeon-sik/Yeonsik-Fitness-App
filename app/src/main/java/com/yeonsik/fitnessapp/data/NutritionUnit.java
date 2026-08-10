package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** Supported physical units for nutrition bases and meal quantities. */
public final class NutritionUnit {
    public static final String GRAM = "g";
    public static final String MILLIGRAM = "mg";
    public static final String KILOGRAM = "kg";
    public static final String MILLILITER = "ml";
    public static final String LITER = "L";
    public static final String SERVING = "serving";
    private static final String[] SUPPORTED_UNITS = {
            GRAM,
            MILLIGRAM,
            KILOGRAM,
            MILLILITER,
            LITER,
            SERVING,
            "개",
            "portion",
            "pack"
    };

    private NutritionUnit() {
    }

    /** Canonical units exposed by nutrition basis selectors. */
    public static String[] options() {
        return SUPPORTED_UNITS.clone();
    }

    /** Canonicalizes supported aliases while preserving unknown legacy units. */
    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "g":
            case "gram":
            case "grams":
                return GRAM;
            case "mg":
            case "milligram":
            case "milligrams":
                return MILLIGRAM;
            case "kg":
            case "kilogram":
            case "kilograms":
                return KILOGRAM;
            case "ml":
            case "milliliter":
            case "milliliters":
            case "millilitre":
            case "millilitres":
                return MILLILITER;
            case "l":
            case "liter":
            case "liters":
            case "litre":
            case "litres":
                return LITER;
            case "serving":
            case "servings":
            case "srv":
                return SERVING;
            case "개":
            case "piece":
            case "pieces":
            case "unit":
            case "units":
                return "개";
            case "portion":
            case "portions":
                return "portion";
            case "pack":
            case "packs":
                return "pack";
            default:
                return value;
        }
    }

    public static String normalizeOrDefault(String raw, String fallback) {
        String normalized = normalize(raw);
        return normalized.isEmpty() ? normalize(fallback) : normalized;
    }

    public static String requireSupported(String raw) {
        String normalized = normalize(raw);
        if (!isSupported(normalized)) {
            throw new IllegalArgumentException(
                    "지원 단위는 g, mg, kg, ml, L, serving, 개, portion, pack 중 하나입니다."
            );
        }
        return normalized;
    }

    public static boolean isSupported(String raw) {
        String normalized = normalize(raw);
        return GRAM.equals(normalized)
                || MILLIGRAM.equals(normalized)
                || KILOGRAM.equals(normalized)
                || MILLILITER.equals(normalized)
                || LITER.equals(normalized)
                || SERVING.equals(normalized)
                || isCount(normalized);
    }

    public static boolean isMass(String raw) {
        String normalized = normalize(raw);
        return GRAM.equals(normalized)
                || MILLIGRAM.equals(normalized)
                || KILOGRAM.equals(normalized);
    }

    public static boolean isVolume(String raw) {
        String normalized = normalize(raw);
        return MILLILITER.equals(normalized) || LITER.equals(normalized);
    }

    private static boolean isCount(String raw) {
        String normalized = normalize(raw);
        return "개".equals(normalized)
                || "portion".equals(normalized)
                || "pack".equals(normalized);
    }

    public static boolean areCompatible(String first, String second) {
        String from = normalize(first);
        String to = normalize(second);
        if (from.equals(to)) {
            return true;
        }
        return (isMass(from) && isMass(to)) || (isVolume(from) && isVolume(to));
    }

    /** Converts between compatible units. Mass uses grams and volume uses milliliters internally. */
    public static double convert(double amount, String fromUnit, String toUnit) {
        String from = normalize(fromUnit);
        String to = normalize(toUnit);
        if (from.equals(to)) {
            return amount;
        }
        if (!areCompatible(from, to)) {
            throw new IllegalArgumentException(
                    "서로 변환할 수 없는 단위입니다: " + display(from) + " → " + display(to)
            );
        }
        return amount * toBaseFactor(from) / toBaseFactor(to);
    }

    /** Returns the smallest practical display unit for a per-unit nutrition preview. */
    public static String perUnit(String basisUnit) {
        String normalized = normalize(basisUnit);
        if (isMass(normalized)) {
            return GRAM;
        }
        if (isVolume(normalized)) {
            return MILLILITER;
        }
        return normalized.isEmpty() ? SERVING : normalized;
    }

    public static String display(String raw) {
        String normalized = normalize(raw);
        return normalized.isEmpty() ? SERVING : normalized;
    }

    private static double toBaseFactor(String unit) {
        switch (normalize(unit)) {
            case MILLIGRAM:
                return 0.001;
            case KILOGRAM:
                return 1000;
            case LITER:
                return 1000;
            case GRAM:
            case MILLILITER:
            case SERVING:
                return 1;
            default:
                throw new IllegalArgumentException("지원하지 않는 단위입니다: " + unit);
        }
    }
}
