package com.yeonsik.fitnessapp.cardio;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CardioDistanceFilterTest {
    @Test
    public void firstAccuratePointEstablishesAnchorWithoutDistance() {
        CardioDistanceFilter.Result result = CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING,
                null,
                sample(37.5665, 126.9780, 5f, 1_000L)
        );

        assertTrue(result.accepted);
        assertEquals(0d, result.segmentDistanceMeters, 0.001d);
    }

    @Test
    public void rejectsWeakAccuracyAndSmallGpsJitter() {
        CardioLocationSample previous = sample(37.5665, 126.9780, 8f, 1_000L);

        CardioDistanceFilter.Result inaccurate = CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING,
                previous,
                sample(37.5666, 126.9780, 60f, 11_000L)
        );
        CardioDistanceFilter.Result jitter = CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING,
                previous,
                sample(37.566505, 126.9780, 8f, 11_000L)
        );

        assertFalse(inaccurate.accepted);
        assertEquals(CardioDistanceFilter.Reason.INACCURATE, inaccurate.reason);
        assertFalse(jitter.accepted);
        assertEquals(CardioDistanceFilter.Reason.JITTER, jitter.reason);
    }

    @Test
    public void acceptsPlausibleWalkingSegmentAndRejectsTeleport() {
        CardioLocationSample previous = sample(37.5665, 126.9780, 5f, 1_000L);

        CardioDistanceFilter.Result walking = CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING,
                previous,
                sample(37.5666, 126.9780, 5f, 11_000L)
        );
        CardioDistanceFilter.Result teleport = CardioDistanceFilter.evaluate(
                CardioActivityType.WALKING,
                previous,
                sample(37.5675, 126.9780, 5f, 2_000L)
        );

        assertTrue(walking.accepted);
        assertTrue(walking.segmentDistanceMeters > 10d);
        assertFalse(teleport.accepted);
        assertEquals(CardioDistanceFilter.Reason.TOO_FAST, teleport.reason);
    }

    private static CardioLocationSample sample(
            double latitude,
            double longitude,
            float accuracyMeters,
            long capturedAtMillis
    ) {
        return new CardioLocationSample(
                latitude,
                longitude,
                accuracyMeters,
                capturedAtMillis,
                null
        );
    }
}
