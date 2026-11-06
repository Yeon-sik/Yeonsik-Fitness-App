package com.yeonsik.fitnessapp.feature.information

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.feature.information.ui.InformationInboxActivity
import com.yeonsik.fitnessapp.config.NutritionSupabaseConfigStore
import com.yeonsik.fitnessapp.config.SupabaseConfigStore
import com.yeonsik.fitnessapp.core.database.FitnessRoomDatabaseProvider
import com.yeonsik.fitnessapp.feature.information.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertNull
import java.io.File
import java.util.UUID

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

    @Test fun genericRecordOffersTypedMappingAndPreviewBeforeNativeApproval() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = SupabaseConfigStore(context).load()
        val profile = InformationProfile(config.effectiveUserId(), if (config.userId.isBlank()) "local" else config.supabaseUrl)
        val db = FitnessRoomDatabaseProvider.get(context)
        val repository = InformationInboxRepository(db, profile)
        val previousDraft = repository.draft()
        val id = UUID.randomUUID().toString()
        val payload = """{"schema_version":"information-record.v1","record_id":"$id","owner_app":"fitness","record_type":"fitness.observation","title":"UI 반영 검토 원문","captured_at":"2026-11-06T00:00:00Z","sources":[{"source_id":"manual","kind":"manual"}],"fields":[{"key":"date","value_type":"date","value":"2000-01-01"},{"key":"weight_kg","value_type":"decimal","value":"72.4","unit":"kg"}],"references":[]}"""
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithText("기록을 처리하고 있습니다.").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText("새 활동 자료 기록").performClick()
            compose.onNodeWithText("구조화된 기록 JSON").performTextReplacement(payload)
            compose.onNodeWithText("이 자료가 나의 활동·운동·식사·체중 정보임을 확인했습니다.").performScrollTo().performClick()
            compose.onNodeWithText("검토 대기에 저장").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("UI 반영 검토 원문").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("UI 반영 검토 원문").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("체중 반영 검토"))
            compose.onNodeWithText("체중 반영 검토").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("반영 미리보기"))
            compose.onNodeWithText("반영 미리보기").performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText("확인한 내용으로 실제 기록에 반영"))
            compose.onNodeWithText("확인한 내용으로 실제 기록에 반영").assertIsEnabled()
            assertNull(db.informationProjectionDao().application(profile.userId, profile.project, id))
            screenshot("information-projection-preview.png")
            // Preview only. Actual atomic writes/statistics are covered by the isolated database test.
        } finally {
            db.openHelper.writableDatabase.execSQL("DELETE FROM fitness_information_inbox WHERE user_id=? AND project=? AND record_id=?", arrayOf(profile.userId, profile.project, id))
            if (previousDraft != null) repository.saveDraft(previousDraft)
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
