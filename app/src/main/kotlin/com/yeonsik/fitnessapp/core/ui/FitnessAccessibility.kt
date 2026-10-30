package com.yeonsik.fitnessapp.core.ui

import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/** True when the system high-contrast text setting is on (API 36+); glass then falls back. */
@Composable
fun rememberHighContrastTextEnabled(): Boolean {
    val context = LocalContext.current
    return Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(AccessibilityManager::class.java)?.isHighContrastTextEnabled == true
}
