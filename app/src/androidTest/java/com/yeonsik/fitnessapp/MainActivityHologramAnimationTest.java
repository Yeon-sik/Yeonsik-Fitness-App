package com.yeonsik.fitnessapp;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MainActivityHologramAnimationTest {
    @Test
    public void workoutAndSelectedDateBordersAnimateAndStopWhenDetached() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();

                clickBottomTab(root, "피트니스");
                Animatable workoutAnimation = findRunningAnimatedBackground(root);
                assertNotNull(workoutAnimation);

                clickBottomTab(root, "기록");
                assertFalse(workoutAnimation.isRunning());
                assertNotNull(findRunningAnimatedBackground(root));
            });
        }
    }

    private static void clickBottomTab(View root, String label) {
        TextView tab = findTextWithClickableParent(root, label);
        assertNotNull(tab);
        ((View) tab.getParent()).performClick();
    }

    private static Animatable findRunningAnimatedBackground(View view) {
        Drawable background = view.getBackground();
        if (background instanceof Animatable && ((Animatable) background).isRunning()) {
            return (Animatable) background;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Animatable match = findRunningAnimatedBackground(group.getChildAt(index));
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static TextView findTextWithClickableParent(View view, String text) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (text.contentEquals(textView.getText())
                    && textView.getParent() instanceof View
                    && ((View) textView.getParent()).isClickable()) {
                return textView;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findTextWithClickableParent(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
