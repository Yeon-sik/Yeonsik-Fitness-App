package com.yeonsik.fitnessapp.integration.healthtrace

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.model.SupplementPlanDraft
import com.yeonsik.fitnessapp.supplement.SupplementPlan

/** Runtime write boundary. Legacy storage remains available to backup and migration code. */
class LegacyHealthSupplementReader(private val legacy: SupplementRepositoryApi) : SupplementRepositoryApi by legacy {
    private fun denied(): Nothing = error("보충제 기록은 HealthTrace에서 관리합니다. Fitness의 이전 기록은 읽기 전용입니다.")
    override fun savePlanDraft(existing: SupplementPlan?, draft: SupplementPlanDraft) = denied()
    override fun archivePlan(itemId: String) = denied()
    override fun recordNextDose(scheduleId: String, date: String, status: String) = denied()
    override fun undoLatestRecord(scheduleId: String, date: String) = denied()
    override fun updateRecordStatus(recordId: String, status: String) = denied()
    override fun deleteRecord(recordId: String) = denied()
    override fun saveEffectCheckin(itemId: String, date: String, effectScore: Int, adverseEffects: String, note: String) = denied()
}

/** Fitness may update water intake and training readiness, while old health facts stay unchanged. */
class FitnessActivityCheckInWriter(private val legacy: RecoveryRepositoryApi) : RecoveryRepositoryApi by legacy {
    override fun saveCheckIn(scope: AccountScope, checkIn: AthleteDailyCheckIn) {
        val previous = legacy.checkIn(scope, checkIn.date)
        require(checkIn.sleepHours == previous.sleepHours && checkIn.energyScore == previous.energyScore &&
            checkIn.hungerScore == previous.hungerScore && checkIn.digestionScore == previous.digestionScore &&
            checkIn.note == previous.note) {
            "수면·건강 상태·건강 메모는 HealthTrace에서 기록하세요. 기존 건강 정보는 유지됩니다."
        }
        legacy.saveCheckIn(scope, checkIn)
    }
}
