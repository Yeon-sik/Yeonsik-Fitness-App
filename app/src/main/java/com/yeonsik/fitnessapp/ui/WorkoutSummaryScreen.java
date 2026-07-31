package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.data.FitnessRepository;
import com.yeonsik.fitnessapp.data.FitnessRecordContract;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import java.util.List;
import java.util.ArrayList;

/**
 * 운동 요약 화면: 스탯 타일 3개 + 다음 행동 + 종목별 세트 그리드(수행 내역).
 * 수행 내역은 종목 순서대로 블록으로 나뉘고, 각 세트는 세그먼트 막대(무게) + 하단 횟수로 표시한다.
 * 한 막대 행은 최대 6세트이며, 6세트를 초과하면 줄바꿈한다.
 */
public final class WorkoutSummaryScreen extends BaseScreen {
    private static final int SETS_PER_ROW = 6;

    public WorkoutSummaryScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        FitnessUi ui = ui();
        String recordId = host.sessionState().activeRecordId();
        FitnessRepository.SessionMetrics metrics = recordId == null
                ? new FitnessRepository.SessionMetrics()
                : repository().sessionMetrics(recordId);

        screenHeader("SUMMARY", "운동 요약");

        FitnessRepository.SessionInfo info = repository().sessionInfo(recordId);
        LinearLayout tiles = ui.tileRow();
        tiles.addView(ui.statTile("외부 중량 볼륨", FitnessUi.formatVolume(metrics.totalVolumeKg), "kg", true, null),
                ui.tileParams(true));
        tiles.addView(ui.statTile("완료 세트", String.valueOf(metrics.setCount), "개", false, null),
                ui.tileParams(false));
        tiles.addView(ui.statTile("운동 시간",
                info.durationSeconds > 0 ? FitnessUi.formatElapsed(info.durationSeconds) : "—",
                "총 시간", false, null), ui.tileParams(false));
        add(tiles, ui.fullWidthParams(0));

        buttonRow(
                ui.button("기록 보기", true, v -> host.navigate(FitnessScreen.RECORDS)),
                ui.button("피트니스로 돌아가기", false, v -> host.navigate(FitnessScreen.WORKOUT)),
                ui.dp(6)
        );
        buttonRow(
                ui.button("기록 수정", false, v -> host.navigate(FitnessScreen.WORKOUT_SESSION)),
                ui.button("메인", false, v -> host.navigate(FitnessScreen.HOME)),
                ui.dp(6)
        );

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

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.orderBadge(exercise.orderIndex, false));
        LinearLayout titleColumn = new LinearLayout(host.activity());
        titleColumn.setOrientation(LinearLayout.VERTICAL);
        titleColumn.setPadding(ui.dp(12), 0, 0, 0);
        titleColumn.addView(ui.text(exercise.name, 16, FitnessUi.COLOR_TEXT, true));
        String metaText = exercise.uiPart + (exercise.equipment.isEmpty() ? "" : " · " + exercise.equipment);
        TextView meta = ui.text(metaText, 12, FitnessUi.COLOR_MUTED, false);
        meta.setPadding(0, ui.dp(2), 0, 0);
        titleColumn.addView(meta);
        header.addView(titleColumn, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(header);

        for (int start = 0; start < sets.size(); start += SETS_PER_ROW) {
            int end = Math.min(start + SETS_PER_ROW, sets.size());
            card.addView(setBarRow(exercise.recordType, sets.subList(start, end), cellWidth),
                    ui.fullWidthParams(start == 0 ? ui.dp(16) : ui.dp(12)));
        }
        return card;
    }

    /** 세트 한 줄(최대 6칸): 막대 내부에 무게, 막대 아래 줄에 횟수. */
    private View setBarRow(
            String recordType,
            List<FitnessRepository.SessionSetEntry> rowSets,
            int cellWidth
    ) {
        FitnessUi ui = ui();
        LinearLayout column = new LinearLayout(host.activity());
        column.setOrientation(LinearLayout.VERTICAL);

        // 무게 막대: 둥근 서브 표면 + 셀 사이 얇은 구분선.
        LinearLayout bar = new LinearLayout(host.activity());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(ui.borderDrawable(ui.subtle(), ui.border(), ui.dp(12)));
        int barHeight = ui.dp(46);

        for (int i = 0; i < rowSets.size(); i++) {
            if (i > 0) {
                View divider = new View(host.activity());
                divider.setBackgroundColor(ui.border());
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ui.dp(1), ui.dp(22));
                dividerParams.gravity = Gravity.CENTER_VERTICAL;
                bar.addView(divider, dividerParams);
            }
            TextView weight = ui.num(
                    primarySetLabel(recordType, rowSets.get(i)),
                    14,
                    FitnessUi.COLOR_TEXT,
                    true
            );
            weight.setGravity(Gravity.CENTER);
            bar.addView(weight, new LinearLayout.LayoutParams(cellWidth, barHeight));
        }
        column.addView(bar);

        // 횟수 줄: 각 칸 아래에 정렬.
        LinearLayout repsRow = new LinearLayout(host.activity());
        repsRow.setOrientation(LinearLayout.HORIZONTAL);
        repsRow.setPadding(0, ui.dp(6), 0, 0);
        for (int i = 0; i < rowSets.size(); i++) {
            if (i > 0) {
                View spacer = new View(host.activity());
                repsRow.addView(spacer, new LinearLayout.LayoutParams(ui.dp(1), ui.dp(1)));
            }
            TextView reps = ui.num(
                    secondarySetLabel(recordType, rowSets.get(i)),
                    12,
                    FitnessUi.COLOR_MUTED,
                    true
            );
            reps.setGravity(Gravity.CENTER);
            repsRow.addView(reps, new LinearLayout.LayoutParams(cellWidth, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        column.addView(repsRow);

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
        String rpe = set.rpe == null ? "" : " · RPE " + set.rpe;
        if (FitnessRecordContract.WEIGHT_TIME.equals(type)) {
            return set.durationSeconds + "초" + rpe;
        }
        if (FitnessRecordContract.REPS_ONLY.equals(type)
                || FitnessRecordContract.TIME.equals(type)) {
            return rpe.isEmpty() ? "완료" : rpe.substring(3);
        }
        return "×" + set.actualReps + rpe;
    }
}
