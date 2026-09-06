package com.yeonsik.fitnessapp.feature.cardio.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot

interface CardioRepositoryApi {
    fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot?
}
