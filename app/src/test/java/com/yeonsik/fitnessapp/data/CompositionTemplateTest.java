package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CompositionTemplateTest {
    @Test
    public void exactOneGroupRejectsMultipleOrMissingSelections() {
        CompositionMember coke = member("coke", 120);
        CompositionMember water = member("water", 0);
        CompositionGroup drinks = new CompositionGroup(
                "drinks-group",
                "drink",
                "음료",
                CompositionGroup.MODE_EXACTLY_ONE,
                1,
                1,
                0,
                Arrays.asList(coke, water)
        );
        CompositionTemplate template = new CompositionTemplate(
                "template",
                "user",
                "버거 세트",
                CompositionTemplate.KIND_DINING_OUT,
                "burger-food",
                null,
                1,
                Collections.singletonList(drinks)
        );

        Map<String, java.util.List<CompositionMember>> selected = new LinkedHashMap<>();
        selected.put("drink", Collections.singletonList(coke));
        CompositionSelection selection = new CompositionSelection(
                template,
                Collections.emptyList(),
                selected
        );

        assertEquals(120d, selection.nutritionProfile().calories(), 0.001d);
        assertTrue(drinks.isSatisfiedBy(Collections.singletonList("coke")));
        assertTrue(!drinks.isSatisfiedBy(Arrays.asList("coke", "water")));
        assertTrue(!drinks.isSatisfiedBy(Collections.emptyList()));
    }

    private static CompositionMember member(String id, double calories) {
        return new CompositionMember(
                id,
                null,
                id,
                null,
                1,
                NutritionUnit.SERVING,
                false,
                0,
                null,
                calories == 0
                        ? NutritionProfile.empty()
                        : NutritionProfile.ofMacros(calories, 0, 0, 0)
        );
    }
}
