package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

/** Plain UI with immutable fixtures and captured callbacks; no app accounts or network. */
class DiningProposalConsentUiTest {
    @get:Rule val compose = createComposeRule()
    private val id = "11111111-1111-4111-8111-111111111111"
    private val food = NutritionFood("food", "nutrition-owner", "원래 메뉴", "external_menu",
        1.0, "serving", 500.0, 20.0, 60.0, 15.0, "dining_out_menu", null)
    private var resubmissions = 0
    private var publications = 0
    private var submittedFacts: DiningMerchantFacts? = null
    private var submittedMenu: String? = null
    private var submittedMerchantCandidate: String? = null
    private var verified = false
    // Only callbacks used by this isolated content need behavior; all other screen events are void.
    private val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader,
        arrayOf(MealScreenActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "Dining consent UI callbacks"
            "resubmitDiningMenu" -> {
                resubmissions++; assertEquals(id, args!![0]); submittedMenu = args[4] as String
                submittedMerchantCandidate = args[3] as String?
                verified = args[5] as Boolean; null
            }
            "resubmitDiningMerchant" -> {
                resubmissions++; assertEquals(id, args!![0]); submittedFacts = args[1] as DiningMerchantFacts
                verified = args[2] as Boolean; null
            }
            "publishApprovedDiningProposal" -> { publications++; null }
            else -> null
        }
    } as MealScreenActions

    @Test fun menuEditRequiresFreshReconfirmationAndDoesNotPublish() {
        render(state(listOf(proposal())))
        compose.onNodeWithText("거절된 메뉴 제안 수정").performScrollTo().performClick()
        val submit = compose.onNodeWithText("수정·재확인한 정보로 재제출")
        submit.assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasText("메뉴명 · 필수")).performTextReplacement("수정 메뉴")
        compose.onNodeWithText("수정한 정보를 직접 확인했습니다").performScrollTo().performClick()
        submit.assertIsEnabled()
        compose.onNode(hasSetTextAction() and hasText("메뉴명 · 필수")).performTextReplacement("최종 메뉴")
        submit.assertIsNotEnabled()
        compose.onNodeWithText("수정한 정보를 직접 확인했습니다").performScrollTo().performClick()
        submit.performClick()
        compose.runOnIdle {
            assertEquals(1, resubmissions); assertEquals("최종 메뉴", submittedMenu)
            assertTrue(verified); assertEquals(0, publications)
        }
    }

    @Test fun merchantEditPrefillsRejectedFactsAndRequiresReconfirmation() {
        val merchant = proposal().copy(kind = "merchant", requestJson =
            "{\"p_merchant\":{\"merchant_name\":\"이전 가게\",\"branch_name\":\"이전 지점\"},\"p_user_verified\":true}")
        render(state(listOf(merchant)))
        compose.onNodeWithText("거절된 가게 제안 수정").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("가게명 · 필수")).assertTextContains("이전 가게")
            .performTextReplacement("수정 가게")
        compose.onNode(hasSetTextAction() and hasText("지점명 · 선택")).assertTextContains("이전 지점")
        compose.onNodeWithText("수정·재확인한 정보로 재제출").assertIsNotEnabled()
        compose.onNodeWithText("수정한 정보를 직접 확인했습니다").performScrollTo().performClick()
        compose.onNodeWithText("수정·재확인한 정보로 재제출").performClick()
        compose.runOnIdle {
            assertEquals(1, resubmissions); assertEquals("수정 가게", submittedFacts!!.name)
            assertEquals("이전 지점", submittedFacts!!.branch); assertTrue(verified); assertEquals(0, publications)
        }
    }

    @Test fun acceptanceRequiresSeparateFinalPublicationConsent() {
        render(state(listOf(accepted())))
        compose.onNodeWithText("거절된 메뉴 제안 수정").assertDoesNotExist()
        compose.onNodeWithText("승인됨 · PT에 공개 연결").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, publications) }
        compose.onNodeWithText("취소").performClick()
        compose.runOnIdle { assertEquals(0, publications) }
        compose.onNodeWithText("승인됨 · PT에 공개 연결").performScrollTo().performClick()
        compose.onNodeWithText("공개 연결").performClick()
        compose.runOnIdle { assertEquals(1, publications); assertEquals(0, resubmissions) }
    }

    @Test fun latestPendingRequestBlocksEditingAndOlderApprovalPublication() {
        render(state(listOf(accepted(), proposal().copy(status = "pending", requestVersion = 2))))
        compose.onNodeWithText("거절된 메뉴 제안 수정").assertDoesNotExist()
        compose.onNodeWithText("승인됨 · PT에 공개 연결").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, publications); assertEquals(0, resubmissions) }
    }

    @Test fun changingRemoteScopeClearsOpenReconfirmationAndPublicationDialogs() {
        val value = mutableStateOf(state(listOf(proposal())))
        compose.setContent {
            FitnessComposeTheme(false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { DiningProposalControls(value.value, null, actions, false) }
            }
        }
        compose.onNodeWithText("거절된 메뉴 제안 수정").performScrollTo().performClick()
        compose.onNodeWithText("수정한 정보를 직접 확인했습니다").performScrollTo().performClick()
        compose.runOnIdle { value.value = state(listOf(proposal().copy(remoteScope = "other-project|other-user"))) }
        compose.onNodeWithText("거절된 제안 수정·재확인").assertDoesNotExist()
        compose.runOnIdle { value.value = state(listOf(accepted())) }
        compose.onNodeWithText("승인됨 · PT에 공개 연결").performScrollTo().performClick()
        compose.runOnIdle { value.value = state(emptyList()) }
        compose.onNodeWithText("승인된 메뉴에 공개 연결").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, resubmissions); assertEquals(0, publications) }
    }

    @Test fun rejectedMenuCanExplicitlyChooseTheRevisedApprovedMerchant() {
        val merchantId = "22222222-2222-4222-8222-222222222222"
        val menu = proposal().copy(requestJson =
            "{\"p_restaurant_id\":null,\"p_restaurant_location_id\":null,\"p_merchant_candidate_id\":\"$id\",\"p_menu_name\":\"원래 메뉴\",\"p_metadata\":{},\"p_user_verified\":true}")
        val merchant = accepted().copy(kind = "merchant", candidateId = merchantId, requestVersion = 2)
        render(state(listOf(menu, merchant)))
        compose.onNodeWithText("수정된 가게 제안에 메뉴 재제출").performScrollTo().performClick()
        compose.onNodeWithText("수정·재확인한 정보로 재제출").assertIsNotEnabled()
        compose.onNodeWithText("수정한 정보를 직접 확인했습니다").performScrollTo().performClick()
        compose.onNodeWithText("수정·재확인한 정보로 재제출").performClick()
        compose.runOnIdle {
            assertEquals(1, resubmissions); assertEquals(merchantId, submittedMerchantCandidate)
            assertTrue(verified); assertEquals(0, publications)
        }
    }

    private fun render(state: NutritionPublicationUiState) = compose.setContent {
        FitnessComposeTheme(false) {
            Column(Modifier.verticalScroll(rememberScrollState())) { DiningProposalControls(state, null, actions, false) }
        }
    }
    private fun state(proposals: List<DiningProposal>) = NutritionPublicationUiState(ownerId = "app-owner",
        open = true, menus = listOf(food), selectedFoodId = food.id, remoteAvailable = true,
        privateFoodIds = setOf(food.id), proposals = proposals)
    private fun proposal() = DiningProposal("nutrition-owner", food.id, "menu", "project|user", "key",
        "{\"p_restaurant_id\":\"$id\",\"p_restaurant_location_id\":\"$id\",\"p_merchant_candidate_id\":null,\"p_menu_name\":\"원래 메뉴\",\"p_metadata\":{},\"p_user_verified\":true}",
        candidateId = id, status = "rejected", createdAt = "created", updatedAt = "updated")
    private fun accepted() = proposal().copy(status = "accepted", restaurantId = id,
        restaurantLocationId = id, restaurantMenuId = id, catalogProductId = id)
}
