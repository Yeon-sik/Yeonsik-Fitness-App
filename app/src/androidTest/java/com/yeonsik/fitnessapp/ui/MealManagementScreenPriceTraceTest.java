package com.yeonsik.fitnessapp.ui;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.yeonsik.fitnessapp.data.ProductReadV1;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MealManagementScreenPriceTraceTest {
    @Test
    public void selectingAndClearingPriceTraceProductRestoresManualHierarchyFields() {
        MealProductSelectionDraft draft = new MealProductSelectionDraft(
                "직접 상품명", "직접 브랜드", "직접 서브브랜드", "직접 제조회사");
        ProductReadV1 product = new ProductReadV1(
                "80111111-1111-4111-8111-111111111111",
                "80222222-2222-4222-8222-222222222222",
                "PT 상품명", "PT 브랜드", "PT 제조회사", "PT 서브브랜드", "PT 판매처",
                null, null, null, null, null, null);

        draft.select(product);
        assertTrue(draft.isSelected());
        assertEquals(product.name, draft.name());
        assertEquals(product.brand, draft.brand());
        assertEquals(product.subBrandName, draft.subBrand());
        assertEquals(product.manufacturerName, draft.manufacturer());

        draft.clearSelection();
        assertFalse(draft.isSelected());
        assertEquals("직접 상품명", draft.name());
        assertEquals("직접 브랜드", draft.brand());
        assertEquals("직접 서브브랜드", draft.subBrand());
        assertEquals("직접 제조회사", draft.manufacturer());
    }
}
