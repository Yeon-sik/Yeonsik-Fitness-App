package com.yeonsik.fitnessapp.core.ui

import android.content.Context
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer

/*
 * Dialogs and sheets draw in their own window, so they cannot sample the page backdrop. Their
 * glass blurs the windows behind them instead (API 31+), and turns opaque again as soon as the
 * system disables cross-window blur, for example in battery saver.
 */

/** [AlertDialog] with the glass surface. Arguments and behavior match AlertDialog. */
@Composable
fun FitnessAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    properties: DialogProperties = DialogProperties()
) {
    var blurActive by rememberDialogBlurActive()
    val shape = RoundedCornerShape(FitnessUiTokens.GLASS_DIALOG_RADIUS_DP.dp)
    val highlight = fitnessDialogHighlight(blurActive)
    val stroke = if (highlight != null) {
        BorderStroke(1.dp, Brush.verticalGradient(0f to highlight, 0.35f to Color.Transparent))
    } else {
        BorderStroke(1.dp, fitnessDialogOutline())
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            // An effect only: it finds the dialog window from inside the dialog's composition.
            FitnessDialogWindowGlass { blurActive = it }
            confirmButton()
        },
        modifier = modifier.border(stroke, shape),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = fitnessDialogContainerColor(blurActive),
        tonalElevation = 0.dp,
        properties = properties
    )
}

/**
 * [ModalBottomSheet] with the glass surface and 28dp top corners. Its edge, the glass highlight
 * or the opaque outline, follows the top corners and fades out below them; the drag handle row
 * draws it, so it moves with the sheet while dragging.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    var blurActive by rememberDialogBlurActive()
    val radius = FitnessUiTokens.GLASS_DIALOG_RADIUS_DP.dp
    val shape = RoundedCornerShape(topStart = radius, topEnd = radius)
    val edge = fitnessDialogHighlight(blurActive) ?: fitnessDialogOutline()
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        shape = shape,
        containerColor = fitnessDialogContainerColor(blurActive),
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val strokeWidth = 1.dp.toPx()
                        val cornerHeight = radius.toPx()
                        // Twice the corner tall: the edge has faded out before the outline's bottom.
                        val outline = shape.createOutline(
                            Size(size.width - strokeWidth, cornerHeight * 2), layoutDirection, this
                        )
                        val brush = Brush.verticalGradient(
                            listOf(edge, Color.Transparent), startY = 0f, endY = cornerHeight
                        )
                        onDrawBehind {
                            translate(strokeWidth / 2, strokeWidth / 2) {
                                drawOutline(outline, brush, style = Stroke(strokeWidth))
                            }
                        }
                    }
            ) {
                FitnessDialogWindowGlass { blurActive = it }
                dragHandle?.invoke()
            }
        },
        content = content
    )
}

/**
 * Turns on blur behind the dialog or sheet window this is composed in and reports whether it is
 * active. Blur behind covers the whole screen behind the scrim, so it needs no window shape.
 */
@Composable
internal fun FitnessDialogWindowGlass(onBlurActiveChange: (Boolean) -> Unit) {
    val glassEnabled = LocalFitnessGlassEnabled.current
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    val radiusPx = with(LocalDensity.current) { FitnessUiTokens.GLASS_SHEET_WINDOW_BLUR_DP.dp.roundToPx() }
    val onChange by rememberUpdatedState(onBlurActiveChange)
    DisposableEffect(window, glassEnabled, radiusPx) {
        if (window == null || !glassEnabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            onChange(false)
            onDispose {}
        } else {
            val detach = attachBlurBehind(window, radiusPx) { onChange(it) }
            onDispose { detach() }
        }
    }
}

/** Starts from the system's current blur state so the first frame already has the right surface. */
@Composable
private fun rememberDialogBlurActive(): MutableState<Boolean> {
    val glassEnabled = LocalFitnessGlassEnabled.current
    val context = LocalContext.current
    return remember(glassEnabled, context) { mutableStateOf(glassEnabled && crossWindowBlurEnabled(context)) }
}

@Composable
private fun fitnessDialogContainerColor(blurActive: Boolean): Color {
    val colors = MaterialTheme.colorScheme
    if (!blurActive) return colors.surfaceContainerHigh
    val alpha = if (LocalFitnessDarkTheme.current) FitnessUiTokens.GLASS_SHEET_SURFACE_ALPHA_DARK
    else FitnessUiTokens.GLASS_SHEET_SURFACE_ALPHA_LIGHT
    return colors.surface.copy(alpha = alpha)
}

/** The white highlight along a glass surface's top edge; null on the opaque surface. */
@Composable
private fun fitnessDialogHighlight(blurActive: Boolean): Color? {
    if (!blurActive) return null
    val alpha = if (LocalFitnessDarkTheme.current) FitnessUiTokens.GLASS_STROKE_ALPHA_DARK
    else FitnessUiTokens.GLASS_STROKE_ALPHA_LIGHT
    return Color.White.copy(alpha = alpha)
}

/** The 1dp outline of the opaque surface, as on every other glass fallback. */
@Composable
private fun fitnessDialogOutline(): Color {
    val colors = MaterialTheme.colorScheme
    return if (rememberHighContrastTextEnabled()) colors.onSurface else colors.outlineVariant
}

private fun crossWindowBlurEnabled(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && CrossWindowBlur.enabled(context)

@RequiresApi(Build.VERSION_CODES.S)
private object CrossWindowBlur {
    fun enabled(context: Context): Boolean =
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
}

/** The listener fires at once with the current state and again whenever the system toggles blur. */
private fun attachBlurBehind(window: Window, radiusPx: Int, onActive: (Boolean) -> Unit): () -> Unit {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return {}
    return BlurBehind.attach(window, radiusPx, onActive)
}

@RequiresApi(Build.VERSION_CODES.S)
private object BlurBehind {
    fun attach(window: Window, radiusPx: Int, onActive: (Boolean) -> Unit): () -> Unit {
        val listener = Consumer<Boolean> { enabled ->
            if (enabled) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply { blurBehindRadius = radiusPx }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            onActive(enabled)
        }
        val windowManager = window.windowManager
        windowManager.addCrossWindowBlurEnabledListener(window.context.mainExecutor, listener)
        return {
            windowManager.removeCrossWindowBlurEnabledListener(listener)
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }
}
