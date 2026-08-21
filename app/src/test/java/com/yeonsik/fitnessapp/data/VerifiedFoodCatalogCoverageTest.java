package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerifiedFoodCatalogCoverageTest {
    @Test
    public void curatedCatalogIncludesVegetablesAndFruits() {
        Set<String> ids = VerifiedFoodCatalogSeed.curatedFoodIds();

        assertEquals(68, VerifiedFoodCatalogSeed.EXPECTED_COUNT);
        assertEquals(VerifiedFoodCatalogSeed.SEEDED_FOOD_COUNT, ids.size());
        assertTrue(ids.contains("kfind:R209-027018401-0000"));
        assertTrue(ids.contains("kfind:R106-092000001-0000"));
        assertTrue(ids.contains("kfind:R106-186000001-0000"));
        assertTrue(ids.contains("kfind:R106-186010001-0000"));
        assertTrue(ids.contains("kfind:R106-194008101-0000"));
        assertTrue(ids.contains("kfind:R106-194008001-0000"));
        assertTrue(ids.contains("kfind:R106-041007601-0000"));
        assertTrue(ids.contains("kfind:R106-132000001-0000"));
        assertTrue(ids.contains("kfind:R106-003000001-0000"));
        assertTrue(ids.contains("kfind:R102-001060001-0000"));
        assertTrue(ids.contains("kfind:R108-050000001-0000"));
        assertTrue(ids.contains("kfind:R108-019010001-0000"));
        assertTrue(ids.contains("kfind:R108-069000001-0000"));
        assertTrue(ids.contains("kfind:R108-092000001-0000"));
    }
}
