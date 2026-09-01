package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.routine.RoutineExerciseInstance;
import com.yeonsik.fitnessapp.routine.RoutineRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

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
                        }
                ));
            }
        }

        section("루틴 관리 (" + routines.size() + "/" + RoutineRepository.MAX_ROUTINES + ")");
        add(ui().button("루틴 추가", false, v -> host.navigate(FitnessScreen.ROUTINE_ADD)),
                ui().fullWidthParams(0));
    }
}
