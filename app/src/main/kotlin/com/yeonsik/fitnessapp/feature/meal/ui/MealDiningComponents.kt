package com.yeonsik.fitnessapp.feature.meal.ui

import com.yeonsik.fitnessapp.data.*
import com.yeonsik.fitnessapp.feature.nutrition.model.NutritionConsistencyPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Incomplete text input stays separate from the validated component snapshot. */
data class MealDiningComponentDraft(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val groupType: String = CompositionGroupType.ADD_ON.value(),
    val provisionType: String = DiningOutProvisionType.PAID.value(),
    val quantity: String = "1",
    val consumedPercent: String = "100",
    val nutrients: Map<String, String> = emptyMap(),
    val catalogFoodId: String? = null,
    val sourceReference: String? = null,
    val groupKey: String = DiningOutOption.DEFAULT_GROUP_KEY,
    val groupLabel: String = DiningOutOption.DEFAULT_GROUP_LABEL,
    val role: String = DiningOutOption.DEFAULT_ROLE,
    val memberId: String? = null
) {
    fun profile(): NutritionProfile = NutritionProfile.builder().apply {
        nutrients.forEach { (key, raw) -> value(key, raw.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }) }
    }.build()

    fun selection(): DiningOutComponent = DiningOutComponent(name, profile(), catalogFoodId, sourceReference,
        groupKey, groupType, groupLabel, role, memberId, provisionType,
        requireNotNull(mealQuantityValue(quantity)), requireNotNull(mealConsumedFraction(consumedPercent)))

    fun error(): String? = when {
        name.isBlank() -> "구성품 이름을 입력하세요."
        mealQuantityValue(quantity) == null -> "구성품 수량은 0보다 큰 숫자로 입력하세요."
        mealConsumedPercentError(consumedPercent) != null -> mealConsumedPercentError(consumedPercent)
        nutrients.values.any { it.isNotBlank() && (it.toDoubleOrNull()?.let { n -> n.isFinite() && n >= 0 } != true) } ->
            "구성품 영양정보는 0 이상의 숫자로 입력하세요."
        else -> runCatching {
            NutritionConsistencyPolicy.validate(profile())
            require(selection().consumedProfile().asMap().values.all { it == null || it.isFinite() })
        }.exceptionOrNull()?.message
    }

    companion object {
        fun from(component: DiningOutComponent) = MealDiningComponentDraft(name = component.name,
            groupType = component.groupType, provisionType = component.provisionType,
            quantity = component.quantity.toString(), consumedPercent = (component.consumedFraction * 100).toString(),
            nutrients = component.profile.asMap().mapNotNull { (key, value) -> value?.let { key to it.toString() } }.toMap(),
            catalogFoodId = component.catalogFoodId, sourceReference = component.sourceReference,
            groupKey = component.groupKey, groupLabel = component.groupLabel, role = component.role, memberId = component.memberId)
    }
}

internal fun encodeDiningComponents(items: List<MealDiningComponentDraft>): String = JSONArray().apply {
    items.forEach { item -> put(JSONObject().apply {
        put("id", item.id); put("name", item.name); put("group", item.groupType); put("provision", item.provisionType)
        put("quantity", item.quantity); put("percent", item.consumedPercent); put("nutrients", JSONObject(item.nutrients))
        put("food_id", item.catalogFoodId ?: JSONObject.NULL); put("source", item.sourceReference ?: JSONObject.NULL)
        put("group_key", item.groupKey); put("group_label", item.groupLabel); put("role", item.role)
        put("member_id", item.memberId ?: JSONObject.NULL)
    }) }
}.toString()

internal fun decodeDiningComponents(raw: String): List<MealDiningComponentDraft> {
    val array = JSONArray(raw)
    return (0 until array.length()).map { index ->
        val json = array.getJSONObject(index)
        fun optional(key: String) = if (json.isNull(key)) null else json.getString(key)
        val nutrients = json.getJSONObject("nutrients")
        MealDiningComponentDraft(json.getString("id"), json.getString("name"), json.getString("group"),
            json.getString("provision"), json.getString("quantity"), json.getString("percent"),
            nutrients.keys().asSequence().associateWith { nutrients.getString(it) }, optional("food_id"), optional("source"),
            json.getString("group_key"), json.getString("group_label"), json.getString("role"), optional("member_id"))
    }
}

internal fun diningMenuNutritionTotals(menu: MealDiningDraftItem): NutritionTotals = NutritionTotals.builder().apply {
    add(diningMenuNutritionProfile(menu))
    menu.components.forEach { item -> add(runCatching { item.selection().consumedProfile() }.getOrDefault(NutritionProfile.empty())) }
}.build()

/** Keep source-location identity alongside the authoritative restaurant/location/menu ids. */
internal fun diningMenuSourceReference(menu: MealDiningDraftItem): String =
    runCatching { JSONObject(menu.sourceReference ?: "{}") }.getOrDefault(JSONObject()).apply {
        if (menu.draft.sourceNamespace.isNotBlank()) put("source_namespace", menu.draft.sourceNamespace)
        if (menu.draft.sourceLocationCode.isNotBlank()) put("source_location_code", menu.draft.sourceLocationCode)
    }.toString()
