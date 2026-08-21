package com.yeonsik.fitnessapp.development;

import org.junit.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PaperAdviceEngineTest {
    private final PaperAdviceEngine engine = new PaperAdviceEngine();

    @Test
    public void doesNotInferAdviceWhenThereIsNoRecentData() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .referenceDate(LocalDate.of(2026, 8, 14)).goal("hypertrophy")
                .recentDataDays(0).build());
        assertEquals(1, advice.size());
        assertEquals(PaperAdvice.Status.INSUFFICIENT_DATA, advice.get(0).status);
        assertEquals("DATA_COVERAGE_001", advice.get(0).adviceId);
    }

    @Test
    public void returnsSleepAndProteinAdviceWithPaperReferences() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .referenceDate(LocalDate.of(2026, 8, 14)).goal("hypertrophy")
                .sleepHours(6.2).sleepRecordedDays(3)
                .proteinGPerKg(1.2).proteinRecordedDays(7)
                .resistanceTrainingSessionsPerWeek(2)
                .recentDataDays(7).build());
        assertEquals(2, advice.size());
        assertEquals("REC_SLEEP_001", advice.get(0).adviceId);
        assertTrue(advice.get(0).evidenceRefs.contains("09#1"));
        assertEquals("NUT_PRO_001", advice.get(1).adviceId);
        assertTrue(advice.get(1).evidenceRefs.contains("08#1"));
    }

    @Test
    public void safetyGateSuppressesNormalRecommendationsWhenPainIsReported() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .goal("hypertrophy").sleepHours(6.0).proteinGPerKg(1.2)
                .painReported(true).recentDataDays(7).build());
        assertEquals(1, advice.size());
        assertEquals(PaperAdvice.Status.SAFETY_REVIEW, advice.get(0).status);
        assertEquals("SAFETY_PAIN_001", advice.get(0).adviceId);
    }

    @Test
    public void flagsColdWaterTradeoffOnlyForAdaptationGoals() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .goal("hypertrophy").coldWaterImmediatelyPostResistance(true)
                .recentDataDays(7).build());
        assertEquals(1, advice.size());
        assertEquals("REC_COLD_003", advice.get(0).adviceId);
        assertEquals(PaperAdvice.Status.INFORMATIONAL, advice.get(0).status);
        assertTrue(advice.get(0).evidenceRefs.contains("09#10"));
    }

    @Test
    public void flagsHighFailureRatioForStrengthGoals() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .goal("max_strength").failureSetsRatio(0.75).recentDataDays(7).build());
        assertEquals(1, advice.size());
        assertEquals("TRAIN_FAIL_001", advice.get(0).adviceId);
        assertTrue(advice.get(0).evidenceRefs.contains("02#3"));
    }

    @Test
    public void flagsFastLossOnlyWhenTwoWeightWindowsAreReady() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .goal("fat_loss")
                .currentWeight7DayAverageKg(78.8).currentWeightRecordedDays(4)
                .previousWeight7DayAverageKg(80.0).previousWeightRecordedDays(4)
                .weeklyWeightChangePct(-1.5)
                .recentDataDays(8)
                .build());

        assertEquals(1, advice.size());
        assertEquals("FAT_RATE_003", advice.get(0).adviceId);
        assertTrue(advice.get(0).evidenceRefs.contains("03#6"));
    }

    @Test
    public void doesNotTreatSparseSleepOrProteinAsACompletedObservationWindow() {
        List<PaperAdvice> advice = engine.evaluate(PaperAdviceInput.builder()
                .goal("hypertrophy")
                .sleepHours(5.5).sleepRecordedDays(2)
                .proteinGPerKg(1.1).proteinRecordedDays(6)
                .recentDataDays(6)
                .build());

        assertTrue(advice.isEmpty());
    }
}
