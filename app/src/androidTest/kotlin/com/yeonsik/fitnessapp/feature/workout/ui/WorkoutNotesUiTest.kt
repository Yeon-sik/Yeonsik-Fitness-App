package com.yeonsik.fitnessapp.feature.workout.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutNotesUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedSaveKeepsTheDraftAndUnrelatedRefreshDoesNotOverwriteIt() {
        val source = mutableStateOf("시트 3")
        var submitted = ""
        compose.setContent { FitnessComposeTheme(false) {
            WorkoutNoteField("owner:record:setup", "기구 설정", source.value, "다음 운동에도 유지", "시트 4", false,
                onSave = { note, result -> submitted = note; result(false) }, tag = "note")
        } }
        compose.onNodeWithTag("note-toggle").performClick()
        compose.onNodeWithTag("note-input").performTextReplacement("시트 4 / 등받이 2")
        compose.runOnIdle { source.value = "다른 화면에서 저장한 설정" }
        compose.onNodeWithTag("note-input").assertTextContains("시트 4 / 등받이 2")
        compose.onNodeWithTag("note-save").performClick()
        compose.runOnIdle { assertEquals("시트 4 / 등받이 2", submitted) }
        compose.onNodeWithTag("note-input").assertTextContains("시트 4 / 등받이 2")
        compose.onNodeWithText("저장하지 못했습니다. 내용을 유지했으니 다시 저장하세요.").assertExists()
    }

    @Test fun draftsSurviveRecreationAndAreNotReusedWhenTheOwnerChanges() {
        val owner = mutableStateOf("owner-a")
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FitnessComposeTheme(false) {
            WorkoutNoteField("${owner.value}:record:condition", "오늘 컨디션", "", "세션 메모", "수면", false,
                onSave = { _, result -> result(true) }, tag = "note")
        } }
        compose.onNodeWithTag("note-toggle").performClick()
        compose.onNodeWithTag("note-input").performTextReplacement("수면 부족")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("note-input").assertTextContains("수면 부족")
        compose.runOnIdle { owner.value = "owner-b" }
        compose.onNodeWithTag("note-toggle").performClick()
        compose.onNodeWithTag("note-input").assert(hasText(""))
    }

    @Test fun completedNotesAreVisibleWithoutEditableControls() {
        compose.setContent { FitnessComposeTheme(false) {
            WorkoutNoteField("owner:completed", "당일 컨디션", "어깨가 뻐근함", "", "", true,
                onSave = { _, _ -> error("Read-only notes cannot save") }, tag = "note")
        } }
        compose.onNodeWithText("어깨가 뻐근함").assertExists()
        compose.onNodeWithTag("note-toggle").assertDoesNotExist()
        compose.onNodeWithTag("note-input").assertDoesNotExist()
    }
}
