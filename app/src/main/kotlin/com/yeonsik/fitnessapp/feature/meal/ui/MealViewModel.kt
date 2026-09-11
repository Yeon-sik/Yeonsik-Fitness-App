package com.yeonsik.fitnessapp.feature.meal.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.NutritionFood
import com.yeonsik.fitnessapp.data.DiningOutIdentity
import org.json.JSONObject
import com.yeonsik.fitnessapp.feature.meal.api.MealRecordRepositoryApi
import com.yeonsik.fitnessapp.feature.nutrition.api.NutritionCatalogRepositoryApi
import com.yeonsik.fitnessapp.integration.nutrition.NutritionIntegrationService
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
        val priceTraceQuery: String = ""
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

/** Owns meal editor/search state; Compose only renders state and emits actions. */
class MealViewModel @JvmOverloads constructor(
    private val savedStateHandle: SavedStateHandle,
    private val mealRepository: MealRecordRepositoryApi,
    private val nutritionCatalog: NutritionCatalogRepositoryApi,
    private val nutritionIntegration: NutritionIntegrationService,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
) : ViewModel() {
    private val mutableState = MutableLiveData<MealUiState>(MealUiState.Idle)
    val uiState: LiveData<MealUiState> = mutableState
    private val mutablePriceTraceState = MutableLiveData<PriceTraceUiState>(PriceTraceUiState.Idle)
    val priceTraceState: LiveData<PriceTraceUiState> = mutablePriceTraceState
    private var ownerId = ""
    private var date = ""
    private var requestVersion = 0L
    private var priceTraceRequestVersion = 0L
    private var selectedFood: NutritionFood? = null

    fun enter(scope: AccountScope, date: String) {
        ownerId = scope.ownerId
        this.date = date
        mealRepository.setUserId(scope.ownerId)
        val request = ++requestVersion
        mutableState.value = ready().copy(ownerId = scope.ownerId, date = date)
        mutablePriceTraceState.value = PriceTraceUiState.Ready(
            ownerId = scope.ownerId,
            query = savedStateHandle[KEY_PRICE_TRACE_QUERY] ?: ""
        )
        val selectedId = savedStateHandle.get<String>(KEY_FOOD_ID).orEmpty()
        if (selectedFood == null && selectedId.isNotBlank()) {
            executor.execute {
                val restored = nutritionCatalog.findFoodById(selectedId)
                if (request == requestVersion && ownerId == scope.ownerId && this.date == date) {
                    selectedFood = restored
                    mutableState.postValue(ready().copy(selectedFood = restored))
                }
            }
        }
    }

    fun startDraft() = update { it.copy(editing = true, notice = null, error = null) }
    fun closeDraft() = update { it.copy(editing = false, diningOut = false, error = null) }
    fun chooseFood() {
        ++requestVersion
        savedStateHandle[KEY_QUERY] = ""
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = false, query = "", searchResults = emptyList(), selectedFood = null, error = null) }
    }
    fun chooseDiningOut() {
        ++requestVersion
        savedStateHandle.remove<String>(KEY_FOOD_ID)
        savedStateHandle.remove<String>(KEY_QUANTITY)
        selectedFood = null
        update { it.copy(editing = true, diningOut = true, query = "", searchResults = emptyList(), selectedFood = null, error = null) }
        search("")
    }
    fun resetDraft() {
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
        mutableState.value = ready().copy(editing = true, diningOut = true, draft = DiningOutDraft())
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

    fun applyPriceTraceSelection(
        restaurantId: String,
        restaurantName: String,
        locationId: String,
        branchName: String,
        menuId: String,
        menuName: String,
        catalogProductId: String
    ) {
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
        savedStateHandle[KEY_QUERY] = value
        val request = ++requestVersion
        update { it.copy(query = value, error = null) }
        val diningOut = ready().diningOut
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
                if (request == requestVersion) mutableState.postValue(ready().copy(query = value, searchResults = results))
            } catch (error: Exception) {
                if (request == requestVersion) mutableState.postValue(ready().copy(error = error.message ?: "식품을 검색하지 못했습니다."))
            }
        }
    }

    fun selectFood(food: NutritionFood) {
        ++requestVersion
        selectedFood = food
        savedStateHandle[KEY_FOOD_ID] = food.id
        savedStateHandle[KEY_QUANTITY] = number(food.basisAmount)
        update {
            it.copy(
                selectedFood = food,
                quantity = number(food.basisAmount),
                query = "",
                searchResults = emptyList(),
                error = null
            )
        }
    }

    fun updateQuantity(value: String) {
        savedStateHandle[KEY_QUANTITY] = value
        update { it.copy(quantity = value, error = null, notice = null) }
    }

    fun saveFood(scope: AccountScope, onSaved: (Boolean) -> Unit) {
        val state = ready()
        if (scope.ownerId != ownerId || state.saving) return
        val food = state.selectedFood
        val quantity = state.quantity.toDoubleOrNull()
        if (food?.id.isNullOrBlank() || quantity == null || quantity <= 0) {
            mutableState.value = state.copy(error = "식품과 0보다 큰 섭취량을 입력하세요.")
            onSaved(false)
            return
        }
        mutableState.value = state.copy(saving = true, error = null)
        executor.execute {
            try {
                mealRepository.saveFoodMeal(scope, date, state.draft.time, food.id, quantity)
                resetSavedDraft()
                selectedFood = null
                mutableState.postValue(ready().copy(
                    editing = false,
                    diningOut = false,
                    query = "",
                    searchResults = emptyList(),
                    selectedFood = null,
                    quantity = "",
                    saving = false,
                    notice = "식단 기록을 저장했습니다."
                ))
                onSaved(true)
            } catch (error: Exception) {
                mutableState.postValue(ready().copy(saving = false, error = error.message ?: "식단 기록을 저장하지 못했습니다."))
                onSaved(false)
            }
        }
    }
    fun useDiningOutFood(food: NutritionFood) {
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
        mutableState.value = ready().copy(editing = true, diningOut = true, query = "", searchResults = emptyList(), draft = savedDraft(), selectedFood = food, error = null, notice = null)
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
        mutableState.value = state.copy(saving = true, error = null)
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
                ) ?: throw IllegalStateException("Nutrition 재사용 메뉴를 저장하지 못했습니다.")
                selectedFood = saved
                savedStateHandle[KEY_FOOD_ID] = saved.id
                mutableState.postValue(ready().copy(
                    saving = false,
                    selectedFood = saved,
                    query = "",
                    searchResults = emptyList(),
                    error = null,
                    notice = "Nutrition 재사용 메뉴로 저장했습니다."
                ))
                onSaved(true)
            } catch (error: Exception) {
                mutableState.postValue(ready().copy(saving = false, error = error.message ?: "Nutrition 재사용 메뉴를 저장하지 못했습니다."))
                onSaved(false)
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
        mutableState.value = state.copy(saving = true, error = null)
        executor.execute {
            try {
                mealRepository.saveManualDiningOut(
                    scope,
                    date, draft.time, draft.store, draft.branch, draft.menu,
                    parsed.calories, parsed.protein, parsed.carbs, parsed.fat,
                    parsed.sodium, parsed.sugars, parsed.saturatedFat,
                    draft.restaurantId, draft.restaurantLocationId,
                    draft.restaurantMenuId, draft.catalogProductId
                )
                resetSavedDraft()
                mutableState.postValue(ready().copy(
                    selectedFood = null,
                    editing = false,
                    diningOut = false,
                    query = "",
                    searchResults = emptyList(),
                    draft = DiningOutDraft(),
                    saving = false,
                    notice = "외식 기록을 저장했습니다."
                ))
                onSaved(true)
            } catch (error: Exception) {
                mutableState.postValue(ready().copy(saving = false, error = error.message ?: "외식 기록을 저장하지 못했습니다."))
                onSaved(false)
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
        savedStateHandle[key] = value
        update { it.copy(draft = savedDraft(), error = null, notice = null) }
    }

    private fun update(block: (MealUiState.Ready) -> MealUiState.Ready) {
        mutableState.value = block(ready())
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
        listOf(KEY_STORE, KEY_BRANCH, KEY_MENU, KEY_CALORIES, KEY_CARBS, KEY_PROTEIN,
            KEY_FAT, KEY_SODIUM, KEY_SUGARS, KEY_SATURATED_FAT, KEY_TIME, KEY_QUERY,
            KEY_FOOD_ID, KEY_QUANTITY, KEY_RESTAURANT_ID, KEY_RESTAURANT_LOCATION_ID,
            KEY_RESTAURANT_MENU_ID, KEY_CATALOG_PRODUCT_ID, KEY_SOURCE_NAMESPACE,
            KEY_SOURCE_LOCATION_CODE, KEY_PRICE_TRACE_QUERY)
            .forEach { savedStateHandle.remove<String>(it) }
    }

    override fun onCleared() { executor.shutdownNow() }

    private fun parseDining(draft: DiningOutDraft): ParsedDining = ParsedDining(
        requiredNumber(draft.calories, "칼로리").toInt(),
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
