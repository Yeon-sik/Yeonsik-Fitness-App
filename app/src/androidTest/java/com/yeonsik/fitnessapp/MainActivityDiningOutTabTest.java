package com.yeonsik.fitnessapp;

import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.data.CompositionGroup;
import com.yeonsik.fitnessapp.data.CompositionGroupType;
import com.yeonsik.fitnessapp.data.CompositionMember;
import com.yeonsik.fitnessapp.data.CompositionTemplate;
import com.yeonsik.fitnessapp.data.DiningOutIdentity;
import com.yeonsik.fitnessapp.data.NutritionProfile;
import com.yeonsik.fitnessapp.data.NutritionUnit;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;

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
                EditText menu = findEditTextWithContentDescription(root, "외식 메뉴 1 이름");
                EditText carbs = findEditTextWithContentDescription(root, "외식 메뉴 1 탄수화물");
                EditText protein = findEditTextWithContentDescription(root, "외식 메뉴 1 단백질");
                EditText fat = findEditTextWithContentDescription(root, "외식 메뉴 1 지방");
                EditText calories = findEditTextWithContentDescription(root, "외식 메뉴 1 칼로리");
                EditText sodium = findEditTextWithContentDescription(root, "외식 메뉴 1 나트륨");
                EditText sugars = findEditTextWithContentDescription(root, "외식 메뉴 1 당류");
                EditText saturatedFat = findEditTextWithContentDescription(root, "외식 메뉴 1 포화지방");
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

    @Test
    public void diningOutMenuAddCreatesAnotherTopLevelMenu() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "메뉴 추가");
                assertNotNull(findText(root, "메뉴 2"));
                assertNotNull(findEditTextWithContentDescription(root, "외식 메뉴 2 이름"));
                clickText(root, "메뉴 추가");
                assertNotNull(findText(root, "메뉴 3"));
                assertNotNull(findEditTextWithContentDescription(root, "외식 메뉴 3 이름"));
            });
        }
    }

    @Test
    public void switchingDiningOutMenusKeepsOptionsIsolatedAfterRerender() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");

                clickText(root, "옵션 추가");
                clickText(root, "반찬");
                clickText(root, "저장 옵션 없이 직접 입력");
                findEditTextWithContentDescription(root, "외식 옵션 1").setText("첫 메뉴 반찬");

                clickText(root, "메뉴 추가");
                clickText(root, "옵션 추가");
                clickText(root, "반찬");
                clickText(root, "저장 옵션 없이 직접 입력");
                findEditTextWithContentDescription(root, "외식 옵션 1").setText("둘째 메뉴 반찬");

                clickButtonText(root, "메뉴 1");
                assertEquals(
                        "첫 메뉴 반찬",
                        findEditTextWithContentDescription(root, "외식 옵션 1")
                                .getText().toString()
                );
                clickButtonText(root, "메뉴 2");
                assertEquals(
                        "둘째 메뉴 반찬",
                        findEditTextWithContentDescription(root, "외식 옵션 1")
                                .getText().toString()
                );
            });
        }
    }

    @Test
    public void deletingDiningOutOptionDoesNotResurrectAfterRerender() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "옵션 추가");
                clickText(root, "반찬");
                clickText(root, "저장 옵션 없이 직접 입력");

                View delete = findViewWithContentDescription(root, "외식 옵션 1 삭제");
                assertNotNull(delete);
                delete.performClick();
                assertEquals(null, findEditTextWithContentDescription(root, "외식 옵션 1"));
                assertNotNull(findText(root, "추가 옵션 없음"));
            });
        }
    }

    @Test
    public void applyingDiningOutTemplateKeepsTheAppliedDraftAfterRerender() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                String userId = activity.repository().currentUserId();
                CompositionMember member = new CompositionMember(
                        "template-member",
                        null,
                        "김치",
                        "템플릿 식당",
                        1d,
                        NutritionUnit.SERVING,
                        true,
                        0,
                        null,
                        NutritionProfile.empty()
                );
                CompositionTemplate template = new CompositionTemplate(
                        "dining-out-rerender-template",
                        userId,
                        "템플릿 식당 · 템플릿 메뉴",
                        CompositionTemplate.KIND_DINING_OUT,
                        null,
                        null,
                        1,
                        Collections.singletonList(new CompositionGroup(
                                "template-banchan",
                                "template-banchan",
                                CompositionGroupType.BANCHAN.label(),
                                CompositionGroup.MODE_EXACTLY_ONE,
                                1,
                                1,
                                0,
                                Collections.singletonList(member)
                        ))
                );
                activity.repository().compositionTemplates().save(template);
                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "템플릿 불러오기");
                clickText(root, "템플릿 식당 · 템플릿 메뉴 · 그룹 1");
                clickText(root, "김치 · 0kcal");
                clickText(root, "적용");

                assertEquals(
                        "템플릿 식당",
                        findEditTextWithContentDescription(root, "가게 명")
                                .getText().toString()
                );
                assertEquals(
                        "템플릿 메뉴",
                        findEditTextWithContentDescription(root, "외식 메뉴 1 이름")
                                .getText().toString()
                );
                assertEquals(
                        "김치",
                        findEditTextWithContentDescription(root, "외식 옵션 1")
                                .getText().toString()
                );
            });
        }
    }

    @Test
    public void identityLessSavedMenuDoesNotOverrideExistingRestaurantScope() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DiningOutIdentity firstIdentity = identity(
                        "71111111-1111-4111-8111-111111111111",
                        "범위 식당 A",
                        "72222222-2222-4222-8222-222222222222",
                        "A 본점",
                        "73333333-3333-4333-8333-333333333333",
                        "범위 메뉴 A",
                        "74444444-4444-4444-8444-444444444444"
                );
                activity.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                        "범위 식당 A", "범위 메뉴 A", 500, 20d, 50d, 15d,
                        800d, 10d, 5d, firstIdentity
                );
                activity.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                        "범위 식당 B", "범위 메뉴 B", 450, 18d, 45d, 14d,
                        700d, 9d, 4d
                );

                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "저장 메뉴 불러오기");
                clickContentDescription(root, "범위 식당 A · 범위 메뉴 A 저장 외식 메뉴 불러오기");
                clickText(root, "메뉴 추가");
                clickContentDescription(root, "범위 식당 B · 범위 메뉴 B 저장 외식 메뉴 불러오기");

                assertEquals(
                        "범위 식당 A",
                        findEditTextWithContentDescription(root, "가게 명")
                                .getText().toString()
                );
                assertEquals(
                        "A 본점",
                        findEditTextWithContentDescription(root, "지점")
                                .getText().toString()
                );
                assertEquals(
                        "범위 메뉴 B",
                        findEditTextWithContentDescription(root, "외식 메뉴 2 이름")
                                .getText().toString()
                );
            });
        }
    }

    @Test
    public void identityLessLegacyTemplateDoesNotOverrideExistingRestaurantScope() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DiningOutIdentity firstIdentity = identity(
                        "75111111-1111-4111-8111-111111111111",
                        "템플릿 범위 식당 A",
                        "75222222-2222-4222-8222-222222222222",
                        "A 본점",
                        "75333333-3333-4333-8333-333333333333",
                        "템플릿 범위 메뉴 A",
                        "75444444-4444-4444-8444-444444444444"
                );
                activity.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                        "템플릿 범위 식당 A", "템플릿 범위 메뉴 A", 500, 20d, 50d, 15d,
                        800d, 10d, 5d, firstIdentity
                );
                CompositionTemplate template = new CompositionTemplate(
                        "identity-less-legacy-scope-template",
                        activity.repository().currentUserId(),
                        "템플릿 범위 식당 B · 템플릿 범위 메뉴 B",
                        CompositionTemplate.KIND_DINING_OUT,
                        null,
                        null,
                        1,
                        Collections.emptyList()
                );
                activity.repository().compositionTemplates().save(template);

                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "저장 메뉴 불러오기");
                clickContentDescription(
                        root,
                        "템플릿 범위 식당 A · 템플릿 범위 메뉴 A 저장 외식 메뉴 불러오기"
                );
                clickText(root, "메뉴 추가");
                clickText(root, "템플릿 불러오기");
                clickText(root, "템플릿 범위 식당 B · 템플릿 범위 메뉴 B · 그룹 0");
                clickText(root, "적용");

                assertEquals(
                        "템플릿 범위 식당 A",
                        findEditTextWithContentDescription(root, "가게 명")
                                .getText().toString()
                );
                assertEquals(
                        "A 본점",
                        findEditTextWithContentDescription(root, "지점")
                                .getText().toString()
                );
                assertEquals(
                        "템플릿 범위 메뉴 B",
                        findEditTextWithContentDescription(root, "외식 메뉴 2 이름")
                                .getText().toString()
                );
            });
        }
    }

    @Test
    public void differentPriceTraceRestaurantCannotBeLoadedIntoTheSameRecord() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                DiningOutIdentity firstIdentity = identity(
                        "76111111-1111-4111-8111-111111111111",
                        "혼합 차단 식당 A",
                        "76222222-2222-4222-8222-222222222222",
                        "A 본점",
                        "76333333-3333-4333-8333-333333333333",
                        "혼합 메뉴 A",
                        "76444444-4444-4444-8444-444444444444"
                );
                DiningOutIdentity otherIdentity = identity(
                        "77111111-1111-4111-8111-111111111111",
                        "혼합 차단 식당 B",
                        "77222222-2222-4222-8222-222222222222",
                        "B 본점",
                        "77333333-3333-4333-8333-333333333333",
                        "혼합 메뉴 B",
                        "77444444-4444-4444-8444-444444444444"
                );
                activity.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                        "혼합 차단 식당 A", "혼합 메뉴 A", 500, 20d, 50d, 15d,
                        800d, 10d, 5d, firstIdentity
                );
                activity.nutritionCatalogRepository().saveDiningOutMenuWithNutrition(
                        "혼합 차단 식당 B", "혼합 메뉴 B", 450, 18d, 45d, 14d,
                        700d, 9d, 4d, otherIdentity
                );

                activity.openMealManagement();
                View root = activity.getWindow().getDecorView();
                clickText(root, "새 끼니 기록");
                clickText(root, "외식");
                clickText(root, "저장 메뉴 불러오기");
                clickContentDescription(root, "혼합 차단 식당 A · 혼합 메뉴 A 저장 외식 메뉴 불러오기");
                clickText(root, "메뉴 추가");
                clickContentDescription(root, "혼합 차단 식당 B · 혼합 메뉴 B 저장 외식 메뉴 불러오기");

                assertEquals(
                        "혼합 차단 식당 A",
                        findEditTextWithContentDescription(root, "가게 명")
                                .getText().toString()
                );
                assertEquals(
                        "",
                        findEditTextWithContentDescription(root, "외식 메뉴 2 이름")
                                .getText().toString()
                );
            });
        }
    }

    private static DiningOutIdentity identity(
            String restaurantId,
            String restaurantName,
            String locationId,
            String branchName,
            String menuId,
            String menuName,
            String catalogProductId
    ) {
        return DiningOutIdentity.fromPriceTrace(
                restaurantId,
                restaurantName,
                locationId,
                branchName,
                menuId,
                menuName,
                catalogProductId
        );
    }

    private static void clickText(View root, String text) {
        TextView target = findText(root, text);
        assertNotNull(target);
        View clickable = clickableSelfOrAncestor(target);
        assertNotNull(clickable);
        clickable.performClick();
    }

    private static void clickContentDescription(View root, String description) {
        View target = findViewWithContentDescription(root, description);
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

    private static void clickButtonText(View root, String text) {
        View target = findButtonWithText(root, text);
        assertNotNull(target);
        target.performClick();
    }

    private static View findButtonWithText(View view, String text) {
        if (view instanceof android.widget.Button
                && text.contentEquals(((android.widget.Button) view).getText())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findButtonWithText(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static View findViewWithContentDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription())) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                View match = findViewWithContentDescription(group.getChildAt(index), description);
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
