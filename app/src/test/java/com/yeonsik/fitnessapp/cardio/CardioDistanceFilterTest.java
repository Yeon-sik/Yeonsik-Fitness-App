package com.yeonsik.fitnessapp.cardio;

import org.junit.Test;
import com.yeonsik.fitness.shared.feature.cardio.model.CardioActivityType;
import com.yeonsik.fitness.shared.feature.cardio.model.CardioDistanceFilter;
import com.yeonsik.fitness.shared.feature.cardio.model.CardioLocationSample;

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

        assertTrue(result.getAccepted());
        assertEquals(0d, result.getSegmentDistanceMeters(), 0.001d);
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

        assertFalse(inaccurate.getAccepted());
        assertEquals(CardioDistanceFilter.Reason.INACCURATE, inaccurate.getReason());
        assertFalse(jitter.getAccepted());
        assertEquals(CardioDistanceFilter.Reason.JITTER, jitter.getReason());
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

        assertTrue(walking.getAccepted());
        assertTrue(walking.getSegmentDistanceMeters() > 10d);
        assertFalse(teleport.getAccepted());
        assertEquals(CardioDistanceFilter.Reason.TOO_FAST, teleport.getReason());
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
