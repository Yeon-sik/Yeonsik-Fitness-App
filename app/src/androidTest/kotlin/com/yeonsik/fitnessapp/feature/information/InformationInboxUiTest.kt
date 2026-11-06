package com.yeonsik.fitnessapp.feature.information

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.feature.information.ui.InformationInboxActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class InformationInboxUiTest {
    @get:Rule val compose = createAndroidComposeRule<InformationInboxActivity>()

    @Test fun emptyInboxAndDraftRequireExplicitOwnershipReview() {
        compose.onNodeWithText("활동 정보 검토함").assertIsDisplayed()
        compose.waitUntil(10000) { compose.onAllNodesWithText("기록을 처리하고 있습니다.").fetchSemanticsNodes().isEmpty() }
        screenshot("information-inbox.png")
        compose.onNodeWithText("새 활동 자료 기록").performClick()
        compose.onNodeWithText("기록 초안").assertIsDisplayed()
        compose.onNodeWithText("검토 대기에 저장").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("이 자료가 나의 활동·운동·식사·체중 정보임을 확인했습니다.").performClick()
        compose.onNodeWithText("검토 대기에 저장").assertIsEnabled()
        screenshot("information-draft.png")
        // Leave unsaved: this UI acceptance must not create native or inbox records.
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
