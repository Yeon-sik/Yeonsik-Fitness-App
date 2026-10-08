package com.yeonsik.fitnessapp.feature.nutrition.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.foodPortionTotals

@Composable
internal fun NutritionEditorDialog(
    state: NutritionEditorState,
    actions: NutritionEditorActions,
    onUseComposition: (String) -> Unit
) {
    if (!state.open) return
    val title = when (state.mode) {
        NutritionEditorMode.LIBRARY -> "내 식단 · 식품"
        NutritionEditorMode.COMPOSITION -> "식단 구성"
        NutritionEditorMode.FOOD -> "식품 등록"
    }
    key(state.mode) {
        NutritionEntryFrame(title, actions::close, closeEnabled = !state.saving,
            subtitle = "자주 먹는 음식을 다음 기록에 불러오세요",
            footer = if (state.mode == NutritionEditorMode.LIBRARY) null else ({
                if (state.mode == NutritionEditorMode.COMPOSITION) {
                    NutritionTotalPreview(foodPortionTotals(state.portions), label = "식품 ${state.portions.size}개")
                }
                AppButton(actions::save, Modifier.fillMaxWidth(), enabled = !state.saving && !state.loading) {
                    Text(if (state.saving) "저장 중…" else if (state.mode == NutritionEditorMode.FOOD) "식품 등록하기" else "식단 구성 저장하기")
                }
            })
        ) {
            if (state.mode != NutritionEditorMode.LIBRARY) TextButton(actions::showLibrary, enabled = !state.saving) { Text("내 식단 목록") }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.mode) {
                NutritionEditorMode.LIBRARY -> NutritionLibraryContent(state, actions, onUseComposition)
                NutritionEditorMode.COMPOSITION -> CompositionEditorContent(state, actions)
                NutritionEditorMode.FOOD -> FoodRegistrationContent(state, actions)
            }
        }
    }
}

@Composable
private fun NutritionLibraryContent(state: NutritionEditorState, actions: NutritionEditorActions, onUseComposition: (String) -> Unit) {
    AppButton(actions::newComposition, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("새 식단 구성") }
    AppOutlinedButton(actions::newFood, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("식품 등록") }
    if (state.name.isNotBlank() || state.portions.isNotEmpty()) {
        AppOutlinedButton(actions::resumeComposition, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("작성 중인 구성 이어서") }
    }
    if (state.templates.isEmpty() && !state.loading) {
        NutritionFormSection("아직 저장한 구성이 없어요", Modifier.fillMaxWidth(), "함께 먹는 식품을 묶어 두면 다음 끼니를 빠르게 기록할 수 있어요.") {}
    }
    state.templates.forEach { template ->
        val members = template.groups.flatMap { it.members }.filter { it.defaultSelected }
        val totals = NutritionTotals.builder().apply { members.forEach { add(it.profile) } }.build()
        NutritionFormSection(template.name, Modifier.fillMaxWidth(), "식품 ${members.size}개 · ${members.joinToString(" · ") { it.name }}") {
            NutritionTotalPreview(totals)
            AppButton({ onUseComposition(template.id) }, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("끼니에 불러오기") }
            TextButton({ actions.editComposition(template.id) }, enabled = !state.loading) { Text("구성 수정") }
        }
    }
}

@Composable
private fun CompositionEditorContent(state: NutritionEditorState, actions: NutritionEditorActions) {
    NutritionFormSection("구성 이름", Modifier.fillMaxWidth(), "예: 운동 후 한 끼, 도시락 기본 구성") {
        OutlinedTextField(state.name, actions::updateName, Modifier.fillMaxWidth(), label = { Text("식단 구성 이름") },
            singleLine = true, enabled = !state.saving, isError = state.submitted && state.name.isBlank(), shape = FitnessShape.input)
    }
    FoodPortionList(state.portions, actions::updateAmount, actions::removeFood, Modifier.fillMaxWidth(), enabled = !state.saving && !state.loading)
    NutritionFormSection("식품 추가", Modifier.fillMaxWidth()) {
        AppTextField(state.query, actions::search, Modifier.fillMaxWidth(), label = { Text("식품 이름 검색") }, enabled = !state.saving)
        if (state.results.isNotEmpty()) Text("식품 ${state.results.size}개 · 목록 안에서 스크롤", style = MaterialTheme.typography.labelLarge)
        FoodSearchResults(state.results, actions::addFood, Modifier.fillMaxWidth(), enabled = !state.saving && !state.loading,
            includedFoodIds = state.portions.map { it.food.id }.toSet())
        if (state.portions.isEmpty() && state.results.isEmpty() && !state.loading) Text("함께 먹는 식품을 검색해 추가하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NutritionFormSection("다시 찾기 쉽게", Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(state.favorite, enabled = !state.saving, role = Role.Checkbox,
            onValueChange = actions::updateFavorite), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(state.favorite, onCheckedChange = null)
            Text("즐겨찾기에 표시", Modifier.padding(start = 8.dp))
        }
        OutlinedTextField(state.memo, actions::updateMemo, Modifier.fillMaxWidth(), label = { Text("메모 (선택)") },
            enabled = !state.saving, shape = FitnessShape.input)
    }
}

@Composable
private fun FoodRegistrationContent(state: NutritionEditorState, actions: NutritionEditorActions) {
    var additional by rememberSaveable { mutableStateOf(false) }
    var showUnits by remember { mutableStateOf(false) }
    NutritionFormSection("어떤 식품인가요?", Modifier.fillMaxWidth()) {
        OutlinedTextField(state.field(FoodEntryField.NAME), { actions.updateField(FoodEntryField.NAME, it) }, Modifier.fillMaxWidth(),
            label = { Text("식품 이름") }, singleLine = true, enabled = !state.saving,
            isError = state.submitted && state.field(FoodEntryField.NAME).isBlank(), shape = FitnessShape.input)
        AppTextField(state.field(FoodEntryField.BRAND), { actions.updateField(FoodEntryField.BRAND, it) }, Modifier.fillMaxWidth(), label = { Text("브랜드 (선택)") }, enabled = !state.saving)
    }
    NutritionFormSection("영양정보 기준량", Modifier.fillMaxWidth(), "포장지 또는 영양표에 적힌 기준량을 입력하세요.") {
        NutritionNumberField(state.field(FoodEntryField.BASIS), { actions.updateField(FoodEntryField.BASIS, it) }, "기준량", NutritionUnit.display(state.basisUnit),
            Modifier.fillMaxWidth(), enabled = !state.saving, showError = state.submitted, positive = true)
        Box {
            AppOutlinedButton({ showUnits = true }, enabled = !state.saving) { Text("단위 · ${NutritionUnit.display(state.basisUnit)}") }
            DropdownMenu(showUnits, { showUnits = false }) {
                NutritionUnit.options().forEach { unit -> DropdownMenuItem(text = { Text(NutritionUnit.display(unit)) },
                    onClick = { actions.updateUnit(unit); showUnits = false }) }
            }
        }
    }
    NutritionFormSection("기본 영양정보", Modifier.fillMaxWidth(), "위 기준량에 들어 있는 값입니다.") {
        NutritionFieldGrid(listOf(FoodEntryField.CALORIES, FoodEntryField.PROTEIN, FoodEntryField.CARBS, FoodEntryField.FAT),
            state::field, actions::updateField, enabled = !state.saving, submitted = state.submitted)
        TextButton({ additional = !additional }, enabled = !state.saving) { Text(if (additional) "추가 영양정보 접기" else "추가 영양정보 3개 입력") }
        if (additional || state.submitted) {
            Text("식품 등록에는 나트륨·당류·포화지방도 필요합니다. 실제 0인 값만 0으로 입력하세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NutritionFieldGrid(listOf(FoodEntryField.SODIUM, FoodEntryField.SUGARS, FoodEntryField.SATURATED_FAT),
                state::field, actions::updateField, enabled = !state.saving, submitted = state.submitted)
        }
    }
    NutritionFormSection("영양정보 출처", Modifier.fillMaxWidth()) {
        listOf("manual" to "포장지 · 라벨", "manual_estimate" to "직접 추정").forEach { (source, label) ->
            AppOutlinedButton({ actions.updateSource(source) }, Modifier.fillMaxWidth(), selected = state.sourceType == source, enabled = !state.saving) { Text(label) }
        }
        Text(if (state.sourceType == "manual_estimate") "입력한 값은 추정 영양정보로 저장합니다." else "라벨의 영양정보를 그대로 입력하세요.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    NutritionFormSection("등록 미리보기", Modifier.fillMaxWidth()) {
        Text(state.field(FoodEntryField.NAME).ifBlank { "식품 이름" }, fontWeight = FontWeight.SemiBold)
        Text("${state.field(FoodEntryField.BASIS)} ${NutritionUnit.display(state.basisUnit)} 기준", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val profile = NutritionProfile.builder().apply {
            listOf(FoodEntryField.CALORIES to NutritionProfile.CALORIES_KCAL, FoodEntryField.PROTEIN to NutritionProfile.PROTEIN_GRAMS,
                FoodEntryField.CARBS to NutritionProfile.CARBS_GRAMS, FoodEntryField.FAT to NutritionProfile.FAT_GRAMS).forEach { (field, key) ->
                state.field(field).toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0.0 }?.let { value(key, it) }
            }
        }.build()
        NutritionTotalPreview(NutritionTotals.builder().add(profile).build())
    }
}
