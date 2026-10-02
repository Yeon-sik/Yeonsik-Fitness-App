package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.feature.home.model.HomeBodyMetric
import com.yeonsik.fitnessapp.feature.home.model.HomeDayWorkoutMetrics
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.model.HomeTodayWorkoutStatus
import org.junit.Assert.*
import org.junit.Test

class HomeBriefingTest {
    @Test fun noRecordsHasThreeMissingDomainsInFixedOrder() {
        val status = homeTodayHeroStatus(snapshot())
        assertEquals(listOf("운동", "식단", "체중"), status.domains.map { it.label })
        assertEquals(listOf("아직", "아직", "아직"), status.domains.map { it.value })
        assertTrue(status.domains.none { it.recorded })
        assertEquals(0, status.completedDomainCount)
        assertFalse(status.showContinue)
    }

    @Test fun mealOnlyCountsAsOneDomainRegardlessOfEntryCount() {
        val status = homeTodayHeroStatus(snapshot().copy(mealCounts = mapOf(TODAY to 3)))
        assertEquals("3회", status.domain("meal").value)
        assertEquals(listOf(false, true, false), status.domains.map { it.recorded })
        assertEquals(1, status.completedDomainCount)
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

    @Test fun strengthUsesCompletedSetProjectionLabels() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "이두")))
        assertEquals("등 · 이두", status.domain("workout").value)
        assertNull(status.domain("workout").detail)
        assertTrue(status.domain("workout").recorded)
    }

    @Test fun completedStrengthWithoutProjectionFallsBackToComplete() {
        val status = workoutStatus(HomeTodayWorkoutStatus(hasCompletedWorkout = true, hasCompletedStrength = true))
        assertEquals("완료", status.domain("workout").value)
    }

    @Test fun manyMusclesAreCompactWithFullAccessibleDescription() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "가슴", "이두", "삼두")))
        assertEquals("등 · 가슴 외 2", status.domain("workout").value)
        assertEquals("등 · 가슴 · 이두 · 삼두", status.domain("workout").accessibilityValue)
        assertNull(status.domain("workout").detail)
    }

    @Test fun cardioOnlyShowsMinutesWithoutStrengthMetrics() {
        val status = workoutStatus(HomeTodayWorkoutStatus(hasCompletedWorkout = true,
            hasCompletedCardio = true, cardioDurationSeconds = 1_979))
        assertEquals("유산소 32분", status.domain("workout").value)
        assertNull(status.domain("workout").detail)
    }

    @Test fun mixedStrengthAndCardioHaveTwoLinesAndOneRecordedDomain() {
        val status = workoutStatus(HomeTodayWorkoutStatus(true, true, listOf("등", "이두"), true, 1_920))
        assertEquals("등 · 이두", status.domain("workout").value)
        assertEquals("유산소 32분", status.domain("workout").detail)
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
        assertEquals("진행 중, 오늘 완료: 등 · 이두 · 유산소 32분", status.domain("workout").accessibilityValue)
        assertTrue(status.domain("workout").recorded)
        assertTrue(status.showContinue)
    }

    @Test fun nonCompletedMetricsAndOtherDatesDoNotFillDomains() {
        val status = homeTodayHeroStatus(snapshot().copy(
            todaySessions = listOf("not-completed"),
            dayMetrics = mapOf(TODAY to HomeDayWorkoutMetrics(1, 18, 12_400.0, 4_080)),
            mealCounts = mapOf("2026-09-30" to 3)))
        assertEquals("아직", status.domain("workout").value)
        assertEquals("아직", status.domain("meal").value)
        assertEquals(0, status.completedDomainCount)
    }

    private fun workoutStatus(workout: HomeTodayWorkoutStatus) =
        homeTodayHeroStatus(snapshot().copy(todayWorkoutStatus = workout))

    private fun HomeTodayHeroStatus.domain(key: String) = domains.single { it.key == key }

    private fun snapshot() = HomeSnapshot("owner", TODAY, emptyList(), null, emptyList(), emptyMap(),
        emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null, null, emptyList(), emptyList())

    private companion object { const val TODAY = "2026-10-01" }
}
