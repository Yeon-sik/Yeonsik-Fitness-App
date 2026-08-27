package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A searchable food or recipe in the nutrition catalog. */
public final class NutritionFood {
    public static final String KIND_INGREDIENT = "ingredient";
    public static final String KIND_EXTERNAL_MENU = "external_menu";
    public static final String KIND_RECIPE = "recipe";

    public static final String CATEGORY_MEAT = "meat";
    public static final String CATEGORY_POULTRY = "poultry";
    public static final String CATEGORY_SEAFOOD = "seafood";
    public static final String CATEGORY_EGG = "egg";
    public static final String CATEGORY_GRAIN = "grain";
    public static final String CATEGORY_VEGETABLE = "vegetable";
    public static final String CATEGORY_FRUIT = "fruit";
    public static final String CATEGORY_LEGUME = "legume";
    public static final String CATEGORY_DAIRY = "dairy";
    public static final String CATEGORY_NUT_SEED = "nut_seed";
    public static final String CATEGORY_PROCESSED = "processed";
    public static final String CATEGORY_BEVERAGE = "beverage";
    public static final String CATEGORY_RECIPE = "recipe";
    public static final String CATEGORY_OTHER = "other";

    private static final String[] CATEGORY_OPTIONS = {
            CATEGORY_MEAT,
            CATEGORY_POULTRY,
            CATEGORY_SEAFOOD,
            CATEGORY_EGG,
            CATEGORY_GRAIN,
            CATEGORY_VEGETABLE,
            CATEGORY_FRUIT,
            CATEGORY_LEGUME,
            CATEGORY_DAIRY,
            CATEGORY_NUT_SEED,
            CATEGORY_PROCESSED,
            CATEGORY_BEVERAGE,
            CATEGORY_RECIPE,
            CATEGORY_OTHER
    };

    public static final String COOKING_METHOD_UNSPECIFIED = "unspecified";
    public static final String COOKING_METHOD_RAW = "raw";
    public static final String COOKING_METHOD_GRILLED = "grilled";
    public static final String COOKING_METHOD_STIR_FRIED = "stir_fried";
    public static final String COOKING_METHOD_BOILED = "boiled";
    public static final String COOKING_METHOD_STEAMED = "steamed";
    public static final String COOKING_METHOD_FRIED = "fried";
    public static final String COOKING_METHOD_BLANCHED = "blanched";
    public static final String COOKING_METHOD_AIR_FRIED = "air_fried";
    public static final String COOKING_METHOD_BAKED = "baked";
    public static final String COOKING_METHOD_OTHER = "other";

    private static final String[] COOKING_METHOD_OPTIONS = {
            COOKING_METHOD_UNSPECIFIED,
            COOKING_METHOD_RAW,
            COOKING_METHOD_GRILLED,
            COOKING_METHOD_STIR_FRIED,
            COOKING_METHOD_BOILED,
            COOKING_METHOD_STEAMED,
            COOKING_METHOD_FRIED,
            COOKING_METHOD_BLANCHED,
            COOKING_METHOD_AIR_FRIED,
            COOKING_METHOD_BAKED,
            COOKING_METHOD_OTHER
    };

    public static final String PREP_UNSPECIFIED = "unspecified";
    public static final String PREP_RAW = "raw";
    public static final String PREP_COOKED = "cooked";
    public static final String PREP_AS_SERVED = "as_served";
    public static final String PREP_DRIED = "dried";
    public static final String PREP_FROZEN = "frozen";

    /** 4대 영양소만 저장하던 시절의 행. 나트륨·포화지방·당류가 NULL일 수 있다. */
    public static final int DATA_VERSION_MACROS_ONLY = 1;
    /** 필수 7종을 모두 요구하는 현재 스키마. */
    public static final int DATA_VERSION_REQUIRED_SEVEN = 2;

    public final String id;
    public final String ownerId;
    public final String name;
    public final String brand;
    public final String kind;
    public final String category;
    public final double basisAmount;
    public final String basisUnit;
    public final String prepState;
    public final String cookingMethod;
    public final NutritionProfile profile;
    public final String sourceType;
    public final String sourceReference;
    /** 출처 데이터의 판/개정 표기. 예: "MFDS 2024-03", "제품 라벨 v2". 모르면 null. */
    public final String sourceVersion;
    public final int dataVersion;
    /** nutrition-read.v1 content revision. Independent from the schema dataVersion. */
    public final int revision;

    public final double calories;
    public final double proteinGrams;
    public final double carbsGrams;
    public final double fatGrams;

    private NutritionFood(Builder builder) {
        if (builder.basisAmount <= 0) {
            throw new IllegalArgumentException("Basis amount must be greater than zero.");
        }
        this.id = builder.id;
        this.ownerId = builder.ownerId;
        this.name = builder.name;
        this.brand = normalizeText(builder.brand);
        this.kind = normalizeKind(builder.kind);
        this.category = normalizeCategory(builder.category);
        this.basisAmount = builder.basisAmount;
        this.basisUnit = NutritionUnit.normalizeOrDefault(builder.basisUnit, NutritionUnit.SERVING);
        this.prepState = normalizePrepState(builder.prepState);
        this.cookingMethod = normalizeCookingMethod(builder.cookingMethod);
        this.profile = builder.profile == null ? NutritionProfile.empty() : builder.profile;
        this.sourceType = builder.sourceType;
        this.sourceReference = builder.sourceReference;
        this.sourceVersion = builder.sourceVersion;
        this.dataVersion = builder.dataVersion;
        this.revision = Math.max(1, builder.revision);
        this.calories = profile.calories();
        this.proteinGrams = profile.proteinGrams();
        this.carbsGrams = profile.carbsGrams();
        this.fatGrams = profile.fatGrams();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 4대 영양소만 아는 음식. 나머지 영양소는 모름으로 남는다. */
    public NutritionFood(
            String id,
            String ownerId,
            String name,
            String kind,
            double basisAmount,
            String basisUnit,
            double calories,
            double proteinGrams,
            double carbsGrams,
            double fatGrams,
            String sourceType,
            String sourceReference
    ) {
        this(builder()
                .id(id)
                .ownerId(ownerId)
                .name(name)
                .kind(kind)
                .category(categoryForKind(kind))
                .basis(basisAmount, basisUnit)
                .cookingMethod(cookingMethodForPrepState("unspecified"))
                .profile(NutritionProfile.ofMacros(calories, proteinGrams, carbsGrams, fatGrams))
                .source(sourceType, sourceReference)
                .dataVersion(DATA_VERSION_MACROS_ONLY));
    }

    /** 기준량 라벨. 조리 상태를 알면 함께 보여 준다. */
    public String basisLabel() {
        String basis = NutritionCalculator.trim(basisAmount) + NutritionUnit.display(basisUnit);
        return PREP_UNSPECIFIED.equals(prepState)
                || !COOKING_METHOD_UNSPECIFIED.equals(cookingMethod)
                ? basis
                : basis + " (" + prepStateLabel(prepState) + ")";
    }

    public String displayName() {
        return brand == null ? name : brand + " · " + name;
    }

    /** 카탈로그에서 같은 종류의 조리 방식을 구분해 보여 주는 표시명. */
    public String identityLabel() {
        String context = categoryCookingLabel();
        return CATEGORY_OTHER.equals(category)
                && COOKING_METHOD_UNSPECIFIED.equals(cookingMethod)
                && PREP_UNSPECIFIED.equals(prepState)
                ? displayName()
                : displayName() + " · " + context;
    }

    /** 카테고리와 조리 방식을 함께 보여 주는 보조 라벨. */
    public String categoryCookingLabel() {
        String categoryLabel = categoryLabel(category);
        String methodLabel = COOKING_METHOD_UNSPECIFIED.equals(cookingMethod)
                ? (PREP_UNSPECIFIED.equals(prepState) ? null : prepStateLabel(prepState))
                : cookingMethodLabel(cookingMethod);
        return methodLabel == null ? categoryLabel : categoryLabel + " · " + methodLabel;
    }

    public String unitNutritionLabel() {
        return NutritionCalculator.unitNutritionLabel(profile, basisAmount, basisUnit);
    }

    public String nutritionLabel() {
        return Math.round(calories) + "kcal · "
                + NutritionCalculator.trim(proteinGrams) + "g P · "
                + NutritionCalculator.trim(carbsGrams) + "g C · "
                + NutritionCalculator.trim(fatGrams) + "g F";
    }

    /** 나트륨·포화지방·당류까지 포함한 확장 라벨. 모르는 값은 "?"로 표시한다. */
    public String extendedNutritionLabel() {
        return nutritionLabel()
                + " · 나트륨 " + NutritionCalculator.trimNullable(profile.sodiumMg()) + "mg"
                + " · 포화지방 " + NutritionCalculator.trimNullable(profile.saturatedFatGrams()) + "g"
                + " · 당류 " + NutritionCalculator.trimNullable(profile.sugarsGrams()) + "g";
    }

    /** 필수값이 비어 있는 레거시 행을 화면에서 구분하기 위한 안내 문구. null이면 정상. */
    public String missingRequiredNotice() {
        List<String> missing = profile.missingRequiredKeys();
        if (missing.isEmpty()) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        for (String key : missing) {
            labels.add(NutritionProfile.labelOf(key));
        }
        return "필수 영양소 미입력: " + String.join(", ", labels);
    }

    public static String normalizeKind(String kind) {
        String normalized = kind == null ? "" : kind.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case KIND_INGREDIENT:
            case KIND_RECIPE:
            case KIND_EXTERNAL_MENU:
                return normalized;
            default:
                return KIND_EXTERNAL_MENU;
        }
    }

    /** 사용자에게 노출하는 카탈로그 분류명. 저장 값은 기존 동기화 계약을 유지한다. */
    public static String kindLabel(String kind) {
        switch (normalizeKind(kind)) {
            case KIND_INGREDIENT:
                return "단일 식품";
            case KIND_RECIPE:
                return "저장 메뉴";
            default:
                return "완제품";
        }
    }

    /** 저장 메뉴는 다른 메뉴 안에 중첩하지 않고, 단일 식품과 완제품만 재료로 쓴다. */
    public static boolean canBeRecipeComponent(String kind) {
        return !KIND_RECIPE.equals(normalizeKind(kind));
    }

    /** Dining-out menu rows are Fitness-owned external menus, not ordinary packaged products. */
    public boolean isDiningOutMenu() {
        return KIND_EXTERNAL_MENU.equals(normalizeKind(kind))
                && isDiningOutSourceType(sourceType);
    }

    /** Legacy manual estimates and canonical OCR food-image estimates share the dining-out UI path. */
    public static boolean isDiningOutSourceType(String sourceType) {
        return "manual_estimate".equalsIgnoreCase(sourceType)
                || "food_image_estimate".equalsIgnoreCase(sourceType);
    }

    public static String normalizeCategory(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase(Locale.US);
        for (String option : CATEGORY_OPTIONS) {
            if (option.equals(normalized)) {
                return normalized;
            }
        }
        return CATEGORY_OTHER;
    }

    public static String categoryForKind(String kind) {
        return KIND_RECIPE.equals(normalizeKind(kind)) ? CATEGORY_RECIPE : CATEGORY_OTHER;
    }

    public static String categoryLabel(String category) {
        switch (normalizeCategory(category)) {
            case CATEGORY_MEAT:
                return "육류";
            case CATEGORY_POULTRY:
                return "가금류";
            case CATEGORY_SEAFOOD:
                return "어류·해산물";
            case CATEGORY_EGG:
                return "달걀·난류";
            case CATEGORY_GRAIN:
                return "곡류·면";
            case CATEGORY_VEGETABLE:
                return "채소";
            case CATEGORY_FRUIT:
                return "과일";
            case CATEGORY_LEGUME:
                return "콩·두부";
            case CATEGORY_DAIRY:
                return "유제품";
            case CATEGORY_NUT_SEED:
                return "견과·씨앗";
            case CATEGORY_PROCESSED:
                return "가공식품";
            case CATEGORY_BEVERAGE:
                return "음료";
            case CATEGORY_RECIPE:
                return "요리·메뉴";
            default:
                return "기타";
        }
    }

    public static String[] categoryOptions() {
        return CATEGORY_OPTIONS.clone();
    }

    public static String normalizeCookingMethod(String cookingMethod) {
        String normalized = cookingMethod == null
                ? ""
                : cookingMethod.trim().toLowerCase(Locale.US);
        for (String option : COOKING_METHOD_OPTIONS) {
            if (option.equals(normalized)) {
                return normalized;
            }
        }
        return COOKING_METHOD_UNSPECIFIED;
    }

    public static String cookingMethodLabel(String cookingMethod) {
        switch (normalizeCookingMethod(cookingMethod)) {
            case COOKING_METHOD_RAW:
                return "생것";
            case COOKING_METHOD_GRILLED:
                return "구이";
            case COOKING_METHOD_STIR_FRIED:
                return "볶음";
            case COOKING_METHOD_BOILED:
                return "삶기·수육";
            case COOKING_METHOD_STEAMED:
                return "찜";
            case COOKING_METHOD_FRIED:
                return "튀김";
            case COOKING_METHOD_BLANCHED:
                return "데침";
            case COOKING_METHOD_AIR_FRIED:
                return "에어프라이";
            case COOKING_METHOD_BAKED:
                return "오븐·구움";
            case COOKING_METHOD_OTHER:
                return "기타";
            default:
                return "미지정";
        }
    }

    public static String[] cookingMethodOptions() {
        return COOKING_METHOD_OPTIONS.clone();
    }

    /** 기존 prep_state를 사용하는 호출부와 원격 레거시 행을 위한 호환 매핑. */
    public static String cookingMethodForPrepState(String prepState) {
        switch (normalizePrepState(prepState)) {
            case PREP_RAW:
                return COOKING_METHOD_RAW;
            case PREP_COOKED:
                return COOKING_METHOD_OTHER;
            default:
                return COOKING_METHOD_UNSPECIFIED;
        }
    }

    /** 새 조리 방식을 기존 prep_state에도 반영해 과거 소비자와 호환한다. */
    public static String prepStateForCookingMethod(String cookingMethod) {
        String normalized = normalizeCookingMethod(cookingMethod);
        if (COOKING_METHOD_UNSPECIFIED.equals(normalized)) {
            return PREP_UNSPECIFIED;
        }
        return COOKING_METHOD_RAW.equals(normalized) ? PREP_RAW : PREP_COOKED;
    }

    public static String normalizePrepState(String prepState) {
        String normalized = prepState == null ? "" : prepState.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case PREP_RAW:
            case PREP_COOKED:
            case PREP_AS_SERVED:
            case PREP_DRIED:
            case PREP_FROZEN:
                return normalized;
            default:
                return PREP_UNSPECIFIED;
        }
    }

    public static String prepStateLabel(String prepState) {
        switch (normalizePrepState(prepState)) {
            case PREP_RAW:
                return "생";
            case PREP_COOKED:
                return "조리 후";
            case PREP_AS_SERVED:
                return "제공 상태";
            case PREP_DRIED:
                return "건조";
            case PREP_FROZEN:
                return "냉동";
            default:
                return "미지정";
        }
    }

    public static final class Builder {
        private String id;
        private String ownerId;
        private String name;
        private String brand;
        private String kind = KIND_EXTERNAL_MENU;
        private String category = CATEGORY_OTHER;
        private double basisAmount = 1;
        private String basisUnit = "serving";
        private String prepState = PREP_UNSPECIFIED;
        private String cookingMethod = COOKING_METHOD_UNSPECIFIED;
        private NutritionProfile profile = NutritionProfile.empty();
        private String sourceType = "manual";
        private String sourceReference;
        private String sourceVersion;
        private int dataVersion = DATA_VERSION_REQUIRED_SEVEN;
        private int revision = 1;

        private Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder brand(String brand) {
            this.brand = brand;
            return this;
        }

        public Builder kind(String kind) {
            this.kind = kind;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder basis(double basisAmount, String basisUnit) {
            this.basisAmount = basisAmount;
            this.basisUnit = basisUnit;
            return this;
        }

        public Builder prepState(String prepState) {
            this.prepState = prepState;
            return this;
        }

        public Builder cookingMethod(String cookingMethod) {
            this.cookingMethod = cookingMethod;
            return this;
        }

        public Builder profile(NutritionProfile profile) {
            this.profile = profile;
            return this;
        }

        public Builder source(String sourceType, String sourceReference) {
            this.sourceType = sourceType;
            this.sourceReference = sourceReference;
            return this;
        }

        public Builder sourceVersion(String sourceVersion) {
            this.sourceVersion = sourceVersion;
            return this;
        }

        public Builder dataVersion(int dataVersion) {
            this.dataVersion = dataVersion;
            return this;
        }

        public Builder revision(int revision) {
            this.revision = revision;
            return this;
        }

        public NutritionFood build() {
            return new NutritionFood(this);
        }
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
