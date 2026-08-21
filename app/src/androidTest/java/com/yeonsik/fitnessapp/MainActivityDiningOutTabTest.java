package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MainActivityDiningOutTabTest {
    @Test
    public void diningOutTabSeparatesStoreAndMenuAndSavesTheEntry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();

                clickText(root, "새 끼니 기록");
                clickText(root, "외식");

                EditText store = findEditTextWithContentDescription(root, "가게 명");
                EditText branch = findEditTextWithContentDescription(root, "지점");
                EditText menu = findEditTextWithContentDescription(root, "먹은 메뉴");
                EditText carbs = findEditTextWithContentDescription(root, "탄수화물");
                EditText protein = findEditTextWithContentDescription(root, "단백질");
                EditText fat = findEditTextWithContentDescription(root, "지방");
                EditText calories = findEditTextWithContentDescription(root, "칼로리");
                EditText sodium = findEditTextWithContentDescription(root, "나트륨");
                EditText sugars = findEditTextWithContentDescription(root, "당류");
                EditText saturatedFat = findEditTextWithContentDescription(root, "포화지방");
                assertNotNull(store);
                assertNotNull(branch);
                assertNotNull(menu);
                assertNotNull(carbs);
                assertNotNull(protein);
                assertNotNull(fat);
                assertNotNull(calories);
                assertNotNull(sodium);
                assertNotNull(sugars);
                assertNotNull(saturatedFat);
                store.setText("테스트 외식 가게");
                branch.setText("테스트 지점");
                menu.setText("테스트 메뉴");
                calories.setText("620");
                carbs.setText("70");
                protein.setText("40");
                fat.setText("20");
                sodium.setText("900");
                sugars.setText("12");
                saturatedFat.setText("8");

                clickText(root, "메뉴 저장하고 기록");

                assertNotNull(findText(root, "테스트 외식 가게 · 테스트 지점 · 테스트 메뉴"));
                assertNotNull(findTextContaining(root, "외식 · 영양 추정"));
                assertEquals(1, activity.nutritionCatalogRepository()
                        .searchFoods("테스트 메뉴").size());
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

    private static EditText findEditTextWithContentDescription(View view, String description) {
        if (view instanceof EditText && description.contentEquals(view.getContentDescription())) {
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

    private static TextView findText(View view, String text) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (text.contentEquals(textView.getText())) {
                return textView;
            }
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

    private static TextView findTextContaining(View view, String text) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getText() != null && textView.getText().toString().contains(text)) {
                return textView;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                TextView match = findTextContaining(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
