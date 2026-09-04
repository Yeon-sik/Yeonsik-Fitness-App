package com.yeonsik.fitnessapp.ui;

import java.util.Locale;

/** Small, pure display policies used by the records and analysis screens. */
public final class RecordsAnalysis {
    /** Two points only describe a difference; three points are the minimum trend view. */
    public static final int MIN_TREND_POINTS = 3;
    public static final int MAX_CALENDAR_MARKERS = 3;

    private RecordsAnalysis() {
    }

    public static boolean hasEnoughTrendPoints(int pointCount) {
        return pointCount >= MIN_TREND_POINTS;
    }

    public static int markerCount(
            boolean hasWorkout,
            boolean hasWeight,
            boolean hasMeal
    ) {
        int count = 0;
        if (hasWorkout) {
            count += 1;
        }
        if (hasWeight) {
            count += 1;
        }
        if (hasMeal) {
            count += 1;
        }
        return Math.min(MAX_CALENDAR_MARKERS, count);
    }

    public static String formatSignedDelta(double first, double latest, String unit) {
        if (!Double.isFinite(first) || !Double.isFinite(latest)) {
            return "미계산";
        }
        double delta = latest - first;
        String number = delta == Math.rint(delta)
                ? String.valueOf((long) delta)
                : String.format(Locale.ROOT, "%.1f", delta);
        String suffix = unit == null ? "" : unit;
        return (delta > 0d ? "+" : "") + number + suffix;
    }
}
