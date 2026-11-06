package com.yeonsik.fitnessapp.feature.information

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.integration.healthtrace.HealthHandoffActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class HealthHandoffUiTest {
    @get:Rule val compose = createAndroidComposeRule<HealthHandoffActivity>()

    @Test fun handoffNamesOwnershipAndKeepsOriginals() {
        compose.onNodeWithText("이전 건강 자료").assertIsDisplayed()
        compose.waitUntil(10000) { compose.onAllNodesWithText("이전 자료를 불러오고 있습니다.").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("HealthTrace 열기").assertIsDisplayed()
        compose.onNodeWithText("수분 섭취와 운동 준비도는 Fitness에 남습니다. 파일에 실제 과거 시각이 없는 경우 관찰 시각을 새로 만들지 않습니다.").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "health-handoff.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
