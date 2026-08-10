package com.yeonsik.fitnessapp.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Stable nutrition-read.v1 projection independent of SQLite and Android UI. */
public final class NutritionReadV1 {
    public static final String CONTRACT_VERSION = "nutrition-read.v1";

    public final String nutritionFoodId;
    public final String name;
    public final String brand;
    public final String kind;
    public final double basisAmount;
    public final String basisUnit;
    public final String prepState;
    public final Map<String, Double> nutritionValues;
    public final Map<String, NutrientAmount> micronutrients;
    public final String sourceType;
    public final String sourceReference;
    public final String sourceRevision;
    public final int revision;

    private NutritionReadV1(NutritionFood food) {
        this.nutritionFoodId = food.id;
        this.name = food.name;
        this.brand = food.brand;
        this.kind = food.kind;
        this.basisAmount = food.basisAmount;
        this.basisUnit = food.basisUnit;
        this.prepState = food.prepState;
        this.sourceType = food.sourceType;
        this.sourceReference = food.sourceReference;
        this.sourceRevision = food.sourceVersion;
        this.revision = food.revision;

        Map<String, Double> values = new LinkedHashMap<>();
        for (String key : NutritionProfile.REQUIRED_KEYS) {
            values.put(key, food.profile.value(key));
        }
        for (String key : NutritionProfile.RECOMMENDED_TYPED_KEYS) {
            values.put(key, food.profile.value(key));
        }
        this.nutritionValues = Collections.unmodifiableMap(values);

        Map<String, NutrientAmount> measuredMicronutrients = new LinkedHashMap<>();
        for (String code : food.profile.knownMicronutrientCodes()) {
            measuredMicronutrients.put(
                    code,
                    new NutrientAmount(food.profile.value(code), NutrientCode.unitOf(code))
            );
        }
        this.micronutrients = Collections.unmodifiableMap(measuredMicronutrients);
    }

    public static NutritionReadV1 from(NutritionFood food) {
        if (food == null) {
            throw new IllegalArgumentException("Nutrition food is required.");
        }
        return new NutritionReadV1(food);
    }

    public static final class NutrientAmount {
        public final double amount;
        public final String unit;

        private NutrientAmount(Double amount, String unit) {
            if (amount == null) {
                throw new IllegalArgumentException("Measured micronutrient amount is required.");
            }
            this.amount = amount;
            this.unit = unit;
        }
    }
}
