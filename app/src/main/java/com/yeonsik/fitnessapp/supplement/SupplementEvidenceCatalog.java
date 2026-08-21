package com.yeonsik.fitnessapp.supplement;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviewed supplement-to-evidence map.
 *
 * <p>The three direct cards are supported by the repository's original-paper verification batch
 * and refreshed against PubMed on 2026-08-19. Other catalog kinds deliberately receive a
 * non-affirmative card until an ingredient/outcome-specific paper set is reviewed.</p>
 */
public final class SupplementEvidenceCatalog {
    public static final String REVIEWED_ON = "2026-08-19";
    private static final String NIH_PERFORMANCE_URL =
            "https://ods.od.nih.gov/factsheets/ExerciseAndAthleticPerformance-HealthProfessional/";
    private static final String IOC_URL = "https://pubmed.ncbi.nlm.nih.gov/29540367/";
    private static final Map<String, SupplementEvidence> EVIDENCE = createEvidence();

    private SupplementEvidenceCatalog() {}

    public static SupplementEvidence forType(String typeCode) {
        SupplementCatalog.require(typeCode);
        SupplementEvidence evidence = EVIDENCE.get(typeCode);
        if (evidence == null) throw new IllegalStateException("영양제 근거 매핑이 누락되었습니다.");
        return evidence;
    }

    public static Map<String, SupplementEvidence> all() {
        return EVIDENCE;
    }

    private static Map<String, SupplementEvidence> createEvidence() {
        Map<String, SupplementEvidence> result = new LinkedHashMap<>();
        result.put("creatine", creatine());
        result.put("caffeine", caffeine());
        result.put("beta_alanine", betaAlanine());

        for (String code : Arrays.asList(
                "l_arginine", "l_citrulline", "eaa", "bcaa", "glutamine", "taurine")) {
            result.put(code, limitedPerformance(code));
        }
        for (SupplementCatalog.Kind kind : SupplementCatalog.KINDS) {
            if (!result.containsKey(kind.code)) result.put(kind.code, contextRequired(kind.code));
        }
        return Collections.unmodifiableMap(result);
    }

    private static SupplementEvidence creatine() {
        return new SupplementEvidence(
                "creatine",
                SupplementEvidence.Status.VERIFIED_DIRECT,
                "직접 수행 근거 확인",
                "반복되는 고강도·간헐적 운동 수행과 저항훈련 적응에서 이점이 보고됩니다. " +
                        "직접 영상 근비대 메타분석의 추가 효과는 매우 작았습니다.",
                "근력·파워·반복 고강도 운동과 저항훈련 맥락에서 해석합니다. " +
                        "지구력 전반이나 개인의 변화량을 보장하는 근거는 아닙니다.",
                "직접 근비대 분석은 10개 연구·44개 결과였고 pooled estimate 0.11, " +
                        "95% CrI -0.02~0.25였습니다. 반응과 체중·수분 변화에는 개인차가 있습니다.",
                "앱은 사용자가 입력한 용량의 적절성, 신장질환·임신·약물 상호작용을 판단하지 않습니다.",
                REVIEWED_ON,
                Arrays.asList(
                        source("08#3", "ISSN creatine position stand",
                                "Kreider et al., JISSN, 2017 · PMID 28615996",
                                "원문 확인", "https://pubmed.ncbi.nlm.nih.gov/28615996/"),
                        source("08#4", "Creatine + resistance training hypertrophy meta-analysis",
                                "Burke et al., Nutrients, 2023 · PMID 37432300",
                                "원문 확인", "https://pubmed.ncbi.nlm.nih.gov/37432300/")
                )
        );
    }

    private static SupplementEvidence caffeine() {
        return new SupplementEvidence(
                "caffeine",
                SupplementEvidence.Status.VERIFIED_DIRECT,
                "직접 수행 근거 확인",
                "여러 운동 과제에서 수행 향상 신호가 있지만 outcome별 확실성과 개인 반응이 다릅니다.",
                "운동 종류, 섭취 시점, 평소 카페인 섭취, 수면과 불안·심박 반응을 함께 봐야 합니다.",
                "엄브렐러 리뷰는 근거 질을 주로 중간으로 평가했고 연구가 젊은 남성에 편중됐습니다. " +
                        "일부 분석은 예측구간에서 효과 방향이 확정되지 않았습니다.",
                "불면, 불안, 두근거림 등의 반응과 다른 카페인 공급원을 합산해야 합니다. " +
                        "앱은 안전한 개인 용량이나 금기를 판정하지 않습니다.",
                REVIEWED_ON,
                Arrays.asList(
                        source("08#5", "Caffeine performance umbrella review",
                                "Grgic et al., BJSM, 2020 · PMID 30926628",
                                "PubMed 초록 교차 확인", "https://pubmed.ncbi.nlm.nih.gov/30926628/"),
                        source("08#6", "ISSN caffeine position stand",
                                "Guest et al., JISSN, 2021 · PMID 33388079",
                                "원문 확인", "https://pubmed.ncbi.nlm.nih.gov/33388079/")
                )
        );
    }

    private static SupplementEvidence betaAlanine() {
        return new SupplementEvidence(
                "beta_alanine",
                SupplementEvidence.Status.VERIFIED_DIRECT,
                "과제 특이적 직접 근거",
                "근육 카르노신 증가와 짧은 고강도 과제의 수행 이점이 보고되지만 모든 운동에 동일하지 않습니다.",
                "특히 약 1~4분 고강도 과제와 지속 섭취 맥락에서 연구 적합성이 높습니다. " +
                        "최대근력이나 장시간 지구력으로 일반화하지 않습니다.",
                "포지션 스탠드는 여러 연구를 통합한 실무 문서이며 개인 효과를 보장하지 않습니다. " +
                        "장기간 안전성과 종목별 효과에는 불확실성이 남습니다.",
                "따끔거림이 보고될 수 있습니다. 앱은 분할 섭취나 개인 용량을 처방하지 않습니다.",
                REVIEWED_ON,
                Collections.singletonList(source("08#7", "ISSN beta-alanine position stand",
                        "Trexler et al., JISSN, 2015 · PMID 26175657",
                        "원문 확인", "https://pubmed.ncbi.nlm.nih.gov/26175657/"))
        );
    }

    private static SupplementEvidence limitedPerformance(String code) {
        SupplementCatalog.Kind kind = SupplementCatalog.require(code);
        return new SupplementEvidence(
                code,
                SupplementEvidence.Status.LIMITED_OR_MIXED,
                "직접 수행 근거 제한·혼재",
                kind.name + "의 운동 수행 효과를 일반 사용자에게 확정할 만큼 일관된 직접 근거를 " +
                        "현재 앱 검증 세트에서 확인하지 못했습니다.",
                "운동 종류, 전체 식사와 단백질 섭취, 대상과 outcome이 일치하는 연구가 필요합니다.",
                "근거 없음과 효과 없음은 같은 뜻이 아닙니다. 현재 단계에서는 효능 점수나 자동 권고를 만들지 않습니다.",
                "제품 조합, 약물, 질환과의 상호작용은 별도 확인이 필요합니다.",
                REVIEWED_ON,
                generalSources()
        );
    }

    private static SupplementEvidence contextRequired(String code) {
        SupplementCatalog.Kind kind = SupplementCatalog.require(code);
        return new SupplementEvidence(
                code,
                SupplementEvidence.Status.CONTEXT_REQUIRED,
                "결핍·건강 목적 확인 필요",
                kind.name + "은 결핍, 식사 상태, 특정 건강 목적에 따라 의미가 달라집니다. " +
                        "복용 기록만으로 필요성이나 운동 효과를 검증할 수 없습니다.",
                "검사 결과, 식사 섭취, 진단·약물, 목표 outcome을 확인한 뒤 근거를 적용해야 합니다.",
                "현재 앱에는 이 종류의 사용자별 결핍·의학적 맥락과 직접 연결된 원문 판정 규칙이 없습니다.",
                "과량 섭취와 약물 상호작용 가능성이 있으므로 앱이 입력 용량을 안전하다고 승인하지 않습니다.",
                REVIEWED_ON,
                generalSources()
        );
    }

    private static List<SupplementEvidence.Source> generalSources() {
        return Arrays.asList(
                source("SUPP-IOC-001", "IOC dietary supplement consensus",
                        "Maughan et al., BJSM, 2018 · PMID 29540367",
                        "원문 확인", IOC_URL),
                source("SUPP-NIH-001", "NIH ODS exercise supplement fact sheet",
                        "NIH Office of Dietary Supplements · 공식 안전·효능 종합",
                        "공식 자료 확인", NIH_PERFORMANCE_URL)
        );
    }

    private static SupplementEvidence.Source source(String id, String title, String citation,
                                                      String status, String url) {
        return new SupplementEvidence.Source(id, title, citation, status, url);
    }
}
