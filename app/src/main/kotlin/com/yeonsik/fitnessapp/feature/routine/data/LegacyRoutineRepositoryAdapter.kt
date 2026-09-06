package com.yeonsik.fitnessapp.feature.routine.data

import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.feature.routine.api.RoutineRepositoryApi
import com.yeonsik.fitnessapp.routine.RoutineRepository

/** Isolates the legacy default-routine write from screen rendering. */
class LegacyRoutineRepositoryAdapter(
    private val legacy: RoutineRepository,
    private val fitnessRepository: FitnessRepository
) : RoutineRepositoryApi {
    override fun ensureActiveRoutine(scope: AccountScope): String {
        check(scope.ownerId == fitnessRepository.currentUserId()) {
            "The account changed while the routine operation was pending."
        }
        return legacy.activeRoutineId()
    }
}
