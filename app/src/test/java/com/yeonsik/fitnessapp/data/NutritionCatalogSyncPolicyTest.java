package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NutritionCatalogSyncPolicyTest {
    @Test
    public void comparesEquivalentInstantsAcrossOffsetsAsEqual() {
        assertEquals(0, NutritionCatalogRepository.compareVersions(
                "2026-08-10T09:00:00+09:00",
                "2026-08-10T00:00:00Z"
        ));
    }

    @Test
    public void aTombstoneWithANewerTimestampWins() {
        assertTrue(NutritionCatalogRepository.compareVersions(
                "2026-08-10T00:00:01Z",
                "2026-08-10T00:00:00Z"
        ) > 0);
    }

    @Test
    public void ordinarySyncNeverPushesPublishedNutritionRows() {
        String foodWhere = NutritionCatalogRepository.publicationSafePushWhere("nutrition_foods");
        assertTrue(foodWhere.contains("visibility = 'private'"));
        assertTrue(foodWhere.contains("'manual_estimate'"));
        assertFalse(foodWhere.contains("'food_image_estimate'"));
        assertFalse(foodWhere.contains("'product_label_ocr'"));
        assertTrue(foodWhere.contains("fitness-nutrition-verified-import.v1"));
        assertTrue(NutritionCatalogRepository.publicationSafePushWhere(
                "nutrition_food_nutrients"
        ).contains("parent.visibility = 'private'"));
        assertTrue(NutritionCatalogRepository.publicationSafePushWhere(
                "nutrition_food_components"
        ).contains("parent.visibility = 'private'"));
    }

    @Test
    public void ordinarySyncDoesNotChangeLinksForPublishedFoods() {
        String where = NutritionCatalogRepository.publicationSafePushWhere(
                "product_nutrition_links"
        );
        assertTrue(where.contains("owner_id = ?"));
        assertTrue(where.contains("parent.visibility = 'private'"));
    }
}
