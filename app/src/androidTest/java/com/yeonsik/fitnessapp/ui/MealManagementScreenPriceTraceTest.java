package com.yeonsik.fitnessapp.ui;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.MainActivity;
import com.yeonsik.fitnessapp.config.SupabaseConfig;
import com.yeonsik.fitnessapp.data.ProductReadV1;
import com.yeonsik.fitnessapp.state.FitnessScreen;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Proxy;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MealManagementScreenPriceTraceTest {
    @Test
    public void selectingAndClearingPriceTraceProductRestoresManualHierarchyFields()
            throws Exception {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                ProductReadV1 product = new ProductReadV1(
                        "80111111-1111-4111-8111-111111111111",
                        "80222222-2222-4222-8222-222222222222",
                        "PT 상품명",
                        "PT 브랜드",
                        "PT 제조회사",
                        "PT 서브브랜드",
                        "PT 판매처",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );
                LinearLayout content = new LinearLayout(activity);
                content.setOrientation(LinearLayout.VERTICAL);
                ScreenHost host = fakeHost(activity, content, product);
                MealManagementScreen screen = new MealManagementScreen(host);
                View form;
                try {
                    java.lang.reflect.Method directFoodForm =
                            MealManagementScreen.class.getDeclaredMethod(
                                    "directFoodForm",
                                    boolean.class
                            );
                    directFoodForm.setAccessible(true);
                    form = (View) directFoodForm.invoke(screen, false);
                } catch (ReflectiveOperationException error) {
                    throw new AssertionError(error);
                }
                content.addView(form);

                EditText name = findEditTextWithHint(form, "예: 포카칩 오리지널");
                EditText brand = findEditTextWithHint(form, "예: 포카칩");
                EditText subBrand = findEditTextWithHint(form, "예: 오!감자, 솥반");
                EditText manufacturer = findEditTextWithHint(form, "예: 오리온, CJ제일제당");
                EditText query = findEditTextWithHint(form, "PriceTrace 상품명 검색");
                assertNotNull(name);
                assertNotNull(brand);
                assertNotNull(subBrand);
                assertNotNull(manufacturer);
                assertNotNull(query);

                String manualName = "직접 상품명";
                String manualBrand = "직접 브랜드";
                String manualSubBrand = "직접 서브브랜드";
                String manualManufacturer = "직접 제조회사";
                name.setText(manualName);
                brand.setText(manualBrand);
                subBrand.setText(manualSubBrand);
                manufacturer.setText(manualManufacturer);

                query.setText("PT 상품명");
                Button search = findButtonWithText(form, "PriceTrace 상품 불러오기");
                assertNotNull(search);
                search.performClick();

                Button choice = findButtonWithText(
                        form,
                        "PT 브랜드 · PT 서브브랜드 · PT 상품명"
                );
                assertNotNull(choice);
                choice.performClick();

                assertEquals(product.name, name.getText().toString());
                assertEquals(product.brand, brand.getText().toString());
                assertEquals(product.subBrandName, subBrand.getText().toString());
                assertEquals(product.manufacturerName, manufacturer.getText().toString());
                assertFalse(name.isEnabled());
                assertFalse(brand.isEnabled());
                assertFalse(subBrand.isEnabled());
                assertFalse(manufacturer.isEnabled());

                Button clear = findButtonWithText(form, "PriceTrace 선택 해제 · 직접 입력");
                assertNotNull(clear);
                clear.performClick();

                assertEquals(manualName, name.getText().toString());
                assertEquals(manualBrand, brand.getText().toString());
                assertEquals(manualSubBrand, subBrand.getText().toString());
                assertEquals(manualManufacturer, manufacturer.getText().toString());
                assertTrue(name.isEnabled());
                assertTrue(brand.isEnabled());
                assertTrue(subBrand.isEnabled());
                assertTrue(manufacturer.isEnabled());
            });
        }
    }

    private static ScreenHost fakeHost(
            MainActivity activity,
            LinearLayout content,
            ProductReadV1 product
    ) {
        SupabaseConfig priceTraceConfig = new SupabaseConfig(
                "https://example.com",
                "test-key",
                "",
                "",
                "",
                "",
                SupabaseConfig.LOCAL_SETTINGS_SOURCE
        );
        return (ScreenHost) Proxy.newProxyInstance(
                ScreenHost.class.getClassLoader(),
                new Class<?>[]{ScreenHost.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "activity":
                            return activity;
                        case "ui":
                            return activity.ui();
                        case "content":
                            return content;
                        case "today":
                            return activity.today();
                        case "currentScreen":
                            return FitnessScreen.MEALS;
                        case "repository":
                            return activity.repository();
                        case "nutritionCatalogRepository":
                            return activity.nutritionCatalogRepository();
                        case "priceTraceSupabaseConfig":
                            return priceTraceConfig;
                        case "searchPriceTraceProducts":
                            ((ScreenHost.ProductSearchCallback) args[1]).onComplete(
                                    Collections.singletonList(product)
                            );
                            return null;
                        case "loadPublicProductNutrition":
                            ((ScreenHost.PublicNutritionCallback) args[1]).onError(
                                    new IllegalStateException("test nutrition response")
                            );
                            return null;
                        default:
                            return defaultValue(method.getReturnType());
                    }
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return '\0';
        }
        return null;
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

    private static Button findButtonWithText(View view, String text) {
        if (view instanceof Button && text.contentEquals(((Button) view).getText())) {
            return (Button) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                Button match = findButtonWithText(group.getChildAt(index), text);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }
}
