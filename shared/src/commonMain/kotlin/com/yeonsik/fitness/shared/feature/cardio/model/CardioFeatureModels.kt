package com.yeonsik.fitness.shared.feature.cardio.model

import kotlin.jvm.JvmStatic

/** Minimum cardio activity types that can be tracked by GPS. */
enum class CardioActivityType(
    val id: String,
    val labelKo: String,
    val maxPlausibleSpeedMetersPerSecond: Double
) {
    WALKING("walking", "걷기", 5.0),
    RUNNING("running", "달리기", 12.0),
    CYCLING("cycling", "자전거", 35.0);

    fun id(): String = id

    fun labelKo(): String = labelKo

    fun maxPlausibleSpeedMetersPerSecond(): Double = maxPlausibleSpeedMetersPerSecond

    companion object {
        @JvmStatic
        fun fromId(value: String?): CardioActivityType {
            val normalized = value?.trim()?.lowercase() ?: ""
            return entries.firstOrNull { it.id == normalized }
                ?: throw IllegalArgumentException("지원하지 않는 유산소 유형입니다: $value")
        }
    }
}

/** Immutable location values required for distance calculation, independent of Android Location. */
data class CardioLocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long?,
    val reportedSpeedMetersPerSecond: Float?
)

/** Filters GPS jitter and implausible jumps before distance is accumulated. */
object CardioDistanceFilter {
    const val MAX_ACCURACY_METERS = 35f
    private const val EARTH_RADIUS_METERS = 6_371_000.0
    private const val MIN_SEGMENT_METERS = 3.0
    private const val MAX_DYNAMIC_MIN_SEGMENT_METERS = 10.0

    @JvmStatic
    fun evaluate(
        activityType: CardioActivityType?,
        previous: CardioLocationSample?,
        candidate: CardioLocationSample?
    ): Result {
        if (activityType == null || candidate == null || !validCoordinates(candidate)) {
            return Result.rejected(Reason.INVALID)
        }
        if (!candidate.accuracyMeters.isFinite() || candidate.accuracyMeters <= 0f
            || candidate.accuracyMeters > MAX_ACCURACY_METERS
        ) {
            return Result.rejected(Reason.INACCURATE)
        }
        if (candidate.reportedSpeedMetersPerSecond != null
            && (!candidate.reportedSpeedMetersPerSecond.isFinite()
                || candidate.reportedSpeedMetersPerSecond < 0f
                || candidate.reportedSpeedMetersPerSecond > activityType.maxPlausibleSpeedMetersPerSecond * 1.25)
        ) {
            return Result.rejected(Reason.TOO_FAST)
        }
        if (previous == null) return Result.accepted(0.0)
        val candidateTime = candidate.capturedAtMillis ?: return Result.rejected(Reason.INVALID)
        val previousTime = previous.capturedAtMillis ?: return Result.rejected(Reason.INVALID)
        if (candidateTime <= previousTime) {
            return Result.rejected(Reason.STALE)
        }

        val segmentMeters = haversineMeters(previous, candidate)
        val dynamicMinimum = ((previous.accuracyMeters + candidate.accuracyMeters) * 0.25)
            .coerceAtLeast(MIN_SEGMENT_METERS)
            .coerceAtMost(MAX_DYNAMIC_MIN_SEGMENT_METERS)
        if (segmentMeters < dynamicMinimum) return Result.rejected(Reason.JITTER)

        val elapsedSeconds = (candidateTime - previousTime) / 1_000.0
        val derivedSpeed = segmentMeters / elapsedSeconds
        if (!derivedSpeed.isFinite() || derivedSpeed > activityType.maxPlausibleSpeedMetersPerSecond) {
            return Result.rejected(Reason.TOO_FAST)
        }
        return Result.accepted(segmentMeters)
    }

    private fun haversineMeters(first: CardioLocationSample, second: CardioLocationSample): Double {
        val firstLat = first.latitude * kotlin.math.PI / 180.0
        val secondLat = second.latitude * kotlin.math.PI / 180.0
        val latDelta = secondLat - firstLat
        val lonDelta = (second.longitude - first.longitude) * kotlin.math.PI / 180.0
        val sinLat = kotlin.math.sin(latDelta / 2.0)
        val sinLon = kotlin.math.sin(lonDelta / 2.0)
        val a = sinLat * sinLat + kotlin.math.cos(firstLat) * kotlin.math.cos(secondLat) * sinLon * sinLon
        return EARTH_RADIUS_METERS * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
    }

    private fun validCoordinates(sample: CardioLocationSample): Boolean =
        sample.latitude.isFinite() && sample.longitude.isFinite()
            && sample.latitude in -90.0..90.0 && sample.longitude in -180.0..180.0
            && sample.capturedAtMillis != null && sample.capturedAtMillis > 0L

    enum class Reason { ACCEPTED, INVALID, INACCURATE, STALE, JITTER, TOO_FAST }

    data class Result(
        val accepted: Boolean,
        val segmentDistanceMeters: Double,
        val reason: Reason
    ) {
        companion object {
            fun accepted(segmentDistanceMeters: Double) = Result(true, segmentDistanceMeters, Reason.ACCEPTED)

            @JvmStatic
            fun rejected(reason: Reason) = Result(false, 0.0, reason)
        }
    }
}

/** Bounded route projection for rendering; raw GPS samples remain in platform storage. */
class CardioRouteProjection private constructor(
    segments: List<List<RoutePoint>>,
    val rawPointCount: Int,
    val displayPointCount: Int
) {
    val segments: List<List<RoutePoint>> = segments.filter { it.isNotEmpty() }.map { it.toList() }

    fun segments(): List<List<RoutePoint>> = segments

    fun rawPointCount(): Int = rawPointCount

    fun displayPointCount(): Int = displayPointCount

    fun hasRenderablePath(): Boolean = segments.any { it.size >= 2 }

    companion object {
        const val DEFAULT_MAX_DISPLAY_POINTS = 800
        const val SEGMENT_GAP_MILLIS = 10_000L

        @JvmStatic

        fun empty() = CardioRouteProjection(emptyList(), 0, 0)

        @JvmStatic
        fun builder(rawPointCount: Int, maxDisplayPoints: Int) = Builder(rawPointCount, maxDisplayPoints)
    }


    class Builder internal constructor(rawPointCount: Int, maxDisplayPoints: Int) {
        private val expectedRawPointCount = rawPointCount.coerceAtLeast(0)
        private val maxDisplayPoints = maxDisplayPoints.coerceAtLeast(1)
        private val targetDisplayPointCount = minOf(expectedRawPointCount, this.maxDisplayPoints)
        private val segments = mutableListOf<MutableList<RoutePoint>>()
        private var currentSegment: MutableList<RoutePoint>? = null
        private var previousCapturedAtMillis = 0L
        private var hasPrevious = false
        private var rawPointCount = 0
        private var displayPointCount = 0
        private var nextSampleOrdinal = 0

        fun add(capturedAtMillis: Long, latitude: Double, longitude: Double): Builder {
            if (hasPrevious && capturedAtMillis - previousCapturedAtMillis > SEGMENT_GAP_MILLIS) {
                currentSegment = null
            }
            if (displayPointCount < maxDisplayPoints && shouldSample(rawPointCount)) {
                val segment = currentSegment ?: mutableListOf<RoutePoint>().also {
                    currentSegment = it
                    segments += it
                }
                segment += RoutePoint(capturedAtMillis, latitude, longitude)
                displayPointCount++
            }
            previousCapturedAtMillis = capturedAtMillis
            hasPrevious = true
            rawPointCount++
            return this
        }

        private fun shouldSample(inputIndex: Int): Boolean {
            if (targetDisplayPointCount == 0) return false
            if (expectedRawPointCount <= maxDisplayPoints) return true
            if (targetDisplayPointCount == 1) return inputIndex == expectedRawPointCount - 1
            if (nextSampleOrdinal >= targetDisplayPointCount) return false

            var targetIndex = sampleIndex(nextSampleOrdinal)
            while (nextSampleOrdinal < targetDisplayPointCount && inputIndex >= targetIndex) {
                nextSampleOrdinal++
                if (inputIndex == targetIndex) return true
                if (nextSampleOrdinal >= targetDisplayPointCount) return false
                targetIndex = sampleIndex(nextSampleOrdinal)
            }
            return false
        }

        private fun sampleIndex(ordinal: Int): Int = kotlin.math.round(
            ordinal.toDouble() * (expectedRawPointCount - 1) / (targetDisplayPointCount - 1)
        ).toInt()

        fun build() = CardioRouteProjection(segments, rawPointCount, displayPointCount)
    }

    data class RoutePoint(
        val capturedAtMillis: Long,
        val latitude: Double,
        val longitude: Double
    )
}
