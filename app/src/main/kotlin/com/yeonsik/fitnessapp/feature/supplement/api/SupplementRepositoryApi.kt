package com.yeonsik.fitnessapp.feature.supplement.api

import com.yeonsik.fitnessapp.supplement.SupplementPlan
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress
import java.time.LocalDate

/** Account-scoped supplement read/write port. */
interface SupplementRepositoryApi {
    fun activePlans(date: String): List<SupplementPlan>
    fun loadProgress(date: String): SupplementProgress
    fun loadAdherence(date: LocalDate, days: Int): SupplementAdherence
    fun recordNextDose(scheduleId: String, date: String, status: String)
    fun undoLatestRecord(scheduleId: String, date: String)
}
