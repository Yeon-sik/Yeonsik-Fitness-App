package com.yeonsik.fitnessapp.data;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Rules shared by meal recording and presentation. */
public final class MealEntryPolicy {
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
}
