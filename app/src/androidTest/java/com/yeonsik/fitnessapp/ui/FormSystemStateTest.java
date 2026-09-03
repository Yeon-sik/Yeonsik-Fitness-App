package com.yeonsik.fitnessapp.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.MainActivity;
import com.yeonsik.fitnessapp.data.NutritionProfile;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class FormSystemStateTest {
    @Test
    public void disabledThenEnabledRestoresOriginalState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FormSystem forms = new FormSystem(activity.ui(), activity);
                LinearLayout root = new LinearLayout(activity);
                TextView child = new TextView(activity);
                root.setEnabled(true);
                root.setAlpha(0.77f);
                root.setContentDescription("기존 설명");
                child.setEnabled(false);
                child.setAlpha(0.31f);
                child.setContentDescription("자식 설명");
                root.addView(child);

                forms.disabled(root, true);
                forms.disabled(root, true);

                assertFalse(root.isEnabled());
                assertFalse(child.isEnabled());
                assertEquals("기존 설명 · 사용할 수 없음", root.getContentDescription());

                forms.disabled(root, false);

                assertTrue(root.isEnabled());
                assertFalse(child.isEnabled());
                assertEquals(0.77f, root.getAlpha(), 0.001f);
                assertEquals(0.31f, child.getAlpha(), 0.001f);
                assertEquals("기존 설명", root.getContentDescription());
                assertEquals("자식 설명", child.getContentDescription());
            });
        }
    }

    @Test
    public void loadingRestoresViewAndChildStatesIncludingDisabledState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FormSystem forms = new FormSystem(activity.ui(), activity);
                LinearLayout root = new LinearLayout(activity);
                TextView child = new TextView(activity);
                root.setEnabled(false);
                root.setAlpha(0.66f);
                root.setContentDescription("원래 설명");
                child.setEnabled(true);
                child.setAlpha(0.22f);
                child.setContentDescription("자식 원래 설명");
                root.addView(child);

                forms.loading(root, true, "불러오는 중");
                assertFalse(root.isEnabled());
                assertFalse(child.isEnabled());
                forms.loading(root, false, null);

                assertFalse(root.isEnabled());
                assertTrue(child.isEnabled());
                assertEquals(0.66f, root.getAlpha(), 0.001f);
                assertEquals(0.22f, child.getAlpha(), 0.001f);
                assertEquals("원래 설명", root.getContentDescription());
                assertEquals("자식 원래 설명", child.getContentDescription());
            });
        }
    }

    @Test
    public void repeatedLoadingDoesNotAccumulateLoadingText() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FormSystem forms = new FormSystem(activity.ui(), activity);
                TextView root = new TextView(activity);
                root.setContentDescription("기존 설명");

                forms.loading(root, true, "불러오는 중");
                forms.loading(root, true, "불러오는 중");
                assertEquals("불러오는 중", root.getContentDescription());
                forms.loading(root, true, "다시 불러오는 중");
                assertEquals("다시 불러오는 중", root.getContentDescription());

                forms.loading(root, false, null);
                assertEquals("기존 설명", root.getContentDescription());
            });
        }
    }

    @Test
    public void nutrientKeyUsesSameLabelAndUnitForReadOnlyAndEditableRows() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                FormSystem forms = new FormSystem(activity.ui(), activity);
                View readOnly = forms.nutrientRow(NutritionProfile.SODIUM_MG, "120");
                NutritionRow editable = forms.nutrientInputRow(
                        NutritionProfile.SODIUM_MG,
                        "120"
                );

                assertEquals("나트륨", NutritionRow.displayLabel(NutritionProfile.SODIUM_MG));
                assertEquals("mg", NutritionRow.displayUnit(NutritionProfile.SODIUM_MG));
                assertEquals("나트륨 값", readOnly.getContentDescription());
                assertEquals("나트륨 입력", editable.view().getContentDescription());
                assertEquals("나트륨 입력값", editable.inputField().getContentDescription());
                assertEquals(
                        "mg",
                        ((TextView) ((ViewGroup) readOnly).getChildAt(2)).getText().toString()
                );
                assertEquals(
                        "mg",
                        ((TextView) ((ViewGroup) editable.view()).getChildAt(2))
                                .getText().toString()
                );
            });
        }
    }

    @Test
    public void nutritionInputShowsInlineErrorAndClearsAfterFieldEdit() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                NutritionInputSection section = new NutritionInputSection(activity.ui(), activity);
                try {
                    section.profile();
                    fail("빈 필수 영양성분 입력은 실패해야 합니다.");
                } catch (IllegalArgumentException expected) {
                    // The existing validation exception remains the source of the message.
                }

                TextView error = findTextViewContaining(section.view(), "필수 입력");
                assertNotNull(error);
                EditText input = findFirstEditText(section.view());
                assertNotNull(input);
                input.setText("100");
                assertEquals(View.GONE, error.getVisibility());
            });
        }
    }

    private static EditText findFirstEditText(View view) {
        if (view instanceof EditText) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText input = findFirstEditText(group.getChildAt(index));
                if (input != null) {
                    return input;
                }
            }
        }
        return null;
    }

    private static TextView findTextViewContaining(View view, String text) {
        if (view instanceof TextView && !(view instanceof EditText)) {
            CharSequence value = ((TextView) view).getText();
            if (((TextView) view).getVisibility() == View.VISIBLE
                    && value != null
                    && value.toString().contains(text)) {
                return (TextView) view;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView result = findTextViewContaining(group.getChildAt(index), text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
