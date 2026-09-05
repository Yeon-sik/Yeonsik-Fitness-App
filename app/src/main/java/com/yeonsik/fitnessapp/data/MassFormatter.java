package com.yeonsik.fitnessapp.data;

import java.util.Locale;

/** Small locale-stable formatter for user-facing mass values. */
public final class MassFormatter {
    private MassFormatter() {
    }

    /** Formats a canonical kilogram value in the selected display unit. */
    public static String format(double kilograms, MassUnit unit) {
        return formatValue(MassUnit.fromKg(kilograms, MassUnit.orDefault(unit)), unit, true);
    }

    /** Formats a canonical kilogram value for an editable field without grouping separators. */
    public static String formatInput(double kilograms, MassUnit unit) {
        return formatValue(MassUnit.fromKg(kilograms, MassUnit.orDefault(unit)), unit, false);
    }

    /** Formats a value that is already expressed in the selected unit. */
    public static String formatValue(double value, MassUnit unit) {
        return formatValue(value, unit, true);
    }

    public static String withUnit(double kilograms, MassUnit unit) {
        MassUnit effective = MassUnit.orDefault(unit);
        return format(kilograms, effective) + effective.symbol();
    }

    public static String withUnitValue(double value, MassUnit unit) {
        MassUnit effective = MassUnit.orDefault(unit);
        return formatValue(value, effective) + effective.symbol();
    }

    private static String formatValue(double value, MassUnit unit, boolean grouped) {
        if (!Double.isFinite(value)) {
            return "—";
        }
        if (Math.abs(value) < 0.0000001d) {
            value = 0d;
        }
        String pattern = grouped ? "%,.2f" : "%.2f";
        String result = String.format(Locale.ROOT, pattern, value);
        int end = result.length();
        while (end > 0 && result.charAt(end - 1) == '0') {
            end -= 1;
        }
        if (end > 0 && result.charAt(end - 1) == '.') {
            end -= 1;
        }
        return result.substring(0, end);
    }
}
