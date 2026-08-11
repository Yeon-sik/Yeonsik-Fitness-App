package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityVerifiedSingleFoodTabTest {
    private static final String VERIFIED_SINGLE_FOOD_SEARCH_TAG =
            "verified-single-food-search-input";
    private static final String VERIFIED_SINGLE_FOOD_RESULTS_TAG =
            "verified-single-food-results";
    private static final String BROCCOLI = "브로콜리";
    private static final String SALMON_QUERY = "연어";
    private static final String RAW_SALMON = "연어회(홍연어·생것 기준)";
    private static final String GRILLED_SALMON = "연어구이(홍연어)";
    private static final String RAW_SEAFOOD_LABEL = "어류·해산물 · 생것";
    private static final String GRILLED_SEAFOOD_LABEL = "어류·해산물 · 구이";
    private static final String VERIFIED_BADGE = "공식 DB · K-FIND";

    @Test
    public void verifiedSingleFoodTabShowsBuiltInFoodAndAddsItToCurrentMealDraft() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();

                clickText(root, "새 끼니 기록");
                clickText(root, "단일 식품 등록");

                EditText searchInput = (EditText) findViewWithTag(
                        root,
                        VERIFIED_SINGLE_FOOD_SEARCH_TAG
                );
                assertNotNull(searchInput);
                searchInput.setText(BROCCOLI);

                View results = findViewWithTag(root, VERIFIED_SINGLE_FOOD_RESULTS_TAG);
                assertNotNull(results);
                assertNotNull(findText(results, BROCCOLI));
                assertNotNull(findText(results, VERIFIED_BADGE));

                clickText(results, BROCCOLI);
                assertTrue(countText(root, BROCCOLI) >= 2);
            });
        }
    }

    @Test
    public void verifiedSingleFoodSearchDistinguishesRawAndGrilledFish() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();

                clickText(root, "새 끼니 기록");
                clickText(root, "단일 식품 등록");

                EditText searchInput = (EditText) findViewWithTag(
                        root,
                        VERIFIED_SINGLE_FOOD_SEARCH_TAG
                );
                assertNotNull(searchInput);
                searchInput.setText(SALMON_QUERY);

                View results = findViewWithTag(root, VERIFIED_SINGLE_FOOD_RESULTS_TAG);
                assertNotNull(results);
                assertNotNull(findText(results, RAW_SALMON));
                assertNotNull(findText(results, GRILLED_SALMON));
                assertNotNull(findText(results, RAW_SEAFOOD_LABEL));
                assertNotNull(findText(results, GRILLED_SEAFOOD_LABEL));

                clickText(results, GRILLED_SALMON);
                assertTrue(countText(root, GRILLED_SALMON) >= 2);
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

    private static View findViewWithTag(View view, Object tag) {
        Object currentTag = view.getTag();
        if (tag.equals(currentTag)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findViewWithTag(group.getChildAt(index), tag);
                if (match != null) {
                    return match;
                }
            }
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

    private static int countText(View view, String text) {
        int count = 0;
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (text.contentEquals(textView.getText())) {
                count++;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                count += countText(group.getChildAt(index), text);
            }
        }
        return count;
    }
}
