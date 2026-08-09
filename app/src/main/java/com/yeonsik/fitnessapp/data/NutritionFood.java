package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A searchable food or recipe in the nutrition catalog. */
public final class NutritionFood {
    public static final String KIND_INGREDIENT = "ingredient";
    public static final String KIND_EXTERNAL_MENU = "external_menu";
    public static final String KIND_RECIPE = "recipe";

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
    public final String kind;
    public final double basisAmount;
    public final String basisUnit;
    public final String prepState;
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
        this.kind = normalizeKind(builder.kind);
        this.basisAmount = builder.basisAmount;
        this.basisUnit = builder.basisUnit;
        this.prepState = normalizePrepState(builder.prepState);
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
                .basis(basisAmount, basisUnit)
                .profile(NutritionProfile.ofMacros(calories, proteinGrams, carbsGrams, fatGrams))
                .source(sourceType, sourceReference)
                .dataVersion(DATA_VERSION_MACROS_ONLY));
    }

    /** 기준량 라벨. 조리 상태를 알면 함께 보여 준다. */
    public String basisLabel() {
        String basis = NutritionCalculator.trim(basisAmount) + basisUnit;
        return PREP_UNSPECIFIED.equals(prepState)
                ? basis
                : basis + " (" + prepStateLabel(prepState) + ")";
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
        private String kind = KIND_EXTERNAL_MENU;
        private double basisAmount = 1;
        private String basisUnit = "serving";
        private String prepState = PREP_UNSPECIFIED;
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

        public Builder kind(String kind) {
            this.kind = kind;
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
}
