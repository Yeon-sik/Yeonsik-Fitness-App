package com.yeonsik.fitnessapp.feature.meal.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NutritionPublicationUxTest {
    private fun message(error: Throwable, publishing: Boolean = false) =
        nutritionPublicationError(error, "다시 시도해 주세요.", publishing)

    @Test fun loginAndPermissionErrorsHaveDifferentRecoveryGuidance() {
        assertTrue(message(IOException("HTTP 401")).contains("로그인"))
        assertTrue(message(IOException("HTTP 403")).contains("권한"))
        assertTrue(message(IllegalStateException("PT 관리자 계정 로그인이 필요합니다.")).contains("설정"))
    }

    @Test fun connectionSettingsAndChangedOwnersExplainTheNextStep() {
        assertTrue(message(IllegalStateException("PriceTrace 식당 DB 연결을 먼저 설정하세요.")).contains("서버 연결 설정"))
        assertTrue(message(IllegalStateException("영양정보 계정이 변경되었습니다.")).contains("다시 불러와"))
    }

    @Test fun wrappedOfflineErrorsDoNotExposeTechnicalMessages() {
        val mapped = message(IOException("private host and payload", UnknownHostException("private.example")))
        assertTrue(mapped.contains("네트워크"))
        assertFalse(mapped.contains("private"))
        assertTrue(message(ConnectException("connection refused")).contains("네트워크"))
    }

    @Test fun interruptedPublicationAsksForReadOnlyVerification() {
        assertTrue(message(SocketTimeoutException(), publishing = true).contains("공개 상태"))
        assertTrue(message(IOException("transport interrupted"), publishing = true).contains("공개 상태"))
        assertFalse(message(SocketTimeoutException()).contains("공개 상태"))
    }

    @Test fun rateLimitUnavailableServerAndMissingMenuHaveUsefulMessages() {
        assertTrue(message(IOException("HTTP 429")).contains("잠시"))
        assertTrue(message(IOException("HTTP 503")).contains("서버"))
        assertTrue(message(IOException("HTTP 404")).contains("새로고침"))
        assertTrue(message(IOException("restaurant identity mismatch")).contains("메뉴 정보"))
    }

    @Test fun unknownFailuresUseTheSafeFallback() {
        assertEquals("다시 시도해 주세요.", message(RuntimeException("credentials or internal stack details")))
        assertEquals("식당 서비스 메뉴를 선택하세요.", message(IllegalArgumentException("PriceTrace 메뉴를 선택하세요.")))
    }

    @Test fun verifiedTargetRequiresAllMenuIdsToMatch() {
        val target = NutritionPublicationTarget("food", "nutrition-owner", "restaurant", "branch", "menu", "product", "메뉴")
        assertTrue(target.matches("food", "branch", "menu", "product"))
        assertFalse(target.matches(null, "branch", "menu", "product"))
        assertFalse(target.matches("other", "branch", "menu", "product"))
        assertFalse(target.matches("food", "other", "menu", "product"))
        assertFalse(target.matches("food", "branch", "other", "product"))
        assertFalse(target.matches("food", "branch", "menu", "other"))
    }

    @Test fun everyAsyncPhaseKeepsSelectionLocked() {
        assertFalse(NutritionPublicationUiState().busy)
        assertTrue(NutritionPublicationUiState(loading = true).busy)
        assertFalse(NutritionPublicationUiState(loading = true).working)
        assertTrue(NutritionPublicationUiState(syncing = true).working)
        assertTrue(NutritionPublicationUiState(publishing = true).working)
        assertTrue(NutritionPublicationUiState(verifying = true).busy)
    }

    @Test fun missingSavedMenuDoesNotLeaveASelectedFood() {
        assertNull(NutritionPublicationUiState(selectedFoodId = "deleted").selectedFood)
    }
}
