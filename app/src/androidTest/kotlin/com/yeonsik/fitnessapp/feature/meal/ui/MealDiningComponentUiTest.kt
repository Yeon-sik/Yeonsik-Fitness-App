package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class MealDiningComponentUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun addEditAndRemoveComponentKeepsSeparateKindProvisionQuantityAndFraction() {
        var menu by mutableStateOf(MealDiningDraftItem("burger", DiningOutDraft(menu = "통새우와퍼")))
        val state = MealUiState.Ready("owner", "2026-10-24", true, true, "", emptyList(), DiningOutDraft())
        val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader, arrayOf(MealScreenActions::class.java)) {
            proxy, method, args -> when (method.name) {
                "equals" -> proxy === args?.firstOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "DiningComponentActions"
                "updateDiningMenu" -> { menu = args!![0] as MealDiningDraftItem; null }
                else -> null
            }
        } as MealScreenActions
        compose.setContent { FitnessComposeTheme(false) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MealDiningComponentEditor(menu, state, actions, true)
            }
        } }
        compose.onNodeWithTag("meal-component-add-burger").performScrollTo().performClick()
        var id = ""
        compose.runOnIdle { id = menu.components.single().id }
        compose.onNodeWithTag("meal-component-name-$id").performScrollTo().performTextReplacement("리뷰 콜라")
        compose.onNodeWithTag("meal-component-quantity-$id").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText("종류 · 추가 구성").performScrollTo().performClick()
        compose.onNodeWithText("음료").performClick()
        compose.onNodeWithText("제공 방식 · 유료 추가").performScrollTo().performClick()
        compose.onNodeWithText("리뷰 이벤트").performClick()
        compose.onNodeWithTag("meal-component-percent-$id").performScrollTo().performTextReplacement("33.3333")
        compose.runOnIdle {
            val item = menu.components.single()
            assertEquals("beverage", item.groupType)
            assertEquals("review_event", item.provisionType)
            assertEquals("3", item.quantity)
            assertEquals("33.3333", item.consumedPercent)
            assertNull(item.selection().profile.value("calories_kcal"))
        }
        compose.onNodeWithTag("meal-component-remove-$id").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(menu.components.isEmpty()) }
    }
}
