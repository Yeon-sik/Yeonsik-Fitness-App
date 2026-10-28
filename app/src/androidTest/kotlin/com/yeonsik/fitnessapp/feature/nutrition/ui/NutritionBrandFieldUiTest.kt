package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.data.BrandReadV1
import com.yeonsik.fitnessapp.feature.meal.ui.ManualFoodDraft
import com.yeonsik.fitnessapp.feature.meal.ui.ManualFoodEditor
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class NutritionBrandFieldUiTest {
    @get:Rule val compose = createComposeRule()
    private val row = BrandReadV1("11111111-1111-4111-8111-111111111111", "농심", "Nongshim")

    @Test fun explicitAliasSelectionAndUnlinkAreCallerOwned() {
        var value by mutableStateOf("Nongshim")
        var selected by mutableStateOf<PriceTraceBrandSelection?>(null)
        var search by mutableStateOf(NutritionBrandSearchState())
        var searched = 0
        compose.setContent { FitnessComposeTheme(false) {
            Column(Modifier.width(320.dp)) {
                NutritionBrandField(value, selected, search, { value = it },
                    { searched++; search = NutritionBrandSearchState(value, listOf(row), searched = true) },
                    { selected = PriceTraceBrandSelection(it.brandId, it.canonicalName); value = it.canonicalName },
                    { selected = null; search = NutritionBrandSearchState(query = value) })
            }
        } }
        compose.onNodeWithTag("nutrition-brand-query").performImeAction()
        assertEquals(1, searched); assertNull(selected)
        compose.onNodeWithText("별칭 · Nongshim").assertExists()
        capture("brand-search")
        compose.onNodeWithText("농심 선택").performClick()
        assertEquals(row.brandId, selected!!.brandId)
        compose.onNodeWithText("PriceTrace 브랜드 · 농심").assertExists()
        capture("brand-linked")
        compose.onNodeWithText("연결 해제").performClick()
        assertNull(selected); assertEquals("농심", value)
        compose.onNodeWithTag("nutrition-brand-query").assertTextContains("농심")
    }

    @Test fun manualFoodKeepsNameEditableAndAllowsOfflineOrEmptyBrandText() {
        var draft by mutableStateOf(ManualFoodDraft(name = "내 제품", brand = "직접 브랜드"))
        var search by mutableStateOf(NutritionBrandSearchState(query = draft.brand, searched = true, error = "offline"))
        var saved = false
        compose.setContent { FitnessComposeTheme(true) {
            Column(Modifier.width(360.dp).verticalScroll(rememberScrollState())) {
                ManualFoodEditor(draft, { draft = it }, { saved = true }, {}, false, null,
                    search, {}, {}, {})
            }
        } }
        compose.onNodeWithText("PriceTrace 제품 검색").assertDoesNotExist()
        compose.onNodeWithText("브랜드 조회에 실패했습니다.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("입력한 텍스트를 미연결 브랜드로 그대로 저장할 수 있습니다.").assertExists()
        compose.runOnIdle { search = search.copy(error = null) }
        compose.onNodeWithText("검색 결과가 없습니다.").assertExists()
        compose.onNodeWithText("내 제품").performScrollTo().performTextReplacement("직접 수정한 제품")
        assertEquals("직접 수정한 제품", draft.name)
        compose.onNodeWithText("내 식품으로 등록").performScrollTo().performClick()
        assertTrue(saved); assertEquals("직접 브랜드", draft.brand)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "brand-registration-tests").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
