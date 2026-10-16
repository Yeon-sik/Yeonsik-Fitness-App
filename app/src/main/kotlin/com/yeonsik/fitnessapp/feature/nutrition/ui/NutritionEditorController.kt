package com.yeonsik.fitnessapp.feature.nutrition.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionTemplateRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ExecutorService

enum class NutritionEditorMode { LIBRARY, COMPOSITION, RECIPE, FOOD }

enum class FoodEntryField(val label: String, val unit: String = "") {
    NAME("식품 이름"), BRAND("브랜드 (선택)"), BASIS("기준량"),
    CALORIES("칼로리", "kcal"), PROTEIN("단백질", "g"), CARBS("탄수화물 (총)", "g"),
    FAT("지방 (총)", "g"), SODIUM("나트륨", "mg"), SUGARS("당류", "g"), SATURATED_FAT("포화지방", "g"),
    TRANS_FAT("트랜스지방 (선택)", "g")
}

data class NutritionEditorState(
    val ownerId: String = "",
    val open: Boolean = false,
    val mode: NutritionEditorMode = NutritionEditorMode.LIBRARY,
    val templates: List<CompositionTemplate> = emptyList(),
    val recipes: List<NutritionFood> = emptyList(),
    val portions: List<FoodPortionDraft> = emptyList(),
    val query: String = "",
    val results: List<NutritionFood> = emptyList(),
    val compositionId: String? = null,
    val revision: Int = 1,
    val name: String = "",
    val memo: String = "",
    val favorite: Boolean = false,
    val fields: Map<FoodEntryField, String> = mapOf(FoodEntryField.BASIS to "100"),
    val basisUnit: String = NutritionUnit.GRAM,
    val sourceType: String = "manual",
    val registrationKind: NutritionRegistrationKind = NutritionRegistrationKind.PRODUCT,
    val packageAmount: String = "",
    val recipeServings: String = "1",
    val cookedWeightGrams: String = "",
    val importPreview: IngredientImportPreview? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
    val notice: String? = null
) {
    fun field(field: FoodEntryField): String = fields[field].orEmpty()
}

interface NutritionEditorActions {
    fun close()
    fun showLibrary()
    fun newComposition()
    fun newRecipe() {}
    fun resumeComposition()
    fun editComposition(id: String)
    fun newFood()
    fun search(query: String)
    fun addFood(food: NutritionFood)
    fun updateAmount(foodId: String, value: String)
    fun removeFood(foodId: String)
    fun updateName(value: String)
    fun updateMemo(value: String)
    fun updateFavorite(value: Boolean)
    fun updateField(field: FoodEntryField, value: String)
    fun updateUnit(value: String)
    fun updateSource(value: String)
    fun updateRegistrationKind(value: NutritionRegistrationKind) {}
    fun updatePackageAmount(value: String) {}
    fun updateRecipeServings(value: String) {}
    fun updateCookedWeight(value: String) {}
    fun previewIngredientFile(contents: String) {}
    fun applyIngredientFile() {}
    fun save()
}

/** Nutrition-owned editor; the Meal ViewModel owns its lifetime and worker executor. */
class NutritionEditorController(
    private val savedState: SavedStateHandle,
    private val catalog: NutritionCatalogRepositoryApi,
    private val templates: NutritionTemplateRepositoryApi?,
    private val executor: ExecutorService
) : NutritionEditorActions {
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData(NutritionEditorState())
    val uiState: LiveData<NutritionEditorState> = mutableState
    private var version = 0L
    private var restoringPortions = false
    private val state: NutritionEditorState get() = mutableState.value ?: NutritionEditorState()

    fun synchronizeOwner() {
        val owner = catalog.currentOwnerId()
        if (state.ownerId == owner) return
        ++version
        if (savedState.get<String>(KEY_OWNER) != owner) clearStorage()
        val fields = FoodEntryField.entries.associateWith { savedState.get<String>("$PREFIX.field.${it.name}").orEmpty() }
            .toMutableMap().apply { if (get(FoodEntryField.BASIS).isNullOrEmpty()) put(FoodEntryField.BASIS, "100") }
        val restoredMode = savedState.get<String>(KEY_MODE)?.let { runCatching { NutritionEditorMode.valueOf(it) }.getOrNull() }
        mutableState.value = NutritionEditorState(
            ownerId = owner,
            open = savedState[KEY_OPEN] ?: false,
            mode = restoredMode ?: NutritionEditorMode.LIBRARY,
            name = savedState[KEY_NAME] ?: "",
            memo = savedState[KEY_MEMO] ?: "",
            favorite = savedState[KEY_FAVORITE] ?: false,
            compositionId = savedState[KEY_ID],
            revision = savedState[KEY_REVISION] ?: 1,
            fields = fields,
            basisUnit = savedState[KEY_UNIT] ?: NutritionUnit.GRAM,
            sourceType = savedState[KEY_SOURCE] ?: "manual",
            registrationKind = savedState.get<String>(KEY_REGISTRATION_KIND)?.let { runCatching { NutritionRegistrationKind.valueOf(it) }.getOrNull() }
                ?: NutritionRegistrationKind.PRODUCT,
            packageAmount = savedState[KEY_PACKAGE_AMOUNT] ?: "",
            recipeServings = savedState[KEY_RECIPE_SERVINGS] ?: "1",
            cookedWeightGrams = savedState[KEY_COOKED_WEIGHT] ?: ""
        )
    }

    fun open() {
        synchronizeOwner()
        val ids = savedState.get<ArrayList<String>>(KEY_FOOD_IDS).orEmpty()
        val amounts = savedState.get<ArrayList<String>>(KEY_AMOUNTS).orEmpty()
        restoringPortions = true
        update { it.copy(open = true, loading = true, error = null) }
        task({
            val restored = ids.mapIndexed { index, id ->
                val food = catalog.findFoodById(id) ?: throw IllegalStateException("구성에 저장한 식품을 찾지 못했습니다. 다시 추가하세요.")
                FoodPortionDraft(food, amounts.getOrElse(index) { NutritionCalculator.trim(food.basisAmount) })
            }
            Triple(restored, listTemplates(), catalog.savedRecipes())
        }) { current, result ->
            val (restored, savedTemplates, recipes) = result
            current.copy(portions = restored, templates = savedTemplates, recipes = recipes)
        }
    }

    override fun close() {
        if (state.saving) return
        ++version
        update { it.copy(open = false, loading = false, error = null) }
        restoringPortions = false
    }

    override fun showLibrary() {
        if (state.saving) return
        update { it.copy(mode = NutritionEditorMode.LIBRARY, error = null) }
        task({ listTemplates() to catalog.savedRecipes() }) { current, (found, recipes) ->
            current.copy(templates = found, recipes = recipes)
        }
    }

    override fun newComposition() {
        if (state.saving) return
        ++version
        restoringPortions = false
        update { it.copy(mode = NutritionEditorMode.COMPOSITION, compositionId = null, revision = 1, recipeServings = "1", cookedWeightGrams = "",
            name = "", memo = "", favorite = false, portions = emptyList(), query = "", results = emptyList(),
            loading = false, submitted = false, notice = null, error = null) }
        search("")
    }

    override fun newRecipe() {
        if (state.saving) return
        ++version
        restoringPortions = false
        update { it.copy(mode = NutritionEditorMode.RECIPE, compositionId = null, revision = 1,
            name = "", memo = "", favorite = false, portions = emptyList(), query = "", results = emptyList(),
            recipeServings = "1", cookedWeightGrams = "", loading = false, submitted = false, notice = null, error = null) }
        search("")
    }

    override fun resumeComposition() = update { it.copy(mode = NutritionEditorMode.COMPOSITION, notice = null) }

    override fun editComposition(id: String) {
        task({
            val template = templates?.findTemplate(id) ?: throw IllegalStateException("식단 구성을 찾지 못했습니다.")
            val restored = template.groups.sortedBy { it.orderIndex }.flatMap { it.members.sortedBy { member -> member.orderIndex } }
                .filter { it.defaultSelected }.map { member ->
                    val food = catalog.findFoodById(member.nutritionFoodId.orEmpty())
                        ?: throw IllegalStateException("${member.name} 식품을 찾지 못했습니다. 식품 목록을 확인하세요.")
                    FoodPortionDraft(food, NutritionCalculator.trim(NutritionUnit.convert(member.quantity, member.unit, food.basisUnit)))
                }
            template to restored
        }) { current, result ->
            val (template, restored) = result
            val metadata = template.sourceReference?.let { runCatching { JSONObject(it) }.getOrNull() }
            current.copy(mode = NutritionEditorMode.COMPOSITION, compositionId = template.id, revision = template.revision + 1,
                name = template.name, memo = metadata?.optString("memo").orEmpty(),
                favorite = metadata?.optBoolean("favorite") ?: false, portions = restored, query = "",
                results = emptyList(), submitted = false, notice = null)
        }
    }

    override fun newFood() {
        if (state.saving) return
        ++version
        update { it.copy(mode = NutritionEditorMode.FOOD, registrationKind = NutritionRegistrationKind.PRODUCT,
            loading = false, submitted = false, error = null, notice = null) }
    }

    override fun search(query: String) {
        update { it.copy(query = query, error = null) }
        task({
            (catalog.searchFoods(query) + catalog.searchPackagedFoods(query, 40))
                .filterNot { it.isDiningOutMenu() || it.isDiningOutComponent() ||
                    state.mode == NutritionEditorMode.RECIPE && it.kind == NutritionFood.KIND_RECIPE }
                .distinctBy { it.id }.take(30)
        }) { current, results -> current.copy(results = results) }
    }

    override fun addFood(food: NutritionFood) = update {
        it.copy(portions = addFoodPortion(it.portions, food), query = "", results = emptyList(), error = null)
    }
    override fun updateAmount(foodId: String, value: String) = update {
        it.copy(portions = it.portions.map { portion -> if (portion.food.id == foodId) portion.copy(quantity = value) else portion }, error = null)
    }
    override fun removeFood(foodId: String) = update { it.copy(portions = it.portions.filterNot { portion -> portion.food.id == foodId }) }
    override fun updateName(value: String) = update { it.copy(name = value, error = null) }
    override fun updateMemo(value: String) = update { it.copy(memo = value) }
    override fun updateFavorite(value: Boolean) = update { it.copy(favorite = value) }
    override fun updateField(field: FoodEntryField, value: String) = update { it.copy(fields = it.fields + (field to value), error = null) }
    override fun updateUnit(value: String) = update { it.copy(basisUnit = value, error = null) }
    override fun updateSource(value: String) = update { it.copy(sourceType = value) }
    override fun updateRegistrationKind(value: NutritionRegistrationKind) = update { it.copy(registrationKind = value) }
    override fun updatePackageAmount(value: String) = update { it.copy(packageAmount = value, error = null) }
    override fun updateRecipeServings(value: String) = update { it.copy(recipeServings = value, error = null) }
    override fun updateCookedWeight(value: String) = update { it.copy(cookedWeightGrams = value, error = null) }

    override fun previewIngredientFile(contents: String) {
        val owner = state.ownerId
        update { it.copy(importPreview = null, error = null) }
        task({
            val batch = IngredientCatalogFile.parse(contents)
            catalog.previewIngredientImport(owner, batch)
        }) { current, preview -> current.copy(importPreview = preview, notice = null) }
    }

    override fun applyIngredientFile() {
        val preview = state.importPreview ?: return
        task({ catalog.applyIngredientImport(preview) }, saving = true) { current, applied ->
            current.copy(importPreview = null, notice = "재료 파일 적용 완료 · 새 재료 ${applied.newCount}개, 수정 ${applied.updatedCount}개, 동일 ${applied.unchangedCount}개")
        }
    }

    override fun save() {
        val draft = state
        if (draft.saving || draft.loading || !draft.open) return
        update { it.copy(submitted = true, error = null) }
        try {
            when (draft.mode) {
                NutritionEditorMode.COMPOSITION -> saveComposition(draft)
                NutritionEditorMode.RECIPE -> saveRecipe(draft)
                NutritionEditorMode.FOOD -> saveFood(draft)
                NutritionEditorMode.LIBRARY -> Unit
            }
        } catch (error: Exception) {
            update { it.copy(error = error.message ?: "입력을 확인하세요.") }
        }
    }

    private fun saveComposition(draft: NutritionEditorState) {
        val repository = templates ?: throw IllegalStateException("식단 구성 저장을 준비하지 못했습니다.")
        require(draft.name.isNotBlank()) { "식단 구성 이름을 입력하세요." }
        require(draft.portions.isNotEmpty()) { "식품을 하나 이상 추가하세요." }
        require(draft.portions.all { it.profile != null }) { "각 식품의 섭취량을 0보다 큰 숫자로 입력하세요." }
        val members = draft.portions.mapIndexed { index, portion ->
            CompositionMember.fromFood(UUID.randomUUID().toString(), portion.food, portion.amount!!, true, index)
        }
        val template = CompositionTemplate(
            draft.compositionId ?: UUID.randomUUID().toString(), draft.ownerId, draft.name.trim(),
            CompositionTemplate.KIND_MEAL_PRESET, null,
            JSONObject().put("memo", draft.memo.trim()).put("favorite", draft.favorite).toString(), draft.revision,
            listOf(CompositionGroup.optionalMany(UUID.randomUUID().toString(), "foods", "other", "식품 구성", 0, members))
        )
        task({
            check(repository.currentOwnerId() == draft.ownerId) { "계정이 변경되었습니다. 식단 구성을 다시 여세요." }
            repository.saveTemplate(template)
            listTemplates()
        }, saving = true) { current, found ->
            current.copy(mode = NutritionEditorMode.LIBRARY, templates = found, portions = emptyList(),
                compositionId = null, name = "", memo = "", favorite = false, notice = "식단 구성을 저장했습니다.")
        }
    }

    private fun saveFood(draft: NutritionEditorState) {
        require(draft.field(FoodEntryField.NAME).isNotBlank()) { "식품 이름을 입력하세요." }
        val basis = requiredNumber(draft.field(FoodEntryField.BASIS), "기준량").also { require(it > 0.0) { "기준량은 0보다 커야 합니다." } }
        val profile = NutritionProfile.builder()
            .value(NutritionProfile.CALORIES_KCAL, requiredNumber(draft.field(FoodEntryField.CALORIES), "칼로리"))
            .value(NutritionProfile.PROTEIN_GRAMS, requiredNumber(draft.field(FoodEntryField.PROTEIN), "단백질"))
            .value(NutritionProfile.CARBS_GRAMS, requiredNumber(draft.field(FoodEntryField.CARBS), "탄수화물"))
            .value(NutritionProfile.FAT_GRAMS, requiredNumber(draft.field(FoodEntryField.FAT), "지방"))
            .value(NutritionProfile.SODIUM_MG, optionalNumber(draft.field(FoodEntryField.SODIUM), "나트륨"))
            .value(NutritionProfile.SUGARS_GRAMS, optionalNumber(draft.field(FoodEntryField.SUGARS), "당류"))
            .value(NutritionProfile.SATURATED_FAT_GRAMS, optionalNumber(draft.field(FoodEntryField.SATURATED_FAT), "포화지방"))
            .value(NutritionProfile.TRANS_FAT_GRAMS, optionalNumber(draft.field(FoodEntryField.TRANS_FAT), "트랜스지방"))
            .build()
        NutritionConsistencyPolicy.validate(profile)
        val input = NutritionFoodInput(draft.field(FoodEntryField.NAME).trim(), draft.field(FoodEntryField.BRAND).trim().ifBlank { null },
            basis, draft.basisUnit, profile, draft.sourceType, draft.registrationKind,
            draft.packageAmount.trim().takeIf(String::isNotEmpty)?.let { requiredNumber(it, "제품 1개 용량") }, draft.basisUnit)
        if (draft.registrationKind == NutritionRegistrationKind.PRODUCT) require(input.packageAmount?.let { it > 0 } == true) { "제품 1개 용량을 입력하세요." }
        task({ catalog.saveManualFoodInput(AccountScope(draft.ownerId), input)
            ?: throw IllegalStateException("식품을 등록하지 못했습니다.") }, saving = true) { current, food ->
            current.copy(mode = NutritionEditorMode.LIBRARY, fields = mapOf(FoodEntryField.BASIS to "100"), packageAmount = "",
                notice = "${food.displayName()} ${draft.registrationKind.label}을 등록했습니다.")
        }
    }

    private fun saveRecipe(draft: NutritionEditorState) {
        require(draft.name.isNotBlank()) { "내 요리 이름을 입력하세요." }
        require(draft.portions.isNotEmpty()) { "재료를 하나 이상 추가하세요." }
        require(draft.portions.all { it.amount != null }) { "각 재료의 양을 확인하세요." }
        val servings = requiredNumber(draft.recipeServings, "완성 인분 수").also { require(it > 0) { "완성 인분 수를 0보다 크게 입력하세요." } }
        val cookedWeight = if (draft.cookedWeightGrams.isBlank()) null else
            requiredNumber(draft.cookedWeightGrams, "완성 후 무게").also { require(it > 0) { "완성 후 무게는 0보다 커야 합니다." } }
        val items = draft.portions.map { MealCompositionItem.from(it.food, it.amount!!) }
        task({ catalog.saveRecipe(draft.ownerId, draft.name.trim(), items, servings, cookedWeight) to catalog.savedRecipes() }, saving = true) { current, result ->
            val (recipe, recipes) = result
            current.copy(mode = NutritionEditorMode.LIBRARY, recipes = recipes, portions = emptyList(), name = "",
                recipeServings = "1", cookedWeightGrams = "", notice = "${recipe.name} · ${recipe.basisLabel()} 기준으로 저장했습니다.")
        }
    }

    private fun listTemplates(): List<CompositionTemplate> = templates?.listTemplates(CompositionTemplate.KIND_MEAL_PRESET)
        .orEmpty().sortedWith(compareByDescending<CompositionTemplate> { template ->
            template.sourceReference?.let { runCatching { JSONObject(it).optBoolean("favorite") }.getOrDefault(false) } ?: false
        }.thenBy { it.name })

    private fun optionalNumber(value: String, label: String): Double? =
        if (value.isBlank()) null else requiredNumber(value, label)

    private fun <T> task(work: () -> T, saving: Boolean = false, apply: (NutritionEditorState, T) -> NutritionEditorState) {
        if (state.saving) return
        val requestedOwner = state.ownerId
        val request = ++version
        update { it.copy(loading = !saving, saving = saving, error = null) }
        executor.execute {
            val result = runCatching {
                check(catalog.currentOwnerId() == requestedOwner) { "계정이 변경되었습니다. 등록 화면을 다시 여세요." }
                work()
            }
            main.post {
                if (request != version || state.ownerId != requestedOwner || catalog.currentOwnerId() != requestedOwner) return@post
                restoringPortions = false
                update(force = true) { current -> result.fold(
                    { value -> apply(current, value).copy(loading = false, saving = false, submitted = false) },
                    { error -> current.copy(loading = false, saving = false, error = error.message ?: "저장하지 못했습니다.") }
                ) }
            }
        }
    }

    private fun update(force: Boolean = false, block: (NutritionEditorState) -> NutritionEditorState) {
        if (state.saving && !force) return
        val next = block(state)
        mutableState.value = next
        savedState[KEY_OWNER] = next.ownerId
        savedState[KEY_OPEN] = next.open
        savedState[KEY_MODE] = next.mode.name
        savedState[KEY_NAME] = next.name
        savedState[KEY_MEMO] = next.memo
        savedState[KEY_FAVORITE] = next.favorite
        savedState[KEY_ID] = next.compositionId
        savedState[KEY_REVISION] = next.revision
        savedState[KEY_UNIT] = next.basisUnit
        savedState[KEY_SOURCE] = next.sourceType
        savedState[KEY_REGISTRATION_KIND] = next.registrationKind.name
        savedState[KEY_PACKAGE_AMOUNT] = next.packageAmount
        savedState[KEY_RECIPE_SERVINGS] = next.recipeServings
        savedState[KEY_COOKED_WEIGHT] = next.cookedWeightGrams
        if (!restoringPortions) {
            savedState[KEY_FOOD_IDS] = ArrayList(next.portions.map { it.food.id })
            savedState[KEY_AMOUNTS] = ArrayList(next.portions.map { it.quantity })
        }
        FoodEntryField.entries.forEach { field -> savedState["$PREFIX.field.${field.name}"] = next.field(field) }
    }

    private fun clearStorage() = savedState.keys().filter { it.startsWith(PREFIX) }.forEach { savedState.remove<Any>(it) }

    private companion object {
        const val PREFIX = "nutrition.editor"
        const val KEY_OWNER = "$PREFIX.owner"
        const val KEY_OPEN = "$PREFIX.open"
        const val KEY_MODE = "$PREFIX.mode"
        const val KEY_NAME = "$PREFIX.name"
        const val KEY_MEMO = "$PREFIX.memo"
        const val KEY_FAVORITE = "$PREFIX.favorite"
        const val KEY_ID = "$PREFIX.id"
        const val KEY_REVISION = "$PREFIX.revision"
        const val KEY_UNIT = "$PREFIX.unit"
        const val KEY_SOURCE = "$PREFIX.source"
        const val KEY_REGISTRATION_KIND = "$PREFIX.registration_kind"
        const val KEY_PACKAGE_AMOUNT = "$PREFIX.package_amount"
        const val KEY_RECIPE_SERVINGS = "$PREFIX.recipe_servings"
        const val KEY_COOKED_WEIGHT = "$PREFIX.cooked_weight"
        const val KEY_FOOD_IDS = "$PREFIX.food_ids"
        const val KEY_AMOUNTS = "$PREFIX.amounts"
        fun requiredNumber(value: String, label: String): Double {
            val amount = value.trim().toDoubleOrNull()
            require(amount != null && amount.isFinite() && amount >= 0.0) { "$label 값을 0 이상의 숫자로 입력하세요." }
            return amount
        }
    }
}
