package com.yeonsik.fitnessapp.cardio;

import java.util.Locale;

/** 유산소 화면과 알림이 공유하는 거리·시간·페이스 표시 규칙. */
public final class CardioMetrics {
    private CardioMetrics() {
    }

    public static String formatDistanceKilometers(double distanceMeters) {
        return String.format(Locale.KOREA, "%.2f", Math.max(0d, distanceMeters) / 1000d);
    }

    public static String formatElapsed(int totalSeconds) {
        int safeSeconds = Math.max(0, totalSeconds);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        int seconds = safeSeconds % 60;
        return String.format(Locale.KOREA, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatAveragePace(int elapsedSeconds, double distanceMeters) {
        if (elapsedSeconds <= 0 || distanceMeters < 20d) {
            return "--:--";
        }
        int paceSeconds = (int) Math.round(elapsedSeconds / (distanceMeters / 1000d));
        int minutes = paceSeconds / 60;
        int seconds = paceSeconds % 60;
        return String.format(Locale.KOREA, "%d:%02d", minutes, seconds);
    }

    public static String formatAverageSpeed(int elapsedSeconds, double distanceMeters) {
        if (elapsedSeconds <= 0 || distanceMeters < 20d) {
            return "--";
        }
        double kilometersPerHour = (distanceMeters / 1000d) / (elapsedSeconds / 3600d);
        return String.format(Locale.KOREA, "%.1f", kilometersPerHour);
    }

    public static boolean isValidAverageHeartRate(Integer averageHeartRateBpm) {
        return averageHeartRateBpm == null || averageHeartRateBpm > 0;
    }

    public static boolean hasAverageHeartRate(Double averageHeartRateBpm) {
        return averageHeartRateBpm != null
                && Double.isFinite(averageHeartRateBpm)
                && averageHeartRateBpm > 0d;
    }

    public static String formatAverageHeartRate(Double averageHeartRateBpm) {
        if (!hasAverageHeartRate(averageHeartRateBpm)) {
            return "—";
        }
        if (averageHeartRateBpm == Math.rint(averageHeartRateBpm)) {
            return String.valueOf((long) Math.rint(averageHeartRateBpm));
        }
        return String.format(Locale.KOREA, "%.1f", averageHeartRateBpm);
    }

    public static String gpsStatusLabel(String gpsStatus) {
        if (CardioRepository.GPS_READY.equals(gpsStatus)) {
            return "GPS 연결됨";
        }
        if (CardioRepository.GPS_WEAK.equals(gpsStatus)) {
            return "GPS 신호 약함";
        }
        if (CardioRepository.GPS_PERMISSION_MISSING.equals(gpsStatus)) {
            return "정확한 위치 권한 필요";
        }
        if (CardioRepository.GPS_UNAVAILABLE.equals(gpsStatus)) {
            return "위치 신호 없음";
        }
        if (CardioRepository.GPS_STOPPED.equals(gpsStatus)) {
            return "GPS 일시정지";
        }
        return "GPS 찾는 중";
    }
}
