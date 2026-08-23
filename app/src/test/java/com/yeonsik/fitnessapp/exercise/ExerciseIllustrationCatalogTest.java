package com.yeonsik.fitnessapp.exercise;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.yeonsik.fitnessapp.R;

import org.junit.Test;

public final class ExerciseIllustrationCatalogTest {
    @Test
    public void mapsSupportedExerciseIdsToTheirDrawables() {
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_flat_bench_press,
                        R.drawable.exercise_barbell_flat_bench_press_frame_2
                },
                ExerciseIllustrationCatalog.drawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_FLAT_BENCH_PRESS_ID
                )
        );
        assertArrayEquals(
                new int[]{R.drawable.exercise_pull_up, R.drawable.exercise_pull_up_frame_2},
                ExerciseIllustrationCatalog.drawablesFor(ExerciseIllustrationCatalog.PULL_UP_ID)
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_back_squat,
                        R.drawable.exercise_barbell_back_squat_frame_2
                },
                ExerciseIllustrationCatalog.drawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_BACK_SQUAT_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_deadlift,
                        R.drawable.exercise_barbell_deadlift_frame_2
                },
                ExerciseIllustrationCatalog.drawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_DEADLIFT_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_bent_over_row,
                        R.drawable.exercise_barbell_bent_over_row_frame_2
                },
                ExerciseIllustrationCatalog.drawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_BENT_OVER_ROW_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_dumbbell_shoulder_press,
                        R.drawable.exercise_dumbbell_shoulder_press_frame_2
                },
                ExerciseIllustrationCatalog.drawablesFor(
                        ExerciseIllustrationCatalog.DUMBBELL_SHOULDER_PRESS_ID
                )
        );
        assertArrayEquals(
                new int[]{R.drawable.exercise_lat_pulldown, R.drawable.exercise_lat_pulldown_frame_2},
                ExerciseIllustrationCatalog.drawablesFor(ExerciseIllustrationCatalog.LAT_PULLDOWN_ID)
        );
    }

    @Test
    public void reservesMoreHeightForFullBodyIllustrations() {
        assertEquals(
                220,
                ExerciseIllustrationCatalog.preferredHeightDp(
                        ExerciseIllustrationCatalog.BARBELL_FLAT_BENCH_PRESS_ID
                )
        );
        assertEquals(
                280,
                ExerciseIllustrationCatalog.preferredHeightDp(
                        ExerciseIllustrationCatalog.BARBELL_BACK_SQUAT_ID
                )
        );
    }

    @Test
    public void returnsEmptyFramesWhenNoIllustrationExists() {
        assertArrayEquals(new int[0], ExerciseIllustrationCatalog.drawablesFor(null));
        assertArrayEquals(
                new int[0],
                ExerciseIllustrationCatalog.drawablesFor("unsupported_exercise")
        );
    }
}
