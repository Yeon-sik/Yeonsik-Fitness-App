package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NutritionCatalogRepositoryIdentityTest {
    @Test
    public void normalizesStoreAndMenuNameForTheNameFallback() {
        assertEquals(
                "store|식당 a|menu|제육 볶음",
                NutritionCatalogRepository.canonicalDiningOutMenuKey(
                        " 식당   A ",
                        " 제육   볶음 ",
                        null
                )
        );
    }
}
