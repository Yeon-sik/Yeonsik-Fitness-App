package com.yeonsik.fitnessapp.core.ui

import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

private val LocalDialogGlassAvailable = staticCompositionLocalOf { false }

internal fun fitnessDialogGlassSupported(api: Int, enabled: Boolean, hasWindow: Boolean, crossWindowBlur: Boolean) =
    api >= 31 && enabled && hasWindow && crossWindowBlur

private fun dialogWindow(view: View): Window? {
    if (view is DialogWindowProvider) return view.window
    var parent = view.parent
    while (parent != null) {
        if (parent is DialogWindowProvider) return parent.window
        parent = parent.parent
    }
    return null
}

/** Runs only inside the modal's own composition; the Activity window is never substituted. */
@Composable
private fun rememberDialogWindowGlass(blurBehind: Boolean = false): Boolean {
    val view = LocalView.current
    val context = LocalContext.current
    val window = remember(view) { dialogWindow(view) }
    val enabled = LocalFitnessGlassEnabled.current
    val radius = with(LocalDensity.current) { FitnessUiTokens.GLASS_SHEET_WINDOW_BLUR_DP.dp.roundToPx() }
    val corner = with(LocalDensity.current) { 28.dp.toPx() }
    var available by remember(window) { mutableStateOf(false) }
    DisposableEffect(window, enabled, radius, corner, blurBehind) {
        if (Build.VERSION.SDK_INT < 31 || window == null || !enabled) {
            available = false
            onDispose { }
        } else {
            val manager = context.getSystemService(WindowManager::class.java)
            val previousBackground = window.decorView.background
            val previousBlurBehind = window.attributes.blurBehindRadius
            val hadBlurBehind = window.attributes.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0
            fun reset() {
                window.setBackgroundBlurRadius(0)
                window.setBackgroundDrawable(previousBackground)
                if (!hadBlurBehind) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = previousBlurBehind }
            }
            fun apply(enabledBySystem: Boolean) {
                available = false
                runCatching {
                    if (enabledBySystem) {
                        if (blurBehind) {
                            // A sheet's window fills the display. Blur behind it, not a full-screen
                            // window background pretending to be the bounded sheet surface.
                            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                            window.attributes = window.attributes.apply { blurBehindRadius = radius }
                        } else {
                            window.setBackgroundDrawable(GradientDrawable().apply {
                                cornerRadius = corner
                                setColor(android.graphics.Color.TRANSPARENT)
                            })
                            window.setBackgroundBlurRadius(radius)
                        }
                        available = true
                    } else reset()
                }.onFailure { runCatching { reset() } }
            }
            val listener = Consumer<Boolean> { apply(it) }
            var registered = false
            runCatching {
                apply(manager.isCrossWindowBlurEnabled)
                manager.addCrossWindowBlurEnabledListener(context.mainExecutor, listener)
                registered = true
            }.onFailure { available = false; runCatching { reset() } }
            onDispose {
                if (registered) manager.removeCrossWindowBlurEnabledListener(listener)
                runCatching { reset() }
            }
        }
    }
    return fitnessDialogGlassSupported(Build.VERSION.SDK_INT, enabled, window != null, available)
}

@Composable
private fun dialogSurfaceColor(available: Boolean, requested: Color = Color.Unspecified): Color {
    val colors = MaterialTheme.colorScheme
    if (!available) return if (requested == Color.Unspecified) colors.surfaceContainerHigh else requested.copy(alpha = 1f)
    val base = if (requested == Color.Unspecified) colors.surface else requested
    return base.copy(alpha = if (LocalFitnessDarkTheme.current) FitnessUiTokens.GLASS_SHEET_SURFACE_ALPHA_DARK
        else FitnessUiTokens.GLASS_SHEET_SURFACE_ALPHA_LIGHT)
}

@Composable
private fun dialogBorder(available: Boolean): BorderStroke = BorderStroke(1.dp, when {
    LocalFitnessGlassHighContrast.current -> MaterialTheme.colorScheme.onSurface
    available -> Color.White.copy(alpha = if (LocalFitnessDarkTheme.current) FitnessUiTokens.GLASS_STROKE_ALPHA_DARK
        else FitnessUiTokens.GLASS_STROKE_ALPHA_LIGHT)
    else -> MaterialTheme.colorScheme.outlineVariant
})

@Composable
fun FitnessAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Unspecified,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    var available by remember { mutableStateOf(false) }
    val glass = available && LocalFitnessGlassEnabled.current
    AlertDialog(onDismissRequest, confirmButton = {
        val current = rememberDialogWindowGlass()
        SideEffect { available = current }
        confirmButton()
    }, modifier = modifier.border(dialogBorder(glass), shape), dismissButton = dismissButton,
        icon = icon, title = title, text = text, shape = shape,
        containerColor = dialogSurfaceColor(glass, containerColor), iconContentColor = iconContentColor,
        titleContentColor = titleContentColor, textContentColor = textContentColor,
        tonalElevation = tonalElevation, properties = properties)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    containerColor: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.windowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit
) {
    var available by remember { mutableStateOf(false) }
    val glass = available && LocalFitnessGlassEnabled.current
    ModalBottomSheet(onDismissRequest, modifier.border(dialogBorder(glass), shape), sheetState, sheetMaxWidth,
        shape, dialogSurfaceColor(glass, containerColor), contentColor, tonalElevation, scrimColor,
        dragHandle, contentWindowInsets, properties) {
        val current = rememberDialogWindowGlass(blurBehind = true)
        SideEffect { available = current }
        content()
    }
}

@Composable
fun FitnessDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest, properties) {
        val available = rememberDialogWindowGlass(blurBehind = !properties.usePlatformDefaultWidth)
        CompositionLocalProvider(LocalDialogGlassAvailable provides available, content = content)
    }
}

/** Only the modal's outer surface uses this adapter; nested content keeps ordinary surfaces. */
@Composable
fun FitnessDialogSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    color: Color = Color.Unspecified,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    tonalElevation: Dp = 0.dp,
    shadowElevation: Dp = 0.dp,
    border: BorderStroke? = null,
    content: @Composable () -> Unit
) {
    val glass = LocalDialogGlassAvailable.current && LocalFitnessGlassEnabled.current
    Surface(modifier, shape, dialogSurfaceColor(glass, color), contentColor, tonalElevation,
        shadowElevation, border ?: dialogBorder(glass), content)
}
