@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.yeonsik.fitness.shared.feature.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.yeonsik.fitness.shared.core.ui.fitnessColorScheme
import com.yeonsik.fitness.shared.core.ui.fitnessShapes
import com.yeonsik.fitness.shared.core.ui.fitnessTypography
import com.yeonsik.fitness.shared.feature.workout.model.MassUnit
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIViewController

object IosDisplaySettings {
    private val defaults = NSUserDefaults.standardUserDefaults
    val themeKey: String get() = DisplayPreferences.THEME_KEY
    var unit by mutableStateOf(DisplayPreferences.massUnit(defaults.stringForKey(DisplayPreferences.MASS_UNIT_KEY)))
        private set
    var theme by mutableStateOf(DisplayPreferences.theme(defaults.stringForKey(themeKey)))
        private set

    internal fun selectUnit(value: MassUnit) {
        defaults.setObject(value.id, forKey = DisplayPreferences.MASS_UNIT_KEY)
        unit = value
    }

    internal fun selectTheme(id: String) {
        val value = DisplayPreferences.theme(id)
        defaults.setObject(value.id, forKey = themeKey)
        theme = value
    }
}

@Composable
internal fun IosFitnessTheme(content: @Composable () -> Unit) {
    val dark = IosDisplaySettings.theme.isDark(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = remember(dark) { fitnessColorScheme(dark) },
        typography = remember { fitnessTypography() },
        shapes = remember { fitnessShapes() },
        content = content
    )
}

fun makeDisplaySettingsViewController(): UIViewController = ComposeUIViewController {
    IosFitnessTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DisplaySettings(IosDisplaySettings.unit, IosDisplaySettings.theme.id,
                    IosDisplaySettings::selectUnit, IosDisplaySettings::selectTheme)
            }
        }
    }
}
