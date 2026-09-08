package com.yeonsik.fitnessapp.ui;

import com.yeonsik.fitnessapp.data.ProductReadV1;

/** Restorable draft values for selecting and clearing a PriceTrace product. */
public final class MealProductSelectionDraft {
    private String manualName;
    private String manualBrand;
    private String manualSubBrand;
    private String manualManufacturer;
    private ProductReadV1 selected;

    public MealProductSelectionDraft(String name, String brand, String subBrand, String manufacturer) {
        manualName = text(name);
        manualBrand = text(brand);
        manualSubBrand = text(subBrand);
        manualManufacturer = text(manufacturer);
    }

    public void updateManual(String name, String brand, String subBrand, String manufacturer) {
        if (selected != null) return;
        manualName = text(name);
        manualBrand = text(brand);
        manualSubBrand = text(subBrand);
        manualManufacturer = text(manufacturer);
    }

    public void select(ProductReadV1 product) { selected = product; }
    public void clearSelection() { selected = null; }
    public boolean isSelected() { return selected != null; }
    public String name() { return selected == null ? manualName : text(selected.name); }
    public String brand() { return selected == null ? manualBrand : text(selected.brand); }
    public String subBrand() { return selected == null ? manualSubBrand : text(selected.subBrandName); }
    public String manufacturer() { return selected == null ? manualManufacturer : text(selected.manufacturerName); }

    private static String text(String value) { return value == null ? "" : value; }
}
