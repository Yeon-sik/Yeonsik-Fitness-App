package com.yeonsik.fitnessapp.supplement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One effective-dated supplement plan version. */
public final class SupplementPlan {
    public final String itemId;
    public final String scheduleId;
    public final String typeCode;
    public final String typeName;
    public final String brandName;
    public final String productForm;
    public final String purposeCode;
    public final double doseAmount;
    public final String doseUnit;
    public final Double activeIngredientAmount;
    public final String activeIngredientUnit;
    public final String ingredientDetails;
    public final int timesPerDay;
    public final String timingLabel;
    public final List<String> timingLabels;
    public final String effectiveFrom;
    public final String effectiveTo;
    public final boolean currentlyActive;
    public final int takenCount;
    public final int skippedCount;

    public SupplementPlan(
            String itemId,
            String scheduleId,
            String typeCode,
            String typeName,
            String brandName,
            String productForm,
            String purposeCode,
            double doseAmount,
            String doseUnit,
            Double activeIngredientAmount,
            String activeIngredientUnit,
            String ingredientDetails,
            int timesPerDay,
            String timingLabel,
            List<String> timingLabels,
            String effectiveFrom,
            String effectiveTo,
            boolean currentlyActive,
            int takenCount,
            int skippedCount
    ) {
        this.itemId = itemId;
        this.scheduleId = scheduleId;
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.brandName = brandName;
        this.productForm = productForm;
        this.purposeCode = purposeCode;
        this.doseAmount = doseAmount;
        this.doseUnit = doseUnit;
        this.activeIngredientAmount = activeIngredientAmount;
        this.activeIngredientUnit = activeIngredientUnit;
        this.ingredientDetails = ingredientDetails;
        this.timesPerDay = timesPerDay;
        this.timingLabel = timingLabel;
        this.timingLabels = Collections.unmodifiableList(new ArrayList<>(timingLabels));
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.currentlyActive = currentlyActive;
        this.takenCount = takenCount;
        this.skippedCount = skippedCount;
    }

    public int recordedCount() {
        return takenCount + skippedCount;
    }

    public int unrecordedCount() {
        return Math.max(0, timesPerDay - recordedCount());
    }
}
