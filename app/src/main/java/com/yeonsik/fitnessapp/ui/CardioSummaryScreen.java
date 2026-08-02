package com.yeonsik.fitnessapp.ui;

import android.widget.LinearLayout;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.cardio.CardioMetrics;
import com.yeonsik.fitnessapp.cardio.CardioRepository;
import com.yeonsik.fitnessapp.state.FitnessScreen;

/** 완료된 GPS 유산소의 로컬 거리·시간·측정 품질 요약. */
public final class CardioSummaryScreen extends BaseScreen {
    public CardioSummaryScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        String recordId = host.sessionState().activeRecordId();
        CardioRepository.SessionSnapshot snapshot = host.cardioRepository().session(recordId);

        screenHeader("CARDIO SUMMARY", snapshot == null ? "유산소 요약" : snapshot.activityType.labelKo());
        if (snapshot == null) {
            emptyState("이 기기에서 GPS 세부 기록을 찾지 못했습니다.",
                    "공유된 운동 요약은 일반 운동 기록에서 확인할 수 있습니다.");
            add(ui().button("기록으로 돌아가기", false,
                    v -> host.navigate(FitnessScreen.RECORDS)), ui().fullWidthParams(0));
            return;
        }

        int elapsedSeconds = snapshot.elapsedSeconds(System.currentTimeMillis());
        LinearLayout firstRow = ui().tileRow();
        firstRow.addView(ui().statTile(
                "이동 거리",
                CardioMetrics.formatDistanceKilometers(snapshot.distanceMeters),
                "km",
                true,
                null
        ), ui().tileParams(true));
        firstRow.addView(ui().statTile(
                "운동 시간",
                CardioMetrics.formatElapsed(elapsedSeconds),
                "일시정지 제외",
                false,
                null
        ), ui().tileParams(false));
        add(firstRow, ui().fullWidthParams(0));

        LinearLayout secondRow = ui().tileRow();
        boolean cycling = snapshot.activityType == CardioActivityType.CYCLING;
        secondRow.addView(ui().statTile(
                cycling ? "평균 속도" : "평균 페이스",
                cycling
                        ? CardioMetrics.formatAverageSpeed(elapsedSeconds, snapshot.distanceMeters)
                        : CardioMetrics.formatAveragePace(elapsedSeconds, snapshot.distanceMeters),
                cycling ? "km/h" : "분/km",
                false,
                null
        ), ui().tileParams(true));
        secondRow.addView(ui().statTile(
                "GPS 포인트",
                String.valueOf(snapshot.acceptedPointCount),
                "개 반영",
                false,
                null
        ), ui().tileParams(false));
        add(secondRow, ui().fullWidthParams(ui().dp(10)));

        section("데이터 범위");
        emptyState(
                "거리와 시간 요약은 운동 기록으로 저장되었습니다.",
                "원시 위·경도 경로는 이 기기에만 남고 Supabase 동기화 대상에서 제외됩니다."
        );

        buttonRow(
                ui().button("기록 보기", true, v -> host.navigate(FitnessScreen.RECORDS)),
                ui().button("피트니스", false, v -> host.navigate(FitnessScreen.WORKOUT)),
                ui().dp(8)
        );
        add(ui().button("메인", false, v -> host.navigate(FitnessScreen.HOME)),
                ui().fullWidthParams(ui().dp(8)));
    }
}
