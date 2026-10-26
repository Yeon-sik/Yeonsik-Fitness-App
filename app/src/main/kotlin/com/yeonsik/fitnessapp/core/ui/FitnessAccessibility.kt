package com.yeonsik.fitnessapp.core.ui

import android.content.Context
import android.os.Build
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Refresh after returning from system accessibility settings as well as on first composition. */
@Composable
internal fun rememberHighContrastTextEnabled(): Boolean {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var enabled by remember(context) { mutableStateOf(highContrastTextEnabled(context)) }
    DisposableEffect(context, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) enabled = highContrastTextEnabled(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

private fun highContrastTextEnabled(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 36 &&
        context.getSystemService(AccessibilityManager::class.java)?.isHighContrastTextEnabled == true
