package com.yeonsik.fitnessapp.ui;

import java.util.Locale;

/** Pure summary calculations shared by the renderer and the workout summary screen. */
public final class WorkoutSummaryAnalytics {
    private static final double EFFECTIVE_SET_SATURATION = 4.0d;

    private WorkoutSummaryAnalytics() {
    }

    /** Saturates absolute effective sets so equal workloads have comparable intensity. */
    public static double intensityForEffectiveSets(double effectiveSets) {
        if (!Double.isFinite(effectiveSets) || effectiveSets <= 0d) {
            return 0d;
        }
        return Math.min(1d, 1d - Math.exp(-effectiveSets / EFFECTIVE_SET_SATURATION));
    }

    public static String formatChangePercent(double previous, double current) {
        if (!Double.isFinite(previous) || !Double.isFinite(current)) {
            return "—";
        }
        if (previous == 0d) {
            return current == 0d ? "—" : "신규";
        }
        double percentage = Math.round(
                (current - previous) / Math.abs(previous) * 1000d
        ) / 10d;
        String number = percentage == Math.rint(percentage)
                ? String.valueOf((long) percentage)
                : String.format(Locale.ROOT, "%.1f", percentage);
        return (percentage >= 0d ? "+" : "") + number + "%";
    }
}
