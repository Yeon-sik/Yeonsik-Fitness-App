package com.yeonsik.fitnessapp.feature.workout.data

import com.yeonsik.fitnessapp.core.database.WorkoutRoomDao
import org.junit.Assert.assertEquals
import org.junit.Test

class FitnessSummaryProjectionMuscleLabelsTest {
    @Test
    fun mapsPositiveCompletedSetsToV2BodyAreasInContractOrder() {
        val rows = listOf(
            WorkoutRoomDao.SummarySetCount("팔", "biceps", 2),
            WorkoutRoomDao.SummarySetCount("가슴", "mid_chest", 0),
            WorkoutRoomDao.SummarySetCount("어깨", "side_delts", 1),
            WorkoutRoomDao.SummarySetCount("팔", "triceps", 1),
            WorkoutRoomDao.SummarySetCount("팔", "forearms", 4),
            WorkoutRoomDao.SummarySetCount("등", "lats", 2),
            WorkoutRoomDao.SummarySetCount("가슴", "overall_chest", 1)
        )

        assertEquals(
            listOf("가슴", "등", "어깨", "삼두", "이두"),
            rows.toFitnessSummaryProjectionMuscleLabels()
        )
    }
}
