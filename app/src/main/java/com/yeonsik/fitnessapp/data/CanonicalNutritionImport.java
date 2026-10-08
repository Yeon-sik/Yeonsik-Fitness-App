package com.yeonsik.fitnessapp.data;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Domain model and validation for Fitness canonical Nutrition input contracts. */
public final class CanonicalNutritionImport {
    public static final String CONTRACT_NUTRITION_LABEL_V1 = "nutrition-label.v1";
    public static final String CONTRACT_FOOD_ESTIMATE_V1 = "food-estimate.v1";
    public static final String CONTRACT_EXTERNAL_REFERENCE_V1 = "external-reference.v1";

    public static final String SOURCE_PRODUCT_LABEL_OCR = "product_label_ocr";
    public static final String SOURCE_FOOD_IMAGE_ESTIMATE = "food_image_estimate";
    public static final String SOURCE_EXTERNAL_REFERENCE = "external_reference";
    public static final String SOURCE_MENU_REFERENCE = "menu_reference";
    public static final String SOURCE_MANUAL = "manual";

    public static final List<String> REQUIRED_NUTRIENTS = Collections.unmodifiableList(Arrays.asList(
            NutritionProfile.CALORIES_KCAL,
            NutritionProfile.CARBS_GRAMS,
            NutritionProfile.PROTEIN_GRAMS,
            NutritionProfile.FAT_GRAMS,
            NutritionProfile.SUGARS_GRAMS,
            NutritionProfile.SATURATED_FAT_GRAMS,
            NutritionProfile.SODIUM_MG
    ));

    public final String inputContract;
    public final Map<String, Double> requiredNutrients;
    public final Map<String, NutrientEvidence> nutrientProvenance;
    public final boolean userVerified;
    public final boolean hasEstimationEvidence;
    public final String manufacturerName;
    public final String brandName;
    public final String subBrandName;
    public final String productName;

    private CanonicalNutritionImport(Builder builder) {
        this.inputContract = normalize(builder.inputContract);
        this.requiredNutrients = immutableCopy(builder.requiredNutrients);
        this.nutrientProvenance = immutableCopy(builder.nutrientProvenance);
        this.userVerified = builder.userVerified;
        this.hasEstimationEvidence = builder.hasEstimationEvidence;
        this.manufacturerName = blankToNull(builder.manufacturerName);
        this.brandName = blankToNull(builder.brandName);
        this.subBrandName = blankToNull(builder.subBrandName);
        this.productName = blankToNull(builder.productName);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Validates the input boundary without rewriting any evidence reference. */
    public void validate() {
        if (!userVerified) {
            throw new IllegalArgumentException("Canonical Nutrition input requires user verification.");
        }
        if (!CONTRACT_NUTRITION_LABEL_V1.equals(inputContract)
                && !CONTRACT_FOOD_ESTIMATE_V1.equals(inputContract)
                && !CONTRACT_EXTERNAL_REFERENCE_V1.equals(inputContract)) {
            throw new IllegalArgumentException("Unsupported canonical Nutrition input contract.");
        }
        requireExactKeys(requiredNutrients, "required nutrients");
        requireExactKeys(nutrientProvenance, "nutrient provenance");
        for (String nutrient : REQUIRED_NUTRIENTS) {
            Double value = requiredNutrients.get(nutrient);
            NutrientEvidence evidence = nutrientProvenance.get(nutrient);
            if (value == null || value < 0 || evidence == null
                    || evidence.value == null || !value.equals(evidence.value)) {
                throw new IllegalArgumentException("Required nutrient value and provenance must match: " + nutrient);
            }
            evidence.validate();
        }

        if (CONTRACT_NUTRITION_LABEL_V1.equals(inputContract)) {
            if (hasEstimationEvidence || !allEvidence(SOURCE_PRODUCT_LABEL_OCR, "observed")) {
                throw new IllegalArgumentException(
                        "nutrition-label.v1 accepts only observed product_label_ocr evidence.");
            }
        } else if (CONTRACT_FOOD_ESTIMATE_V1.equals(inputContract)) {
            if (hasEstimationEvidence == false && !anyEstimated()) {
                throw new IllegalArgumentException("food-estimate.v1 requires estimation evidence.");
            }
            if (containsEvidenceSource(SOURCE_PRODUCT_LABEL_OCR)) {
                throw new IllegalArgumentException(
                        "food-estimate.v1 cannot claim product-label nutrient evidence.");
            }
        } else {
            if (hasEstimationEvidence || !allEvidence(SOURCE_EXTERNAL_REFERENCE, "observed")) {
                throw new IllegalArgumentException(
                        "external-reference.v1 accepts only observed external_reference evidence.");
            }
            if (containsForbiddenExternalEvidence()) {
                throw new IllegalArgumentException(
                        "external-reference.v1 rejects estimated, OCR, image-estimate, manual, and menu evidence.");
            }
            for (NutrientEvidence evidence : nutrientProvenance.values()) {
                for (String reference : evidence.evidenceRefs) {
                    requirePublicUrl(reference);
                }
            }
        }
    }

    private boolean allEvidence(String sourceType, String valueStatus) {
        for (NutrientEvidence evidence : nutrientProvenance.values()) {
            if (!sourceType.equals(evidence.sourceType) || !valueStatus.equals(evidence.valueStatus)) {
                return false;
            }
        }
        return true;
    }

    private boolean anyEstimated() {
        for (NutrientEvidence evidence : nutrientProvenance.values()) {
            if ("estimated".equals(evidence.valueStatus)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsEvidenceSource(String sourceType) {
        for (NutrientEvidence evidence : nutrientProvenance.values()) {
            if (sourceType.equals(evidence.sourceType)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsForbiddenExternalEvidence() {
        return containsEvidenceSource(SOURCE_PRODUCT_LABEL_OCR)
                || containsEvidenceSource(SOURCE_FOOD_IMAGE_ESTIMATE)
                || containsEvidenceSource(SOURCE_MANUAL)
                || containsEvidenceSource(SOURCE_MENU_REFERENCE);
    }

    private static void requireExactKeys(Map<?, ?> values, String label) {
        if (values.size() != REQUIRED_NUTRIENTS.size() || !values.keySet().containsAll(REQUIRED_NUTRIENTS)) {
            throw new IllegalArgumentException(label + " must contain exactly the seven required nutrients.");
        }
    }

    private static void requirePublicUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException("External reference evidence must be a public HTTP(S) URL.");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("External reference evidence must be a public HTTP(S) URL.", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static <T> Map<String, T> immutableCopy(Map<String, T> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null
                ? Collections.<String, T>emptyMap()
                : values));
    }

    public static final class NutrientEvidence {
        public final Double value;
        public final String valueStatus;
        public final String sourceType;
        public final List<String> evidenceRefs;

        public NutrientEvidence(
                Double value,
                String valueStatus,
                String sourceType,
                List<String> evidenceRefs
        ) {
            this.value = value;
            this.valueStatus = normalize(valueStatus);
            this.sourceType = normalize(sourceType);
            this.evidenceRefs = Collections.unmodifiableList(Arrays.asList(
                    evidenceRefs == null ? new String[0] : evidenceRefs.toArray(new String[0])
            ));
        }

        private void validate() {
            if (value == null || value < 0 || (!"observed".equals(valueStatus) && !"estimated".equals(valueStatus))
                    || sourceType.isEmpty() || evidenceRefs.isEmpty()) {
                throw new IllegalArgumentException("Each nutrient requires a non-negative value and evidence.");
            }
            for (String reference : evidenceRefs) {
                if (reference == null || reference.trim().isEmpty()) {
                    throw new IllegalArgumentException("Evidence references cannot be blank.");
                }
            }
        }
    }

    public static final class Builder {
        private String inputContract;
        private Map<String, Double> requiredNutrients = new LinkedHashMap<>();
        private Map<String, NutrientEvidence> nutrientProvenance = new LinkedHashMap<>();
        private boolean userVerified;
        private boolean hasEstimationEvidence;
        private String manufacturerName;
        private String brandName;
        private String subBrandName;
        private String productName;

        private Builder() {
        }

        public Builder inputContract(String value) {
            inputContract = value;
            return this;
        }

        public Builder requiredNutrients(Map<String, Double> value) {
            requiredNutrients = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
            return this;
        }

        public Builder nutrientProvenance(Map<String, NutrientEvidence> value) {
            nutrientProvenance = value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
            return this;
        }

        public Builder userVerified(boolean value) {
            userVerified = value;
            return this;
        }

        public Builder hasEstimationEvidence(boolean value) {
            hasEstimationEvidence = value;
            return this;
        }

        public Builder hierarchy(String manufacturer, String brand, String subBrand, String product) {
            manufacturerName = manufacturer;
            brandName = brand;
            subBrandName = subBrand;
            productName = product;
            return this;
        }

        public CanonicalNutritionImport build() {
            return new CanonicalNutritionImport(this);
        }
    }
}
