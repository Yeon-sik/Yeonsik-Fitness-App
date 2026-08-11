package com.yeonsik.fitnessapp.development;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DevelopmentGoal {
    public static final String OBJECTIVE_MUSCLE_GAIN = "muscle_gain";
    public static final String OBJECTIVE_STRENGTH = "strength";
    public static final String OBJECTIVE_FAT_LOSS = "fat_loss";
    public static final String OBJECTIVE_ENDURANCE = "endurance";
    public static final String OBJECTIVE_MAINTENANCE = "maintenance";

    public static final String BODY_PART_CHEST = "chest";
    public static final String BODY_PART_BACK = "back";
    public static final String BODY_PART_LEGS = "legs";
    public static final String BODY_PART_SHOULDERS = "shoulders";
    public static final String BODY_PART_ARMS = "arms";
    public static final String BODY_PART_ABS = "abs";

    public static final List<String> OBJECTIVES = Collections.unmodifiableList(Arrays.asList(
            OBJECTIVE_MUSCLE_GAIN,
            OBJECTIVE_STRENGTH,
            OBJECTIVE_FAT_LOSS,
            OBJECTIVE_ENDURANCE,
            OBJECTIVE_MAINTENANCE
    ));

    public static final List<String> FOCUS_BODY_PARTS = Collections.unmodifiableList(Arrays.asList(
            BODY_PART_CHEST,
            BODY_PART_BACK,
            BODY_PART_LEGS,
            BODY_PART_SHOULDERS,
            BODY_PART_ARMS,
            BODY_PART_ABS
    ));

    public final String objective;
    public final Integer weeklySessionsTarget;
    public final String focusBodyPart;
    public final String effectiveFrom;
    public final String createdAt;
    public final String updatedAt;

    public DevelopmentGoal(
            String objective,
            Integer weeklySessionsTarget,
            String focusBodyPart,
            String effectiveFrom,
            String createdAt,
            String updatedAt
    ) {
        String normalizedObjective = normalizeObjective(objective);
        Integer normalizedTarget = weeklySessionsTarget;
        String normalizedFocus = normalizeFocusBodyPart(focusBodyPart);
        String normalizedDate = normalizeDate(effectiveFrom);
        boolean hasAnyValue = normalizedObjective != null
                || normalizedTarget != null
                || normalizedFocus != null
                || normalizedDate != null;
        if (!hasAnyValue) {
            this.objective = null;
            this.weeklySessionsTarget = null;
            this.focusBodyPart = null;
            this.effectiveFrom = null;
        } else {
            this.objective = requireObjective(normalizedObjective);
            this.weeklySessionsTarget = requireWeeklySessionsTarget(normalizedTarget);
            this.focusBodyPart = requireFocusBodyPart(normalizedFocus);
            this.effectiveFrom = requireDate(normalizedDate);
        }
        this.createdAt = normalizeTimestamp(createdAt);
        this.updatedAt = normalizeTimestamp(updatedAt);
    }

    public static DevelopmentGoal empty() {
        return new DevelopmentGoal(null, null, null, null, "", "");
    }

    public boolean isConfigured() {
        return objective != null;
    }

    public String objectiveLabelKo() {
        return objectiveLabelKo(objective);
    }

    public String focusBodyPartLabelKo() {
        return bodyPartLabelKo(focusBodyPart);
    }

    public static String objectiveLabelKo(String objective) {
        String normalized = normalizeObjective(objective);
        if (OBJECTIVE_MUSCLE_GAIN.equals(normalized)) {
            return "근비대";
        }
        if (OBJECTIVE_STRENGTH.equals(normalized)) {
            return "근력";
        }
        if (OBJECTIVE_FAT_LOSS.equals(normalized)) {
            return "체지방 감량";
        }
        if (OBJECTIVE_ENDURANCE.equals(normalized)) {
            return "지구력";
        }
        if (OBJECTIVE_MAINTENANCE.equals(normalized)) {
            return "유지";
        }
        return "미설정";
    }

    public static String bodyPartLabelKo(String bodyPart) {
        String normalized = normalizeFocusBodyPart(bodyPart);
        if (BODY_PART_CHEST.equals(normalized)) {
            return "가슴";
        }
        if (BODY_PART_BACK.equals(normalized)) {
            return "등";
        }
        if (BODY_PART_LEGS.equals(normalized)) {
            return "하체";
        }
        if (BODY_PART_SHOULDERS.equals(normalized)) {
            return "어깨";
        }
        if (BODY_PART_ARMS.equals(normalized)) {
            return "팔";
        }
        if (BODY_PART_ABS.equals(normalized)) {
            return "복근";
        }
        return "미설정";
    }

    static String normalizeObjective(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        return OBJECTIVES.contains(normalized) ? normalized : normalized;
    }

    static String normalizeFocusBodyPart(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        if ("가슴".equals(normalized)) return BODY_PART_CHEST;
        if ("등".equals(normalized)) return BODY_PART_BACK;
        if ("하체".equals(normalized)) return BODY_PART_LEGS;
        if ("어깨".equals(normalized)) return BODY_PART_SHOULDERS;
        if ("팔".equals(normalized) || "biceps".equals(normalized) || "triceps".equals(normalized)) {
            return BODY_PART_ARMS;
        }
        if ("복근".equals(normalized)) return BODY_PART_ABS;
        return normalized;
    }

    private static String requireObjective(String value) {
        if (value == null || !OBJECTIVES.contains(value)) {
            throw new IllegalArgumentException("발전 목표는 muscle_gain, strength, fat_loss, endurance, maintenance 중 하나여야 합니다.");
        }
        return value;
    }

    private static Integer requireWeeklySessionsTarget(Integer value) {
        if (value == null || value < 1 || value > 7) {
            throw new IllegalArgumentException("주간 운동 목표 횟수는 1~7 범위여야 합니다.");
        }
        return value;
    }

    private static String requireFocusBodyPart(String value) {
        if (value == null || !FOCUS_BODY_PARTS.contains(value)) {
            throw new IllegalArgumentException("집중 부위는 chest, back, legs, shoulders, arms, abs 중 하나여야 합니다.");
        }
        return value;
    }

    private static String requireDate(String value) {
        if (value == null) {
            throw new IllegalArgumentException("목표 시작일은 YYYY-MM-DD 형식이어야 합니다.");
        }
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("목표 시작일은 YYYY-MM-DD 형식이어야 합니다.", error);
        }
    }

    private static String normalizeDate(String value) {
        String normalized = normalizeToken(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized).toString();
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("목표 시작일은 YYYY-MM-DD 형식이어야 합니다.", error);
        }
    }

    private static String normalizeToken(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.US);
    }

    private static String normalizeTimestamp(String value) {
        return value == null ? "" : value.trim();
    }
}
