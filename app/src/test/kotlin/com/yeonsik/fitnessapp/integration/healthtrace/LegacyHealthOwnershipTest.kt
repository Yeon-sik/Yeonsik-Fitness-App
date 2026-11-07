package com.yeonsik.fitnessapp.integration.healthtrace

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.data.AthleteDailyCheckIn
import com.yeonsik.fitnessapp.feature.recovery.api.RecoveryRepositoryApi
import com.yeonsik.fitnessapp.feature.supplement.api.SupplementRepositoryApi
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class LegacyHealthOwnershipTest {
    @Test fun runtimeSupplementPortRejectsEveryMutationAndKeepsReads() {
        var calls = 0
        val legacy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SupplementRepositoryApi::class.java)) { _, method, _ ->
            calls++
            if (method.name == "activePlans") emptyList<Any>() else error("Unexpected legacy call")
        } as SupplementRepositoryApi
        val reader = LegacyHealthSupplementReader(legacy)
        assertTrue(reader.activePlans("2026-11-06").isEmpty())
        assertThrows(IllegalStateException::class.java) { reader.archivePlan("id") }
        assertThrows(IllegalStateException::class.java) { reader.recordNextDose("id", "2026-11-06", "taken") }
        assertThrows(IllegalStateException::class.java) { reader.undoLatestRecord("id", "2026-11-06") }
        assertThrows(IllegalStateException::class.java) { reader.updateRecordStatus("id", "taken") }
        assertThrows(IllegalStateException::class.java) { reader.deleteRecord("id") }
        assertThrows(IllegalStateException::class.java) { reader.saveEffectCheckin("id", "2026-11-06", 3, "", "") }
        // Reflection verifies savePlanDraft also reaches the rejecting boundary without needing a valid plan.
        val method = SupplementRepositoryApi::class.java.methods.single { it.name == "savePlanDraft" }
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) { method.invoke(reader, null, null) }
        assertEquals(1, calls)
    }

    @Test fun activityCheckInCanChangeOnlyWaterAndReadiness() {
        val old = AthleteDailyCheckIn("id", "2026-11-06", 300, 7.5, 3, 2, 4, 2, "기존 건강 메모")
        var saved: AthleteDailyCheckIn? = null
        val legacy = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(RecoveryRepositoryApi::class.java)) { _, method, args ->
            when (method.name) {
                "checkIn" -> old
                "saveCheckIn" -> { saved = args!![1] as AthleteDailyCheckIn; Unit }
                else -> error("Unexpected call")
            }
        } as RecoveryRepositoryApi
        val writer = FitnessActivityCheckInWriter(legacy)
        val scope = AccountScope("owner")
        val permitted = AthleteDailyCheckIn(old.id, old.date, 800, old.sleepHours, old.energyScore, old.hungerScore, old.digestionScore, 4, old.note)
        writer.saveCheckIn(scope, permitted)
        assertSame(permitted, saved)
        saved = null
        val healthChange = AthleteDailyCheckIn(old.id, old.date, 800, 8.0, old.energyScore, old.hungerScore, old.digestionScore, 4, old.note)
        assertThrows(IllegalArgumentException::class.java) { writer.saveCheckIn(scope, healthChange) }
        val erased = AthleteDailyCheckIn.empty(old.date)
        assertThrows(IllegalArgumentException::class.java) { writer.saveCheckIn(scope, erased) }
        assertNull(saved)
    }
}
