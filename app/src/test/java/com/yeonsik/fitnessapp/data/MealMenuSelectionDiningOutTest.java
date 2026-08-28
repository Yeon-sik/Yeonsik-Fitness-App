package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public final class MealMenuSelectionDiningOutTest {
    @Test
    public void keepsSelectedOptionsWithTheTopLevelMenu() {
        DiningOutOption option = DiningOutOption.grouped(
                "날치알 추가",
                NutritionProfile.ofMacros(80, 3, 2, 5),
                "option-1",
                null,
                "add_on_1",
                CompositionGroupType.ADD_ON.value(),
                CompositionGroupType.ADD_ON.label(),
                DiningOutOption.DEFAULT_ROLE,
                "member-1"
        );
        MealMenuSelection selection = MealMenuSelection.diningOut(
                MealCompositionItem.from(food("menu-1", "육회비빔밥"), 1),
                "user",
                "식당",
                Collections.singletonList(option)
        );

        assertEquals(1, selection.components.size());
        assertEquals(1, selection.diningOutOptions.size());
        assertEquals("add_on", selection.diningOutOptions.get(0).groupType);
        assertEquals("날치알 추가", selection.components.get(0).food.name);
    }

    private static NutritionFood food(String id, String name) {
        return NutritionFood.builder()
                .id(id)
                .ownerId("user")
                .name(name)
                .kind(NutritionFood.KIND_EXTERNAL_MENU)
                .category(NutritionFood.CATEGORY_OTHER)
                .basis(1, NutritionUnit.SERVING)
                .profile(NutritionProfile.ofMacros(600, 30, 50, 20))
                .source("manual_estimate", null)
                .build();
    }
}
