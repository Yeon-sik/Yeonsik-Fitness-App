package com.yeonsik.fitnessapp.feature.nutrition.model;

import com.yeonsik.fitnessapp.data.NutritionProfile;

import java.util.Map;

/** Nutrition-label relations checked at input/write boundaries, never while reading profiles. */
public final class NutritionConsistencyPolicy {
    public static final String LABEL_GUIDANCE =
            "당류는 탄수화물 총량에 포함되고, 포화지방·트랜스지방은 지방 총량에 포함됩니다.";

    private NutritionConsistencyPolicy() {
    }

    public static void validate(NutritionProfile profile) {
        if (profile != null) {
            validateKnownNutrients(profile.asMap());
        }
    }

    public static void validateKnownNutrients(Map<String, Double> values) {
        if (values == null) return;
        requireIncluded(values, NutritionProfile.SUGARS_GRAMS, NutritionProfile.CARBS_GRAMS,
                "당류는 탄수화물 총량을 초과할 수 없습니다.");
        requireIncluded(values, NutritionProfile.ADDED_SUGARS_GRAMS, NutritionProfile.SUGARS_GRAMS,
                "첨가당은 당류를 초과할 수 없습니다.");
        requireIncluded(values, NutritionProfile.SATURATED_FAT_GRAMS, NutritionProfile.FAT_GRAMS,
                "포화지방은 지방 총량을 초과할 수 없습니다.");
        requireIncluded(values, NutritionProfile.TRANS_FAT_GRAMS, NutritionProfile.FAT_GRAMS,
                "트랜스지방은 지방 총량을 초과할 수 없습니다.");
    }

    private static void requireIncluded(Map<String, Double> values, String childKey,
                                        String parentKey, String message) {
        Double child = values.get(childKey);
        Double parent = values.get(parentKey);
        if (child != null && parent != null && child > parent) {
            throw new IllegalArgumentException(message);
        }
    }
}
