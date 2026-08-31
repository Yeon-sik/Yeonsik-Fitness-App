package com.yeonsik.fitnessapp.exercise;

import java.util.Locale;

/** Resolves the compact representative muscle label used by exercise pickers. */
public final class ExercisePrimaryMuscleLabel {
    private ExercisePrimaryMuscleLabel() {
    }

    public static String forPreset(RuntimeExercisePreset preset) {
        if (preset == null) {
            return "";
        }
        return forPrimarySubPart(preset.primarySubPart, preset.defaultUiPart);
    }

    /** Uses canonical primarySubPart ids and never infers from a localized exercise name. */
    public static String forPrimarySubPart(String primarySubPart, String defaultUiPart) {
        String key = primarySubPart == null
                ? ""
                : primarySubPart.trim().toLowerCase(Locale.ROOT);
        switch (key) {
            case "upper_chest":
            case "mid_chest":
            case "lower_chest":
            case "overall_chest":
                return "가슴";
            case "front_delts":
            case "side_delts":
            case "rear_delts":
            case "rear_delt_linked":
            case "external_rotation":
            case "overall_shoulders":
                return "어깨";
            case "biceps":
                return "이두";
            case "triceps":
                return "삼두";
            case "glutes":
                return "둔근";
            case "abductors":
            case "adductors":
            case "quads":
            case "hamstrings":
            case "calves":
            case "overall_legs":
                return "하체";
            case "lats":
            case "traps":
            case "mid_back":
            case "upper_back":
            case "scapular_retraction":
            case "erector_spinae":
            case "overall_back":
                return "등";
            case "forearms":
                return "전완";
            case "rectus_abdominis":
            case "obliques":
            case "transverse_abdominis":
            case "core_stability":
            case "overall_abs":
                return "복근";
            default:
                BodyPart bodyPart = BodyPart.fromId(defaultUiPart);
                return bodyPart == null ? "" : bodyPart.labelKo();
        }
    }
}
