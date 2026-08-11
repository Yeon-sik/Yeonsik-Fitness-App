package com.yeonsik.fitnessapp.development;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class DevelopmentInsightRules {
    private DevelopmentInsightRules() {
    }

    static List<DevelopmentInsight> build(Input input) {
        if (input == null) {
            return Collections.emptyList();
        }
        List<DevelopmentInsight> insights = new ArrayList<>();
        if (!input.goalConfigured) {
            insights.add(new DevelopmentInsight(
                    "planning",
                    "목표 설정이 먼저 필요합니다",
                    "발전 목표가 아직 저장되지 않아 " + input.referenceDate + " 기준 주간 빈도와 집중 부위 해석이 제한됩니다.",
                    "목표, 주간 운동 횟수, 집중 부위를 먼저 저장한 뒤 같은 보고서를 다시 비교하세요.",
                    "현재 보고서는 저장된 운동/식사/체크인 사실만 집계하며 목표 부재 상태에서는 우선순위를 추천하지 않습니다."
            ));
        } else if (input.currentWeekCompletedWorkoutSessions < input.weeklySessionsTarget) {
            insights.add(new DevelopmentInsight(
                    "consistency",
                    "이번 주 운동 빈도가 목표보다 낮습니다",
                    "이번 주 완료 운동은 " + input.currentWeekCompletedWorkoutSessions + "회, 운동한 날은 "
                            + input.currentWeekCompletedWorkoutDays + "일이며 목표는 주 "
                            + input.weeklySessionsTarget + "회입니다.",
                    "남은 주간 일정에서 최소 " + (input.weeklySessionsTarget - input.currentWeekCompletedWorkoutSessions)
                            + "회 슬롯을 먼저 확보하세요.",
                    "OS 요약 운동은 빈도에만 포함되고 세트 상세 근거에는 포함되지 않습니다."
            ));
        }

        if (insights.size() < 3 && input.goalConfigured) {
            boolean staleFocus = input.goalFocusBodyPartLastTrainedDate == null;
            long daysSinceLastFocus = Long.MAX_VALUE;
            if (!staleFocus) {
                daysSinceLastFocus = ChronoUnit.DAYS.between(
                        LocalDate.parse(input.goalFocusBodyPartLastTrainedDate),
                        input.referenceDate
                );
                staleFocus = daysSinceLastFocus > 10;
            }
            boolean missingRecentFocus = input.goalFocusBodyPartCompletedSets == 0
                    && (input.goalFocusBodyPartLastTrainedDate == null || daysSinceLastFocus > 0);
            if (missingRecentFocus || staleFocus) {
                String evidence = input.goalFocusBodyPartCompletedSets == 0
                        ? "최근 14일 창(" + input.recentWindowStart + " ~ " + input.recentWindowEnd + ")에 "
                        + input.goalFocusBodyPartLabelKo + " 완료 세트가 없습니다."
                        : input.goalFocusBodyPartLabelKo + " 마지막 상세 훈련일이 "
                        + input.goalFocusBodyPartLastTrainedDate + "로 " + daysSinceLastFocus + "일 전입니다.";
                insights.add(new DevelopmentInsight(
                        "focus",
                        input.goalFocusBodyPartLabelKo + " 자극 근거가 부족합니다",
                        evidence,
                        input.goalFocusBodyPartLabelKo + " 중심 세션을 먼저 배치하고 세트 완료 기록을 남기세요.",
                        "상세 세트/부위 근거는 Fitness 앱에서 완료된 세션만 사용하며 OS 요약 기록은 제외됩니다."
                ));
            }
        }

        if (insights.size() < 3 && input.lowEnergyOrReadinessDays >= 3) {
            insights.add(new DevelopmentInsight(
                    "recovery",
                    "낮은 컨디션 체크인이 반복됩니다",
                    "최근 14일 동안 에너지 또는 준비도 점수 2 이하인 날이 "
                            + input.lowEnergyOrReadinessDays + "일입니다.",
                    "운동 강도 조정보다 먼저 수면, 휴식, 일정 밀도를 점검하고 필요하면 체크인 메모를 함께 남기세요.",
                    "체크인 점수는 자가 보고 데이터이며 통증, 질환, 의료 판단을 포함하지 않습니다."
            ));
        }

        if (insights.size() < 3 && input.mealRecordedDays == 0 && input.currentWeekCompletedWorkoutSessions > 0) {
            insights.add(new DevelopmentInsight(
                    "nutrition_logging",
                    "식사 기록 근거가 부족합니다",
                    "최근 14일 동안 식사 기록이 0일이어서 운동 빈도와 함께 볼 영양 근거가 없습니다.",
                    "식사 자체를 0으로 보지 말고, 먼저 기록일 수를 늘려서 다음 보고서의 비교 근거를 확보하세요.",
                    "미기록은 미섭취를 의미하지 않으며 이 보고서는 칼로리 처방을 하지 않습니다."
            ));
        }

        if (insights.size() < 3 && input.daysWithAnyData < 7) {
            insights.add(new DevelopmentInsight(
                    "coverage",
                    "최근 2주 데이터 커버리지가 낮습니다",
                    "최근 14일 중 기록이 남은 날은 " + input.daysWithAnyData + "일뿐이며, 체중 "
                            + input.weightRecordedDays + "일 / 식사 " + input.mealRecordedDays
                            + "일 / 체크인 " + input.checkInRecordedDays + "일입니다.",
                    "다음 1주일은 체중, 식사, 체크인을 같은 날짜 기준으로 함께 남겨 비교 가능한 근거를 늘리세요.",
                    "커버리지가 낮으면 추세 해석보다 기록 누락 영향이 더 큽니다."
            ));
        }

        if (insights.size() < 3 && input.latestWeightKg == null) {
            insights.add(new DevelopmentInsight(
                    "coverage",
                    "기준 체중 기록이 없습니다",
                    "참조일 " + input.referenceDate + " 이전 체중 기록이 없어 체중 변동 근거를 제시할 수 없습니다.",
                    "최근 체중 1건을 먼저 남긴 뒤 다음 보고서에서 운동·식사 기록과 함께 비교하세요.",
                    "이 보고서는 체중이 없다고 임의 보정하지 않습니다."
            ));
        }

        return insights.size() <= 3 ? insights : insights.subList(0, 3);
    }

    static final class Input {
        final LocalDate referenceDate;
        final LocalDate recentWindowStart;
        final LocalDate recentWindowEnd;
        final boolean goalConfigured;
        final int weeklySessionsTarget;
        final int currentWeekCompletedWorkoutDays;
        final int currentWeekCompletedWorkoutSessions;
        final String goalFocusBodyPartLabelKo;
        final int goalFocusBodyPartCompletedSets;
        final String goalFocusBodyPartLastTrainedDate;
        final int mealRecordedDays;
        final int lowEnergyOrReadinessDays;
        final int daysWithAnyData;
        final int checkInRecordedDays;
        final int weightRecordedDays;
        final Double latestWeightKg;

        Input(
                LocalDate referenceDate,
                LocalDate recentWindowStart,
                LocalDate recentWindowEnd,
                boolean goalConfigured,
                int weeklySessionsTarget,
                int currentWeekCompletedWorkoutDays,
                int currentWeekCompletedWorkoutSessions,
                String goalFocusBodyPartLabelKo,
                int goalFocusBodyPartCompletedSets,
                String goalFocusBodyPartLastTrainedDate,
                int mealRecordedDays,
                int lowEnergyOrReadinessDays,
                int daysWithAnyData,
                int checkInRecordedDays,
                int weightRecordedDays,
                Double latestWeightKg
        ) {
            this.referenceDate = referenceDate;
            this.recentWindowStart = recentWindowStart;
            this.recentWindowEnd = recentWindowEnd;
            this.goalConfigured = goalConfigured;
            this.weeklySessionsTarget = weeklySessionsTarget;
            this.currentWeekCompletedWorkoutDays = currentWeekCompletedWorkoutDays;
            this.currentWeekCompletedWorkoutSessions = currentWeekCompletedWorkoutSessions;
            this.goalFocusBodyPartLabelKo = goalFocusBodyPartLabelKo;
            this.goalFocusBodyPartCompletedSets = goalFocusBodyPartCompletedSets;
            this.goalFocusBodyPartLastTrainedDate = goalFocusBodyPartLastTrainedDate;
            this.mealRecordedDays = mealRecordedDays;
            this.lowEnergyOrReadinessDays = lowEnergyOrReadinessDays;
            this.daysWithAnyData = daysWithAnyData;
            this.checkInRecordedDays = checkInRecordedDays;
            this.weightRecordedDays = weightRecordedDays;
            this.latestWeightKg = latestWeightKg;
        }
    }
}
