package com.yeonsik.fitnessapp.feature.exercise.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.yeonsik.fitness.shared.feature.exercise.model.BodyPart
import com.yeonsik.fitness.shared.feature.exercise.model.EquipmentType
import com.yeonsik.fitness.shared.feature.workout.model.ManualWorkoutExercise
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManualWorkoutExerciseUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun requiresAllFourFieldsAndEmitsOnlyAProvisionalDraft() {
        var confirmed: ManualWorkoutExercise? = null
        compose.setContent {
            FitnessComposeTheme(false) {
                ManualWorkoutExerciseDialog("", {}, { confirmed = it })
            }
        }
        compose.onNodeWithText("운동 추가").assertIsNotEnabled()
        compose.onNodeWithTag("manual-exercise-name").performTextInput("나만의 스쿼트")
        compose.onNodeWithText("부위: 선택").performClick()
        compose.onNodeWithText("하체").performClick()
        compose.onNodeWithText("장비: 선택").performClick()
        compose.onNodeWithText("케틀벨").performClick()
        compose.onNodeWithText("운동 추가").assertIsNotEnabled()
        compose.onNodeWithText("기록 방식: 선택").performClick()
        compose.onNodeWithText("중량 · 반복").performClick()
        compose.onNodeWithText("운동 추가").performClick()
        compose.runOnIdle {
            assertEquals(ManualWorkoutExercise("나만의 스쿼트", BodyPart.LEGS, EquipmentType.KETTLEBELL, "weight_reps"), confirmed)
            assertEquals("manual", confirmed!!.toReplacement().masterExerciseId)
            assertNull(confirmed!!.toReplacement().familyIdentity)
        }
    }
}
