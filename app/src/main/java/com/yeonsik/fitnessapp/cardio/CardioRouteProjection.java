package com.yeonsik.fitnessapp.cardio;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 지도에 표시할 유산소 경로의 제한된 투영본이다.
 *
 * <p>원본 GPS 좌표는 SQLite에 남기고, 화면에는 최대 개수로 줄인 좌표만 전달한다.
 * 일시정지 또는 긴 GPS 공백은 서로 다른 선분으로 분리해 잘못된 직선 연결을 막는다.</p>
 */
public final class CardioRouteProjection {
    public static final int DEFAULT_MAX_DISPLAY_POINTS = 800;
    public static final long SEGMENT_GAP_MILLIS = 10_000L;

    private final List<List<RoutePoint>> segments;
    private final int rawPointCount;
    private final int displayPointCount;

    private CardioRouteProjection(
            List<List<RoutePoint>> segments,
            int rawPointCount,
            int displayPointCount
    ) {
        List<List<RoutePoint>> immutableSegments = new ArrayList<>();
        for (List<RoutePoint> segment : segments) {
            if (!segment.isEmpty()) {
                immutableSegments.add(Collections.unmodifiableList(new ArrayList<>(segment)));
            }
        }
        this.segments = Collections.unmodifiableList(immutableSegments);
        this.rawPointCount = rawPointCount;
        this.displayPointCount = displayPointCount;
    }

    public static CardioRouteProjection empty() {
        return new CardioRouteProjection(Collections.emptyList(), 0, 0);
    }

    public static Builder builder(int rawPointCount, int maxDisplayPoints) {
        return new Builder(rawPointCount, maxDisplayPoints);
    }

    public List<List<RoutePoint>> segments() {
        return segments;
    }

    public int rawPointCount() {
        return rawPointCount;
    }

    public int displayPointCount() {
        return displayPointCount;
    }

    public boolean hasRenderablePath() {
        for (List<RoutePoint> segment : segments) {
            if (segment.size() >= 2) {
                return true;
            }
        }
        return false;
    }

    public static final class Builder {
        private final int expectedRawPointCount;
        private final int maxDisplayPoints;
        private final int targetDisplayPointCount;
        private final List<List<RoutePoint>> segments = new ArrayList<>();
        private List<RoutePoint> currentSegment;
        private long previousCapturedAtMillis;
        private boolean hasPrevious;
        private int rawPointCount;
        private int displayPointCount;
        private int nextSampleOrdinal;

        private Builder(int rawPointCount, int maxDisplayPoints) {
            expectedRawPointCount = Math.max(0, rawPointCount);
            this.maxDisplayPoints = Math.max(1, maxDisplayPoints);
            targetDisplayPointCount = Math.min(
                    expectedRawPointCount,
                    this.maxDisplayPoints
            );
        }

        public Builder add(
                long capturedAtMillis,
                double latitude,
                double longitude
        ) {
            boolean gap = hasPrevious
                    && capturedAtMillis - previousCapturedAtMillis > SEGMENT_GAP_MILLIS;
            if (gap) {
                currentSegment = null;
            }

            if (displayPointCount < maxDisplayPoints && shouldSample(rawPointCount)) {
                if (currentSegment == null) {
                    currentSegment = new ArrayList<>();
                    segments.add(currentSegment);
                }
                currentSegment.add(new RoutePoint(
                        capturedAtMillis,
                        latitude,
                        longitude
                ));
                displayPointCount++;
            }

            previousCapturedAtMillis = capturedAtMillis;
            hasPrevious = true;
            rawPointCount++;
            return this;
        }

        private boolean shouldSample(int inputIndex) {
            if (targetDisplayPointCount == 0) {
                return false;
            }
            if (expectedRawPointCount <= maxDisplayPoints) {
                return true;
            }
            if (targetDisplayPointCount == 1) {
                return inputIndex == expectedRawPointCount - 1;
            }
            if (nextSampleOrdinal >= targetDisplayPointCount) {
                return false;
            }

            int targetIndex = sampleIndex(nextSampleOrdinal);
            while (nextSampleOrdinal < targetDisplayPointCount
                    && inputIndex >= targetIndex) {
                nextSampleOrdinal++;
                if (inputIndex == targetIndex) {
                    return true;
                }
                if (nextSampleOrdinal >= targetDisplayPointCount) {
                    return false;
                }
                targetIndex = sampleIndex(nextSampleOrdinal);
            }
            return false;
        }

        private int sampleIndex(int ordinal) {
            return (int) Math.round(
                    (double) ordinal * (expectedRawPointCount - 1)
                            / (targetDisplayPointCount - 1)
            );
        }

        public CardioRouteProjection build() {
            return new CardioRouteProjection(
                    segments,
                    rawPointCount,
                    displayPointCount
            );
        }
    }

    public static final class RoutePoint {
        public final long capturedAtMillis;
        public final double latitude;
        public final double longitude;

        RoutePoint(long capturedAtMillis, double latitude, double longitude) {
            this.capturedAtMillis = capturedAtMillis;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
