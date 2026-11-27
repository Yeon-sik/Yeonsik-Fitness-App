package com.yeonsik.fitness.shared.feature.settings

import com.yeonsik.fitness.shared.feature.workout.model.MassUnit

enum class DisplayTheme(val id: String, val label: String) {
    DARK("dark", "어둡게"), LIGHT("light", "밝게"), SYSTEM("system", "기기 설정");

    fun isDark(systemIsDark: Boolean): Boolean = this == DARK || (this == SYSTEM && systemIsDark)
}

object DisplayPreferences {
    const val MASS_UNIT_KEY = "preferred_mass_unit"
    const val THEME_KEY = "theme_mode"
    const val DEFAULT_THEME_ID = "dark"

    fun massUnit(stored: String?): MassUnit = MassUnit.orDefault(MassUnit.parse(stored))
    fun theme(stored: String?): DisplayTheme = DisplayTheme.entries.firstOrNull { it.id == stored } ?: DisplayTheme.DARK
}
