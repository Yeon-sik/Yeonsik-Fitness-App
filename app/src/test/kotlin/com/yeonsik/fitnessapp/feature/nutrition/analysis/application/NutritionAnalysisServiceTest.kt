package com.yeonsik.fitnessapp.feature.nutrition.analysis.application

import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotComponentRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotConsumptionRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotItemRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotNutritionRead
import com.yeonsik.fitnessapp.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisTarget
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
        sodium: Double? = null
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
        micronutrients = Collections.emptyMap()
    )
}
