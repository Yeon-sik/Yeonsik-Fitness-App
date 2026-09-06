package com.yeonsik.fitnessapp.feature.cardio.data

import com.yeonsik.fitnessapp.cardio.CardioRepository
import com.yeonsik.fitnessapp.core.account.AccountScope
import com.yeonsik.fitnessapp.data.FitnessRepository
import com.yeonsik.fitnessapp.feature.cardio.api.CardioRepositoryApi
import com.yeonsik.fitnessapp.feature.cardio.model.CardioSessionSnapshot

/** Keeps SQLite and legacy cardio types behind the Stage-2 feature API. */
class LegacyCardioRepositoryAdapter(
    private val legacy: CardioRepository,
    private val fitnessRepository: FitnessRepository
) : CardioRepositoryApi {
    override fun loadSession(scope: AccountScope, recordId: String): CardioSessionSnapshot? {
        check(scope.ownerId == fitnessRepository.currentUserId()) {
            "The account changed while the cardio operation was pending."
        }
        val snapshot = legacy.session(recordId) ?: return null
        return CardioSessionSnapshot(
            snapshot.recordId,
            snapshot.activityType.id(),
            snapshot.activityType.labelKo(),
            snapshot.status,
            snapshot.startedAtEpochMillis,
            snapshot.lastResumedAtEpochMillis,
            snapshot.activeDurationMillis,
            snapshot.distanceMeters,
            snapshot.gpsStatus
        )
    }
}
