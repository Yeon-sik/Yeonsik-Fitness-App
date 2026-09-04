package com.yeonsik.fitnessapp.ui;

import java.util.List;
import java.util.Locale;

/** Small, pure display policies used by the records and analysis screens. */
public final class RecordsAnalysis {
    /** Two points only describe a difference; three points are the minimum trend view. */
    public static final int MIN_TREND_POINTS = 3;
    public static final int MAX_CALENDAR_MARKERS = 3;
    public static final double MIN_RANGE_PADDED_SPAN = 2d;

    private RecordsAnalysis() {
    }

    public enum TrendScalePolicy {
        ZERO_BASED,
        RANGE_PADDED
    }

    public enum TrendCurrentState {
        NONE,
        IN_PROGRESS,
        COMPLETED
    }

    public static boolean hasEnoughTrendPoints(int pointCount) {
        return pointCount >= MIN_TREND_POINTS;
    }

    public static boolean hasEnoughTrendPoints(
            int completedHistoryCount,
            TrendCurrentState currentState
    ) {
        return hasEnoughTrendPoints(trendPointCount(completedHistoryCount, currentState));
    }

    public static int trendPointCount(
            int completedHistoryCount,
            TrendCurrentState currentState
    ) {
        int safeHistoryCount = Math.max(0, completedHistoryCount);
        return safeHistoryCount
                + (currentState == TrendCurrentState.COMPLETED ? 1 : 0);
    }

    public static int requiredCompletedHistoryPoints(TrendCurrentState currentState) {
        return Math.max(
                0,
                MIN_TREND_POINTS
                        - (currentState == TrendCurrentState.COMPLETED ? 1 : 0)
        );
    }

    public static TrendRange trendRange(
            List<Double> values,
            TrendScalePolicy scalePolicy
    ) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        if (values != null) {
            for (Double value : values) {
                if (value == null || !Double.isFinite(value)) {
                    continue;
                }
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return new TrendRange(0d, 1d);
        }
        if (scalePolicy != TrendScalePolicy.RANGE_PADDED) {
            return new TrendRange(0d, Math.max(1d, max));
        }

        double rawSpan = Math.max(0d, max - min);
        double paddedSpan = Math.max(MIN_RANGE_PADDED_SPAN, rawSpan * 1.2d);
        double padding = (paddedSpan - rawSpan) / 2d;
        return new TrendRange(min - padding, max + padding);
    }

    public static String trendPeriodLabel(List<String> completedDates, String currentDate) {
        String first = null;
        String latest = null;
        if (completedDates != null) {
            for (String date : completedDates) {
                String normalized = normalizeDate(date);
                if (normalized == null) {
                    continue;
                }
                if (first == null || normalized.compareTo(first) < 0) {
                    first = normalized;
                }
                if (latest == null || normalized.compareTo(latest) > 0) {
                    latest = normalized;
                }
            }
        }
        String normalizedCurrentDate = normalizeDate(currentDate);
        if (normalizedCurrentDate != null) {
            if (first == null || normalizedCurrentDate.compareTo(first) < 0) {
                first = normalizedCurrentDate;
            }
            if (latest == null || normalizedCurrentDate.compareTo(latest) > 0) {
                latest = normalizedCurrentDate;
            }
        }
        if (first == null) {
            return "";
        }
        return first.equals(latest) ? first : first + " ~ " + latest;
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

    private static String normalizeDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        return date.trim();
    }

    public static final class TrendRange {
        public final double min;
        public final double max;

        private TrendRange(double min, double max) {
            this.min = min;
            this.max = max > min ? max : min + 1d;
        }

        public double span() {
            return max - min;
        }
    }
}
