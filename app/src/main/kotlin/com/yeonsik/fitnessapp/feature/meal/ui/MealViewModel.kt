package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Handler
import android.os.Bundle
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.meal.api.MealReadApi
import com.yeonsik.fitness.shared.feature.meal.model.MealSnapshotRead
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.BrandReadV1
import com.yeonsik.fitnessapp.data.NutritionProfile
import com.yeonsik.fitnessapp.data.ProductReadV1
import com.yeonsik.fitnessapp.data.MealCompositionItem
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.feature.meal.model.FoodPortionInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMealInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMenuIntake
import com.yeonsik.fitnessapp.feature.meal.model.MealRecordEditItem
import com.yeonsik.fitnessapp.feature.meal.model.MealRecordEditRequest
import com.yeonsik.fitnessapp.feature.nutrition.model.*
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionTemplateRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionEditorController
import com.yeonsik.fitnessapp.feature.nutrition.ui.FoodEntryField
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionBrandLookup
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionBrandSearchState
import com.yeonsik.fitnessapp.feature.nutrition.model.PriceTraceBrandSelection
import org.json.JSONObject
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposalApi
import com.yeonsik.fitnessapp.integration.nutrition.DiningProposal
import com.yeonsik.fitnessapp.integration.nutrition.DiningMerchantFacts
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.nutrition.analysis.api.NutritionAnalysisApi
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class MealFoodDraftItem(
    val id: String,
    val food: NutritionFood,
    val quantity: String,
    val consumedPercent: String = "100",
    val amountMode: PortionInputMode = PortionInputMode.BASIS,
    val recordItemId: String? = null
)

data class MealDiningDraftItem(
    val id: String,
    val draft: DiningOutDraft,
    val quantity: String = "1",
    val consumedPercent: String = "100",
    val nutritionFoodId: String? = null,
    val recordItemId: String? = null
)

data class RemovedMealDraftItem(
    val index: Int,
    val food: MealFoodDraftItem? = null,
    val menu: MealDiningDraftItem? = null
) {
    val label: String get() = food?.food?.displayName() ?: menu?.draft?.menu.orEmpty()
}

data class DiningOutDraft(
    val store: String = "",
    val branch: String = "",
    val menu: String = "",
    val calories: String = "",
    val carbs: String = "",
    val protein: String = "",
    val fat: String = "",
    val sodium: String = "",
    val sugars: String = "",
    val saturatedFat: String = "",
    val time: String = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
    val restaurantId: String = "",
    val restaurantLocationId: String = "",
    val restaurantMenuId: String = "",
    val catalogProductId: String = "",
    val sourceNamespace: String = "",
    val sourceLocationCode: String = "",
    val transFat: String = ""
)

data class MealRecordEditor(
    val recordId: String,
    val date: String,
    val title: String,
    val time: String,
    val updatedAt: String = ""
)

sealed interface MealDetailState {
    data object Idle : MealDetailState
    data class Loading(val recordId: String) : MealDetailState
    data class Ready(val recordId: String, val snapshot: MealSnapshotRead) : MealDetailState
    data class Error(val recordId: String, val message: String) : MealDetailState
}

sealed interface MealUiState {
    data object Idle : MealUiState
    data class Ready(
        val ownerId: String,
        val date: String,
        val editing: Boolean,
        val diningOut: Boolean,
        val query: String,
        val searchResults: List<NutritionFood>,
        val draft: DiningOutDraft,
        val saving: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val selectedFood: NutritionFood? = null,
        val quantity: String = "",
        val priceTraceQuery: String = "",
        val recordEditor: MealRecordEditor? = null,
        val dateTimeEditor: MealRecordEditor? = null,
        val recordActionSaving: Boolean = false,
        val nutritionAnalysis: NutritionAnalysisReport? = null,
        val nutritionAnalysisLoading: Boolean = false,
        val nutritionAnalysisError: String? = null,
        val searchLoading: Boolean = false,
        val searchCompleted: Boolean = false,
        val searchError: String? = null,
        val searching: Boolean = false,
        val manualFoodEntry: Boolean = false,
        val manualFoodDraft: ManualFoodDraft = ManualFoodDraft(),
        val manualBrandSearch: NutritionBrandSearchState = NutritionBrandSearchState(),
        val catalogNotice: String? = null,
        val foodItems: List<MealFoodDraftItem> = emptyList(),
        val foodPortions: List<FoodPortionDraft> = emptyList(),
        val diningPortion: String = "1",
        val draftLoading: Boolean = false,
        val diningConsumedPercent: String = "100",
        val diningMenus: List<MealDiningDraftItem> = emptyList(),
        val category: MealCatalogCategory = MealCatalogCategory.ALL,
        val removedItem: RemovedMealDraftItem? = null,
        val recordDetail: MealDetailState = MealDetailState.Idle,
        val photoEstimate: PhotoEstimateDraft? = null
    ) : MealUiState
}

sealed interface PriceTraceUiState {
    data object Idle : PriceTraceUiState
    data class Ready(
        val ownerId: String,
        val query: String = "",
        val restaurants: List<NutritionIntegrationService.RestaurantSummary> = emptyList(),
        val detail: NutritionIntegrationService.RestaurantDetail? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val searchedQuery: String? = null,
        val selectedRestaurantId: String? = null
    ) : PriceTraceUiState
}

data class NutritionPublicationUiState(
    val ownerId: String = "",
    val open: Boolean = false,
    val menus: List<NutritionFood> = emptyList(),
    val selectedFoodId: String? = null,
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val publishing: Boolean = false,
    val proposing: Boolean = false,
    val remoteAvailable: Boolean = false,
    val proposals: List<DiningProposal> = emptyList(),
    val privateFoodIds: Set<String> = emptySet(),
    val error: String? = null,
    val notice: String? = null,
    val nutritionOwnerId: String = "",
    val verifying: Boolean = false,
    val failure: NutritionPublicationFailure? = null,
    val lastTarget: NutritionPublicationTarget? = null,
    val verifiedTarget: NutritionPublicationTarget? = null,
    val needsVerification: Boolean = false
) {
    val working: Boolean get() = syncing || publishing || proposing || verifying
    val busy: Boolean get() = loading || working
    val progressLabel: String? get() = when {
        loading -> "저장된 메뉴를 불러오는 중"
        syncing -> "영양정보 동기화 중"
        publishing -> "영양정보 공개 및 연결 확인 중"
        proposing -> "등록 제안을 저장하는 중"
        verifying -> "공개 상태 확인 중"
        else -> null
    }
    val selectedFood: NutritionFood?
        get() = menus.firstOrNull { it.id == selectedFoodId }
    val allowsExistingMenuPublication: Boolean
        get() = selectedFoodId != null && proposals.none { it.nutritionFoodId == selectedFoodId }
}

/** Owns meal editor/search state; Compose only renders state and emits actions. */
class MealViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRecordRepositoryApi,
    private val nutritionCatalog: NutritionCatalogRepositoryApi,
    private val nutritionIntegration: NutritionIntegrationService,
    private val nutritionAnalysisApi: NutritionAnalysisApi? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
    private val diningProposals: DiningProposalApi? = null,
    private val mainExecutor: Executor = Executor { Handler(Looper.getMainLooper()).post(it) },
    private val searchDelayMillis: Long = 250L,
    private val nutritionTemplates: NutritionTemplateRepositoryApi? = null,
    private val mealReadApi: MealReadApi? = null,
    private val brandLookup: NutritionBrandLookup? = null
) : ViewModel() {
    private val main = Handler(Looper.getMainLooper())
    val nutritionEditor = NutritionEditorController(savedStateHandle, nutritionCatalog, nutritionTemplates, executor,
        brandCatalog = nutritionIntegration, brandLookup = brandLookup)
    private val mutableState = MutableLiveData<MealUiState>(MealUiState.Idle)
    val uiState: LiveData<MealUiState> = mutableState
    private val mutablePriceTraceState = MutableLiveData<PriceTraceUiState>(PriceTraceUiState.Idle)
    val priceTraceState: LiveData<PriceTraceUiState> = mutablePriceTraceState
    private val mutableNutritionPublicationState = MutableLiveData(NutritionPublicationUiState())
    val nutritionPublicationState: LiveData<NutritionPublicationUiState> = mutableNutritionPublicationState
    @Volatile private var ownerId = ""
    @Volatile private var date = ""
    @Volatile private var requestVersion = 0L
    private var catalogRequestVersion = 0L
    private var priceTraceRequestVersion = 0L
    private var nutritionPublicationRequestVersion = 0L
    @Volatile private var nutritionAnalysisRequestVersion = 0L
    private var detailRequestVersion = 0L
    private var selectedFood: NutritionFood? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null
    private var manualBrandJob: Job? = null
    private var manualBrandRequestVersion = 0L
    private var manualBrandResultScope = -1L
    private var manualBrandResultOwner = ""
    private var draftBeforeRecordEdit: MealUiState.Ready? = null

    fun enter(scope: AccountScope, date: String) {
        val activeEdit = mutableState.value as? MealUiState.Ready
        if (activeEdit?.ownerId == scope.ownerId && activeEdit.date == date && activeEdit.recordEditor != null) return
        searchJob?.cancel()
        val previousOwner = ownerId.ifEmpty { savedStateHandle[KEY_DRAFT_OWNER] ?: "" }
        val previousDate = this.date.ifEmpty { savedStateHandle[KEY_DRAFT_DATE] ?: "" }
        val dateChanged = previousOwner != scope.ownerId || previousDate != date
        ownerId = scope.ownerId
        this.date = date
        val nutritionOwner = nutritionCatalog.currentOwnerId()
        val savedNutritionOwner = savedStateHandle.get<String>(KEY_MANUAL_NUTRITION_OWNER)
        if (savedNutritionOwner != null && savedNutritionOwner != nutritionOwner) {
            ++manualBrandRequestVersion
            manualBrandJob?.cancel()
            savedStateHandle.remove<ArrayList<String>>(KEY_MANUAL_FOOD)
            savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
            activeEdit?.let { mutableState.value = it.copy(manualFoodDraft = ManualFoodDraft(), manualFoodEntry = false,
                manualBrandSearch = NutritionBrandSearchState()) }
        }
        savedStateHandle[KEY_MANUAL_NUTRITION_OWNER] = nutritionOwner
        val publication = nutritionPublicationReady()
        val restorePublication = publication.ownerId != scope.ownerId || publication.nutritionOwnerId != nutritionOwner
        if (dateChanged || restorePublication) ++catalogRequestVersion
        if (restorePublication) {
            ++nutritionPublicationRequestVersion
            val savedScopeMatches = savedStateHandle.get<String>(KEY_PUBLICATION_OWNER) == scope.ownerId &&
                savedStateHandle.get<String>(KEY_PUBLICATION_NUTRITION_OWNER) == nutritionOwner
            if (!savedScopeMatches) {
                savedStateHandle[KEY_PUBLICATION_OPEN] = false
                savedStateHandle.remove<String>(KEY_PUBLICATION_FOOD)
            }
            savedStateHandle[KEY_PUBLICATION_OWNER] = scope.ownerId
            savedStateHandle[KEY_PUBLICATION_NUTRITION_OWNER] = nutritionOwner
            mutableNutritionPublicationState.value = NutritionPublicationUiState(
                ownerId = scope.ownerId, nutritionOwnerId = nutritionOwner,
                open = savedStateHandle[KEY_PUBLICATION_OPEN] ?: false,
                selectedFoodId = savedStateHandle[KEY_PUBLICATION_FOOD])
        }
        mealRepository.setUserId(scope.ownerId)
        nutritionEditor.synchronizeOwner()
        if (nutritionEditor.uiState.value?.open == true) nutritionEditor.open()
        val request = ++requestVersion
        if (dateChanged) {
            ++manualBrandRequestVersion
            manualBrandJob?.cancel()
            draftBeforeRecordEdit = null
            ++detailRequestVersion
            selectedFood = null
            clearDraftStorage()
            mutableState.value = MealUiState.Idle
        }
        savedStateHandle[KEY_DRAFT_OWNER] = scope.ownerId
        savedStateHandle[KEY_DRAFT_DATE] = date
        mutableState.value = ready().copy(
            ownerId = scope.ownerId,
            date = date,
            recordEditor = null,
            recordActionSaving = false,
            error = null,
            notice = null,
            nutritionAnalysis = null,
            nutritionAnalysisLoading = nutritionAnalysisApi != null,
            nutritionAnalysisError = null,
            searchLoading = false,
            searchCompleted = false,
            searchError = null,
            searching = false,
            saving = if (dateChanged) false else ready().saving,
            query = if (dateChanged) "" else ready().query,
            searchResults = if (dateChanged) emptyList() else ready().searchResults,
            selectedFood = if (dateChanged) null else ready().selectedFood,
            quantity = if (dateChanged) "" else ready().quantity,
            draft = if (dateChanged) savedDraft() else ready().draft,
            manualFoodEntry = if (dateChanged) false else ready().manualFoodEntry,
            manualFoodDraft = if (dateChanged) ManualFoodDraft() else ready().manualFoodDraft,
            manualBrandSearch = if (dateChanged) NutritionBrandSearchState() else ready().manualBrandSearch,
            catalogNotice = null,
            foodItems = if (dateChanged) emptyList() else ready().foodItems,
            foodPortions = if (dateChanged) emptyList() else ready().foodPortions,
            diningPortion = if (dateChanged) "1" else savedStateHandle[KEY_DINING_PORTION] ?: ready().diningPortion,
            diningConsumedPercent = if (dateChanged) "100" else savedStateHandle[KEY_DINING_PERCENT] ?: ready().diningConsumedPercent,
            diningMenus = if (dateChanged) emptyList() else ready().diningMenus,
            draftLoading = false
        )
        loadNutritionAnalysis(scope, date)
        if (mutablePriceTraceState.value !is PriceTraceUiState.Ready ||
            priceTraceReady().ownerId != scope.ownerId ||
            (dateChanged && !nutritionPublicationReady().open)) {
            ++priceTraceRequestVersion
            mutablePriceTraceState.value = PriceTraceUiState.Ready(
                ownerId = scope.ownerId, query = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: "")
        }
        if (restorePublication && nutritionPublicationReady().open) openNutritionPublication()
        savedStateHandle.get<String>(KEY_EDIT_RECORD_ID)?.let { recordId ->
            loadRecordEditor(recordId, restoring = true)
            return
        }
        val selectedId = savedStateHandle.get<String>(KEY_FOOD_ID).orEmpty()
        val portionIds = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_IDS).orEmpty()
        val portionAmounts = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_AMOUNTS).orEmpty()
        val portionPercents = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_PERCENTS).orEmpty()
        val portionModes = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_MODES).orEmpty()
        val currentDraft = ready()
        val needsPortionRestore = currentDraft.foodItems.isEmpty() && currentDraft.foodPortions.isEmpty()
        if (!dateChanged && needsPortionRestore && (selectedId.isNotBlank() || portionIds.isNotEmpty())) {
            mutableState.value = ready().copy(draftLoading = true)
            executor.execute {
                val restored = runCatching {
                    val selected = selectedId.takeIf(String::isNotBlank)?.let(nutritionCatalog::findFoodById)
                    val ids = portionIds.ifEmpty { listOfNotNull(selectedId.takeIf { it.isNotBlank() }) }
                    val items = ids.mapIndexed { index, id ->
                        val food = nutritionCatalog.findFoodById(id)
                            ?: throw IllegalStateException("초안의 식품을 찾지 못했습니다. 식품을 다시 추가하세요.")
                        MealFoodDraftItem(
                            UUID.randomUUID().toString(),
                            food,
                            portionAmounts.getOrElse(index) { mealQuantityText(food.basisAmount) },
                            portionPercents.getOrElse(index) { "100" },
                            portionModes.getOrNull(index)?.let { runCatching { PortionInputMode.valueOf(it) }.getOrNull() } ?: PortionInputMode.BASIS
                        )
                    }
                    selected to items
                }
                mainExecutor.execute {
                    if (request == requestVersion && ownerId == scope.ownerId && this.date == date) {
                        restored.fold({ (selected, items) ->
                            selectedFood = selected ?: items.lastOrNull()?.food
                            update { it.copy(selectedFood = selectedFood, foodItems = items,
                                foodPortions = items.map { item -> FoodPortionDraft(item.food, item.quantity, item.amountMode) },
                                draftLoading = false) }
                            if (ready().editing) search(ready().query)
                        }, { error -> update { it.copy(draftLoading = false, error = error.message) } })
                    }
                }
            }
        } else if (ready().editing && !ready().saving) {
            search(ready().query)
        }
    }

    fun startDraft() {
        val state = ready()
        if (state.saving) return
        if (!savedStateHandle.contains(KEY_TIME)) {
            savedStateHandle[KEY_TIME] = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        }
        update { it.copy(editing = true, draft = savedDraft(), notice = null, error = null) }
        search(state.query)
    }

    fun closeDraft() {
        if (ready().saving) return
        if (ready().recordEditor != null) {
            closeRecordEditor()
            return
        }
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        update { it.copy(editing = false, diningOut = false, manualFoodEntry = false,
            searching = false, searchLoading = false, error = null) }
    }

fun chooseFood() = chooseCategory(MealCatalogCategory.INGREDIENT)

    fun chooseCategory(category: MealCatalogCategory) {
        val state = ready()
        if (state.saving || state.draftLoading || state.editing && state.category == category) return
        if (state.category == MealCatalogCategory.DINING_OUT && category != MealCatalogCategory.DINING_OUT && hasCurrentDiningMenu(state)) {
            update { current -> current.copy(diningMenus = current.diningMenus + currentDiningMenu(current).copy(id = UUID.randomUUID().toString())) }
            clearCurrentDiningMenu()
        }
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        update { it.copy(editing = true, category = category, diningOut = category == MealCatalogCategory.DINING_OUT,
            searchResults = emptyList(), error = null, notice = null,
            searchLoading = false, searchCompleted = false, searchError = null,
            searching = false, manualFoodEntry = false, catalogNotice = null) }
        search(state.query)
    }

fun chooseDiningOut() = chooseCategory(MealCatalogCategory.DINING_OUT)
    fun updateStore(value: String) = draft(KEY_STORE, value)
    fun updateBranch(value: String) = draft(KEY_BRANCH, value)
    fun updateMenu(value: String) = draft(KEY_MENU, value)
    fun updateCalories(value: String) = draft(KEY_CALORIES, value)
    fun updateCarbs(value: String) = draft(KEY_CARBS, value)
    fun updateProtein(value: String) = draft(KEY_PROTEIN, value)
    fun updateFat(value: String) = draft(KEY_FAT, value)
    fun updateSodium(value: String) = draft(KEY_SODIUM, value)
    fun updateSugars(value: String) = draft(KEY_SUGARS, value)
    fun updateSaturatedFat(value: String) = draft(KEY_SATURATED_FAT, value)
    fun updateTransFat(value: String) = draft(KEY_TRANS_FAT, value)
    fun updateTime(value: String) = draft(KEY_TIME, value)
    fun updateDiningPortion(value: String) {
        if (ready().saving) return
        savedStateHandle[KEY_DINING_PORTION] = value
        update { it.copy(diningPortion = value, error = null) }
    }

    fun updateDiningConsumedPercent(value: String) {
        if (ready().saving) return
        savedStateHandle[KEY_DINING_PERCENT] = value
        update { it.copy(diningConsumedPercent = value, error = null) }
    }

    fun updateFoodConsumedPercent(itemId: String, value: String) {
        if (ready().saving) return
        update { state -> state.copy(foodItems = state.foodItems.map {
            if (it.id == itemId || it.food.id == itemId) it.copy(consumedPercent = value) else it
        }, error = null) }
    }

    fun addDiningMenu() {
        val state = ready()
        if (state.saving) return
        val menu = currentDiningMenu(state).copy(id = UUID.randomUUID().toString())
        diningMenuRegistrationError(menu)?.let { error -> update { it.copy(error = error) }; return }
        update { it.copy(diningMenus = it.diningMenus + menu, error = null) }
        clearCurrentDiningMenu()
    }

    /** Begin another menu at an already chosen store without losing the pending menu. */
    fun startAnotherDiningMenu(menuId: String) {
        if (ready().saving) return
        val source = if (menuId == "current-menu" && hasCurrentDiningMenu(ready())) ready().draft
            else ready().diningMenus.firstOrNull { it.id == menuId }?.draft ?: return
        if (!keepPendingDiningMenu()) return
        clearCurrentDiningMenu()
        savedStateHandle[KEY_STORE] = source.store
        savedStateHandle[KEY_BRANCH] = source.branch
        savedStateHandle[KEY_RESTAURANT_ID] = source.restaurantId
        savedStateHandle[KEY_RESTAURANT_LOCATION_ID] = source.restaurantLocationId
        savedStateHandle[KEY_SOURCE_NAMESPACE] = source.sourceNamespace
        savedStateHandle[KEY_SOURCE_LOCATION_CODE] = source.sourceLocationCode
        update { it.copy(draft = savedDraft(), error = null) }
    }

    /** Selecting the next menu must either keep the current one or explain what needs fixing. */
    private fun keepPendingDiningMenu(): Boolean {
        val state = ready()
        if (!hasCurrentDiningMenu(state)) return true
        diningMenuRegistrationError(currentDiningMenu(state))?.let { error ->
            update { it.copy(error = "입력 중인 메뉴를 먼저 완성하거나 취소하세요. $error") }
            return false
        }
        addDiningMenu()
        return true
    }

    fun clearCurrentDiningMenu() {
        if (ready().saving) return
        val state = ready()
        listOf(KEY_MENU, KEY_CALORIES, KEY_CARBS, KEY_PROTEIN, KEY_FAT, KEY_SODIUM, KEY_SUGARS,
            KEY_SATURATED_FAT, KEY_TRANS_FAT, KEY_FOOD_ID, KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID)
            .forEach { savedStateHandle.remove<String>(it) }
        savedStateHandle[KEY_TIME] = state.draft.time
        savedStateHandle[KEY_DINING_PORTION] = "1"
        savedStateHandle[KEY_DINING_PERCENT] = "100"
        selectedFood = null
        update { it.copy(draft = savedDraft(), selectedFood = null, diningPortion = "1",
            diningConsumedPercent = "100", error = null, catalogNotice = null) }
    }

    fun updateDiningMenu(menu: MealDiningDraftItem) {
        if (ready().saving) return
        if (menu.id == "current-menu") {
            val previous = currentDiningMenu(ready())
            val renamed = previous.draft.store != menu.draft.store || previous.draft.branch != menu.draft.branch ||
                previous.draft.menu != menu.draft.menu
            val draft = if (renamed) menu.draft.copy(restaurantId = "", restaurantLocationId = "",
                restaurantMenuId = "", catalogProductId = "", sourceNamespace = "", sourceLocationCode = "") else menu.draft
            listOf(KEY_STORE to draft.store, KEY_BRANCH to draft.branch, KEY_MENU to draft.menu,
                KEY_CALORIES to draft.calories, KEY_CARBS to draft.carbs, KEY_PROTEIN to draft.protein,
                KEY_FAT to draft.fat, KEY_SODIUM to draft.sodium, KEY_SUGARS to draft.sugars,
                KEY_SATURATED_FAT to draft.saturatedFat, KEY_RESTAURANT_ID to draft.restaurantId,
                KEY_TRANS_FAT to draft.transFat,
                KEY_RESTAURANT_LOCATION_ID to draft.restaurantLocationId, KEY_RESTAURANT_MENU_ID to draft.restaurantMenuId,
                KEY_CATALOG_PRODUCT_ID to draft.catalogProductId, KEY_SOURCE_NAMESPACE to draft.sourceNamespace,
                KEY_SOURCE_LOCATION_CODE to draft.sourceLocationCode).forEach { (key, value) -> savedStateHandle[key] = value }
            savedStateHandle[KEY_DINING_PORTION] = menu.quantity
            savedStateHandle[KEY_DINING_PERCENT] = menu.consumedPercent
            if (renamed) { selectedFood = null; savedStateHandle.remove<String>(KEY_FOOD_ID) }
            update { it.copy(draft = draft, diningPortion = menu.quantity, diningConsumedPercent = menu.consumedPercent,
                selectedFood = if (renamed) null else it.selectedFood, error = null) }
            return
        }
        update { state -> state.copy(diningMenus = state.diningMenus.map { existing ->
            if (existing.id != menu.id) existing else {
                val renamed = existing.draft.store != menu.draft.store || existing.draft.branch != menu.draft.branch ||
                    existing.draft.menu != menu.draft.menu
                if (renamed) menu.copy(nutritionFoodId = null, draft = menu.draft.copy(restaurantId = "",
                    restaurantLocationId = "", restaurantMenuId = "", catalogProductId = "", sourceNamespace = "", sourceLocationCode = ""))
                else menu
            }
        }, error = null) }
    }

    fun removeDiningMenu(menuId: String) {
        val state = ready()
        if (state.saving) return
        val menus = diningMealMenus(state)
        val index = menus.indexOfFirst { it.id == menuId }
        if (index < 0) return
        val removed = RemovedMealDraftItem(index, menu = menus[index])
        if (menuId == "current-menu") clearCurrentDiningMenu()
        update { it.copy(diningMenus = it.diningMenus.filterNot { menu -> menu.id == menuId }, removedItem = removed, error = null) }
    }

    fun undoRemoveItem() {
        val state = ready()
        if (state.saving || state.draftLoading) return
        val removed = state.removedItem ?: return
        val food = removed.food
        val menu = removed.menu
        update { current ->
            when {
                food != null && mealFoodItems(current).none { it.food.id == food.food.id } -> {
                    val items = mealFoodItems(current).toMutableList().apply { add(removed.index.coerceIn(0, size), food) }
                    current.copy(foodItems = items, removedItem = null, error = null)
                }
                menu != null && current.diningMenus.none { it.id == menu.id } -> {
                    val menus = current.diningMenus.toMutableList().apply {
                        add(removed.index.coerceIn(0, size), if (menu.id == "current-menu") menu.copy(id = UUID.randomUUID().toString()) else menu)
                    }
                    current.copy(diningMenus = menus, removedItem = null, error = null)
                }
                else -> current.copy(removedItem = null)
            }
        }
    }

    fun openRecordEditor(meal: HomeMealSummary) {
        val state = ready()
        if (meal.id.isBlank() || meal.date != date || !meal.timeEditable || state.saving || state.draftLoading) return
        if (meal.isPhotoEstimate) {
            openPhotoEstimateEdit(meal.id)
            return
        }
        ++detailRequestVersion
        draftBeforeRecordEdit = state
        val backup = Bundle()
        savedStateHandle.keys().filter { it.startsWith("meal.") && !it.startsWith("meal.publication.") &&
            it !in listOf(KEY_EDIT_RECORD_ID, KEY_DRAFT_BEFORE_EDIT, KEY_DRAFT_OWNER, KEY_DRAFT_DATE) }.forEach { key ->
            when (val value = savedStateHandle.get<Any>(key)) {
                is String -> backup.putString(key, value)
                is Boolean -> backup.putBoolean(key, value)
                is ArrayList<*> -> backup.putStringArrayList(key, ArrayList(value.filterIsInstance<String>()))
            }
        }
        savedStateHandle[KEY_DRAFT_BEFORE_EDIT] = backup
        loadRecordEditor(meal.id, restoring = false)
    }

    fun openMealDateTimeEditor(meal: HomeMealSummary) {
        val state = ready()
        if (meal.id.isBlank() || meal.date != date || !meal.timeEditable || state.saving ||
            state.draftLoading || state.editing || state.recordEditor != null || state.recordActionSaving) return
        ++requestVersion
        ++detailRequestVersion
        mutableState.value = state.copy(
            recordDetail = MealDetailState.Idle,
            dateTimeEditor = MealRecordEditor(
                recordId = meal.id,
                date = meal.date,
                title = meal.previewTitle,
                time = meal.mealTime.takeUnless { it == "시간 미기록" }.orEmpty()
            ),
            error = null,
            notice = null
        )
    }

    fun closeMealDateTimeEditor() {
        val state = ready()
        if (state.recordActionSaving) return
        mutableState.value = state.copy(dateTimeEditor = null, error = null)
    }

    private fun loadRecordEditor(recordId: String, restoring: Boolean) {
        cancelSearch()
        val scope = AccountScope(ownerId)
        val requestedDate = date
        val request = ++requestVersion
        val restoredDraft = ready()
        val savedIds = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_IDS)
        val savedAmounts = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_AMOUNTS).orEmpty()
        val savedPercents = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_PERCENTS).orEmpty()
        val savedModes = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_MODES).orEmpty()
        val savedMenus = savedStateHandle.get<ArrayList<String>>(KEY_DINING_MENUS)
        val hasSavedTime = savedStateHandle.contains(KEY_TIME)
        if (!restoring) {
            val backup = savedStateHandle.get<Bundle>(KEY_DRAFT_BEFORE_EDIT)
            clearDraftStorage()
            savedStateHandle[KEY_DRAFT_BEFORE_EDIT] = backup
        }
        savedStateHandle[KEY_EDIT_RECORD_ID] = recordId
        update { it.copy(editing = true, draftLoading = true, saving = false, recordActionSaving = false,
            recordEditor = MealRecordEditor(recordId, requestedDate, "식단", ""),
            foodItems = emptyList(), foodPortions = emptyList(), diningMenus = emptyList(), selectedFood = null,
            draft = if (restoring) restoredDraft.draft else DiningOutDraft(time = ""),
            manualFoodEntry = false, searchLoading = false, searchResults = emptyList(), error = null, notice = null) }
        executor.execute {
            val result = runCatching {
                val snapshot = mealRepository.loadMealForEditing(scope, recordId)
                    ?: error("수정할 식단 기록을 찾지 못했습니다.")
                check(snapshot.date == requestedDate) { "선택한 날짜의 식단이 아닙니다." }
                val baseline = snapshot.items.filterNot { it.diningOut }.map { item ->
                    MealFoodDraftItem(item.itemId ?: item.food.id, item.food, mealQuantityText(item.quantity), recordItemId = item.itemId)
                }
                val foods = if (restoring && savedIds != null) savedIds.mapIndexed { index, id ->
                    val original = baseline.firstOrNull { it.food.id == id }
                    val food = original?.food ?: nutritionCatalog.findFoodById(id) ?: error("수정 중인 음식을 다시 추가하세요.")
                    (original ?: MealFoodDraftItem(UUID.randomUUID().toString(), food, mealQuantityText(food.basisAmount)))
                        .copy(quantity = savedAmounts.getOrElse(index) { mealQuantityText(food.basisAmount) },
                            consumedPercent = savedPercents.getOrElse(index) { "100" },
                            amountMode = savedModes.getOrNull(index)?.let { runCatching { PortionInputMode.valueOf(it) }.getOrNull() }
                                ?: PortionInputMode.BASIS)
                } else baseline
                val menus = if (restoring && savedMenus != null) savedMenus.map { raw ->
                    decodeDiningMenu(raw) ?: error("수정 중인 메뉴를 복구하지 못했습니다. 식단을 다시 열어 주세요.")
                }
                    else snapshot.items.filter { it.diningOut }.map(::recordDiningDraft)
                Triple(snapshot, foods, menus)
            }
            mainExecutor.execute loaded@{
                if (request != requestVersion || ownerId != scope.ownerId || date != requestedDate) return@loaded
                result.fold({ (snapshot, foods, menus) ->
                    selectedFood = null
                    val draft = if (restoring && hasSavedTime) restoredDraft.draft else DiningOutDraft(time = snapshot.time)
                    savedStateHandle[KEY_TIME] = draft.time
                    update { it.copy(draftLoading = false, recordEditor = MealRecordEditor(snapshot.recordId,
                        snapshot.date, snapshot.title, snapshot.time, snapshot.updatedAt),
                        foodItems = foods, foodPortions = foods.map { item -> FoodPortionDraft(item.food, item.quantity, item.amountMode) },
                        diningMenus = menus, draft = draft, diningOut = false, category = MealCatalogCategory.ALL,
                        diningPortion = if (restoring) restoredDraft.diningPortion else "1",
                        diningConsumedPercent = if (restoring) restoredDraft.diningConsumedPercent else "100",
                        query = "", removedItem = null, error = null) }
                }, { error -> update { it.copy(draftLoading = false, error = error.message ?: "식단 내용을 불러오지 못했습니다.") } })
            }
        }
    }

    private fun recordDiningDraft(item: MealRecordEditItem): MealDiningDraftItem {
        val food = item.food
        val source = runCatching { JSONObject(food.sourceReference ?: "{}") }.getOrNull()
        fun nutrient(key: String) = food.profile.value(key)?.let(::number).orEmpty()
        return MealDiningDraftItem(item.itemId ?: UUID.randomUUID().toString(), DiningOutDraft(
            store = sourceValue(source, "restaurant_name").ifBlank { food.brand.orEmpty() },
            branch = sourceValue(source, "branch_name"), menu = food.name,
            calories = nutrient("calories_kcal"), carbs = nutrient("carbs_grams"), protein = nutrient("protein_grams"),
            fat = nutrient("fat_grams"), sodium = nutrient("sodium_mg"), sugars = nutrient("sugars_grams"),
            saturatedFat = nutrient("saturated_fat_grams"), transFat = nutrient("trans_fat_grams"),
            restaurantId = sourceValue(source, "restaurant_id"), restaurantLocationId = sourceValue(source, "restaurant_location_id"),
            restaurantMenuId = sourceValue(source, "restaurant_menu_id"), catalogProductId = sourceValue(source, "catalog_product_id"),
            sourceNamespace = sourceValue(source, "source_namespace"), sourceLocationCode = sourceValue(source, "source_location_code")
        ), mealQuantityText(item.quantity), number(item.consumedFraction * 100), item.sourceFoodId, item.itemId)
    }

    fun closeRecordEditor() = restoreDraftBeforeEdit(null)

    private fun restoreDraftBeforeEdit(notice: String?) {
        if (ready().saving) return
        cancelSearch()
        ++requestVersion
        val backup = savedStateHandle.get<Bundle>(KEY_DRAFT_BEFORE_EDIT)
        val previous = draftBeforeRecordEdit
        draftBeforeRecordEdit = null
        clearDraftStorage()
        backup?.keySet()?.forEach { key -> savedStateHandle[key] = backup.get(key) }
        if (previous != null && previous.ownerId == ownerId && previous.date == date) {
            selectedFood = previous.selectedFood
            mutableState.value = previous.copy(editing = false, recordEditor = null, saving = false,
                recordActionSaving = false, error = null, notice = notice)
            update { it }
        } else {
            selectedFood = null
            mutableState.value = MealUiState.Idle
            enter(AccountScope(ownerId), date)
            update { it.copy(editing = false, notice = notice) }
        }
    }

    fun openMealDetail(scope: AccountScope, recordId: String) {
        val state = ready()
        if (scope.ownerId != ownerId || state.ownerId != scope.ownerId || recordId.isBlank() ||
            state.saving || state.recordActionSaving || state.draftLoading) return
        val request = ++detailRequestVersion
        val requestedDate = state.date
        mutableState.value = state.copy(recordDetail = MealDetailState.Loading(recordId), recordEditor = null)
        executor.execute {
            val result = runCatching {
                val api = mealReadApi ?: throw IllegalStateException("식단 상세를 불러올 수 없습니다.")
                api.mealSnapshot(scope, recordId) ?: throw IllegalStateException("식단 기록을 찾을 수 없습니다.")
            }
            mainExecutor.execute publish@{
                val current = mutableState.value as? MealUiState.Ready ?: return@publish
                if (request != detailRequestVersion || ownerId != scope.ownerId || date != requestedDate ||
                    current.ownerId != scope.ownerId || current.recordDetail != MealDetailState.Loading(recordId)) return@publish
                mutableState.value = current.copy(recordDetail = result.fold(
                    { MealDetailState.Ready(recordId, it) },
                    { MealDetailState.Error(recordId, it.message ?: "식단 상세를 불러오지 못했습니다.") }
                ))
            }
        }
    }

    fun closeMealDetail() {
        ++detailRequestVersion
        update { it.copy(recordDetail = MealDetailState.Idle) }
    }

    fun saveMealTime(scope: AccountScope, recordId: String, mealTime: String) {
        val state = ready()
        val recordEditor = state.recordEditor
        if (recordEditor?.recordId == recordId) {
            if (scope.ownerId != ownerId || state.saving || state.draftLoading) return
            updateTime(mealTime)
            save(scope) { }
            return
        }
        val dateTimeEditor = state.dateTimeEditor ?: return
        saveMealDateTime(scope, recordId, dateTimeEditor.date, mealTime)
    }

    fun saveMealDateTime(scope: AccountScope, recordId: String, recordDate: String, mealTime: String) {
        val state = ready()
        val dateTimeEditor = state.dateTimeEditor
        if (scope.ownerId != ownerId || dateTimeEditor?.recordId != recordId ||
            state.recordActionSaving || state.saving) return
        val request = ++requestVersion
        mutableState.value = state.copy(recordActionSaving = true, error = null, notice = null)
        executor.execute {
            try {
                if (!mealRepository.updateMealDateTime(scope, recordId, recordDate, mealTime)) {
                    throw IllegalStateException("이 식단 기록 날짜와 시간을 수정할 수 없습니다.")
                }
                mainExecutor.execute {
                    if (request == requestVersion && ownerId == scope.ownerId && date == state.date) {
                        mutableState.value = ready().copy(
                            dateTimeEditor = null,
                            recordActionSaving = false,
                            notice = "식단 기록 날짜와 시간을 수정했습니다."
                        )
                        loadNutritionAnalysis(scope, state.date)
                    }
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (request == requestVersion && ownerId == scope.ownerId && date == state.date) {
                        mutableState.value = ready().copy(
                            recordActionSaving = false,
                            error = error.message ?: "식단 기록 날짜와 시간을 수정하지 못했습니다."
                        )
                    }
                }
            }
        }
    }

    fun deleteMeal(scope: AccountScope, recordId: String) {
        val state = ready()
        if (scope.ownerId != ownerId || state.recordActionSaving) return
        val request = ++requestVersion
        mutableState.value = state.copy(
            recordActionSaving = true,
            error = null,
            notice = null
        )
        executor.execute {
            try {
                if (!mealRepository.deleteMeal(scope, recordId)) {
                    throw IllegalStateException("이 식단 기록을 삭제할 수 없습니다.")
                }
                if (request == requestVersion && ownerId == scope.ownerId) {
                    mutableState.postValue(
                        ready().copy(
                            recordEditor = null,
                            recordActionSaving = false,
                            notice = "식단 기록을 삭제했습니다."
                        )
                    )
                    loadNutritionAnalysis(scope, date)
                }
            } catch (error: Exception) {
                if (request == requestVersion && ownerId == scope.ownerId) {
                    mutableState.postValue(
                        ready().copy(
                            recordActionSaving = false,
                            error = error.message ?: "식단 기록을 삭제하지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun updatePriceTraceQuery(value: String) {
        savedStateHandle[KEY_PRICE_TRACE_QUERY] = value
        update { it.copy(priceTraceQuery = value) }
        val current = priceTraceReady()
        if (current.query == value) return
        ++priceTraceRequestVersion
        mutablePriceTraceState.value = current.copy(query = value, restaurants = emptyList(), detail = null,
            loading = false, error = null, searchedQuery = null, selectedRestaurantId = null)
    }

    fun searchPriceTraceRestaurants() {
        val current = priceTraceReady()
        val query = current.query.trim()
        if (query.isEmpty()) {
            mutablePriceTraceState.value = current.copy(error = "식당 이름을 입력해 주세요.")
            return
        }
        if (current.loading) return
        val request = ++priceTraceRequestVersion
        val requestedOwner = ownerId
        mutablePriceTraceState.value = current.copy(loading = true, error = null, detail = null,
            restaurants = emptyList(), selectedRestaurantId = null, searchedQuery = null)
        executor.execute {
            val result = runCatching { nutritionIntegration.searchRestaurants(query) }
            main.post {
                if (request != priceTraceRequestVersion || ownerId != requestedOwner) return@post
                mutablePriceTraceState.value = result.fold(
                    { found -> priceTraceReady().copy(restaurants = found, loading = false, error = null, searchedQuery = query) },
                    { error -> priceTraceReady().copy(loading = false, searchedQuery = query,
                        error = nutritionPublicationError(error, "식당 검색을 완료하지 못했어요. 다시 시도해 주세요.")) }
                )
            }
        }
    }

    fun loadPriceTraceRestaurant(restaurantId: String) {
        if (priceTraceReady().loading) return
        val request = ++priceTraceRequestVersion
        val requestedOwner = ownerId
        mutablePriceTraceState.value = priceTraceReady().copy(loading = true, error = null, detail = null,
            selectedRestaurantId = restaurantId)
        executor.execute {
            val result = runCatching { nutritionIntegration.loadRestaurant(restaurantId) }
            main.post {
                if (request != priceTraceRequestVersion || ownerId != requestedOwner) return@post
                mutablePriceTraceState.value = result.fold(
                    { detail -> priceTraceReady().copy(detail = detail, loading = false, error = null) },
                    { error -> priceTraceReady().copy(loading = false,
                        error = nutritionPublicationError(error, "지점과 메뉴를 불러오지 못했어요. 다시 시도해 주세요.")) }
                )
            }
        }
    }

    fun openNutritionPublication() {
        val current = nutritionPublicationReady()
        savedStateHandle[KEY_PUBLICATION_OPEN] = true
        if (current.working) {
            mutableNutritionPublicationState.value = current.copy(open = true)
            return
        }
        if (current.loading && current.open) return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        val requestedNutritionOwner = nutritionCatalog.currentOwnerId()
        val sameNutritionOwner = current.nutritionOwnerId == requestedNutritionOwner
        val selectedId = if (sameNutritionOwner) current.selectedFoodId else null
        val keepOutcome = sameNutritionOwner && current.failure in
            listOf(NutritionPublicationFailure.PUBLISH, NutritionPublicationFailure.VERIFY, NutritionPublicationFailure.SYNC)
        savedStateHandle[KEY_PUBLICATION_OWNER] = requestedOwner
        savedStateHandle[KEY_PUBLICATION_NUTRITION_OWNER] = requestedNutritionOwner
        val restoredId = savedStateHandle.get<String>(KEY_PUBLICATION_FOOD).takeIf { sameNutritionOwner }
        mutableNutritionPublicationState.value = (if (sameNutritionOwner) current else NutritionPublicationUiState()).copy(
            ownerId = requestedOwner, nutritionOwnerId = requestedNutritionOwner, open = true,
            loading = true, selectedFoodId = selectedId ?: restoredId,
            error = current.error.takeIf { keepOutcome }, failure = current.failure.takeIf { keepOutcome }
        )
        executor.execute {
            try {
                val menus = nutritionCatalog.savedDiningOutMenus()
                    .filter { it.isDiningOutMenu() }
                val proposals = menus.mapNotNull { it.ownerId }.distinct()
                    .flatMap { diningProposals?.list(it).orEmpty() }
                val privateIds = menus.filter { it.ownerId != null
                    && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }
                    .map { it.id }.toSet()
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            ownerId = requestedOwner,
                            open = true,
                            menus = menus,
                            proposals = proposals,
                            privateFoodIds = privateIds,
                            remoteAvailable = menus.any { it.ownerId != null && diningProposals?.available(it.ownerId) == true },
                            selectedFoodId = current.selectedFoodId
                                ?.takeIf { id -> menus.any { it.id == id } },
                            loading = false,
                            error = null
                        )
                    )
                }
            } catch (error: Exception) {
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            ownerId = requestedOwner,
                            open = true,
                            loading = false,
                            error = error.message ?: "Nutrition 외식 메뉴를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun createDiningOutMenu() {
        if (ready().saving) return
        closeNutritionPublication()
        if (!ready().editing) startDraft()
        if (!ready().diningOut) chooseDiningOut()
    }

    fun closeNutritionPublication() {
        val current = nutritionPublicationReady()
        savedStateHandle[KEY_PUBLICATION_OPEN] = false
        // A write already accepted by the server cannot be cancelled by dismissing a window.
        if (!current.working) ++nutritionPublicationRequestVersion
        ++priceTraceRequestVersion
        mutablePriceTraceState.value = priceTraceReady().copy(loading = false)
        mutableNutritionPublicationState.value = current.copy(open = false, loading = false)
    }

    fun selectNutritionPublicationMenu(foodId: String) {
        val state = nutritionPublicationReady()
        val food = state.menus.firstOrNull { it.id == foodId } ?: return
        mutableNutritionPublicationState.value = state.copy(
            selectedFoodId = food.id,
            error = null,
            notice = null
        )
        val source = food.sourceReference?.let { reference ->
            runCatching { JSONObject(reference) }.getOrNull()
        }
        val restaurantName = food.brand?.takeIf { it.isNotBlank() }
            ?: sourceValue(source, "restaurant_name")
        val query = restaurantName.ifBlank { food.name }
        updatePriceTraceQuery(query)
        if (state.remoteAvailable) searchPriceTraceRestaurants()
    }

    fun proposeDiningMerchant(facts: DiningMerchantFacts) = runProposalAction { api, owner, food ->
        api.submitMerchant(owner, food, facts)
    }

    fun proposeDiningMenu(locationId: String?, merchantCandidateId: String?, menuName: String) {
        val detail = priceTraceReady().detail
        if (merchantCandidateId == null && (detail == null
                || detail.locations.none { it.restaurantLocationId == locationId })) {
            mutableNutritionPublicationState.value = nutritionPublicationReady().copy(error = "PT 지점을 다시 선택하세요.")
            return
        }
        runProposalAction { api, owner, food ->
            api.submitMenu(owner, food, if (merchantCandidateId == null) detail!!.restaurantId else null,
                if (merchantCandidateId == null) locationId else null, merchantCandidateId, menuName)
        }
    }

    fun refreshDiningProposals() = runProposalAction { api, owner, _ -> api.refresh(owner) }
    fun resubmitDiningMerchant(previousCandidateId: String, facts: DiningMerchantFacts, userVerified: Boolean) =
        runProposalAction { api, owner, food -> api.resubmitMerchant(owner, food, previousCandidateId, facts, userVerified) }

    fun resubmitDiningMenu(previousCandidateId: String, restaurantId: String?, locationId: String?,
        merchantCandidateId: String?, menuName: String, userVerified: Boolean) = runProposalAction { api, owner, food ->
        api.resubmitMenu(owner, food, previousCandidateId, restaurantId, locationId, merchantCandidateId, menuName, userVerified)
    }

    fun publishApprovedDiningProposal() = runProposalAction(publish = true) { api, owner, food ->
        val identity = api.approvedIdentity(owner, food)
        nutritionIntegration.publishDiningOutForExistingMenu(food, identity)
        api.list(owner)
    }

    /** Only explicit proposal/publication actions reach remote integration; ordinary save never calls this. */
    private fun runProposalAction(publish: Boolean = false,
        operation: (DiningProposalApi, String, String) -> List<DiningProposal>) {
        val state = nutritionPublicationReady()
        val api = diningProposals
        val food = state.selectedFood ?: return
        if (!state.open || state.loading || state.syncing || state.publishing || state.proposing) return
        val nutritionOwner = food.ownerId
        if (api == null || nutritionOwner.isNullOrBlank() || !api.available(nutritionOwner)) {
            mutableNutritionPublicationState.value = state.copy(error = "Nutrition·PT 계정 연결이 필요합니다. 식사와 운동은 기기에 저장할 수 있습니다.")
            return
        }
        val requestedOwner = ownerId
        val request = ++nutritionPublicationRequestVersion
        mutableNutritionPublicationState.value = state.copy(proposing = !publish, publishing = publish,
            error = null, notice = null)
        executor.execute {
            try {
                val proposals = operation(api, nutritionOwner, food.id)
                val menus = nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() }
                val privateIds = menus.filter { it.ownerId != null
                    && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }
                    .map { it.id }.toSet()
                if (request == nutritionPublicationRequestVersion && requestedOwner == ownerId) {
                    mutableNutritionPublicationState.postValue(nutritionPublicationReady().copy(
                        proposals = proposals, menus = menus, privateFoodIds = privateIds,
                        proposing = false, publishing = false,
                        notice = if (publish) "공개 연결 완료 · PT 공개 조회에서 영양 행을 확인했습니다."
                            else "제안 상태를 저장했습니다. 승인 후에도 공개 연결은 직접 선택해야 합니다."))
                }
            } catch (error: Exception) {
                // A timed-out submission still has a durable idempotency reservation. Show it
                // immediately so an uncertain request cannot look like an unsubmitted menu.
                val proposals = runCatching { api.list(nutritionOwner) }.getOrDefault(state.proposals)
                if (request == nutritionPublicationRequestVersion && requestedOwner == ownerId) {
                    mutableNutritionPublicationState.postValue(nutritionPublicationReady().copy(
                        proposals = proposals, proposing = false, publishing = false,
                        error = error.message ?: "PT 제안을 처리하지 못했습니다."))
                }
            }
        }
    }

    /** Syncs the Nutrition catalog without creating or editing intake records. */
    fun syncNutritionPublicationCatalog() {
        val state = nutritionPublicationReady()
        if (!state.open || state.busy) return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        val requestedNutritionOwner = state.nutritionOwnerId
        mutableNutritionPublicationState.value = state.copy(syncing = true, error = null, notice = null, failure = null)
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == requestedNutritionOwner) { "영양정보 계정이 변경되었습니다." }
                val synced = nutritionIntegration.syncCurrentCatalog(requestedNutritionOwner)
                val menus = nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() }
                val proposals = menus.mapNotNull { it.ownerId }.distinct()
                    .flatMap { diningProposals?.list(it).orEmpty() }
                val privateIds = menus.filter { it.ownerId != null
                    && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }.map { it.id }.toSet()
                Triple(synced, menus, proposals to privateIds)
            }
            applyPublicationResult(request, requestedOwner, requestedNutritionOwner) { current ->
                result.fold({ (synced, menus, proposalSnapshot) ->
                    val (proposals, privateIds) = proposalSnapshot
                    val selected = current.selectedFoodId?.takeIf { id -> menus.any { it.id == id } }
                    savedStateHandle[KEY_PUBLICATION_FOOD] = selected
                    current.copy(
                        menus = menus,
                        proposals = proposals,
                        privateFoodIds = privateIds,
                        remoteAvailable = menus.any { it.ownerId != null && diningProposals?.available(it.ownerId) == true },
                        selectedFoodId = selected,
                        syncing = false,
                        notice = "Nutrition 동기화 완료 · 가져옴 ${synced.pulledRows}건 · 보냄 ${synced.pushedRows}건",
                        lastTarget = current.lastTarget?.takeIf { it.foodId == selected },
                        verifiedTarget = current.verifiedTarget?.takeIf { it.foodId == selected },
                        error = null,
                        failure = null
                    )
                }, { error -> current.copy(
                    syncing = false,
                    failure = NutritionPublicationFailure.SYNC,
                    error = nutritionPublicationError(error, "동기화를 완료하지 못했어요. 저장된 메뉴를 확인하고 다시 시도해 주세요.")
                ) })
            }
        }
    }

    /** Resolves only IDs from the currently loaded restaurant, before any remote action. */
    private fun selectedPublicationTarget(
        locationId: String, menuId: String, catalogProductId: String
    ): Pair<NutritionPublicationTarget, DiningOutIdentity>? {
        val current = nutritionPublicationReady()
        if (!current.open || current.busy) return null
        fun reject(message: String): Nothing? {
            mutableNutritionPublicationState.value = current.copy(error = message, failure = NutritionPublicationFailure.SELECTION)
            return null
        }
        val food = current.selectedFood ?: return reject("공개할 외식 영양정보를 먼저 선택해 주세요.")
        if (!current.allowsExistingMenuPublication) {
            return reject("등록 제안은 승인 후 ‘PT에 공개 연결’ 버튼에서 공개하세요. 검토 중이거나 거절된 제안은 공개할 수 없습니다.")
        }
        if (current.nutritionOwnerId != nutritionCatalog.currentOwnerId()) {
            return reject("영양정보 계정이 바뀌었어요. 메뉴 목록을 다시 불러와 주세요.")
        }
        val prices = priceTraceReady()
        val detail = prices.detail
        if (prices.loading || detail == null) return reject("식당의 지점과 메뉴를 먼저 불러와 주세요.")
        val location = detail.locations.singleOrNull { it.restaurantLocationId == locationId }
        val menu = detail.menus.singleOrNull { it.restaurantMenuId == menuId && it.catalogProductId == catalogProductId }
        if (location == null || menu == null) return reject("선택한 지점 또는 메뉴가 바뀌었어요. 다시 선택해 주세요.")
        val identity = runCatching {
            DiningOutIdentity.fromPriceTrace(detail.restaurantId, detail.restaurantName, location.restaurantLocationId,
                location.locationSourceNamespace, location.sourceLocationCode, location.branchName, menu.restaurantMenuId,
                menu.menuName, menu.catalogProductId)
        }.getOrElse { return reject("식당 메뉴의 연결 정보를 확인하지 못했어요. 식당 정보를 다시 불러와 주세요.") }
        return NutritionPublicationTarget(food.id, current.nutritionOwnerId, detail.restaurantId, locationId, menuId,
            catalogProductId, "${detail.restaurantName} · ${location.branchName.ifBlank { "본점" }} · ${menu.menuName}") to identity
    }

    fun publishNutritionMenuForPriceTraceSelection(locationId: String, menuId: String, catalogProductId: String) {
        val (target, identity) = selectedPublicationTarget(locationId, menuId, catalogProductId) ?: return
        val state = nutritionPublicationReady()
        val food = state.selectedFood ?: return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        mutableNutritionPublicationState.value = state.copy(publishing = true, error = null,
            notice = null, failure = null, lastTarget = target, verifiedTarget = null, needsVerification = false)
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == target.nutritionOwnerId) { "영양정보 계정이 변경되었습니다." }
                val proposals = diningProposals?.list(target.nutritionOwnerId)
                check(proposals.orEmpty().none { it.nutritionFoodId == target.foodId }) {
                    "등록 제안은 승인 후 ‘PT에 공개 연결’ 버튼에서 공개하세요."
                }
                val published = nutritionIntegration.publishDiningOutForExistingMenu(
                    target.foodId, identity, target.nutritionOwnerId
                )
                if (!published.state.isPublic || published.state.catalogProductId != target.catalogProductId) {
                    throw java.io.IOException("공개 연결 결과를 확인하지 못했습니다.")
                }
                val refreshed = runCatching {
                    val menus = nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() }
                    val privateIds = menus.filter { it.ownerId != null
                        && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }.map { it.id }.toSet()
                    Triple(menus, privateIds, proposals)
                }.getOrNull()
                refreshed
            }
            applyPublicationResult(request, requestedOwner, target.nutritionOwnerId) { current ->
                result.fold({ refreshed -> current.copy(
                    menus = refreshed?.first ?: current.menus,
                    privateFoodIds = refreshed?.second ?: current.privateFoodIds,
                    proposals = refreshed?.third ?: current.proposals,
                    publishing = false,
                    verifiedTarget = target,
                    notice = "${target.label}의 영양정보 공개 연결을 확인했습니다." +
                        if (refreshed == null) " 메뉴 목록은 새로고침해 주세요." else "",
                    error = null,
                    failure = null
                ) }, { error -> current.copy(
                    publishing = false,
                    failure = NutritionPublicationFailure.PUBLISH,
                    needsVerification = generateSequence<Throwable>(error) { it.cause }.take(6)
                        .any { it is java.io.IOException },
                    error = nutritionPublicationError(error, "공개 연결을 완료하지 못했어요. 선택한 메뉴와 계정을 확인해 주세요.", publishing = true)
                ) })
            }
        }
    }

    /** Checks the public read contract only; never repeats a publish request. */
    fun verifyNutritionMenuForPriceTraceSelection(locationId: String, menuId: String, catalogProductId: String) {
        val (target, _) = selectedPublicationTarget(locationId, menuId, catalogProductId) ?: return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        mutableNutritionPublicationState.value = nutritionPublicationReady().copy(verifying = true,
            lastTarget = target, verifiedTarget = null, error = null, notice = null, failure = null)
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == target.nutritionOwnerId) { "영양정보 계정이 변경되었습니다." }
                nutritionIntegration.verifyDiningOutForExistingMenu(target.foodId, target.catalogProductId)
            }
            applyPublicationResult(request, requestedOwner, target.nutritionOwnerId) { state ->
                result.fold({ found -> if (found) state.copy(verifying = false, verifiedTarget = target,
                    needsVerification = false, notice = "${target.label}의 공개 연결을 확인했습니다.")
                else state.copy(verifying = false, verifiedTarget = null, needsVerification = false,
                    failure = NutritionPublicationFailure.VERIFY,
                    error = "선택한 메뉴의 공개 연결을 아직 확인하지 못했어요. 동기화하거나 잠시 후 다시 확인해 주세요.")
                }, { error -> state.copy(verifying = false, failure = NutritionPublicationFailure.VERIFY,
                    error = nutritionPublicationError(error, "공개 상태를 확인하지 못했어요. 잠시 후 다시 확인해 주세요.")) })
            }
        }
    }

    private fun applyPublicationResult(
        request: Long, requestedOwner: String, requestedNutritionOwner: String,
        apply: (NutritionPublicationUiState) -> NutritionPublicationUiState
    ) {
        main.post {
            if (request != nutritionPublicationRequestVersion || ownerId != requestedOwner) return@post
            val current = nutritionPublicationReady()
            val activeNutritionOwner = nutritionCatalog.currentOwnerId()
            if (activeNutritionOwner != requestedNutritionOwner) {
                savedStateHandle[KEY_PUBLICATION_NUTRITION_OWNER] = activeNutritionOwner
                savedStateHandle.remove<String>(KEY_PUBLICATION_FOOD)
                mutableNutritionPublicationState.value = NutritionPublicationUiState(
                    ownerId = requestedOwner, nutritionOwnerId = activeNutritionOwner, open = current.open,
                    error = "영양정보 계정이 바뀌었어요. 메뉴 목록을 다시 불러와 주세요.",
                    failure = NutritionPublicationFailure.LOAD)
                return@post
            }
            mutableNutritionPublicationState.value = apply(current)
        }
    }

    fun applyPriceTraceSelection(
        restaurantId: String,
        restaurantName: String,
        locationId: String,
        branchName: String,
        menuId: String,
        menuName: String,
        catalogProductId: String
    ) {
        if (ready().saving) return
        if (listOf(restaurantId, locationId, menuId, catalogProductId).any(String::isBlank)) {
            update { it.copy(error = "식당·지점·메뉴 식별정보를 확인하고 다시 선택하세요.") }
            return
        }
        ++requestVersion
        ++catalogRequestVersion
        val targetChanged = ready().draft.let {
            it.restaurantId != restaurantId || it.restaurantLocationId != locationId ||
                it.restaurantMenuId != menuId || it.catalogProductId != catalogProductId
        }
        if (targetChanged && !keepPendingDiningMenu()) return
        if (targetChanged) {
            listOf(KEY_CALORIES, KEY_CARBS, KEY_PROTEIN, KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT, KEY_TRANS_FAT)
                .forEach { savedStateHandle.remove<String>(it) }
            savedStateHandle[KEY_DINING_PORTION] = "1"
            savedStateHandle[KEY_DINING_PERCENT] = "100"
        }
        savedStateHandle[KEY_RESTAURANT_ID] = restaurantId
        savedStateHandle[KEY_RESTAURANT_LOCATION_ID] = locationId
        savedStateHandle[KEY_RESTAURANT_MENU_ID] = menuId
        savedStateHandle[KEY_CATALOG_PRODUCT_ID] = catalogProductId
        savedStateHandle[KEY_STORE] = restaurantName
        savedStateHandle[KEY_BRANCH] = branchName
        savedStateHandle[KEY_MENU] = menuName
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_SOURCE_NAMESPACE)
        savedStateHandle.remove<String>(KEY_SOURCE_LOCATION_CODE)
        selectedFood = null
        savedStateHandle.remove<String>(KEY_QUANTITY)
        savedStateHandle[KEY_QUERY] = ""
        update { it.copy(editing = true, diningOut = true, draft = savedDraft(),
            diningPortion = if (targetChanged) "1" else it.diningPortion,
            diningConsumedPercent = if (targetChanged) "100" else it.diningConsumedPercent,
            query = "", searchResults = emptyList(), selectedFood = null, quantity = "", error = null,
            notice = null, searchLoading = false, searchCompleted = false, searchError = null) }
    }

    fun search(value: String) {
        val state = ready()
        if (!state.editing || state.saving) return
        val nutritionOwner = nutritionCatalog.activeOwnerId()
        savedStateHandle[KEY_QUERY] = value
        searchJob?.cancel()
        val request = ++requestVersion
        val inputError = state.error.takeUnless { it == state.searchError }
        update { it.copy(query = value, searchResults = emptyList(), error = inputError,
            searchLoading = true, searchCompleted = false, searchError = null, searching = true) }
        searchJob = viewModelScope.launch {
            delay(searchDelayMillis)
            executor.execute {
                if (request != requestVersion) return@execute
                val result = runCatching {
                    val term = value.trim()
                    val dining = if (state.category == MealCatalogCategory.ALL || state.category == MealCatalogCategory.DINING_OUT) {
                        nutritionCatalog.savedDiningOutMenus().filter { food ->
                            val branch = food.sourceReference?.let { source ->
                                runCatching { JSONObject(source).optString("branch_name", "") }.getOrDefault("")
                            }.orEmpty()
                            listOf(food.displayName(), food.name, food.brand.orEmpty(), branch)
                                .any { it.contains(term, ignoreCase = true) }
                        }
                    } else emptyList()
                    if (state.category == MealCatalogCategory.DINING_OUT) dining else {
                        val standard = nutritionCatalog.searchFoods(value).filterNot { food ->
                            food.isDiningOutMenu() || food.isDiningOutComponent()
                        }
                        val packaged = nutritionCatalog.searchPackagedFoods(value, 50)
                        val recipes = if (state.category == MealCatalogCategory.ALL || state.category == MealCatalogCategory.RECIPE) nutritionCatalog.savedRecipes().filter { food ->
                            value.isBlank() || listOf(food.name, food.brand.orEmpty()).any { it.contains(value.trim(), ignoreCase = true) }
                        } else emptyList()
                        (standard + packaged + recipes + dining).distinctBy { it.id }.filter(state.category::includes)
                    }
                }
                mainExecutor.execute {
                    val current = ready()
                    if (request == requestVersion && current.ownerId == state.ownerId &&
                        current.date == state.date && current.category == state.category &&
                        current.query == value) {
                        if (nutritionCatalog.activeOwnerId() != nutritionOwner) {
                            val message = "식품 검색 계정이 변경되었습니다. 다시 검색하세요."
                            mutableState.value = current.copy(searchResults = emptyList(), searching = false,
                                searchLoading = false, searchCompleted = true,
                                searchError = message, error = message)
                        } else {
                            val message = result.exceptionOrNull()?.message
                                ?: if (result.isFailure) "식품을 검색하지 못했습니다." else null
                            mutableState.value = current.copy(searchResults = result.getOrDefault(emptyList()),
                                searching = false, searchLoading = false, searchCompleted = true,
                                searchError = message, error = current.error.takeUnless { it == current.searchError } ?: message)
                        }
                    }
                }
            }
        }
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        ++requestVersion
    }

    fun openManualFood() {
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = true
        update { state -> state.copy(manualFoodEntry = true, error = null, catalogNotice = null,
            manualFoodDraft = state.manualFoodDraft.let { draft ->
                if (draft.name.isBlank()) draft.copy(name = state.query.trim()) else draft
            }) }
    }

    fun searchManualBrands() {
        val current = mutableState.value as? MealUiState.Ready ?: return
        if (current.saving || !current.manualFoodEntry || current.ownerId != ownerId) return
        val query = current.manualFoodDraft.brand.trim()
        val request = ++manualBrandRequestVersion
        manualBrandJob?.cancel()
        if (query.isBlank()) {
            update { it.copy(manualBrandSearch = NutritionBrandSearchState()) }
            return
        }
        val requestedOwner = ownerId
        val requestedDate = current.date
        val nutritionOwner = nutritionCatalog.currentOwnerId()
        val scope = nutritionIntegration.brandCatalogScope()
        update { it.copy(manualBrandSearch = NutritionBrandSearchState(query = query, loading = true)) }
        manualBrandJob = viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { requireNotNull(brandLookup) { "PriceTrace 브랜드 검색을 사용할 수 없습니다." }.search(query) }
            }
            val active = mutableState.value as? MealUiState.Ready ?: return@launch
            if (request != manualBrandRequestVersion || ownerId != requestedOwner || date != requestedDate ||
                !active.manualFoodEntry || nutritionCatalog.currentOwnerId() != nutritionOwner) return@launch
            if (nutritionIntegration.brandCatalogScope() != scope) {
                update { it.copy(manualBrandSearch = NutritionBrandSearchState(query,
                    searched = true, error = "연결 설정이 변경되었습니다. 다시 검색하세요.")) }
                return@launch
            }
            manualBrandResultScope = scope
            manualBrandResultOwner = nutritionOwner
            update { it.copy(manualBrandSearch = NutritionBrandSearchState(query,
                results = result.getOrDefault(emptyList()), searched = true,
                error = result.exceptionOrNull()?.let { it.message ?: "브랜드를 불러오지 못했습니다." })) }
        }
    }

    fun selectManualBrand(brand: BrandReadV1) {
        val current = mutableState.value as? MealUiState.Ready ?: return
        val search = current.manualBrandSearch
        if (current.saving || !current.manualFoodEntry || current.ownerId != ownerId || search.loading ||
            search.error != null || brand !in search.results || nutritionCatalog.currentOwnerId() != manualBrandResultOwner ||
            nutritionIntegration.brandCatalogScope() != manualBrandResultScope) return
        updateManualFood(current.manualFoodDraft.copy(brand = brand.canonicalName,
            priceTraceBrand = PriceTraceBrandSelection(brand.brandId, brand.canonicalName)))
    }

    fun clearManualBrand() {
        val current = mutableState.value as? MealUiState.Ready ?: return
        updateManualFood(current.manualFoodDraft.copy(priceTraceBrand = null))
    }

    fun closeManualFood() {
        ++manualBrandRequestVersion
        manualBrandJob?.cancel()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        update { it.copy(manualFoodEntry = false, error = null, manualBrandSearch = NutritionBrandSearchState(query = it.manualFoodDraft.brand)) }
    }

    fun updateManualFood(draft: ManualFoodDraft) {
        val current = mutableState.value as? MealUiState.Ready ?: return
        if (current.saving) return
        val changed = current.manualFoodDraft.brand != draft.brand || current.manualFoodDraft.priceTraceBrand != draft.priceTraceBrand
        if (changed) { ++manualBrandRequestVersion; manualBrandJob?.cancel() }
        savedStateHandle[KEY_MANUAL_FOOD] = arrayListOf(draft.name, draft.brand, draft.basisAmount,
            draft.basisUnit, draft.calories, draft.carbs, draft.protein, draft.fat, draft.sodium,
            draft.sugars, draft.saturatedFat, draft.registrationKind.name, draft.packageAmount, draft.packageUnit, draft.transFat,
            draft.priceTraceBrand?.brandId.orEmpty(), draft.priceTraceBrand?.canonicalName.orEmpty())
        update { it.copy(manualFoodDraft = draft, error = null, catalogNotice = null,
            manualBrandSearch = if (changed) NutritionBrandSearchState(query = draft.brand) else it.manualBrandSearch) }
    }

    fun saveManualFood(scope: AccountScope) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving || !state.manualFoodEntry) return
        if (savedStateHandle.get<String>(KEY_MANUAL_NUTRITION_OWNER) != nutritionCatalog.currentOwnerId()) {
            update { it.copy(error = "영양정보 계정이 변경되었습니다. 등록 화면을 다시 여세요.") }
            return
        }
        val input = runCatching { state.manualFoodDraft.validated() }.getOrElse { error ->
            update { it.copy(error = error.message) }
            return
        }
        val nutritionOwner = nutritionCatalog.activeOwnerId()
        if (nutritionOwner.isNullOrBlank()) {
            update { it.copy(error = "식품 저장 계정을 확인하지 못했습니다. 다시 시도하세요.") }
            return
        }
        cancelSearch()
        update { it.copy(saving = true, searching = false, searchLoading = false, error = null) }
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == nutritionOwner) { "식품 저장 계정이 변경되었습니다. 다시 시도하세요." }
                nutritionCatalog.saveManualFoodInput(AccountScope(nutritionOwner), input)
                    ?: throw IllegalStateException("식품을 등록하지 못했습니다.")
            }
            mainExecutor.execute publish@{
                if (ownerId != scope.ownerId || date != state.date) return@publish
                result.onSuccess { food ->
                    if (nutritionCatalog.activeOwnerId() != nutritionOwner) {
                        update { it.copy(saving = false, error = "식품 저장 계정이 변경되었습니다. 다시 검색하세요.") }
                    } else {
                        selectedFood = food
                        val portion = initialFoodPortion(food, preferCount = true)
                        val quantity = portion.quantity
                        savedStateHandle[KEY_FOOD_ID] = food.id
                        savedStateHandle[KEY_QUANTITY] = quantity
                        savedStateHandle[KEY_QUERY] = ""
                        update { current -> current.copy(saving = false, manualFoodEntry = false,
            manualFoodDraft = ManualFoodDraft(), manualBrandSearch = NutritionBrandSearchState(), selectedFood = food, quantity = quantity,
                            query = "", searchResults = emptyList(), searching = false,
                            searchLoading = false, searchCompleted = false, searchError = null,
                            foodItems = current.foodItems + MealFoodDraftItem(UUID.randomUUID().toString(), food, quantity, amountMode = portion.mode),
                            foodPortions = current.foodPortions + portion,
                            category = if (input.registrationKind == NutritionRegistrationKind.PRODUCT)
                                MealCatalogCategory.PRODUCT else MealCatalogCategory.INGREDIENT,
                            diningOut = false,
                            catalogNotice = "내 식품으로 등록했습니다. 섭취량을 확인하고 식단을 저장하세요.") }
                        savedStateHandle.remove<ArrayList<String>>(KEY_MANUAL_FOOD)
                    }
                }.onFailure { error -> update { it.copy(saving = false, error = error.message
                    ?: "식품을 등록하지 못했습니다. 다시 시도하세요.") } }
            }
        }
    }
    fun selectFood(food: NutritionFood) {
        val portion = initialFoodPortion(food, preferCount = true)
        selectFood(food, portion.quantity, portion.mode)
    }

    fun selectFood(food: NutritionFood, quantity: String, mode: PortionInputMode) {
        val state = ready()
        if (state.saving || state.draftLoading) return
        if (mealFoodItems(state).any { it.food.id == food.id } || diningMealMenus(state).any { it.nutritionFoodId == food.id }) {
            update { it.copy(notice = "${food.displayName()}은 이미 담겨 있어요. 이번 식단에서 양을 수정하세요.") }
            return
        }
        val portion = FoodPortionDraft(food, quantity, mode)
        if (mode !in portion.availableModes() || portion.amount == null) {
            update { it.copy(error = "먹은 양은 0보다 큰 숫자로 입력하세요.") }
            return
        }
        if (food.isDiningOutMenu()) {
            useDiningOutFood(food)
            if (ready().selectedFood?.id != food.id) return
            updateDiningPortion(quantity)
            addDiningMenu()
            update { it.copy(diningOut = it.category == MealCatalogCategory.DINING_OUT) }
            return
        }
        if (!food.basisAmount.isFinite() || food.basisAmount <= 0.0) {
            update { it.copy(error = "이 음식의 기준량을 확인할 수 없어요. 다른 음식을 선택하세요.") }
            return
        }
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        savedStateHandle[KEY_QUANTITY] = quantity
        update { current -> current.copy(selectedFood = food, quantity = quantity,
            error = null, searchLoading = false, searchCompleted = true, searchError = null,
            searching = false, manualFoodEntry = false,
            foodPortions = current.foodPortions + portion) }
    }

    fun updateQuantity(value: String) {
        val state = ready()
        if (state.saving) return
        if (!state.diningOut) {
            state.foodItems.lastOrNull()?.let { updateFoodQuantity(it.id, value) }
            return
        }
        savedStateHandle[KEY_QUANTITY] = value
        update { it.copy(quantity = value, error = null, notice = null) }
    }

    fun useComposition(templateId: String) {
        if (ready().saving || ready().draftLoading) return
        val repository = nutritionTemplates ?: return
        val request = ++requestVersion
        ++catalogRequestVersion
        val requestedOwner = ownerId
        val requestedDate = date
        val catalogOwner = nutritionCatalog.currentOwnerId()
        nutritionEditor.close()
        if (!savedStateHandle.contains(KEY_TIME)) savedStateHandle[KEY_TIME] = ready().draft.time
        update { it.copy(editing = true, diningOut = false, draftLoading = true, searchLoading = false, searchError = null, error = null) }
        executor.execute {
            val result = runCatching {
                check(repository.currentOwnerId() == catalogOwner) { "식단 구성 계정이 변경되었습니다." }
                val template = repository.findTemplate(templateId) ?: throw IllegalStateException("식단 구성을 찾지 못했습니다.")
                template.groups.sortedBy { it.orderIndex }.flatMap { it.members.sortedBy { member -> member.orderIndex } }
                    .filter { it.defaultSelected }.map { member ->
                        val food = nutritionCatalog.findFoodById(member.nutritionFoodId.orEmpty())
                            ?: throw IllegalStateException("${member.name} 식품을 찾지 못했습니다. 구성을 수정하세요.")
                        FoodPortionDraft(food, NutritionCalculator.trim(NutritionUnit.convert(member.quantity, member.unit, food.basisUnit)))
                    }.also { require(it.isNotEmpty()) { "식단 구성에 식품이 없습니다." } }
            }
            main.post {
                if (request != requestVersion || ownerId != requestedOwner || date != requestedDate || nutritionCatalog.currentOwnerId() != catalogOwner) return@post
                update { current -> result.fold({ portions ->
                    val merged = portions.fold(current.foodPortions) { items, portion ->
                        val existing = items.firstOrNull { it.food.id == portion.food.id }
                        if (existing == null) items + portion else items.map { item ->
                            if (item.food.id == portion.food.id) item.copy(
                                quantity = NutritionCalculator.trim((item.amount ?: 0.0) + portion.amount!!),
                                mode = PortionInputMode.BASIS
                            ) else item
                        }
                    }
                    current.copy(foodPortions = merged, draftLoading = false, query = "", searchResults = emptyList())
                }, { error -> current.copy(draftLoading = false, error = error.message) }) }
            }
        }
    }

    fun updateFoodQuantity(itemId: String, value: String) {
        val state = ready()
        if (state.saving) return
        val selectedItem = state.foodItems.lastOrNull { it.id == itemId || it.food.id == itemId }
        if (selectedItem != null && state.foodItems.lastOrNull()?.id == selectedItem.id) {
            savedStateHandle[KEY_QUANTITY] = value
        }
        update { current ->
            val lastItem = current.foodItems.lastOrNull()
            current.copy(foodItems = current.foodItems.map { item ->
                if (item.id == itemId || item.food.id == itemId) item.copy(quantity = value) else item
            }, foodPortions = current.foodPortions.map { portion ->
                if (portion.food.id == itemId || current.foodItems.any { it.id == itemId && it.food.id == portion.food.id }) portion.copy(quantity = value) else portion
            }, quantity = if (lastItem?.id == itemId || lastItem?.food?.id == itemId) value else current.quantity,
                error = null, notice = null)
        }
    }

    fun removeFood(itemId: String) {
        val state = ready()
        if (state.saving) return
        val items = mealFoodItems(state)
        val removedIndex = items.indexOfFirst { it.id == itemId || it.food.id == itemId }
        if (removedIndex < 0) {
            // The all-food picker also includes saved dining menus in the same draft.
            diningMealMenus(state).firstOrNull { it.nutritionFoodId == itemId }?.let { removeDiningMenu(it.id) }
            return
        }
        val remaining = state.foodItems.filterNot { it.id == itemId || it.food.id == itemId }
        val lastItem = remaining.lastOrNull()
        selectedFood = lastItem?.food
        if (lastItem == null) {
            savedStateHandle.remove<String>(KEY_FOOD_ID)
            savedStateHandle.remove<String>(KEY_QUANTITY)
        } else {
            savedStateHandle[KEY_FOOD_ID] = lastItem.food.id
            savedStateHandle[KEY_QUANTITY] = lastItem.quantity
        }
        update { it.copy(foodItems = remaining,
            foodPortions = it.foodPortions.filterNot { portion -> portion.food.id == itemId || state.foodItems.any { food -> food.id == itemId && food.food.id == portion.food.id } },
            selectedFood = lastItem?.food,
            quantity = lastItem?.quantity.orEmpty(), removedItem = RemovedMealDraftItem(removedIndex, food = items[removedIndex]),
            error = null, notice = null) }
    }

    fun updateFoodAmountMode(itemId: String, mode: PortionInputMode) {
        if (ready().saving) return
        update { current -> current.copy(foodItems = current.foodItems.map { item ->
            if (item.id != itemId && item.food.id != itemId) item else
                FoodPortionDraft(item.food, item.quantity, item.amountMode).withMode(mode)
                    ?.let { item.copy(quantity = it.quantity, amountMode = it.mode) } ?: item
        }, foodPortions = current.foodPortions.map { portion ->
            if (portion.food.id != itemId && current.foodItems.none { it.id == itemId && it.food.id == portion.food.id }) portion else
                current.foodItems.firstOrNull { it.id == itemId || it.food.id == itemId }?.let { item ->
                    FoodPortionDraft(item.food, item.quantity, item.amountMode).withMode(mode)?.let { portion.copy(quantity = it.quantity, mode = it.mode) }
                } ?: portion
        }, error = null) }
    }

fun saveFood(scope: AccountScope, onSaved: (Boolean) -> Unit) = save(scope, onSaved)
    fun useDiningOutFood(food: NutritionFood) {
        if (ready().saving) return
        if (ready().selectedFood?.id == food.id && hasCurrentDiningMenu(ready())) return
        if (!keepPendingDiningMenu()) return
        cancelSearch()
        val source = food.sourceReference?.let { sourceReference ->
            runCatching { JSONObject(sourceReference) }.getOrNull()
        }
        val store = food.brand?.takeIf { it.isNotBlank() }
            ?: sourceValue(source, "restaurant_name")
        val branch = sourceValue(source, "branch_name")
        savedStateHandle[KEY_STORE] = store
        savedStateHandle[KEY_BRANCH] = branch
        savedStateHandle[KEY_MENU] = food.name
        savedStateHandle[KEY_CALORIES] = food.profile.value(NutritionFoodProfileKeys.CALORIES)?.let(::number).orEmpty()
        savedStateHandle[KEY_CARBS] = food.profile.value(NutritionFoodProfileKeys.CARBS)?.let(::number).orEmpty()
        savedStateHandle[KEY_PROTEIN] = food.profile.value(NutritionFoodProfileKeys.PROTEIN)?.let(::number).orEmpty()
        savedStateHandle[KEY_FAT] = food.profile.value(NutritionFoodProfileKeys.FAT)?.let(::number).orEmpty()
        savedStateHandle[KEY_SODIUM] = food.profile.value(NutritionFoodProfileKeys.SODIUM)?.let(::number).orEmpty()
        savedStateHandle[KEY_SUGARS] = food.profile.value(NutritionFoodProfileKeys.SUGARS)?.let(::number).orEmpty()
        savedStateHandle[KEY_SATURATED_FAT] = food.profile.value(NutritionFoodProfileKeys.SATURATED_FAT)?.let(::number).orEmpty()
        savedStateHandle[KEY_TRANS_FAT] = food.profile.transFatGrams()?.let(::number).orEmpty()
        savedStateHandle[KEY_RESTAURANT_ID] = sourceValue(source, "restaurant_id")
        savedStateHandle[KEY_RESTAURANT_LOCATION_ID] = sourceValue(source, "restaurant_location_id")
        savedStateHandle[KEY_RESTAURANT_MENU_ID] = sourceValue(source, "restaurant_menu_id")
        savedStateHandle[KEY_CATALOG_PRODUCT_ID] = sourceValue(source, "catalog_product_id")
        savedStateHandle[KEY_SOURCE_NAMESPACE] = sourceValue(source, "source_namespace")
        savedStateHandle[KEY_SOURCE_LOCATION_CODE] = sourceValue(source, "source_location_code")
        savedStateHandle[KEY_DINING_PORTION] = "1"
        savedStateHandle[KEY_DINING_PERCENT] = "100"
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        mutableState.value = ready().copy(editing = true, diningOut = true,
            draft = savedDraft(), selectedFood = food, diningPortion = "1", diningConsumedPercent = "100", error = null, notice = null,
            searchLoading = false, searchError = null,
            searching = false, catalogNotice = null)
    }

    fun saveReusableDiningOutMenu(
        scope: AccountScope,
        onSaved: (Boolean) -> Unit = {}
    ) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving) return
        val draft = state.draft
        val parsed = runCatching { parseDining(draft) }.getOrElse { error ->
            mutableState.value = state.copy(error = error.message ?: "영양정보를 확인하세요.")
            onSaved(false)
            return
        }
        cancelSearch()
        update { it.copy(saving = true, searching = false, error = null, catalogNotice = null) }
        val requestedCatalogOwner = nutritionCatalog.currentOwnerId()
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == requestedCatalogOwner) { "영양정보 계정이 변경되었습니다. 메뉴를 다시 여세요." }
                nutritionCatalog.saveDiningOutMenuWithNutrition(
                    draft.store,
                    draft.menu,
                    parsed.calories,
                    parsed.protein,
                    parsed.carbs,
                    parsed.fat,
                    parsed.sodium,
                    parsed.sugars,
                    parsed.saturatedFat,
                    draft.branch.takeIf { it.isNotBlank() },
                    identityFromDraft(draft),
                    parsed.transFat
                ) ?: throw IllegalStateException("내 메뉴로 저장하지 못했습니다.")
            }
            mainExecutor.execute {
                if (ownerId == scope.ownerId && date == state.date) {
                    if (nutritionCatalog.currentOwnerId() != requestedCatalogOwner) {
                        update { it.copy(saving = false, error = "영양정보 계정이 변경되었습니다. 메뉴를 다시 여세요.") }
                        onSaved(false)
                    } else result.fold(
                        onSuccess = { saved ->
                            selectedFood = saved
                            savedStateHandle[KEY_FOOD_ID] = saved.id
                            savedStateHandle[KEY_QUERY] = ""
                            update { it.copy(saving = false, selectedFood = saved, query = "",
                                searchResults = emptyList(), error = null, notice = null,
                                catalogNotice = "내 메뉴로 저장했습니다. 다음 외식 기록에서 검색할 수 있습니다.") }
                            onSaved(true)
                        },
                        onFailure = { error ->
                            update { it.copy(saving = false, error = error.message ?: "내 메뉴로 저장하지 못했습니다.") }
                            onSaved(false)
                        }
                    )
                }
            }
        }
    }
    fun startPhotoEstimate() {
        val state = ready()
        if (state.saving || state.recordActionSaving || state.editing) return
        updatePhotoEstimate(PhotoEstimateDraft(time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))))
    }

    fun updatePhotoEstimate(draft: PhotoEstimateDraft) {
        if (ready().recordActionSaving) return
        savedStateHandle[KEY_PHOTO_ESTIMATE] = draft.toBundle()
        update { it.copy(photoEstimate = draft, error = null, notice = null) }
    }

    fun closePhotoEstimate() {
        if (ready().recordActionSaving) return
        savedStateHandle.remove<Bundle>(KEY_PHOTO_ESTIMATE)
        update { it.copy(photoEstimate = null, error = null) }
    }

    private fun openPhotoEstimateEdit(recordId: String) {
        if (ready().recordActionSaving) return
        val scope = AccountScope(ownerId)
        val requestedDate = date
        val request = ++requestVersion
        update { it.copy(recordActionSaving = true, error = null) }
        executor.execute {
            val result = runCatching {
                val snapshot = mealRepository.loadMealForEditing(scope, recordId) ?: error("식단 기록을 찾지 못했습니다.")
                check(snapshot.date == requestedDate)
                val input = snapshot.photoEstimate ?: error("사진 추정 기록이 아닙니다.")
                PhotoEstimateDraft(mealQuantityText(input.calories), snapshot.time,
                    input.proteinGrams?.let(::mealQuantityText).orEmpty(), input.carbsGrams?.let(::mealQuantityText).orEmpty(),
                    input.fatGrams?.let(::mealQuantityText).orEmpty(), snapshot.recordId, snapshot.updatedAt)
            }
            mainExecutor.execute {
                if (request == requestVersion && ownerId == scope.ownerId && date == requestedDate) {
                    update { it.copy(recordActionSaving = false) }
                    result.fold(::updatePhotoEstimate) { error -> update { it.copy(error = error.message) } }
                }
            }
        }
    }

    fun savePhotoEstimate(scope: AccountScope) {
        val state = ready()
        val draft = state.photoEstimate ?: return
        if (scope.ownerId != ownerId || state.recordActionSaving || state.saving) return
        draft.error()?.let { error -> update { it.copy(error = error) }; return }
        val input = draft.input()
        update { it.copy(recordActionSaving = true, error = null) }
        executor.execute {
            val result = runCatching {
                mealRepository.savePhotoEstimate(scope, state.date, draft.time, input, draft.recordId, draft.updatedAt)
            }
            mainExecutor.execute {
                if (ownerId == scope.ownerId && date == state.date) {
                    update { it.copy(recordActionSaving = false) }
                    result.fold({
                        closePhotoEstimate()
                        update { it.copy(notice = "사진 기반 추정 식단을 저장했습니다.") }
                        loadNutritionAnalysis(scope, state.date)
                    }, { error -> update { it.copy(error = error.message ?: "사진 추정 기록을 저장하지 못했습니다.") } })
                }
            }
        }
    }

    fun save(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving || state.draftLoading) return
        mealEntryRegistrationError(state)?.let { error ->
            update { it.copy(error = error) }
            onSaved(false)
            return
        }
        val items = mealFoodItems(state).map { item ->
            MealCompositionItem.from(item.food, requireNotNull(mealFoodConsumedQuantity(item)))
        }
        val diningInputs = diningMealMenus(state).map { menu ->
            val source = menu.draft
            val parsed = parseDining(source)
            DiningOutMenuIntake(DiningOutMealInput(source.store, source.branch, source.menu,
                requireNotNull(mealQuantityValue(menu.quantity)), parsed.calories, parsed.protein, parsed.carbs, parsed.fat,
                parsed.sodium, parsed.sugars, parsed.saturatedFat, source.restaurantId.takeIf(String::isNotBlank),
                source.restaurantLocationId.takeIf(String::isNotBlank), source.restaurantMenuId.takeIf(String::isNotBlank),
                source.catalogProductId.takeIf(String::isNotBlank), parsed.transFat), requireNotNull(mealConsumedFraction(menu.consumedPercent)),
                menu.nutritionFoodId, requireNotNull(source.calories.toDoubleOrNull()))
        }
        cancelSearch()
        update { it.copy(saving = true, searching = false, searchLoading = false, error = null) }
        executor.execute {
            try {
                val target = state.recordEditor
                if (target == null) mealRepository.saveMealItems(scope, state.date, state.draft.time, items, diningInputs)
                else check(mealRepository.updateMealItems(scope, MealRecordEditRequest(target.recordId, target.updatedAt,
                    mealFoodItems(state).map { it.recordItemId }, diningMealMenus(state).map { it.recordItemId }),
                    state.draft.time, items, diningInputs)) { "식단 내용을 수정하지 못했습니다." }
                mainExecutor.execute saved@{
                    if (ownerId == scope.ownerId && date == state.date) {
                        if (target != null) {
                            mutableState.value = ready().copy(saving = false)
                            restoreDraftBeforeEdit("식단 내용을 수정했습니다.")
                            loadNutritionAnalysis(scope, state.date)
                            onSaved(true)
                            return@saved
                        }
                        resetSavedDraft()
                        selectedFood = null
                        update { it.copy(selectedFood = null, foodItems = emptyList(), editing = false,
                            diningOut = false, category = MealCatalogCategory.ALL, query = "", searchResults = emptyList(), quantity = "", removedItem = null,
                            draft = DiningOutDraft(), saving = false, manualFoodEntry = false,
                            diningPortion = "1", diningConsumedPercent = "100", diningMenus = emptyList(),
                            manualFoodDraft = ManualFoodDraft(), catalogNotice = null,
                            notice = "${items.size + diningInputs.size}개 항목을 한 끼로 저장했습니다.") }
                        loadNutritionAnalysis(scope, state.date)
                    }
                    onSaved(true)
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        update { it.copy(saving = false, error = error.message ?: "식단 기록을 저장하지 못했습니다.") }
                    }
                    onSaved(false)
                }
            }
        }
    }
    private fun sourceValue(source: JSONObject?, key: String): String =
        source?.optString(key, "").orEmpty().takeUnless { it == "null" }.orEmpty()
    private fun identityFromDraft(draft: DiningOutDraft): DiningOutIdentity? {
        if (draft.restaurantId.isBlank() || draft.restaurantLocationId.isBlank()
            || draft.restaurantMenuId.isBlank() || draft.catalogProductId.isBlank()) return null
        return runCatching {
            if (draft.sourceNamespace.isNotBlank() || draft.sourceLocationCode.isNotBlank()) {
                DiningOutIdentity.fromPriceTrace(
                    draft.restaurantId,
                    draft.store,
                    draft.restaurantLocationId,
                    draft.sourceNamespace.takeIf { it.isNotBlank() },
                    draft.sourceLocationCode.takeIf { it.isNotBlank() },
                    draft.branch,
                    draft.restaurantMenuId,
                    draft.menu,
                    draft.catalogProductId
                )
            } else {
                DiningOutIdentity.fromPriceTrace(
                    draft.restaurantId,
                    draft.store,
                    draft.restaurantLocationId,
                    draft.branch,
                    draft.restaurantMenuId,
                    draft.menu,
                    draft.catalogProductId
                )
            }
        }.getOrNull()
    }
    private fun draft(key: String, value: String) {
        val changedIdentity = key in listOf(KEY_STORE, KEY_BRANCH, KEY_MENU) &&
            savedStateHandle.get<String>(key).orEmpty() != value
        if (changedIdentity) {
            val parentKeys = when (key) {
                KEY_STORE -> listOf(KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID, KEY_SOURCE_NAMESPACE, KEY_SOURCE_LOCATION_CODE)
                KEY_BRANCH -> listOf(KEY_RESTAURANT_LOCATION_ID, KEY_SOURCE_NAMESPACE, KEY_SOURCE_LOCATION_CODE)
                else -> emptyList()
            }
            (parentKeys + listOf(KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID, KEY_FOOD_ID))
                .forEach { savedStateHandle.remove<String>(it) }
            selectedFood = null
        }
        savedStateHandle[key] = value
        update { it.copy(draft = savedDraft(), selectedFood = if (changedIdentity) null else it.selectedFood,
            error = null, notice = null, catalogNotice = null) }
    }

    private fun update(block: (MealUiState.Ready) -> MealUiState.Ready) {
        val previous = ready()
        val proposed = block(previous)
        if (previous.saving && proposed.saving) return
        val next = when {
            proposed.foodItems != previous.foodItems && proposed.foodPortions == previous.foodPortions ->
                proposed.copy(foodPortions = proposed.foodItems.map { FoodPortionDraft(it.food, it.quantity, it.amountMode) })
            proposed.foodPortions != previous.foodPortions && proposed.foodItems == previous.foodItems ->
                proposed.copy(foodItems = proposed.foodPortions.map { portion ->
                    val existing = previous.foodItems.firstOrNull { it.food.id == portion.food.id }
                    MealFoodDraftItem(existing?.id ?: UUID.randomUUID().toString(), portion.food, portion.quantity,
                        existing?.consumedPercent ?: "100", portion.mode, existing?.recordItemId)
                })
            proposed.foodPortions != previous.foodPortions && proposed.foodItems != previous.foodItems -> {
                val itemsByFoodId = proposed.foodItems.associateBy { it.food.id }.toMutableMap()
                proposed.foodPortions.forEach { portion ->
                    val existing = itemsByFoodId[portion.food.id]
                    itemsByFoodId[portion.food.id] = MealFoodDraftItem(existing?.id ?: UUID.randomUUID().toString(),
                        portion.food, portion.quantity, existing?.consumedPercent ?: "100", portion.mode, existing?.recordItemId)
                }
                proposed.copy(foodItems = proposed.foodItems.mapNotNull { itemsByFoodId.remove(it.food.id) } + itemsByFoodId.values)
            }
            else -> proposed
        }
        mutableState.value = next
        savedStateHandle[KEY_EDITING] = next.editing
        savedStateHandle[KEY_DINING_OUT] = next.diningOut
        savedStateHandle[KEY_CATEGORY] = next.category.name
        if (!next.draftLoading && (next.recordEditor == null || next.recordEditor.updatedAt.isNotBlank())) {
            savedStateHandle[KEY_PORTION_IDS] = ArrayList(next.foodItems.map { it.food.id })
            savedStateHandle[KEY_PORTION_AMOUNTS] = ArrayList(next.foodItems.map { it.quantity })
            savedStateHandle[KEY_PORTION_PERCENTS] = ArrayList(next.foodItems.map { it.consumedPercent })
            savedStateHandle[KEY_PORTION_MODES] = ArrayList(next.foodItems.map { it.amountMode.name })
            savedStateHandle[KEY_DINING_MENUS] = ArrayList(next.diningMenus.map(::encodeDiningMenu))
        }
    }

    private fun loadNutritionAnalysis(scope: AccountScope, requestedDate: String) {
        val api = nutritionAnalysisApi ?: return
        val request = ++nutritionAnalysisRequestVersion
        mainExecutor.execute {
            if (request == nutritionAnalysisRequestVersion && ownerId == scope.ownerId && date == requestedDate) {
                update { it.copy(nutritionAnalysis = null, nutritionAnalysisLoading = true,
                    nutritionAnalysisError = null) }
            }
        }
        executor.execute {
            try {
                val report = api.analyzeDay(scope, requestedDate)
                mainExecutor.execute {
                    if (request == nutritionAnalysisRequestVersion && ownerId == scope.ownerId && date == requestedDate) {
                        update { it.copy(nutritionAnalysis = report, nutritionAnalysisLoading = false,
                            nutritionAnalysisError = null) }
                    }
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (request == nutritionAnalysisRequestVersion && ownerId == scope.ownerId && date == requestedDate) {
                        update { it.copy(nutritionAnalysis = null, nutritionAnalysisLoading = false,
                            nutritionAnalysisError = error.message ?: "영양 분석을 불러오지 못했습니다.") }
                    }
                }
            }
        }
    }
    private fun ready(): MealUiState.Ready = (mutableState.value as? MealUiState.Ready) ?: MealUiState.Ready(
        ownerId = ownerId,
        date = date,
        editing = savedStateHandle.get<Boolean>(KEY_EDITING) ?: savedStateHandle.get<Boolean>(KEY_MANUAL_FOOD_OPEN) ?: false,
        diningOut = savedStateHandle[KEY_DINING_OUT] ?: false,
        category = savedStateHandle.get<String>(KEY_CATEGORY)?.let { runCatching { MealCatalogCategory.valueOf(it) }.getOrNull() }
            ?: if (savedStateHandle.get<Boolean>(KEY_DINING_OUT) == true) MealCatalogCategory.DINING_OUT else MealCatalogCategory.ALL,
            query = savedStateHandle[KEY_QUERY] ?: "",
            searchResults = emptyList(),
            draft = savedDraft(),
            selectedFood = selectedFood,
        quantity = savedStateHandle[KEY_QUANTITY] ?: "",
        diningPortion = savedStateHandle[KEY_DINING_PORTION] ?: "1",
        diningConsumedPercent = savedStateHandle[KEY_DINING_PERCENT] ?: "100",
        diningMenus = savedStateHandle.get<ArrayList<String>>(KEY_DINING_MENUS).orEmpty().mapNotNull(::decodeDiningMenu),
        priceTraceQuery = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: "",
        manualFoodDraft = savedManualFoodDraft(),
        manualBrandSearch = NutritionBrandSearchState(query = savedManualFoodDraft().brand),
        manualFoodEntry = savedStateHandle[KEY_MANUAL_FOOD_OPEN] ?: false,
        photoEstimate = PhotoEstimateDraft.fromBundle(savedStateHandle[KEY_PHOTO_ESTIMATE])
    )

    private fun priceTraceReady(): PriceTraceUiState.Ready =
        (mutablePriceTraceState.value as? PriceTraceUiState.Ready)
            ?: PriceTraceUiState.Ready(ownerId = ownerId)

    private fun nutritionPublicationReady(): NutritionPublicationUiState =
        mutableNutritionPublicationState.value ?: NutritionPublicationUiState(ownerId = ownerId)

    private fun savedDraft() = DiningOutDraft(
        savedStateHandle[KEY_STORE] ?: "", savedStateHandle[KEY_BRANCH] ?: "",
        savedStateHandle[KEY_MENU] ?: "", savedStateHandle[KEY_CALORIES] ?: "",
        savedStateHandle[KEY_CARBS] ?: "", savedStateHandle[KEY_PROTEIN] ?: "",
        savedStateHandle[KEY_FAT] ?: "", savedStateHandle[KEY_SODIUM] ?: "",
        savedStateHandle[KEY_SUGARS] ?: "", savedStateHandle[KEY_SATURATED_FAT] ?: "",
        savedStateHandle[KEY_TIME] ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")),
        savedStateHandle[KEY_RESTAURANT_ID] ?: "",
        savedStateHandle[KEY_RESTAURANT_LOCATION_ID] ?: "",
        savedStateHandle[KEY_RESTAURANT_MENU_ID] ?: "",
        savedStateHandle[KEY_CATALOG_PRODUCT_ID] ?: "",
        savedStateHandle[KEY_SOURCE_NAMESPACE] ?: "",
        savedStateHandle[KEY_SOURCE_LOCATION_CODE] ?: "",
        transFat = savedStateHandle[KEY_TRANS_FAT] ?: ""
    )

    private fun resetSavedDraft() {
        clearDraftStorage()
    }

    private fun clearDraftStorage() {
        savedStateHandle.remove<Bundle>(KEY_PHOTO_ESTIMATE)
        savedStateHandle.remove<String>(KEY_EDIT_RECORD_ID)
        savedStateHandle.remove<Bundle>(KEY_DRAFT_BEFORE_EDIT)
        savedStateHandle.remove<ArrayList<String>>(KEY_MANUAL_FOOD)
        savedStateHandle.remove<Boolean>(KEY_MANUAL_FOOD_OPEN)
        listOf(KEY_STORE, KEY_BRANCH, KEY_MENU, KEY_CALORIES, KEY_CARBS, KEY_PROTEIN,
            KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT, KEY_TRANS_FAT, KEY_TIME, KEY_QUERY,
            KEY_FOOD_ID, KEY_QUANTITY, KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID,
            KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID, KEY_SOURCE_NAMESPACE,
            KEY_SOURCE_LOCATION_CODE, KEY_PRICE_TRACE_QUERY)
            .forEach { savedStateHandle.remove<String>(it) }
        listOf(KEY_EDITING, KEY_DINING_OUT, KEY_DINING_PORTION, KEY_DINING_PERCENT, KEY_DINING_MENUS,
            KEY_PORTION_IDS, KEY_PORTION_AMOUNTS, KEY_PORTION_PERCENTS, KEY_PORTION_MODES, KEY_CATEGORY, "meal.consumed_percent")
            .forEach { savedStateHandle.remove<Any>(it) }
    }

    private fun savedManualFoodDraft(): ManualFoodDraft {
        val values = savedStateHandle.get<ArrayList<String>>(KEY_MANUAL_FOOD) ?: return ManualFoodDraft()
        if (values.size < 11) return ManualFoodDraft()
        return ManualFoodDraft(values[0], values[1], values[2], values[3], values[4], values[5],
            values[6], values[7], values[8], values[9], values[10],
            values.getOrNull(11)?.let { runCatching { NutritionRegistrationKind.valueOf(it) }.getOrNull() }
                ?: NutritionRegistrationKind.INGREDIENT,
            values.getOrNull(12).orEmpty(), values.getOrNull(13) ?: NutritionUnit.GRAM,
            transFat = values.getOrNull(14).orEmpty(),
            priceTraceBrand = runCatching { PriceTraceBrandSelection.fromSnapshot(
                values.getOrNull(15)?.takeIf(String::isNotEmpty), values.getOrNull(16)?.takeIf(String::isNotEmpty), null, null) }.getOrNull())
    }

    override fun onCleared() {
        nutritionEditor.dispose()
        ++requestVersion
        ++detailRequestVersion
        searchJob?.cancel()
        manualBrandJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
    }
    private fun parseDining(draft: DiningOutDraft): ParsedDining {
        diningOutRegistrationError(draft)?.let { throw IllegalArgumentException(it) }
        return ParsedDining(
            requiredNumber(draft.calories, "칼로리").toInt(),
            requiredNumber(draft.protein, "단백질"),
            requiredNumber(draft.carbs, "탄수화물"),
            requiredNumber(draft.fat, "지방"),
            optionalNumber(draft.sodium, "나트륨"),
            optionalNumber(draft.sugars, "당류"),
            optionalNumber(draft.saturatedFat, "포화지방"),
            optionalNumber(draft.transFat, "트랜스지방")
        )
    }
    private fun requiredNumber(value: String, label: String): Double {
        val parsed = value.trim().toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed < 0.0) {
            throw IllegalArgumentException("${label}을 0 이상 숫자로 입력하세요.")
        }
        return parsed
    }
    private fun optionalNumber(value: String, label: String): Double? =
        if (value.trim().isEmpty()) null else requiredNumber(value, label)
    private data class ParsedDining(
        val calories: Int, val protein: Double, val carbs: Double, val fat: Double,
        val sodium: Double?, val sugars: Double?, val saturatedFat: Double?, val transFat: Double?
    )

    private object NutritionFoodProfileKeys {
        const val CALORIES = "calories_kcal"
        const val PROTEIN = "protein_grams"
        const val CARBS = "carbs_grams"
        const val FAT = "fat_grams"
        const val SODIUM = "sodium_mg"
        const val SUGARS = "sugars_grams"
        const val SATURATED_FAT = "saturated_fat_grams"
    }

    private companion object {
        private const val KEY_PHOTO_ESTIMATE = "meal.photo_estimate"
        const val KEY_EDIT_RECORD_ID = "meal.edit_record_id"
        const val KEY_DRAFT_BEFORE_EDIT = "meal.draft_before_record_edit"
        const val KEY_DRAFT_OWNER = "meal.draft_owner"
        const val KEY_DRAFT_DATE = "meal.draft_date"
        const val KEY_MANUAL_FOOD_OPEN = "meal.manual_food_open"
        const val KEY_MANUAL_FOOD = "meal.manual_food"
        const val KEY_MANUAL_NUTRITION_OWNER = "meal.manual_food_nutrition_owner"
        const val KEY_QUERY = "meal.query"
        const val KEY_STORE = "meal.store"
        const val KEY_BRANCH = "meal.branch"
        const val KEY_MENU = "meal.menu"
        const val KEY_CALORIES = "meal.calories"
        const val KEY_CARBS = "meal.carbs"
        const val KEY_PROTEIN = "meal.protein"
        const val KEY_FAT = "meal.fat"
        const val KEY_SODIUM = "meal.sodium"
        const val KEY_SUGARS = "meal.sugars"
        const val KEY_SATURATED_FAT = "meal.saturated_fat"
        const val KEY_TRANS_FAT = "meal.trans_fat"
        const val KEY_TIME = "meal.time"
        const val KEY_FOOD_ID = "meal.food_id"
        const val KEY_QUANTITY = "meal.quantity"
        const val KEY_RESTAURANT_ID = "meal.restaurant_id"
        const val KEY_RESTAURANT_LOCATION_ID = "meal.restaurant_location_id"
        const val KEY_RESTAURANT_MENU_ID = "meal.restaurant_menu_id"
        const val KEY_CATALOG_PRODUCT_ID = "meal.catalog_product_id"
        const val KEY_SOURCE_NAMESPACE = "meal.source_namespace"
        const val KEY_SOURCE_LOCATION_CODE = "meal.source_location_code"
        const val KEY_PRICE_TRACE_QUERY = "meal.price_trace_query"
        const val KEY_EDITING = "meal.editing"
        const val KEY_DINING_OUT = "meal.dining_out"
        const val KEY_DINING_PORTION = "meal.dining_portion"
        const val KEY_DINING_PERCENT = "meal.dining_percent"
        const val KEY_DINING_MENUS = "meal.dining_menus"
        const val KEY_PORTION_IDS = "meal.portion_ids"
        const val KEY_PORTION_AMOUNTS = "meal.portion_amounts"
        const val KEY_PORTION_PERCENTS = "meal.portion_percents"
        const val KEY_PORTION_MODES = "meal.portion_modes"
        const val KEY_CATEGORY = "meal.category"
        const val KEY_PUBLICATION_OPEN = "meal.publication.open"
        const val KEY_PUBLICATION_OWNER = "meal.publication.owner"
        const val KEY_PUBLICATION_NUTRITION_OWNER = "meal.publication.nutrition_owner"
        const val KEY_PUBLICATION_FOOD = "meal.publication.food"
        fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
