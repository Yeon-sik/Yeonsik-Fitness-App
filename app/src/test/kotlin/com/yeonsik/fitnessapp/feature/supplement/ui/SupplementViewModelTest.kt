package com.yeonsik.fitnessapp.feature.supplement.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupplementViewModelTest {
    @Test
    fun staleDateRequestCannotReplaceCurrentSelection() {
        val gate = SupplementRequestGate()
        val today = SupplementRequestIdentity("owner-a", "2026-09-16", "2026-09-16")
        val yesterday = SupplementRequestIdentity("owner-a", "2026-09-16", "2026-09-15")
        val todayToken = gate.begin(today)
        val yesterdayToken = gate.begin(yesterday)

        assertFalse(gate.accepts(todayToken, today))
        assertTrue(gate.accepts(yesterdayToken, yesterday))
    }

    @Test
    fun accountAndTodayArePartOfSupplementRequestIdentity() {
        val gate = SupplementRequestGate()
        val accountA = SupplementRequestIdentity("owner-a", "2026-09-16", "2026-09-14")
        val accountB = SupplementRequestIdentity("owner-b", "2026-09-16", "2026-09-14")
        val accountAToken = gate.begin(accountA)
        val accountBToken = gate.begin(accountB)

        assertFalse(gate.accepts(accountAToken, accountA))
        assertFalse(gate.accepts(accountBToken, accountA))
        assertTrue(gate.accepts(accountBToken, accountB))
    }
}
