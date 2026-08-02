package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.cardio.CardioActivityType;
import com.yeonsik.fitnessapp.state.FitnessScreen;

/** 유산소 화면: 기존 GPS 걷기·달리기·자전거 시작 옵션을 소유한다. */
public final class CardioScreen extends BaseScreen {
    public CardioScreen(ScreenHost host) {
        super(host);
    }

    @Override
    public void render() {
        add(ui().textAction("‹ 피트니스", FitnessUi.COLOR_MUTED,
                () -> host.navigate(FitnessScreen.WORKOUT)), ui().fullWidthParams(0));
        screenHeader("CARDIO", "유산소");

        section("GPS 유산소");
        add(ui().button("걷기 시작", false,
                v -> host.startCardioWorkout(CardioActivityType.WALKING)),
                ui().fullWidthParams(0));
        add(ui().button("달리기 시작", true,
                v -> host.startCardioWorkout(CardioActivityType.RUNNING)),
                ui().fullWidthParams(ui().dp(8)));
        add(ui().button("자전거 시작", false,
                v -> host.startCardioWorkout(CardioActivityType.CYCLING)),
                ui().fullWidthParams(ui().dp(8)));
    }
}
