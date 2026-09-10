package com.yeonsik.fitnessapp.core.ui;

import com.yeonsik.fitnessapp.data.NutrientCode;
import com.yeonsik.fitnessapp.data.NutritionProfile;

/** Pure nutrition row labels and units shared by Compose presentation and compatibility fixtures. */
public final class NutritionRow {
    private NutritionRow() {
    }

    /** Returns the component label used by the product UI, not the storage key. */
    public static String displayLabel(String key) {
        return NutritionProfile.CALORIES_KCAL.equals(key)
                ? "칼로리"
                : NutritionProfile.labelOf(key);
    }

    /** Returns the display unit while keeping the stored unit contract unchanged. */
    public static String displayUnit(String key) {
        String unit = NutritionProfile.unitOf(key);
        return NutrientCode.displayUnit(unit == null ? "" : unit);
    }
}
