package com.yeonsik.fitnessapp.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.yeonsik.fitnessapp.development.DevelopmentGoal;
import com.yeonsik.fitnessapp.development.DevelopmentInsight;
import com.yeonsik.fitnessapp.development.DevelopmentReport;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 최근 로컬 기록을 근거로 다음 행동을 정리하는 발전 탭 MVP. */
public final class DevelopmentScreen extends BaseScreen {
    public DevelopmentScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        DevelopmentReport report = host.developmentRepository().buildReport(LocalDate.parse(host.today()));

        screenHeader("NEXT ACTION", "발전");
        add(heroCard());

        section("신체 정보", "수정", host::showDevelopmentBodyProfileDialog);
        add(bodyProfileCard(report));

        section("발전 목표", "수정", host::showDevelopmentGoalDialog);
        add(goalCard(report));

        section("우선 행동");
        if (report.insights.isEmpty()) {
            emptyState(
                    "현재 기록에서 추가로 경고할 우선 행동이 없습니다.",
                    "운동, 체중, 식사, 체크인을 계속 남기면 같은 기준으로 다시 점검합니다."
            );
        } else {
            for (int index = 0; index < report.insights.size() && index < 3; index++) {
                add(insightCard(index + 1, report.insights.get(index)));
            }
        }

        section("훈련 부위 근거");
        add(trainingEvidenceCard(report));

        section("영양·회복 근거");
        add(nutritionRecoveryCard(report));

        section("판단 근거 범위");
        add(coverageCard(report));
    }

    private View heroCard() {
        FitnessUi ui = ui();
        LinearLayout card = ui.heroCard();
        card.addView(ui.caption("RECENT RECORDS", FitnessUi.COLOR_FLOW_MUTED));
        TextView title = ui.text(
                "최근 기록으로 지금 할 일을 정합니다",
                23,
                FitnessUi.COLOR_FLOW_TEXT,
                true
        );
        title.setPadding(0, ui.dp(10), 0, 0);
        card.addView(title);
        TextView helper = ui.text(
                "의료 진단이나 영양 처방이 아니라, 저장된 기록에서 확인 가능한 행동만 최대 3개 제시합니다.",
                13,
                FitnessUi.COLOR_FLOW_MUTED,
                false
        );
        helper.setPadding(0, ui.dp(8), 0, 0);
        helper.setLineSpacing(ui.dp(3), 1f);
        card.addView(helper);
        return card;
    }

    private View bodyProfileCard(DevelopmentReport report) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "기준 신체 정보", "로컬 저장");

        String height = report.bodyProfile.heightCm == null
                ? "미입력"
                : String.valueOf(report.bodyProfile.heightCm);
        String weight = report.latestWeightKg == null
                ? "미기록"
                : FitnessUi.trimDouble(report.latestWeightKg);
        LinearLayout stats = ui.tileRow();
        stats.addView(
                ui.statTile("키", height, report.bodyProfile.heightCm == null ? "" : "cm", false,
                        v -> host.showDevelopmentBodyProfileDialog()),
                ui.tileParams(true)
        );
        stats.addView(
                ui.statTile("최근 체중", weight, report.latestWeightKg == null ? "" : "kg", false,
                        v -> host.showDevelopmentBodyProfileDialog()),
                ui.tileParams(false)
        );
        card.addView(stats, ui.fullWidthParams(ui.dp(10)));

        TextView helper = ui.text(
                report.latestWeightKg == null
                        ? "키와 오늘 체중을 입력하면 이후 점검의 기본 기준으로 사용합니다."
                        : "체중은 오늘 이전의 가장 최근 기록을 표시하며, 기존 체중 기록 흐름에 함께 저장됩니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        helper.setPadding(0, ui.dp(12), 0, 0);
        helper.setLineSpacing(ui.dp(3), 1f);
        card.addView(helper);
        return card;
    }

    private View goalCard(DevelopmentReport report) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "이번 집중", null);

        if (!report.goal.isConfigured()) {
            TextView empty = ui.text(
                    "목표, 주간 운동 횟수, 집중 부위를 설정해 주세요.",
                    14,
                    FitnessUi.COLOR_WARNING,
                    true
            );
            empty.setLineSpacing(ui.dp(3), 1f);
            card.addView(empty, ui.fullWidthParams(ui.dp(8)));
            card.addView(
                    ui.button("발전 목표 설정", true, v -> host.showDevelopmentGoalDialog()),
                    ui.fullWidthParams(ui.dp(14))
            );
            return card;
        }

        TextView objective = ui.text(report.goal.objectiveLabelKo(), 18, FitnessUi.COLOR_TEXT, true);
        card.addView(objective, ui.fullWidthParams(ui.dp(6)));

        List<View> rows = new ArrayList<>();
        rows.add(ui.recordListRow(
                "주",
                report.currentWeekCompletedWorkoutSessions + " / " + report.goal.weeklySessionsTarget + "회",
                "이번 주 완료 / 목표",
                null
        ));
        rows.add(ui.recordListRow(
                "집",
                report.goal.focusBodyPartLabelKo(),
                "집중 부위 · " + report.goalFocusBodyPartCompletedSets + "세트",
                null
        ));
        card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(12)));

        double ratio = report.goal.weeklySessionsTarget == null
                ? 0d
                : (double) report.currentWeekCompletedWorkoutSessions / report.goal.weeklySessionsTarget;
        card.addView(ui.progressBar(ratio, false), ui.fullWidthParams(ui.dp(12)));
        return card;
    }

    private View insightCard(int order, DevelopmentInsight insight) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();

        LinearLayout header = new LinearLayout(host.activity());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(ui.compactOrderBadge(order), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        TextView title = ui.text(insight.title, 16, FitnessUi.COLOR_TEXT, true);
        title.setPadding(ui.dp(10), 0, 0, 0);
        title.setLineSpacing(ui.dp(2), 1f);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));
        card.addView(header);

        TextView evidence = ui.text("근거 · " + insight.evidence, 13, FitnessUi.COLOR_MUTED, false);
        evidence.setPadding(0, ui.dp(10), 0, 0);
        evidence.setLineSpacing(ui.dp(3), 1f);
        card.addView(evidence);

        TextView nextAction = ui.text("다음 행동 · " + insight.nextAction, 13, FitnessUi.COLOR_TEXT, true);
        nextAction.setPadding(0, ui.dp(12), 0, 0);
        nextAction.setLineSpacing(ui.dp(3), 1f);
        card.addView(nextAction);

        TextView limitation = ui.text("판단 한계 · " + insight.limitation, 11, FitnessUi.COLOR_TERTIARY, false);
        limitation.setPadding(0, ui.dp(10), 0, 0);
        limitation.setLineSpacing(ui.dp(3), 1f);
        card.addView(limitation);

        String actionLabel = insightActionLabel(insight.category);
        if (!actionLabel.isEmpty()) {
            card.addView(
                    ui.button(actionLabel, false, v -> host.openDevelopmentInsightAction(insight)),
                    ui.fullWidthParams(ui.dp(14))
            );
        }
        return card;
    }

    private View trainingEvidenceCard(DevelopmentReport report) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(
                card,
                "최근 14일 완료 세트",
                report.recentWindowStart + " ~ " + report.recentWindowEnd
        );

        List<View> rows = new ArrayList<>();
        for (String bodyPart : DevelopmentGoal.FOCUS_BODY_PARTS) {
            int sets = report.strengthSetsForBodyPart(bodyPart);
            rows.add(ui.recordListRow(
                    sets > 0 ? "✓" : "·",
                    DevelopmentGoal.bodyPartLabelKo(bodyPart),
                    sets + "세트",
                    null
            ));
        }
        card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(10)));

        if (report.goal.isConfigured()) {
            String lastTrained = report.goalFocusBodyPartLastTrainedDate == null
                    ? "상세 완료 기록 없음"
                    : "마지막 상세 훈련 " + report.goalFocusBodyPartLastTrainedDate;
            TextView focus = ui.text(
                    "집중 부위 · " + report.goal.focusBodyPartLabelKo() + " · " + lastTrained,
                    12,
                    FitnessUi.COLOR_TERTIARY,
                    false
            );
            focus.setPadding(0, ui.dp(12), 0, 0);
            focus.setLineSpacing(ui.dp(3), 1f);
            card.addView(focus);
        }
        return card;
    }

    private View nutritionRecoveryCard(DevelopmentReport report) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        ui.cardHeader(card, "최근 14일 기록", null);

        List<View> rows = new ArrayList<>();
        rows.add(ui.recordListRow("식", report.mealRecordedDays + "일", "식사 기록일", null));
        rows.add(ui.recordListRow("체", report.checkInRecordedDays + "일", "컨디션 체크인", null));
        rows.add(ui.recordListRow("저", report.lowEnergyOrReadinessDays + "일", "에너지/준비도 2 이하", null));
        card.addView(ui.rowsCard(rows), ui.fullWidthParams(ui.dp(10)));

        TextView helper = ui.text(
                "MVP는 영양소 섭취량을 임의 처방하지 않고, 식사·체크인 기록 유무와 낮은 컨디션 반복만 점검합니다.",
                12,
                FitnessUi.COLOR_TERTIARY,
                false
        );
        helper.setPadding(0, ui.dp(12), 0, 0);
        helper.setLineSpacing(ui.dp(3), 1f);
        card.addView(helper);
        return card;
    }

    private View coverageCard(DevelopmentReport report) {
        FitnessUi ui = ui();
        LinearLayout card = ui.card();
        DevelopmentReport.DataCoverage coverage = report.dataCoverage;
        ui.cardHeader(card, "기록 커버리지", coverage.daysWithAnyData + " / " + coverage.windowDays + "일");

        double ratio = coverage.windowDays == 0
                ? 0d
                : (double) coverage.daysWithAnyData / coverage.windowDays;
        card.addView(ui.progressBar(ratio, false), ui.fullWidthParams(ui.dp(10)));

        TextView detail = ui.text(
                "운동 " + coverage.workoutRecordedDays + "일 · 식사 " + coverage.mealRecordedDays
                        + "일 · 체크인 " + coverage.checkInRecordedDays + "일 · 체중 "
                        + coverage.weightRecordedDays + "일",
                12,
                FitnessUi.COLOR_MUTED,
                false
        );
        detail.setPadding(0, ui.dp(12), 0, 0);
        detail.setLineSpacing(ui.dp(3), 1f);
        card.addView(detail);
        return card;
    }

    private static String insightActionLabel(String category) {
        if ("planning".equals(category)) return "목표 설정";
        if ("consistency".equals(category) || "focus".equals(category)) return "운동으로 이동";
        if ("recovery".equals(category) || "nutrition_logging".equals(category)) return "식사·체크인 열기";
        if ("coverage".equals(category)) return "기록 보완";
        return "";
    }
}
