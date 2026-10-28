package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import com.yeonsik.fitnessapp.core.ui.AppOutlinedButton
import com.yeonsik.fitnessapp.core.ui.AppSpacing
import com.yeonsik.fitnessapp.core.ui.AppTextField
import com.yeonsik.fitnessapp.data.BrandReadV1
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection

/** Renders either explicit identity or editable unlinked text. All work belongs to the caller. */
@Composable
internal fun NutritionBrandField(
    value: String,
    selected: PriceTraceBrandSelection?,
    search: NutritionBrandSearchState,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (BrandReadV1) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
        if (selected != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("PriceTrace 브랜드 · ${selected.canonicalName}", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                TextButton(onClear, enabled = enabled) { Text("연결 해제") }
            }
        } else {
            AppTextField(value, onValueChange, Modifier.fillMaxWidth().testTag("nutrition-brand-query"),
                label = { Text("브랜드 (선택)") }, enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (enabled && !search.loading) onSearch() }))
            AppOutlinedButton(onSearch, Modifier.fillMaxWidth(), enabled = enabled && !search.loading && value.isNotBlank()) {
                Text("브랜드 검색")
            }
            if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!search.loading && search.error != null) Text("브랜드 조회에 실패했습니다.", color = MaterialTheme.colorScheme.error)
            if (search.searched && !search.loading && search.error == null && search.results.isEmpty()) Text("검색 결과가 없습니다.")
            if (search.searched && !search.loading && (search.error != null || search.results.isEmpty())) {
                Text("입력한 텍스트를 미연결 브랜드로 그대로 저장할 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            search.results.forEach { brand ->
                AppOutlinedButton({ onSelect(brand) }, Modifier.fillMaxWidth().testTag("nutrition-brand-${brand.brandId}"),
                    enabled = enabled && !search.loading) {
                    Column {
                        Text("${brand.canonicalName} 선택")
                        brand.matchedAlias?.let { Text("별칭 · $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
