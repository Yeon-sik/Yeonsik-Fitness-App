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
                        "기본 포함",
                        "유료 추가",
                        "리뷰 이벤트",
                        "서비스",
                        "쿠폰",
                        "프로모션"
                },
                DiningOutProvisionType.labels()
        );
    }
}
