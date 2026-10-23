package com.yeonsik.fitnessapp.data;

import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionFoodInput;
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PriceTraceBrandCompatibilityTest {
    @Test public void existingJavaConstructorAndBuilderNeedNoBrandReference() {
        NutritionFoodInput input = new NutritionFoodInput("제품", "라벨", 100, "g",
                NutritionProfile.ofMacros(100, 10, 10, 2), "manual",
                NutritionRegistrationKind.PRODUCT, 100d, "g");
        assertNull(input.getPriceTraceBrand());
        assertEquals("라벨", input.getBrand());
        assertNull(NutritionFood.builder().name("기존 제품").brand("원래 브랜드").build().priceTraceBrand);
    }
}
