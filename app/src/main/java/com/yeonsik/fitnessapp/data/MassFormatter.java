package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit;

import com.yeonsik.fitness.shared.feature.workout.ui.WorkoutDisplayNumbers;

/** Small locale-stable formatter for user-facing mass values. */
public final class MassFormatter {
    private MassFormatter() {
    }

    /** Formats a canonical kilogram value in the selected display unit. */
    public static String format(double kilograms, MassUnit unit) {
        return WorkoutDisplayNumbers.INSTANCE.mass(kilograms, MassUnit.orDefault(unit));
    }

    /** Formats a canonical kilogram value for an editable field without grouping separators. */
    public static String formatInput(double kilograms, MassUnit unit) {
        return WorkoutDisplayNumbers.INSTANCE.decimal(MassUnit.fromKg(kilograms, unit), false, 2);
    }

    /** Formats a value that is already expressed in the selected unit. */
    public static String formatValue(double value, MassUnit unit) {
        return WorkoutDisplayNumbers.INSTANCE.decimal(value, true, 1);
    }

    public static String withUnit(double kilograms, MassUnit unit) {
        return WorkoutDisplayNumbers.INSTANCE.withUnit(kilograms, MassUnit.orDefault(unit));
    }
}
