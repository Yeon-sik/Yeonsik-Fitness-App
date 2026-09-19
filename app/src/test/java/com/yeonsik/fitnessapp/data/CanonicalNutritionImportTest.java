package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertThrows;

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
        return values;
    }
}
