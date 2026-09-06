package com.yeonsik.fitnessapp.data;

/** Body-owned read model for one visible weight record. */
public final class BodyMetricEntry {
    public final String id;
    public final String date;
    public final double weightKg;
    public final String memo;

    public BodyMetricEntry(String id, String date, double weightKg, String memo) {
        this.id = id;
        this.date = date;
        this.weightKg = weightKg;
        this.memo = memo;
    }
}
