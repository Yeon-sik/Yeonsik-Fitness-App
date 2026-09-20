package com.yeonsik.fitnessapp.feature.nutrition.analysis.application

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.data.AthleteNutritionGoal
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.meal.model.MealNutritionReadSummary
import com.yeonsik.fitness.shared.feature.meal.model.MealReadNutritionTotals
import com.yeonsik.fitness.shared.feature.meal.model.MealReadSummary
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotComponentRead
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotConsumptionRead
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotItemRead
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotNutritionRead
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisTarget
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionDataQuality
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionTargetStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class NutritionAnalysisServiceTest {
    @Test
    fun simpleAndComplexSnapshotsAggregateWithoutTurningUnknownIntoZero() {
        val simple = snapshot(
            id = "simple",
            date = "2026-09-14",
            metadata = "{\"menu_name\":\"닭가슴살\",\"nutrition_status\":\"recorded\"}",
            item = item(
                id = "simple-item",
                name = "닭가슴살",
                nutrition = nutrition(100.0, 10.0, 5.0, 2.0, sodium = 30.0)
            )
        )
        val complex = snapshot(
            id = "complex",
            date = "2026-09-15",
            metadata = "{\"menu_name\":\"세트 메뉴\",\"estimated\":true,\"nutrition_status\":\"estimated\"}",
            item = item(
                id = "complex-item",
                name = "세트 메뉴",
                nutrition = nutrition(200.0, 20.0, 10.0, 4.0),
                consumption = MealSnapshotConsumptionRead(2, 0.5, "manual", "estimated"),
                components = listOf(
                    component(
                        id = "side",
                        name = "사이드",
                        nutrition = nutrition(50.0, 2.0, 3.0, 1.0, sodium = 100.0),
                        consumedFraction = 0.5
                    )
                )
            )
        )
        val target = NutritionAnalysisTarget(
            phase = "maintenance",
            dailyValues = mapOf(
                NutritionProfile.CALORIES_KCAL to 200.0,
                NutritionProfile.PROTEIN_GRAMS to 20.0,
                NutritionProfile.CARBS_GRAMS to 20.0,
                NutritionProfile.FAT_GRAMS to 10.0,
                NutritionProfile.FIBER_GRAMS to 10.0,
                NutritionProfile.SODIUM_MG to 200.0
            )
        )

        val report = NutritionAnalysisCalculator.calculate(
            java.time.LocalDate.parse("2026-09-14"),
            java.time.LocalDate.parse("2026-09-15"),
            listOf(simple, complex),
            target
        )

        assertEquals(225.0, report.metric(NutritionProfile.CALORIES_KCAL)?.knownSum ?: -1.0, 0.001)
        assertEquals(21.0, report.metric(NutritionProfile.PROTEIN_GRAMS)?.knownSum ?: -1.0, 0.001)
        val sodium = report.metric(NutritionProfile.SODIUM_MG)
        assertEquals(80.0, sodium?.knownSum ?: -1.0, 0.001)
        assertFalse(sodium?.isComplete == true)
        assertEquals("≥80 (미상 1건)", sodium?.displayValue())
        assertEquals(NutritionTargetStatus.UNKNOWN_ACTUAL, report.comparison(NutritionProfile.SODIUM_MG)?.status)
        assertEquals(1, report.metric(NutritionProfile.CALORIES_KCAL)?.estimatedCount)
        assertEquals(1, report.metric(NutritionProfile.CALORIES_KCAL)?.recordedCount)
        assertEquals(NutritionTargetStatus.BELOW_TARGET, report.comparison(NutritionProfile.CALORIES_KCAL)?.status)
        assertEquals(2, report.proteinDistribution.knownMealCount)
        assertTrue(report.proteinDistribution.isComplete)
    }

    @Test
    fun emptyAndSinglePointAnalysisExposeUnknownAndNoTargetSafely() {
        val report = NutritionAnalysisCalculator.calculate(
            java.time.LocalDate.parse("2026-09-15"),
            java.time.LocalDate.parse("2026-09-15"),
            emptyList(),
            null
        )

        assertEquals(1, report.calendarDayCount)
        assertEquals("?", report.metric(NutritionProfile.PROTEIN_GRAMS)?.displayValue())
        assertNull(report.comparison(NutritionProfile.PROTEIN_GRAMS))
        assertEquals(0, report.proteinDistribution.knownMealCount)
        assertFalse(report.proteinDistribution.isComplete)
    }

    @Test
    fun detailedNutrientsKeepMixedProvenanceExplicit() {
        val recorded = snapshot(
            id = "recorded-detail",
            date = "2026-09-14",
            metadata = "{\"nutrition_status\":\"recorded\"}",
            item = item(
                id = "recorded-detail-item",
                name = "recorded meal",
                nutrition = nutrition(
                    calories = 100.0,
                    protein = 10.0,
                    carbs = 5.0,
                    fat = 2.0,
                    micronutrients = mapOf("iron" to 1.5)
                )
            )
        )
        val estimated = snapshot(
            id = "estimated-detail",
            date = "2026-09-15",
            metadata = "{\"estimated\":true}",
            item = item(
                id = "estimated-detail-item",
                name = "estimated meal",
                nutrition = nutrition(
                    calories = 200.0,
                    protein = 20.0,
                    carbs = 10.0,
                    fat = 4.0,
                    micronutrients = mapOf("iron" to 2.5)
                )
            )
        )

        val report = NutritionAnalysisCalculator.calculate(
            java.time.LocalDate.parse("2026-09-14"),
            java.time.LocalDate.parse("2026-09-15"),
            listOf(recorded, estimated),
            null
        )

        val calories = report.metric(NutritionProfile.CALORIES_KCAL)
        assertEquals(NutritionDataQuality.MIXED, calories?.dataQuality)
        assertEquals(1, calories?.recordedCount)
        assertEquals(1, calories?.estimatedCount)
        val iron = report.metric("iron")
        assertEquals(4.0, iron?.knownSum ?: -1.0, 0.001)
        assertEquals(2, iron?.knownCount)
        assertEquals(0, iron?.missingCount)
        assertEquals("mg", iron?.unit)
        assertEquals(NutritionDataQuality.MIXED, iron?.dataQuality)
        assertEquals(1, report.estimatedMealCount)
    }

    @Test
    fun unknownMetadataNeverBecomesZeroAndMalformedMetadataDoesNotCrash() {
        val unknown = snapshot(
            id = "unknown",
            date = "2026-09-14",
            metadata = "{\"nutrition_status\":\"unknown\"}",
            item = item(
                id = "unknown-item",
                name = "unknown meal",
                nutrition = nutrition(100.0, 10.0, 5.0, 2.0)
            )
        )
        val malformed = snapshot(
            id = "malformed",
            date = "2026-09-15",
            metadata = "{not-json",
            item = item(
                id = "malformed-item",
                name = "malformed meal",
                nutrition = nutrition(50.0, 5.0, 2.0, 1.0, sodium = 20.0)
            )
        )

        val report = NutritionAnalysisCalculator.calculate(
            java.time.LocalDate.parse("2026-09-14"),
            java.time.LocalDate.parse("2026-09-15"),
            listOf(unknown, malformed),
            null
        )

        assertEquals(NutritionDataQuality.UNKNOWN, report.meals[0].dataQuality)
        assertEquals(NutritionDataQuality.RECORDED, report.meals[1].dataQuality)
        val sodium = report.metric(NutritionProfile.SODIUM_MG)
        assertEquals(20.0, sodium?.knownSum ?: -1.0, 0.001)
        assertEquals(1, sodium?.missingCount)
        assertEquals(1, sodium?.unknownProvenanceCount)
        assertFalse(sodium?.isComplete == true)
        assertEquals(NutritionDataQuality.MIXED, sodium?.dataQuality)
        assertEquals(150.0, report.metric(NutritionProfile.CALORIES_KCAL)?.knownSum ?: -1.0, 0.001)
    }
    @Test
    fun serviceForwardsAccountScopeAndScalesTargetsAcrossPeriod() {
        val scope = AccountScope("owner-a")
        val mealApi = RecordingMealReadApi(
            listOf(
                snapshot(
                    id = "period-meal",
                    date = "2026-09-14",
                    metadata = "",
                    item = item(
                        id = "period-meal-item",
                        name = "period meal",
                        nutrition = nutrition(400.0, 40.0, 20.0, 10.0, sodium = 100.0)
                    )
                )
            )
        )
        val recoveryApi = RecordingRecoveryApi(
            AthleteNutritionGoal("maintenance", 100.0, 10.0, 20.0, 5.0, 1.0, 50.0, 2000)
        )

        val report = NutritionAnalysisService(mealApi, recoveryApi).analyze(
            scope,
            " 2026-09-14 ",
            "2026-09-15"
        )

        assertEquals(scope, mealApi.requestedScope)
        assertEquals("2026-09-14", mealApi.requestedStartDate)
        assertEquals("2026-09-15", mealApi.requestedEndDate)
        assertEquals(scope, recoveryApi.requestedScope)
        assertEquals(2, report.calendarDayCount)
        assertEquals(1, report.recordedDays)
        val calories = report.comparison(NutritionProfile.CALORIES_KCAL)
        assertEquals(200.0, calories?.targetValue ?: -1.0, 0.001)
        assertEquals(2, calories?.targetDays)
        assertEquals(2.0, calories?.ratio ?: -1.0, 0.001)
        assertEquals(200.0, calories?.delta ?: 0.0, 0.001)
        assertEquals(NutritionTargetStatus.ABOVE_TARGET, calories?.status)
    }
    private fun snapshot(
        id: String,
        date: String,
        metadata: String,
        item: MealSnapshotItemRead
    ) = MealSnapshotRead(
        id = id,
        date = date,
        mealKind = "food",
        metadata = metadata,
        items = listOf(item)
    )

    private fun item(
        id: String,
        name: String,
        nutrition: MealSnapshotNutritionRead,
        consumption: MealSnapshotConsumptionRead? = null,
        components: List<MealSnapshotComponentRead> = emptyList()
    ) = MealSnapshotItemRead(
        id = id,
        foodId = null,
        foodName = name,
        brand = null,
        manufacturerName = null,
        brandName = null,
        subBrandName = null,
        productName = null,
        packageAmount = null,
        packageUnit = null,
        packageCount = null,
        foodKind = null,
        quantity = 1.0,
        unit = "serving",
        basisAmount = 1.0,
        basisUnit = "serving",
        prepState = null,
        sourceType = null,
        sourceReference = null,
        sourceVersion = null,
        foodDataVersion = null,
        templateId = null,
        templateRevision = null,
        portionBasis = null,
        nominalServings = null,
        nutrition = nutrition,
        components = components,
        consumption = consumption
    )

    private fun component(
        id: String,
        name: String,
        nutrition: MealSnapshotNutritionRead,
        consumedFraction: Double
    ) = MealSnapshotComponentRead(
        id = id,
        foodId = null,
        foodName = name,
        brand = null,
        foodKind = null,
        quantity = 1.0,
        unit = "serving",
        basisAmount = 1.0,
        basisUnit = "serving",
        prepState = null,
        groupKey = "side",
        groupType = "side",
        provisionType = "paid",
        role = "optional",
        memberId = null,
        consumedFraction = consumedFraction,
        sourceType = null,
        sourceReference = null,
        sourceVersion = null,
        foodDataVersion = null,
        nutrition = nutrition
    )

    private fun nutrition(
        calories: Double?,
        protein: Double?,
        carbs: Double?,
        fat: Double?,
        sodium: Double? = null,
        micronutrients: Map<String, Double> = Collections.emptyMap()
    ) = MealSnapshotNutritionRead(
        calories = calories,
        proteinGrams = protein,
        carbsGrams = carbs,
        fatGrams = fat,
        sodiumMg = sodium,
        saturatedFatGrams = null,
        sugarsGrams = null,
        fiberGrams = null,
        addedSugarsGrams = null,
        transFatGrams = null,
        cholesterolMg = null,
        micronutrients = micronutrients
    )
    private class RecordingMealReadApi(
        private val values: List<MealSnapshotRead>
    ) : MealReadApi {
        var requestedScope: AccountScope? = null
        var requestedStartDate: String? = null
        var requestedEndDate: String? = null

        override fun mealCount(scope: AccountScope, date: String): Int = error("unused")

        override fun mealTotals(scope: AccountScope, date: String): MealReadNutritionTotals =
            error("unused")

        override fun meals(scope: AccountScope, date: String): List<MealReadSummary> =
            error("unused")

        override fun recordedDays(scope: AccountScope, startDate: String, endDate: String): Int =
            error("unused")

        override fun dates(scope: AccountScope, startDate: String, endDate: String): List<String> =
            error("unused")

        override fun nutritionSummary(
            scope: AccountScope,
            startDate: String,
            endDate: String
        ): MealNutritionReadSummary = error("unused")

        override fun mealSnapshots(
            scope: AccountScope,
            startDate: String,
            endDate: String
        ): List<MealSnapshotRead> {
            requestedScope = scope
            requestedStartDate = startDate
            requestedEndDate = endDate
            return values
        }
    }

    private class RecordingRecoveryApi(
        private val value: AthleteNutritionGoal?
    ) : RecoveryRepositoryApi {
        var requestedScope: AccountScope? = null

        override fun nutritionGoal(scope: AccountScope): AthleteNutritionGoal? {
            requestedScope = scope
            return value
        }

        override fun saveNutritionGoal(scope: AccountScope, goal: AthleteNutritionGoal) {
            error("unused")
        }

        override fun checkIn(scope: AccountScope, date: String): AthleteDailyCheckIn =
            error("unused")

        override fun saveCheckIn(scope: AccountScope, checkIn: AthleteDailyCheckIn) {
            error("unused")
        }
    }
}
