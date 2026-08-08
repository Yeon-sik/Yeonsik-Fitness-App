package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 한 음식(또는 섭취량만큼 환산된 음식)의 영양성분 묶음.
 *
 * <p>모든 영양소는 하나의 키-값 맵으로 다룬다. 키는 typed column을 쓰는 필수·1단계 권고
 * 영양소의 경우 DB 컬럼명과 같고, 미네랄·비타민은 {@link NutrientCode}의 코드다.</p>
 *
 * <p>핵심 규칙: <b>값이 없는 영양소는 0이 아니라 "모름"</b>이다. 맵에 키가 없으면 모름이며,
 * 스케일링·합산 과정에서도 0으로 치환하지 않고 모름으로 보존한다.</p>
 */
public final class NutritionProfile {
    public static final String CALORIES_KCAL = "calories_kcal";
    public static final String PROTEIN_GRAMS = "protein_grams";
    public static final String CARBS_GRAMS = "carbs_grams";
    public static final String FAT_GRAMS = "fat_grams";
    public static final String SODIUM_MG = "sodium_mg";
    public static final String SATURATED_FAT_GRAMS = "saturated_fat_grams";
    public static final String SUGARS_GRAMS = "sugars_grams";

    public static final String FIBER_GRAMS = "fiber_grams";
    public static final String ADDED_SUGARS_GRAMS = "added_sugars_grams";
    public static final String TRANS_FAT_GRAMS = "trans_fat_grams";
    public static final String CHOLESTEROL_MG = "cholesterol_mg";

    /** 모든 음식이 반드시 가져야 하는 영양소. 신규 저장 시 null을 허용하지 않는다. */
    public static final List<String> REQUIRED_KEYS = Collections.unmodifiableList(new ArrayList<>(
            java.util.Arrays.asList(
                    CALORIES_KCAL,
                    PROTEIN_GRAMS,
                    CARBS_GRAMS,
                    FAT_GRAMS,
                    SODIUM_MG,
                    SATURATED_FAT_GRAMS,
                    SUGARS_GRAMS
            )
    ));

    /** 1단계 권고 영양소. typed column으로 저장하되 모르면 NULL로 남긴다. */
    public static final List<String> RECOMMENDED_TYPED_KEYS =
            Collections.unmodifiableList(new ArrayList<>(java.util.Arrays.asList(
                    FIBER_GRAMS,
                    ADDED_SUGARS_GRAMS,
                    TRANS_FAT_GRAMS,
                    CHOLESTEROL_MG
            )));

    private static final Map<String, String> TYPED_UNITS = typedUnits();
    private static final Map<String, String> TYPED_LABELS = typedLabels();

    private final Map<String, Double> values;

    private NutritionProfile(Map<String, Double> values) {
        this.values = Collections.unmodifiableMap(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 기존 4대 영양소만 아는 레거시 데이터를 위한 최소 생성자. 나머지는 모름으로 남는다. */
    public static NutritionProfile ofMacros(
            double calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams
    ) {
        return builder()
                .value(CALORIES_KCAL, calories)
                .value(PROTEIN_GRAMS, proteinGrams)
                .value(CARBS_GRAMS, carbsGrams)
                .value(FAT_GRAMS, fatGrams)
                .build();
    }

    public static NutritionProfile empty() {
        return builder().build();
    }

    /** 값이 없으면 null(모름). 0과 구분된다. */
    public Double value(String key) {
        return values.get(normalizeKey(key));
    }

    public boolean isKnown(String key) {
        return values.containsKey(normalizeKey(key));
    }

    /** 모름을 0으로 간주해야 하는 표시 전용 경로에서만 사용한다. */
    public double valueOrZero(String key) {
        Double value = value(key);
        return value == null ? 0 : value;
    }

    public double calories() {
        return valueOrZero(CALORIES_KCAL);
    }

    public double proteinGrams() {
        return valueOrZero(PROTEIN_GRAMS);
    }

    public double carbsGrams() {
        return valueOrZero(CARBS_GRAMS);
    }

    public double fatGrams() {
        return valueOrZero(FAT_GRAMS);
    }

    public Double sodiumMg() {
        return value(SODIUM_MG);
    }

    public Double saturatedFatGrams() {
        return value(SATURATED_FAT_GRAMS);
    }

    public Double sugarsGrams() {
        return value(SUGARS_GRAMS);
    }

    public Double fiberGrams() {
        return value(FIBER_GRAMS);
    }

    public Double addedSugarsGrams() {
        return value(ADDED_SUGARS_GRAMS);
    }

    public Double transFatGrams() {
        return value(TRANS_FAT_GRAMS);
    }

    public Double cholesterolMg() {
        return value(CHOLESTEROL_MG);
    }

    public Double micronutrient(String nutrientCode) {
        return value(NutrientCode.normalize(nutrientCode));
    }

    /** 값이 기록된 미네랄·비타민 코드 목록. 사전 정의 순서를 따른다. */
    public List<String> knownMicronutrientCodes() {
        List<String> codes = new ArrayList<>();
        for (NutrientCode nutrient : NutrientCode.all()) {
            if (values.containsKey(nutrient.code)) {
                codes.add(nutrient.code);
            }
        }
        return Collections.unmodifiableList(codes);
    }

    public Map<String, Double> asMap() {
        return values;
    }

    /** 필수 영양소가 모두 채워졌는지. 레거시 행은 false가 될 수 있다. */
    public boolean hasAllRequired() {
        for (String key : REQUIRED_KEYS) {
            if (!values.containsKey(key)) {
                return false;
            }
        }
        return true;
    }

    public List<String> missingRequiredKeys() {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_KEYS) {
            if (!values.containsKey(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    /** 섭취량 배수만큼 모든 영양소를 비례 환산한다. 모름은 모름으로 남는다. */
    public NutritionProfile scaled(double multiplier) {
        if (multiplier < 0) {
            throw new IllegalArgumentException("Nutrition multiplier cannot be negative.");
        }
        Map<String, Double> scaled = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            scaled.put(entry.getKey(), entry.getValue() * multiplier);
        }
        return new NutritionProfile(scaled);
    }

    public static String unitOf(String key) {
        String normalized = normalizeKey(key);
        String typedUnit = TYPED_UNITS.get(normalized);
        if (typedUnit != null) {
            return typedUnit;
        }
        String nutrientUnit = NutrientCode.unitOf(normalized);
        return nutrientUnit == null ? "" : nutrientUnit;
    }

    public static String labelOf(String key) {
        String normalized = normalizeKey(key);
        String typedLabel = TYPED_LABELS.get(normalized);
        if (typedLabel != null) {
            return typedLabel;
        }
        return NutrientCode.labelOf(normalized);
    }

    public static boolean isTypedKey(String key) {
        return TYPED_UNITS.containsKey(normalizeKey(key));
    }

    /** typed column 키이거나 사전에 등록된 미네랄·비타민 코드여야 저장할 수 있다. */
    public static boolean isSupportedKey(String key) {
        String normalized = normalizeKey(key);
        return TYPED_UNITS.containsKey(normalized) || NutrientCode.isKnown(normalized);
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.trim().toLowerCase(java.util.Locale.US);
    }

    private static Map<String, String> typedUnits() {
        Map<String, String> units = new LinkedHashMap<>();
        units.put(CALORIES_KCAL, "kcal");
        units.put(PROTEIN_GRAMS, "g");
        units.put(CARBS_GRAMS, "g");
        units.put(FAT_GRAMS, "g");
        units.put(SODIUM_MG, "mg");
        units.put(SATURATED_FAT_GRAMS, "g");
        units.put(SUGARS_GRAMS, "g");
        units.put(FIBER_GRAMS, "g");
        units.put(ADDED_SUGARS_GRAMS, "g");
        units.put(TRANS_FAT_GRAMS, "g");
        units.put(CHOLESTEROL_MG, "mg");
        return Collections.unmodifiableMap(units);
    }

    private static Map<String, String> typedLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(CALORIES_KCAL, "열량");
        labels.put(PROTEIN_GRAMS, "단백질");
        labels.put(CARBS_GRAMS, "탄수화물");
        labels.put(FAT_GRAMS, "지방");
        labels.put(SODIUM_MG, "나트륨");
        labels.put(SATURATED_FAT_GRAMS, "포화지방");
        labels.put(SUGARS_GRAMS, "당류");
        labels.put(FIBER_GRAMS, "식이섬유");
        labels.put(ADDED_SUGARS_GRAMS, "첨가당");
        labels.put(TRANS_FAT_GRAMS, "트랜스지방");
        labels.put(CHOLESTEROL_MG, "콜레스테롤");
        return Collections.unmodifiableMap(labels);
    }

    /** 모름(null)과 0을 구분해 담는 빌더. null을 넣으면 키가 아예 기록되지 않는다. */
    public static final class Builder {
        private final Map<String, Double> values = new LinkedHashMap<>();

        private Builder() {
        }

        public Builder value(String key, Double value) {
            String normalized = normalizeKey(key);
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("Nutrient key is required.");
            }
            if (!isSupportedKey(normalized)) {
                throw new IllegalArgumentException("Unknown nutrient key: " + normalized);
            }
            if (value == null) {
                values.remove(normalized);
                return this;
            }
            if (value < 0) {
                throw new IllegalArgumentException(
                        "Nutrient " + normalized + " cannot be negative."
                );
            }
            values.put(normalized, value);
            return this;
        }

        public Builder micronutrient(String nutrientCode, Double value) {
            String normalized = NutrientCode.normalize(nutrientCode);
            if (!NutrientCode.isKnown(normalized)) {
                throw new IllegalArgumentException("Unknown nutrient code: " + normalized);
            }
            return value(normalized, value);
        }

        public Builder from(NutritionProfile profile) {
            if (profile != null) {
                values.putAll(profile.values);
            }
            return this;
        }

        public NutritionProfile build() {
            return new NutritionProfile(new LinkedHashMap<>(values));
        }
    }
}
