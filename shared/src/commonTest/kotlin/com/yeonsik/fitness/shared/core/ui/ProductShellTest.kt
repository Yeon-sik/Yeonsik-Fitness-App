package com.yeonsik.fitness.shared.core.ui

import com.yeonsik.fitness.shared.feature.settings.DisplayPreferences
import com.yeonsik.fitness.shared.feature.settings.DisplayTheme
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import kotlin.test.*

class ProductShellTest {
    @Test fun fourTabIdsAndLabelsHaveStableOrderAndStopAtBothEnds() {
        val tabs = ProductTab.ordered
        assertEquals(listOf("home", "fitness", "records", "settings"), tabs.map { it.id })
        assertEquals(listOf("메인", "피트니스", "기록", "설정"), tabs.map { it.label })
        for ((index, tab) in tabs.withIndex()) {
            assertEquals(tab, ProductTab.fromId(tab.id))
            assertFalse(tab.needsRootChange(tab))
            assertTrue(tab.needsRootChange(null))
            assertEquals(tabs.getOrNull(index + 1), tab.adjacent(true))
            assertEquals(tabs.getOrNull(index - 1), tab.adjacent(false))
        }
        assertNull(ProductTab.fromId("체중"))
        assertTrue(ProductTab.RECORDS.needsRootChange(ProductTab.FITNESS))
    }

    @Test fun installedPreferencesAndAbsentValuesHaveTheSameMeaning() {
        assertEquals("preferred_mass_unit", DisplayPreferences.MASS_UNIT_KEY)
        assertEquals("theme_mode", DisplayPreferences.THEME_KEY)
        assertEquals(MassUnit.KG, DisplayPreferences.massUnit(null))
        assertEquals(MassUnit.KG, DisplayPreferences.massUnit("?"))
        assertEquals(MassUnit.LB, DisplayPreferences.massUnit(" LB "))
        assertEquals(DisplayTheme.DARK.id, DisplayPreferences.DEFAULT_THEME_ID)
        assertEquals(DisplayTheme.DARK, DisplayPreferences.theme(null))
        assertEquals(DisplayTheme.DARK, DisplayPreferences.theme("unknown"))
        assertFalse(DisplayPreferences.theme("light").isDark(true))
        assertTrue(DisplayPreferences.theme("dark").isDark(false))
        assertFalse(DisplayPreferences.theme("system").isDark(false))
        assertTrue(DisplayPreferences.theme("system").isDark(true))
    }
}
