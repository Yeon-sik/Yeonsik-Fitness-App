package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.MealCompositionItem
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import com.yeonsik.fitnessapp.data.NutritionCalculator
import com.yeonsik.fitnessapp.data.NutritionUnit
import com.yeonsik.fitnessapp.feature.meal.model.FoodPortionInput
import com.yeonsik.fitnessapp.feature.meal.model.DiningOutMealInput
import com.yeonsik.fitnessapp.feature.nutrition.model.FoodPortionDraft
import com.yeonsik.fitnessapp.feature.nutrition.model.addFoodPortion
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionTemplateRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.ui.NutritionEditorController
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
    val quantity: String
)

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
    val sourceLocationCode: String = ""
)

data class MealRecordEditor(
    val recordId: String,
    val date: String,
    val title: String,
    val time: String
)

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
        val catalogNotice: String? = null,
        val foodItems: List<MealFoodDraftItem> = emptyList(),
        val foodPortions: List<FoodPortionDraft> = emptyList(),
        val diningPortion: String = "1",
        val draftLoading: Boolean = false
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
    private val nutritionTemplates: NutritionTemplateRepositoryApi? = null
) : ViewModel() {
    private val main = Handler(Looper.getMainLooper())
    val nutritionEditor = NutritionEditorController(savedStateHandle, nutritionCatalog, nutritionTemplates, executor)
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
    private var selectedFood: NutritionFood? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var searchJob: Job? = null

    fun enter(scope: AccountScope, date: String) {
        searchJob?.cancel()
        val previousOwner = ownerId.ifEmpty { savedStateHandle[KEY_DRAFT_OWNER] ?: "" }
        val previousDate = this.date.ifEmpty { savedStateHandle[KEY_DRAFT_DATE] ?: "" }
        val dateChanged = previousOwner != scope.ownerId || previousDate != date
        ownerId = scope.ownerId
        this.date = date
        val nutritionOwner = nutritionCatalog.currentOwnerId()
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
            catalogNotice = null,
            foodItems = if (dateChanged) emptyList() else ready().foodItems,
            foodPortions = if (dateChanged) emptyList() else ready().foodPortions,
            diningPortion = if (dateChanged) "1" else savedStateHandle[KEY_DINING_PORTION] ?: ready().diningPortion,
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
        val selectedId = savedStateHandle.get<String>(KEY_FOOD_ID).orEmpty()
        val portionIds = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_IDS).orEmpty()
        val portionAmounts = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_AMOUNTS).orEmpty()
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
                            portionAmounts.getOrElse(index) { mealQuantityText(food.basisAmount) }
                        )
                    }
                    selected to items
                }
                mainExecutor.execute {
                    if (request == requestVersion && ownerId == scope.ownerId && this.date == date) {
                        restored.fold({ (selected, items) ->
                            selectedFood = selected ?: items.lastOrNull()?.food
                            update { it.copy(selectedFood = selectedFood, foodItems = items, draftLoading = false) }
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
        if (state.selectedFood == null && !state.diningOut) search(state.query)
    }

    fun closeDraft() {
        if (ready().saving) return
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        update { it.copy(editing = false, diningOut = false, manualFoodEntry = false,
            searching = false, searchLoading = false, error = null) }
    }

    fun chooseFood() {
        val state = ready()
        if (state.saving || state.editing && !state.diningOut) return
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = false, query = "", searchResults = emptyList(),
            selectedFood = null, quantity = "", error = null, notice = null,
            searchLoading = false, searchCompleted = false, searchError = null,
            searching = false, manualFoodEntry = false, catalogNotice = null) }
        search("")
    }

    fun chooseDiningOut() {
        val state = ready()
        if (state.saving || state.editing && state.diningOut) return
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = true, query = "", searchResults = emptyList(),
            selectedFood = null, quantity = "", error = null, notice = null,
            searchLoading = false, searchCompleted = false, searchError = null,
            searching = false, manualFoodEntry = false, catalogNotice = null) }
        search("")
    }
    fun resetDraft() {
        if (ready().saving) return
        savedStateHandle.remove<String>(KEY_STORE)
        savedStateHandle.remove<String>(KEY_BRANCH)
        savedStateHandle.remove<String>(KEY_MENU)
        savedStateHandle.remove<String>(KEY_CALORIES)
        savedStateHandle.remove<String>(KEY_CARBS)
        savedStateHandle.remove<String>(KEY_PROTEIN)
        savedStateHandle.remove<String>(KEY_FAT)
        savedStateHandle.remove<String>(KEY_SODIUM)
        savedStateHandle.remove<String>(KEY_SUGARS)
        savedStateHandle.remove<String>(KEY_SATURATED_FAT)
        savedStateHandle.remove<String>(KEY_TIME)
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        savedStateHandle.remove<String>(KEY_RESTAURANT_ID)
        savedStateHandle.remove<String>(KEY_RESTAURANT_LOCATION_ID)
        savedStateHandle.remove<String>(KEY_RESTAURANT_MENU_ID)
        savedStateHandle.remove<String>(KEY_CATALOG_PRODUCT_ID)
        savedStateHandle.remove<String>(KEY_PRICE_TRACE_QUERY)
        savedStateHandle.remove<String>(KEY_SOURCE_NAMESPACE)
        savedStateHandle.remove<String>(KEY_SOURCE_LOCATION_CODE)
        savedStateHandle[KEY_DINING_PORTION] = "1"
        savedStateHandle.remove<ArrayList<String>>(KEY_PORTION_IDS)
        savedStateHandle.remove<ArrayList<String>>(KEY_PORTION_AMOUNTS)
        selectedFood = null
        mutableState.value = ready().copy(editing = true, diningOut = true,
            draft = DiningOutDraft(), foodItems = emptyList(), foodPortions = emptyList(), selectedFood = null,
            quantity = "", diningPortion = "1", error = null, notice = null)
    }

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
    fun updateTime(value: String) = draft(KEY_TIME, value)
    fun updateDiningPortion(value: String) {
        if (ready().saving) return
        savedStateHandle[KEY_DINING_PORTION] = value
        update { it.copy(diningPortion = value, error = null) }
    }

    fun openRecordEditor(meal: HomeMealSummary) {
        if (meal.id.isBlank() || meal.date != date) return
        ++requestVersion
        mutableState.value = ready().copy(
            recordEditor = MealRecordEditor(
                recordId = meal.id,
                date = meal.date,
                title = meal.previewTitle,
                time = meal.mealTime.takeUnless { it == "시간 미기록" }.orEmpty()
            ),
            error = null,
            notice = null
        )
    }

    fun closeRecordEditor() {
        ++requestVersion
        update { it.copy(recordEditor = null, recordActionSaving = false, error = null) }
    }

    fun saveMealTime(scope: AccountScope, recordId: String, mealTime: String) {
        val state = ready()
        if (scope.ownerId != ownerId || state.recordEditor?.recordId != recordId || state.recordActionSaving) {
            return
        }
        val request = ++requestVersion
        mutableState.value = state.copy(
            recordActionSaving = true,
            error = null,
            notice = null
        )
        executor.execute {
            try {
                if (!mealRepository.updateMealTime(scope, recordId, mealTime)) {
                    throw IllegalStateException("이 식단 기록 시간을 수정할 수 없습니다.")
                }
                if (request == requestVersion && ownerId == scope.ownerId) {
                    mutableState.postValue(
                        ready().copy(
                            recordEditor = null,
                            recordActionSaving = false,
                            notice = "식단 기록 시간을 수정했습니다."
                        )
                    )
                    loadNutritionAnalysis(scope, date)
                }
            } catch (error: Exception) {
                if (request == requestVersion && ownerId == scope.ownerId) {
                    mutableState.postValue(
                        ready().copy(
                            recordActionSaving = false,
                            error = error.message ?: "식단 기록 시간을 수정하지 못했습니다."
                        )
                    )
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
        if (!state.open || state.syncing || state.publishing || state.proposing) return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        val requestedNutritionOwner = current.nutritionOwnerId
        mutableNutritionPublicationState.value = current.copy(syncing = true, error = null, notice = null, failure = null)
        executor.execute {
            try {
                val result = nutritionIntegration.syncCurrentCatalog()
                val menus = nutritionCatalog.savedDiningOutMenus()
                    .filter { it.isDiningOutMenu() }
                val proposals = menus.mapNotNull { it.ownerId }.distinct().flatMap { diningProposals?.list(it).orEmpty() }
                val privateIds = menus.filter { it.ownerId != null && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }
                    .map { it.id }.toSet()
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            ownerId = requestedOwner,
                            menus = menus,
                            proposals = proposals,
                            privateFoodIds = privateIds,
                            remoteAvailable = menus.any { it.ownerId != null && diningProposals?.available(it.ownerId) == true },
                            selectedFoodId = state.selectedFoodId
                                ?.takeIf { id -> menus.any { it.id == id } },
                            syncing = false,
                            notice = "Nutrition 동기화 완료 · 가져옴 ${result.pulledRows}건 · 보냄 ${result.pushedRows}건",
                            error = null
                        )
                    )
                }
            } catch (error: Exception) {
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            syncing = false,
                            error = error.message ?: "Nutrition 동기화에 실패했습니다."
                        )
                    )
                }
            }
        }
    }

    /** Validates selected UUIDs against the loaded PriceTrace detail before writing. */
    fun publishNutritionMenuForPriceTraceSelection(
        locationId: String,
        menuId: String,
        catalogProductId: String
    ) {
        val state = nutritionPublicationReady()
        val food = state.selectedFood
        if (!state.open || food == null || state.publishing || state.syncing || state.proposing) return
        if (!state.allowsExistingMenuPublication) {
            mutableNutritionPublicationState.value = state.copy(
                error = "등록 제안은 승인 후 ‘PT에 공개 연결’ 버튼에서 공개하세요. 검토 중이거나 거절된 제안은 공개할 수 없습니다."
            )
            return
        }
        val detail = priceTraceReady().detail
        if (detail == null) {
            mutableNutritionPublicationState.value = state.copy(
                error = "PriceTrace 식당 상세를 다시 불러오세요."
            )
            return
        }
        val food = current.selectedFood ?: return reject("공개할 외식 영양정보를 먼저 선택해 주세요.")
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
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        mutableNutritionPublicationState.value = nutritionPublicationReady().copy(publishing = true, error = null,
            notice = null, failure = null, lastTarget = target, verifiedTarget = null, needsVerification = false)
        executor.execute {
            try {
                // Check the durable state again: the displayed list can predate a timeout or
                // an account change. Every tracked proposal must use its approved identity.
                val proposals = food.ownerId?.let { diningProposals?.list(it) }.orEmpty()
                check(proposals.none { it.nutritionFoodId == food.id }) {
                    "등록 제안은 승인 후 ‘PT에 공개 연결’ 버튼에서 공개하세요."
                }
                val result = nutritionIntegration.publishDiningOutForExistingMenu(food.id, identity)
                if (!result.state.isPublic || result.state.catalogProductId != catalogProductId) {
                    throw IllegalStateException("선택한 PT 메뉴의 공개 영양정보를 확인하지 못했습니다.")
                }
                val menus = nutritionCatalog.savedDiningOutMenus()
                    .filter { it.isDiningOutMenu() }
                val privateIds = menus.filter { it.ownerId != null
                    && nutritionCatalog.isPrivateDiningOutMenu(it.id, it.ownerId) }
                    .map { it.id }.toSet()
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            ownerId = requestedOwner,
                            menus = menus,
                            privateFoodIds = privateIds,
                            selectedFoodId = food.id,
                            publishing = false,
                            notice = "공개 완료 · ${menu.menuName} · PT 공개 조회에서 이 영양 행의 연결을 확인했습니다.",
                            error = null
                        )
                    )
                }
            } catch (error: Exception) {
                if (request == nutritionPublicationRequestVersion && ownerId == requestedOwner) {
                    mutableNutritionPublicationState.postValue(
                        nutritionPublicationReady().copy(
                            publishing = false,
                            error = error.message ?: "외식 영양정보를 연결하거나 공개하지 못했습니다."
                        )
                    )
                }
                // A failed local refresh must not turn a verified server result into a failed publication.
                runCatching { nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() } }.getOrNull()
            }
            applyPublicationResult(request, requestedOwner, target.nutritionOwnerId) { state ->
                result.fold({ menus -> state.copy(menus = menus ?: state.menus, publishing = false, verifiedTarget = target,
                    notice = "${target.label}의 영양정보 공개 연결을 확인했습니다." +
                        if (menus == null) " 메뉴 목록은 새로고침해 주세요." else "", error = null, failure = null)
                }, { error -> state.copy(publishing = false, failure = NutritionPublicationFailure.PUBLISH,
                    needsVerification = generateSequence<Throwable>(error) { it.cause }.take(6).any { it is java.io.IOException },
                    error = nutritionPublicationError(error, "공개 연결을 완료하지 못했어요. 선택한 메뉴와 계정을 확인해 주세요.", publishing = true)) })
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
        ++requestVersion
        ++catalogRequestVersion
        val targetChanged = ready().draft.let {
            it.restaurantId != restaurantId || it.restaurantLocationId != locationId ||
                it.restaurantMenuId != menuId || it.catalogProductId != catalogProductId
        }
        if (targetChanged) {
            listOf(KEY_CALORIES, KEY_CARBS, KEY_PROTEIN, KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT)
                .forEach { savedStateHandle.remove<String>(it) }
            savedStateHandle[KEY_DINING_PORTION] = "1"
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
        update { it.copy(editing = true, diningOut = true, draft = savedDraft(), diningPortion = "1", query = "", searchResults = emptyList(), selectedFood = null, quantity = "", error = null, notice = null, searchLoading = false, searchCompleted = false, searchError = null) }
    }

    fun search(value: String) {
        val state = ready()
        if (!state.editing || state.saving) return
        val nutritionOwner = nutritionCatalog.activeOwnerId()
        savedStateHandle[KEY_QUERY] = value
        searchJob?.cancel()
        val request = ++requestVersion
        update { it.copy(query = value, searchResults = emptyList(), error = null,
            searchLoading = true, searchCompleted = false, searchError = null, searching = true) }
        searchJob = viewModelScope.launch {
            delay(searchDelayMillis)
            executor.execute {
                if (request != requestVersion) return@execute
                val result = runCatching {
                    if (state.diningOut) {
                        val term = value.trim()
                        nutritionCatalog.savedDiningOutMenus().filter { food ->
                            val branch = food.sourceReference?.let { source ->
                                runCatching { JSONObject(source).optString("branch_name", "") }.getOrDefault("")
                            }.orEmpty()
                            listOf(food.displayName(), food.name, food.brand.orEmpty(), branch)
                                .any { it.contains(term, ignoreCase = true) }
                        }
                    } else {
                        val standard = nutritionCatalog.searchFoods(value).filterNot { food ->
                            food.isDiningOutMenu() || food.isDiningOutComponent()
                        }
                        val packaged = nutritionCatalog.searchPackagedFoods(value, 50)
                        (standard + packaged).distinctBy { it.id }
                    }
                }
                mainExecutor.execute {
                    val current = ready()
                    if (request == requestVersion && current.ownerId == state.ownerId &&
                        current.date == state.date && current.diningOut == state.diningOut &&
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
                                searchError = message, error = message)
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

    fun closeManualFood() {
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        update { it.copy(manualFoodEntry = false, error = null) }
    }

    fun updateManualFood(draft: ManualFoodDraft) {
        savedStateHandle[KEY_MANUAL_FOOD] = arrayListOf(draft.name, draft.brand, draft.basisAmount,
            draft.basisUnit, draft.calories, draft.carbs, draft.protein, draft.fat, draft.sodium,
            draft.sugars, draft.saturatedFat)
        update { it.copy(manualFoodDraft = draft, error = null, catalogNotice = null) }
    }

    fun saveManualFood(scope: AccountScope) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving || state.diningOut || !state.manualFoodEntry) return
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
                nutritionCatalog.saveManualFood(AccountScope(nutritionOwner), input.name, input.brand,
                    input.basisAmount, input.basisUnit, input.profile)
                    ?: throw IllegalStateException("식품을 등록하지 못했습니다.")
            }
            mainExecutor.execute publish@{
                if (ownerId != scope.ownerId || date != state.date) return@publish
                result.onSuccess { food ->
                    if (nutritionCatalog.activeOwnerId() != nutritionOwner) {
                        update { it.copy(saving = false, error = "식품 저장 계정이 변경되었습니다. 다시 검색하세요.") }
                    } else {
                        selectedFood = food
                        val quantity = mealQuantityText(food.basisAmount)
                        savedStateHandle[KEY_FOOD_ID] = food.id
                        savedStateHandle[KEY_QUANTITY] = quantity
                        savedStateHandle[KEY_QUERY] = ""
                        update { current -> current.copy(saving = false, manualFoodEntry = false,
                            manualFoodDraft = ManualFoodDraft(), selectedFood = food, quantity = quantity,
                            query = "", searchResults = emptyList(), searching = false,
                            searchLoading = false, searchCompleted = false, searchError = null,
                            foodItems = current.foodItems + MealFoodDraftItem(UUID.randomUUID().toString(), food, quantity),
                            catalogNotice = "내 식품으로 등록했습니다. 섭취량을 확인하고 식단을 저장하세요.") }
                        savedStateHandle.remove<ArrayList<String>>(KEY_MANUAL_FOOD)
                    }
                }.onFailure { error -> update { it.copy(saving = false, error = error.message
                    ?: "식품을 등록하지 못했습니다. 다시 시도하세요.") } }
            }
        }
    }
    fun selectFood(food: NutritionFood) {
        val state = ready()
        if (state.saving || state.diningOut) return
        if (!food.basisAmount.isFinite() || food.basisAmount <= 0.0) {
            update { it.copy(error = "이 음식의 기준량을 확인할 수 없어요. 다른 음식을 선택하세요.") }
            return
        }
        cancelSearch()
        savedStateHandle[KEY_MANUAL_FOOD_OPEN] = false
        selectedFood = food
        val quantity = mealQuantityText(food.basisAmount)
        savedStateHandle[KEY_FOOD_ID] = food.id
        savedStateHandle[KEY_QUANTITY] = quantity
        update { current -> current.copy(selectedFood = food, quantity = quantity,
            error = null, searchLoading = false, searchCompleted = true, searchError = null,
            searching = false, manualFoodEntry = false,
            foodPortions = addFoodPortion(current.foodPortions, food)) }
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
                            if (item.food.id == portion.food.id) item.copy(quantity = NutritionCalculator.trim((item.amount ?: 0.0) + portion.amount!!)) else item
                        }
                    }
                    current.copy(foodPortions = merged, draftLoading = false, query = "", searchResults = emptyList())
                }, { error -> current.copy(draftLoading = false, error = error.message) }) }
            }
        }
    }

    fun updateFoodQuantity(itemId: String, value: String) {
        val state = ready()
        if (state.saving || state.diningOut) return
        val selectedItem = state.foodItems.lastOrNull { it.id == itemId || it.food.id == itemId }
        if (selectedItem != null && state.foodItems.lastOrNull()?.id == selectedItem.id) {
            savedStateHandle[KEY_QUANTITY] = value
        }
        update { current ->
            val lastItem = current.foodItems.lastOrNull()
            current.copy(foodItems = current.foodItems.map { item ->
                if (item.id == itemId || item.food.id == itemId) item.copy(quantity = value) else item
            }, quantity = if (lastItem?.id == itemId || lastItem?.food?.id == itemId) value else current.quantity,
                error = null, notice = null)
        }
    }

    fun removeFood(itemId: String) {
        val state = ready()
        if (state.saving || state.diningOut) return
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
        update { it.copy(foodItems = remaining, selectedFood = lastItem?.food,
            quantity = lastItem?.quantity.orEmpty(), error = null, notice = null) }
    }

    fun saveFood(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving || state.diningOut) return
        val inputError = foodMealRegistrationError(state)
        if (inputError != null) {
            mutableState.value = state.copy(error = inputError)
            onSaved(false)
            return
        }
        val items = try {
            state.foodItems.map { item ->
                MealCompositionItem.from(item.food, requireNotNull(mealQuantityValue(item.quantity)))
            }
        } catch (error: IllegalArgumentException) {
            update { it.copy(error = error.message ?: "음식별 먹은 양을 확인하세요.") }
            onSaved(false)
            return
        }
        cancelSearch()
        update { it.copy(saving = true, error = null, searching = false, searchLoading = false) }
        executor.execute {
            try {
                mealRepository.saveFoodMealItems(scope, state.date, state.draft.time, items)
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        resetSavedDraft()
                        selectedFood = null
                        update { it.copy(editing = false, diningOut = false, query = "",
                            searchResults = emptyList(), selectedFood = null, quantity = "",
                            foodItems = emptyList(), draft = DiningOutDraft(), saving = false,
                            diningPortion = "1", manualFoodEntry = false,
                            manualFoodDraft = ManualFoodDraft(), catalogNotice = null,
                            notice = "음식 ${items.size}개를 한 끼로 저장했습니다.") }
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
    fun useDiningOutFood(food: NutritionFood) {
        if (ready().saving) return
        cancelSearch()
        savedStateHandle[KEY_QUERY] = ""
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
        savedStateHandle[KEY_RESTAURANT_ID] = sourceValue(source, "restaurant_id")
        savedStateHandle[KEY_RESTAURANT_LOCATION_ID] = sourceValue(source, "restaurant_location_id")
        savedStateHandle[KEY_RESTAURANT_MENU_ID] = sourceValue(source, "restaurant_menu_id")
        savedStateHandle[KEY_CATALOG_PRODUCT_ID] = sourceValue(source, "catalog_product_id")
        savedStateHandle[KEY_SOURCE_NAMESPACE] = sourceValue(source, "source_namespace")
        savedStateHandle[KEY_SOURCE_LOCATION_CODE] = sourceValue(source, "source_location_code")
        savedStateHandle[KEY_DINING_PORTION] = "1"
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        mutableState.value = ready().copy(editing = true, diningOut = true, query = "", searchResults = emptyList(),
            draft = savedDraft(), selectedFood = food, diningPortion = "1", error = null, notice = null,
            searchLoading = false, searchCompleted = false, searchError = null,
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
                    identityFromDraft(draft)
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
    fun save(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving) return
        val draft = state.draft
        mealTimeError(draft.time)?.let { error ->
            mutableState.value = state.copy(error = error)
            onSaved(false)
            return
        }
        val parsed = runCatching { parseDining(draft) }.getOrElse { error ->
            mutableState.value = state.copy(error = error.message ?: "칼로리와 필수 영양정보를 확인하세요.")
            onSaved(false)
            return
        }
        val quantity = state.diningPortion.trim().toDoubleOrNull()
        if (quantity == null || !quantity.isFinite() || quantity <= 0.0) {
            mutableState.value = state.copy(error = "먹은 양을 0보다 큰 숫자로 입력하세요.")
            onSaved(false)
            return
        }
        val diningInput = DiningOutMealInput(
            storeName = draft.store,
            branchName = draft.branch,
            menuName = draft.menu,
            quantity = quantity,
            calories = parsed.calories,
            proteinGrams = parsed.protein,
            carbsGrams = parsed.carbs,
            fatGrams = parsed.fat,
            sodiumMg = parsed.sodium,
            sugarsGrams = parsed.sugars,
            saturatedFatGrams = parsed.saturatedFat,
            restaurantId = draft.restaurantId.takeIf { it.isNotBlank() },
            restaurantLocationId = draft.restaurantLocationId.takeIf { it.isNotBlank() },
            restaurantMenuId = draft.restaurantMenuId.takeIf { it.isNotBlank() },
            catalogProductId = draft.catalogProductId.takeIf { it.isNotBlank() }
        )
        cancelSearch()
        update { it.copy(saving = true, searching = false, searchLoading = false, error = null) }
        executor.execute {
            try {
                mealRepository.saveDiningOutPortion(scope, state.date, draft.time, diningInput)
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        resetSavedDraft()
                        selectedFood = null
                        update { it.copy(selectedFood = null, foodItems = emptyList(), editing = false,
                            diningOut = false, query = "", searchResults = emptyList(), quantity = "",
                            draft = DiningOutDraft(), saving = false, manualFoodEntry = false,
                            diningPortion = "1",
                            manualFoodDraft = ManualFoodDraft(), catalogNotice = null,
                            notice = "외식 기록을 저장했습니다.") }
                        loadNutritionAnalysis(scope, state.date)
                    }
                    onSaved(true)
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        update { it.copy(saving = false, error = error.message ?: "외식 기록을 저장하지 못했습니다.") }
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
            listOf(KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID, KEY_RESTAURANT_MENU_ID,
                KEY_CATALOG_PRODUCT_ID, KEY_SOURCE_NAMESPACE, KEY_SOURCE_LOCATION_CODE, KEY_FOOD_ID)
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
                proposed.copy(foodPortions = proposed.foodItems.map { FoodPortionDraft(it.food, it.quantity) })
            proposed.foodPortions != previous.foodPortions && proposed.foodItems == previous.foodItems ->
                proposed.copy(foodItems = proposed.foodPortions.map { portion ->
                    val existing = previous.foodItems.firstOrNull { it.food.id == portion.food.id }
                    MealFoodDraftItem(existing?.id ?: UUID.randomUUID().toString(), portion.food, portion.quantity)
                })
            else -> proposed
        }
        mutableState.value = next
        savedStateHandle[KEY_EDITING] = next.editing
        savedStateHandle[KEY_DINING_OUT] = next.diningOut
        if (!next.draftLoading) {
            savedStateHandle[KEY_PORTION_IDS] = ArrayList(next.foodItems.map { it.food.id })
            savedStateHandle[KEY_PORTION_AMOUNTS] = ArrayList(next.foodItems.map { it.quantity })
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
        editing = savedStateHandle[KEY_EDITING] ?: savedStateHandle[KEY_MANUAL_FOOD_OPEN] ?: false,
        diningOut = savedStateHandle[KEY_DINING_OUT] ?: false,
            query = savedStateHandle[KEY_QUERY] ?: "",
            searchResults = emptyList(),
            draft = savedDraft(),
            selectedFood = selectedFood,
        quantity = savedStateHandle[KEY_QUANTITY] ?: "",
        diningPortion = savedStateHandle[KEY_DINING_PORTION] ?: "1",
        priceTraceQuery = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: "",
        manualFoodDraft = savedManualFoodDraft(),
        manualFoodEntry = savedStateHandle[KEY_MANUAL_FOOD_OPEN] ?: false
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
        savedStateHandle[KEY_SOURCE_LOCATION_CODE] ?: ""
    )

    private fun resetSavedDraft() {
        clearDraftStorage()
    }

    private fun clearDraftStorage() {
        savedStateHandle.remove<ArrayList<String>>(KEY_MANUAL_FOOD)
        savedStateHandle.remove<Boolean>(KEY_MANUAL_FOOD_OPEN)
        listOf(KEY_STORE, KEY_BRANCH, KEY_MENU, KEY_CALORIES, KEY_CARBS, KEY_PROTEIN,
            KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT, KEY_TIME, KEY_QUERY,
            KEY_FOOD_ID, KEY_QUANTITY, KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID,
            KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID, KEY_SOURCE_NAMESPACE,
            KEY_SOURCE_LOCATION_CODE, KEY_PRICE_TRACE_QUERY)
            .forEach { savedStateHandle.remove<String>(it) }
        listOf(KEY_EDITING, KEY_DINING_OUT, KEY_DINING_PORTION, KEY_PORTION_IDS, KEY_PORTION_AMOUNTS)
            .forEach { savedStateHandle.remove<Any>(it) }
    }

    private fun savedManualFoodDraft(): ManualFoodDraft {
        val values = savedStateHandle.get<ArrayList<String>>(KEY_MANUAL_FOOD) ?: return ManualFoodDraft()
        if (values.size != 11) return ManualFoodDraft()
        return ManualFoodDraft(values[0], values[1], values[2], values[3], values[4], values[5],
            values[6], values[7], values[8], values[9], values[10])
    }

    override fun onCleared() {
        ++requestVersion
        searchJob?.cancel()
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
            optionalNumber(draft.saturatedFat, "포화지방")
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
        val sodium: Double?, val sugars: Double?, val saturatedFat: Double?
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
        const val KEY_DRAFT_OWNER = "meal.draft_owner"
        const val KEY_DRAFT_DATE = "meal.draft_date"
        const val KEY_MANUAL_FOOD_OPEN = "meal.manual_food_open"
        const val KEY_MANUAL_FOOD = "meal.manual_food"
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
        const val KEY_PORTION_IDS = "meal.portion_ids"
        const val KEY_PORTION_AMOUNTS = "meal.portion_amounts"
        const val KEY_PUBLICATION_OPEN = "meal.publication.open"
        const val KEY_PUBLICATION_OWNER = "meal.publication.owner"
        const val KEY_PUBLICATION_NUTRITION_OWNER = "meal.publication.nutrition_owner"
        const val KEY_PUBLICATION_FOOD = "meal.publication.food"
        fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
