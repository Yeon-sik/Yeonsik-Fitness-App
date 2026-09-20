package com.yeonsik.fitness.shared.feature.cardio.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType
import com.yeonsik.fitness.shared.feature.cardio.model.CardioRouteProjection
import com.yeonsik.fitness.shared.feature.cardio.model.CardioSessionSnapshot

interface CardioRepositoryApi {
    fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot?
    fun isCardioSession(scope: AccountScope, recordId: String): Boolean = false
    fun routeProjection(scope: AccountScope, recordId: String): CardioRouteProjection = CardioRouteProjection.empty()
    fun startSession(scope: AccountScope, recordId: String, activityType: CardioActivityType): Boolean = false
    fun pause(scope: AccountScope, recordId: String): Boolean = false
    fun resume(scope: AccountScope, recordId: String): Boolean = false
    fun completeSession(scope: AccountScope, recordId: String, activeDurationMillis: Long): Boolean = false
    fun deleteLocalData(scope: AccountScope, recordId: String): Boolean = false
}
