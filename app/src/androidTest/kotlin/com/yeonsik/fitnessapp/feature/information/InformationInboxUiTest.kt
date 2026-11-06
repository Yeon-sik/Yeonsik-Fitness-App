package com.yeonsik.fitnessapp.feature.information

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.feature.information.ui.InformationInboxActivity
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
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

    @Test fun remoteMealActionRequiresExplicitAccountAndProfileConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = NutritionSupabaseConfigStore(context)
        val original = store.load()
        try {
            if (!original.isConnectionConfigured) store.saveConnection("https://nutrition.example", "public-test-key")
            store.saveSession("12345678-1234-4234-8234-1234567890ab", "remote-meal-review@example.test", "test-token", "test-refresh")
            compose.waitUntil(10000) { compose.onAllNodesWithText("기록을 처리하고 있습니다.").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("Nutrition 식사 가져오기").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("계정 연결 확인").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("계정 연결 확인").assertIsDisplayed()
            compose.onNodeWithText("저장할 Fitness 프로필", substring = true).assertExists()
            compose.onNodeWithText("remote-meal-review@example.test", substring = true).assertExists()
            screenshot("remote-meal-account-review.png")
            // Cancel before HTTP: this UI test must never send synthetic credentials to a server.
            compose.onNodeWithText("취소").performClick()
        } finally {
            store.saveConnection(original.supabaseUrl, original.supabaseAnonKey)
            if (original.userId.isBlank()) store.clearSession()
            else store.saveSession(original.userId, original.email, original.accessToken, original.refreshToken)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Compose idle does not wait for the platform Dialog window fade-in.
        instrumentation.uiAutomation.waitForIdle(500, 3000)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
