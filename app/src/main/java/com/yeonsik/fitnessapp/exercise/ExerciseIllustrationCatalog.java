package com.yeonsik.fitnessapp.exercise;

import com.yeonsik.fitnessapp.R;

/** 운동 마스터 ID를 앱 내 자세 이미지 프레임에 연결한다. */
public final class ExerciseIllustrationCatalog {
    public static final String BARBELL_FLAT_BENCH_PRESS_ID = "chest_barbell_flat_bench_press";
    public static final String PULL_UP_ID = "back_bodyweight_pull_up";
    public static final String BARBELL_BACK_SQUAT_ID = "legs_barbell_back_squat";
    public static final String BARBELL_DEADLIFT_ID = "back_barbell_deadlift";
    public static final String BARBELL_BENT_OVER_ROW_ID = "back_barbell_bent_over_row";
    public static final String DUMBBELL_SHOULDER_PRESS_ID = "shoulders_dumbbell_shoulder_press";
    public static final String LAT_PULLDOWN_ID = "back_machine_lat_pulldown";

    private ExerciseIllustrationCatalog() {
    }

    public static int[] drawablesFor(String exerciseId) {
        if (BARBELL_FLAT_BENCH_PRESS_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_barbell_flat_bench_press,
                    R.drawable.exercise_barbell_flat_bench_press_frame_2
            };
        }
        if (PULL_UP_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_pull_up,
                    R.drawable.exercise_pull_up_frame_2
            };
        }
        if (BARBELL_BACK_SQUAT_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_barbell_back_squat,
                    R.drawable.exercise_barbell_back_squat_frame_2
            };
        }
        if (BARBELL_DEADLIFT_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_barbell_deadlift,
                    R.drawable.exercise_barbell_deadlift_frame_2
            };
        }
        if (BARBELL_BENT_OVER_ROW_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_barbell_bent_over_row,
                    R.drawable.exercise_barbell_bent_over_row_frame_2
            };
        }
        if (DUMBBELL_SHOULDER_PRESS_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_dumbbell_shoulder_press,
                    R.drawable.exercise_dumbbell_shoulder_press_frame_2
            };
        }
        if (LAT_PULLDOWN_ID.equals(exerciseId)) {
            return new int[]{
                    R.drawable.exercise_lat_pulldown,
                    R.drawable.exercise_lat_pulldown_frame_2
            };
        }
        return new int[0];
    }

    /** 장면 전체가 잘리지 않도록 운동별 권장 이미지 영역 높이를 반환한다. */
    public static int preferredHeightDp(String exerciseId) {
        return BARBELL_FLAT_BENCH_PRESS_ID.equals(exerciseId) ? 220 : 280;
    }
}
