package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.CompositionGroupType;
import com.yeonsik.fitnessapp.data.DiningOutProvisionType;
import com.yeonsik.fitnessapp.data.FitnessRepository;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class MealManagementScreenDisplayTest {
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
