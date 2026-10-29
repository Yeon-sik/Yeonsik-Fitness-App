package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind
import com.yeonsik.fitnessapp.feature.nutrition.ui.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

@OptIn(ExperimentalFoundationApi::class)
class MealNutrientInputHierarchyUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualFoodChildrenKeepTheirOwnValuesWhenDetailsCollapseAndReopen() {
        var draft by mutableStateOf(ManualFoodDraft(name = "계층 확인 식품", calories = "200",
            carbs = "30", protein = "10", fat = "5", sugars = "0", transFat = "0.1"))
        compose.setContent { Host {
            ManualFoodEditor(draft, { draft = it }, {}, {}, false, null)
        } }
        revealOptional()
        assertIncludedFields()
        field("트랜스지방", "지방").performScrollTo()
        savePreview("manual-food", compose.onNodeWithTag("nutrient-input-host"))
        field("트랜스지방", "지방").performTextReplacement("0.2")
        compose.runOnIdle {
            assertEquals("0.2", draft.transFat)
            assertEquals("0", draft.sugars)
            assertEquals("30", draft.carbs)
            assertEquals("5", draft.fat)
            assertTrue(draft.saturatedFat.isBlank())
        }
        compose.onNodeWithText("추가 영양정보 접기").performScrollTo().performClick()
        compose.onNodeWithTag("nutrition-included-지방").assertDoesNotExist()
        revealOptional()
        field("트랜스지방", "지방").assertTextContains("0.2")
    }

    @Test fun diningEntryKeepsSugarAndBothFatFieldsUnderTheirTotal() {
        var editor by mutableStateOf(ready(diningDraft()))
        val actions = actions(MealScreenActions::class.java) { name, args ->
            if (name == "updateTransFat") editor = editor.copy(draft = editor.draft.copy(transFat = args[0] as String))
        }
        compose.setContent { Host(dark = true) {
            val controls = rememberDiningOutEditorState(editor, PriceTraceUiState.Idle)
            val requester = remember { BringIntoViewRequester() }
            DiningOutEditor(actions, editor, PriceTraceUiState.Idle, controls, requester)
        } }
        assertIncludedFields()
        field("트랜스지방", "지방").performScrollTo()
        savePreview("dining-entry", compose.onNodeWithTag("nutrient-input-host"))
        field("트랜스지방", "지방").performTextReplacement("0")
        compose.runOnIdle {
            assertEquals("0", editor.draft.transFat)
            assertEquals("30", editor.draft.carbs)
            assertEquals("5", editor.draft.fat)
            assertTrue(editor.draft.saturatedFat.isBlank())
        }
    }

    @Test fun diningCartEditingUsesTheSameHierarchyWithoutReplacingTotals() {
        var menu by mutableStateOf(MealDiningDraftItem("qa-menu", diningDraft()))
        val actions = actions(MealScreenActions::class.java) { name, args ->
            if (name == "updateDiningMenu") menu = args[0] as MealDiningDraftItem
        }
        compose.setContent { Host {
            MealCartContent(actions, ready(DiningOutDraft()).copy(diningMenus = listOf(menu)),
                "dining-qa-menu", {}, {}, {})
        } }
        compose.onNodeWithTag("meal-dining-edit-qa-menu").performScrollTo().performClick()
        assertIncludedFields()
        field("트랜스지방", "지방").performScrollTo()
        savePreview("dining-cart", compose.onNodeWithTag("nutrient-input-host"))
        field("트랜스지방", "지방").performTextReplacement("0.4")
        compose.runOnIdle {
            assertEquals("0.4", menu.draft.transFat)
            assertEquals("30", menu.draft.carbs)
            assertEquals("5", menu.draft.fat)
        }
    }

    @Test fun catalogRegistrationKeepsOptionalFieldsGroupedAtLargeFontScale() {
        var state by mutableStateOf(NutritionEditorState(open = true, mode = NutritionEditorMode.FOOD,
            registrationKind = NutritionRegistrationKind.INGREDIENT,
            fields = mapOf(FoodEntryField.BASIS to "100", FoodEntryField.NAME to "계층 확인 재료",
                FoodEntryField.CALORIES to "200", FoodEntryField.CARBS to "30",
                FoodEntryField.PROTEIN to "10", FoodEntryField.FAT to "5",
                FoodEntryField.SUGARS to "0", FoodEntryField.TRANS_FAT to "0.1")))
        val actions = actions(NutritionEditorActions::class.java) { name, args ->
            if (name == "updateField") state = state.copy(fields = state.fields + (args[0] as FoodEntryField to args[1] as String))
        }
        compose.setContent { Host(fontScale = 1.5f) {
            NutritionEditorDialog(state, actions, {})
        } }
        revealOptional()
        assertIncludedFields()
        field("트랜스지방", "지방").performScrollTo()
        savePreview("catalog-fat-large", compose.onNodeWithTag("nutrition-included-지방"))
        field("트랜스지방", "지방").performTextReplacement("0")
        compose.runOnIdle {
            assertEquals("0", state.field(FoodEntryField.TRANS_FAT))
            assertEquals("30", state.field(FoodEntryField.CARBS))
            assertEquals("5", state.field(FoodEntryField.FAT))
            assertTrue(state.field(FoodEntryField.SATURATED_FAT).isBlank())
        }
    }

    private fun revealOptional() {
        compose.onNodeWithText("나트륨·당류·포화지방·트랜스지방 입력 (선택)")
            .performScrollTo().performClick()
    }

    private fun field(label: String, parent: String) = compose.onNode(
        hasSetTextAction() and hasText(label, substring = true) and
            hasAnyAncestor(hasTestTag("nutrition-included-$parent")), useUnmergedTree = false)

    private fun assertIncludedFields() {
        field("당류", "탄수화물").assertExists()
        field("포화지방", "지방").assertExists()
        field("트랜스지방", "지방").assertExists()
        compose.onNode(hasSetTextAction() and hasText("트랜스지방", substring = true) and
            hasAnyAncestor(hasTestTag("nutrition-included-탄수화물")), useUnmergedTree = false).assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("단백질", substring = true) and
            hasAnyAncestor(hasTestTag("nutrition-included-지방")), useUnmergedTree = false).assertDoesNotExist()
    }

    @Composable
    private fun Host(dark: Boolean = false, fontScale: Float = 1f, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            FitnessComposeTheme(dark) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
                        .verticalScroll(rememberScrollState()).padding(AppSpacing.card)
                        .testTag("nutrient-input-host"),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.small)
                ) { content() }
            }
        }
    }

    private fun diningDraft() = DiningOutDraft(store = "확인 식당", menu = "확인 메뉴",
        time = "12:00", calories = "200", carbs = "30", protein = "10", fat = "5",
        sugars = "0", transFat = "0.1")

    private fun ready(draft: DiningOutDraft) = MealUiState.Ready(ownerId = "owner",
        date = LocalDate.now().toString(), editing = true, diningOut = true,
        query = "", searchResults = emptyList(), draft = draft)

    private fun <T : Any> actions(type: Class<T>, onCall: (String, Array<out Any?>) -> Unit): T {
        return requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, args ->
            when (method.name) {
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "toString" -> type.simpleName
                else -> { onCall(method.name, args.orEmpty()); null }
            }
        }))
    }

    private fun savePreview(name: String, node: SemanticsNodeInteraction) {
        val phase = androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString("nutritionQaPass") ?: return
        val folder = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir("codex-nutrition-hierarchy")!!
        folder.mkdirs()
        val bitmap = node.captureToImage().asAndroidBitmap()
        java.io.File(folder, "$phase-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
