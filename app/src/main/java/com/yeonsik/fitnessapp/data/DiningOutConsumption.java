package com.yeonsik.fitnessapp.data;

/**
 * One diner's share of a whole dining-out menu snapshot.
 *
 * <p>The diner count is only the defaulting context. The canonical calculation value is
 * {@link #consumedFraction}, which is the fraction of the whole ordered menu consumed by this
 * user. This keeps a menu label such as "2인" separate from the user's actual intake.</p>
 */
public final class DiningOutConsumption {
    public static final String CONTRACT_VERSION = "dining-out-sharing.v1";
    public static final String CONSUMER_SCOPE_SELF = "self";
    public static final String SHARE_METHOD_EQUAL_BY_DINERS = "equal_by_diners";
    public static final String SHARE_METHOD_MANUAL = "manual";
    public static final String CONFIDENCE_ESTIMATED = "estimated";

    public final int dinerCount;
    public final double consumedFraction;
    public final String shareMethod;
    public final String confidence;

    private DiningOutConsumption(
            int dinerCount,
            double consumedFraction,
            String shareMethod,
            String confidence
    ) {
        this.dinerCount = requireDinerCount(dinerCount);
        this.consumedFraction = requireFraction(consumedFraction);
        this.shareMethod = requireShareMethod(shareMethod);
        this.confidence = confidence == null || confidence.trim().isEmpty()
                ? CONFIDENCE_ESTIMATED
                : confidence.trim();
    }

    /** Uses equal splitting only as the input default, never as a permanent denominator. */
    public static DiningOutConsumption equalByDiners(int dinerCount) {
        int normalizedDinerCount = requireDinerCount(dinerCount);
        return new DiningOutConsumption(
                normalizedDinerCount,
                1d / normalizedDinerCount,
                SHARE_METHOD_EQUAL_BY_DINERS,
                CONFIDENCE_ESTIMATED
        );
    }

    /** Stores a manually corrected fraction of the whole ordered menu. */
    public static DiningOutConsumption manual(int dinerCount, double consumedFraction) {
        return new DiningOutConsumption(
                dinerCount,
                consumedFraction,
                SHARE_METHOD_MANUAL,
                CONFIDENCE_ESTIMATED
        );
    }

    /** Resolves a blank UI share as an equal split and a supplied value as a manual correction. */
    public static DiningOutConsumption resolve(int dinerCount, Double consumedFraction) {
        return consumedFraction == null
                ? equalByDiners(dinerCount)
                : manual(dinerCount, consumedFraction);
    }

    private static int requireDinerCount(int value) {
        if (value < 1 || value > 100) {
            throw new IllegalArgumentException("함께 먹은 인원은 1명 이상 100명 이하로 입력하세요.");
        }
        return value;
    }

    private static double requireFraction(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException("내 섭취 비율은 0보다 크고 100% 이하여야 합니다.");
        }
        return value;
    }

    private static String requireShareMethod(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!SHARE_METHOD_EQUAL_BY_DINERS.equals(normalized)
                && !SHARE_METHOD_MANUAL.equals(normalized)) {
            throw new IllegalArgumentException("외식 섭취 분배 방식이 올바르지 않습니다.");
        }
        return normalized;
    }
}
