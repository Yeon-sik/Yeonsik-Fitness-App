package com.yeonsik.fitnessapp.feature.meal.ui

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.NutritionFood
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
import com.yeonsik.fitnessapp.feature.home.model.HomeMealSummary
import com.yeonsik.fitnessapp.feature.nutrition.analysis.api.NutritionAnalysisApi
import com.yeonsik.fitnessapp.feature.nutrition.analysis.model.NutritionAnalysisReport
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    val error: String? = null,
    val notice: String? = null,
    val nutritionOwnerId: String = "",
    val verifying: Boolean = false,
    val failure: NutritionPublicationFailure? = null,
    val lastTarget: NutritionPublicationTarget? = null,
    val verifiedTarget: NutritionPublicationTarget? = null,
    val needsVerification: Boolean = false
) {
    val working: Boolean get() = syncing || publishing || verifying
    val busy: Boolean get() = loading || working
    val progressLabel: String? get() = when {
        loading -> "저장된 메뉴를 불러오는 중"
        syncing -> "영양정보 동기화 중"
        publishing -> "영양정보 공개 및 연결 확인 중"
        verifying -> "공개 상태 확인 중"
        else -> null
    }
    val selectedFood: NutritionFood?
        get() = menus.firstOrNull { it.id == selectedFoodId }
}

/** Owns meal editor/search state; Compose only renders state and emits actions. */
class MealViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRecordRepositoryApi,
    private val nutritionCatalog: NutritionCatalogRepositoryApi,
    private val nutritionIntegration: NutritionIntegrationService,
    private val nutritionAnalysisApi: NutritionAnalysisApi? = null,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor(),
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
    private var ownerId = ""
    private var date = ""
    private var requestVersion = 0L
    private var priceTraceRequestVersion = 0L
    private var nutritionPublicationRequestVersion = 0L
    private var nutritionAnalysisRequestVersion = 0L
    private var selectedFood: NutritionFood? = null

    fun enter(scope: AccountScope, date: String) {
        val dateChanged = ownerId != scope.ownerId || this.date != date
        ownerId = scope.ownerId
        this.date = date
        val nutritionOwner = nutritionCatalog.currentOwnerId()
        val publication = nutritionPublicationReady()
        val restorePublication = publication.ownerId != scope.ownerId || publication.nutritionOwnerId != nutritionOwner
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
        val request = ++requestVersion
        if (dateChanged) {
            selectedFood = null
            val sameSavedScope = savedStateHandle.get<String>(KEY_OWNER) == scope.ownerId &&
                savedStateHandle.get<String>(KEY_DATE) == date
            if (!sameSavedScope) clearDraftStorage()
            mutableState.value = MealUiState.Ready(
                ownerId = scope.ownerId, date = date,
                editing = savedStateHandle[KEY_EDITING] ?: false,
                diningOut = savedStateHandle[KEY_DINING_OUT] ?: false,
                query = savedStateHandle[KEY_QUERY] ?: "", searchResults = emptyList(), draft = savedDraft(),
                quantity = savedStateHandle[KEY_QUANTITY] ?: "",
                diningPortion = savedStateHandle[KEY_DINING_PORTION] ?: "1"
            )
        }
        savedStateHandle[KEY_OWNER] = scope.ownerId
        savedStateHandle[KEY_DATE] = date
        nutritionEditor.synchronizeOwner()
        if (nutritionEditor.uiState.value?.open == true) nutritionEditor.open()
        mutableState.value = ready().copy(
            ownerId = scope.ownerId,
            date = date,
            recordEditor = null,
            recordActionSaving = false,
            error = null,
            notice = null,
            nutritionAnalysis = null,
            nutritionAnalysisLoading = nutritionAnalysisApi != null,
            nutritionAnalysisError = null
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
        val foodIds = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_IDS).orEmpty()
        val amounts = savedStateHandle.get<ArrayList<String>>(KEY_PORTION_AMOUNTS).orEmpty()
        if ((dateChanged || ready().draftLoading) && (selectedId.isNotBlank() || foodIds.isNotEmpty())) {
            mutableState.value = ready().copy(draftLoading = true)
            executor.execute {
                val restored = runCatching {
                    val selected = selectedId.takeIf { it.isNotBlank() }?.let(nutritionCatalog::findFoodById)
                    val portions = foodIds.mapIndexed { index, id ->
                        val food = nutritionCatalog.findFoodById(id)
                            ?: throw IllegalStateException("초안의 식품을 찾지 못했습니다. 식품을 다시 추가하세요.")
                        FoodPortionDraft(food, amounts.getOrElse(index) { NutritionCalculator.trim(food.basisAmount) })
                    }
                    selected to portions
                }
                main.post {
                    if (request != requestVersion || ownerId != scope.ownerId || this.date != date) return@post
                    restored.fold({ (food, portions) ->
                        selectedFood = food
                        mutableState.value = ready().copy(selectedFood = food, foodPortions = portions, draftLoading = false)
                        if (ready().editing) search(ready().query)
                    }, { error -> mutableState.value = ready().copy(draftLoading = false, error = error.message) })
                }
            }
        } else if (ready().editing && !ready().saving) {
            search(ready().query)
        }
    }

    fun startDraft() {
        if (ready().saving || ready().draftLoading || ready().recordActionSaving) return
        if (!savedStateHandle.contains(KEY_TIME)) savedStateHandle[KEY_TIME] = ready().draft.time
        update { it.copy(editing = true, notice = null, error = null) }
        search(ready().query)
    }
    fun closeDraft() {
        if (ready().saving) return
        update { it.copy(editing = false, error = null) }
    }
    fun chooseFood() {
        if (ready().saving || ready().draftLoading || !ready().diningOut) return
        ++requestVersion
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = false, query = "", searchResults = emptyList(), selectedFood = null, error = null) }
        search("")
    }
    fun chooseDiningOut() {
        if (ready().saving || ready().draftLoading || ready().diningOut) return
        ++requestVersion
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = true, query = "", searchResults = emptyList(), selectedFood = null, error = null) }
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
        savedStateHandle.remove<ArrayList<String>>(KEY_PORTION_IDS)
        savedStateHandle.remove<ArrayList<String>>(KEY_PORTION_AMOUNTS)
        update { it.copy(editing = true, diningOut = true, draft = DiningOutDraft(), foodPortions = emptyList(), selectedFood = null, quantity = "") }
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
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == requestedNutritionOwner) { "영양정보 계정이 변경되었습니다." }
                nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() }
            }
            applyPublicationResult(request, requestedOwner, requestedNutritionOwner) { state ->
                result.fold({ menus ->
                    val selected = state.selectedFoodId?.takeIf { id -> menus.any { it.id == id } }
                    val selectionRemoved = state.selectedFoodId != null && selected == null
                    savedStateHandle[KEY_PUBLICATION_FOOD] = selected
                    state.copy(menus = menus, selectedFoodId = selected, loading = false,
                        lastTarget = state.lastTarget?.takeIf { it.foodId == selected },
                        verifiedTarget = state.verifiedTarget?.takeIf { it.foodId == selected },
                        needsVerification = state.needsVerification && !selectionRemoved,
                        notice = if (selectionRemoved) "이전에 선택한 메뉴가 목록에 없어 선택을 해제했어요." else state.notice)
                }, { error -> state.copy(loading = false, failure = NutritionPublicationFailure.LOAD,
                    error = nutritionPublicationError(error, "저장된 외식 메뉴를 불러오지 못했어요. 다시 시도해 주세요.")) })
            }
        }
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
        val current = nutritionPublicationReady()
        if (!current.open || current.busy) return
        val food = current.menus.firstOrNull { it.id == foodId } ?: return
        if (foodId == current.selectedFoodId) return
        savedStateHandle[KEY_PUBLICATION_FOOD] = food.id
        mutableNutritionPublicationState.value = current.copy(selectedFoodId = food.id, error = null, notice = null,
            failure = null, lastTarget = null, verifiedTarget = null, needsVerification = false)
        val source = food.sourceReference?.let { reference -> runCatching { JSONObject(reference) }.getOrNull() }
        val restaurantName = food.brand?.takeIf { it.isNotBlank() } ?: sourceValue(source, "restaurant_name")
        updatePriceTraceQuery(restaurantName.ifBlank { food.name })
        // Discard the previous food's restaurant even when its search term is unchanged.
        ++priceTraceRequestVersion
        mutablePriceTraceState.value = priceTraceReady().copy(detail = null, restaurants = emptyList(),
            loading = false, selectedRestaurantId = null, searchedQuery = null, error = null)
        searchPriceTraceRestaurants()
    }

    /** Syncs the Nutrition catalog without creating or editing intake records. */
    fun syncNutritionPublicationCatalog() {
        val current = nutritionPublicationReady()
        if (!current.open || current.busy) return
        val request = ++nutritionPublicationRequestVersion
        val requestedOwner = ownerId
        val requestedNutritionOwner = current.nutritionOwnerId
        mutableNutritionPublicationState.value = current.copy(syncing = true, error = null, notice = null, failure = null)
        executor.execute {
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == requestedNutritionOwner) { "영양정보 계정이 변경되었습니다." }
                val synced = nutritionIntegration.syncCurrentCatalog(requestedNutritionOwner)
                synced to nutritionCatalog.savedDiningOutMenus().filter { it.isDiningOutMenu() }
            }
            applyPublicationResult(request, requestedOwner, requestedNutritionOwner) { state ->
                result.fold({ (synced, menus) ->
                    val selected = state.selectedFoodId?.takeIf { id -> menus.any { it.id == id } }
                    savedStateHandle[KEY_PUBLICATION_FOOD] = selected
                    state.copy(menus = menus, selectedFoodId = selected, syncing = false,
                        notice = "동기화 완료 · 가져온 항목 ${synced.pulledRows}개 · 보낸 항목 ${synced.pushedRows}개",
                        lastTarget = state.lastTarget?.takeIf { it.foodId == selected },
                        verifiedTarget = state.verifiedTarget?.takeIf { it.foodId == selected })
                }, { error -> state.copy(syncing = false, failure = NutritionPublicationFailure.SYNC,
                    error = nutritionPublicationError(error, "동기화를 완료하지 못했어요. 저장된 메뉴를 확인하고 다시 시도해 주세요.")) })
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
            val result = runCatching {
                check(nutritionCatalog.currentOwnerId() == target.nutritionOwnerId) { "영양정보 계정이 변경되었습니다." }
                val published = nutritionIntegration.publishDiningOutForExistingMenu(target.foodId, identity, target.nutritionOwnerId)
                if (!published.state.isPublic || published.state.catalogProductId != target.catalogProductId) {
                    throw java.io.IOException("공개 연결 결과를 확인하지 못했습니다.")
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
        update { it.copy(editing = true, diningOut = true, draft = savedDraft(), query = "", searchResults = emptyList(), selectedFood = null, quantity = "", error = null, notice = null) }
    }

    fun search(value: String) {
        if (ready().saving || ready().draftLoading) return
        savedStateHandle[KEY_QUERY] = value
        val request = ++requestVersion
        update { it.copy(query = value, error = null) }
        val diningOut = ready().diningOut
        val requestedOwner = ownerId
        val requestedDate = date
        executor.execute {
            try {
                val results = if (diningOut) {
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
                main.post {
                    if (request == requestVersion && ownerId == requestedOwner && date == requestedDate) {
                        update { it.copy(query = value, searchResults = results) }
                    }
                }
            } catch (error: Exception) {
                main.post {
                    if (request == requestVersion && ownerId == requestedOwner && date == requestedDate) {
                        update { it.copy(error = error.message ?: "식품을 검색하지 못했습니다.") }
                    }
                }
            }
        }
    }

    fun selectFood(food: NutritionFood) {
        if (ready().saving || ready().draftLoading) return
        ++requestVersion
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        savedStateHandle[KEY_QUANTITY] = number(food.basisAmount)
        update {
            it.copy(
                selectedFood = food,
                quantity = number(food.basisAmount),
                foodPortions = addFoodPortion(it.foodPortions, food),
                query = "",
                searchResults = emptyList(),
                error = null
            )
        }
    }

    fun updateQuantity(value: String) {
        if (ready().saving) return
        savedStateHandle[KEY_QUANTITY] = value
        update { it.copy(quantity = value, foodPortions = it.foodPortions.map { portion ->
            if (portion.food.id == it.selectedFood?.id) portion.copy(quantity = value) else portion
        }, error = null, notice = null) }
    }

    fun updateFoodQuantity(foodId: String, value: String) = update {
        it.copy(foodPortions = it.foodPortions.map { portion ->
            if (portion.food.id == foodId) portion.copy(quantity = value) else portion
        }, error = null, notice = null)
    }

    fun removeFood(foodId: String) = update {
        it.copy(foodPortions = it.foodPortions.filterNot { portion -> portion.food.id == foodId }, error = null)
    }

    fun useComposition(templateId: String) {
        if (ready().saving || ready().draftLoading) return
        val repository = nutritionTemplates ?: return
        val request = ++requestVersion
        val requestedOwner = ownerId
        val requestedDate = date
        val catalogOwner = nutritionCatalog.currentOwnerId()
        nutritionEditor.close()
        if (!savedStateHandle.contains(KEY_TIME)) savedStateHandle[KEY_TIME] = ready().draft.time
        update { it.copy(editing = true, diningOut = false, draftLoading = true, error = null) }
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

    fun saveFood(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving || state.draftLoading || state.recordActionSaving) return
        if (state.foodPortions.isEmpty() || state.foodPortions.any { it.profile == null || it.food.id.isNullOrBlank() }) {
            mutableState.value = state.copy(error = "식품을 추가하고 각 섭취량을 0보다 큰 숫자로 입력하세요.")
            onSaved(false)
            return
        }
        val portions = state.foodPortions.map { FoodPortionInput(it.food.id, it.amount!!) }
        val requestedDate = date
        val request = ++requestVersion
        mutableState.value = state.copy(saving = true, error = null)
        executor.execute {
            val result = runCatching { mealRepository.saveFoodComposition(scope, requestedDate, state.draft.time, portions) }
            main.post {
                if (request != requestVersion || ownerId != scope.ownerId || date != requestedDate) return@post
                result.fold({
                    resetSavedDraft()
                    selectedFood = null
                    mutableState.value = ready().copy(editing = false, diningOut = false, query = "", searchResults = emptyList(),
                        selectedFood = null, quantity = "", foodPortions = emptyList(), draft = DiningOutDraft(), saving = false,
                        notice = "끼니 기록을 저장했습니다.")
                    loadNutritionAnalysis(scope, requestedDate)
                    onSaved(true)
                }, { error ->
                    mutableState.value = ready().copy(saving = false, error = error.message ?: "끼니를 저장하지 못했습니다.")
                    onSaved(false)
                })
            }
        }
    }
    fun useDiningOutFood(food: NutritionFood) {
        if (ready().saving) return
        ++requestVersion
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
        savedStateHandle[KEY_DINING_PORTION] = "1"
        update { it.copy(editing = true, diningOut = true, diningPortion = "1", query = "", searchResults = emptyList(), draft = savedDraft(), selectedFood = food, error = null, notice = null) }
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
        val requestedDate = date
        val requestedCatalogOwner = nutritionCatalog.currentOwnerId()
        val request = ++requestVersion
        mutableState.value = state.copy(saving = true, error = null)
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
                ) ?: throw IllegalStateException("외식 메뉴를 저장하지 못했습니다.")
            }
            main.post {
                if (request != requestVersion || ownerId != scope.ownerId || date != requestedDate || nutritionCatalog.currentOwnerId() != requestedCatalogOwner) return@post
                result.fold({ saved ->
                    selectedFood = saved
                    savedStateHandle[KEY_FOOD_ID] = saved.id
                    mutableState.value = ready().copy(saving = false, selectedFood = saved, query = "", searchResults = emptyList(),
                        error = null, notice = "내 외식 목록에 메뉴를 저장했습니다.")
                    onSaved(true)
                }, { error ->
                    mutableState.value = ready().copy(saving = false, error = error.message ?: "외식 메뉴를 저장하지 못했습니다.")
                    onSaved(false)
                })
            }
        }
    }
    fun save(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving) return
        val draft = state.draft
        val parsed = runCatching { parseDining(draft) }.getOrElse { error ->
            mutableState.value = state.copy(error = error.message ?: "칼로리와 필수 영양정보를 확인하세요.")
            onSaved(false)
            return
        }
        val portion = state.diningPortion.trim().toDoubleOrNull()
        if (portion == null || !portion.isFinite() || portion <= 0.0) {
            mutableState.value = state.copy(error = "먹은 양은 0보다 큰 숫자로 입력하세요.")
            onSaved(false)
            return
        }
        val input = DiningOutMealInput(draft.store, draft.branch, draft.menu, portion,
            parsed.calories, parsed.protein, parsed.carbs, parsed.fat, parsed.sodium, parsed.sugars, parsed.saturatedFat,
            draft.restaurantId, draft.restaurantLocationId, draft.restaurantMenuId, draft.catalogProductId)
        val request = ++requestVersion
        val requestedDate = date
        mutableState.value = state.copy(saving = true, error = null)
        executor.execute {
            val result = runCatching { mealRepository.saveDiningOutPortion(scope, requestedDate, draft.time, input) }
            main.post {
                if (request != requestVersion || ownerId != scope.ownerId || date != requestedDate) return@post
                result.fold({
                    resetSavedDraft()
                    selectedFood = null
                    mutableState.value = ready().copy(selectedFood = null, editing = false, diningOut = false,
                        query = "", searchResults = emptyList(), draft = DiningOutDraft(), diningPortion = "1", foodPortions = emptyList(),
                        saving = false, notice = "외식 기록을 저장했습니다.")
                    loadNutritionAnalysis(scope, requestedDate)
                    onSaved(true)
                }, { error ->
                    mutableState.value = ready().copy(saving = false, error = error.message ?: "외식 기록을 저장하지 못했습니다.")
                    onSaved(false)
                })
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
        if (ready().saving) return
        if (key in listOf(KEY_STORE, KEY_BRANCH, KEY_MENU) && value != savedStateHandle.get<String>(key).orEmpty()) {
            listOf(KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID, KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID,
                KEY_SOURCE_NAMESPACE, KEY_SOURCE_LOCATION_CODE, KEY_FOOD_ID).forEach { savedStateHandle.remove<String>(it) }
            selectedFood = null
        }
        savedStateHandle[key] = value
        update { it.copy(draft = savedDraft(), selectedFood = selectedFood, error = null, notice = null) }
    }

    private fun update(block: (MealUiState.Ready) -> MealUiState.Ready) {
        if (ready().saving) return
        mutableState.value = block(ready())
        val next = ready()
        savedStateHandle[KEY_EDITING] = next.editing
        savedStateHandle[KEY_DINING_OUT] = next.diningOut
        if (!next.draftLoading) {
            savedStateHandle[KEY_PORTION_IDS] = ArrayList(next.foodPortions.map { it.food.id })
            savedStateHandle[KEY_PORTION_AMOUNTS] = ArrayList(next.foodPortions.map { it.quantity })
        }
    }

    private fun loadNutritionAnalysis(scope: AccountScope, requestedDate: String) {
        val api = nutritionAnalysisApi ?: return
        main.post {
            if (ownerId != scope.ownerId || date != requestedDate) return@post
            val request = ++nutritionAnalysisRequestVersion
            mutableState.value = ready().copy(nutritionAnalysis = null, nutritionAnalysisLoading = true, nutritionAnalysisError = null)
            executor.execute {
                val result = runCatching { api.analyzeDay(scope, requestedDate) }
                main.post {
                    if (request != nutritionAnalysisRequestVersion || ownerId != scope.ownerId || date != requestedDate) return@post
                    mutableState.value = result.fold(
                        { report -> ready().copy(nutritionAnalysis = report, nutritionAnalysisLoading = false, nutritionAnalysisError = null) },
                        { error -> ready().copy(nutritionAnalysisLoading = false, nutritionAnalysisError = error.message ?: "영양 분석을 불러오지 못했습니다.") }
                    )
                }
            }
        }
    }

    private fun ready(): MealUiState.Ready = (mutableState.value as? MealUiState.Ready) ?: MealUiState.Ready(
        ownerId = ownerId,
        date = date,
        editing = false,
        diningOut = false,
            query = savedStateHandle[KEY_QUERY] ?: "",
            searchResults = emptyList(),
            draft = savedDraft(),
            selectedFood = selectedFood,
        quantity = savedStateHandle[KEY_QUANTITY] ?: "",
        priceTraceQuery = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: ""
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
        listOf(KEY_STORE, KEY_BRANCH, KEY_MENU, KEY_CALORIES, KEY_CARBS, KEY_PROTEIN,
            KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT, KEY_TIME, KEY_QUERY,
            KEY_FOOD_ID, KEY_QUANTITY, KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID,
            KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID, KEY_SOURCE_NAMESPACE,
            KEY_SOURCE_LOCATION_CODE, KEY_PRICE_TRACE_QUERY)
            .forEach { savedStateHandle.remove<String>(it) }
        listOf(KEY_EDITING, KEY_DINING_OUT, KEY_DINING_PORTION, KEY_PORTION_IDS, KEY_PORTION_AMOUNTS)
            .forEach { savedStateHandle.remove<Any>(it) }
    }

    override fun onCleared() { executor.shutdownNow() }

    private fun parseDining(draft: DiningOutDraft): ParsedDining = ParsedDining(
        requiredNumber(draft.calories, "칼로리").also { require(it <= Int.MAX_VALUE && it % 1.0 == 0.0) { "칼로리는 0 이상의 정수로 입력하세요." } }.toInt(),
        requiredNumber(draft.protein, "단백질"),
        requiredNumber(draft.carbs, "탄수화물"),
        requiredNumber(draft.fat, "지방"),
        optionalNumber(draft.sodium, "나트륨"),
        optionalNumber(draft.sugars, "당류"),
        optionalNumber(draft.saturatedFat, "포화지방")
    )
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
        const val KEY_OWNER = "meal.owner"
        const val KEY_DATE = "meal.date"
        const val KEY_EDITING = "meal.editing"
        const val KEY_DINING_OUT = "meal.dining_out"
        const val KEY_DINING_PORTION = "meal.dining_portion"
        const val KEY_PORTION_IDS = "meal.portion_ids"
        const val KEY_PORTION_AMOUNTS = "meal.portion_amounts"
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
        const val KEY_PUBLICATION_OPEN = "meal.publication.open"
        const val KEY_PUBLICATION_OWNER = "meal.publication.owner"
        const val KEY_PUBLICATION_NUTRITION_OWNER = "meal.publication.nutrition_owner"
        const val KEY_PUBLICATION_FOOD = "meal.publication.food"
        fun number(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
    }
}
