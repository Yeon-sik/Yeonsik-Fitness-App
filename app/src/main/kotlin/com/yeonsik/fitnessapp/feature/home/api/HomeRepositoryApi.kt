package com.yeonsik.fitnessapp.feature.home.api

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.home.model.HomeSnapshot

interface HomeRepositoryApi {
    fun load(scope: AccountScope, today: String): HomeSnapshot
}
