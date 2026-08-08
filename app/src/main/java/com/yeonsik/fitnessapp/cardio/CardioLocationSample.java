package com.yeonsik.fitnessapp.cardio;

/** Android Location에서 거리 계산에 필요한 값만 분리한 불변 샘플. */
public final class CardioLocationSample {
    public final double latitude;
    public final double longitude;
    public final float accuracyMeters;
    public final Long capturedAtMillis;
    public final Float reportedSpeedMetersPerSecond;

    public CardioLocationSample(
            double latitude,
            double longitude,
            float accuracyMeters,
            long capturedAtMillis,
            Float reportedSpeedMetersPerSecond
    ) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.capturedAtMillis = capturedAtMillis;
        this.reportedSpeedMetersPerSecond = reportedSpeedMetersPerSecond;
    }
}
