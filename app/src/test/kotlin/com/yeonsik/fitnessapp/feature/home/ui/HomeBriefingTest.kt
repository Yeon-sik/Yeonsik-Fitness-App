package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.model.HomeTodayWorkoutStatus
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionGoal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotal
import com.yeonsik.fitnessapp.feature.home.model.HomeNutritionTotals
import com.yeonsik.fitnessapp.data.NutritionProfile
import org.junit.Assert.*
import org.junit.Test

class HomeBriefingTest {
    @Test fun noRecordsHasThreeMissingDomainsInFixedOrder() {
        val status = homeTodayHeroStatus(snapshot())
        assertEquals(listOf("운동", "식단", "체중"), status.domains.map { it.label })
        assertEquals(listOf("아직", "현재 : 0끼, 0g", "아직"), status.domains.map { it.value })
        assertTrue(status.domains.none { it.recorded })
        assertEquals(0, status.completedDomainCount)
        assertFalse(status.showContinue)
    }

    @Test fun mealEntriesAloneDoNotCompleteTheProteinGoal() {
        val status = homeTodayHeroStatus(snapshot().copy(mealCounts = mapOf(TODAY to 3)))
        assertEquals("현재 : 3끼, 미확인", status.domain("meal").value)
        assertEquals("목표 단백질 : 미설정", status.domain("meal").detail)
        assertFalse(status.domain("meal").recorded)
        assertEquals(0, status.completedDomainCount)
    }

    @Test fun sixtyGramsOfAHundredShowsSixtyPercentAndIsNotComplete() {
        val status = homeTodayHeroStatus(proteinSnapshot(60.0))
        val meal = status.domain("meal")
        assertEquals("현재 : 1끼, 60g (60%)", meal.value)
        assertEquals("목표 단백질 : 100g", meal.detail)
        assertEquals(0.6f, meal.progress, 0.0001f)
        assertEquals(0.2f, status.progress, 0.0001f)
        assertFalse(meal.recorded)
        assertEquals(0, status.completedDomainCount)
    }

    @Test fun goalIsCompleteAtTheThresholdAndOverflowOnlyCapsTheBar() {
        listOf(100.0, 120.0).forEach { amount ->
            val status = homeTodayHeroStatus(proteinSnapshot(amount))
            assertTrue(status.domain("meal").recorded)
            assertEquals("단백질 목표 달성", status.domain("meal").completionDescription)
            assertEquals("현재 : 1끼, ${amount.toInt()}g (${amount.toInt()}%)", status.domain("meal").value)
            assertEquals(1f, status.domain("meal").progress, 0f)
            assertEquals(1, status.completedDomainCount)
        }
    }

    @Test fun belowTheThresholdDoesNotRoundUpToACompletedPercent() {
        val meal = homeTodayHeroStatus(proteinSnapshot(99.9)).domain("meal")
        assertEquals("현재 : 1끼, 99.9g (99%)", meal.value)
        assertFalse(meal.recorded)
    }

    @Test fun changingTheConfiguredGoalUpdatesAttainmentWithoutChangingMeals() {
        val source = proteinSnapshot(60.0)
        assertFalse(homeTodayHeroStatus(source).domain("meal").recorded)
        val updated = homeTodayHeroStatus(source.copy(nutritionGoal = goal(50.0))).domain("meal")
        assertTrue(updated.recorded)
        assertEquals("현재 : 1끼, 60g (120%)", updated.value)
    }

    @Test fun missingOrInvalidGoalNeverInventsAProteinTarget() {
        listOf(null, goal(0.0), goal(-10.0), goal(Double.NaN), goal(Double.POSITIVE_INFINITY)).forEach { goal ->
            val meal = homeTodayHeroStatus(proteinSnapshot(60.0).copy(nutritionGoal = goal)).domain("meal")
            assertEquals("현재 : 1끼, 60g", meal.value)
            assertEquals("목표 단백질 : 미설정", meal.detail)
            assertFalse(meal.recorded)
            assertEquals(0f, meal.progress, 0f)
        }
    }

    @Test fun noMealWithAConfiguredGoalShowsAnActualZeroPercent() {
        val meal = homeTodayHeroStatus(snapshot().copy(nutritionGoal = goal())).domain("meal")
        assertEquals("현재 : 0끼, 0g (0%)", meal.value)
        assertEquals(0f, meal.progress, 0f)
    }

    @Test fun unknownProteinDoesNotBecomeZeroOrCompleted() {
        val source = proteinSnapshot(0.0, known = 0, missing = 1)
        val meal = homeTodayHeroStatus(source).domain("meal")
        assertEquals("현재 : 1끼, 미확인", meal.value)
        assertEquals("단백질 섭취량 미확인", meal.completionDescription)
        assertFalse(meal.recorded)
        assertEquals(0f, meal.progress, 0f)
    }

    @Test fun partialNutritionShowsAKnownLowerBoundAndOnlyProvesAttainmentWhenEnough() {
        val meal = homeTodayHeroStatus(proteinSnapshot(60.0, missing = 1)).domain("meal")
        assertEquals("현재 : 2끼, ≥60g (≥60%)", meal.value)
        assertFalse(meal.recorded)
        assertEquals(0.6f, meal.progress, 0.0001f)
        assertTrue(homeTodayHeroStatus(proteinSnapshot(100.0, missing = 1)).domain("meal").recorded)
    }

    @Test fun anotherDatesProteinDoesNotFillTodaysMealSegment() {
        val source = proteinSnapshot(120.0)
        val meal = homeTodayHeroStatus(source.copy(mealCounts = mapOf("2026-09-30" to 1),
            mealNutritionTotals = mapOf("2026-09-30" to source.mealNutritionTotals.getValue(TODAY))))
            .domain("meal")
        assertEquals("현재 : 0끼, 0g (0%)", meal.value)
        assertFalse(meal.recorded)
    }

    @Test fun currentProteinIncludesTheActualMealCount() {
        val meal = homeTodayHeroStatus(proteinSnapshot(60.0).copy(mealCounts = mapOf(TODAY to 4)))
            .domain("meal")
        assertEquals("현재 : 4끼, 60g (60%)", meal.value)
        assertEquals(4, meal.mealCount)
    }

    @Test fun weightOnlyUsesExistingMassFormatting() {
        val status = homeTodayHeroStatus(snapshot().copy(todayWeight = HomeBodyMetric("weight", TODAY, 88.4, "")))
        assertEquals("88.4 kg", status.domain("body").value)
        assertEquals(listOf(false, false, true), status.domains.map { it.recorded })
        assertEquals(1, status.completedDomainCount)
    }

    @Test fun weightRespectsPreferredMassUnit() {
        val status = homeTodayHeroStatus(snapshot().copy(todayWeight = HomeBodyMetric("weight", TODAY, 1.0, "")), MassUnit.LB)
        assertEquals("2.2 lb", status.domain("body").value)
    }

    @Test fun weightChangesHaveTheRequestedSignAndDirection() {
        listOf(80.5 to HomeHeroDetailTone.INCREASE, 79.5 to HomeHeroDetailTone.DECREASE,
            80.0 to HomeHeroDetailTone.DEFAULT).forEach { (today, tone) ->
            val body = homeTodayHeroStatus(snapshot().copy(
                todayWeight = HomeBodyMetric("today", TODAY, today, ""),
                yesterdayWeight = HomeBodyMetric("yesterday", "2026-09-30", 80.0, ""))).domain("body")
            val change = when (tone) {
                HomeHeroDetailTone.INCREASE -> "+0.5"
                HomeHeroDetailTone.DECREASE -> "-0.5"
                HomeHeroDetailTone.DEFAULT -> "0"
            }
            assertEquals("어제보다 $change kg", body.detail)
            assertEquals(tone, body.detailTone)
            assertTrue(body.recorded)
            assertTrue(body.accessibilityValue.contains(body.detail!!))
        }
    }

    @Test fun missingOrDifferentDatesWeightNeverBecomesYesterday() {
        listOf(null, HomeBodyMetric("old", "2026-09-29", 80.0, ""),
            HomeBodyMetric("invalid", "2026-09-30", Double.NaN, "")).forEach { previous ->
            val body = homeTodayHeroStatus(snapshot().copy(
                todayWeight = HomeBodyMetric("today", TODAY, 80.5, ""), yesterdayWeight = previous)).domain("body")
            assertEquals("어제 기록 없음", body.detail)
            assertEquals(HomeHeroDetailTone.DEFAULT, body.detailTone)
        }
        assertNull(homeTodayHeroStatus(snapshot().copy(
            yesterdayWeight = HomeBodyMetric("yesterday", "2026-09-30", 80.0, ""))).domain("body").detail)
    }

    @Test fun weightDeltaUsesThePreferredUnitAndDoesNotShowSignedZero() {
        val source = snapshot().copy(todayWeight = HomeBodyMetric("today", TODAY, 80.5, ""),
            yesterdayWeight = HomeBodyMetric("yesterday", "2026-09-30", 80.0, ""))
        assertEquals("어제보다 +1.1 lb", homeTodayHeroStatus(source, MassUnit.LB).domain("body").detail)
        listOf(80.01, 79.99).forEach { amount ->
            val body = homeTodayHeroStatus(source.copy(todayWeight = source.todayWeight!!.copy(weightKg = amount)))
                .domain("body")
            assertEquals("어제보다 0 kg", body.detail)
            assertEquals(HomeHeroDetailTone.DEFAULT, body.detailTone)
        }
    }

    @Test fun strengthUsesCompletedSetProjectionLabels() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "이두")))
        assertEquals("등 · 이두", status.domain("workout").value)
        assertEquals("0 kg", status.domain("workout").detail)
        assertTrue(status.domain("workout").recorded)
    }

    @Test fun completedStrengthWithoutProjectionFallsBackToComplete() {
        val status = workoutStatus(HomeTodayWorkoutStatus(hasCompletedWorkout = true, hasCompletedStrength = true))
        assertEquals("완료", status.domain("workout").value)
    }

    @Test fun manyMusclesAreCompactWithFullAccessibleDescription() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "가슴", "이두", "삼두")))
        assertEquals("등 · 가슴 외 2", status.domain("workout").value)
        assertEquals("등 · 가슴 · 이두 · 삼두 · 총 운동량 0 kg", status.domain("workout").accessibilityValue)
        assertEquals("0 kg", status.domain("workout").detail)
    }

    @Test fun cardioOnlyShowsMinutesWithoutStrengthMetrics() {
        val status = workoutStatus(HomeTodayWorkoutStatus(hasCompletedWorkout = true,
            hasCompletedCardio = true, cardioDurationSeconds = 1_979))
        assertEquals("유산소 32분", status.domain("workout").value)
        assertNull(status.domain("workout").detail)
    }

    @Test fun mixedStrengthAndCardioKeepVolumeAndCardioBelowTheMuscles() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "이두"), true, 1_920))
        assertEquals("등 · 이두", status.domain("workout").value)
        assertEquals("0 kg", status.domain("workout").detail)
        assertEquals("유산소 32분", status.domain("workout").additionalDetail)
        assertEquals(1, status.completedDomainCount)
        assertEquals(3, status.domains.size)
    }

    @Test fun activeSessionKeepsContinueWithoutImplyingCompletion() {
        val status = homeTodayHeroStatus(snapshot().copy(inProgressSessionId = "active"))
        assertEquals("진행 중", status.domain("workout").value)
        assertFalse(status.domain("workout").recorded)
        assertEquals(0, status.completedDomainCount)
        assertTrue(status.showContinue)
    }

    @Test fun activeSessionRetainsTodaysCompletedFacts() {
        val status = homeTodayHeroStatus(snapshot().copy(inProgressSessionId = "active",
            todayWorkoutStatus = HomeTodayWorkoutStatus(true, true, listOf("등", "이두"), true, 1_920)))
        assertEquals("진행 중", status.domain("workout").value)
        assertEquals("등 · 이두 · 유산소 32분", status.domain("workout").detail)
        assertEquals("진행 중, 오늘 완료: 등 · 이두 · 총 운동량 0 kg · 유산소 32분", status.domain("workout").accessibilityValue)
        assertEquals("0 kg", status.domain("workout").additionalDetail)
        assertTrue(status.domain("workout").recorded)
        assertTrue(status.showContinue)
    }

    @Test fun nonCompletedMetricsAndOtherDatesDoNotFillDomains() {
        val status = homeTodayHeroStatus(snapshot().copy(
            todaySessions = listOf("not-completed"),
            dayMetrics = mapOf(TODAY to HomeDayWorkoutMetrics(1, 18, 12_400.0, 4_080)),
            mealCounts = mapOf("2026-09-30" to 3)))
        assertEquals("아직", status.domain("workout").value)
        assertNull(status.domain("workout").detail)
        assertNull(status.domain("workout").additionalDetail)
        assertEquals("현재 : 0끼, 0g", status.domain("meal").value)
        assertEquals(0, status.completedDomainCount)
    }

    @Test fun completedVolumeUsesOnlyTheCompletedProjectionAndKeepsItsUnit() {
        val source = snapshot().copy(todayWorkoutStatus = HomeTodayWorkoutStatus(true, true,
            listOf("등", "이두"), completedStrengthVolumeKg = 1_850.5),
            dayMetrics = mapOf(TODAY to HomeDayWorkoutMetrics(2, 18, 12_400.0, 4_080)))
        assertEquals("1,850.5 kg", homeTodayHeroStatus(source).domain("workout").detail)
        assertEquals("4,079.7 lb", homeTodayHeroStatus(source, MassUnit.LB).domain("workout").detail)
    }

    private fun workoutStatus(workout: HomeTodayWorkoutStatus) =
        homeTodayHeroStatus(snapshot().copy(todayWorkoutStatus = workout))

    private fun HomeTodayHeroStatus.domain(key: String) = domains.single { it.key == key }

    private fun proteinSnapshot(amount: Double, known: Int = 1, missing: Int = 0) = snapshot().copy(
        mealCounts = mapOf(TODAY to known + missing), nutritionGoal = goal(),
        mealNutritionTotals = mapOf(TODAY to HomeNutritionTotals(known + missing,
            mapOf(NutritionProfile.PROTEIN_GRAMS to HomeNutritionTotal(amount, known, missing)))))

    private fun goal(protein: Double = 100.0) = HomeNutritionGoal("maintenance", 2000.0,
        protein, 250.0, 60.0, 25.0, 2000.0, 2000)

    private fun snapshot() = HomeSnapshot("owner", TODAY, emptyList(), null, emptyList(), emptyMap(),
        emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())

    private companion object { const val TODAY = "2026-10-01" }
}
