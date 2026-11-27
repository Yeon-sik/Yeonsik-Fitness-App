package com.yeonsik.fitnessapp.core.ui;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Stable visual and pure presentation tokens shared by Compose and retained special renderers.
 *
 * <p>This class has no Activity/View dependency. The legacy {@code FitnessUi} factory keeps
 * compatibility aliases for the remaining illustration renderer, while application screens use
 * this neutral foundation directly.</p>
 */
public final class FitnessUiTokens {
    private FitnessUiTokens() {
    }

    // Fixed brand and activity coverage colors are shared across light and dark modes.
    public static final int COLOR_BRAND_BLUE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_BRAND_BLUE;
    public static final int COLOR_HOME_ACTIVITY_FULL_COVERAGE = 0xFF00FFFF;

    // Light semantic tokens
    public static final int COLOR_BACKGROUND = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_BACKGROUND;
    public static final int COLOR_SURFACE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_SURFACE;
    public static final int COLOR_SUBTLE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_SUBTLE;
    public static final int COLOR_TEXT = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_TEXT;
    public static final int COLOR_MUTED = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_MUTED;
    public static final int COLOR_TERTIARY = COLOR_MUTED;
    public static final int COLOR_BORDER = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_BORDER;
    // Retained names are compatibility aliases for the fixed brand color.
    public static final int COLOR_PASTEL_BLUE = COLOR_BRAND_BLUE;
    public static final int COLOR_BLUE_CONTAINER = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_BLUE_CONTAINER;
    public static final int COLOR_BLUE_INK = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_BLUE_INK;
    public static final int COLOR_PRIMARY = COLOR_BRAND_BLUE;
    public static final int COLOR_INVERSE_TEXT = 0xFFFFFFFF;
    public static final int COLOR_INVERSE_MUTED = 0xE6FFFFFF;
    public static final int COLOR_POSITIVE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_POSITIVE;
    public static final int COLOR_NEGATIVE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_NEGATIVE;
    public static final int COLOR_WARNING = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_WARNING;
    public static final int COLOR_CHART_CALORIES = 0xFF2F6F9F;
    public static final int COLOR_CHART_CARBS = 0xFF2B7A78;
    public static final int COLOR_CHART_PROTEIN = 0xFF8B5E3C;
    public static final int COLOR_CHART_FAT = 0xFF8A5A83;
    public static final int COLOR_RIPPLE_LIGHT = 0x18111827;
    public static final int COLOR_RIPPLE_DARK = 0x24F5F8FA;
    public static final int COLOR_BAR_MUTED = 0x38111827;
    public static final int COLOR_BAR_EMPTY = 0x16111827;
    public static final int COLOR_TRACK_LIGHT = 0x12111827;
    public static final int COLOR_TRACK_DARK = 0x2EF5F8FA;
    public static final int COLOR_INVERSE_CHIP = 0x1EFFFFFF;
    public static final int COLOR_INVERSE_LINE = 0x1AFFFFFF;

    // Dark semantic tokens
    public static final int COLOR_D_BACKGROUND = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_BACKGROUND;
    public static final int COLOR_D_SURFACE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_SURFACE;
    public static final int COLOR_D_SUBTLE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_SUBTLE;
    public static final int COLOR_D_TEXT = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_TEXT;
    public static final int COLOR_D_MUTED = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_MUTED;
    public static final int COLOR_D_TERTIARY = COLOR_D_MUTED;
    public static final int COLOR_D_BORDER = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_BORDER;
    public static final int COLOR_D_PASTEL_BLUE = COLOR_BRAND_BLUE;
    public static final int COLOR_D_BLUE_CONTAINER = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_BLUE_CONTAINER;
    public static final int COLOR_D_BLUE_INK = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_BLUE_INK;
    public static final int COLOR_D_ON_PASTEL_BLUE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_ON_PASTEL_BLUE;
    public static final int COLOR_D_HERO_END = 0xFF214A63;
    public static final int COLOR_D_HERO_MUTED = 0xFFAFBAC4;
    public static final int COLOR_D_HERO_BORDER = 0xFF2A526A;
    public static final int COLOR_D_POSITIVE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_POSITIVE;
    public static final int COLOR_D_NEGATIVE = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_NEGATIVE;
    public static final int COLOR_D_WARNING = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.COLOR_D_WARNING;
    public static final int COLOR_D_ACCENT = COLOR_D_PASTEL_BLUE;
    public static final int COLOR_D_ON_ACCENT_MUTED = 0xB80E2938;
    public static final int COLOR_D_CHIP_ON_ACCENT = 0x1E0E2938;
    public static final int COLOR_D_LINE_ON_ACCENT = 0x1E0E2938;
    public static final int COLOR_D_TRACK_ON_ACCENT = 0x300E2938;
    public static final int COLOR_D_BAR_MUTED = 0x78F5F8FA;
    public static final int COLOR_D_BAR_EMPTY = 0x1AF5F8FA;

    // Shape/depth tokens
    public static final int CARD_RADIUS_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.CARD_RADIUS_DP;
    public static final int HERO_RADIUS_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.HERO_RADIUS_DP;
    public static final int INPUT_RADIUS_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.INPUT_RADIUS_DP;
    public static final int BUTTON_RADIUS_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.BUTTON_RADIUS_DP;
    public static final int CHIP_RADIUS_DP = 999;
    public static final int SHEET_RADIUS_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.SHEET_RADIUS_DP;
    public static final int DEPTH_FLAT_DP = 0;
    public static final int DEPTH_SURFACE_DP = 1;
    public static final int DEPTH_EMPHASIS_DP = 3;

    // Layout/spacing tokens
    public static final int PAGE_HORIZONTAL_PADDING_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.PAGE_HORIZONTAL_PADDING_DP;
    public static final int PAGE_TOP_PADDING_DP = 20;
    public static final int PAGE_BOTTOM_PADDING_DP = 28;
    public static final int SCREEN_TITLE_TOP_SPACING_DP = 4;
    public static final int SCREEN_TITLE_BOTTOM_SPACING_DP = 18;
    public static final int SECTION_TOP_SPACING_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.SECTION_TOP_SPACING_DP;
    public static final int SECTION_BOTTOM_SPACING_DP = 10;
    public static final int CARD_GAP_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.CARD_GAP_DP;
    public static final int HERO_PADDING_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.HERO_PADDING_DP;
    public static final int HOME_ACTION_MIN_HEIGHT_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.HOME_ACTION_MIN_HEIGHT_DP;
    public static final int FIELD_LABEL_GAP_DP = 6;
    public static final int FORM_ITEM_GAP_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.FORM_ITEM_GAP_DP;
    public static final int NUTRITION_ROW_MIN_HEIGHT_DP = 56;
    public static final int NUTRITION_ROW_VERTICAL_PADDING_DP = 4;
    public static final int NUTRITION_VALUE_WIDTH_DP = 96;
    public static final int NUTRITION_UNIT_WIDTH_DP = 36;
    public static final int NUTRITION_LABEL_MAX_LINES = 2;
    public static final int NUTRITION_INPUT_HEIGHT_DP = 48;
    public static final int BUTTON_GAP_DP = 5;
    public static final int TREND_CHART_HEIGHT_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.TREND_CHART_HEIGHT_DP;
    public static final int NAV_ITEM_RADIUS_DP = 12;
    public static final int NAV_ITEM_MIN_HEIGHT_DP = com.yeonsik.fitness.shared.core.ui.FitnessComposeTokens.NAV_ITEM_MIN_HEIGHT_DP;
    public static final int NAV_BAR_HORIZONTAL_PADDING_DP = 8;
    public static final int NAV_BAR_TOP_PADDING_DP = 8;
    public static final int NAV_BAR_BOTTOM_PADDING_DP = 12;
    public static final int NAV_GLASS_BLUR_RADIUS_DP = 7;
    public static final int NAV_ITEM_GAP_DP = 4;
    public static final int NAV_MARKER_SLOT_HEIGHT_DP = 14;
    public static final int NAV_ACTIVE_MARKER_WIDTH_DP = 24;
    public static final int NAV_ACTIVE_MARKER_HEIGHT_DP = 4;
    public static final int NAV_PROGRESS_MARKER_SIZE_DP = 6;

    public static int pageBackground(boolean dark) {
        return dark ? COLOR_D_BACKGROUND : COLOR_BACKGROUND;
    }

    public static String trimDouble(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        if (Math.abs(value) < 0.05d) {
            value = 0d;
        }
        String result = String.format(Locale.ROOT, "%.1f", value);
        return result.endsWith(".0") ? result.substring(0, result.length() - 2) : result;
    }

    public static String formatVolume(double kg) {
        if (kg == Math.rint(kg)) {
            return String.format(Locale.KOREAN, "%,d", (long) kg);
        }
        return String.format(Locale.KOREAN, "%,.1f", kg);
    }

    public static String formatDuration(int durationSeconds) {
        if (durationSeconds <= 0) {
            return "미기록";
        }
        int hours = durationSeconds / 3600;
        int minutes = (durationSeconds % 3600) / 60;
        if (hours > 0) {
            return minutes > 0 ? hours + "시간 " + minutes + "분" : hours + "시간";
        }
        if (minutes > 0) {
            return minutes + "분";
        }
        return durationSeconds + "초";
    }

    public static String formatElapsed(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainder = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    public static String formatStartTime(String startedAt) {
        if (startedAt == null || startedAt.trim().isEmpty()) {
            return "미기록";
        }
        try {
            return OffsetDateTime.parse(startedAt.trim()).toLocalTime()
                    .format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception error) {
            return startedAt;
        }
    }

    public static String stripLeadingDate(String value) {
        int split = value.indexOf("  ");
        return split > 0 ? value.substring(split + 2) : value;
    }
}
