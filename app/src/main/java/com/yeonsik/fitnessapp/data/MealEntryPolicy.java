package com.yeonsik.fitnessapp.data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** Rules shared by meal recording and presentation. */
public final class MealEntryPolicy {
    private static final DateTimeFormatter TIME_INPUT_FORMAT =
            DateTimeFormatter.ofPattern("H:mm", Locale.ROOT);
    private static final DateTimeFormatter TIME_DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private MealEntryPolicy() {
    }

    /** Meal labels are derived from their zero-based order and have no fixed upper bound. */
    public static String labelForIndex(int zeroBasedIndex) {
        if (zeroBasedIndex < 0) {
            throw new IllegalArgumentException("식사 순서는 0 이상이어야 합니다.");
        }
        return ((long) zeroBasedIndex + 1L) + "끼";
    }

    /** Empty input means today; future dates are not valid meal-record dates. */
    public static LocalDate requireRecordDate(String isoDate, LocalDate today) {
        if (today == null) {
            throw new IllegalArgumentException("오늘 날짜가 필요합니다.");
        }
        String normalized = isoDate == null ? "" : isoDate.trim();
        LocalDate recordDate;
        try {
            recordDate = normalized.isEmpty() ? today : LocalDate.parse(normalized);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("식사 날짜 형식이 올바르지 않습니다.");
        }
        if (recordDate.isAfter(today)) {
            throw new IllegalArgumentException("미래 날짜에는 식사를 기록할 수 없습니다.");
        }
        return recordDate;
    }

    public static boolean isBackfilled(LocalDate recordDate, LocalDate today) {
        return recordDate != null && today != null && recordDate.isBefore(today);
    }

    /** User-entered meal time normalized to the 24-hour HH:mm display contract. */
    public static String requireMealTime(String value) {
        String normalized = value == null ? "" : value.trim();
        try {
            return LocalTime.parse(normalized, TIME_INPUT_FORMAT).format(TIME_DISPLAY_FORMAT);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("끼니 시간을 HH:mm 형식으로 입력하세요.");
        }
    }

    /** Combines the selected record date and local time into a sync-safe offset timestamp. */
    public static String eatenAt(LocalDate recordDate, String mealTime, ZoneId zoneId) {
        if (recordDate == null || zoneId == null) {
            throw new IllegalArgumentException("끼니 날짜와 시간대가 필요합니다.");
        }
        LocalTime time = LocalTime.parse(requireMealTime(mealTime), TIME_DISPLAY_FORMAT);
        return ZonedDateTime.of(recordDate, time, zoneId)
                .withSecond(0)
                .withNano(0)
                .toOffsetDateTime()
                .toString();
    }

    /** Existing rows without eaten_at remain explicit instead of borrowing their save time. */
    public static String displayMealTime(String eatenAt) {
        String normalized = eatenAt == null ? "" : eatenAt.trim();
        if (normalized.isEmpty()) {
            return "시간 미기록";
        }
        try {
            return OffsetDateTime.parse(normalized).toLocalTime().format(TIME_DISPLAY_FORMAT);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalTime.parse(normalized, TIME_INPUT_FORMAT).format(TIME_DISPLAY_FORMAT);
            } catch (DateTimeParseException error) {
                return "시간 미기록";
            }
        }
    }

    /** First recorded food plus the number of additional snapshot rows. */
    public static String previewTitle(String firstFoodName, int itemCount, String legacyMenu) {
        String first = normalizedText(firstFoodName);
        if (!first.isEmpty()) {
            return itemCount > 1 ? first + " 외 " + (itemCount - 1) + "건" : first;
        }
        String fallback = normalizedText(legacyMenu);
        return fallback.isEmpty() ? "직접 입력 끼니" : fallback;
    }

    /** Macro energy ratio: carbohydrates 4 kcal/g, protein 4 kcal/g, fat 9 kcal/g. */
    public static String macroRatioLabel(Double carbsGrams, Double proteinGrams, Double fatGrams) {
        int[] ratios = macroRatios(carbsGrams, proteinGrams, fatGrams);
        if (ratios == null) {
            return "탄·단·지 비율 없음";
        }
        return "탄 " + ratios[0] + "% · 단 " + ratios[1] + "% · 지 " + ratios[2] + "%";
    }

    public static String macroRatioAccessibilityLabel(
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        int[] ratios = macroRatios(carbsGrams, proteinGrams, fatGrams);
        if (ratios == null) {
            return "탄수화물, 단백질, 지방 비율 없음";
        }
        return "탄수화물 " + ratios[0] + "퍼센트, 단백질 " + ratios[1]
                + "퍼센트, 지방 " + ratios[2] + "퍼센트";
    }

    private static int[] macroRatios(Double carbsGrams, Double proteinGrams, Double fatGrams) {
        if (carbsGrams == null || proteinGrams == null || fatGrams == null) {
            return null;
        }
        double[] calories = new double[]{
                Math.max(0d, carbsGrams) * 4d,
                Math.max(0d, proteinGrams) * 4d,
                Math.max(0d, fatGrams) * 9d
        };
        double total = calories[0] + calories[1] + calories[2];
        if (total <= 0d) {
            return null;
        }

        int[] ratios = new int[3];
        double[] remainders = new double[3];
        int assigned = 0;
        for (int index = 0; index < calories.length; index++) {
            double exact = calories[index] * 100d / total;
            ratios[index] = (int) Math.floor(exact);
            remainders[index] = exact - ratios[index];
            assigned += ratios[index];
        }
        for (int point = assigned; point < 100; point++) {
            int winner = 0;
            for (int index = 1; index < remainders.length; index++) {
                if (remainders[index] > remainders[winner]) {
                    winner = index;
                }
            }
            ratios[winner]++;
            remainders[winner] = -1d;
        }
        return ratios;
    }

    private static String normalizedText(String value) {
        return value == null ? "" : value.trim();
    }
}
