package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 섭취 시점의 영양성분 스냅샷.
 *
 * <p>meal_record_items는 음식 DB를 참조만 하지 않고 섭취 당시 값을 통째로 복사해 둔다.
 * 이후 음식 DB의 값이 수정되거나 삭제돼도 과거 기록의 영양성분은 바뀌지 않는다.
 * food_id는 추적용으로만 남기며, 표시·집계는 전부 스냅샷 값으로 한다.</p>
 *
 * <p>SQLite 의존이 없는 순수 자바로 두어 단위 테스트가 스냅샷 규칙을 직접 검증한다.</p>
 */
public final class MealItemSnapshot {
    /** typed column으로 저장하는 영양소 키. 순서가 곧 컬럼 순서다. */
    public static final List<String> TYPED_KEYS = typedKeys();

    public final String foodId;
    public final String foodNameSnapshot;
    public final String brandSnapshot;
    public final String foodKindSnapshot;
    public final double quantity;
    public final String unit;
    public final double basisAmountSnapshot;
    public final String basisUnitSnapshot;
    public final String prepStateSnapshot;
    public final String sourceTypeSnapshot;
    public final String sourceReferenceSnapshot;
    public final String sourceVersionSnapshot;
    public final int foodDataVersionSnapshot;
    public final String compositionGroupKeySnapshot;
    public final String compositionRoleSnapshot;
    public final String compositionMemberIdSnapshot;
    public final NutritionProfile profile;
    public final int orderIndex;

    private MealItemSnapshot(
            MealCompositionItem item,
            int orderIndex,
            String compositionGroupKeySnapshot,
            String compositionRoleSnapshot,
            String compositionMemberIdSnapshot
    ) {
        NutritionFood food = item.food;
        this.foodId = food.id;
        this.foodNameSnapshot = food.name;
        this.brandSnapshot = food.brand;
        this.foodKindSnapshot = food.kind;
        this.quantity = item.quantity;
        this.unit = food.basisUnit;
        this.basisAmountSnapshot = food.basisAmount;
        this.basisUnitSnapshot = food.basisUnit;
        this.prepStateSnapshot = food.prepState;
        this.sourceTypeSnapshot = food.sourceType;
        this.sourceReferenceSnapshot = food.sourceReference;
        this.sourceVersionSnapshot = food.sourceVersion;
        this.foodDataVersionSnapshot = food.dataVersion;
        this.compositionGroupKeySnapshot = blankToNull(compositionGroupKeySnapshot);
        this.compositionRoleSnapshot = blankToNull(compositionRoleSnapshot);
        this.compositionMemberIdSnapshot = blankToNull(compositionMemberIdSnapshot);
        this.profile = item.profile;
        this.orderIndex = orderIndex;
    }

    public static MealItemSnapshot of(MealCompositionItem item, int orderIndex) {
        return of(item, orderIndex, null, null, null);
    }

    /** Creates a snapshot with the generic composition role used by variable menu members. */
    public static MealItemSnapshot of(
            MealCompositionItem item,
            int orderIndex,
            String compositionGroupKeySnapshot,
            String compositionRoleSnapshot,
            String compositionMemberIdSnapshot
    ) {
        if (item == null || item.food == null) {
            throw new IllegalArgumentException("Meal composition contains an empty food.");
        }
        if (orderIndex < 0) {
            throw new IllegalArgumentException("Order index cannot be negative.");
        }
        return new MealItemSnapshot(
                item,
                orderIndex,
                compositionGroupKeySnapshot,
                compositionRoleSnapshot,
                compositionMemberIdSnapshot
        );
    }

    public static List<MealItemSnapshot> of(List<MealCompositionItem> items) {
        List<MealItemSnapshot> snapshots = new ArrayList<>();
        if (items == null) {
            return snapshots;
        }
        int orderIndex = 0;
        for (MealCompositionItem item : items) {
            snapshots.add(of(item, orderIndex++));
        }
        return snapshots;
    }

    /**
     * typed column에 넣을 값. 모르는 영양소는 키가 존재하되 값이 null이라,
     * 호출부가 0 대신 NULL을 명시적으로 기록할 수 있다.
     */
    public Map<String, Double> typedNutritionColumns() {
        Map<String, Double> columns = new LinkedHashMap<>();
        for (String key : TYPED_KEYS) {
            columns.put(key, profile.value(key));
        }
        return columns;
    }

    /** 확장 테이블에 넣을 미네랄·비타민 행. 값을 아는 것만 담는다. */
    public List<MicronutrientRow> micronutrientRows() {
        List<MicronutrientRow> rows = new ArrayList<>();
        for (String code : profile.knownMicronutrientCodes()) {
            Double amount = profile.value(code);
            if (amount != null) {
                rows.add(new MicronutrientRow(code, amount, NutrientCode.unitOf(code)));
            }
        }
        return rows;
    }

    private static List<String> typedKeys() {
        List<String> keys = new ArrayList<>();
        keys.addAll(NutritionProfile.REQUIRED_KEYS);
        keys.addAll(NutritionProfile.RECOMMENDED_TYPED_KEYS);
        return Collections.unmodifiableList(keys);
    }

    private static String blankToNull(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static final class MicronutrientRow {
        public final String nutrientCode;
        public final double amount;
        public final String unit;

        MicronutrientRow(String nutrientCode, double amount, String unit) {
            this.nutrientCode = nutrientCode;
            this.amount = amount;
            this.unit = unit;
        }
    }
}
