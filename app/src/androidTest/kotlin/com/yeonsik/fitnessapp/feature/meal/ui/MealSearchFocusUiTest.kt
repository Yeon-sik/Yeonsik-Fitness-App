package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Build
import android.view.View
import android.view.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.yeonsik.fitnessapp.core.ui.FitnessComposeTheme
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot
import com.yeonsik.fitnessapp.feature.home.ui.HomeUiState
import org.junit.Rule
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

/** Reproduces the real editor losing its focused field during a background history refresh. */
class MealSearchFocusUiTest {
    @get:Rule val compose = createComposeRule()
    private val owner = "meal-focus-test-owner"
    private val date = LocalDate.now().toString()
    private var hostView: View? = null

    @Test fun foodSearchKeepsFocusAndTextDuringHistoryRefresh() = retainsSearch(false, "식품 검색")

    @Test fun diningSearchKeepsFocusAndTextDuringHistoryRefresh() = retainsSearch(true, "저장된 외식 메뉴 검색")

    private fun retainsSearch(diningOut: Boolean, label: String) {
        val home = mutableStateOf<HomeUiState>(readyHome())
        val editor = mutableStateOf(MealUiState.Ready(owner, date, true, diningOut, "",
            emptyList(), DiningOutDraft()))
        val actions = Proxy.newProxyInstance(MealScreenActions::class.java.classLoader,
            arrayOf(MealScreenActions::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "Meal focus callbacks"
                "searchFood" -> { editor.value = editor.value.copy(query = args!![0] as String); null }
                else -> null
            }
        } as MealScreenActions
        compose.setContent {
            val view = LocalView.current
            SideEffect { hostView = view }
            FitnessComposeTheme(false) {
                Column(Modifier.imePadding().verticalScroll(rememberScrollState())) {
                    MealScreen(home.value, editor.value, PriceTraceUiState.Idle,
                        NutritionPublicationUiState(), owner, date, actions)
                }
            }
        }
        val search = compose.onNode(hasSetTextAction() and hasText(label))
        search.performScrollTo().performClick().performTextInput("김")
        search.assertIsFocused()
        assertKeyboardVisible()
        compose.runOnIdle { home.value = HomeUiState.Loading }
        search.assertIsFocused().assertTextContains("김")
        assertKeyboardVisible()
        search.performTextInput("밥")
        compose.runOnIdle { home.value = readyHome() }
        search.assertIsFocused().assertTextContains("김밥")
        assertKeyboardVisible()
    }

    private fun assertKeyboardVisible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            compose.waitUntil(3000) { hostView?.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        }
    }

    private fun readyHome() = HomeUiState.Ready(HomeSnapshot(owner, date, emptyList(), null,
        emptyList(), emptyMap(), emptyMap(), null, emptyMap(), emptyMap(), emptyMap(), null,
        null, emptyList(), emptyList()))
}
