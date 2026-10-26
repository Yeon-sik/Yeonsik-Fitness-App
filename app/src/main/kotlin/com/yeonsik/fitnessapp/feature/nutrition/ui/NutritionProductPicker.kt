package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*

/** Plain rendering boundary: lookup, drafts and registration belong to the controller. */
@Composable
internal fun NutritionProductPicker(state: NutritionProductPickerState, actions: NutritionEditorActions,
    enabled: Boolean, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("PriceTrace 제품 검색", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("제품을 선택해 연결하거나 아래에서 직접 입력하세요.", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val search = { focus.clearFocus(); actions.searchProducts() }
        AppTextField(state.query, actions::updateProductQuery, Modifier.fillMaxWidth().testTag("nutrition-product-query"),
            enabled = enabled, label = { Text("PriceTrace 제품 이름 검색") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
        AppOutlinedButton(search, Modifier.fillMaxWidth().testTag("nutrition-product-search"),
            enabled = enabled && !state.loading && state.query.isNotBlank()) { Text(if (state.loading) "불러오는 중…" else "제품 검색") }
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        if (state.searched && state.results.isEmpty() && state.selected == null && !state.loading)
            Text("일치하는 PriceTrace 제품이 없습니다. 다른 이름으로 검색하거나 직접 입력하세요.", style = MaterialTheme.typography.bodyMedium)
        if (state.results.isNotEmpty()) Column(Modifier.fillMaxWidth().heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState()).testTag("nutrition-product-results"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.results.forEach { product ->
                AppOutlinedButton({ focus.clearFocus(); actions.selectProduct(product) },
                    Modifier.fillMaxWidth().testTag("nutrition-product-${product.standardProductId ?: product.catalogProductId}"),
                    enabled = enabled && !state.loading) { Text(product.standardProductLabel(), Modifier.fillMaxWidth()) }
            }
        }
        state.selected?.let { product ->
            Text("선택됨 · ${product.standardProductLabel()}", Modifier.testTag("nutrition-product-selected"),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            listOfNotNull(product.manufacturerName?.let { "제조회사 · $it" }, product.subBrandName?.let { "서브브랜드 · $it" })
                .forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.notice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            AppOutlinedButton({ focus.clearFocus(); actions.clearProductSelection() },
                Modifier.fillMaxWidth().testTag("nutrition-product-clear"), enabled = enabled) { Text("PriceTrace 선택 해제 · 직접 입력") }
        }
    }
}
