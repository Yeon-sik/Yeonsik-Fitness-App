package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.state.FitnessScreen;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MainActivityMealDraftTest {
    private static final String MENU_NAME_HINT = "메뉴 이름 (예: 계란 볶음밥)";
    private static final String SINGLE_FOOD_HINT = "단일 식품 이름 (예: 현미밥, 구운 닭가슴살)";
    private static final String FINISHED_PRODUCT_HINT = "예: 포카칩 오리지널";

    @Test
    public void mealBuilderDraftSurvivesNavigationCloseReopenAndExplicitReset() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "직접 만든 메뉴 추가");

                EditText menuName = findEditTextWithHint(root, MENU_NAME_HINT);
                assertNotNull(menuName);
                menuName.setText("내 메뉴");

                activity.navigate(FitnessScreen.WORKOUT);
                activity.openMealManagement();
                root = activity.getWindow().getDecorView();
                assertEquals("내 메뉴", findEditTextWithHint(root, MENU_NAME_HINT)
                        .getText().toString());

                clickText(root, "입력 닫기");
                clickText(root, "입력 열기");
                assertEquals("내 메뉴", findEditTextWithHint(root, MENU_NAME_HINT)
                        .getText().toString());

                clickText(root, "초기화");
                clickText(root, "직접 만든 메뉴 추가");
                assertEquals("", findEditTextWithHint(root, MENU_NAME_HINT)
                        .getText().toString());
            });
        }
    }

    @Test
    public void directFoodAndFinishedProductDraftsSurviveNavigationAndReset() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");

                EditText singleFood = findEditTextWithHint(root, SINGLE_FOOD_HINT);
                EditText calories = findEditTextWithContentDescription(root, "칼로리 필수");
                assertNotNull(singleFood);
                assertNotNull(calories);
                singleFood.setText("내 단일 식품");
                calories.setText("123");

                activity.navigate(FitnessScreen.WORKOUT);
                activity.openMealManagement();
                root = activity.getWindow().getDecorView();
                assertEquals("내 단일 식품", findEditTextWithHint(root, SINGLE_FOOD_HINT)
                        .getText().toString());
                assertEquals("123", findEditTextWithContentDescription(root, "칼로리 필수")
                        .getText().toString());

                clickLastButtonWithText(root, "완제품");
                EditText finishedProduct = findEditTextWithHint(root, FINISHED_PRODUCT_HINT);
                assertNotNull(finishedProduct);
                finishedProduct.setText("내 완제품");

                activity.navigate(FitnessScreen.WORKOUT);
                activity.openMealManagement();
                root = activity.getWindow().getDecorView();
                assertEquals("내 완제품", findEditTextWithHint(root, FINISHED_PRODUCT_HINT)
                        .getText().toString());

                clickText(root, "입력 닫기");
                clickText(root, "입력 열기");
                assertEquals("내 완제품", findEditTextWithHint(root, FINISHED_PRODUCT_HINT)
                        .getText().toString());

                clickText(root, "초기화");
                assertEquals("", findEditTextWithHint(root, FINISHED_PRODUCT_HINT)
                        .getText().toString());
            });
        }
    }

    private static void clickText(View root, String text) {
        TextView target = findText(root, text);
        assertNotNull(target);
        View clickable = clickableSelfOrAncestor(target);
        assertNotNull(clickable);
        clickable.performClick();
    }

    private static void clickLastButtonWithText(View root, String text) {
        Button target = findLastButtonWithText(root, text);
        assertNotNull(target);
        target.performClick();
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findText(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Button findLastButtonWithText(View view, String text) {
        Button match = null;
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) {
            match = (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button nested = findLastButtonWithText(group.getChildAt(index), text);
                if (nested != null) {
                    match = nested;
                }
            }
        }
        return match;
    }

    private static EditText findEditTextWithHint(View view, String hint) {
        if (view instanceof EditText && hint.contentEquals(((EditText) view).getHint())) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText match = findEditTextWithHint(group.getChildAt(index), hint);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static EditText findEditTextWithContentDescription(View view, String description) {
        if (view instanceof EditText
                && description.contentEquals(view.getContentDescription())) {
            return (EditText) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                EditText match = findEditTextWithContentDescription(
                        group.getChildAt(index),
                        description
                );
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static View clickableSelfOrAncestor(View view) {
        View current = view;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            if (!(current.getParent() instanceof View)) {
                return null;
            }
            current = (View) current.getParent();
        }
        return null;
    }
}