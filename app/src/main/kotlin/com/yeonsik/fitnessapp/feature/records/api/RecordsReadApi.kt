package com.yeonsik.fitnessapp.feature.records.api

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.records.model.RecordsSnapshot

/** Read-only Records composition boundary. It never exposes a Home snapshot. */
interface RecordsReadApi {
    fun load(
        scope: AccountScope,
        displayedMonth: String,
        selectedDate: String,
        today: String
    ): RecordsSnapshot
}
