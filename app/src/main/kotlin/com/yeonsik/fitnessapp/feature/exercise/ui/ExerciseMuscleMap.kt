package com.yeonsik.fitnessapp.feature.exercise.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yeonsik.fitnessapp.core.ui.FitnessCard
import com.yeonsik.fitnessapp.core.ui.FitnessOutlinedButton
import com.yeonsik.fitnessapp.core.ui.FitnessSpacing
import com.yeonsik.fitnessapp.core.ui.LocalFitnessColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.roundToInt

/** The picker uses the same approved style-4 masters and layer IDs as the muscle renderer. */
@Composable
internal fun ExerciseMuscleMap(
    state: ExercisePickerUiState.Ready,
    onSelect: (String) -> Unit,
    onClearSelection: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val loaded by produceState<Result<MuscleMapAssets>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { runCatching { MuscleMapAssets.load(context) } }
    }
    val assets = loaded?.getOrNull()
    val available = state.availablePrimarySubParts.map { it.id }.toSet()
    val selectedLabel = state.availablePrimarySubParts.firstOrNull {
        it.id == state.primarySubPart
    }?.label

    FitnessCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(FitnessSpacing.card),
                verticalArrangement = Arrangement.spacedBy(FitnessSpacing.small)
            ) {
                Text("그림에서 근육 선택", style = MaterialTheme.typography.titleMedium)
                Text(
                    "앞면·뒷면에서 근육을 누르면 주요 세부 부위가 설정됩니다.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            BoxWithConstraints(
                Modifier.fillMaxWidth().padding(horizontal = FitnessSpacing.micro)
            ) {
                val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                val imageRatio = (assets?.width?.toFloat() ?: 2f) / (assets?.height ?: 3)
                val sideWidth = minOf(
                    (maxWidth - FitnessSpacing.micro) / 2,
                    screenHeight * 0.4f * imageRatio
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for ((view, label) in listOf("front" to "앞면", "back" to "뒷면")) {
                        Column(
                            Modifier.width(sideWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(FitnessSpacing.micro)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge)
                            if (assets != null) {
                                MuscleMapSide(
                                    label, view, sideWidth, assets,
                                    state.primarySubPart, available, onSelect
                                )
                            } else {
                                val message = if (loaded == null) "불러오는 중" else "표시할 수 없음"
                                MuscleMapPlaceholder(sideWidth, message)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(FitnessSpacing.card),
                horizontalArrangement = Arrangement.spacedBy(FitnessSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "선택된 부위: ${selectedLabel ?: "없음"}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge
                )
                FitnessOutlinedButton(onClick = onClearSelection) { Text("초기화") }
            }
        }
    }
}

@Composable
private fun MuscleMapPlaceholder(width: Dp, message: String) {
    Box(
        Modifier.width(width).aspectRatio(2f / 3f),
        contentAlignment = Alignment.Center
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MuscleMapSide(
    label: String,
    view: String,
    width: Dp,
    assets: MuscleMapAssets,
    selectedGroup: String?,
    available: Set<String>,
    onSelect: (String) -> Unit
) {
    val highlight = LocalFitnessColors.current.action.toArgb()
    Box(
        Modifier
            .width(width)
            .aspectRatio(assets.width.toFloat() / assets.height)
            .pointerInput(assets, view, available) {
                detectTapGestures { position ->
                    val x = position.x * assets.width / size.width
                    val y = position.y * assets.height / size.height
                    assets.groupAt(view, x.roundToInt(), y.roundToInt(), available)
                        ?.let(onSelect)
                }
            }
    ) {
        Image(
            bitmap = assets.image(view),
            contentDescription = "$label 근육 지도",
            modifier = Modifier.matchParentSize()
        )
        if (selectedGroup != null) {
            Canvas(Modifier.matchParentSize()) {
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    val saved = native.save()
                    native.scale(size.width / assets.width, size.height / assets.height)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = highlight
                        alpha = 130
                        style = Paint.Style.FILL
                    }
                    assets.pathsForGroup(view, selectedGroup).forEach { path ->
                        native.drawPath(path, paint)
                    }
                    native.restoreToCount(saved)
                }
            }
        }
    }
}
internal fun groupForMuscleLayer(
    layerId: String,
    groups: Map<String, List<String>>,
    available: Set<String>
): String? = groups.entries
    .asSequence()
    .filter { (group, layers) -> group in available && layerId in layers }
    .minWithOrNull(compareBy<Map.Entry<String, List<String>>> { it.value.size }.thenBy { it.key })
    ?.key

internal class MuscleMapAssets private constructor(
    val width: Int,
    val height: Int,
    private val front: ImageBitmap,
    private val back: ImageBitmap,
    private val layers: List<Layer>,
    private val groups: Map<String, List<String>>
) {
    private class Layer(
        val id: String,
        val view: String,
        val kind: String,
        val path: Path,
        val region: Region
    )

    fun image(view: String): ImageBitmap = if (view == "front") front else back

    fun pathsForGroup(view: String, group: String): List<Path> {
        val selected = groups[group].orEmpty().toSet()
        return layers.filter { it.view == view && it.id in selected }.map { it.path }
    }

    fun groupAt(view: String, x: Int, y: Int, available: Set<String>): String? {
        if (x !in 0 until width || y !in 0 until height) return null
        return layers.asSequence()
            .filter { it.view == view && it.region.contains(x, y) }
            .sortedWith(compareBy<Layer> { if (it.kind == "deep_projection") 1 else 0 }
                .thenBy { it.region.bounds.width() * it.region.bounds.height() })
            .mapNotNull { groupForMuscleLayer(it.id, groups, available) }
            .firstOrNull()
    }

    companion object {
        @Volatile private var cached: MuscleMapAssets? = null
        private const val ROOT = "exercise_muscle/"

        fun load(context: Context): MuscleMapAssets =
            cached ?: synchronized(this) {
                cached ?: read(context).also { cached = it }
            }

        private fun read(context: Context): MuscleMapAssets {
            val document = context.assets.open(ROOT + "muscle-layers.json").bufferedReader()
                .use { JSONObject(it.readText()) }
            val canvas = document.getJSONObject("canvas")
            val width = canvas.getInt("width")
            val height = canvas.getInt("height")
            require(width > 0 && height > 0)
            val front = decode(context, "front")
            val back = decode(context, "back")
            val groupsJson = document.getJSONObject("exerciseGroups")
            val groups = groupsJson.keys().asSequence().associateWith { group ->
                val ids = groupsJson.getJSONArray(group)
                (0 until ids.length()).map(ids::getString)
            }
            val layerJson = document.getJSONArray("layers")
            val clip = Region(0, 0, width, height)
            val mirror = Matrix().apply { setScale(-1f, 1f, width / 2f, 0f) }
            val layers = (0 until layerJson.length()).mapNotNull { index ->
                val entry = layerJson.getJSONObject(index)
                val view = entry.getString("view")
                if (view != "front" && view != "back") return@mapNotNull null
                val path = Path()
                val shapes = entry.getJSONArray("paths")
                for (shapeIndex in 0 until shapes.length()) {
                    path.addPath(parseMusclePath(shapes.getString(shapeIndex)))
                }
                if (entry.optBoolean("mirror", false)) {
                    val mirrored = Path()
                    path.transform(mirror, mirrored)
                    path.addPath(mirrored)
                }
                val region = Region()
                if (!region.setPath(path, clip)) return@mapNotNull null
                Layer(entry.getString("id"), view, entry.optString("kind"), path, region)
            }
            require(layers.isNotEmpty())
            return MuscleMapAssets(width, height, front, back, layers, groups)
        }

        private fun decode(context: Context, side: String): ImageBitmap {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 2
                inScaled = false
            }
            val bitmap = context.assets.open(ROOT + "source/$side-master.png").use {
                BitmapFactory.decodeStream(it, null, options)
            } ?: error("Missing style-4 $side master")
            return bitmap.asImageBitmap()
        }
    }
}

/** Style-4 layer paths use only absolute M, L, C and Z commands. Unknown commands fail closed. */
private fun parseMusclePath(data: String): Path {
    val tokens = Regex("[MLCZ]|-?\\d+(?:\\.\\d+)?").findAll(data).map { it.value }.toList()
    val path = Path()
    var index = 0
    fun number(): Float = tokens.getOrNull(index++)?.toFloatOrNull()
        ?: error("Invalid style-4 path")
    while (index < tokens.size) {
        when (tokens[index++]) {
            "M" -> path.moveTo(number(), number())
            "L" -> path.lineTo(number(), number())
            "C" -> path.cubicTo(number(), number(), number(), number(), number(), number())
            "Z" -> path.close()
            else -> error("Unsupported style-4 path command")
        }
    }
    return path
}
