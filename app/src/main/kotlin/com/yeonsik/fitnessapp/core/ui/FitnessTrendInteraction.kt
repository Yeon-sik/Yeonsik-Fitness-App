package com.yeonsik.fitnessapp.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** Drawing and hit testing share the same coordinates, including filtered-out invalid values. */
internal fun fitnessTrendPointOffsets(
    model: FitnessTrendPresentation,
    size: Size,
    padding: Float
): List<Offset> {
    val width = size.width.coerceAtLeast(0f)
    val height = size.height.coerceAtLeast(0f)
    val left = padding.coerceIn(0f, width / 2f)
    val top = padding.coerceIn(0f, height / 2f)
    val xSpan = width - 2f * left
    val ySpan = height - 2f * top
    return model.finitePoints.mapIndexed { index, point ->
        val x = if (model.finitePoints.size == 1) width / 2f
        else left + xSpan * index / (model.finitePoints.size - 1).toFloat()
        val y = height - top - ySpan * model.range.normalize(point.value ?: Double.NaN)
        Offset(x, y)
    }
}

/** Select only a nearby point; taps on empty chart space dismiss the current detail. */
internal fun fitnessTrendHitTest(points: List<Offset>, tap: Offset, radius: Float): Int? {
    if (!tap.x.isFinite() || !tap.y.isFinite() || !radius.isFinite() || radius < 0f) return null
    val nearest = points.indices.minByOrNull { (points[it] - tap).getDistanceSquared() }
        ?: return null
    return nearest.takeIf { (points[it] - tap).getDistanceSquared() <= radius * radius }
}
