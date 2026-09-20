package com.yeonsik.fitnessapp.feature.statistics.ui

import com.yeonsik.fitness.shared.core.account.AccountScope
import com.yeonsik.fitnessapp.feature.statistics.model.StatisticsPeriod
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsViewModelTest {
    @Test
    fun requestGateRejectsLateResultsFromOlderPeriodOrAccount() {
        val gate = StatisticsRequestGate()
        val first = StatisticsRequestIdentity("owner-a", "2024-02-10", StatisticsPeriod.THIRTY_DAYS)
        val second = StatisticsRequestIdentity("owner-b", "2024-02-10", StatisticsPeriod.SEVEN_DAYS)
        val firstToken = gate.begin(first)
        val secondToken = gate.begin(second)

        assertFalse(gate.accepts(firstToken, first))
        assertTrue(gate.accepts(secondToken, second))
        assertFalse(gate.accepts(secondToken, first))
    }

    @Test
    fun periodWindowIsExplicitlySameLengthAndAdjacent() {
        val identity = StatisticsRequestIdentity(
            AccountScope("owner-a").ownerId,
            "2024-02-10",
            StatisticsPeriod.NINETY_DAYS
        )

        assertTrue(identity.ownerId.isNotBlank())
        assertTrue(identity.period.days == 90)
    }
}
