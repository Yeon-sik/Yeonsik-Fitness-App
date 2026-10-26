package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.ProductReadV1
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind
import com.yeonsik.fitnessapp.feature.meal.ui.ManualFoodDraft
import com.yeonsik.fitnessapp.feature.meal.ui.ManualFoodEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy

class NutritionProductPickerUiTest {
    @get:Rule val compose = createComposeRule()
    private val product = ProductReadV1("81111111-1111-4111-8111-111111111111", "82222222-2222-4222-8222-222222222222",
        "연결할 제품", "브랜드", null, null, null, 100.0, "g", 1, "sha256:" + "a".repeat(64))

    @Test fun productRegistrationCanSearchSelectAndReturnToManualIdentity() {
        var state by mutableStateOf(NutritionEditorState(open = true, mode = NutritionEditorMode.FOOD,
            fields = mapOf(FoodEntryField.NAME to "직접 입력", FoodEntryField.BRAND to "직접 브랜드")))
        val calls = mutableListOf<String>()
        val actions = actions { name, args ->
            calls += name
            when (name) {
                "updateProductQuery" -> state = state.copy(productPicker = state.productPicker.copy(query = args!![0] as String))
                "searchProducts" -> state = state.copy(productPicker = state.productPicker.copy(results = listOf(product), searched = true))
                "selectProduct" -> state = state.copy(fields = state.fields + (FoodEntryField.NAME to product.name),
                    productPicker = state.productPicker.copy(selected = product, results = emptyList()))
                "clearProductSelection" -> state = state.copy(fields = state.fields + (FoodEntryField.NAME to "직접 입력"), productPicker = NutritionProductPickerState())
            }
        }
        compose.setContent { FitnessComposeTheme(true) { NutritionEditorDialog(state, actions, {}) } }
        compose.onNodeWithTag("nutrition-product-query").performScrollTo().performTextInput("제품")
        compose.onNodeWithTag("nutrition-product-search").performScrollTo().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("nutrition-product-${product.standardProductId}").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition-product-selected").assertExists()
        compose.onNodeWithTag("nutrition-product-name").assertIsNotEnabled()
        compose.onNodeWithTag("nutrition-product-clear").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition-product-name").assertIsEnabled().assertTextContains("직접 입력")
        assertTrue(calls.containsAll(listOf("searchProducts", "selectProduct", "clearProductSelection")))
    }

    @Test fun ingredientsHideProductLookupAndBusyLookupDisablesRegistration() {
        var state by mutableStateOf(NutritionEditorState(open = true, mode = NutritionEditorMode.FOOD,
            registrationKind = NutritionRegistrationKind.INGREDIENT))
        compose.setContent { FitnessComposeTheme(false) { NutritionEditorDialog(state, actions { _, _ -> }, {}) } }
        compose.onNodeWithTag("nutrition-product-query").assertDoesNotExist()
        compose.runOnIdle { state = state.copy(registrationKind = NutritionRegistrationKind.PRODUCT,
            productPicker = NutritionProductPickerState(loading = true)) }
        compose.onNodeWithTag("nutrition-register-save").assertIsNotEnabled()
        compose.onNodeWithTag("nutrition-product-search").assertIsNotEnabled()
    }

    @Test fun mealProductTabExposesTheLookupEntryWithoutSavingOrChangingTheDraft() {
        var draft by mutableStateOf(ManualFoodDraft(name = "작성 중인 제품", registrationKind = NutritionRegistrationKind.PRODUCT))
        var openings = 0
        compose.setContent { FitnessComposeTheme(true) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                ManualFoodEditor(draft, { draft = it }, {}, {}, false, null, onSearchProduct = { openings++ })
            }
        } }
        compose.onNodeWithText("PriceTrace 제품 검색").performScrollTo().performClick()
        assertEquals(1, openings)
        assertEquals("작성 중인 제품", draft.name)
        compose.runOnIdle { draft = draft.copy(registrationKind = NutritionRegistrationKind.INGREDIENT) }
        compose.onNodeWithText("PriceTrace 제품 검색").assertDoesNotExist()
    }

    private fun actions(call: (String, Array<out Any?>?) -> Unit) = Proxy.newProxyInstance(
        NutritionEditorActions::class.java.classLoader, arrayOf(NutritionEditorActions::class.java)) { proxy, method, args ->
        when (method.name) {
            "equals" -> proxy === args?.firstOrNull()
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "ProductPickerActions"
            else -> { call(method.name, args); null }
        }
    } as NutritionEditorActions
}
