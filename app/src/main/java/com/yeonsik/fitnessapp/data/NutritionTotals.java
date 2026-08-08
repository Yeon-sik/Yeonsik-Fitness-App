package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 여러 음식의 영양성분 합계.
 *
 * <p>모름(NULL)을 0으로 더해 버리면 "나트륨 300mg"과 "나트륨 300mg + 모름 2건"이 같아 보인다.
 * 그래서 합계는 값뿐 아니라 <b>몇 개 항목이 값을 갖고 있었고 몇 개가 모름이었는지</b>를 함께 담는다.
 * 모든 항목이 값을 가진 영양소만 {@link Total#isComplete()}가 참이다.</p>
 */
public final class NutritionTotals {
    private final Map<String, Total> totals;
    private final int itemCount;

    private NutritionTotals(Map<String, Total> totals, int itemCount) {
        this.totals = Collections.unmodifiableMap(totals);
        this.itemCount = itemCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static NutritionTotals empty() {
        return new Builder().build();
    }

    public int itemCount() {
        return itemCount;
    }

    /** 값이 하나라도 기록된 영양소만 담긴다. 완전히 모르는 영양소는 조회 시 빈 합계를 돌려준다. */
    public Total total(String key) {
        Total total = totals.get(key == null ? "" : key.trim().toLowerCase(java.util.Locale.US));
        return total == null ? new Total(0, 0, itemCount) : total;
    }

    /** 부분적으로라도 값이 모인 영양소 키. */
    public List<String> keys() {
        return new ArrayList<>(totals.keySet());
    }

    /** 값이 모인 미네랄·비타민 코드. 사전 정의 순서를 따른다. */
    public List<String> knownMicronutrientCodes() {
        List<String> codes = new ArrayList<>();
        for (NutrientCode nutrient : NutrientCode.all()) {
            if (totals.containsKey(nutrient.code)) {
                codes.add(nutrient.code);
            }
        }
        return codes;
    }

    public double calories() {
        return total(NutritionProfile.CALORIES_KCAL).knownSum();
    }

    public double proteinGrams() {
        return total(NutritionProfile.PROTEIN_GRAMS).knownSum();
    }

    public double carbsGrams() {
        return total(NutritionProfile.CARBS_GRAMS).knownSum();
    }

    public double fatGrams() {
        return total(NutritionProfile.FAT_GRAMS).knownSum();
    }

    /** 합계를 다시 하나의 프로필로 본다. 일부라도 모름인 영양소는 키를 넣지 않는다. */
    public NutritionProfile toCompleteProfile() {
        NutritionProfile.Builder builder = NutritionProfile.builder();
        for (Map.Entry<String, Total> entry : totals.entrySet()) {
            if (entry.getValue().isComplete()) {
                builder.value(entry.getKey(), entry.getValue().knownSum());
            }
        }
        return builder.build();
    }

    public static final class Total {
        private final double knownSum;
        private final int knownCount;
        private final int missingCount;

        Total(double knownSum, int knownCount, int missingCount) {
            this.knownSum = knownSum;
            this.knownCount = knownCount;
            this.missingCount = missingCount;
        }

        /** 값을 아는 항목만 더한 합. 모름 항목은 포함되지 않는다. */
        public double knownSum() {
            return knownSum;
        }

        public int knownCount() {
            return knownCount;
        }

        public int missingCount() {
            return missingCount;
        }

        /** 모든 항목이 값을 가져 합계를 신뢰할 수 있는지. */
        public boolean isComplete() {
            return missingCount == 0 && knownCount > 0;
        }

        /** 신뢰할 수 있을 때만 값을, 아니면 null(모름)을 돌려준다. */
        public Double completeValue() {
            return isComplete() ? knownSum : null;
        }
    }

    public static final class Builder {
        private final Map<String, Accumulator> accumulators = new LinkedHashMap<>();
        private int itemCount;

        private Builder() {
        }

        public Builder add(NutritionProfile profile) {
            itemCount++;
            if (profile == null) {
                markAllMissing();
                return this;
            }
            for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
                if (!profile.isKnown(entry.getKey())) {
                    entry.getValue().missingCount++;
                }
            }
            for (Map.Entry<String, Double> entry : profile.asMap().entrySet()) {
                Accumulator accumulator = accumulators.get(entry.getKey());
                if (accumulator == null) {
                    // 이전 항목들은 이 영양소를 몰랐다는 사실을 소급해서 기록한다.
                    accumulator = new Accumulator();
                    accumulator.missingCount = itemCount - 1;
                    accumulators.put(entry.getKey(), accumulator);
                }
                accumulator.knownSum += entry.getValue();
                accumulator.knownCount++;
            }
            return this;
        }

        public NutritionTotals build() {
            Map<String, Total> totals = new LinkedHashMap<>();
            for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
                Accumulator accumulator = entry.getValue();
                totals.put(entry.getKey(), new Total(
                        accumulator.knownSum,
                        accumulator.knownCount,
                        accumulator.missingCount
                ));
            }
            return new NutritionTotals(totals, itemCount);
        }

        private void markAllMissing() {
            for (Accumulator accumulator : accumulators.values()) {
                accumulator.missingCount++;
            }
        }
    }

    private static final class Accumulator {
        private double knownSum;
        private int knownCount;
        private int missingCount;
    }
}
