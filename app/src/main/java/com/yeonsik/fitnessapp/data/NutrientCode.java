package com.yeonsik.fitnessapp.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 확장 영양소(미네랄·비타민) 코드 사전.
 *
 * <p>필수값과 1단계 권고값은 typed column으로 저장하고, 여기 정의된 코드들은
 * nutrition_food_nutrients / meal_record_item_nutrients 확장 테이블에 저장한다.
 * 코드 자체가 저장 단위를 확정하므로 같은 코드는 항상 같은 단위로 기록된다.</p>
 */
public final class NutrientCode {
    public static final String GROUP_MINERAL = "mineral";
    public static final String GROUP_VITAMIN = "vitamin";

    public static final String UNIT_MG = "mg";
    public static final String UNIT_UG = "ug";

    public static final String CALCIUM = "calcium";
    public static final String IRON = "iron";
    public static final String MAGNESIUM = "magnesium";
    public static final String POTASSIUM = "potassium";
    public static final String ZINC = "zinc";
    public static final String PHOSPHORUS = "phosphorus";
    public static final String COPPER = "copper";
    public static final String MANGANESE = "manganese";
    public static final String SELENIUM = "selenium";
    public static final String IODINE = "iodine";

    public static final String VITAMIN_A = "vitamin_a";
    public static final String VITAMIN_D = "vitamin_d";
    public static final String VITAMIN_E = "vitamin_e";
    public static final String VITAMIN_K = "vitamin_k";
    public static final String VITAMIN_C = "vitamin_c";
    public static final String VITAMIN_B1 = "vitamin_b1";
    public static final String VITAMIN_B2 = "vitamin_b2";
    public static final String VITAMIN_B3 = "vitamin_b3";
    public static final String VITAMIN_B5 = "vitamin_b5";
    public static final String VITAMIN_B6 = "vitamin_b6";
    public static final String VITAMIN_B7 = "vitamin_b7";
    public static final String VITAMIN_B9 = "vitamin_b9";
    public static final String VITAMIN_B12 = "vitamin_b12";

    private static final Map<String, NutrientCode> REGISTRY = buildRegistry();
    private static final List<NutrientCode> ALL =
            Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));

    public final String code;
    public final String group;
    public final String unit;
    public final String label;

    private NutrientCode(String code, String group, String unit, String label) {
        this.code = code;
        this.group = group;
        this.unit = unit;
        this.label = label;
    }

    /** 사전에 정의된 모든 확장 영양소를 화면 표시 순서대로 돌려준다. */
    public static List<NutrientCode> all() {
        return ALL;
    }

    public static List<NutrientCode> group(String group) {
        List<NutrientCode> matched = new ArrayList<>();
        for (NutrientCode nutrient : ALL) {
            if (nutrient.group.equals(group)) {
                matched.add(nutrient);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    /** 알 수 없는 코드는 null을 돌려준다. 저장 전 검증에 사용한다. */
    public static NutrientCode find(String code) {
        return REGISTRY.get(normalize(code));
    }

    public static boolean isKnown(String code) {
        return REGISTRY.containsKey(normalize(code));
    }

    /** 코드 사전이 정의한 표준 단위. 알 수 없는 코드는 null. */
    public static String unitOf(String code) {
        NutrientCode nutrient = find(code);
        return nutrient == null ? null : nutrient.unit;
    }

    public static String labelOf(String code) {
        NutrientCode nutrient = find(code);
        return nutrient == null ? normalize(code) : nutrient.label;
    }

    /** 화면 표시용 단위 기호. 저장 단위 문자열은 ASCII를 유지한다. */
    public static String displayUnit(String unit) {
        return UNIT_UG.equals(unit) ? "µg" : unit;
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.US);
    }

    private static Map<String, NutrientCode> buildRegistry() {
        Map<String, NutrientCode> registry = new LinkedHashMap<>();
        register(registry, CALCIUM, GROUP_MINERAL, UNIT_MG, "칼슘");
        register(registry, IRON, GROUP_MINERAL, UNIT_MG, "철");
        register(registry, MAGNESIUM, GROUP_MINERAL, UNIT_MG, "마그네슘");
        register(registry, POTASSIUM, GROUP_MINERAL, UNIT_MG, "칼륨");
        register(registry, ZINC, GROUP_MINERAL, UNIT_MG, "아연");
        register(registry, PHOSPHORUS, GROUP_MINERAL, UNIT_MG, "인");
        register(registry, COPPER, GROUP_MINERAL, UNIT_MG, "구리");
        register(registry, MANGANESE, GROUP_MINERAL, UNIT_MG, "망간");
        register(registry, SELENIUM, GROUP_MINERAL, UNIT_UG, "셀레늄");
        register(registry, IODINE, GROUP_MINERAL, UNIT_UG, "요오드");

        register(registry, VITAMIN_A, GROUP_VITAMIN, UNIT_UG, "비타민 A (RAE)");
        register(registry, VITAMIN_D, GROUP_VITAMIN, UNIT_UG, "비타민 D");
        register(registry, VITAMIN_E, GROUP_VITAMIN, UNIT_MG, "비타민 E");
        register(registry, VITAMIN_K, GROUP_VITAMIN, UNIT_UG, "비타민 K");
        register(registry, VITAMIN_C, GROUP_VITAMIN, UNIT_MG, "비타민 C");
        register(registry, VITAMIN_B1, GROUP_VITAMIN, UNIT_MG, "비타민 B1 (티아민)");
        register(registry, VITAMIN_B2, GROUP_VITAMIN, UNIT_MG, "비타민 B2 (리보플라빈)");
        register(registry, VITAMIN_B3, GROUP_VITAMIN, UNIT_MG, "비타민 B3 (나이아신)");
        register(registry, VITAMIN_B5, GROUP_VITAMIN, UNIT_MG, "비타민 B5 (판토텐산)");
        register(registry, VITAMIN_B6, GROUP_VITAMIN, UNIT_MG, "비타민 B6");
        register(registry, VITAMIN_B7, GROUP_VITAMIN, UNIT_UG, "비타민 B7 (비오틴)");
        register(registry, VITAMIN_B9, GROUP_VITAMIN, UNIT_UG, "비타민 B9 (엽산, DFE)");
        register(registry, VITAMIN_B12, GROUP_VITAMIN, UNIT_UG, "비타민 B12");
        return Collections.unmodifiableMap(registry);
    }

    private static void register(
            Map<String, NutrientCode> registry,
            String code,
            String group,
            String unit,
            String label
    ) {
        registry.put(code, new NutrientCode(code, group, unit, label));
    }
}
