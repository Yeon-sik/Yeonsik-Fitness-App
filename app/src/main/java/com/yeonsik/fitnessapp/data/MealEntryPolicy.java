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
    private static final String GOHYANG_MAMAS_HAND_KEYWORD = "고향엄마손";
    private static final String KALGUKSU_KEYWORD = "칼국수";
    private static final String GOHYANG_MAMAS_HAND_DEFAULT_BRANCH = "영등포점";

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

    /** External meals keep the store and the consumed menu as separate display snapshots. */
    public static String previewDiningOutTitle(String storeName, String menuName) {
        return previewDiningOutTitle(storeName, "", menuName);
    }

    public static String previewDiningOutTitle(
            String storeName,
            String branchName,
            String menuName
    ) {
        String store = isMissingText(storeName) ? "" : normalizedText(storeName);
        String branch = isMissingText(branchName) ? "" : normalizedText(branchName);
        String menu = isMissingText(menuName) ? "" : normalizedText(menuName);
        if (store.isEmpty()) {
            store = "가게 미기록";
        }
        if (menu.isEmpty()) {
            menu = "메뉴 미기록";
        }
        return branch.isEmpty()
                ? store + " · " + menu
                : store + " · " + branch + " · " + menu;
    }

    /**
     * Resolves a local dining-out branch label without creating or replacing a cross-app
     * identity. Existing restaurant and location IDs remain whatever was stored.
     */
    public static String resolveDiningOutBranchName(String storeName, String branchName) {
        String explicit = normalizedText(branchName);
        if (!isMissingText(explicit)) {
            return explicit;
        }
        return defaultDiningOutBranchName(storeName);
    }

    /** Returns a known local branch default, or an empty string when no default is known. */
    public static String defaultDiningOutBranchName(String storeName) {
        String normalizedStore = normalizedText(storeName).replaceAll("\\s+", "");
        if (normalizedStore.contains(GOHYANG_MAMAS_HAND_KEYWORD)
                && normalizedStore.contains(KALGUKSU_KEYWORD)) {
            return GOHYANG_MAMAS_HAND_DEFAULT_BRANCH;
        }
        return "";
    }

    /** Null database/JSON values must not become the literal UI text "null". */
    public static boolean isMissingText(String value) {
        String normalized = normalizedText(value);
        return normalized.isEmpty() || "null".equalsIgnoreCase(normalized);
    }

    public static String requireDiningOutStoreName(String value) {
        return requireDiningOutText(value, "가게 명");
    }

    public static String requireDiningOutMenuName(String value) {
        return requireDiningOutText(value, "먹은 메뉴");
    }

    /** Parses one optional macro estimate entered for a dining-out record. */
    public static Double optionalDiningOutMacro(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        final double parsed;
        try {
            parsed = Double.parseDouble(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(label + "은 숫자로 입력하세요.");
        }
        requireNonNegativeDiningOutMacro(parsed, label);
        return parsed;
    }

    /** Parses a required macro value entered for a dining-out record. */
    public static Double requireDiningOutMacro(String value, String label) {
        Double parsed = optionalDiningOutMacro(value, label);
        if (parsed == null) {
            throw new IllegalArgumentException(label + "은 필수 입력입니다.");
        }
        return parsed;
    }

    /** Parses the optional whole-kcal estimate entered for a dining-out record. */
    public static Integer optionalDiningOutCalories(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        final int parsed;
        try {
            parsed = Integer.parseInt(normalized);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("칼로리는 0 이상인 정수로 입력하세요.");
        }
        if (parsed < 0) {
            throw new IllegalArgumentException("칼로리는 0 이상인 정수로 입력하세요.");
        }
        return parsed;
    }

    /** Parses a required whole-kcal value for a dining-out menu. */
    public static int requireDiningOutCaloriesInput(String value) {
        Integer parsed = optionalDiningOutCalories(value);
        if (parsed == null) {
            throw new IllegalArgumentException("칼로리는 필수 입력입니다.");
        }
        return parsed;
    }

    /** Validates a dining-out menu: calories and macros are required; extended values are optional. */
    public static void requireDiningOutMenuNutrition(
            Number calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        if (calories == null) {
            throw new IllegalArgumentException("칼로리는 필수 입력입니다.");
        }
        double calorieValue = calories.doubleValue();
        if (Double.isNaN(calorieValue) || Double.isInfinite(calorieValue)
                || calorieValue < 0d) {
            throw new IllegalArgumentException("칼로리는 0 이상인 숫자로 입력하세요.");
        }
        if (proteinGrams == null || carbsGrams == null || fatGrams == null) {
            throw new IllegalArgumentException(
                    "외식 메뉴의 탄수화물·단백질·지방은 모두 입력하세요."
            );
        }
        requireNonNegativeDiningOutMacro(proteinGrams, "단백질");
        requireNonNegativeDiningOutMacro(carbsGrams, "탄수화물");
        requireNonNegativeDiningOutMacro(fatGrams, "지방");
        if (sodiumMg != null) {
            requireNonNegativeDiningOutMacro(sodiumMg, "나트륨");
        }
        if (sugarsGrams != null) {
            requireNonNegativeDiningOutMacro(sugarsGrams, "당류");
        }
        if (saturatedFatGrams != null) {
            requireNonNegativeDiningOutMacro(saturatedFatGrams, "포화지방");
        }
    }

    /** Ensures estimates are either omitted or complete and non-negative. */
    public static void requireDiningOutEstimatedMacros(
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        boolean hasAny = carbsGrams != null || proteinGrams != null || fatGrams != null;
        if (!hasAny) {
            return;
        }
        if (carbsGrams == null || proteinGrams == null || fatGrams == null) {
            throw new IllegalArgumentException(
                    "외식 추정 영양값은 탄수화물·단백질·지방을 모두 입력하세요."
            );
        }
        requireNonNegativeDiningOutMacro(carbsGrams, "탄수화물");
        requireNonNegativeDiningOutMacro(proteinGrams, "단백질");
        requireNonNegativeDiningOutMacro(fatGrams, "지방");
    }

    public static boolean hasDiningOutEstimatedMacros(
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        return carbsGrams != null || proteinGrams != null || fatGrams != null;
    }

    /** Ensures a complete dining-out nutrition estimate is either omitted or fully present. */
    public static void requireDiningOutEstimatedNutrition(
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        boolean hasAny = calories != null
                || proteinGrams != null
                || carbsGrams != null
                || fatGrams != null
                || sodiumMg != null
                || sugarsGrams != null
                || saturatedFatGrams != null;
        if (!hasAny) {
            return;
        }
        if (calories == null || proteinGrams == null || carbsGrams == null
                || fatGrams == null || sodiumMg == null || sugarsGrams == null
                || saturatedFatGrams == null) {
            throw new IllegalArgumentException(
                    "외식 추정 영양값은 칼로리·탄수화물·단백질·지방·나트륨·당류·포화지방을 모두 입력하세요."
            );
        }
        if (calories < 0) {
            throw new IllegalArgumentException("칼로리는 0 이상인 숫자로 입력하세요.");
        }
        requireNonNegativeDiningOutMacro(proteinGrams, "단백질");
        requireNonNegativeDiningOutMacro(carbsGrams, "탄수화물");
        requireNonNegativeDiningOutMacro(fatGrams, "지방");
        requireNonNegativeDiningOutMacro(sodiumMg, "나트륨");
        requireNonNegativeDiningOutMacro(sugarsGrams, "당류");
        requireNonNegativeDiningOutMacro(saturatedFatGrams, "포화지방");
    }

    public static boolean hasDiningOutEstimatedNutrition(
            Integer calories,
            Double proteinGrams,
            Double carbsGrams,
            Double fatGrams,
            Double sodiumMg,
            Double sugarsGrams,
            Double saturatedFatGrams
    ) {
        return calories != null
                || proteinGrams != null
                || carbsGrams != null
                || fatGrams != null
                || sodiumMg != null
                || sugarsGrams != null
                || saturatedFatGrams != null;
    }

    /** Calculates estimated energy using the standard 4/4/9 macro conversion. */
    public static int estimatedDiningOutCalories(
            Double carbsGrams,
            Double proteinGrams,
            Double fatGrams
    ) {
        requireDiningOutEstimatedMacros(carbsGrams, proteinGrams, fatGrams);
        if (!hasDiningOutEstimatedMacros(carbsGrams, proteinGrams, fatGrams)) {
            return 0;
        }
        double calories = carbsGrams * 4d + proteinGrams * 4d + fatGrams * 9d;
        if (calories > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("외식 추정 영양값이 너무 큽니다.");
        }
        return (int) Math.round(calories);
    }

    private static String requireDiningOutText(String value, String label) {
        String normalized = normalizedText(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + "을 입력하세요.");
        }
        return normalized;
    }

    private static void requireNonNegativeDiningOutMacro(double value, String label) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0d) {
            throw new IllegalArgumentException(label + "은 0 이상인 숫자로 입력하세요.");
        }
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
