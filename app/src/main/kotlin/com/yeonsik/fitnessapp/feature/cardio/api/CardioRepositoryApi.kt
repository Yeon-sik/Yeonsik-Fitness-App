package com.yeonsik.fitnessapp.feature.cardio.api

import com.yeonsik.fitnessapp.cardio.CardioActivityType
import com.yeonsik.fitnessapp.cardio.CardioRouteProjection
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot

interface CardioRepositoryApi {
    fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot?

    fun isCardioSession(scope: AccountScope, recordId: String): Boolean = false

    fun routeProjection(scope: AccountScope, recordId: String): CardioRouteProjection =
        CardioRouteProjection.empty()

    /** Creates only the GPS-owned session row for an already-created workout record. */
    fun startSession(
        scope: AccountScope,
        recordId: String,
        activityType: CardioActivityType
    ): Boolean = false

    fun pause(scope: AccountScope, recordId: String): Boolean = false

    fun resume(scope: AccountScope, recordId: String): Boolean = false

    /** Completes only the GPS-owned session row; Workout owns the shared record summary. */
    fun completeSession(
        scope: AccountScope,
        recordId: String,
        activeDurationMillis: Long
    ): Boolean = false

    /** Deletes route points and the GPS-owned session row only. */
    fun deleteLocalData(scope: AccountScope, recordId: String): Boolean = false
}
