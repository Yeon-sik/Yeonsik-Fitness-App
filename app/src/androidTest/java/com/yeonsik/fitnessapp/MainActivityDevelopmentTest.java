package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityDevelopmentTest {
    @Test
    public void developmentBottomTabOpensMvpSections() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();
                TextView developmentTab = findTextWithClickableParent(root, "발전");
                assertNotNull(developmentTab);
                assertTrue(((View) developmentTab.getParent()).isClickable());

                ((View) developmentTab.getParent()).performClick();

                assertNotNull(findText(root, "신체 정보"));
                assertNotNull(findText(root, "발전 목표"));
                assertNotNull(findText(root, "우선 행동"));
                assertNotNull(findText(root, "훈련 부위 근거"));
                assertNotNull(findText(root, "영양·회복 근거"));
                assertNotNull(findText(root, "판단 근거 범위"));
            });
        }
    }

    private static TextView findTextWithClickableParent(View view, String text) {
        TextView candidate = findText(view, text);
        while (candidate != null) {
            if (candidate.getParent() instanceof View && ((View) candidate.getParent()).isClickable()) {
                return candidate;
            }
            candidate = findTextAfter(view, text, candidate);
        }
        return null;
    }

    private static TextView findText(View view, String text) {
        return findTextAfter(view, text, null);
    }

    private static TextView findTextAfter(View view, String text, TextView previous) {
        boolean[] passedPrevious = {previous == null};
        return findTextRecursive(view, text, previous, passedPrevious);
    }

    private static TextView findTextRecursive(
            View view,
            String text,
            TextView previous,
            boolean[] passedPrevious
    ) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (!passedPrevious[0]) {
                if (textView == previous) {
                    passedPrevious[0] = true;
                }
            } else if (text.contentEquals(textView.getText())) {
                return textView;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findTextRecursive(group.getChildAt(index), text, previous, passedPrevious);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
