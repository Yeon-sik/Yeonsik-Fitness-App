package com.yeonsik.fitnessapp.cardio;

/** GPS 흔들림과 비현실적인 순간 이동을 거리 합산 전에 제거한다. */
public final class CardioDistanceFilter {
    public static final float MAX_ACCURACY_METERS = 35f;
    private static final double EARTH_RADIUS_METERS = 6_371_000d;
    private static final double MIN_SEGMENT_METERS = 3d;
    private static final double MAX_DYNAMIC_MIN_SEGMENT_METERS = 10d;

    private CardioDistanceFilter() {
    }

    public static Result evaluate(
            CardioActivityType activityType,
            CardioLocationSample previous,
            CardioLocationSample candidate
    ) {
        if (activityType == null || candidate == null || !validCoordinates(candidate)) {
            return Result.rejected(Reason.INVALID);
        }
        if (!Float.isFinite(candidate.accuracyMeters)
                || candidate.accuracyMeters <= 0
                || candidate.accuracyMeters > MAX_ACCURACY_METERS) {
            return Result.rejected(Reason.INACCURATE);
        }
        if (candidate.reportedSpeedMetersPerSecond != null
                && (!Float.isFinite(candidate.reportedSpeedMetersPerSecond)
                || candidate.reportedSpeedMetersPerSecond < 0
                || candidate.reportedSpeedMetersPerSecond
                > activityType.maxPlausibleSpeedMetersPerSecond() * 1.25d)) {
            return Result.rejected(Reason.TOO_FAST);
        }
        if (previous == null) {
            return Result.accepted(0d);
        }
        if (candidate.capturedAtMillis <= previous.capturedAtMillis) {
            return Result.rejected(Reason.STALE);
        }

        double segmentMeters = haversineMeters(previous, candidate);
        double dynamicMinimum = Math.min(
                MAX_DYNAMIC_MIN_SEGMENT_METERS,
                Math.max(MIN_SEGMENT_METERS,
                        (previous.accuracyMeters + candidate.accuracyMeters) * 0.25d)
        );
        if (segmentMeters < dynamicMinimum) {
            return Result.rejected(Reason.JITTER);
        }

        double elapsedSeconds = (candidate.capturedAtMillis - previous.capturedAtMillis) / 1000d;
        double derivedSpeed = segmentMeters / elapsedSeconds;
        if (!Double.isFinite(derivedSpeed)
                || derivedSpeed > activityType.maxPlausibleSpeedMetersPerSecond()) {
            return Result.rejected(Reason.TOO_FAST);
        }
        return Result.accepted(segmentMeters);
    }

    static double haversineMeters(CardioLocationSample first, CardioLocationSample second) {
        double firstLat = Math.toRadians(first.latitude);
        double secondLat = Math.toRadians(second.latitude);
        double latDelta = secondLat - firstLat;
        double lonDelta = Math.toRadians(second.longitude - first.longitude);
        double sinLat = Math.sin(latDelta / 2d);
        double sinLon = Math.sin(lonDelta / 2d);
        double a = sinLat * sinLat
                + Math.cos(firstLat) * Math.cos(secondLat) * sinLon * sinLon;
        return EARTH_RADIUS_METERS * 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
    }

    private static boolean validCoordinates(CardioLocationSample sample) {
        return Double.isFinite(sample.latitude)
                && Double.isFinite(sample.longitude)
                && sample.latitude >= -90d
                && sample.latitude <= 90d
                && sample.longitude >= -180d
                && sample.longitude <= 180d
                && sample.capturedAtMillis != null
                && sample.capturedAtMillis > 0;
    }

    public enum Reason {
        ACCEPTED,
        INVALID,
        INACCURATE,
        STALE,
        JITTER,
        TOO_FAST
    }

    public static final class Result {
        public final boolean accepted;
        public final double segmentDistanceMeters;
        public final Reason reason;

        private Result(boolean accepted, double segmentDistanceMeters, Reason reason) {
            this.accepted = accepted;
            this.segmentDistanceMeters = segmentDistanceMeters;
            this.reason = reason;
        }

        static Result accepted(double segmentDistanceMeters) {
            return new Result(true, segmentDistanceMeters, Reason.ACCEPTED);
        }

        static Result rejected(Reason reason) {
            return new Result(false, 0d, reason);
        }
    }
}
