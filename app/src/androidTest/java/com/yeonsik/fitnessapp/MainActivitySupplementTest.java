package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MainActivitySupplementTest {
    @Test
    public void fitnessTabOpensSupplementDailyLog() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                clickText(root, "피트니스");
                clickText(root, "영양제");

                assertNotNull(findText(root, "매일 복용 기록"));
                assertNotNull(findText(root, "복용 날짜"));
                assertNotNull(findText(root, "복용 계획"));
                assertNotNull(findText(root, "최근 7일 기록"));
            });
        }
    }

    private static void clickText(View root, String text) {
        TextView target = findText(root, text);
        assertNotNull(target);
        View clickable = target;
        while (!clickable.isClickable() && clickable.getParent() instanceof View) {
            clickable = (View) clickable.getParent();
        }
        clickable.performClick();
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
