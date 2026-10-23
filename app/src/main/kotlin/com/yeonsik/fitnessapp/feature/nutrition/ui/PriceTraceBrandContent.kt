package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection

@Composable
internal fun PriceTraceBrandSnapshot(selection: PriceTraceBrandSelection) {
    Text("PriceTrace · ${selection.canonicalName}", style = MaterialTheme.typography.bodyMedium)
    Text(when {
        selection.parentBrandId == null -> "직접 상위 브랜드 없음"
        selection.parentCanonicalName != null -> "상위 브랜드 · ${selection.parentCanonicalName}"
        else -> "상위 브랜드 이름 미확인"
    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun PriceTraceBrandPicker(state: NutritionEditorState, actions: NutritionEditorActions) {
    val search = state.brandSearch ?: return
    val selected = state.editingBrandFood?.priceTraceBrand ?: if (state.editingBrandFood == null) state.priceTraceBrand else null
    val textColors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
    NutritionEntryFrame("브랜드 연결", actions::closeBrandSearch, closeEnabled = !state.saving,
        subtitle = state.editingBrandFood?.let { "PriceTrace · ${it.displayName()}" } ?: "PriceTrace 공개 카탈로그에서 직접 선택하세요") {
        selected?.let {
            PriceTraceBrandSnapshot(it)
            Text("선택할 때 저장한 이름입니다.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(actions::clearBrand, enabled = !state.saving, colors = textColors) { Text("브랜드 연결 해제") }
        }
        AppTextField(search.query, actions::searchBrands, Modifier.fillMaxWidth(), label = { Text("브랜드 이름 검색") },
            enabled = !state.saving)
        if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        search.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error)
            AppOutlinedButton({ actions.searchBrands(search.query) }, Modifier.fillMaxWidth()) { Text("다시 검색") }
        }
        if (search.searched && !search.loading && search.error == null && search.rows.isEmpty()) {
            Text("검색 결과가 없습니다. 다른 이름이나 별칭으로 검색하세요.")
            if (selected != null) Text("현재 PriceTrace 공개 카탈로그에서 확인되지 않음 · 저장된 연결은 유지됩니다.",
                style = MaterialTheme.typography.bodySmall)
        }
        search.rows.forEach { row ->
            NutritionFormSection(row.selection.canonicalName, Modifier.fillMaxWidth()) {
                row.selection.parentBrandId?.let {
                    Text("상위 브랜드 · ${row.selection.parentCanonicalName ?: "이름 미확인"}",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (row.matchKind == "alias") Text("별칭 · ${row.matchedName}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppOutlinedButton({ actions.selectBrand(row) }, Modifier.fillMaxWidth(),
                    enabled = !search.loading && !state.saving) { Text("${row.selection.canonicalName} 선택") }
            }
        }
        if (search.hasMore) AppOutlinedButton(actions::moreBrands, Modifier.fillMaxWidth(), enabled = !search.loading) { Text("브랜드 더 보기") }
        TextButton(actions::closeBrandSearch, colors = textColors) { Text("연결 변경 없이 돌아가기") }
    }
}
