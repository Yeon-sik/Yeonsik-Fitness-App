package com.yeonsik.fitnessapp.feature.development.data

import android.content.Context
import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.development.api.DevelopmentTrackingRepositoryApi
import org.json.JSONArray

/** Account-scoped local UI preferences. Workout facts and nutrition goals stay in their owners. */
class DevelopmentTrackingRepository(context: Context) : DevelopmentTrackingRepositoryApi {
    private val preferences = context.applicationContext.getSharedPreferences(
        "development_tracking", Context.MODE_PRIVATE)

    override fun followedExerciseKeys(scope: AccountScope): List<String>? {
        require(scope.ownerId.isNotBlank())
        val raw = preferences.getString("followed.${scope.ownerId}", null) ?: return null
        return runCatching {
            val values = JSONArray(raw)
            (0 until values.length()).map { values.getString(it) }.filter { it.isNotBlank() }.distinct().take(5)
        }.getOrNull()
    }

    override fun saveFollowedExerciseKeys(scope: AccountScope, keys: List<String>) {
        require(scope.ownerId.isNotBlank())
        require(keys.size <= 5 && keys.all { it.isNotBlank() } && keys.distinct().size == keys.size)
        check(preferences.edit().putString("followed.${scope.ownerId}", JSONArray(keys).toString()).commit()) {
            "관심 운동을 저장하지 못했습니다. 다시 선택해 주세요."
        }
    }
}
