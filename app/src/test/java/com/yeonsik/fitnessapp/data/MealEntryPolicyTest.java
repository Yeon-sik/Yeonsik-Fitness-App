package com.yeonsik.fitnessapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
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
    public void importedEatenAtUsesItsOffsetLocalDateAndRejectsNaiveOrFutureValues() {
        assertEquals(
                OffsetDateTime.parse("2026-08-09T07:05:00+09:00"),
                MealEntryPolicy.requireImportedEatenAt("2026-08-09T07:05:00+09:00", TODAY)
        );
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireImportedEatenAt("2026-08-10T00:05:00+00:00", TODAY));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireImportedEatenAt("2026-08-09T07:05:00", TODAY));
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
    public void diningOutKeepsStoreAndMenuDistinctAndRequiresBoth() {
        assertEquals("강남식당 · 제육볶음",
                MealEntryPolicy.previewDiningOutTitle("강남식당", "제육볶음"));
        assertEquals("강남식당 · 강남점 · 제육볶음",
                MealEntryPolicy.previewDiningOutTitle("강남식당", "강남점", "제육볶음"));
        assertEquals("강남식당", MealEntryPolicy.requireDiningOutStoreName(" 강남식당 "));
        assertEquals("제육볶음", MealEntryPolicy.requireDiningOutMenuName(" 제육볶음 "));
        IllegalArgumentException storeError = assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireDiningOutStoreName("   "));
        assertEquals("가게 명을 입력하세요.", storeError.getMessage());
        IllegalArgumentException menuError = assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireDiningOutMenuName(""));
        assertEquals("먹은 메뉴를 입력하세요.", menuError.getMessage());
    }

    @Test
    public void knownDiningOutBranchDefaultsApplyOnlyWhenBranchIsMissing() {
        assertEquals("영등포점", MealEntryPolicy.defaultDiningOutBranchName(
                "고향 엄마손 생바지락 칼국수"
        ));
        assertEquals("영등포점", MealEntryPolicy.resolveDiningOutBranchName(
                "고향 엄마손 칼국수",
                null
        ));
        assertEquals("영등포점", MealEntryPolicy.resolveDiningOutBranchName(
                "고향 엄마손 칼국수",
                "null"
        ));
        assertEquals("강남점", MealEntryPolicy.resolveDiningOutBranchName(
                "고향 엄마손 칼국수",
                "강남점"
        ));
        assertEquals("", MealEntryPolicy.defaultDiningOutBranchName("알 수 없는 식당"));
    }

    @Test
    public void diningOutPreviewDoesNotExposeNullLiteral() {
        assertEquals(
                "고향 엄마손 칼국수 · 얼큰 바지락 칼국수",
                MealEntryPolicy.previewDiningOutTitle(
                        "고향 엄마손 칼국수",
                        "null",
                        "얼큰 바지락 칼국수"
                )
        );
        assertTrue(MealEntryPolicy.isMissingText(null));
        assertTrue(MealEntryPolicy.isMissingText(" null "));
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

    @Test
    public void diningOutMacroEstimatesAreOptionalButCompleteWhenPresent() {
        assertEquals(Double.valueOf(70d),
                MealEntryPolicy.optionalDiningOutMacro(" 70 ", "탄수화물"));
        assertEquals(620,
                MealEntryPolicy.estimatedDiningOutCalories(70d, 40d, 20d));
        assertTrue(MealEntryPolicy.hasDiningOutEstimatedMacros(70d, 40d, 20d));
        assertFalse(MealEntryPolicy.hasDiningOutEstimatedMacros(null, null, null));
        assertEquals(0, MealEntryPolicy.estimatedDiningOutCalories(null, null, null));
    }

    @Test
    public void diningOutRecordRequiresOnlyTheThreeMacros() {
        assertEquals(Double.valueOf(70d),
                MealEntryPolicy.requireDiningOutMacro(" 70 ", "탄수화물"));
        assertEquals(Double.valueOf(40d),
                MealEntryPolicy.requireDiningOutMacro("40", "단백질"));
        assertEquals(Double.valueOf(20d),
                MealEntryPolicy.requireDiningOutMacro("20", "지방"));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireDiningOutMacro("", "지방"));
    }

    @Test
    public void diningOutMenuRequiresCaloriesAndMacrosButAllowsUnknownExtendedValues() {
        assertEquals(620, MealEntryPolicy.requireDiningOutCaloriesInput("620"));
        MealEntryPolicy.requireDiningOutMenuNutrition(
                620,
                40d,
                70d,
                20d,
                null,
                null,
                null
        );
        assertThrows(IllegalArgumentException.class, () ->
                MealEntryPolicy.requireDiningOutMenuNutrition(
                        null,
                        40d,
                        70d,
                        20d,
                        null,
                        null,
                        null
                ));
        assertThrows(IllegalArgumentException.class, () ->
                MealEntryPolicy.requireDiningOutMenuNutrition(
                        620,
                        40d,
                        null,
                        20d,
                        null,
                        null,
                        null
                ));
    }

    @Test
    public void diningOutMacroEstimatesRejectPartialOrInvalidValues() {
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.requireDiningOutEstimatedMacros(70d, 40d, null));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.optionalDiningOutMacro("-1", "지방"));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.optionalDiningOutMacro("not-a-number", "단백질"));
    }

    @Test
    public void diningOutCompleteNutritionRequiresCaloriesAndAllExtendedNutrients() {
        assertEquals(Integer.valueOf(620),
                MealEntryPolicy.optionalDiningOutCalories("620"));
        MealEntryPolicy.requireDiningOutEstimatedNutrition(
                620,
                40d,
                70d,
                20d,
                900d,
                12d,
                8d
        );
        assertTrue(MealEntryPolicy.hasDiningOutEstimatedNutrition(
                620,
                40d,
                70d,
                20d,
                900d,
                12d,
                8d
        ));
    }

    @Test
    public void diningOutCompleteNutritionRejectsPartialValues() {
        assertThrows(IllegalArgumentException.class, () ->
                MealEntryPolicy.requireDiningOutEstimatedNutrition(
                        620,
                        40d,
                        70d,
                        20d,
                        900d,
                        null,
                        8d
                ));
        assertThrows(IllegalArgumentException.class,
                () -> MealEntryPolicy.optionalDiningOutCalories("620.5"));
    }
}
