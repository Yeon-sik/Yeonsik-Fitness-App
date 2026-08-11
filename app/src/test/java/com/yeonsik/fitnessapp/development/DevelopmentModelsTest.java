package com.yeonsik.fitnessapp.development;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class DevelopmentModelsTest {
    @Test
    public void bodyProfileAllowsEmptyButValidatesRecordedHeight() {
        BodyProfile empty = BodyProfile.empty();
        assertFalse(empty.isConfigured());
        assertEquals("미설정", empty.heightLabelKo());

        BodyProfile profile = new BodyProfile(178, "2026-08-01T10:00:00+09:00", "2026-08-02T10:00:00+09:00");
        assertTrue(profile.isConfigured());
        assertEquals(Integer.valueOf(178), profile.heightCm);
        assertEquals("178cm", profile.heightLabelKo());
    }

    @Test(expected = IllegalArgumentException.class)
    public void bodyProfileRejectsOutOfRangeHeight() {
        new BodyProfile(301, "", "");
    }

    @Test
    public void developmentGoalKeepsStableIdsAndKoreanLabels() {
        DevelopmentGoal goal = new DevelopmentGoal(
                DevelopmentGoal.OBJECTIVE_STRENGTH,
                4,
                DevelopmentGoal.BODY_PART_SHOULDERS,
                "2026-08-10",
                "2026-08-01T10:00:00+09:00",
                "2026-08-02T10:00:00+09:00"
        );

        assertTrue(goal.isConfigured());
        assertEquals("근력", goal.objectiveLabelKo());
        assertEquals("어깨", goal.focusBodyPartLabelKo());
        assertEquals(Integer.valueOf(4), goal.weeklySessionsTarget);
        assertEquals("2026-08-10", goal.effectiveFrom);
    }

    @Test(expected = IllegalArgumentException.class)
    public void developmentGoalRejectsOutOfRangeWeeklyTarget() {
        new DevelopmentGoal(
                DevelopmentGoal.OBJECTIVE_MUSCLE_GAIN,
                8,
                DevelopmentGoal.BODY_PART_CHEST,
                "2026-08-10",
                "",
                ""
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void developmentGoalRejectsPartialConfiguration() {
        new DevelopmentGoal(
                DevelopmentGoal.OBJECTIVE_ENDURANCE,
                null,
                DevelopmentGoal.BODY_PART_LEGS,
                "2026-08-10",
                "",
                ""
        );
    }

    @Test
    public void insightRulesStayDeterministicAndLimitedToThree() {
        List<DevelopmentInsight> insights = DevelopmentInsightRules.build(
                new DevelopmentInsightRules.Input(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 7, 27),
                        LocalDate.of(2026, 8, 9),
                        false,
                        0,
                        1,
                        1,
                        "가슴",
                        0,
                        null,
                        0,
                        4,
                        3,
                        2,
                        1,
                        null
                )
        );

        assertEquals(3, insights.size());
        assertEquals("planning", insights.get(0).category);
        assertEquals("recovery", insights.get(1).category);
        assertEquals("nutrition_logging", insights.get(2).category);
    }

    @Test
    public void focusRuleDoesNotWarnWhenFocusPartWasTrainedToday() {
        List<DevelopmentInsight> insights = DevelopmentInsightRules.build(
                new DevelopmentInsightRules.Input(
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 7, 27),
                        LocalDate.of(2026, 8, 9),
                        true,
                        1,
                        1,
                        1,
                        "가슴",
                        0,
                        "2026-08-10",
                        14,
                        0,
                        14,
                        14,
                        14,
                        80.0
                )
        );

        assertTrue(insights.isEmpty());
    }

    @Test
    public void reportBodyPartNormalizerFoldsArmAliasesAndRejectsUnknownParts() {
        assertEquals(DevelopmentGoal.BODY_PART_ARMS, DevelopmentRepository.normalizeReportBodyPart("이두"));
        assertEquals(DevelopmentGoal.BODY_PART_ARMS, DevelopmentRepository.normalizeReportBodyPart("triceps"));
        assertEquals(DevelopmentGoal.BODY_PART_ABS, DevelopmentRepository.normalizeReportBodyPart("복부"));
        assertNull(DevelopmentRepository.normalizeReportBodyPart("cardio"));
        assertNull(DevelopmentRepository.normalizeReportBodyPart(""));
    }

    @Test
    public void referenceDateParserRejectsBadFormat() {
        assertEquals(LocalDate.of(2026, 8, 10), DevelopmentRepository.requireReferenceDate("2026-08-10"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void referenceDateParserRejectsInvalidDate() {
        DevelopmentRepository.requireReferenceDate("2026/08/10");
    }

    @Test
    public void emptyGoalRemainsUnconfigured() {
        DevelopmentGoal empty = DevelopmentGoal.empty();
        assertFalse(empty.isConfigured());
        assertNull(empty.objective);
        assertEquals("미설정", empty.objectiveLabelKo());
    }
}
