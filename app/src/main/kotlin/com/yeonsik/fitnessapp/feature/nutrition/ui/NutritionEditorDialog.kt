package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.yeonsik.fitnessapp.feature.nutrition.model.IngredientCatalogFile
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionRegistrationKind
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionConsistencyPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import com.yeonsik.fitnessapp.core.ui.*
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.foodPortionTotals

@Composable
internal fun NutritionEditorDialog(
    state: NutritionEditorState,
    actions: NutritionEditorActions,
    onUseComposition: (String) -> Unit,
    onUseRecipe: (NutritionFood) -> Unit = {}
) {
    if (!state.open) return
    if (state.brandSearch != null) {
        PriceTraceBrandPicker(state, actions)
        return
    }
    val title = when (state.mode) {
        NutritionEditorMode.LIBRARY -> "내 식단 · 식품"
        NutritionEditorMode.COMPOSITION -> "식단 구성"
        NutritionEditorMode.RECIPE -> "내 요리"
        NutritionEditorMode.FOOD -> "제품 · 재료 등록"
    }
    key(state.mode) {
        NutritionEntryFrame(title, actions::close, closeEnabled = !state.saving,
            subtitle = "자주 먹는 음식을 다음 기록에 불러오세요",
            footer = if (state.mode == NutritionEditorMode.LIBRARY) null else ({
                if (state.mode == NutritionEditorMode.COMPOSITION || state.mode == NutritionEditorMode.RECIPE) {
                    NutritionTotalPreview(foodPortionTotals(state.portions), label = "식품 ${state.portions.size}개")
                }
                AppButton(actions::save, Modifier.fillMaxWidth().testTag("nutrition-register-save"), enabled = !state.saving && !state.loading &&
                    (state.mode != NutritionEditorMode.FOOD || !state.productPicker.loading)) {
                    Text(if (state.saving) "저장 중…" else when (state.mode) {
                        NutritionEditorMode.FOOD -> "등록하기"
                        NutritionEditorMode.RECIPE -> "내 요리 저장하기"
                        else -> "식단 구성 저장하기"
                    })
                }
            })
        ) {
            if (state.mode != NutritionEditorMode.LIBRARY) TextButton(actions::showLibrary, enabled = !state.saving) { Text("내 식단 목록") }
            state.notice?.let { Text(it, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            when (state.mode) {
                NutritionEditorMode.LIBRARY -> NutritionLibraryContent(state, actions, onUseComposition, onUseRecipe)
                NutritionEditorMode.COMPOSITION, NutritionEditorMode.RECIPE -> CompositionEditorContent(state, actions, state.mode == NutritionEditorMode.RECIPE)
                NutritionEditorMode.FOOD -> FoodRegistrationContent(state, actions)
            }
        }
    }
}

@Composable
private fun NutritionLibraryContent(state: NutritionEditorState, actions: NutritionEditorActions,
    onUseComposition: (String) -> Unit, onUseRecipe: (NutritionFood) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var fileError by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            fileError = null
            val contents = runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= IngredientCatalogFile.MAX_BYTES) { "재료 파일은 1MB 이하로 선택하세요." }
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                } ?: throw IllegalArgumentException("선택한 파일을 읽을 수 없습니다.")
            } }
            contents.onSuccess(actions::previewIngredientFile).onFailure { fileError = it.message ?: "재료 파일을 읽지 못했습니다." }
        }
    }
    AppButton(actions::newComposition, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("새 식단 구성") }
    AppButton(actions::newRecipe, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("내 요리 등록 · 레시피 저장") }
    AppOutlinedButton(actions::newFood, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("식품 등록") }
    AppOutlinedButton({ filePicker.launch(arrayOf("application/json", "text/json", "text/plain")) },
        Modifier.fillMaxWidth(), enabled = !state.loading && !state.saving) { Text("재료 영양정보 JSON 가져오기") }
    fileError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    NutritionFormSection("재료 파일 형식", Modifier.fillMaxWidth(), "fitness.ingredient-catalog.v1 · JSON · 최대 1MB · 재료 2,000개") {
        Text("기본 영양정보에는 calories_kcal, carbs_grams, protein_grams, fat_grams가 필요합니다. 기준량 단위는 g 또는 ml입니다.",
            style = MaterialTheme.typography.bodySmall)
        state.importPreview?.let { preview ->
            Text("추가 ${preview.newCount}개 · 수정 ${preview.updatedCount}개 · 동일 ${preview.unchangedCount}개")
            AppButton(actions::applyIngredientFile, Modifier.fillMaxWidth(), enabled = !state.loading && !state.saving && preview.changedCount > 0) {
                Text(if (preview.changedCount == 0) "모든 재료가 최신입니다" else "변경 항목 적용")
            }
        }
    }
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
    state.recipes.forEach { recipe ->
        val calories = recipe.profile.value(NutritionProfile.CALORIES_KCAL)
            ?.let(NutritionCalculator::trim) ?: "미확인"
        NutritionFormSection(recipe.displayName(), Modifier.fillMaxWidth(),
            "1인분 · ${recipe.basisLabel()} · $calories kcal") {
            NutritionTotalPreview(NutritionTotals.builder().add(recipe.profile).build(), label = "1인분")
            AppButton({ onUseRecipe(recipe) }, Modifier.fillMaxWidth(), enabled = !state.loading) { Text("이번 식단에 추가") }
        }
    }
    NutritionFormSection("등록한 제품", Modifier.fillMaxWidth(), "기존 제품의 영양정보를 유지하며 브랜드 연결을 관리하세요.") {
        AppTextField(state.query, actions::search, Modifier.fillMaxWidth(), label = { Text("제품 이름 검색") }, enabled = !state.saving)
        if (state.packagedFoods.isEmpty() && !state.loading) Text("등록한 제품이 없습니다.")
    }
    state.packagedFoods.forEach { food ->
        NutritionFormSection(food.displayName(), Modifier.fillMaxWidth(), food.packagedVariantLabel()) {
            food.priceTraceBrand?.let { PriceTraceBrandSnapshot(it) }
            if (food.id in state.brandEditableFoodIds) {
                TextButton({ actions.editFoodBrand(food.id) }, enabled = !state.saving && !state.loading,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(if (food.priceTraceBrand == null) "PriceTrace 브랜드 연결" else "PriceTrace 브랜드 변경 · 해제")
                }
            }
        }
    }
}

@Composable
private fun CompositionEditorContent(state: NutritionEditorState, actions: NutritionEditorActions, recipe: Boolean) {
    NutritionFormSection(if (recipe) "요리 이름" else "구성 이름", Modifier.fillMaxWidth(),
        if (recipe) "재료 전체량을 나눌 완성 인분 수를 입력하세요." else "예: 운동 후 한 끼, 도시락 기본 구성") {
        OutlinedTextField(state.name, actions::updateName, Modifier.fillMaxWidth(), label = { Text(if (recipe) "요리 이름" else "식단 구성 이름") },
            singleLine = true, enabled = !state.saving, isError = state.submitted && state.name.isBlank(), shape = FitnessShape.input)
        if (recipe) {
            NutritionNumberField(state.recipeServings, actions::updateRecipeServings, "완성 인분 수", "인분", Modifier.fillMaxWidth(),
                enabled = !state.saving, showError = state.submitted, positive = true)
            NutritionNumberField(state.cookedWeightGrams, actions::updateCookedWeight, "조리 후 전체 무게 (선택)", "g",
                Modifier.fillMaxWidth(), enabled = !state.saving, required = false, positive = true)
        }
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NutritionRegistrationKind.entries.forEach { kind ->
                AppOutlinedButton({ actions.updateRegistrationKind(kind) }, Modifier.weight(1f),
                    enabled = !state.saving, selected = state.registrationKind == kind) { Text(kind.label) }
            }
        }
        if (state.registrationKind == NutritionRegistrationKind.PRODUCT) {
            NutritionProductPicker(state.productPicker, actions, enabled = !state.saving)
        }
        OutlinedTextField(state.field(FoodEntryField.NAME), { actions.updateField(FoodEntryField.NAME, it) }, Modifier.fillMaxWidth().testTag("nutrition-product-name"),
            label = { Text("식품 이름") }, singleLine = true, enabled = !state.saving && state.productPicker.selected == null,
            isError = state.submitted && state.field(FoodEntryField.NAME).isBlank(), shape = FitnessShape.input)
        AppTextField(state.field(FoodEntryField.BRAND), { actions.updateField(FoodEntryField.BRAND, it) }, Modifier.fillMaxWidth(), label = { Text("브랜드 (선택)") },
            enabled = !state.saving && state.productPicker.selected == null)
    }
    if (state.registrationKind == NutritionRegistrationKind.PRODUCT) {
        NutritionFormSection("PriceTrace 브랜드 연결", Modifier.fillMaxWidth(), "선택 사항 · 라벨에 적힌 브랜드 이름은 그대로 보존됩니다.") {
            state.priceTraceBrand?.let { PriceTraceBrandSnapshot(it) }
            AppOutlinedButton(actions::openBrandSearch, Modifier.fillMaxWidth(), enabled = !state.saving) {
                Text(if (state.priceTraceBrand == null) "브랜드 검색" else "연결 브랜드 변경")
            }
            if (state.priceTraceBrand != null) TextButton(actions::clearBrand, enabled = !state.saving,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("브랜드 연결 해제") }
        }
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
    if (state.registrationKind == NutritionRegistrationKind.PRODUCT) NutritionFormSection("제품 1개 용량", Modifier.fillMaxWidth(),
        "예: 1개 90g이면 90을 입력합니다. 영양 기준량 단위와 같은 g 또는 ml로 저장합니다.") {
        NutritionNumberField(state.packageAmount, actions::updatePackageAmount, "제품 1개 용량", NutritionUnit.display(state.basisUnit),
            Modifier.fillMaxWidth(), enabled = !state.saving, showError = state.submitted, positive = true)
    }
    NutritionFormSection("기본 영양정보", Modifier.fillMaxWidth(), "위 기준량에 들어 있는 값입니다.") {
        Text(NutritionConsistencyPolicy.LABEL_GUIDANCE, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        NutritionFieldGrid(listOf(FoodEntryField.CALORIES, FoodEntryField.PROTEIN, FoodEntryField.CARBS, FoodEntryField.FAT),
            state::field, actions::updateField, enabled = !state.saving, submitted = state.submitted)
        TextButton({ additional = !additional }, enabled = !state.saving) { Text(if (additional) "추가 영양정보 접기" else "나트륨·당류·포화지방·트랜스지방 입력 (선택)") }
        if (additional || state.submitted) {
            Text("모르는 값은 비워 두세요. 실제 0인 값만 0으로 입력하세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            NutritionFieldGrid(listOf(FoodEntryField.SODIUM, FoodEntryField.SUGARS, FoodEntryField.SATURATED_FAT),
                state::field, actions::updateField, enabled = !state.saving, required = false, submitted = state.submitted)
            NutritionFieldGrid(listOf(FoodEntryField.TRANS_FAT), state::field, actions::updateField,
                enabled = !state.saving, required = false, submitted = state.submitted)
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
