package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityHologramAnimationTest {
    @Test
    public void workoutSelectionUsesStaticMarkerAndClearsOnTabChange() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View root = activity.getWindow().getDecorView();

                View workoutTab = clickBottomTab(root, "피트니스");
                assertTrue(workoutTab.isSelected());
                View workoutMarker = activeMarker(workoutTab);
                assertNotNull(workoutMarker);
                assertEquals(View.VISIBLE, workoutMarker.getVisibility());

                View recordsTab = clickBottomTab(root, "기록");
                assertFalse(workoutTab.isSelected());
                assertEquals(View.INVISIBLE, workoutMarker.getVisibility());
                assertTrue(recordsTab.isSelected());
                assertEquals(View.VISIBLE, activeMarker(recordsTab).getVisibility());
            });
        }
    }

    private static View clickBottomTab(View root, String label) {
        TextView tab = findTextWithClickableParent(root, label);
        assertNotNull(tab);
        View area = (View) tab.getParent();
        area.performClick();
        return area;
    }

    private static View activeMarker(View area) {
        assertTrue(area instanceof ViewGroup);
        ViewGroup group = (ViewGroup) area;
        assertTrue(group.getChildCount() > 0);
        View markerSlot = group.getChildAt(0);
        assertTrue(markerSlot instanceof ViewGroup);
        ViewGroup slot = (ViewGroup) markerSlot;
        assertTrue(slot.getChildCount() > 0);
        return slot.getChildAt(0);
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
