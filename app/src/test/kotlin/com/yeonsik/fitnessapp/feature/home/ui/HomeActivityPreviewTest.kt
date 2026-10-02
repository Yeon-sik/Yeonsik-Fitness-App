package com.yeonsik.fitnessapp.feature.home.ui

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import com.yeonsik.fitnessapp.data.MassFormatter
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityDayDetails
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityKind
import com.yeonsik.fitnessapp.feature.home.model.HomeActivityRecordSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeActivityPreviewTest {
    @Test fun previewUsesDomainOrderAndSummarizesRecordsWithoutDetailNames() {
        val details = HomeActivityDayDetails(
            "2026-09-30",
            listOf(
                HomeActivityRecordSummary(
                    HomeActivityKind.EXERCISE,
                    name = "상체 루틴",
                    category = "등 · 가슴",
                    workoutType = "strength"
                ),
                HomeActivityRecordSummary(
                    HomeActivityKind.EXERCISE,
                    name = "팔 루틴",
                    category = "이두",
                    workoutType = "strength"
                ),
                HomeActivityRecordSummary(
                    HomeActivityKind.EXERCISE,
                    name = "아침 달리기",
                    workoutType = "cardio",
                    durationSeconds = 900
                ),
                HomeActivityRecordSummary(
                    HomeActivityKind.EXERCISE,
                    name = "저녁 걷기",
                    workoutType = "cardio",
                    durationSeconds = 1_020
                ),
                HomeActivityRecordSummary(HomeActivityKind.MEAL, name = "현미밥", category = "점심"),
                HomeActivityRecordSummary(HomeActivityKind.MEAL, name = "닭가슴살", category = "저녁"),
                HomeActivityRecordSummary(HomeActivityKind.WEIGHT, weightKg = 88.4),
                HomeActivityRecordSummary(HomeActivityKind.WEIGHT, weightKg = 89.1)
            )
        )

        val rows = homeActivityPreviewRows(details, MassUnit.KG)

        assertEquals(
            listOf(HomeActivityKind.EXERCISE, HomeActivityKind.MEAL, HomeActivityKind.WEIGHT),
            rows.map { it.kind }
        )
        assertEquals("등 · 가슴 외 1 · 유산소 32분", rows[0].summary)
        assertEquals("2회", rows[1].summary)
        assertEquals(MassFormatter.withUnit(88.4, MassUnit.KG), rows[2].summary)
        assertFalse(rows.joinToString { it.summary }.contains("상체 루틴"))
        assertFalse(rows.joinToString { it.summary }.contains("현미밥"))
    }

    @Test fun noDomainFactsProducesNoRows() {
        assertEquals(emptyList<HomeActivityPreviewRow>(), homeActivityPreviewRows(
            HomeActivityDayDetails("2026-09-30", emptyList()), MassUnit.KG
        ))
    }
}
