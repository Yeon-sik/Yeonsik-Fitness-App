package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;

public final class MealEntryPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);

    @Test
    public void labelsFollowInputOrderWithoutATypeLimit() {
        assertEquals("1끼", MealEntryPolicy.labelForIndex(0));
        assertEquals("2끼", MealEntryPolicy.labelForIndex(1));
        assertEquals("4끼", MealEntryPolicy.labelForIndex(3));
        assertEquals("10000끼", MealEntryPolicy.labelForIndex(9999));
    }

    @Test
    public void todayAndPastDatesAreAccepted() {
        LocalDate today = MealEntryPolicy.requireRecordDate("2026-08-09", TODAY);
        LocalDate past = MealEntryPolicy.requireRecordDate("2026-07-31", TODAY);

        assertFalse(MealEntryPolicy.isBackfilled(today, TODAY));
        assertTrue(MealEntryPolicy.isBackfilled(past, TODAY));
    }

    @Test
    public void emptyDateDefaultsToToday() {
        assertEquals(TODAY, MealEntryPolicy.requireRecordDate("", TODAY));
    }

    @Test
    public void futureDateIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireRecordDate("2026-08-10", TODAY));
    }

    @Test
    public void mealTimeIsNormalizedAndCombinedWithTheSelectedDate() {
        assertEquals("07:05", MealEntryPolicy.requireMealTime("7:05"));
        assertEquals(
                "2026-08-09T07:05+09:00",
                MealEntryPolicy.eatenAt(TODAY, "07:05", ZoneId.of("Asia/Seoul"))
        );
        assertEquals(
                "07:05",
                MealEntryPolicy.displayMealTime("2026-08-09T07:05:00+09:00")
        );
    }

    @Test
    public void missingOrInvalidMealTimeIsNotInferredFromCreatedAt() {
        assertEquals("시간 미기록", MealEntryPolicy.displayMealTime(null));
        assertEquals("시간 미기록", MealEntryPolicy.displayMealTime("not-a-time"));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireMealTime("25:10"));
    }

    @Test
    public void previewUsesFirstFoodAndCountsOnlyAdditionalItems() {
        assertEquals("닭가슴살", MealEntryPolicy.previewTitle("닭가슴살", 1, "이전 이름"));
        assertEquals("닭가슴살 외 2건",
                MealEntryPolicy.previewTitle("닭가슴살", 3, "이전 이름"));
        assertEquals("이전 이름", MealEntryPolicy.previewTitle(null, 0, "이전 이름"));
        assertEquals("직접 입력 끼니", MealEntryPolicy.previewTitle(null, 0, ""));
    }

    @Test
    public void macroRatioUsesEnergyAndAlwaysTotalsOneHundredPercent() {
        assertEquals("탄 50% · 단 25% · 지 25%",
                MealEntryPolicy.macroRatioLabel(50d, 25d, 11.111111d));
        assertEquals("탄 34% · 단 33% · 지 33%",
                MealEntryPolicy.macroRatioLabel(25d, 25d, 11.111111d));
        assertEquals("탄·단·지 비율 없음",
                MealEntryPolicy.macroRatioLabel(0d, 0d, 0d));
        assertEquals("탄·단·지 비율 없음",
                MealEntryPolicy.macroRatioLabel(null, 10d, 5d));
    }
}
