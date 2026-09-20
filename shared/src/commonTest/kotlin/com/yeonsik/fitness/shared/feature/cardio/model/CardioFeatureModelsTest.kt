package com.yeonsik.fitness.shared.feature.cardio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardioFeatureModelsTest {
    @Test
    fun activityMetadataAndIdLookupStayStable() {
        assertEquals("walking", CardioActivityType.WALKING.id)
        assertEquals("걷기", CardioActivityType.WALKING.labelKo)
        assertEquals(5.0, CardioActivityType.WALKING.maxPlausibleSpeedMetersPerSecond)
        assertEquals(CardioActivityType.RUNNING, CardioActivityType.fromId(" RUNNING "))
    }

    @Test
    fun distanceFilterRejectsJitterAndImplausibleSpeed() {
        val previous = sample(37.5665, 126.9780, 5f, 1_000L)
        assertEquals(
            CardioDistanceFilter.Reason.JITTER,
            CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING, previous, sample(37.566505, 126.9780, 5f, 11_000L)
            ).reason
        )
        assertEquals(
            CardioDistanceFilter.Reason.TOO_FAST,
            CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING, previous, sample(37.5675, 126.9780, 5f, 2_000L)
            ).reason
        )
    }

    @Test
    fun routeProjectionSamplesEndpointsAndSplitsLongGaps() {
        val builder = CardioRouteProjection.builder(10, 4)
        repeat(10) { index -> builder.add(index * 1_000L, 37.0 + index, 127.0 + index) }
        val projection = builder.build()
        assertEquals(10, projection.rawPointCount)
        assertEquals(4, projection.displayPointCount)
        assertEquals(37.0, projection.segments.first().first().latitude)
        assertEquals(46.0, projection.segments.first().last().latitude)

        val split = CardioRouteProjection.builder(4, 800)
            .add(0L, 37.0, 127.0)
            .add(1_000L, 37.001, 127.001)
            .add(20_000L, 37.1, 127.1)
            .add(21_000L, 37.101, 127.101)
            .build()
        assertEquals(2, split.segments.size)
        assertTrue(split.hasRenderablePath())
        assertFalse(CardioRouteProjection.empty().hasRenderablePath())
    }

    private fun sample(latitude: Double, longitude: Double, accuracy: Float, capturedAtMillis: Long) =
        CardioLocationSample(latitude, longitude, accuracy, capturedAtMillis, null)
}
