package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.CompositionGroupType;
import com.yeonsik.fitnessapp.data.DiningOutProvisionType;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.MealCompositionItem;
import com.yeonsik.fitnessapp.data.MealMenuSelection;
import com.yeonsik.fitnessapp.data.NutritionFood;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MealManagementScreenDisplayTest {
    @Test
    public void mealPrimaryNutrientDisplayStartsWithCalories() {
        assertEquals(
                NutritionProfile.CALORIES_KCAL,
                NutritionProfile.PRIMARY_DISPLAY_ORDER.get(0)
        );
        assertEquals(
                "칼로리",
                MealManagementScreen.mealNutrientDisplayLabel(
                        NutritionProfile.PRIMARY_DISPLAY_ORDER.get(0)
                )
        );
        assertEquals(
                NutritionProfile.labelOf(NutritionProfile.CARBS_GRAMS),
                MealManagementScreen.mealNutrientDisplayLabel(
                        NutritionProfile.CARBS_GRAMS
                )
        );
    }

    @Test
    public void diningOutComponentDisplayIncludesGroupAndProvisionForNonIncludedItems() {
        FitnessRepository.MealComponentEntry reviewEvent = component(
                "치즈볼",
                CompositionGroupType.SIDE,
                DiningOutProvisionType.REVIEW_EVENT
        );
        FitnessRepository.MealComponentEntry paid = component(
                "치즈 추가",
                CompositionGroupType.ADD_ON,
                DiningOutProvisionType.PAID
        );

        assertEquals(
                "· 치즈볼 · 1회 · 180kcal · 사이드 · 리뷰 이벤트",
                MealManagementScreen.diningOutComponentDisplayLabel(reviewEvent)
        );
        assertEquals(
                "· 치즈 추가 · 1회 · 180kcal · 추가 구성 · 유료 추가",
                MealManagementScreen.diningOutComponentDisplayLabel(paid)
        );
    }

    @Test
    public void includedComponentDisplayKeepsLegacyLabelWithoutProvisionSuffix() {
        FitnessRepository.MealComponentEntry included = component(
                "김치",
                CompositionGroupType.BANCHAN,
                DiningOutProvisionType.INCLUDED
        );

        assertEquals(
                "· 김치 · 1회 · 180kcal",
                MealManagementScreen.diningOutComponentDisplayLabel(included)
        );
        assertFalse(
                MealManagementScreen.diningOutComponentDisplayLabel(included)
                        .contains("기본 제공")
        );
    }

    @Test
    public void servingPercentageIsDerivedFromExistingMenuQuantity() {
        NutritionFood product = NutritionFood.builder()
                .id("product")
                .ownerId("user")
                .name("상품")
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_PROCESSED)
                .basis(1d, NutritionUnit.SERVING)
                .profile(NutritionProfile.ofMacros(200d, 10d, 20d, 5d))
                .source("manual", null)
                .build();
        MealMenuSelection half = MealMenuSelection.standalone(
                MealCompositionItem.from(product, MealManagementScreen.quantityForServingPercent(
                        product,
                        50d
                ))
        );

        assertTrue(MealManagementScreen.supportsServingPercentage(product));
        assertEquals(0.5d, half.menu.quantity, 0.0001d);
        assertEquals(50d, MealManagementScreen.servingPercentForQuantity(
                product,
                half.menu.quantity
        ), 0.0001d);
        assertEquals(100d, half.menu.calories, 0.0001d);
        assertEquals(5d, half.menu.proteinGrams, 0.0001d);
        assertEquals(10d, half.menu.carbsGrams, 0.0001d);
        assertEquals(2.5d, half.menu.fatGrams, 0.0001d);
    }

    @Test
    public void servingPercentageIsNotShownForSingleIngredient() {
        NutritionFood ingredient = NutritionFood.builder()
                .id("ingredient")
                .ownerId("user")
                .name("재료")
                .kind(NutritionFood.KIND_INGREDIENT)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1d, NutritionUnit.SERVING)
                .profile(NutritionProfile.ofMacros(100d, 5d, 10d, 2d))
                .source("manual", null)
                .build();

        assertFalse(MealManagementScreen.supportsServingPercentage(ingredient));
    }

    private static FitnessRepository.MealComponentEntry component(
            String name,
            CompositionGroupType groupType,
            DiningOutProvisionType provisionType
    ) {
        return new FitnessRepository.MealComponentEntry(
                "component-" + name,
                name,
                1d,
                "회",
                180d,
                8d,
                20d,
                7d,
                null,
                null,
                null,
                "group-" + groupType.value(),
                groupType.value(),
                "optional",
                null,
                null,
                provisionType.value()
        );
    }
}
