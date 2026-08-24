package com.yeonsik.fitnessapp.cardio;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class CardioRouteProjectionTest {
    @Test
    public void capsDisplayPointsWhileKeepingRouteEndpoints() {
        CardioRouteProjection.Builder builder =
                CardioRouteProjection.builder(10, 4);
        for (int index = 0; index < 10; index++) {
            builder.add(index * 1_000L, 37.0 + index, 127.0 + index);
        }

        CardioRouteProjection projection = builder.build();

        assertEquals(10, projection.rawPointCount());
        assertEquals(4, projection.displayPointCount());
        List<CardioRouteProjection.RoutePoint> segment =
                projection.segments().get(0);
        assertEquals(37.0, segment.get(0).latitude, 0.0);
        assertEquals(46.0, segment.get(segment.size() - 1).latitude, 0.0);
    }

    @Test
    public void splitsLongGpsGapsIntoSeparateSegments() {
        CardioRouteProjection.Builder builder =
                CardioRouteProjection.builder(4, 800);
        builder.add(0L, 37.0, 127.0);
        builder.add(1_000L, 37.001, 127.001);
        builder.add(20_000L, 37.1, 127.1);
        builder.add(21_000L, 37.101, 127.101);

        CardioRouteProjection projection = builder.build();

        assertEquals(2, projection.segments().size());
        assertEquals(2, projection.segments().get(0).size());
        assertEquals(2, projection.segments().get(1).size());
        assertTrue(projection.hasRenderablePath());
    }

    @Test
    public void emptyProjectionHasNoRenderablePath() {
        CardioRouteProjection projection = CardioRouteProjection.empty();

        assertEquals(0, projection.rawPointCount());
        assertEquals(0, projection.displayPointCount());
        assertTrue(!projection.hasRenderablePath());
    }
}
