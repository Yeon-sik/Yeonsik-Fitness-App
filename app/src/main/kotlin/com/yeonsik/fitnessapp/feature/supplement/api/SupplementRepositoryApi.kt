package com.yeonsik.fitnessapp.feature.supplement.api

import com.yeonsik.fitnessapp.supplement.SupplementPlan
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementAdherence
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementEffectCheckin
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementHistoryEntry
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanSaveResult
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementProgress
import java.time.LocalDate

/** Account-scoped supplement read/write port. */
interface SupplementRepositoryApi {
    fun activePlans(date: String): List<SupplementPlan>
    fun loadProgress(date: String): SupplementProgress
    fun loadAdherence(date: LocalDate, days: Int): SupplementAdherence
    fun loadHistory(endDate: LocalDate, days: Int): List<SupplementHistoryEntry>
    fun savePlanDraft(existing: SupplementPlan?, draft: SupplementPlanDraft): SupplementPlanSaveResult
    fun archivePlan(itemId: String)
    fun recordNextDose(scheduleId: String, date: String, status: String)
    fun undoLatestRecord(scheduleId: String, date: String)
    fun updateRecordStatus(recordId: String, status: String)
    fun deleteRecord(recordId: String)
    fun loadLatestEffectCheckin(itemId: String): SupplementEffectCheckin?
    fun saveEffectCheckin(
        itemId: String,
        date: String,
        effectScore: Int,
        adverseEffects: String,
        note: String
    )
}
