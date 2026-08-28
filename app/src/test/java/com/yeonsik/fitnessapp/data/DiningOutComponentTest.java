package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DiningOutComponentTest {
    @Test
    public void provisionTypesAreIndependentFromTheSixCompositionGroups() {
        assertEquals(6, CompositionGroupType.values().length);
        assertEquals("other", CompositionGroupType.normalize("review_event"));
        assertEquals("review_event", DiningOutProvisionType.normalize("review_event"));
        assertEquals("리뷰 이벤트", DiningOutProvisionType.labelOf("review_event"));
    }

    @Test
    public void reviewEventComponentKeepsFoodKindAndProvisionSeparately() {
        NutritionProfile profile = NutritionProfile.builder()
                .value(NutritionProfile.CALORIES_KCAL, 180d)
                .value(NutritionProfile.PROTEIN_GRAMS, 8d)
                .value(NutritionProfile.CARBS_GRAMS, 20d)
                .value(NutritionProfile.FAT_GRAMS, 7d)
                .value(NutritionProfile.SODIUM_MG, 240d)
                .build();

        DiningOutComponent component = DiningOutComponent.grouped(
                "치즈볼",
                profile,
                "component-cheese-ball",
                null,
                "side",
                CompositionGroupType.SIDE.value(),
                CompositionGroupType.SIDE.label(),
                DiningOutOption.DEFAULT_ROLE,
                null,
                DiningOutProvisionType.REVIEW_EVENT.value(),
                1d
        );

        assertEquals(CompositionGroupType.SIDE.value(), component.groupType);
        assertEquals(DiningOutProvisionType.REVIEW_EVENT.value(), component.provisionType);
        assertEquals(240d, component.profile.value(NutritionProfile.SODIUM_MG), 0.001d);
    }

    @Test
    public void legacyOptionPromotesToComponentWithoutLosingProvisionOrFraction() {
        DiningOutOption option = DiningOutOption.grouped(
                "콜라",
                NutritionProfile.ofMacros(140d, 0d, 35d, 0d),
                "component-cola",
                null,
                "beverage",
                CompositionGroupType.BEVERAGE.value(),
                CompositionGroupType.BEVERAGE.label(),
                DiningOutOption.DEFAULT_ROLE,
                null,
                DiningOutProvisionType.REVIEW_EVENT.value(),
                0.5d
        );

        DiningOutComponent component = option.asComponent();

        assertTrue(component instanceof DiningOutComponent);
        assertEquals(DiningOutProvisionType.REVIEW_EVENT.value(), component.provisionType);
        assertEquals(0.5d, component.consumedFraction, 0.0001d);
    }
}
