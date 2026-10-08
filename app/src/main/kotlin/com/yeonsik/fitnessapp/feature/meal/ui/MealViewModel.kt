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
        val foodItems: List<MealFoodDraftItem> = emptyList()
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
        val error: String? = null
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
    val notice: String? = null
) {
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
    private val searchDelayMillis: Long = 250L
) : ViewModel() {
    private val mutableState = MutableLiveData<MealUiState>(MealUiState.Idle)
    val uiState: LiveData<MealUiState> = mutableState
    private val mutablePriceTraceState = MutableLiveData<PriceTraceUiState>(PriceTraceUiState.Idle)
    val priceTraceState: LiveData<PriceTraceUiState> = mutablePriceTraceState
    private val mutableNutritionPublicationState = MutableLiveData(NutritionPublicationUiState())
    val nutritionPublicationState: LiveData<NutritionPublicationUiState> = mutableNutritionPublicationState
    @Volatile private var ownerId = ""
    @Volatile private var date = ""
    @Volatile private var requestVersion = 0L
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
        if (nutritionPublicationReady().ownerId.isNotBlank()
            && nutritionPublicationReady().ownerId != scope.ownerId) {
            ++nutritionPublicationRequestVersion
            mutableNutritionPublicationState.value = NutritionPublicationUiState(ownerId = scope.ownerId)
        }
        mealRepository.setUserId(scope.ownerId)
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
            foodItems = if (dateChanged) emptyList() else ready().foodItems
        )
        loadNutritionAnalysis(scope, date)
        mutablePriceTraceState.value = PriceTraceUiState.Ready(
            ownerId = scope.ownerId,
            query = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: ""
        )
        val selectedId = savedStateHandle.get<String>(KEY_FOOD_ID).orEmpty()
        if (selectedFood == null && selectedId.isNotBlank()) {
            executor.execute {
                val restored = nutritionCatalog.findFoodById(selectedId)
                mainExecutor.execute {
                    if (request == requestVersion && ownerId == scope.ownerId && this.date == date) {
                        selectedFood = restored
                        update { it.copy(selectedFood = restored) }
                    }
                }
            }
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
        selectedFood = null
        mutableState.value = ready().copy(editing = true, diningOut = true,
            draft = DiningOutDraft(), foodItems = emptyList(), selectedFood = null,
            quantity = "", error = null, notice = null)
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
        val state = priceTraceReady()
        mutablePriceTraceState.value = state.copy(query = value, error = null)
    }

    fun searchPriceTraceRestaurants() {
        val query = priceTraceReady().query.trim()
        if (query.isEmpty()) {
            mutablePriceTraceState.value = priceTraceReady().copy(error = "식당 검색어를 입력하세요.")
            return
        }
        val request = ++priceTraceRequestVersion
        mutablePriceTraceState.value = priceTraceReady().copy(
            loading = true,
            error = null,
            detail = null
        )
        executor.execute {
            try {
                val restaurants = nutritionIntegration.searchRestaurants(query)
                if (request == priceTraceRequestVersion) {
                    mutablePriceTraceState.postValue(
                        priceTraceReady().copy(
                            query = query,
                            restaurants = restaurants,
                            detail = null,
                            loading = false,
                            error = null
                        )
                    )
                }
            } catch (error: Exception) {
                if (request == priceTraceRequestVersion) {
                    mutablePriceTraceState.postValue(
                        priceTraceReady().copy(
                            loading = false,
                            error = error.message ?: "PriceTrace 식당을 찾지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun loadPriceTraceRestaurant(restaurantId: String) {
        val request = ++priceTraceRequestVersion
        mutablePriceTraceState.value = priceTraceReady().copy(loading = true, error = null)
        executor.execute {
            try {
                val detail = nutritionIntegration.loadRestaurant(restaurantId)
                if (request == priceTraceRequestVersion) {
                    mutablePriceTraceState.postValue(
                        priceTraceReady().copy(detail = detail, loading = false, error = null)
                    )
                }
            } catch (error: Exception) {
                if (request == priceTraceRequestVersion) {
                    mutablePriceTraceState.postValue(
                        priceTraceReady().copy(
                            loading = false,
                            error = error.message ?: "PriceTrace 메뉴를 불러오지 못했습니다."
                        )
                    )
                }
            }
        }
    }

    fun openNutritionPublication() {
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        val current = nutritionPublicationReady()
        mutableNutritionPublicationState.value = current.copy(
            ownerId = requestedOwner,
            open = true,
            loading = true,
            error = null,
            notice = null
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

    fun closeNutritionPublication() {
        ++nutritionPublicationRequestVersion
        mutableNutritionPublicationState.value = nutritionPublicationReady().copy(
            open = false,
            loading = false,
            error = null
        )
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

    /** Runs the existing Nutrition catalog sync, then refreshes the owner-scoped menu list. */
    fun syncNutritionPublicationCatalog() {
        val state = nutritionPublicationReady()
        if (!state.open || state.syncing || state.publishing || state.proposing) return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        mutableNutritionPublicationState.value = state.copy(
            syncing = true,
            error = null,
            notice = null
        )
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
        val location = detail.locations.singleOrNull {
            it.restaurantLocationId == locationId
        }
        val menu = detail.menus.singleOrNull {
            it.restaurantMenuId == menuId && it.catalogProductId == catalogProductId
        }
        if (location == null || menu == null) {
            mutableNutritionPublicationState.value = state.copy(
                error = "선택한 지점·메뉴가 현재 PriceTrace 상세와 일치하지 않습니다. 다시 선택하세요."
            )
            return
        }
        val identity = runCatching {
            DiningOutIdentity.fromPriceTrace(
                detail.restaurantId,
                detail.restaurantName,
                location.restaurantLocationId,
                location.locationSourceNamespace,
                location.sourceLocationCode,
                location.branchName,
                menu.restaurantMenuId,
                menu.menuName,
                menu.catalogProductId
            )
        }.getOrElse { error ->
            mutableNutritionPublicationState.value = state.copy(
                error = error.message ?: "PriceTrace identity가 올바르지 않습니다."
            )
            return
        }

        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        mutableNutritionPublicationState.value = state.copy(
            publishing = true,
            error = null,
            notice = null
        )
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
            }
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
        update { it.copy(editing = true, diningOut = true, draft = savedDraft(), query = "", searchResults = emptyList(), selectedFood = null, quantity = "", error = null, notice = null, searchLoading = false, searchCompleted = false, searchError = null) }
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
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle[KEY_QUANTITY] = quantity
        update { current -> current.copy(selectedFood = food, quantity = quantity,
            query = "", searchResults = emptyList(), error = null, searchLoading = false,
            searchCompleted = false, searchError = null, searching = false, manualFoodEntry = false,
            foodItems = current.foodItems + MealFoodDraftItem(UUID.randomUUID().toString(), food, quantity)) }
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

    fun updateFoodQuantity(itemId: String, value: String) {
        val state = ready()
        if (state.saving || state.diningOut) return
        if (state.foodItems.lastOrNull()?.id == itemId) savedStateHandle[KEY_QUANTITY] = value
        update { current ->
            current.copy(foodItems = current.foodItems.map { item ->
                if (item.id == itemId) item.copy(quantity = value) else item
            }, quantity = if (current.foodItems.lastOrNull()?.id == itemId) value else current.quantity,
                error = null, notice = null)
        }
    }

    fun removeFood(itemId: String) {
        val state = ready()
        if (state.saving || state.diningOut) return
        val remaining = state.foodItems.filterNot { it.id == itemId }
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
                            manualFoodEntry = false, manualFoodDraft = ManualFoodDraft(), catalogNotice = null,
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
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        mutableState.value = ready().copy(editing = true, diningOut = true, query = "", searchResults = emptyList(),
            draft = savedDraft(), selectedFood = food, error = null, notice = null,
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
        executor.execute {
            try {
                val saved = nutritionCatalog.saveDiningOutMenuWithNutrition(
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
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        selectedFood = saved
                        savedStateHandle[KEY_FOOD_ID] = saved.id
                        savedStateHandle[KEY_QUERY] = ""
                        update { it.copy(saving = false, selectedFood = saved, query = "",
                            searchResults = emptyList(), error = null, notice = null,
                            catalogNotice = "내 메뉴로 저장했습니다. 다음 외식 기록에서 검색할 수 있습니다.") }
                        onSaved(true)
                    }
                }
            } catch (error: Exception) {
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        update { it.copy(saving = false, error = error.message ?: "내 메뉴로 저장하지 못했습니다.") }
                        onSaved(false)
                    }
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
        cancelSearch()
        update { it.copy(saving = true, searching = false, searchLoading = false, error = null) }
        executor.execute {
            try {
                mealRepository.saveManualDiningOut(
                    scope,
                    state.date, draft.time, draft.store, draft.branch, draft.menu,
                    parsed.calories, parsed.protein, parsed.carbs, parsed.fat,
                    parsed.sodium, parsed.sugars, parsed.saturatedFat,
                    draft.restaurantId, draft.restaurantLocationId,
                    draft.restaurantMenuId, draft.catalogProductId
                )
                mainExecutor.execute {
                    if (ownerId == scope.ownerId && date == state.date) {
                        resetSavedDraft()
                        selectedFood = null
                        update { it.copy(selectedFood = null, foodItems = emptyList(), editing = false,
                            diningOut = false, query = "", searchResults = emptyList(), quantity = "",
                            draft = DiningOutDraft(), saving = false, manualFoodEntry = false,
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
        mutableState.value = block(ready())
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
        editing = savedStateHandle[KEY_MANUAL_FOOD_OPEN] ?: false,
        diningOut = false,
            query = savedStateHandle[KEY_QUERY] ?: "",
            searchResults = emptyList(),
            draft = savedDraft(),
            selectedFood = selectedFood,
        quantity = savedStateHandle[KEY_QUANTITY] ?: "",
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
        fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
