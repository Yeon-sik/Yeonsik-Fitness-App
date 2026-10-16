package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

public final class CanonicalNutritionImportTest {
    @Test
    public void acceptsExternalReferenceWithSevenObservedPublicUrlValues() {
        Map<String, Double> values = values();
        Map<String, CanonicalNutritionImport.NutrientEvidence> provenance = new LinkedHashMap<>();
        for (String nutrient : CanonicalNutritionImport.REQUIRED_NUTRIENTS) {
            provenance.put(nutrient, new CanonicalNutritionImport.NutrientEvidence(
                    values.get(nutrient),
                    "observed",
                    CanonicalNutritionImport.SOURCE_EXTERNAL_REFERENCE,
                    Arrays.asList("https://manufacturer.example/products/alpha#" + nutrient)
            ));
        }

        CanonicalNutritionImport.builder()
                .inputContract(CanonicalNutritionImport.CONTRACT_EXTERNAL_REFERENCE_V1)
                .requiredNutrients(values)
                .nutrientProvenance(provenance)
                .userVerified(true)
                .hierarchy("Manufacturer", "Brand", "Sub-brand", "Product")
                .build()
                .validate();
        NutritionFood food = NutritionFood.builder()
                .id("external")
                .ownerId("owner")
                .name("Product")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .basis(100, "g")
                .source(NutritionFood.SOURCE_EXTERNAL_REFERENCE, "https://manufacturer.example/products/alpha")
                .build();
        org.junit.Assert.assertTrue(food.isPackagedFood());
        org.junit.Assert.assertFalse(NutritionFood.isDiningOutSourceType(
                NutritionFood.SOURCE_EXTERNAL_REFERENCE));
    }

    @Test
    public void rejectsExternalReferenceWhenEvidenceIsEstimatedOrOcr() {
        Map<String, Double> values = values();
        Map<String, CanonicalNutritionImport.NutrientEvidence> provenance = new LinkedHashMap<>();
        for (String nutrient : CanonicalNutritionImport.REQUIRED_NUTRIENTS) {
            provenance.put(nutrient, new CanonicalNutritionImport.NutrientEvidence(
                    values.get(nutrient),
                    "observed",
                    nutrient.equals(NutritionProfile.CALORIES_KCAL)
                            ? CanonicalNutritionImport.SOURCE_PRODUCT_LABEL_OCR
                            : CanonicalNutritionImport.SOURCE_EXTERNAL_REFERENCE,
                    Arrays.asList("https://manufacturer.example/products/alpha")
            ));
        }

        CanonicalNutritionImport value = CanonicalNutritionImport.builder()
                .inputContract(CanonicalNutritionImport.CONTRACT_EXTERNAL_REFERENCE_V1)
                .requiredNutrients(values)
                .nutrientProvenance(provenance)
                .userVerified(true)
                .build();

        assertThrows(IllegalArgumentException.class, value::validate);
    }

    private static Map<String, Double> values() {
        Map<String, Double> values = new LinkedHashMap<>();
        double next = 10;
        for (String nutrient : CanonicalNutritionImport.REQUIRED_NUTRIENTS) {
            values.put(nutrient, next++);
        }
        values.put(NutritionProfile.CARBS_GRAMS, 43d);
        values.put(NutritionProfile.SUGARS_GRAMS, 8d);
        values.put(NutritionProfile.FAT_GRAMS, 28d);
        values.put(NutritionProfile.SATURATED_FAT_GRAMS, 9d);
        return values;
    }

    @Test public void canonicalInputsEnforceRelationsWithExistingOptionalNutrients() {
        String[] contracts = {CanonicalNutritionImport.CONTRACT_NUTRITION_LABEL_V1,
                CanonicalNutritionImport.CONTRACT_FOOD_ESTIMATE_V1,
                CanonicalNutritionImport.CONTRACT_EXTERNAL_REFERENCE_V1};
        for (String contract : contracts) {
            Map<String, Double> optional = new LinkedHashMap<>();
            optional.put(NutritionProfile.TRANS_FAT_GRAMS, 0.4d);
            optional.put(NutritionProfile.ADDED_SUGARS_GRAMS, null);
            canonical(contract, values(), optional).validate();
            assertNull(canonical(contract, values(), optional).optionalNutrients.get(NutritionProfile.ADDED_SUGARS_GRAMS));
            Map<String, Double> invalidSugar = values();
            invalidSugar.put(NutritionProfile.SUGARS_GRAMS, 50d);
            assertThrows(IllegalArgumentException.class, () -> canonical(contract, invalidSugar, optional).validate());
            Map<String, Double> invalidFat = values();
            invalidFat.put(NutritionProfile.SATURATED_FAT_GRAMS, 30d);
            assertThrows(IllegalArgumentException.class, () -> canonical(contract, invalidFat, optional).validate());
            optional.put(NutritionProfile.ADDED_SUGARS_GRAMS, 9d);
            assertThrows(IllegalArgumentException.class, () -> canonical(contract, values(), optional).validate());
            optional.remove(NutritionProfile.ADDED_SUGARS_GRAMS);
            optional.put(NutritionProfile.TRANS_FAT_GRAMS, 30d);
            assertThrows(IllegalArgumentException.class, () -> canonical(contract, values(), optional).validate());
        }
    }

    private static CanonicalNutritionImport canonical(String contract, Map<String, Double> values,
                                                     Map<String, Double> optional) {
        boolean estimated = CanonicalNutritionImport.CONTRACT_FOOD_ESTIMATE_V1.equals(contract);
        String source = estimated ? CanonicalNutritionImport.SOURCE_FOOD_IMAGE_ESTIMATE :
                CanonicalNutritionImport.CONTRACT_NUTRITION_LABEL_V1.equals(contract)
                        ? CanonicalNutritionImport.SOURCE_PRODUCT_LABEL_OCR : CanonicalNutritionImport.SOURCE_EXTERNAL_REFERENCE;
        Map<String, CanonicalNutritionImport.NutrientEvidence> evidence = new LinkedHashMap<>();
        for (String key : CanonicalNutritionImport.REQUIRED_NUTRIENTS) {
            evidence.put(key, new CanonicalNutritionImport.NutrientEvidence(values.get(key),
                    estimated ? "estimated" : "observed", source, Arrays.asList("https://manufacturer.example/label")));
        }
        return CanonicalNutritionImport.builder().inputContract(contract).requiredNutrients(values)
                .optionalNutrients(optional).nutrientProvenance(evidence).userVerified(true)
                .hasEstimationEvidence(estimated).build();
    }
}
