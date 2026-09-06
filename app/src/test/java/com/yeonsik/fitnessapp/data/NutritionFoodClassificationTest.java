package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class NutritionFoodClassificationTest {
    @Test
    public void keepsFoodKindCategoryAndCookingMethodSeparate() {
        NutritionFood food = NutritionFood.builder()
                .name("오겹살")
                .kind(NutritionFood.KIND_INGREDIENT)
                .category(NutritionFood.CATEGORY_MEAT)
                .basis(100, NutritionUnit.GRAM)
                .cookingMethod(NutritionFood.COOKING_METHOD_GRILLED)
                .prepState(NutritionFood.PREP_COOKED)
                .build();

        assertEquals(NutritionFood.KIND_INGREDIENT, food.kind);
        assertEquals(NutritionFood.CATEGORY_MEAT, food.category);
        assertEquals(NutritionFood.COOKING_METHOD_GRILLED, food.cookingMethod);
        assertEquals("오겹살 · 육류 · 구이", food.identityLabel());
    }

    @Test
    public void mapsUnknownValuesToSafeDefaults() {
        assertEquals(
                NutritionFood.CATEGORY_OTHER,
                NutritionFood.normalizeCategory("unknown-category")
        );
        assertEquals(
                NutritionFood.COOKING_METHOD_UNSPECIFIED,
                NutritionFood.normalizeCookingMethod("unknown-method")
        );
    }

    @Test
    public void preservesLegacyPrepStateCompatibility() {
        assertEquals(
                NutritionFood.COOKING_METHOD_RAW,
                NutritionFood.cookingMethodForPrepState(NutritionFood.PREP_RAW)
        );
        assertEquals(
                NutritionFood.PREP_COOKED,
                NutritionFood.prepStateForCookingMethod(NutritionFood.COOKING_METHOD_BOILED)
        );
    }

    @Test
    public void exposesTheThreeUserFacingCatalogTypes() {
        assertEquals("단일 식품", NutritionFood.kindLabel(NutritionFood.KIND_INGREDIENT));
        assertEquals("완제품", NutritionFood.kindLabel(NutritionFood.KIND_EXTERNAL_MENU));
        assertEquals("저장 메뉴", NutritionFood.kindLabel(NutritionFood.KIND_RECIPE));
    }

    @Test
    public void preventsSavedMenuNestingButAllowsFoodsAndProducts() {
        assertTrue(NutritionFood.canBeRecipeComponent(NutritionFood.KIND_INGREDIENT));
        assertTrue(NutritionFood.canBeRecipeComponent(NutritionFood.KIND_EXTERNAL_MENU));
        assertFalse(NutritionFood.canBeRecipeComponent(NutritionFood.KIND_RECIPE));
    }

    @Test
    public void distinguishesDiningOutMenusFromOtherFinishedProducts() {
        NutritionFood diningOut = NutritionFood.builder()
                .name("김치찌개")
                .brand("테스트 식당")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .source("manual_estimate", "{\"schema_version\":\"dining-out-identity.v1\"}")
                .build();
        NutritionFood packaged = NutritionFood.builder()
                .name("포장 식품")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .source("manual", "packaged")
                .build();
        NutritionFood option = NutritionFood.builder()
                .name("치즈 추가")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .source("manual_option", "dining-out-option")
                .build();

        assertTrue(diningOut.isDiningOutMenu());
        assertFalse(packaged.isDiningOutMenu());
        assertFalse(option.isDiningOutMenu());
    }

    @Test
    public void acceptsMealComponentEstimateAsDiningOutNutritionWithoutMenuIdentity() {
        NutritionFood component = NutritionFood.builder()
                .name("무료 김치")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .source(NutritionFood.SOURCE_MEAL_COMPONENT_ESTIMATE, "component-import")
                .build();

        assertTrue(NutritionFood.isDiningOutSourceType(
                NutritionFood.SOURCE_MEAL_COMPONENT_ESTIMATE));
        assertTrue(component.isDiningOutMenu());
        assertFalse(component.isDiningOutComponent());
    }
}
