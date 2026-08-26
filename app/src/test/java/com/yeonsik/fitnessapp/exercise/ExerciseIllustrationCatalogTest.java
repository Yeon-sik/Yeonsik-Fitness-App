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
                        R.drawable.exercise_barbell_flat_bench_press_a,
                        R.drawable.exercise_barbell_flat_bench_press_b
                },
                ExerciseIllustrationCatalog.detailDrawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_FLAT_BENCH_PRESS_ID
                )
        );
        assertEquals(
                R.drawable.exercise_barbell_flat_bench_press_a,
                ExerciseIllustrationCatalog.listPreviewDrawableFor(
                        ExerciseIllustrationCatalog.BARBELL_FLAT_BENCH_PRESS_ID
                )
        );
        assertArrayEquals(
                new int[]{R.drawable.exercise_pull_up_a, R.drawable.exercise_pull_up_b},
                ExerciseIllustrationCatalog.detailDrawablesFor(ExerciseIllustrationCatalog.PULL_UP_ID)
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_back_squat_a,
                        R.drawable.exercise_barbell_back_squat_b
                },
                ExerciseIllustrationCatalog.detailDrawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_BACK_SQUAT_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_deadlift_a,
                        R.drawable.exercise_barbell_deadlift_b
                },
                ExerciseIllustrationCatalog.detailDrawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_DEADLIFT_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_barbell_bent_over_row_a,
                        R.drawable.exercise_barbell_bent_over_row_b
                },
                ExerciseIllustrationCatalog.detailDrawablesFor(
                        ExerciseIllustrationCatalog.BARBELL_BENT_OVER_ROW_ID
                )
        );
        assertArrayEquals(
                new int[]{
                        R.drawable.exercise_dumbbell_shoulder_press_a,
                        R.drawable.exercise_dumbbell_shoulder_press_b
                },
                ExerciseIllustrationCatalog.detailDrawablesFor(
                        ExerciseIllustrationCatalog.DUMBBELL_SHOULDER_PRESS_ID
                )
        );
        assertArrayEquals(
                new int[]{R.drawable.exercise_lat_pulldown_a, R.drawable.exercise_lat_pulldown_b},
                ExerciseIllustrationCatalog.detailDrawablesFor(ExerciseIllustrationCatalog.LAT_PULLDOWN_ID)
        );
    }

    @Test
    public void readsFrameDurationsFromSceneCatalog() {
        assertArrayEquals(
                new int[]{1000, 1000},
                ExerciseIllustrationCatalog.frameDurationsMsFor(
                        ExerciseIllustrationCatalog.BARBELL_FLAT_BENCH_PRESS_ID
                )
        );
        assertArrayEquals(
                new int[0],
                ExerciseIllustrationCatalog.frameDurationsMsFor("unsupported_exercise")
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
        assertEquals(0, ExerciseIllustrationCatalog.listPreviewDrawableFor(null));
        assertArrayEquals(
                new int[0],
                ExerciseIllustrationCatalog.detailDrawablesFor("unsupported_exercise")
        );
    }
}
