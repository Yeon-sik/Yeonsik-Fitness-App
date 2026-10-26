package com.yeonsik.fitnessapp.feature.workout.ui

import com.yeonsik.fitnessapp.core.ui.FitnessDialog
import com.yeonsik.fitnessapp.core.ui.FitnessDialogSurface

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.yeonsik.fitness.shared.feature.exercise.model.ExerciseFamilyIdentity
import com.yeonsik.fitnessapp.core.ui.FitnessSemanticStatus
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.FitnessStatusBadge
import com.yeonsik.fitnessapp.exercise.ExerciseIllustrationLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Only the live set editor opts into motion; lists and historical records show frame A. */
@Composable
internal fun WorkoutExerciseImage(
    exerciseId: String,
    identity: ExerciseFamilyIdentity?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    animate: Boolean = false,
    sharedAnchor: Any? = null,
    onClick: (() -> Unit)? = null,
    maxImageHeight: Dp = 220.dp
) {
    val resources = LocalContext.current.resources
    val context = LocalContext.current
    val resolution = remember(context, exerciseId, identity) {
        if (identity != null) ExerciseIllustrationLookup.resolveExact(context, identity)
        else ExerciseIllustrationLookup.resolveExactForStorageExerciseId(context, exerciseId)
    }
    if (resolution.isPlaceholder) {
        FitnessStatusBadge(FitnessSemanticStatus.UNKNOWN, "운동 이미지 없음", modifier = modifier)
        return
    }
    val imageKey = WorkoutImageKey(resolution.drawables.toList(), Configuration(LocalConfiguration.current))
    key(imageKey) {
        val scene by produceState(WorkoutImageCache.peek(imageKey), imageKey) {
            value = WorkoutImageCache.load(resources, imageKey)
        }
        val playback = animate && LocalWorkoutImagePlayback.current
        val motionFrames by produceState<List<ImageBitmap>>(emptyList(), imageKey, playback) {
            value = if (playback) WorkoutImageCache.loadMotion(resources, imageKey) else emptyList()
        }
        var frame by remember { mutableIntStateOf(0) }
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, imageKey, playback, motionFrames) {
            frame = 0
            if (!playback || motionFrames.size < 2) return@LaunchedEffect
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    delay((resolution.durationsMs.getOrNull(frame) ?: 1000).coerceAtLeast(1).toLong())
                    frame = (frame + 1) % motionFrames.size
                }
            }
        }
        BoxWithConstraints(modifier.then(if (onClick != null) Modifier.clickable(
            onClickLabel = "운동 이미지 확대", role = Role.Button, onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center) {
            val loaded = scene
            val aspectRatio = loaded?.let {
                it.frameA.width * it.bounds.width / (it.frameA.height * it.bounds.height)
            }?.takeIf { it.isFinite() && it > 0f } ?: 1f
            val canvas = Modifier.fillMaxWidth().height((maxWidth / aspectRatio).coerceAtMost(maxImageHeight))
            if (loaded == null) {
                Spacer(canvas)
            } else {
                val visibleFrame = if (playback) frame else 0
                val bitmap = if (playback) motionFrames.getOrNull(visibleFrame) ?: loaded.frameA else loaded.frameA
                val painter = remember(bitmap, loaded.bounds) {
                    WorkoutExerciseCanvasPainter(BitmapPainter(bitmap), loaded.bounds)
                }
                Image(painter, contentDescription, Modifier.workoutSharedElement(sharedAnchor).then(canvas)
                    .testTag("workout-exercise-frame-$visibleFrame"),
                    contentScale = ContentScale.Fit)
                if (onClick != null) Surface(Modifier.align(Alignment.BottomEnd),
                    shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("확대", Modifier.padding(horizontal = FitnessSpacing.small, vertical = FitnessSpacing.micro),
                        style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** The viewer shares decoded artwork with the thumbnail and allocates no enlarged bitmap. */
@Composable
internal fun WorkoutExerciseImageViewer(
    exerciseId: String,
    identity: ExerciseFamilyIdentity?,
    title: String,
    animate: Boolean = false,
    onDismiss: () -> Unit
) {
    var scale by rememberSaveable(exerciseId, identity) { mutableFloatStateOf(1f) }
    var pan by remember(exerciseId, identity) { mutableStateOf(Offset.Zero) }
    fun zoomTo(next: Float) {
        val zoom = next.coerceIn(1f, 5f)
        pan = if (scale > 1f) pan * ((zoom - 1f) / (scale - 1f)) else Offset.Zero
        scale = zoom
    }
    FitnessDialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        FitnessDialogSurface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = FitnessSpacing.small),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("workout-image-close")) { Text("닫기") }
                }
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).clipToBounds()
                    .testTag("workout-image-viewer")
                    .pointerInput(exerciseId, identity) {
                        detectTransformGestures { centroid, drag, zoom, _ ->
                            val nextScale = (scale * zoom).coerceIn(1f, 5f)
                            val factor = nextScale / scale
                            val focal = centroid - Offset(size.width / 2f, size.height / 2f)
                            val moved = pan * factor + focal * (1f - factor) + drag
                            val limitX = size.width * (nextScale - 1f) / 2f
                            val limitY = size.height * (nextScale - 1f) / 2f
                            pan = Offset(moved.x.coerceIn(-limitX, limitX), moved.y.coerceIn(-limitY, limitY))
                            scale = nextScale
                        }
                    }, contentAlignment = Alignment.Center) {
                    WorkoutExerciseImage(exerciseId, identity, "$title 확대 이미지",
                        animate = animate,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = pan.x
                            translationY = pan.y
                        }, maxImageHeight = maxHeight)
                }
                Text("두 손가락으로 확대하거나 이미지를 끌어 이동하세요.",
                    Modifier.padding(horizontal = FitnessSpacing.card), style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(FitnessSpacing.small),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { zoomTo(scale - 0.5f) }, enabled = scale > 1f) { Text("축소") }
                    TextButton(onClick = { zoomTo(1f) }) { Text("${(scale * 100).toInt()}% · 원래 크기") }
                    TextButton(onClick = { zoomTo(scale + 0.5f) }, enabled = scale < 5f) { Text("확대") }
                }
            }
        }
    }
}

private data class WorkoutImageKey(val drawables: List<Int>, val configuration: Configuration)
private data class WorkoutImageScene(val frameA: ImageBitmap, val bounds: Rect, val byteCount: Int)
private data class WorkoutMotionFrames(val frames: List<ImageBitmap>, val byteCount: Int)

/** Process-wide, bounded artwork cache. It holds no Activity, user data or Compose state. */
private object WorkoutImageCache {
    private val decodeMutex = Mutex()
    private val scenes = object : LruCache<WorkoutImageKey, WorkoutImageScene>(16 * 1024 * 1024) {
        override fun sizeOf(key: WorkoutImageKey, value: WorkoutImageScene) = value.byteCount
    }
    private val motion = object : LruCache<WorkoutImageKey, WorkoutMotionFrames>(8 * 1024 * 1024) {
        override fun sizeOf(key: WorkoutImageKey, value: WorkoutMotionFrames) = value.byteCount
    }

    fun peek(key: WorkoutImageKey): WorkoutImageScene? = scenes.get(key)

    suspend fun load(resources: Resources, key: WorkoutImageKey): WorkoutImageScene? =
        withContext(Dispatchers.Default) {
            decodeMutex.withLock {
                scenes.get(key) ?: run {
                    val bitmap = decode(resources, key.drawables.first()) ?: return@withLock null
                    val scene = WorkoutImageScene(bitmap.asImageBitmap(),
                        workoutExerciseCanvasBounds(resources, key.drawables), bitmap.byteCount)
                    scenes.put(key, scene)
                    scene
                }
            }
        }

    suspend fun loadMotion(resources: Resources, key: WorkoutImageKey): List<ImageBitmap> {
        val scene = load(resources, key) ?: return emptyList()
        return withContext(Dispatchers.Default) {
            decodeMutex.withLock {
                val remaining = motion.get(key) ?: run {
                    val bitmaps = key.drawables.drop(1).map { decode(resources, it) ?: return@withLock emptyList() }
                    WorkoutMotionFrames(bitmaps.map { it.asImageBitmap() }, bitmaps.sumOf { it.byteCount })
                        .also { motion.put(key, it) }
                }
                listOf(scene.frameA) + remaining.frames
            }
        }
    }

    private fun decode(resources: Resources, drawable: Int): Bitmap? =
        BitmapFactory.decodeResource(resources, drawable, BitmapFactory.Options().apply {
            inScaled = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })?.also { it.prepareToDraw() }
}

/** Scan on the decode dispatcher once per cached scene, with one shared A/B camera transform. */
private fun workoutExerciseCanvasBounds(resources: Resources, drawables: List<Int>): Rect {
    val fullCanvas = Rect(0f, 0f, 1f, 1f)
    var union: Rect? = null
    for (drawable in drawables) {
        val bitmap = BitmapFactory.decodeResource(resources, drawable,
            BitmapFactory.Options().apply { inSampleSize = 8; inScaled = false }) ?: return fullCanvas
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        pixels.forEachIndexed { index, pixel ->
            if (pixel ushr 24 != 0) {
                val x = index % width
                val y = index / width
                left = minOf(left, x)
                top = minOf(top, y)
                right = maxOf(right, x)
                bottom = maxOf(bottom, y)
            }
        }
        if (right < left || bottom < top) return fullCanvas
        val bounds = Rect((left - 1).coerceAtLeast(0).toFloat() / width,
            (top - 1).coerceAtLeast(0).toFloat() / height,
            (right + 2).coerceAtMost(width).toFloat() / width,
            (bottom + 2).coerceAtMost(height).toFloat() / height)
        val previous = union
        union = if (previous == null) bounds else Rect(minOf(previous.left, bounds.left),
            minOf(previous.top, bounds.top), maxOf(previous.right, bounds.right), maxOf(previous.bottom, bounds.bottom))
    }
    return union ?: fullCanvas
}

private class WorkoutExerciseCanvasPainter(private val source: Painter, private val bounds: Rect) : Painter() {
    override val intrinsicSize = Size(source.intrinsicSize.width * bounds.width, source.intrinsicSize.height * bounds.height)
    override fun DrawScope.onDraw() {
        val canvasSize = Size(size.width / bounds.width, size.height / bounds.height)
        clipRect {
            translate(-canvasSize.width * bounds.left, -canvasSize.height * bounds.top) {
                with(source) { draw(canvasSize) }
            }
        }
    }
}
