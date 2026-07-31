package com.yeonsik.fitnessapp.data;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FitnessRecordContract {
    public static final int VERSION = 1;

    public static final String WEIGHT_REPS = "weight_reps";
    public static final String REPS_ONLY = "reps_only";
    public static final String TIME = "time";
    public static final String WEIGHT_TIME = "weight_time";
    public static final String ASSISTED_WEIGHT_REPS = "assisted_weight_reps";
    public static final String BODYWEIGHT_ADDED_WEIGHT_REPS = "bodyweight_added_weight_reps";

    private FitnessRecordContract() {
    }

    public static String normalizeRecordType(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case REPS_ONLY:
            case TIME:
            case WEIGHT_TIME:
            case ASSISTED_WEIGHT_REPS:
            case BODYWEIGHT_ADDED_WEIGHT_REPS:
                return normalized;
            case "sets_reps_weight":
            case WEIGHT_REPS:
            default:
                return WEIGHT_REPS;
        }
    }

    public static String categoryCode(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.US);
        switch (normalized) {
            case "가슴":
            case "가슴운동":
            case "chest":
                return "chest";
            case "등":
            case "등운동":
            case "back":
                return "back";
            case "하체":
            case "하체운동":
            case "legs":
                return "legs";
            case "어깨":
            case "어깨운동":
            case "shoulders":
                return "shoulders";
            case "복부":
            case "복부운동":
            case "abs":
                return "abs";
            case "삼두":
            case "삼두운동":
            case "triceps":
                return "triceps";
            case "이두":
            case "이두운동":
            case "biceps":
                return "biceps";
            case "유산소":
            case "cardio":
                return "cardio";
            default:
                return "other";
        }
    }

    public static JSONArray categoryCodes(List<String> categories) {
        List<String> unique = new ArrayList<>();
        for (String category : categories) {
            String code = categoryCode(category);
            if (!unique.contains(code)) {
                unique.add(code);
            }
        }
        return new JSONArray(unique);
    }
}
