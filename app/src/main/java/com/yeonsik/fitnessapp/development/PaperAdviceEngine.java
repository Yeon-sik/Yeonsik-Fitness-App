package com.yeonsik.fitnessapp.development;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 논문 기반 조언 엔진의 첫 번째 순수 Java vertical slice. */
public final class PaperAdviceEngine {
    private static final int MAX_ADVICE = 5;

    /** DB를 직접 읽지 않고 정규화 snapshot만 평가한다. */
    public List<PaperAdvice> evaluate(PaperAdviceInput input) {
        if (input == null) return Collections.emptyList();
        List<PaperAdvice> advice = new ArrayList<>();
        if (!input.hasRecentData()) {
            advice.add(new PaperAdvice(
                    "DATA_COVERAGE_001", "coverage", "조언을 만들 최근 기록이 부족합니다",
                    "최근 기록일이 0일이어서 사용자의 상태와 훈련·영양·회복 추세를 비교할 수 없습니다.",
                    "체중, 식사, 운동, 수면·준비도 중 가능한 항목을 같은 날짜 기준으로 기록한 뒤 다시 평가하세요.",
                    "기록 부재는 미섭취·미운동·회복 실패를 의미하지 않습니다.", "unknown",
                    PaperAdvice.Status.INSUFFICIENT_DATA, Collections.emptyList()
            ));
            return advice;
        }

        // 통증은 논문 평균효과보다 우선하는 앱 안전 gate이며, 진단이나 운동 허가를 하지 않는다.
        if (Boolean.TRUE.equals(input.painReported)) {
            advice.add(new PaperAdvice(
                    "SAFETY_PAIN_001", "safety", "통증 기록이 있어 일반 조언을 제한합니다",
                    "현재 입력에 통증 보고가 포함되어 있어 평균적인 훈련·회복 효과를 그대로 적용할 수 없습니다.",
                    "통증을 유발하는 동작과 강도를 줄이거나 중단하고, 지속·악화·기능 저하가 있으면 의료 전문가의 평가를 받으세요.",
                    "이 엔진은 통증의 원인, 부상 여부, 운동 가능 여부를 판단하지 않습니다.", "unknown",
                    PaperAdvice.Status.SAFETY_REVIEW, Collections.emptyList()
            ));
            return advice;
        }

        addSleepAdvice(input, advice);
        addProteinAdvice(input, advice);
        addFailureAdvice(input, advice);
        addColdWaterAdvice(input, advice);
        if (advice.isEmpty() && input.goal.isEmpty()) {
            advice.add(new PaperAdvice(
                    "GOAL_CONTEXT_001", "planning", "목표가 없어 논문 근거를 좁히기 어렵습니다",
                    "현재 기록은 있지만 목표가 비어 있어 근비대·근력·감량·지구력 중 어떤 outcome을 우선할지 정할 수 없습니다.",
                    "우선 목표와 기간을 저장한 뒤 같은 기록을 다시 평가하세요.",
                    "목표가 없을 때 특정 outcome을 임의로 우선하지 않습니다.", "unknown",
                    PaperAdvice.Status.INSUFFICIENT_DATA, Collections.emptyList()
            ));
        }
        return advice.size() <= MAX_ADVICE
                ? Collections.unmodifiableList(advice)
                : Collections.unmodifiableList(new ArrayList<>(advice.subList(0, MAX_ADVICE)));
    }

    private static void addSleepAdvice(PaperAdviceInput input, List<PaperAdvice> advice) {
        boolean shortSleep = input.sleepHours != null && input.sleepHours < 7d;
        boolean lowReadiness = isLow(input.energyScore) || isLow(input.readinessScore);
        if (!shortSleep && !lowReadiness) return;
        String observation = shortSleep
                ? String.format("최근 수면 입력이 %.1f시간으로 기록되었습니다.", input.sleepHours)
                : "에너지 또는 훈련 준비도 점수가 2 이하로 기록되었습니다.";
        advice.add(new PaperAdvice(
                "REC_SLEEP_001", "recovery", "오늘의 readiness를 보수적으로 조절하세요", observation,
                "기술 복잡도·세션량·고강도 노출을 조절할 후보로 표시하고, 하루 수치만으로 훈련을 자동 취소하지 마세요.",
                "수면·수행 연구는 평균적인 단기 효과를 보여주며 개인별 수면 필요량과 원인을 확정하지 않습니다.",
                "high", PaperAdvice.Status.ACTIONABLE, Arrays.asList("09#1", "09#2", "09#3")
        ));
    }

    private static void addProteinAdvice(PaperAdviceInput input, List<PaperAdvice> advice) {
        boolean belowReviewPoint = input.proteinGPerKg != null
                && input.proteinRecordedDays >= 7
                && input.proteinGPerKg < 1.6d;
        boolean relevantGoal = input.hasGoal("hypertrophy") || input.hasGoal("lean_gain")
                || input.hasGoal("recomposition") || input.hasGoal("max_strength");
        if (!belowReviewPoint || !relevantGoal) return;
        advice.add(new PaperAdvice(
                "NUT_PRO_001", "nutrition", "총 단백질 섭취량을 검토할 후보가 있습니다",
                String.format("현재 입력 단백질은 %.2fg/kg/day이며 목표는 %s입니다.", input.proteinGPerKg, input.goal),
                "식사 기록의 누락과 에너지 섭취를 먼저 확인한 뒤 총 단백질을 높일 수 있는지 검토하세요. 보충제만으로 해결한다고 가정하지 않습니다.",
                "1.6g/kg/day는 meta-analysis의 평균적 포화점 추정치에 가깝고 개인의 절대 최소량이나 보장된 목표량이 아닙니다.",
                "high", PaperAdvice.Status.ACTIONABLE, Arrays.asList("08#1", "08#2")
        ));
    }

    private static void addFailureAdvice(PaperAdviceInput input, List<PaperAdvice> advice) {
        boolean relevantGoal = input.hasGoal("hypertrophy") || input.hasGoal("max_strength");
        if (!relevantGoal || input.failureSetsRatio == null || input.failureSetsRatio <= 0.5d) return;
        advice.add(new PaperAdvice(
                "TRAIN_FAIL_001", "training", "모든 세트를 실패까지 수행하는지 점검하세요",
                String.format("실패 세트 비율이 %.0f%%로 기록되었습니다.", input.failureSetsRatio * 100d),
                "반복 실패를 필수조건으로 보지 말고, 주요 세트의 수행 품질·RIR·다음 세션 회복을 함께 확인하세요.",
                "실패훈련 연구의 효과는 목표·볼륨·훈련경력에 따라 달라지며 특정 비율을 보편적 금지선으로 확정하지 않습니다.",
                "high", PaperAdvice.Status.INFORMATIONAL, Arrays.asList("02#3", "01#3")
        ));
    }

    private static void addColdWaterAdvice(PaperAdviceInput input, List<PaperAdvice> advice) {
        boolean adaptationGoal = input.hasGoal("hypertrophy") || input.hasGoal("lean_gain")
                || input.hasGoal("recomposition");
        if (!adaptationGoal || !Boolean.TRUE.equals(input.coldWaterImmediatelyPostResistance)) return;
        advice.add(new PaperAdvice(
                "REC_COLD_003", "recovery", "근비대 목표에서 운동 직후 냉수욕은 기본값으로 두지 마세요",
                "저항운동 직후 냉수욕이 반복되는 것으로 입력되었습니다.",
                "장기 적응이 목표라면 이를 기본 회복법으로 자동 추천하지 말고, 경기 간 회복·열 스트레스 등 별도 목적과 trade-off를 확인하세요.",
                "특정 protocol의 소규모 연구이며 CWI의 모든 상황·모든 사람에게 장기 저하를 확정하지 않습니다.",
                "moderate", PaperAdvice.Status.INFORMATIONAL, Arrays.asList("09#9", "09#10")
        ));
    }

    private static boolean isLow(Integer value) {
        return value != null && value <= 2;
    }
}
