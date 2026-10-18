package com.yeonsik.fitnessapp.feature.development.api

import com.yeonsik.fitness.shared.core.account.AccountScope

/** Display preferences only; null means automatic selection, an empty list means none selected. */
interface DevelopmentTrackingRepositoryApi {
    fun followedExerciseKeys(scope: AccountScope): List<String>?
    fun saveFollowedExerciseKeys(scope: AccountScope, keys: List<String>)
}
