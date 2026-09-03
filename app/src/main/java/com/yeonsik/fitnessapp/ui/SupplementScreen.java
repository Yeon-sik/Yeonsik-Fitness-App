package com.yeonsik.fitnessapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yeonsik.fitnessapp.state.FitnessScreen;
import com.yeonsik.fitnessapp.supplement.SupplementCatalog;
import com.yeonsik.fitnessapp.supplement.SupplementEvidence;
import com.yeonsik.fitnessapp.supplement.SupplementEvidenceCatalog;
import com.yeonsik.fitnessapp.supplement.SupplementPlan;
import com.yeonsik.fitnessapp.supplement.SupplementRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Supplement plan management and per-dose daily logging. */
public final class SupplementScreen extends BaseScreen {
    private LocalDate selectedDate;
    private final FormSystem formSystem;

    public SupplementScreen(ScreenHost host) {
        super(host);
        formSystem = new FormSystem(host.ui(), host.activity());
    }

    @Override
    public void render() {
        if (selectedDate == null) selectedDate = LocalDate.parse(host.today());
        SupplementRepository repository = host.supplementRepository();
        List<SupplementPlan> plans = repository.activePlans(selectedDate.toString());

        add(ui().textAction("‹ 피트니스", FitnessUi.COLOR_MUTED,
                () -> backOr(FitnessScreen.WORKOUT)), ui().fullWidthParams(0));
        screenHeader("매일 복용 기록", "영양제");

        TextView localOnly = ui().text(
                "이 기록은 현재 이 기기에만 저장됩니다. 설정의 로컬 백업에 포함되며 계정 동기화는 아직 지원하지 않습니다.",
                12, FitnessUi.COLOR_TERTIARY, false);
        localOnly.setPadding(0, 0, 0, ui().dp(12));
        add(localOnly);

        section("복용 날짜", "오늘", () -> {
            selectedDate = LocalDate.parse(host.today());
            host.rerender();
        });
        add(dateCard());

        LocalDate today = LocalDate.parse(host.today());
        SupplementRepository.AdherenceSummary adherence = repository.adherence(
                selectedDate.isAfter(today) ? today : selectedDate, 7);
        LinearLayout adherenceCard = ui().card();
        ui().cardHeader(adherenceCard, "최근 7일 이행", adherence.adherencePercent() + "%");
        adherenceCard.addView(ui().text(
                "예정 " + adherence.planned + " · 복용 " + adherence.taken
                        + " · 건너뜀 " + adherence.skipped + " · 미기록 " + adherence.unrecorded,
                13, FitnessUi.COLOR_MUTED, false));
        add(adherenceCard);

        if (selectedDate.equals(today)) section("복용 계획", "추가", () -> showPlanDialog(null));
        else section("복용 계획");
        if (plans.isEmpty()) {
            emptyState("등록된 영양제가 없습니다.", "종류와 브랜드, 용량, 복용법을 먼저 등록하세요.");
        } else {
            for (SupplementPlan plan : plans) add(planCard(plan, plans));
        }

        section("최근 7일 기록");
        List<SupplementRepository.HistoryEntry> history = repository.history(selectedDate, 7);
        if (history.isEmpty()) {
            emptyState("최근 복용 기록이 없습니다.", "복용 또는 건너뜀 버튼으로 날짜별 기록을 남기세요.");
        } else {
            for (SupplementRepository.HistoryEntry entry : history) add(historyRow(entry));
        }

        TextView disclaimer = ui().text(
                "표시되는 용량과 복용법은 사용자가 입력한 계획입니다. 의료 조언이나 권장 용량을 제공하지 않습니다.",
                12, FitnessUi.COLOR_TERTIARY, false);
        disclaimer.setPadding(0, ui().dp(22), 0, ui().dp(8));
        disclaimer.setLineSpacing(ui().dp(2), 1f);
        add(disclaimer);
    }

    private View dateCard() {
        LinearLayout card = ui().card();
        TextView date = ui().num(selectedDate.toString(), 20, FitnessUi.COLOR_TEXT, true);
        card.addView(date);

        Button previous = ui().button("이전 날", false, v -> {
            selectedDate = selectedDate.minusDays(1);
            host.rerender();
        });
        Button next = ui().button("다음 날", false, v -> {
            selectedDate = selectedDate.plusDays(1);
            host.rerender();
        });
        boolean canMoveNext = selectedDate.isBefore(LocalDate.parse(host.today()).plusDays(1));
        formSystem.disabled(next, !canMoveNext);
        card.addView(ui().buttonRow(previous, next), ui().fullWidthParams(ui().dp(12)));
        if (!canMoveNext) {
            card.addView(formSystem.helper("아직 기록할 수 없는 미래 날짜입니다."),
                    ui().fullWidthParams(ui().dp(4)));
        }
        return card;
    }

    private View planCard(SupplementPlan plan, List<SupplementPlan> allPlans) {
        LinearLayout card = ui().card();
        ui().cardHeader(card, plan.typeName, plan.brandName);
        SupplementEvidence evidence = SupplementEvidenceCatalog.forType(plan.typeCode);

        TextView dose = ui().text(
                "제품 1회 " + SupplementRepository.formatDose(plan.doseAmount, plan.doseUnit)
                        + " · " + plan.productForm + " · " + SupplementRepository.purposeLabel(plan.purposeCode),
                14, FitnessUi.COLOR_TEXT, true);
        card.addView(dose);
        if (plan.activeIngredientAmount != null) {
            card.addView(ui().text("주요 성분 " + SupplementRepository.formatDose(
                    plan.activeIngredientAmount, plan.activeIngredientUnit)
                    + (plan.ingredientDetails.isEmpty() ? "" : " · " + plan.ingredientDetails),
                    13, FitnessUi.COLOR_MUTED, false));
        }
        card.addView(ui().text("하루 " + plan.timesPerDay + "회 · "
                + String.join(" / ", plan.timingLabels), 13, FitnessUi.COLOR_MUTED, false));
        int sameTypeCount = 0;
        for (SupplementPlan candidate : allPlans) {
            if (candidate.typeCode.equals(plan.typeCode)) sameTypeCount++;
        }
        if (sameTypeCount > 1) {
            card.addView(ui().text(String.format(Locale.KOREAN,
                            "같은 종류가 %d개 등록되어 있습니다. 중복 성분과 총 섭취량을 확인하세요.",
                            sameTypeCount),
                    12, FitnessUi.COLOR_NEGATIVE, true));
        }
        TextView progress = ui().num(
                "복용 " + plan.takenCount + " / " + plan.timesPerDay
                        + " · 건너뜀 " + plan.skippedCount + " · 미기록 " + plan.unrecordedCount(),
                13, FitnessUi.COLOR_MUTED, false);
        progress.setPadding(0, ui().dp(10), 0, 0);
        card.addView(progress);

        TextView evidenceStatus = ui().text(
                "논문 근거 · " + evidence.statusLabel + " · " + evidence.reviewedOn + " 검토",
                12,
                evidence.status == SupplementEvidence.Status.VERIFIED_DIRECT
                        ? FitnessUi.COLOR_POSITIVE
                        : FitnessUi.COLOR_MUTED,
                true);
        evidenceStatus.setPadding(0, ui().dp(9), 0, 0);
        card.addView(evidenceStatus);

        Button taken = ui().button("복용 기록", true,
                v -> record(plan, SupplementRepository.STATUS_TAKEN));
        Button skipped = ui().button("건너뜀", false,
                v -> record(plan, SupplementRepository.STATUS_SKIPPED));
        boolean canRecord = plan.recordedCount() < plan.timesPerDay
                && !selectedDate.isAfter(LocalDate.parse(host.today()));
        formSystem.disabled(taken, !canRecord);
        formSystem.disabled(skipped, !canRecord);
        card.addView(ui().buttonRow(taken, skipped), ui().fullWidthParams(ui().dp(12)));

        Button undo = ui().button("마지막 기록 취소", false, v -> {
            runAction(() -> host.supplementRepository().undoLatestRecord(
                    plan.scheduleId, selectedDate.toString()), "마지막 기록을 취소했습니다.");
        });
        formSystem.disabled(undo, plan.recordedCount() <= 0);
        Button edit = ui().button("계획 수정", false, v -> showPlanDialog(plan));
        formSystem.disabled(edit, !plan.currentlyActive);
        card.addView(ui().buttonRow(undo, edit), ui().fullWidthParams(ui().dp(8)));

        card.addView(ui().button("논문 근거 보기", false,
                v -> showEvidenceDialog(plan, evidence)), ui().fullWidthParams(ui().dp(8)));

        card.addView(ui().button("효능·이상반응 점검", false,
                v -> showEffectCheckinDialog(plan)), ui().fullWidthParams(ui().dp(8)));

        if (plan.currentlyActive) {
            card.addView(ui().textAction("복용 계획 종료", FitnessUi.COLOR_NEGATIVE,
                    () -> confirmArchive(plan)), ui().fullWidthParams(ui().dp(4)));
        } else if (!plan.currentlyActive) {
            card.addView(ui().text("이 날짜에 적용됐던 종료된 계획", 11,
                    FitnessUi.COLOR_TERTIARY, false));
        }
        return card;
    }

    private View historyRow(SupplementRepository.HistoryEntry entry) {
        String status = SupplementRepository.STATUS_TAKEN.equals(entry.status) ? "복용" : "건너뜀";
        String actualTime = SupplementRepository.STATUS_TAKEN.equals(entry.status)
                ? formatTime(entry.takenAt) : "시각 없음";
        String backfill = "backfill".equals(entry.recordSource) ? " · 사후 입력" : "";
        String detail = entry.brandName + " · "
                + SupplementRepository.formatDose(entry.doseAmount, entry.doseUnit)
                + " · " + entry.timingLabel + " · " + entry.doseIndex + "회차 · " + actualTime + backfill;
        return ui().recordListRow(
                SupplementRepository.STATUS_TAKEN.equals(entry.status) ? "✓" : "–",
                entry.date + " · " + entry.typeName + " · " + status,
                detail,
                v -> showHistoryCorrectionDialog(entry));
    }

    private void record(SupplementPlan plan, String status) {
        runAction(() -> host.supplementRepository().recordNextDose(
                        plan.scheduleId, selectedDate.toString(), status),
                SupplementRepository.STATUS_TAKEN.equals(status)
                        ? "복용을 기록했습니다." : "건너뜀을 기록했습니다.");
    }

    private void runAction(Runnable action, String successMessage) {
        try {
            action.run();
            host.toast(successMessage);
            host.rerender();
        } catch (RuntimeException error) {
            host.toast(error.getMessage() == null ? "저장하지 못했습니다." : error.getMessage());
        }
    }

    private void confirmArchive(SupplementPlan plan) {
        ui().confirmSheet(
                "복용 계획 종료",
                plan.typeName + " 계획을 종료할까요? 이전 복용 기록은 유지됩니다.",
                "종료 후에도 이전 복용 기록은 유지됩니다.",
                "종료",
                () -> runAction(
                        () -> host.supplementRepository().archivePlan(plan.itemId),
                        "복용 계획을 종료했습니다.")
        );
    }

    private void showEvidenceDialog(SupplementPlan plan, SupplementEvidence evidence) {
        LinearLayout body = formSystem.column();
        body.setPadding(ui().dp(20), ui().dp(4), ui().dp(20), ui().dp(16));

        addEvidenceText(body, "근거 상태", evidence.statusLabel, true);
        addEvidenceText(body, "연구 요약", evidence.summaryKo, false);
        addEvidenceText(body, "이 기록에 적용할 때", evidence.applicabilityKo, false);
        addEvidenceText(body, "불확실성", evidence.limitationsKo, false);
        addEvidenceText(body, "안전 경계", evidence.safetyKo, false);

        TextView enteredPlan = ui().text(
                "사용자 입력 · 제품 " + SupplementRepository.formatDose(plan.doseAmount, plan.doseUnit)
                        + (plan.activeIngredientAmount == null ? "" : " · 주요 성분 "
                        + SupplementRepository.formatDose(plan.activeIngredientAmount, plan.activeIngredientUnit))
                        + " · 목적 " + SupplementRepository.purposeLabel(plan.purposeCode),
                12, FitnessUi.COLOR_TEXT, true);
        enteredPlan.setPadding(0, ui().dp(16), 0, 0);
        enteredPlan.setLineSpacing(ui().dp(2), 1f);
        body.addView(enteredPlan);
        TextView doseBoundary = ui().text(
                "논문은 성분 종류 수준의 근거입니다. 제품 형태·성분량·복용 목적이 연구 조건과 일치하거나 개인에게 효과적이고 안전하다는 판정이 아닙니다.",
                11, FitnessUi.COLOR_TERTIARY, false);
        doseBoundary.setPadding(0, ui().dp(4), 0, 0);
        body.addView(doseBoundary);

        TextView sourceHeader = ui().caption("검증 출처", FitnessUi.COLOR_MUTED);
        sourceHeader.setPadding(0, ui().dp(20), 0, ui().dp(4));
        body.addView(sourceHeader);
        for (SupplementEvidence.Source source : evidence.sources) {
            TextView sourceMeta = ui().text(
                    source.evidenceId + " · " + source.citation + " · " + source.verificationStatus,
                    11, FitnessUi.COLOR_MUTED, false);
            sourceMeta.setPadding(0, ui().dp(8), 0, ui().dp(4));
            sourceMeta.setLineSpacing(ui().dp(2), 1f);
            body.addView(sourceMeta);
            body.addView(ui().button(source.title + " 열기", false,
                    v -> openEvidenceUrl(source.url)), ui().fullWidthParams(0));
        }

        TextView reviewed = ui().text(
                "마지막 검토일 " + evidence.reviewedOn
                        + " · 효능·진단·의료 처방이 아닌 연구 적용성 안내",
                11, FitnessUi.COLOR_TERTIARY, false);
        reviewed.setPadding(0, ui().dp(18), 0, 0);
        body.addView(reviewed);

        ScrollView scroll = new ScrollView(host.activity());
        scroll.addView(body);
        ui().bottomSheet(plan.typeName + " 논문 근거", scroll, "닫기", () -> { }, null, null);
    }

    private void addEvidenceText(LinearLayout body, String label, String value, boolean strong) {
        TextView caption = ui().caption(label, FitnessUi.COLOR_MUTED);
        caption.setPadding(0, ui().dp(14), 0, ui().dp(4));
        body.addView(caption);
        TextView content = ui().text(value, 13, FitnessUi.COLOR_TEXT, strong);
        content.setLineSpacing(ui().dp(3), 1f);
        body.addView(content);
    }

    private void openEvidenceUrl(String url) {
        try {
            host.activity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException error) {
            host.toast("원문 링크를 열 수 없습니다.");
        }
    }

    private void showPlanDialog(SupplementPlan existing) {
        SupplementCatalog.Kind initialKind = existing == null
                ? SupplementCatalog.KINDS.get(0)
                : SupplementCatalog.require(existing.typeCode);
        String[] kindCode = {initialKind.code};
        String[] doseUnit = {existing == null ? "캡슐" : existing.doseUnit};
        String[] activeUnit = {existing == null || existing.activeIngredientUnit.isEmpty()
                ? "mg" : existing.activeIngredientUnit};
        String[] productForm = {existing == null ? "캡슐" : existing.productForm};
        String[] purposeCode = {existing == null ? "general_health" : existing.purposeCode};
        ArrayList<String> timingValues = new ArrayList<>();
        if (existing == null) timingValues.add("상관없음");
        else timingValues.addAll(existing.timingLabels);

        LinearLayout body = formSystem.column();
        body.setPadding(ui().dp(20), ui().dp(8), ui().dp(20), ui().dp(8));

        body.addView(formSystem.sectionTitle("제품 정보"), ui().fullWidthParams(0));

        Button kindPicker = formSystem.selector(initialKind.name, null);
        addDialogField(body, "영양제 종류", kindPicker);
        kindPicker.setOnClickListener(v -> {
            String[] labels = new String[SupplementCatalog.KINDS.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = SupplementCatalog.KINDS.get(i).name;
            int selectedIndex = 0;
            for (int i = 0; i < SupplementCatalog.KINDS.size(); i++) {
                if (SupplementCatalog.KINDS.get(i).code.equals(kindCode[0])) {
                    selectedIndex = i;
                    break;
                }
            }
            ui().choiceSheet("영양제 종류", Arrays.asList(labels), selectedIndex, which -> {
                if (which >= 0 && which < SupplementCatalog.KINDS.size()) {
                    SupplementCatalog.Kind selected = SupplementCatalog.KINDS.get(which);
                    kindCode[0] = selected.code;
                    kindPicker.setText(selected.name);
                }
            });
        });

        EditText brand = ui().input("예: 제품 라벨의 브랜드", existing == null ? "" : existing.brandName);
        addDialogField(body, "브랜드", brand);

        Button formPicker = formSystem.selector(productForm[0], null);
        addDialogField(body, "제품 형태", formPicker);
        formPicker.setOnClickListener(v -> showStringPicker("제품 형태",
                SupplementRepository.PRODUCT_FORMS, selected -> {
                    productForm[0] = selected;
                    formPicker.setText(selected);
                }));

        Button purposePicker = formSystem.selector(
                SupplementRepository.purposeLabel(purposeCode[0]), null);
        addDialogField(body, "복용 목적", purposePicker);
        purposePicker.setOnClickListener(v -> {
            int selectedIndex = SupplementRepository.PURPOSE_CODES.indexOf(purposeCode[0]);
            ui().choiceSheet("복용 목적", SupplementRepository.PURPOSE_LABELS,
                    selectedIndex, which -> {
                if (which < 0 || which >= SupplementRepository.PURPOSE_CODES.size()) {
                    return;
                }
                    purposeCode[0] = SupplementRepository.PURPOSE_CODES.get(which);
                    purposePicker.setText(SupplementRepository.PURPOSE_LABELS.get(which));
                    });
        });

        body.addView(formSystem.sectionTitle("섭취량"), ui().fullWidthParams(ui().dp(4)));
        EditText amount = formSystem.decimalInput("예: 500", existing == null
                ? "" : SupplementRepository.formatDose(existing.doseAmount, "").trim());
        addDialogField(body, "제품 1회 섭취량", amount);

        Button unitPicker = formSystem.selector(doseUnit[0], null);
        addDialogField(body, "제품 섭취량 단위", unitPicker);
        unitPicker.setOnClickListener(v -> showStringPicker("제품 섭취량 단위",
                SupplementRepository.DOSE_UNITS, selected -> {
                    doseUnit[0] = selected;
                    unitPicker.setText(selected);
                }));

        body.addView(formSystem.sectionTitle("주요 성분"), ui().fullWidthParams(ui().dp(4)));
        EditText activeAmount = formSystem.decimalInput("선택 입력, 예: 500",
                existing == null || existing.activeIngredientAmount == null ? ""
                        : SupplementRepository.formatDose(existing.activeIngredientAmount, "").trim());
        Button activeUnitPicker = formSystem.selector(activeUnit[0], null);
        activeUnitPicker.setOnClickListener(v -> showStringPicker("주요 성분 단위",
                SupplementRepository.DOSE_UNITS, selected -> {
                    activeUnit[0] = selected;
                    activeUnitPicker.setText(selected);
                }));

        EditText ingredientDetails = formSystem.textInput("예: 크레아틴 모노하이드레이트",
                existing == null ? "" : existing.ingredientDetails);
        LinearLayout ingredientFields = formSystem.column();
        addDialogField(ingredientFields, "주요 성분량 (선택)", activeAmount);
        addDialogField(ingredientFields, "주요 성분 단위", activeUnitPicker);
        addDialogField(ingredientFields, "성분 형태·상세 (선택)", ingredientDetails);
        boolean hasIngredientDetails = existing != null
                && (existing.activeIngredientAmount != null
                || !existing.ingredientDetails.trim().isEmpty());
        ingredientFields.setVisibility(hasIngredientDetails ? View.VISIBLE : View.GONE);
        Button ingredientToggle = ui().secondaryButton(
                hasIngredientDetails ? "주요 성분 정보 접기" : "주요 성분 정보 열기",
                null);
        ingredientToggle.setOnClickListener(v -> {
            boolean opening = ingredientFields.getVisibility() == View.GONE;
            ingredientFields.setVisibility(opening ? View.VISIBLE : View.GONE);
            ingredientToggle.setText(opening ? "주요 성분 정보 접기" : "주요 성분 정보 열기");
        });
        body.addView(ingredientToggle, ui().fullWidthParams(ui().dp(2)));
        body.addView(ingredientFields, ui().fullWidthParams(0));

        body.addView(formSystem.sectionTitle("복용 계획"), ui().fullWidthParams(ui().dp(4)));
        EditText times = formSystem.numberInput("1~6", existing == null
                ? "1" : String.valueOf(existing.timesPerDay));
        times.setInputType(InputType.TYPE_CLASS_NUMBER);
        addDialogField(body, "하루 횟수", times);

        Button timingPicker = formSystem.selector(timingSummary(timingValues), null);
        addDialogField(body, "회차별 복용 시점", timingPicker);
        timingPicker.setOnClickListener(v -> {
            try {
                int count = Integer.parseInt(times.getText().toString().trim());
                if (count < 1 || count > 6) throw new NumberFormatException();
                while (timingValues.size() < count) timingValues.add("상관없음");
                while (timingValues.size() > count) timingValues.remove(timingValues.size() - 1);
                showTimingSlotPicker(count, timingValues, 0,
                        () -> timingPicker.setText(timingSummary(timingValues)));
            } catch (NumberFormatException error) {
                host.toast("먼저 하루 횟수를 1~6으로 입력하세요.");
            }
        });

        ScrollView scroll = new ScrollView(host.activity());
        scroll.addView(body);
        ui().validatedSheet(
                existing == null ? "영양제 추가" : "복용 계획 수정",
                scroll,
                "저장",
                () -> {
                    try {
                        double parsedAmount = Double.parseDouble(amount.getText().toString().trim());
                        int parsedTimes = Integer.parseInt(times.getText().toString().trim());
                        while (timingValues.size() < parsedTimes) timingValues.add("상관없음");
                        while (timingValues.size() > parsedTimes) timingValues.remove(timingValues.size() - 1);
                        String activeText = activeAmount.getText().toString().trim();
                        Double parsedActive = activeText.isEmpty() ? null : Double.parseDouble(activeText);
                        SupplementRepository.PlanSaveResult result = host.supplementRepository().savePlan(
                                existing, kindCode[0], brand.getText().toString(), productForm[0],
                                purposeCode[0], parsedAmount, doseUnit[0], parsedActive, activeUnit[0],
                                ingredientDetails.getText().toString(), new ArrayList<>(timingValues),
                                "");
                        host.toast(existing == null ? "영양제를 추가했습니다."
                                : result.startsTomorrow ? "오늘 기록을 보존하고 내일부터 새 계획을 적용합니다."
                                : "복용 계획을 수정했습니다.");
                        host.rerender();
                        return true;
                    } catch (NumberFormatException error) {
                        host.toast("섭취량·성분량·하루 횟수를 올바른 숫자로 입력하세요.");
                        return false;
                    } catch (RuntimeException error) {
                        host.toast(error.getMessage() == null ? "저장하지 못했습니다." : error.getMessage());
                        return false;
                    }
                }
        );
    }

    private void showTimingSlotPicker(int count, ArrayList<String> values, int index, Runnable done) {
        if (index >= count) {
            done.run();
            return;
        }
        ui().choiceSheet(
                (index + 1) + "회차 복용 시점",
                SupplementRepository.TIMING_LABELS,
                -1,
                which -> {
                    if (which < 0 || which >= SupplementRepository.TIMING_LABELS.size()) {
                        return;
                    }
                    values.set(index, SupplementRepository.TIMING_LABELS.get(which));
                    showTimingSlotPicker(count, values, index + 1, done);
                }
        );
    }

    private String timingSummary(List<String> values) {
        return String.join(" / ", values);
    }

    private String formatTime(String value) {
        if (value == null || value.isEmpty()) return "시각 없음";
        try {
            return OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (RuntimeException error) {
            return "시각 확인 불가";
        }
    }

    private void showHistoryCorrectionDialog(SupplementRepository.HistoryEntry entry) {
        ui().choiceSheet(
                entry.typeName + " 기록 수정",
                Arrays.asList("복용으로 변경", "건너뜀으로 변경", "기록 삭제"),
                -1,
                which -> {
                    if (which == 2) {
                        runAction(() -> host.supplementRepository().deleteRecord(entry.id), "기록을 삭제했습니다.");
                    } else {
                        String status = which == 0 ? SupplementRepository.STATUS_TAKEN : SupplementRepository.STATUS_SKIPPED;
                        runAction(() -> host.supplementRepository().updateRecordStatus(entry.id, status), "기록을 수정했습니다.");
                    }
                }
        );
    }

    private void showEffectCheckinDialog(SupplementPlan plan) {
        SupplementRepository.EffectCheckin latest = host.supplementRepository().latestEffectCheckin(plan.itemId);
        int[] score = {latest == null ? 3 : latest.effectScore};
        LinearLayout body = formSystem.column();
        body.setPadding(ui().dp(20), ui().dp(8), ui().dp(20), ui().dp(8));
        Button scorePicker = formSystem.selector(score[0] + "점", null);
        addDialogField(body, "최근 체감 (1 낮음 · 5 높음)", scorePicker);
        scorePicker.setOnClickListener(v -> ui().choiceSheet(
                "체감 점수",
                Arrays.asList("1점", "2점", "3점", "4점", "5점"),
                score[0] - 1,
                which -> {
                    if (which < 0 || which >= 5) {
                        return;
                    }
                    score[0] = which + 1;
                    scorePicker.setText(String.format(Locale.KOREAN, "%d점", score[0]));
                }
        ));
        EditText adverse = ui().input("예: 속 불편, 두통 (없으면 비움)",
                latest == null ? "" : latest.adverseEffects);
        addDialogField(body, "이상반응", adverse);
        EditText note = ui().input("수면·회복·운동 변화 등",
                latest == null ? "" : latest.note);
        addDialogField(body, "메모", note);
        TextView boundary = formSystem.helper(
                "체감 기록은 인과관계나 의학적 효능을 증명하지 않습니다. "
                        + "심한 이상반응은 복용을 중단하고 전문가와 상의하세요.");
        boundary.setPadding(0, ui().dp(12), 0, 0);
        body.addView(boundary);
        ui().validatedSheet(
                plan.typeName + " 경과 점검",
                body,
                "저장",
                () -> {
                    try {
                        host.supplementRepository().saveEffectCheckin(
                                plan.itemId,
                                selectedDate.toString(),
                                score[0],
                                adverse.getText().toString(),
                                note.getText().toString()
                        );
                        host.toast("경과를 저장했습니다.");
                        host.rerender();
                        return true;
                    } catch (RuntimeException error) {
                        host.toast(error.getMessage() == null
                                ? "경과를 저장하지 못했습니다." : error.getMessage());
                        return false;
                    }
                }
        );
    }

    private void addDialogField(LinearLayout body, String label, View field) {
        body.addView(formSystem.field(label, field), ui().fullWidthParams(ui().dp(6)));
    }

    private void showStringPicker(String title, List<String> values, ValueConsumer consumer) {
        ui().choiceSheet(title, values, -1, which -> {
            if (which >= 0 && which < values.size()) {
                consumer.accept(values.get(which));
            }
        });
    }

    private interface ValueConsumer {
        void accept(String value);
    }
}
