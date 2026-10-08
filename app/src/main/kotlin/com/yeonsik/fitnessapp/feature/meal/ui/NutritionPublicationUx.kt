package com.yeonsik.fitnessapp.feature.meal.ui

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

enum class NutritionPublicationFailure { LOAD, SYNC, SELECTION, PUBLISH, VERIFY }

/** The exact menu approved by the user, including the independent Nutrition owner. */
data class NutritionPublicationTarget(
    val foodId: String,
    val nutritionOwnerId: String,
    val restaurantId: String,
    val locationId: String,
    val menuId: String,
    val catalogProductId: String,
    val label: String
) {
    fun matches(foodId: String?, locationId: String?, menuId: String?, catalogProductId: String?): Boolean =
        this.foodId == foodId && this.locationId == locationId && this.menuId == menuId &&
            this.catalogProductId == catalogProductId
}

internal fun nutritionPublicationError(error: Throwable, fallback: String, publishing: Boolean = false): String {
    val causes = generateSequence(error) { it.cause }.take(6).toList()
    val message = causes.joinToString(" ") { it.message.orEmpty() }.lowercase(Locale.ROOT)
    return when {
        "401" in message || "로그인" in message || "unauthorized" in message ->
            "서비스 계정 로그인이 필요해요. 설정에서 로그인 상태를 확인해 주세요."
        "403" in message || "권한" in message || "permission denied" in message ->
            "이 메뉴를 처리할 권한을 확인하지 못했어요. 로그인 계정과 메뉴 소유자를 확인해 주세요."
        "계정이 변경" in message -> "영양정보 계정이 바뀌었어요. 메뉴 목록을 다시 불러와 주세요."
        "설정" in message || "not configured" in message ->
            "서버 연결 설정이 필요해요. 설정에서 영양정보·식당 서비스 연결을 확인해 주세요."
        "429" in message -> "요청이 많아 잠시 기다려야 해요. 잠시 후 다시 시도해 주세요."
        "404" in message -> "요청한 메뉴나 연결 기능을 찾지 못했어요. 목록을 새로고침해 주세요."
        Regex("\\b5\\d{2}\\b").containsMatchIn(message) ->
            "서버가 일시적으로 응답하지 않아요. 잠시 후 다시 시도해 주세요."
        causes.any { it is SocketTimeoutException } ->
            if (publishing) "서버 응답이 늦어 공개 결과를 확인하지 못했어요. 공개 상태를 먼저 확인해 주세요."
            else "서버 응답이 늦어지고 있어요. 잠시 후 다시 시도해 주세요."
        causes.any { it is UnknownHostException || it is ConnectException } ->
            "서버에 연결하지 못했어요. 네트워크 연결을 확인해 주세요."
        publishing && causes.any { it is IOException } ->
            "공개 요청 결과를 확인하지 못했어요. 공개 상태를 먼저 확인해 주세요."
        "identity" in message || "contract" in message || "응답" in message ->
            "서버에서 받은 메뉴 정보를 확인하지 못했어요. 목록을 새로고침하고 다시 시도해 주세요."
        error is IllegalArgumentException && error.message.orEmpty().any { it in '\uac00'..'\ud7a3' } ->
            error.message.orEmpty().replace("PriceTrace", "식당 서비스").replace("Nutrition", "영양정보")
        else -> fallback
    }
}
