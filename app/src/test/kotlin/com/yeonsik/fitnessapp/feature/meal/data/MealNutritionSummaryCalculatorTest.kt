package com.yeonsik.fitnessapp.feature.meal.data

import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotNutritionRead
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.development.PaperAdvice
import com.yeonsik.fitnessapp.development.PaperAdviceEngine
import com.yeonsik.fitnessapp.development.PaperAdviceInput
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MealNutritionSummaryCalculatorTest {
    private val start = LocalDate.of(2026, 10, 1)
    private val end = start.plusDays(6)

    @Test fun allUnknownDoesNotBecomeZeroOrActionable() {
        val summary = summary(List(7) { null })
        assertNull(summary.proteinGrams)
        assertEquals(7, summary.recordedDays)
        assertEquals(0, summary.proteinKnownMealCount)
        assertEquals(7, summary.proteinMissingMealCount)
        assertEquals(0, summary.proteinCompleteDays)
        assertTrue(advice(summary).isEmpty())
    }

    @Test fun partialCoverageRetainsKnownSubtotalAndBlocksDeficiencyAdvice() {
        val summary = summary(listOf(20.0, null, 20.0, 20.0, 20.0, 20.0, 20.0))
        assertEquals(120.0, summary.proteinGrams!!, 0.0)
        assertEquals(6, summary.proteinKnownMealCount)
        assertEquals(1, summary.proteinMissingMealCount)
        assertEquals(6, summary.proteinCompleteDays)
        assertTrue(advice(summary).isEmpty())
    }

    @Test fun realZeroIsKnownAndCanBeReviewedWithCompleteCoverage() {
        val summary = summary(List(7) { 0.0 })
        assertEquals(0.0, summary.proteinGrams!!, 0.0)
        assertEquals(7, summary.proteinKnownMealCount)
        assertEquals(0, summary.proteinMissingMealCount)
        assertEquals(PaperAdvice.Status.ACTIONABLE, advice(summary).single().status)
    }

    @Test fun estimatedValuesRemainEstimatedAndInformational() {
        val summary = summary(List(7) { 20.0 }, "{\"nutrition_status\" : \"estimated\"}")
        assertEquals(7, summary.proteinEstimatedMealCount)
        assertEquals(7, summary.estimatedMealCount)
        val advice = advice(summary).single()
        assertEquals(PaperAdvice.Status.INFORMATIONAL, advice.status)
        assertTrue(advice.observationKo.contains("추정"))
    }

    @Test fun mixedEstimatedAndRecordedValuesPreserveCounts() {
        val rows = snapshots(List(7) { 20.0 }).toMutableList()
        rows[0] = rows[0].copy(metadata = "{\"estimated\" : true}")
        val summary = MealNutritionSummaryCalculator.calculate(start, end, rows)
        assertEquals(1, summary.proteinEstimatedMealCount)
        assertEquals(PaperAdvice.Status.INFORMATIONAL, advice(summary).single().status)
    }

    @Test fun explicitUnknownProvenanceCannotProduceActionableAdvice() {
        val summary = summary(List(7) { 0.0 }, "{\"nutrition_status\":\"unknown\"}")
        assertNull(summary.proteinGrams)
        assertEquals(7, summary.proteinUnknownProvenanceMealCount)
        assertTrue(advice(summary).isEmpty())
    }

    @Test fun legacySummaryWithoutCoverageCannotProduceActionableAdvice() {
        assertTrue(advice(com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary(0.0, 7, 7, 0)).isEmpty())
    }

    private fun summary(values: List<Double?>, metadata: String = "{}") =
        MealNutritionSummaryCalculator.calculate(start, end, snapshots(values, metadata))

    private fun snapshots(values: List<Double?>, metadata: String = "{}") = values.mapIndexed { index, value ->
        MealSnapshotRead("meal-$index", start.plusDays(index.toLong()).toString(), "simple", metadata,
            emptyList(), MealSnapshotNutritionRead(null, value, null, null, null, null, null,
                null, null, null, null, emptyMap()))
    }

    private fun advice(summary: com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary): List<PaperAdvice> =
        PaperAdviceEngine().evaluate(PaperAdviceInput.builder().goal("hypertrophy")
            .bodyWeightKg(80.0).proteinGPerKg(summary.proteinGrams?.div(7)?.div(80))
            .proteinRecordedDays(summary.recordedDays).proteinEvidence(summary)
            .resistanceTrainingSessionsPerWeek(2).recentDataDays(7).build())
            .filter { it.adviceId == "NUT_PRO_001" }
}
