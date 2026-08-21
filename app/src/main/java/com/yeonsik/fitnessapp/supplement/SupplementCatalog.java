package com.yeonsik.fitnessapp.supplement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Brand-neutral supplement kinds exposed by the product. */
public final class SupplementCatalog {
    private SupplementCatalog() {}

    public static final List<Kind> KINDS = Collections.unmodifiableList(Arrays.asList(
            new Kind("multivitamin", "종합비타민"),
            new Kind("vitamin_b_complex", "비타민 B군"),
            new Kind("vitamin_c", "비타민 C"),
            new Kind("vitamin_d", "비타민 D"),
            new Kind("vitamin_k", "비타민 K"),
            new Kind("omega_3", "오메가-3"),
            new Kind("magnesium", "마그네슘"),
            new Kind("calcium", "칼슘"),
            new Kind("zinc", "아연"),
            new Kind("iron", "철분"),
            new Kind("selenium", "셀레늄"),
            new Kind("probiotics", "유산균"),
            new Kind("prebiotics", "프리바이오틱스"),
            new Kind("dietary_fiber", "식이섬유"),
            new Kind("creatine", "크레아틴"),
            new Kind("l_arginine", "L-아르기닌"),
            new Kind("l_citrulline", "L-시트룰린"),
            new Kind("beta_alanine", "베타알라닌"),
            new Kind("caffeine", "카페인"),
            new Kind("eaa", "EAA"),
            new Kind("bcaa", "BCAA"),
            new Kind("glutamine", "글루타민"),
            new Kind("taurine", "타우린"),
            new Kind("coenzyme_q10", "코엔자임Q10"),
            new Kind("collagen", "콜라겐"),
            new Kind("msm", "MSM"),
            new Kind("glucosamine", "글루코사민"),
            new Kind("lutein_zeaxanthin", "루테인·지아잔틴"),
            new Kind("milk_thistle", "밀크시슬")
    ));

    public static Kind require(String code) {
        for (Kind kind : KINDS) {
            if (kind.code.equals(code)) return kind;
        }
        throw new IllegalArgumentException("지원하지 않는 영양제 종류입니다.");
    }

    public static final class Kind {
        public final String code;
        public final String name;

        public Kind(String code, String name) {
            this.code = code;
            this.name = name;
        }
    }
}
