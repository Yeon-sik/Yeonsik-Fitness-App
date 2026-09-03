package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.Arrays;
import java.util.List;

/** 무산소 화면: 기존 근력 운동 시작과 루틴 관리 옵션을 소유한다. */
public final class StrengthScreen extends BaseScreen {
    public StrengthScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        host.routineRepository().activeRoutineId();
        List<RoutineRepository.RoutineSummary> routines = host.routineRepository().routines();

        add(ui().textAction("‹ 피트니스", FitnessUi.COLOR_MUTED,
                () -> backOr(FitnessScreen.WORKOUT)), ui().fullWidthParams(0));
        screenHeader("루틴과 세트", "무산소");

        section("근력 운동 시작");
        add(ui().button("루틴 없이 운동 시작", true, v -> host.startEmptyWorkout()),
                ui().fullWidthParams(0));
        if (routines.isEmpty()) {
            emptyState("만들어진 루틴이 없습니다.", "아래에서 루틴을 추가하세요.");
        } else {
            for (RoutineRepository.RoutineSummary routine : routines) {
                List<RoutineExerciseInstance> exercises = host.routineRepository()
                        .routineExercises(routine.id);
                add(ui().routineCard(
                        routine.name,
                        routine.exerciseCount,
                        true,
                        repository().latestCompletedWorkoutDateForRoutine(routine.id, routine.name),
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.startRoutineWorkout(exercises);
                        },
                        () -> {
                            host.routineRepository().selectRoutine(routine.id);
                            host.navigate(FitnessScreen.ROUTINE_DETAIL);
                        },
                        () -> showRoutineMenu(routine, exercises)
                ));
            }
        }

        section("루틴 관리 (" + routines.size() + "/" + RoutineRepository.MAX_ROUTINES + ")");
        add(ui().button("루틴 추가", false, v -> host.navigate(FitnessScreen.ROUTINE_ADD)),
                ui().fullWidthParams(0));
    }

    private void showRoutineMenu(
            RoutineRepository.RoutineSummary routine,
            List<RoutineExerciseInstance> exercises
    ) {
        ui().choiceSheet("루틴 관리", Arrays.asList(
                "루틴 상세 보기",
                "바로 운동 시작",
                "이름 변경",
                "루틴 복사",
                "루틴 삭제"
        ), -1, which -> {
            if (which == 0) {
                host.routineRepository().selectRoutine(routine.id);
                host.navigate(FitnessScreen.ROUTINE_DETAIL);
            } else if (which == 1) {
                host.routineRepository().selectRoutine(routine.id);
                host.startRoutineWorkout(exercises);
            } else if (which == 2) {
                showRenameRoutine(routine);
            } else if (which == 3) {
                showCopyRoutine(routine);
            } else if (which == 4) {
                confirmDeleteRoutine(routine);
            }
        });
    }

    private void showRenameRoutine(RoutineRepository.RoutineSummary routine) {
        EditText input = ui().input("루틴 이름", routine.name);
        LinearLayout body = ui().form();
        body.addView(input, ui().fullWidthParams(0));
        ui().validatedSheet("루틴 이름 변경", body, "저장", () -> {
            String name = FitnessUi.inputText(input).trim();
            if (name.isEmpty()) {
                host.toast("루틴 이름을 입력하세요.");
                return false;
            }
            if (!host.routineRepository().renameRoutine(routine.id, name)) {
                host.toast("루틴을 찾지 못했습니다.");
                return false;
            }
            host.toast("루틴 이름을 변경했습니다.");
            host.rerender();
            return true;
        });
    }

    private void showCopyRoutine(RoutineRepository.RoutineSummary routine) {
        EditText input = ui().input("새 루틴 이름", routine.name + " 복사");
        LinearLayout body = ui().form();
        body.addView(input, ui().fullWidthParams(0));
        ui().validatedSheet("루틴 복사", body, "복사", () -> {
            String name = FitnessUi.inputText(input).trim();
            if (name.isEmpty()) {
                host.toast("루틴 이름을 입력하세요.");
                return false;
            }
            String copiedId = host.routineRepository().copyRoutine(routine.id, name);
            if (copiedId == null) {
                host.toast("루틴은 최대 " + RoutineRepository.MAX_ROUTINES + "개까지 저장할 수 있습니다.");
                return false;
            }
            host.routineRepository().selectRoutine(copiedId);
            host.toast("루틴을 복사했습니다.");
            host.rerender();
            return true;
        });
    }

    private void confirmDeleteRoutine(RoutineRepository.RoutineSummary routine) {
        ui().confirmSheet(
                "루틴 삭제",
                "\"" + routine.name + "\" 루틴을 삭제 표시합니다.",
                "완료된 운동 기록은 그대로 보존됩니다.",
                "삭제",
                () -> {
                    if (host.routineRepository().deleteRoutine(routine.id)) {
                        host.toast("루틴을 삭제했습니다.");
                        host.rerender();
                    } else {
                        host.toast("루틴을 찾지 못했습니다.");
                    }
                }
        );
    }
}
