package com.yeonsik.fitnessapp.data;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public final class DiningOutProvisionTypeContractTest {
    @Test
    public void keepsTheSixProvisionValuesAndLabels() {
        assertEquals(6, DiningOutProvisionType.values().length);
        assertArrayEquals(
                new String[]{
                        "included",
                        "paid",
                        "review_event",
                        "service",
                        "coupon",
                        "promotion"
                },
                new String[]{
                        DiningOutProvisionType.INCLUDED.value(),
                        DiningOutProvisionType.PAID.value(),
                        DiningOutProvisionType.REVIEW_EVENT.value(),
                        DiningOutProvisionType.SERVICE.value(),
                        DiningOutProvisionType.COUPON.value(),
                        DiningOutProvisionType.PROMOTION.value()
                }
        );
        assertArrayEquals(
                new String[]{
                        "기본 제공",
                        "유료 추가",
                        "리뷰 이벤트",
                        "서비스",
                        "쿠폰",
                        "프로모션"
                },
                DiningOutProvisionType.labels()
        );
    }

    @Test
    public void defaultsNewMealOptionsByCompositionGroup() {
        assertEquals(
                DiningOutProvisionType.INCLUDED,
                DiningOutProvisionType.defaultProvisionForGroup(
                        CompositionGroupType.SAUCE.value()
                )
        );
        for (CompositionGroupType groupType : new CompositionGroupType[]{
                CompositionGroupType.BANCHAN,
                CompositionGroupType.SIDE,
                CompositionGroupType.ADD_ON,
                CompositionGroupType.BEVERAGE,
                CompositionGroupType.OTHER
        }) {
            assertEquals(
                    groupType.label(),
                    CompositionGroupType.labelOf(groupType.value())
            );
            assertEquals(
                    DiningOutProvisionType.PAID,
                    DiningOutProvisionType.defaultProvisionForGroup(groupType.value())
            );
        }
    }
}
