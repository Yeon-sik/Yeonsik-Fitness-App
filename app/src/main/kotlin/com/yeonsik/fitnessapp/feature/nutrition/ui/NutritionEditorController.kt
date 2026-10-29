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
import com.yeonsik.fitnessapp.feature.nutrition.api.PriceTraceBrandCatalog
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
    val priceTraceBrand: PriceTraceBrandSelection? = null,
    val brandSearch: PriceTraceBrandSearchState? = null,
    val editingBrandFood: NutritionFood? = null,
    val packagedFoods: List<NutritionFood> = emptyList(),
    val brandEditableFoodIds: Set<String> = emptySet(),
    val recipeServings: String = "1",
    val cookedWeightGrams: String = "",
    val importPreview: IngredientImportPreview? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val submitted: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val productPicker: NutritionProductPickerState = NutritionProductPickerState(),
    val returnToMeal: Boolean = false
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
    fun openBrandSearch() {}
    fun editFoodBrand(foodId: String) {}
    fun searchBrands(query: String) {}
    fun moreBrands() {}
    fun closeBrandSearch() {}
    fun selectBrand(row: PriceTraceBrandRow) {}
    fun clearBrand() {}
    fun updateProductQuery(value: String) {}
    fun searchProducts() {}
    fun selectProduct(product: ProductReadV1) {}
    fun clearProductSelection() {}
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
    private val executor: ExecutorService,
    private val productLookup: NutritionProductLookup? = null,
    private val onFoodRegistered: (NutritionFood) -> Unit = {},
    private val brandCatalog: PriceTraceBrandCatalog? = null
) : NutritionEditorActions {
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableLiveData(NutritionEditorState())
    val uiState: LiveData<NutritionEditorState> = mutableState
    private var version = 0L
    private var restoringPortions = false
    private val state: NutritionEditorState get() = mutableState.value ?: NutritionEditorState()
    private val brandSearch = PriceTraceBrandSearchController(brandCatalog, catalog::currentOwnerId) { search ->
        update { it.copy(brandSearch = search) }
    }

    fun synchronizeOwner() {
        val owner = catalog.currentOwnerId()
        if (state.ownerId == owner) return
        // 복원 전에 빈 editor state를 저장하면 기존 draft의 owner/필드가 덮어써진다.
        brandSearch.dismiss(notify = false)
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
            priceTraceBrand = runCatching { PriceTraceBrandSelection.fromSnapshot(savedState[KEY_BRAND_ID],
                savedState[KEY_BRAND_NAME], savedState[KEY_PARENT_ID], savedState[KEY_PARENT_NAME]) }.getOrNull(),
            recipeServings = savedState[KEY_RECIPE_SERVINGS] ?: "1",
            cookedWeightGrams = savedState[KEY_COOKED_WEIGHT] ?: "",
            returnToMeal = savedState[KEY_RETURN_TO_MEAL] ?: false,
            productPicker = NutritionProductPickerState(
                query = savedState[KEY_PRODUCT_QUERY] ?: "",
                selected = NutritionProductDraftCodec.decode(savedState[KEY_PRODUCT_SELECTION]),
                manualFields = listOf(FoodEntryField.NAME, FoodEntryField.BRAND, FoodEntryField.BASIS)
                    .associateWith { savedState.get<String>("$PREFIX.product.manual.${it.name}").orEmpty() },
                manualUnit = savedState[KEY_PRODUCT_MANUAL_UNIT] ?: "",
                manualPackageAmount = savedState[KEY_PRODUCT_MANUAL_PACKAGE] ?: ""
            )
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
            Triple(restored, listTemplates(), catalog.savedRecipes()) to libraryProducts("")
        }) { current, result ->
            val (restored, savedTemplates, recipes) = result.first
            current.copy(portions = restored, templates = savedTemplates, recipes = recipes,
                packagedFoods = result.second.first, brandEditableFoodIds = result.second.second)
        }
    }

    override fun close() {
        if (state.saving) return
        brandSearch.dismiss()
        ++version
        update { it.copy(open = false, loading = false, error = null, editingBrandFood = null, productPicker = it.productPicker.copy(loading = false)) }
        restoringPortions = false
    }

    override fun showLibrary() {
        if (state.saving) return
        brandSearch.dismiss()
        update { it.copy(mode = NutritionEditorMode.LIBRARY, error = null, productPicker = it.productPicker.copy(loading = false)) }
        task({ Triple(listTemplates(), catalog.savedRecipes(), libraryProducts("")) }) { current, (found, recipes, products) ->
            current.copy(templates = found, recipes = recipes, query = "", editingBrandFood = null,
                packagedFoods = products.first, brandEditableFoodIds = products.second)
        }
    }

    override fun newComposition() {
        if (state.saving) return
        brandSearch.dismiss()
        ++version
        restoringPortions = false
        update { it.copy(mode = NutritionEditorMode.COMPOSITION, compositionId = null, revision = 1, recipeServings = "1", cookedWeightGrams = "",
            name = "", memo = "", favorite = false, portions = emptyList(), query = "", results = emptyList(),
            loading = false, submitted = false, notice = null, error = null, editingBrandFood = null) }
        search("")
    }

    override fun newRecipe() {
        if (state.saving) return
        brandSearch.dismiss()
        ++version
        restoringPortions = false
        update { it.copy(mode = NutritionEditorMode.RECIPE, compositionId = null, revision = 1,
            name = "", memo = "", favorite = false, portions = emptyList(), query = "", results = emptyList(),
            recipeServings = "1", cookedWeightGrams = "", loading = false, submitted = false, notice = null, error = null, editingBrandFood = null) }
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
        brandSearch.dismiss()
        ++version
        update { it.copy(mode = NutritionEditorMode.FOOD, registrationKind = NutritionRegistrationKind.PRODUCT,
            loading = false, submitted = false, error = null, notice = null, editingBrandFood = null, returnToMeal = false,
            productPicker = it.productPicker.copy(loading = false)) }
    }

    fun openProductForMeal(fields: Map<FoodEntryField, String>, basisUnit: String, packageAmount: String) {
        synchronizeOwner()
        if (state.saving) return
        ++version
        update { it.copy(open = true, mode = NutritionEditorMode.FOOD, returnToMeal = true,
            registrationKind = NutritionRegistrationKind.PRODUCT, fields = fields, basisUnit = basisUnit,
            packageAmount = packageAmount, sourceType = "manual", priceTraceBrand = null, editingBrandFood = null, productPicker = NutritionProductPickerState(),
            loading = false, submitted = false, error = null, notice = null) }
    }

    override fun search(query: String) {
        update { it.copy(query = query, error = null) }
        if (state.mode == NutritionEditorMode.LIBRARY) {
            task({ libraryProducts(query) }) { current, (foods, editable) ->
                current.copy(packagedFoods = foods, brandEditableFoodIds = editable)
            }
            return
        }
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
    override fun updateField(field: FoodEntryField, value: String) {
        if (state.productPicker.selected != null && field in listOf(FoodEntryField.NAME, FoodEntryField.BRAND)) return
        cancelProductNutritionRead()
        val changedBrand = field == FoodEntryField.BRAND && state.field(field).trim() != value.trim()
        if (changedBrand) brandSearch.dismiss()
        update { it.copy(fields = it.fields + (field to value), error = null,
            priceTraceBrand = if (changedBrand) null else it.priceTraceBrand) }
    }
    override fun updateUnit(value: String) {
        cancelProductNutritionRead()
        update { it.copy(basisUnit = value, error = null) }
    }
    override fun updateSource(value: String) = update { it.copy(sourceType = value) }
    override fun updateRegistrationKind(value: NutritionRegistrationKind) {
        if (state.saving) return
        if (value != NutritionRegistrationKind.PRODUCT) {
            brandSearch.dismiss()
            clearProductSelection()
        }
        update { it.copy(registrationKind = value, priceTraceBrand = if (value == NutritionRegistrationKind.PRODUCT) it.priceTraceBrand else null) }
    }
    override fun updatePackageAmount(value: String) = update { it.copy(packageAmount = value, error = null) }

    override fun openBrandSearch() {
        if (state.saving || state.mode != NutritionEditorMode.FOOD || state.registrationKind != NutritionRegistrationKind.PRODUCT) return
        update { it.copy(editingBrandFood = null) }
        brandSearch.open(state.field(FoodEntryField.BRAND))
    }

    override fun editFoodBrand(foodId: String) {
        val owner = state.ownerId
        task({
            check(catalog.canEditPriceTraceBrand(foodId, owner)) { "현재 계정의 비공개 제품만 브랜드를 변경할 수 있습니다." }
            catalog.findFoodById(foodId) ?: error("제품을 찾지 못했습니다.")
        }) { current, food ->
            // 검색은 별도 executor가 담당하며 저장된 snapshot을 자동 교체하지 않는다.
            main.post {
                if (state.editingBrandFood?.id == food.id && state.ownerId == owner && catalog.currentOwnerId() == owner)
                    brandSearch.open(listOf(food.subBrandName, food.brandName, food.brand).firstOrNull { !it.isNullOrBlank() }.orEmpty())
            }
            current.copy(editingBrandFood = food)
        }
    }

    override fun searchBrands(query: String) = brandSearch.search(query)
    override fun moreBrands() { state.brandSearch?.let { brandSearch.search(it.query, append = true, delay = 0) } }
    override fun closeBrandSearch() { brandSearch.dismiss(); update { it.copy(editingBrandFood = null) } }
    override fun selectBrand(row: PriceTraceBrandRow) {
        if (!brandSearch.isCurrent(row)) return
        val food = state.editingBrandFood
        if (food == null) {
            update { it.copy(priceTraceBrand = row.selection) }
            closeBrandSearch()
        } else saveExistingBrand(food, row.selection)
    }
    override fun clearBrand() {
        val food = state.editingBrandFood
        if (food == null) { update { it.copy(priceTraceBrand = null) }; closeBrandSearch() }
        else saveExistingBrand(food, null)
    }
    private fun saveExistingBrand(food: NutritionFood, selection: PriceTraceBrandSelection?) {
        val owner = state.ownerId
        val scope = brandCatalog?.brandCatalogScope()
        brandSearch.dismiss()
        task({
            if (selection != null) check(brandCatalog?.brandCatalogScope() == scope) { "PriceTrace 연결 설정이 변경되었습니다. 다시 선택하세요." }
            catalog.setPriceTraceBrand(food.id, owner, selection)
        }, saving = true) { current, saved ->
            current.copy(editingBrandFood = null, brandSearch = null,
                packagedFoods = current.packagedFoods.map { if (it.id == saved.id) saved else it },
                notice = if (selection == null) "PriceTrace 브랜드 연결을 해제했습니다." else "PriceTrace 브랜드를 연결했습니다.")
        }
    }

    fun dispose() = brandSearch.close()

    private fun libraryProducts(query: String): Pair<List<NutritionFood>, Set<String>> {
        val owner = catalog.currentOwnerId()
        val foods = catalog.searchPackagedFoods(query, 40)
        return foods to foods.filter { catalog.canEditPriceTraceBrand(it.id, owner) }.map { it.id }.toSet()
    }
    override fun updateRecipeServings(value: String) = update { it.copy(recipeServings = value, error = null) }
    override fun updateCookedWeight(value: String) = update { it.copy(cookedWeightGrams = value, error = null) }

    override fun updateProductQuery(value: String) {
        if (state.saving) return
        ++version
        update { it.copy(productPicker = it.productPicker.copy(query = value, results = emptyList(),
            loading = false, searched = false, error = null)) }
    }

    override fun searchProducts() {
        if (state.saving || state.mode != NutritionEditorMode.FOOD || state.registrationKind != NutritionRegistrationKind.PRODUCT) return
        val query = state.productPicker.query.trim()
        if (query.isBlank()) {
            update { it.copy(productPicker = it.productPicker.copy(error = "검색할 제품 이름을 입력하세요.")) }
            return
        }
        productTask({ requireNotNull(productLookup) { "PriceTrace 제품 검색을 사용할 수 없습니다." }.search(query) }) { current, found ->
            current.copy(productPicker = current.productPicker.copy(results = found, searched = true))
        }
    }

    override fun selectProduct(product: ProductReadV1) {
        if (state.saving || state.registrationKind != NutritionRegistrationKind.PRODUCT || product !in state.productPicker.results) return
        ++version
        val previous = state
        val picker = previous.productPicker
        var fields = previous.fields + (FoodEntryField.NAME to product.name) + (FoodEntryField.BRAND to product.brand.orEmpty())
        val exact = product.isExactCatalogProduct()
        val supportedUnit = product.contentUnit?.takeIf(NutritionUnit::isSupported)
        if (exact && product.contentAmount?.let { it.isFinite() && it > 0 } == true && supportedUnit != null)
            fields = fields + (FoodEntryField.BASIS to NutritionCalculator.trim(product.contentAmount))
        update { it.copy(fields = fields,
            basisUnit = if (exact) supportedUnit ?: it.basisUnit else it.basisUnit,
            packageAmount = if (exact && product.contentAmount?.let { amount -> amount.isFinite() && amount > 0 } == true && supportedUnit != null)
                NutritionCalculator.trim(product.contentAmount) else it.packageAmount,
            productPicker = picker.copy(selected = product, results = emptyList(), loading = false, error = null,
                manualFields = if (picker.selected == null) previous.fields.filterKeys { key -> key in listOf(FoodEntryField.NAME, FoodEntryField.BRAND, FoodEntryField.BASIS) } else picker.manualFields,
                manualUnit = if (picker.selected == null) previous.basisUnit else picker.manualUnit,
                manualPackageAmount = if (picker.selected == null) previous.packageAmount else picker.manualPackageAmount,
                notice = if (exact) "선택한 제품에 연결합니다. 영양정보는 확인하거나 직접 입력하세요."
                    else "표준상품 선택됨 · 기준량과 일치하는 규격이 있을 때만 정확한 제품에 연결합니다."), error = null) }
        if (!exact || !product.hasValidPriceTraceCatalogMetadata() || productLookup == null) return
        val selectedFields = state.fields
        val selectedUnit = state.basisUnit
        productTask({ productLookup.nutrition(product) }) { current, nutrition ->
            if (nutrition == null || current.fields != selectedFields || current.basisUnit != selectedUnit) current.copy(
                productPicker = current.productPicker.copy(notice = "선택됨 · 공개 영양정보가 없거나 입력이 변경되어 직접 입력값을 유지합니다."))
            else current.copy(fields = current.fields + profileFields(nutrition.profile) +
                (FoodEntryField.BASIS to NutritionCalculator.trim(nutrition.basisAmount)), basisUnit = nutrition.basisUnit,
                productPicker = current.productPicker.copy(notice = "선택됨 · 공개 영양정보를 불러왔습니다. 필요하면 수정하세요."))
        }
    }

    override fun clearProductSelection() {
        if (state.saving) return
        ++version
        val picker = state.productPicker
        update { it.copy(fields = if (picker.selected == null) it.fields else it.fields + picker.manualFields,
            basisUnit = if (picker.selected == null) it.basisUnit else picker.manualUnit.ifBlank { NutritionUnit.GRAM },
            packageAmount = if (picker.selected == null) it.packageAmount else picker.manualPackageAmount,
            productPicker = NutritionProductPickerState(query = picker.query), error = null) }
    }

    private fun cancelProductNutritionRead() {
        if (state.saving || state.productPicker.selected == null || !state.productPicker.loading) return
        ++version
        update { it.copy(productPicker = it.productPicker.copy(loading = false)) }
    }

    private fun <T> productTask(work: () -> T, apply: (NutritionEditorState, T) -> NutritionEditorState) {
        val owner = state.ownerId
        val request = ++version
        update { it.copy(productPicker = it.productPicker.copy(loading = true, error = null)) }
        executor.execute {
            val result = runCatching {
                check(catalog.currentOwnerId() == owner) { "계정이 변경되었습니다. 등록 화면을 다시 여세요." }
                work()
            }
            main.post {
                if (request != version || !state.open || state.mode != NutritionEditorMode.FOOD ||
                    state.ownerId != owner || catalog.currentOwnerId() != owner) return@post
                update { current -> result.fold(
                    { apply(current, it).let { next -> next.copy(productPicker = next.productPicker.copy(loading = false)) } },
                    { current.copy(productPicker = current.productPicker.copy(loading = false, error = it.message ?: "PriceTrace 조회에 실패했습니다. 다시 검색하세요.")) }) }
            }
        }
    }

    private fun profileFields(profile: NutritionProfile): Map<FoodEntryField, String> = mapOf(
        FoodEntryField.CALORIES to NutritionProfile.CALORIES_KCAL, FoodEntryField.PROTEIN to NutritionProfile.PROTEIN_GRAMS,
        FoodEntryField.CARBS to NutritionProfile.CARBS_GRAMS, FoodEntryField.FAT to NutritionProfile.FAT_GRAMS,
        FoodEntryField.SODIUM to NutritionProfile.SODIUM_MG, FoodEntryField.SUGARS to NutritionProfile.SUGARS_GRAMS,
        FoodEntryField.SATURATED_FAT to NutritionProfile.SATURATED_FAT_GRAMS, FoodEntryField.TRANS_FAT to NutritionProfile.TRANS_FAT_GRAMS
    ).mapValues { (_, key) -> profile.value(key)?.let(NutritionCalculator::trim).orEmpty() }

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
        if (draft.saving || draft.loading || !draft.open || (draft.mode == NutritionEditorMode.FOOD && draft.productPicker.loading)) return
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
            draft.packageAmount.trim().takeIf(String::isNotEmpty)?.let { requiredNumber(it, "제품 1개 용량") }, draft.basisUnit,
            draft.priceTraceBrand)
        if (draft.registrationKind == NutritionRegistrationKind.PRODUCT) require(input.packageAmount?.let { it > 0 } == true) { "제품 1개 용량을 입력하세요." }
        task({
            val selected = draft.productPicker.selected
            val saved = if (selected == null) catalog.saveManualFoodInput(AccountScope(draft.ownerId), input)
                else catalog.savePriceTraceProductInput(AccountScope(draft.ownerId), input, selected)
            saved ?: throw IllegalStateException("식품을 등록하지 못했습니다.")
        }, saving = true) { current, food ->
            if (draft.returnToMeal) onFoodRegistered(food)
            current.copy(open = !draft.returnToMeal, returnToMeal = false,
                mode = NutritionEditorMode.LIBRARY, fields = mapOf(FoodEntryField.BASIS to "100"), packageAmount = "",
                productPicker = NutritionProductPickerState(),
                priceTraceBrand = null, brandSearch = null,
                packagedFoods = if (food.isPackagedFood()) listOf(food) + current.packagedFoods else current.packagedFoods,
                brandEditableFoodIds = if (food.isPackagedFood()) current.brandEditableFoodIds + food.id else current.brandEditableFoodIds,
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
        savedState[KEY_BRAND_ID] = next.priceTraceBrand?.brandId
        savedState[KEY_BRAND_NAME] = next.priceTraceBrand?.canonicalName
        savedState[KEY_PARENT_ID] = next.priceTraceBrand?.parentBrandId
        savedState[KEY_PARENT_NAME] = next.priceTraceBrand?.parentCanonicalName
        savedState[KEY_RECIPE_SERVINGS] = next.recipeServings
        savedState[KEY_COOKED_WEIGHT] = next.cookedWeightGrams
        savedState[KEY_RETURN_TO_MEAL] = next.returnToMeal
        savedState[KEY_PRODUCT_QUERY] = next.productPicker.query
        savedState[KEY_PRODUCT_SELECTION] = next.productPicker.selected?.let(NutritionProductDraftCodec::encode)
        savedState[KEY_PRODUCT_MANUAL_UNIT] = next.productPicker.manualUnit
        savedState[KEY_PRODUCT_MANUAL_PACKAGE] = next.productPicker.manualPackageAmount
        listOf(FoodEntryField.NAME, FoodEntryField.BRAND, FoodEntryField.BASIS).forEach { field ->
            savedState["$PREFIX.product.manual.${field.name}"] = next.productPicker.manualFields[field].orEmpty()
        }
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
        const val KEY_BRAND_ID = "$PREFIX.pt_brand_id"
        const val KEY_BRAND_NAME = "$PREFIX.pt_brand_name"
        const val KEY_PARENT_ID = "$PREFIX.pt_parent_id"
        const val KEY_PARENT_NAME = "$PREFIX.pt_parent_name"
        const val KEY_RECIPE_SERVINGS = "$PREFIX.recipe_servings"
        const val KEY_COOKED_WEIGHT = "$PREFIX.cooked_weight"
        const val KEY_RETURN_TO_MEAL = "$PREFIX.return_to_meal"
        const val KEY_PRODUCT_QUERY = "$PREFIX.product.query"
        const val KEY_PRODUCT_SELECTION = "$PREFIX.product.selection"
        const val KEY_PRODUCT_MANUAL_UNIT = "$PREFIX.product.manual_unit"
        const val KEY_PRODUCT_MANUAL_PACKAGE = "$PREFIX.product.manual_package"
        const val KEY_FOOD_IDS = "$PREFIX.food_ids"
        const val KEY_AMOUNTS = "$PREFIX.amounts"
        fun requiredNumber(value: String, label: String): Double {
            val amount = value.trim().toDoubleOrNull()
            require(amount != null && amount.isFinite() && amount >= 0.0) { "$label 값을 0 이상의 숫자로 입력하세요." }
            return amount
        }
    }
}
