package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.exercise.RuntimeExerciseCatalog;
import com.yeonsik.fitnessapp.exercise.RoutineExercise;
import com.yeonsik.fitnessapp.routine.WorkoutRoutineMapper;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 운동 요약 화면: 스탯 타일 3개 + 다음 행동 + 종목별 세트 그리드(수행 내역).
 * 수행 내역은 종목 순서대로 블록으로 나뉘고, 각 세트는 연결된 둥근 표 셀로 표시한다.
 * 한 표 행은 최대 6세트이며, 6세트를 초과하면 줄바꿈한다.
 */
public final class WorkoutSummaryScreen extends BaseScreen {
    private static final int SETS_PER_ROW = 6;
    private final ExerciseCardRenderer exerciseCardRenderer;

    public WorkoutSummaryScreen(ScreenHost host) {
        super(host);
        exerciseCardRenderer = new ExerciseCardRenderer(
                host.activity(),
                host.ui(),
                new ExerciseIllustrationPreview(host.activity(), host.ui())
        );
    }

    @Override
    public void render() {
        FitnessUi ui = ui();
        String recordId = host.sessionState().activeRecordId();
        FitnessRepository.SessionMetrics metrics = recordId == null
                ? new FitnessRepository.SessionMetrics()
                : repository().sessionMetrics(recordId);

        FitnessRepository.SessionInfo info = repository().sessionInfo(recordId);
        boolean cardio = "cardio".equals(info.workoutType);
        screenHeader("완료 기록", cardio ? "유산소 요약" : "운동 요약");

        LinearLayout tiles = ui.tileRow();
        if (cardio) {
            tiles.addView(ui.statTile(
                    "이동 거리",
                    CardioMetrics.formatDistanceKilometers(metrics.totalDistanceMeters),
                    "km",
                    true,
                    null
            ), ui.tileParams(true));
            tiles.addView(ui.statTile(
                    "운동 시간",
                    info.durationSeconds > 0 ? FitnessUi.formatElapsed(info.durationSeconds) : "—",
                    "총 시간",
                    false,
                    null
            ), ui.tileParams(false));
            tiles.addView(ui.statTile(
                    "평균 심박수",
                    CardioMetrics.formatAverageHeartRate(info.averageHeartRateBpm),
                    CardioMetrics.hasAverageHeartRate(info.averageHeartRateBpm) ? "bpm" : "미입력",
                    false,
                    null
            ), ui.tileParams(false));
        } else {
            tiles.addView(ui.statTile("외부 중량 볼륨", FitnessUi.formatVolume(metrics.totalVolumeKg), "kg", true, null),
                    ui.tileParams(true));
            tiles.addView(ui.statTile("완료 세트", String.valueOf(metrics.setCount), "개", false, null),
                    ui.tileParams(false));
            tiles.addView(ui.statTile("운동 시간",
                    info.durationSeconds > 0 ? FitnessUi.formatElapsed(info.durationSeconds) : "—",
                    "총 시간", false, null), ui.tileParams(false));
        }
        add(tiles, ui.fullWidthParams(0));

        buttonRow(
                ui.button("기록 보기", true, v -> host.navigate(FitnessScreen.RECORDS)),
                ui.button(cardio ? "유산소로 돌아가기" : "무산소로 돌아가기", false,
                        v -> host.navigate(cardio ? FitnessScreen.CARDIO : FitnessScreen.STRENGTH)),
                ui.dp(6)
        );
        if (cardio) {
            add(ui.button("메인", false, v -> host.navigate(FitnessScreen.HOME)),
                    ui.fullWidthParams(ui.dp(6)));
        } else {
            buttonRow(
                    ui.button("기록 수정", false, v -> host.navigate(FitnessScreen.WORKOUT_SESSION)),
                    ui.button("메인", false, v -> host.navigate(FitnessScreen.HOME)),
                    ui.dp(6)
            );
            add(ui.button("이 운동을 루틴으로 저장", false,
                            v -> showSaveAsRoutineDialog(recordId)),
                    ui.fullWidthParams(ui.dp(6)));
        }

        section("수행 내역");
        renderPerformance(recordId);
    }

    private void renderPerformance(String recordId) {
        List<FitnessRepository.SessionExerciseEntry> exercises = recordId == null
                ? java.util.Collections.emptyList()
                : repository().sessionExerciseEntries(recordId);

        int cellWidth = cellWidth();
        boolean rendered = false;
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            List<FitnessRepository.SessionSetEntry> sets = new ArrayList<>();
            for (FitnessRepository.SessionSetEntry set : repository().setsForExercise(exercise.id)) {
                if (set.isCompleted) {
                    sets.add(set);
                }
            }
            if (sets.isEmpty()) {
                continue;
            }
            add(exerciseBlock(exercise, sets, cellWidth));
            rendered = true;
        }

        if (!rendered) {
            emptyState("세부 운동 기록이 없습니다.", null);
        }
    }

    private void showSaveAsRoutineDialog(String recordId) {
        if (recordId == null) {
            host.toast("저장할 운동 기록이 없습니다.");
            return;
        }
        List<FitnessRepository.SessionExerciseEntry> exercises =
                repository().sessionExerciseEntries(recordId);
        Map<String, List<FitnessRepository.SessionSetEntry>> setsByExercise =
                new LinkedHashMap<>();
        for (FitnessRepository.SessionExerciseEntry exercise : exercises) {
            setsByExercise.put(exercise.id, repository().setsForExercise(exercise.id));
        }
        RuntimeExerciseCatalog catalog = host.exerciseMasterRepository().runtimeCatalog();
        List<RoutineExercise> routineExercises = WorkoutRoutineMapper.mapCompletedExercises(
                exercises,
                setsByExercise,
                catalog
        );
        if (routineExercises.isEmpty()) {
            host.toast("완료된 세트가 있는 운동 종목이 없습니다.");
            return;
        }
        if (!host.routineRepository().canCreateRoutine()) {
            host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
            return;
        }

        FitnessUi ui = ui();
        LinearLayout form = new LinearLayout(host.activity());
        form.setOrientation(LinearLayout.VERTICAL);
        form.addView(ui.text("완료 세트가 있는 종목만 현재 순서대로 저장합니다.",
                13, FitnessUi.COLOR_MUTED, false), ui.fullWidthParams(ui.dp(8)));
        form.addView(ui.fieldLabel("루틴 이름"), ui.fullWidthParams(ui.dp(10)));
        EditText routineNameInput = ui.input("루틴 이름", "");
        form.addView(routineNameInput, ui.fullWidthParams(0));

        ui.validatedSheet("이 운동을 루틴으로 저장", form, "루틴 저장", () -> {
            String routineName = FitnessUi.inputText(routineNameInput).trim();
            if (routineName.isEmpty()) {
                host.toast("루틴 이름을 입력하세요.");
                return false;
            }
            if (!host.routineRepository().canCreateRoutine()) {
                host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                return false;
            }
            String routineId = host.routineRepository().createRoutine(
                    routineName,
                    routineExercises
            );
            if (routineId == null) {
                host.toast("루틴은 최대 5개까지 저장할 수 있습니다.");
                return false;
            }
            host.routineRepository().selectRoutine(routineId);
            host.toast("루틴을 저장했습니다. (" + host.routineRepository().routines().size() + "/5)");
            return true;
        });
    }

    /** 화면·카드 여백을 뺀 폭을 6등분한 막대 한 칸의 폭(px). 세트가 6개일 때 막대가 폭을 채운다. */
    private int cellWidth() {
        FitnessUi ui = ui();
        int screenWidth = host.activity().getResources().getDisplayMetrics().widthPixels;
        // content 좌우 20dp + 카드 좌우 18dp = 76dp, 셀 사이 구분선 5개(1dp).
        int inner = screenWidth - ui.dp(76) - ui.dp(SETS_PER_ROW - 1);
        return Math.max(ui.dp(40), inner / SETS_PER_ROW);
    }

    private View exerciseBlock(FitnessRepository.SessionExerciseEntry exercise,
                               List<FitnessRepository.SessionSetEntry> sets, int cellWidth) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        ExerciseCardRenderer.Content content =
                ExerciseCardRenderer.Content.fromSessionExercise(
                        exercise,
                        host.exerciseMasterRepository().getExerciseById(exercise.exerciseId)
                );
        TextView exerciseName = ui.text(content.name, 18, FitnessUi.COLOR_TEXT, true);
        exerciseName.setGravity(Gravity.CENTER);
        exerciseName.setPadding(0, 0, 0, ui.dp(8));
        card.addView(exerciseName, ui.fullWidthParams(0));
        exerciseCardRenderer.addPreviewOnly(card, content);
        card.addView(ui.text("수행 횟수", 12, FitnessUi.COLOR_MUTED, true),
                ui.fullWidthParams(ui.dp(12)));

        for (int start = 0; start < sets.size(); start += SETS_PER_ROW) {
            int end = Math.min(start + SETS_PER_ROW, sets.size());
            card.addView(setBarRow(exercise.recordType, sets.subList(start, end), cellWidth),
                    ui.fullWidthParams(start == 0 ? ui.dp(16) : ui.dp(12)));
        }
        return card;
    }

    /** 세트 한 줄(최대 6칸): 각 칸에 대표값과 횟수를 넣은 연결된 둥근 표. */
    private View setBarRow(
            String recordType,
            List<FitnessRepository.SessionSetEntry> rowSets,
            int cellWidth
    ) {
        FitnessUi ui = ui();
        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        // 하나의 외곽 표 안에 셀과 구분선을 붙여 배치해 셀 사이 빈틈이 생기지 않게 한다.
        LinearLayout table = new LinearLayout(host.activity());
        table.setOrientation(LinearLayout.HORIZONTAL);
        table.setGravity(Gravity.CENTER_VERTICAL);
        table.setBackground(ui.borderDrawable(ui.subtle(), ui.border(), ui.dp(12)));
        table.setClipToOutline(true);
        int tableHeight = ui.dp(60);
        int tableWidth = cellWidth * rowSets.size() + ui.dp(Math.max(0, rowSets.size() - 1));

        for (int i = 0; i < rowSets.size(); i++) {
            LinearLayout cell = new LinearLayout(host.activity());
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(ui.dp(4), ui.dp(3), ui.dp(4), ui.dp(3));

            TextView primary = ui.num(
                    primarySetLabel(recordType, rowSets.get(i)),
                    14,
                    FitnessUi.COLOR_TEXT,
                    true
            );
            primary.setGravity(Gravity.CENTER);
            cell.addView(primary, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
            ));

            TextView secondary = ui.num(
                    secondarySetLabel(recordType, rowSets.get(i)),
                    12,
                    FitnessUi.COLOR_MUTED,
                    true
            );
            secondary.setGravity(Gravity.CENTER);
            cell.addView(secondary, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            table.addView(cell, new LinearLayout.LayoutParams(cellWidth, tableHeight));

            if (i < rowSets.size() - 1) {
                View divider = new View(host.activity());
                divider.setBackgroundColor(ui.border());
                table.addView(divider, new LinearLayout.LayoutParams(ui.dp(1), tableHeight));
            }
        }
        column.addView(table, new LinearLayout.LayoutParams(tableWidth, tableHeight));

        return column;
    }

    private String weightLabel(double weightKg) {
        if (weightKg <= 0) {
            return "맨몸";
        }
        return FitnessUi.trimDouble(weightKg);
    }

    private String primarySetLabel(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        if (FitnessRecordContract.REPS_ONLY.equals(type)) {
            return set.actualReps + "회";
        }
        if (FitnessRecordContract.TIME.equals(type)) {
            return set.durationSeconds + "초";
        }
        if (FitnessRecordContract.ASSISTED_WEIGHT_REPS.equals(type)) {
            return "보조 " + FitnessUi.trimDouble(set.assistedWeightKg);
        }
        if (FitnessRecordContract.BODYWEIGHT_ADDED_WEIGHT_REPS.equals(type)) {
            return "추가 " + FitnessUi.trimDouble(set.addedWeightKg);
        }
        return weightLabel(set.weightKg);
    }

    private String secondarySetLabel(
            String recordType,
            FitnessRepository.SessionSetEntry set
    ) {
        String type = FitnessRecordContract.normalizeRecordType(recordType);
        String rir = FitnessRecordContract.supportsRir(type) && set.rir != null
                ? " · RIR " + set.rir
                : "";
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return set.durationSeconds + "초";
        }
        if (FitnessRecordContract.REPS_ONLY.equals(type)
                || FitnessRecordContract.TIME.equals(type)) {
            if (FitnessRecordContract.TIME.equals(type) && set.distanceMeters > 0d) {
                return CardioMetrics.formatDistanceKilometers(set.distanceMeters) + "km";
            }
            return rir.isEmpty() ? "완료" : rir.substring(3);
        }
        return "×" + set.actualReps + rir;
    }
}
