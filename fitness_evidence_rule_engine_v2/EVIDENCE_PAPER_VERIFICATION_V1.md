# Original-Paper Evidence Verification v1

> 상태: `in_progress`
> 기준일: 2026-08-14
> 1차 범위: `evidence/01_근비대_근성장.md`의 10개 seed 문헌
> 목적: 문헌 요약을 PubMed·PMC·공식 저널 원문과 대조해 PICO, 표본·기간, 효과 방향·효과크기, 불확실성, 앱 적용 조건을 검증한다.

## 0. 검증 경계

이 문서는 기존 `EVIDENCE_PAPER_APPLICATION_MAP.md`의 설계 요약을 원문 근거로 교정하는 작업 기록이다. `verified_full_text`는 원문 본문 또는 공식 오픈액세스 PDF를 확인한 항목, `verified_abstract`는 PubMed 초록과 서지정보만 확인한 항목이다. 초록만 확인한 항목의 방법·위험편향·하위군·이질성은 완결된 것으로 보지 않는다.

검색·판정 우선순위:

1. PubMed 서지정보와 초록
2. PMC 또는 저널이 제공하는 공식 오픈액세스 전문
3. DOI와 원문 PDF의 일치 여부
4. 원문에서 확인할 수 없는 값은 `unknown`으로 기록

이 배치에서는 블로그·인플루언서 요약·검색 결과의 2차 해설을 근거로 사용하지 않았다. 효과크기는 원문이 제공한 지표를 그대로 기록하며, 서로 다른 SMD·MD·CrI를 하나의 앱 점수로 합치지 않는다.

## 1. 1차 검증 현황

| seed ref | 원문 확인 상태 | 실제 서지정보 | 주요 교정/결론 |
|---|---|---|---|
| `01#1` | `verified_abstract` + DOI/PDF text spot-check | Pelland et al., *Sports Medicine*, 2026, PMID 41343037 | 볼륨은 근비대·근력 모두 증가 방향이나 diminishing returns; 빈도는 근비대에서 일관 효과가 아니고 근력에서는 양의 관계 |
| `01#2` | `verified_full_text` | Schoenfeld et al., IUSCA Position Stand, 2021, DOI 10.47206/ijsc.v1i1.81 | 약 10 sets/muscle/week를 일반적 최소 처방으로 제시하지만 consensus이지 hard clinical threshold가 아님 |
| `01#3` | `verified_full_text` | Grgic et al., *J Sport Health Sci*, 2022, PMID 33497853, PMCID PMC9068575 | 전체 근력·근비대에서 실패 우월성은 작거나 불명확; volume non-equated·trained hypertrophy 하위군은 별도 표시 필요 |
| `01#4` | `verified_abstract` | Carvalho et al., *Appl Physiol Nutr Metab*, 2022, PMID 35015560 | seed의 2021 표기는 online/검색연도 혼동 가능; volume-load matched에서 고부하는 1RM, 근비대는 부하 간 유사 |
| `01#5` | `verified_full_text` | Lopez et al., *Med Sci Sports Exerc*, 2021, PMID 33433148, PMCID PMC8126497 | 28 studies/747 healthy adults; 근비대는 부하 독립적, 근력은 high·moderate가 low보다 우수 |
| `01#6` | `verified_abstract` | Schoenfeld et al., *J Sports Sci*, 2017, PMID 27433992 | 15 studies/34 treatment groups; 추가 1 set당 ES 0.023, 약 0.37% gain 차이로 보고됨 |
| `01#7` | `metadata_corrected` + `verified_abstract` | seed 제목은 2016 논문(PMID 27102172), seed의 주장과 더 직접 맞는 2019 논문은 *How many times per week...*, PMID 30558493 | volume-equated frequency는 유의·의미 있는 근비대 차이 없음 |
| `01#8` | `verified_full_text` | Schoenfeld & Grgic, *SAGE Open Med*, 2020, PMID 32030125, PMCID PMC6977096 | seed의 2021 표기를 2020으로 교정; 하체 full ROM 이점, 상체 근거 제한·상충 |
| `01#9` | `metadata_corrected` + `verified_full_text` | 실제 2017 논문은 *The effects of short versus long inter-set rest intervals...*, PMID 28641044; 2024 Bayesian update는 PMID 39205815 | 짧고 긴 휴식 모두 가능, 긴 휴식의 작은 이점 가능; >90초 추가 이점은 불확실 |
| `01#10` | `verified_full_text` | Burke et al., *Nutrients*, 2023, PMID 37432300, PMCID PMC10180745 | 10 studies/44 outcomes; direct imaging pooled SMD 0.11, 95% CrI −0.02~0.25의 매우 작은 효과 |

## 2. 검증된 evidence cards

### 01#1 — The Resistance Training Dose Response

**원문:** Pelland JC, Remmert JF, Robinson ZP, Hinson SR, Zourdos MC. *The Resistance Training Dose Response: Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and Strength Gains*. Sports Medicine. 2026;56:481–505. [PubMed](https://pubmed.ncbi.nlm.nih.gov/41343037/) · [DOI](https://doi.org/10.1007/s40279-025-02344-w)

- **설계/PICO:** 저항훈련의 주당 set volume·frequency와 근비대·근력 결과의 dose-response를 조사한 다층 meta-regression. 67 studies, 2,058 participants; 평균 연령 25.16±5.22세, 남성 79.1%. intervention 기간과 training status를 모델에서 보정했다.
- **핵심 효과:** fractional set을 사용하는 primary model에서 volume의 marginal slope가 근비대·근력 모두 증가 방향일 posterior probability가 100%였다. 두 결과 모두 diminishing returns가 있었고, strength에서 더 두드러졌다. frequency는 hypertrophy에서 일관된 독립 효과와 양립하지 않았고, strength에서는 증가 방향 posterior probability가 100%였지만 역시 diminishing returns가 있었다.
- **앱 해석:** `weekly_hard_sets_per_muscle`은 근비대의 핵심 추세 입력으로, `exercise_or_lift_frequency`는 최대근력 목표에서 별도 입력으로 둔다. 근육군별 direct set과 indirect/fractional set을 구분하지 않으면 이 논문의 모델과 비교할 수 없다.
- **불확실성/제한:** 평균 표본은 젊고 남성 비중이 높다. 빈도 효과는 volume·training status·실제 per-session volume에 민감하다. 논문은 평균 dose-response이지 개인별 최소·최대 세트 처방이 아니다.
- **rule 영향:** `HYP_VOL_001` 유지. `HYP_FREQ_002`는 “빈도 독립 효과 없음”으로 단정하지 말고 목표별로 분기한다. `STR_SPEC_001`에 리프트별 빈도·고중량 노출을 추가할 근거가 강화된다.

### 01#2 — IUSCA Position Stand

**원문:** Schoenfeld BJ et al. *Resistance Training Recommendations to Maximize Muscle Hypertrophy in an Athletic Population: Position Stand of the IUSCA*. International Journal of Strength and Conditioning. 2021. [공식 오픈액세스 PDF](https://journal.iusca.org/index.php/Journal/article/download/81/140/5323) · [DOI](https://doi.org/10.47206/ijsc.v1i1.81)

- **설계/PICO:** 운동선수의 근비대 최적화를 위해 기존 연구를 통합한 전문가 consensus position stand. RCT 메타분석처럼 하나의 pooled sample·pooled effect size를 산출한 연구가 아니다.
- **실제 권고:** wide loading zones에서 비교 가능한 근비대가 가능하다고 정리한다. 약 10 sets/muscle/week를 일반적 최소 처방으로 제시하되 lower volume에서도 큰 반응을 보이는 개인이 있을 수 있다고 명시한다. 고볼륨은 세션당 약 10 sets/muscle을 넘기지 않도록 빈도로 분산하는 방안을 제시한다. 다관절 운동은 일반적으로 최소 2분, 단관절·일부 머신은 60–90초 휴식을 제시한다.
- **실패 근접도:** 초보자는 failure 근접 없이도 robust gains가 가능하고, 숙련자는 일부 set을 failure까지 수행할 수 있으나 보수적으로 마지막 set·단관절/머신에 제한하는 방안을 제시한다.
- **앱 해석:** `10 sets`는 `hard_gate`가 아닌 설명용 guideline anchor로 저장한다. `training_age_months`, session sets, exercise type, rest seconds를 함께 필요로 한다.
- **불확실성/제한:** consensus 문서이며 athlete population에 초점을 둔다. “10 sets 최소”를 모든 사용자·모든 근육의 생리적 threshold로 승격하지 않는다.
- **rule 영향:** `HYP_VOL_001`에 `guideline_anchor=~10`을 optional metadata로 둘 수 있으나 condition은 보수적으로 유지한다. `STR_REST_003`의 복합운동 휴식 입력을 강화한다.

### 01#3 — Failure versus Non-failure

**원문:** Grgic J et al. *Effects of resistance training performed to repetition failure or non-failure on muscular strength and hypertrophy: A systematic review and meta-analysis*. Journal of Sport and Health Science. 2022;11:202–211. [PubMed](https://pubmed.ncbi.nlm.nih.gov/33497853/) · [PMC full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC9068575/) · [DOI](https://doi.org/10.1016/j.jshs.2021.01.007)

- **설계/PICO:** 건강한 참가자의 randomized resistance-training studies를 대상으로 failure와 non-failure를 비교했다. 최소 6주, strength·hypertrophy 측정, blood-flow restriction·concurrent training은 제외했다.
- **효과:** 전체 strength pooled estimate는 유의한 차이가 없었다(ES −0.09, 95% CI −0.22~0.05). volume을 맞춘 하위군도 차이가 없었다(ES 0.01, 95% CI −0.12~0.15). volume이 맞지 않은 연구에서는 non-failure 쪽 strength가 유리했다. trained 참가자의 hypertrophy 하위군에서는 failure 쪽 작은 효과(ES 0.15, 95% CI 0.03~0.26)가 보고되었으므로 전체 결과와 분리해야 한다.
- **앱 해석:** `failure_sets_ratio > 0.5`는 “실패가 나쁘다”가 아니라 피로·기술·회복을 확인하는 조건부 점검이다. `volume_equated`, `training_status`, `exercise_type`을 evidence match 필드로 저장한다.
- **불확실성/제한:** 연구별 failure 정의, volume 통제, 훈련경력과 기간이 달랐다. 고도로 숙련된 사용자와 고령자 근거가 부족하다.
- **rule 영향:** `HYP_FAIL_003`와 `STR_FAIL_002`의 설명에 “대부분의 적응에서 failure 필수 아님, 단 trained hypertrophy subgroup은 별도 불확실성”을 반영한다.

### 01#4 — Volume-matched loads

**원문:** Carvalho L et al. *Muscle hypertrophy and strength gains after resistance training with different volume-matched loads: a systematic review and meta-analysis*. Applied Physiology, Nutrition, and Metabolism. 2022;47:357–368. [PubMed](https://pubmed.ncbi.nlm.nih.gov/35015560/) · [DOI](https://doi.org/10.1139/apnm-2021-0515)

- **설계/PICO:** volume load(`sets × repetitions × weight`)를 맞춘 저항훈련에서 very-low(<30% 1RM 또는 >35RM), low(30–59%), moderate(60–79%), high(≥80%) load를 비교했다.
- **효과:** hypertrophy pooled analysis는 load 간 차이가 없었다. 1RM strength는 high load가 low·moderate보다, moderate가 low보다 유리했다. 즉 volume load가 같아도 1RM 결과는 훈련 부하 특이성을 가진다.
- **앱 해석:** `goal=hypertrophy`에서는 특정 rep range를 hard gate로 만들지 않고 load·effort·volume을 함께 본다. `goal=max_strength`에서는 `load_pct_1rm`와 실제 해당 리프트의 high-load exposure를 직접 추적한다.
- **불확실성/제한:** 초보·숙련·운동 종류·failure 수행 조건이 결과의 적용성을 바꾼다. 초록에서 세부 하위군과 위험편향을 완전히 확인하지 않았으므로 이 카드는 `verified_abstract`다.
- **rule 영향:** `HYP_LOAD_004` 유지. `STR_SPEC_001`의 high-load specificity 근거를 강화한다.

### 01#5 — Resistance Training Load Effects

**원문:** Lopez P et al. *Resistance Training Load Effects on Muscle Hypertrophy and Strength Gain: Systematic Review and Network Meta-analysis*. Medicine & Science in Sports & Exercise. 2021;53:1206–1216. [PubMed](https://pubmed.ncbi.nlm.nih.gov/33433148/) · [PMC full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC8126497/) · [DOI](https://doi.org/10.1249/MSS.0000000000002585)

- **설계/PICO:** 건강한 성인이 volitional failure까지 수행한 low(>15RM), moderate(9–15RM), high(≤8RM) load를 비교한 network meta-analysis. 28 studies, 747 participants.
- **효과:** hypertrophy는 전체 및 high-quality subgroup에서 load 간 차이가 없었다. strength는 high-load와 moderate-load가 low-load보다 유리했다(high vs low SMD 약 0.60–0.63, moderate vs low 약 0.34–0.35). high vs moderate는 유의하지 않은 우세 경향이었다.
- **앱 해석:** “근비대에는 넓은 부하 허용, 최대근력에는 고부하 특이성”을 별도 outcome rule로 유지한다. `performed_to_failure`가 연구 조건이므로 실패 여부가 입력에 없으면 evidence match를 낮춘다.
- **불확실성/제한:** healthy adults, relatively short interventions, volitional failure라는 조건이 있다. 스포츠 선수·고령·통증 사용자에게 직접 확장하지 않는다. 2022 corrigendum이 있으므로 DOI/erratum도 source metadata에 연결한다.
- **rule 영향:** `HYP_LOAD_004`, `STR_SPEC_001` 유지. `population_fit`과 `set_endpoint` 필드를 필수화한다.

### 01#6 — Weekly resistance-training volume

**원문:** Schoenfeld BJ, Ogborn D, Krieger JW. *Dose-response relationship between weekly resistance training volume and increases in muscle mass: A systematic review and meta-analysis*. Journal of Sports Sciences. 2017;35:1073–1082. [PubMed](https://pubmed.ncbi.nlm.nih.gov/27433992/) · [DOI](https://doi.org/10.1080/02640414.2016.1210197)

- **설계/PICO:** 주당 resistance-training set volume과 muscle-size 변화의 dose-response를 조사한 meta-regression. 15 studies에서 34 treatment groups를 분석했다.
- **효과:** weekly sets를 연속변수로 볼 때 volume 효과는 유의했다(P=0.002). 추가 1 set은 ES 0.023 증가와 약 0.37% gain 차이에 대응했다. higher-vs-lower volume 비교의 ES 차이는 0.241, percentage gain 차이는 3.9%였다. `<5`, `5–9`, `10+`의 세 구간 분석은 trend였지만 유의하지 않았다(P=0.074).
- **앱 해석:** `weekly_hard_sets_per_muscle`을 기록하되 `<5`를 “부족 확정”으로 말하지 않는다. volume 증가 후보는 recovery·performance trend와 함께 발화한다.
- **불확실성/제한:** 평균 dose-response이며 개인의 회복능력·훈련경력·운동별 indirect set 계산이 다르다. 효과크기 증가량을 개인의 근육 증가율로 직접 표시하지 않는다.
- **rule 영향:** `HYP_VOL_001`의 현재 보수적 문장을 유지하고, `condition`에 hard threshold가 아닌 `volume_band`와 confidence를 추가하는 후속 작업이 필요하다.

### 01#7 — Frequency metadata correction

**seed가 가리키는 2016 원문:** Schoenfeld BJ et al. *Effects of Resistance Training Frequency on Measures of Muscle Hypertrophy: A Systematic Review and Meta-Analysis*. Sports Medicine. 2016;46:1689–1697. [PubMed](https://pubmed.ncbi.nlm.nih.gov/27102172/). 이 논문은 10 studies를 포함했고, 1–3 days/week 비교에서 higher frequency effect size가 컸으며, volume-equated frequency 비교는 표본 부족으로 신뢰할 추정치를 계산하지 못했다.

**seed 주장과 직접 맞는 2019 원문:** Schoenfeld BJ et al. *How many times per week should a muscle be trained to maximize muscle hypertrophy? A systematic review and meta-analysis of studies examining the effects of resistance training frequency*. Journal of Sports Sciences. 2019;37:1286–1295. [PubMed](https://pubmed.ncbi.nlm.nih.gov/30558493/) · [DOI](https://doi.org/10.1080/02640414.2018.1555906)

- **설계/PICO:** weekly frequency를 비교한 25 studies. volume-equated 조건과 non-volume-equated 조건을 구분했다.
- **효과:** volume-equated 조건에서는 higher vs lower frequency의 유의·의미 있는 hypertrophy 차이가 없었다. non-volume-equated 연구에서는 higher frequency가 유리했지만 1 vs 3+ days/week의 차이는 modest했다.
- **앱 해석:** `weekly_frequency`는 volume distribution·session quality·recovery를 설명하는 변수다. 근비대에 빈도 자체의 보너스를 주지 않는다. 단, 이 결론은 2019 논문을 별도 evidence ID로 등록할 때만 정확하다.
- **불확실성/제한:** 2016 논문과 2019 논문은 제목·연도·포함연구가 다르다. 기존 `01#7`을 그대로 유지하면 source fingerprint가 잘못된다.
- **rule 영향:** 기존 `01#7`을 `01#7a`(2016)와 `01#7b`(2019)로 분리하거나 `01#7`을 2019 논문으로 교체한다. `HYP_FREQ_002`는 2019 원문을 주 근거로 삼는다.

### 01#8 — Range of motion

**원문:** Schoenfeld BJ, Grgic J. *Effects of range of motion on muscle development during resistance training interventions: A systematic review*. SAGE Open Medicine. 2020;8. [PubMed](https://pubmed.ncbi.nlm.nih.gov/32030125/) · [PMC full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC6977096/) · [DOI](https://doi.org/10.1177/2050312120901559)

- **설계/PICO:** dynamic longitudinal RT에서 full vs partial ROM을 비교한 systematic review. 6 studies, 총 135명(남성 127, 여성 8), 연구의 최소 기간은 6주 이상이었다.
- **효과:** 하체 4개 연구 중 다수가 full ROM을 선호했고 lower-body hypertrophy 이점이 관찰됐다. 상체 2개 연구는 제한적이고 결과가 상충했다. 몸통 연구는 없었다.
- **앱 해석:** `rom_quality`를 “깊을수록 무조건 좋음”으로 계산하지 않는다. body region·exercise·pain·측정 방식이 일치할 때만 참고 evidence를 발화한다.
- **불확실성/제한:** 연구 수가 적고 상체·몸통의 외삽이 어렵다. 영상·센서 없이 ROM을 정밀하게 산출하지 않는다.
- **rule 영향:** 기존 문장의 연도를 2020으로 교정한다. ROM은 현재 24개 seed rule에는 직접 연결되지 않았으므로 신규 rule로 승격하지 않고 optional feature로 둔다.

### 01#9 — Inter-set rest metadata correction and update

**seed가 가리키는 실제 2017 원문:** Grgic J et al. *The effects of short versus long inter-set rest intervals in resistance training on measures of muscle hypertrophy: A systematic review*. European Journal of Sport Science. 2017;17:983–993. [PubMed](https://pubmed.ncbi.nlm.nih.gov/28641044/) · [DOI](https://doi.org/10.1080/17461391.2017.1340524)

- **설계/PICO:** short `≤60 s` vs long `>60 s` inter-set rest, 최소 4주·주 2회 이상·건강 성인·근육량 측정. 6 studies가 기준을 충족했다.
- **효과:** short와 long 모두 hypertrophy에 사용할 수 있다. 숙련자에서 long rest가 유리할 가능성이 있었지만 연구 수와 직접 측정의 부족으로 강한 결론은 불가했다.
- **최신 update:** Singer et al. 2024 Bayesian meta-analysis는 9 studies, 19 measurements를 포함했고 `>60 s`가 `≤60 s`보다 작은 이점 방향을 보였지만 이질성이 컸다. `>90 s` 사이에서는 추가적인 appreciable hypertrophy 차이를 찾지 못했다. [PMC 2024 update](https://pmc.ncbi.nlm.nih.gov/articles/PMC11349676/) · [PubMed](https://pubmed.ncbi.nlm.nih.gov/39205815/)
- **앱 해석:** `rest_sec` 자체보다 `rep_dropoff`, `volume_load_maintained`, exercise type을 함께 본다. 복합운동에서 짧은 휴식으로 후속 수행이 크게 떨어지면 휴식 증가 후보를 낸다.
- **불확실성/제한:** 휴식시간의 효과는 exercise selection·effort·training status에 의존한다. `120 s`나 `180 s`를 모든 사용자에게 hard threshold로 만들지 않는다.
- **rule 영향:** 기존 `STR_REST_003`의 evidence ref와 제목을 실제 2017 source로 교정한다. 2024 update는 `01#9b`로 추가하고 moderate confidence를 유지한다.

### 01#10 — Creatine and regional hypertrophy

**원문:** Burke R et al. *The Effects of Creatine Supplementation Combined with Resistance Training on Regional Measures of Muscle Hypertrophy: A Systematic Review with Meta-Analysis*. Nutrients. 2023;15:2116. [PubMed](https://pubmed.ncbi.nlm.nih.gov/37432300/) · [PMC full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC10180745/) · [DOI](https://doi.org/10.3390/nu15092116)

- **설계/PICO:** 건강 성인에서 최소 6주간 creatine+RT와 RT+placebo를 비교하고 MRI·CT·ultrasound 같은 direct regional imaging으로 hypertrophy를 측정한 RCT를 포함했다. 10 studies, 44 outcomes; 기간 6–52주, 젊은 성인과 older adults, trained·untrained가 섞였다.
- **효과:** 전체 pooled standardized outcome은 0.11, 95% CrI −0.02~0.25로 매우 작은 creatine 우세 효과였다. upper/lower body muscle thickness의 absolute differences는 대략 0.10–0.16 cm 범위였고, 연구 간 heterogeneity가 있었다. young vs older benefit 차이는 0.17, 95% CrI −0.09~0.45로 불확실했다.
- **앱 해석:** 크레아틴은 훈련·에너지·단백질 이후의 작은 보조 변수다. DXA lean mass만으로 근비대 효과를 판정하지 말고 수분 변화 가능성과 direct imaging 여부를 evidence metadata에 남긴다.
- **불확실성/제한:** pooled CrI가 0을 포함하고 실용적 개인 효과는 작을 수 있다. 보충제 안전·금기·약물 상호작용은 이 논문으로 자동 판정하지 않는다.
- **rule 영향:** `ANA_CRE_001`과 `HYP_LOAD_004`의 보조 근거로 연결하되, 크레아틴을 근비대의 주 원인이나 보장된 효과로 표현하지 않는다.

## 3. 이번 배치에서 확인된 원문-요약 불일치

| 항목 | 기존 seed | 원문 확인 결과 | 조치 |
|---|---|---|---|
| `01#4` | 2021 | PubMed 서지정보는 2022; DOI는 `apnm-2021-0515` | 검증본은 2022로 기록 |
| `01#5` | 2023 | PubMed/PMC 서지정보는 2021 | 검증본은 2021로 기록 |
| `01#7` | 2019 제목이 2016 논문 제목과 일치 | 2016 논문과 2019 volume-equated 논문이 혼합됨 | source를 분리하고 2019 PMID 30558493을 주 근거로 사용 |
| `01#8` | 2021 | PubMed는 2020 | 검증본은 2020으로 기록 |
| `01#9` | `Effects of Inter-Set Rest Interval Duration...` | 실제 2017 제목은 `The effects of short versus long inter-set rest intervals...`; 2024 update는 별도 논문 | source fingerprint와 rule ref 교정 |

## 4. 앱 모델에 반영할 verified fields

이번 배치 후 최소 evidence schema에 다음 필드를 추가해야 한다.

```json
{
  "evidence_id": "01#10",
  "source_fingerprint": {
    "title": "The Effects of Creatine Supplementation Combined with Resistance Training on Regional Measures of Muscle Hypertrophy: A Systematic Review with Meta-Analysis",
    "year": 2023,
    "pmid": "37432300",
    "pmcid": "PMC10180745",
    "doi": "10.3390/nu15092116"
  },
  "verification_status": "verified_full_text",
  "study_design": "systematic_review_meta_analysis",
  "population": {
    "healthy_adults": true,
    "training_status": ["trained", "untrained"],
    "age_range": "young_and_older_adults"
  },
  "intervention": "creatine_plus_resistance_training",
  "comparator": "resistance_training_plus_placebo",
  "outcomes": ["regional_muscle_hypertrophy"],
  "effect": {
    "metric": "standardized_mean_difference",
    "estimate": 0.11,
    "interval": [-0.02, 0.25],
    "interval_type": "95_percent_credible_interval",
    "direction": "small_favoring_creatine",
    "practical_significance": "small"
  },
  "app_translation": {
    "required_inputs": ["creatine_use", "resistance_training", "direct_or_proxy_body_composition_measure"],
    "hard_gate": false,
    "medical_rule": false
  },
  "limitations": [
    "small pooled effect",
    "credible interval includes zero",
    "water-related lean-mass measurement concern",
    "heterogeneity across studies"
  ]
}
```

`effect_size`, `interval`, `interval_type`, `sample_size`, `duration_range`, `risk_of_bias`, `certainty`, `verification_status`, `pmid`, `pmcid`, `doi`, `source_fingerprint`는 이후 `rules.jsonl`의 `evidence_refs`만으로는 보존되지 않는 원문 근거이므로 별도 evidence catalog에 저장해야 한다.

## 5. 검증 배치 상태

1. 01번과 02–09번 seed 문헌을 각 10편씩 순차 확인했다. 따라서 현재 배치는 90개 seed 항목을 대상으로 한다.
2. 02–09번은 공식 PubMed 초록, 공개 전문, 출판사 원문을 우선 사용해 source fingerprint·설계·주요 결과·제한을 기록했다. 중복 논문은 원문 하나를 여러 도메인의 outcome으로 연결했다.
3. `07#10`은 제목 검색만으로 정확한 seed fingerprint를 확정하지 못해 `unresolved_seed_metadata`로 남겼다. 정확한 PMID/DOI를 확인하기 전에는 rule evidence로 사용하지 않는다.
4. `rules.jsonl`의 `evidence_refs` 교정과 `effect_size`의 rule weight 연결은 별도 작업이다. 이 문서는 근거 검증 결과를 고정하지만, 아직 규칙 로직이나 내부 weight를 변경하지 않는다.

## 6. 기존 설계 문서와의 관계

- 설계·앱 모델: `EVIDENCE_PAPER_APPLICATION_MAP.md`
- 사람이 검토한 seed 문헌 요약: `evidence/01_근비대_근성장.md`
- 기계 판독 seed rule: `rules.jsonl`
- 이 파일: 원문 확인 상태, 서지 교정, 효과크기·제한의 검증 기록

## 7. 02 근력·최대근력 — 원문 근거 검증 배치

검증 경계: `2026-08-14`, 02번 seed 문헌 10편. 공식 PubMed 초록과 공개 전문을 우선 사용했다. `verified_abstract`는 연구 설계·주요 결과·결론까지는 확인했지만 본문 전체의 세부 표·부록·위험도 평가까지 확인한 상태가 아니다. 01번과 중복되는 논문은 같은 원문을 다시 읽고 근력 결과만 분리해 기록했다.

### 02#1. Pelland et al. (2026) — 주당 볼륨과 빈도

- **원문**: [PubMed PMID 41343037](https://pubmed.ncbi.nlm.nih.gov/41343037/), DOI `10.1007/s40279-025-02344-w`.
- **상태**: `cross_verified_full_text` — 01#1과 같은 systematic review/meta-analysis를 근력 관점에서 재확인.
- **PICO/결과**: 저항운동의 주당 세트 수와 근육군별 빈도를 성인 근력·근비대 결과와 비교했다. 67개 연구, 2,058명, 평균 연령 25.16세, 남성 79.1%였다. 근력은 주당 볼륨 증가에 따라 대체로 증가하지만 증가폭이 둔화되는 패턴이었고, 빈도는 볼륨을 맞추면 독립 효과가 작거나 불명확했다.
- **앱 번역**: `weekly_hard_sets`, `sessions_per_muscle`, `strength_outcome`을 분리 저장한다. 빈도만으로 근력 향상을 예측하지 말고, 빈도 증가는 주당 볼륨을 분산하는 입력으로 취급한다.
- **제한**: 집단·운동·측정법의 이질성이 크며, 평균 효과를 개인의 1RM 변화로 직접 환산하면 안 된다.

### 02#2. Lopez et al. (2021) — 부하와 근력

- **원문**: [PubMed PMID 33433148](https://pubmed.ncbi.nlm.nih.gov/33433148/), [공개 전문 PMC8126497](https://pmc.ncbi.nlm.nih.gov/articles/PMC8126497/), DOI `10.1249/MSS.0000000000002585`.
- **상태**: `cross_verified_full_text` — 01#5와 같은 systematic review/meta-analysis.
- **PICO/결과**: 건강한 성인의 저항운동에서 부하가 근비대·근력에 미치는 영향을 비교한 28개 연구, 747명. 근비대는 대체로 부하와 무관했고, 근력은 고·중부하가 저부하보다 유리했다. 고부하 대 저부하 근력 차이는 대략 SMD `0.60–0.63`, 중부하 대 저부하는 `0.34–0.35` 범위였다.
- **앱 번역**: 목표가 `strength`이면 `load_intensity`와 `specific_lift_practice`를 기록하고, 목표가 `hypertrophy`이면 특정 고부하만 강제하지 않는다. 저부하 세트는 실패 근접도와 피로를 함께 표시한다.
- **제한**: 연구별 운동 선택·반복 범위·훈련 경험이 달라 개인 처방의 임계값으로 사용할 수 없다.

### 02#3. Grgic et al. (2022) — 반복 실패와 근력

- **원문**: [PubMed PMID 33497853](https://pubmed.ncbi.nlm.nih.gov/33497853/), [공개 전문 PMC9068575](https://pmc.ncbi.nlm.nih.gov/articles/PMC9068575/), DOI `10.1016/j.jshs.2021.01.007`.
- **상태**: `cross_verified_full_text` — 01#3과 같은 systematic review/meta-analysis.
- **PICO/결과**: 실패까지 수행하는 훈련과 비실패 훈련을 비교했다. 전체 근력 효과는 유의한 차이가 없었고 ES `−0.09`, 95% CI `−0.22 to 0.05`; 볼륨을 맞춘 분석도 ES `0.01`, 95% CI `−0.12 to 0.15`였다. 훈련 경험자 근비대 하위군에서는 실패 쪽 ES `0.15`, 95% CI `0.03 to 0.26`이었지만 근력 규칙으로 일반화하지 않는다.
- **앱 번역**: `rir`, `failure_flag`, `set_quality`, `joint_discomfort`를 저장하고, 실패를 근력 향상의 필수 게이트로 만들지 않는다. 고피로 세트가 연속되면 다음 세트의 부하·볼륨 조정 후보로만 제시한다.
- **제한**: 실패 정의·운동 종류·볼륨 통제가 연구마다 달라 RIR 0의 보편적 우월성을 입증하지 않는다.

### 02#4. Grgic et al. (2018) — 근력 향상을 위한 빈도

- **원문**: [PubMed PMID 29470825](https://pubmed.ncbi.nlm.nih.gov/29470825/), DOI `10.1007/s40279-018-0872-x`.
- **상태**: `verified_abstract`.
- **PICO/결과**: 주당 근력훈련 빈도를 비교한 22개 연구. 비등량 분석에서는 주당 빈도가 높을수록 관찰된 근력 효과가 증가했고 ES가 주 1·2·3·4회 이상에서 각각 `0.74`, `0.82`, `0.93`, `1.08`이었다. 그러나 볼륨 등량 하위분석에서는 빈도 효과가 유의하지 않았다(`P=.421`). 상체·젊은 참가자·여성 하위군에서 신호가 있었지만, 대부분 비훈련자였고 하체·남성·중년/고령 하위군은 일관되지 않았다.
- **앱 번역**: 빈도 카드는 `volume_equated=false/true`를 명시한다. “더 자주 = 더 강해짐”을 직접 주장하지 않고, 회복 가능한 범위에서 총 볼륨을 나누는 선택지로 설명한다.
- **제한**: 공식 초록까지 확인했으며, 하위군 표와 연구별 위험도는 전문 재확인이 남아 있다. 현재는 rule weight 산정에 사용하지 않는다.

### 02#5. Schoenfeld et al. (2016/2019) — 빈도와 근력의 서지 혼입

- **원문 확인**: seed의 제목은 [PMID 27102172](https://pubmed.ncbi.nlm.nih.gov/27102172/)인 2016년 근비대 논문과 일치한다. seed가 주장하는 “빈도와 근력”을 직접 지지하는 별도 연구는 2019년 [PubMed PMID 30558493](https://pubmed.ncbi.nlm.nih.gov/30558493/), DOI `10.1080/02640414.2018.1555906`이다.
- **상태**: `metadata_corrected`, `verified_abstract`.
- **결과**: 2016 논문은 근비대 중심이며, 2019 논문은 25개 연구를 검토해 볼륨을 맞추면 빈도의 독립적 근비대 차이가 유의하지 않거나 작다고 결론냈다. 따라서 02#5를 근력 rule의 직접 효과크기로 사용하지 않는다.
- **앱 번역**: evidence record를 `02#5a`(2016 hypertrophy)와 `02#5b`(2019 frequency review)로 분리한다. seed 한 건에 서로 다른 PMID·연도·결과를 덮어쓰지 않는다.
- **제한**: 원문 식별 오류가 있었으므로 기존 `evidence_refs`의 fingerprint 교정이 필요하다.

### 02#6. Carvalho et al. (2022) — 부하와 근력·근비대

- **원문**: [PubMed PMID 35015560](https://pubmed.ncbi.nlm.nih.gov/35015560/), DOI `10.1139/apnm-2021-0515`.
- **상태**: `verified_abstract`; seed의 2021 표기는 온라인/서지 연도 혼입으로 2022 출판 기록과 교정.
- **PICO/결과**: 볼륨-부하를 맞춘 저항운동 비교에서 근비대는 부하군 간 뚜렷한 차이가 없었고, 1RM 근력은 높은 부하가 유리했다. 즉 근육 크기와 특정 고중량 리프트의 근력 적응을 같은 outcome으로 취급하면 안 된다.
- **앱 번역**: `outcome_type=hypertrophy|1rm_strength`를 분리하고, 목표가 1RM일 때만 부하 특이성을 반영한다. 체성분 프록시만으로 근력 향상을 판정하지 않는다.
- **제한**: 초록 확인 경계이며, 운동별 세부·훈련경험별 차이는 전문 확인 전 보류한다.

### 02#7. Greig et al. (2020) — autoregulation

- **원문**: [PubMed PMID 32813181](https://pubmed.ncbi.nlm.nih.gov/32813181/), [공개 전문 PMC7575491](https://pmc.ncbi.nlm.nih.gov/articles/PMC7575491/), DOI `10.1007/s40279-020-01330-8`.
- **상태**: `verified_full_text`.
- **성격/결과**: 단일 개입의 pooled effect를 제시하는 메타분석이 아니라, autoregulation의 정의·측정·실행 불일치를 정리한 review다. 측정 가능한 수행도·인지된 능력·피로를 이용해 개인화하는 개념을 설명하며 RPE/RIR, bar velocity 등 입력의 사용 맥락을 제안한다.
- **앱 번역**: `readiness_score`, `rpe`, `rir`, `velocity_if_available`, `planned_load`, `actual_load`, `adjustment_reason`를 이벤트로 남긴다. “autoregulation 사용 = 효과 증가”라는 주장은 생성하지 않는다.
- **제한**: 건강·재활·선수 집단과 측정 도구가 섞여 있고, 앱 알고리즘의 효과크기를 제공하지 않는다. rule evidence가 아니라 입력 모델·설계 근거로 분류한다.

### 02#8. Weakley et al. (2021) — velocity-based training

- **원문**: [University of Miami 공식 서지/초록](https://scholarship.miami.edu/esploro/outputs/journalArticle/Velocity-Based-Training-From-Theory-to-Application/991031785535902976), DOI `10.1519/SSC.0000000000000560`.
- **상태**: `metadata_corrected`, `verified_abstract`; seed의 2017 표기는 실제 2021 논문과 불일치.
- **성격/결과**: 이론에서 적용까지를 다루는 review이며 pooled effect를 보고하지 않는다. 객관적 피드백, load–velocity profile, velocity-loss threshold, 프로그램 적용을 다룬다.
- **앱 번역**: 장비가 있을 때만 `mean_velocity`, `velocity_loss_percent`, `load_velocity_profile`을 선택 입력으로 저장한다. 센서가 없으면 RPE/RIR 기반으로 동작하며 velocity를 추정하지 않는다.
- **제한**: 센서 정확도·운동별 속도 기준·사용자 숙련도에 의존한다. velocity threshold를 보편적 컷오프로 하드코딩하지 않는다.

### 02#9. Grgic et al. (2018) — 세트 간 휴식과 근력

- **원문 교정**: seed의 “short versus long rest and hypertrophy”와 달리 02#9의 근력 근거는 [PubMed PMID 28933024](https://pubmed.ncbi.nlm.nih.gov/28933024/), DOI `10.1007/s40279-017-0788-x`, *Effects of Rest Interval Duration in Resistance Training on Measures of Muscular Strength*이다. 근비대 휴식 논문은 [PMID 28641044](https://pubmed.ncbi.nlm.nih.gov/28641044/)로 별도 관리한다.
- **상태**: `metadata_corrected`, `verified_abstract`.
- **PICO/결과**: 23개 연구, 491명(남성 413, 여성 78)을 검토했다. 60초 미만에서도 근력 증가는 가능하지만, 훈련자에서 최대화를 위해 2분 초과가 필요한 경향이었고, 비훈련자에서는 60–120초가 충분할 수 있었다.
- **앱 번역**: 운동 유형·훈련 수준·목표에 따라 `rest_seconds`를 추천하되, “짧은 휴식은 무효”로 차단하지 않는다. 다음 세트의 반복 달성·RPE·수행 저하를 함께 기록한다.
- **제한**: 연구 질은 양호~중간 수준이며 운동·세트 수·부하가 이질적이다. 개인의 회복과 장비 점유 시간도 별도 고려가 필요하다.

### 02#10. ACSM Progression Models — 2009 공식 position stand

- **원문**: [PubMed PMID 19204579](https://pubmed.ncbi.nlm.nih.gov/19204579/), DOI `10.1249/MSS.0b013e3181915670`. 동일 제목의 2002판도 [PMID 11828249](https://pubmed.ncbi.nlm.nih.gov/11828249/)로 존재하므로 seed의 연도·PMID를 분리한다.
- **상태**: `metadata_corrected`, `verified_abstract`.
- **권고 요지**: 초·중급과 고급자의 반복범위·빈도·휴식·부하 진행을 구분한다. 초보 근력은 대략 8–12RM, 고급 근력은 1–12RM을 주기화하되 1–6RM 비중과 긴 휴식을 활용하고, 근비대는 6–12RM 및 대략 1–2분 휴식을 강조한다. 초·중급 빈도는 주 2–3회, 고급자는 주 4–5회가 제시된다. 목표 반복을 1–2회 초과하면 2–10% 부하를 올리는 progression 규칙도 포함된다.
- **앱 번역**: 이를 절대 처방이 아니라 `training_level`, `goal`, `rep_range`, `rest_range`, `progression_trigger`의 초기 템플릿으로 저장하고, 최신 systematic review와 사용자 로그가 우선하도록 한다.
- **제한**: 오래된 position stand이며 모든 현재 장비·집단·운동에 대한 개인별 효과크기는 제공하지 않는다. 현재 rule weight의 단독 근거로 쓰지 않는다.

### 02번 서지·적용 판정

| seed | 판정 | 필요한 데이터 조치 |
|---|---|---|
| 02#1 | 01#1과 중복, 근력 결과로 재분류 | 동일 source fingerprint에 domain만 추가 |
| 02#2 | 01#5와 중복, 근력 결과로 재분류 | `load`와 `outcome_type` 분리 |
| 02#3 | 01#3과 중복, 근력 결과로 재분류 | failure를 hard gate로 쓰지 않음 |
| 02#4 | 공식 초록 확인 | 볼륨 등량 여부를 필수 필드화 |
| 02#5 | 2016/2019 논문 혼입 | `02#5a`, `02#5b`로 분리 |
| 02#6 | 출판연도 2022로 교정 | seed year 교체, DOI는 유지 |
| 02#7 | 효과 연구가 아닌 review | rule weight가 아닌 input-model 근거 |
| 02#8 | 실제 논문은 2021 | velocity를 선택 입력으로 처리 |
| 02#9 | seed title이 근비대 휴식 논문과 혼입 | 근력 PMID 28933024, 근비대 PMID 28641044 분리 |
| 02#10 | 2002/2009 동일 제목 판본 존재 | PMID와 판본 연도 필수 저장 |

02번의 결론은 “더 높은 빈도·실패·속도 피드백·짧은 휴식이 항상 더 좋은 근력을 만든다”가 아니다. 앱에서는 목표 outcome, 총 볼륨, 훈련 수준, 세트 간 회복, 실제 수행 로그를 분리해야 하며, 현재 확인된 문헌만으로 자동 처방의 효과크기를 확정하지 않는다.

## 8. 03 체지방감소·다이어트 — 원문 근거 검증 배치

### 03#1. Binmahfoz et al. (2025) — 감량 중 저항운동

- **원문**: [PubMed PMID 40909191](https://pubmed.ncbi.nlm.nih.gov/40909191/), [공개 전문 PMC12406911](https://pmc.ncbi.nlm.nih.gov/articles/PMC12406911/), DOI `10.1136/bmjsem-2024-002363`.
- **상태**: `verified_full_text`.
- **PICO/결과**: BMI 25 이상 성인 18–65세의 식이 감량에서 저항운동 추가와 식이 단독을 비교한 25개 RCT. 체중은 유의한 차이가 없었지만(MD `−0.32 kg`, `p=.35`), 제지방 보존은 SMD `0.40`(`p=.0003`, 중간 확실성), 지방량 감소는 SMD `−0.36`(`p<.00001`, 높은 확실성), 근력은 SMD `2.36`(`p<.00001`, 낮은 확실성)였다.
- **앱 번역**: 감량 성공을 `body_mass` 하나로 판정하지 않고 `fat_mass`, `fat_free_mass`, `waist`, `strength`를 별도 outcome으로 계산한다. 체중 정체를 즉시 실패로 표시하지 않는다.
- **제한**: 근력 확실성이 낮고, 대상자는 과체중·비만 성인에 한정된다.

### 03#2. Xie et al. (2025) — 칼로리 제한 중 운동 양식

- **원문**: [PubMed PMID 40510496](https://pubmed.ncbi.nlm.nih.gov/40510496/), [공개 전문 PMC12158682](https://pmc.ncbi.nlm.nih.gov/articles/PMC12158682/), DOI `10.3389/fnut.2025.1579024`.
- **상태**: `verified_full_text`.
- **PICO/결과**: 건강한 집단의 칼로리 제한+운동 RCT 62개, 4,429명. 운동 강도·양식에 따라 체중·지방량·체지방률 순위가 달랐고, 제지방 보존에서는 중강도 혼합/저·중강도 저항운동의 추정치가 비교적 유리했다. 제지방 보존 추정치는 저항운동군에서 대조군과 신뢰구간이 겹쳤다.
- **앱 번역**: `energy_deficit`, `exercise_modality`, `intensity`, `fat_mass`, `fat_free_mass`를 함께 저장한다. 네트워크 순위만으로 사용자에게 운동 우열을 단정하지 않는다.
- **제한**: 간접 비교의 불확실성과 운동 강도 분류의 이질성이 크다.

### 03#3. Lopez et al. (2022) — 과체중·비만군 저항운동

- **원문**: [PubMed PMID 35191588](https://pubmed.ncbi.nlm.nih.gov/35191588/), [공개 전문 PMC9285060](https://pmc.ncbi.nlm.nih.gov/articles/PMC9285060/), DOI `10.1111/obr.13428`.
- **상태**: `verified_full_text`.
- **PICO/결과**: 114개 RCT, 4,184명. 저항운동+칼로리 제한은 무운동과 비교해 체지방률 약 `−3.8%`, 지방량 `−5.3 kg`의 효과를 보였고, 저항운동 단독은 제지방 `+0.8 kg`을 보였다. 저항운동+칼로리 제한에서는 제지방이 약 `−0.3 kg`으로 유지에 가까웠다.
- **앱 번역**: 체중 감량 모드에서 저항운동을 근육 보존 전략으로 표시하되, 지방 감소와 제지방 증가를 별도 상태로 판정한다.
- **제한**: 연구의 연령·비만도·식이·운동 프로토콜이 다양하다. 평균값을 개인의 예상 kg 변화로 직접 약속하지 않는다.

### 03#4. Murphy & Koehler (2022) — 에너지 적자와 저항훈련 적응

- **원문**: [PubMed PMID 34623696](https://pubmed.ncbi.nlm.nih.gov/34623696/), DOI `10.1111/sms.14075`.
- **상태**: `verified_abstract`.
- **PICO/결과**: 3주 이상 에너지 적자에서 저항훈련을 수행한 RCT를 검토했다. 병렬 대조 분석에서 제지방 증가는 적자군이 낮았고 ES `−0.57`, `p=.02`; 근력 차이는 유의하지 않았다(ES `−0.31`, `p=.28`). 메타회귀는 약 `500 kcal/day` 적자가 제지방 증가를 막는 경계로 제시했지만, 이는 평균적 추정치다.
- **앱 번역**: `estimated_energy_deficit`, `training_status`, `lean_mass_delta`, `strength_delta`를 분리하고, 감량 중 근육 증가가 없다는 이유만으로 훈련 실패를 표시하지 않는다.
- **제한**: 적자 계산·기간·대상자와 연구 수가 제한적이다. `500 kcal`를 하드 게이트로 사용하지 않는다.

### 03#5. Ali et al. (2026) — 저항훈련자의 시간제한 식사

- **원문**: [PubMed PMID 41687432](https://pubmed.ncbi.nlm.nih.gov/41687432/), DOI `10.1016/j.nutres.2026.01.001`.
- **상태**: `verified_abstract`.
- **PICO/결과**: 저항훈련 성인의 TRE RCT 8개를 검토했고 식사창은 8–10시간이었다. 체중 MD `−1.82 kg`(95% CI `−3.66 to 0.01`), 제지방 MD `0.27 kg`(CI `−0.80 to 1.34`)은 유의하지 않았고, 체지방률 MD `−1.57%`, 지방량 MD `−1.25 kg`은 유의했다. BMI도 MD `−0.75 kg/m²`였다.
- **앱 번역**: TRE는 `meal_window`와 순응도 옵션으로 저장하고, 에너지 섭취·단백질·훈련을 대체하는 규칙으로 만들지 않는다.
- **제한**: 8개 연구, 소표본·단기간이며 임상적 크기는 아직 불명확하다.

### 03#6. Garthe et al. (2011) — 느린 감량과 빠른 감량

- **원문**: [PubMed PMID 21558571](https://pubmed.ncbi.nlm.nih.gov/21558571/), DOI `10.1123/ijsnem.21.2.97`.
- **상태**: `verified_abstract`.
- **설계/결과**: 엘리트 선수 24명을 주당 체중 0.7% 감량(느림, n=13)과 1.4% 감량(빠름, n=11)으로 무작위 배정했다. 두 군 모두 체중·지방량이 감소했지만, 제지방은 느린 감량에서 `+2.1%`, 빠른 감량에서 `−0.2%`로 달랐다. 느린 군의 중재 기간은 8.5주, 빠른 군은 5.3주였다.
- **앱 번역**: `weekly_weight_change_percent`와 `fat_free_mass_delta`, `strength_delta`를 함께 추적하고 빠른 감량에는 보존 리스크 경고를 둔다.
- **제한**: 표본이 작고 엘리트 선수 연구이므로 일반 사용자에게 동일한 효과를 보장하지 않는다.

### 03#7. Ruiz-Castellano et al. (2021) — 저항훈련자 감량기 내러티브 리뷰

- **원문**: [PubMed PMID 34579132](https://pubmed.ncbi.nlm.nih.gov/34579132/), [공개 전문 PMC8471721](https://pmc.ncbi.nlm.nih.gov/articles/PMC8471721/), DOI `10.3390/nu13093255`.
- **상태**: `verified_full_text`.
- **성격/요지**: pooled effect가 아닌 내러티브 리뷰다. 감량 속도 0.5–1.0%/주, 단백질 2.2–3.0 g/kg/day, 3–6회 분배, 활동량에 따른 탄수화물, 카페인·크레아틴을 실무 전략으로 제시한다.
- **앱 번역**: 처방값이 아니라 `target_rate`, `protein_target`, `activity_carbohydrate`, `supplement_use`의 초기 제안으로 저장한다.
- **제한**: 근거가 서로 다른 수준으로 섞였고 저항훈련 선수·대회 준비 맥락이 강하다.

### 03#8. Helms et al. (2014) — 자연 보디빌딩 감량기 영양

- **원문**: [PubMed PMID 24864135](https://pubmed.ncbi.nlm.nih.gov/24864135/), [공개 전문 PMC4033492](https://pmc.ncbi.nlm.nih.gov/articles/PMC4033492/), DOI `10.1186/1550-2783-11-20`.
- **상태**: `verified_full_text`.
- **요지**: 주당 체중 0.5–1% 감량, 제지방 기준 단백질 2.3–3.1 g/kg/day, 지방 15–30% 에너지, 3–6회 식사 등을 제시한다. 수분·전해질 조작은 위험할 수 있으며, 보충제·식사 타이밍의 효과는 제한적이거나 연구가 더 필요하다.
- **앱 번역**: 대회 준비용 수치를 일반 사용자의 기본값으로 복사하지 않고, `contest_prep_context=true`일 때만 참고값으로 노출한다. 극단적 수분 조작은 안전 게이트로 차단한다.
- **제한**: 리뷰이며 자연 보디빌딩이라는 특수 맥락이다. 섭식장애·신체상 위험도 함께 기록해야 한다.

### 03#9. Peos et al. (2019) — 간헐적 식이

- **원문**: [PubMed PMID 30654501](https://pubmed.ncbi.nlm.nih.gov/30654501/), [공개 전문 PMC6359485](https://pmc.ncbi.nlm.nih.gov/articles/PMC6359485/), DOI `10.3390/sports7010022`.
- **상태**: `verified_full_text`.
- **성격/결론**: 이론적 고려를 정리한 review로, 운동선수에서 IER의 효과 근거가 부족하다고 명시한다. diet break/refeed는 에너지 균형·순응도·심리 부담을 조정하는 선택지로 다룬다.
- **앱 번역**: `diet_break`, `adherence`, `hunger`, `training_quality`를 기록하되 IER을 지방 감소 우월 규칙으로 평가하지 않는다.
- **제한**: 직접적인 운동선수 RCT pooled effect가 없다.

### 03#10. Helms et al. (2015) — 감량기 저항·유산소 훈련

- **원문**: [PubMed PMID 24998610](https://pubmed.ncbi.nlm.nih.gov/24998610/), *Recommendations for natural bodybuilding contest preparation: resistance and cardiovascular training*.
- **상태**: `verified_abstract`.
- **요지**: 주당 근육군 2회 이상, 6–12회 중심의 저항운동과 필요한 만큼의 최소 유산소를 권고한다. 유산소 빈도·기간이 늘면 근력 적응 간섭이 커질 수 있고, 사이클·전신 양식이 간섭을 줄일 수 있다고 설명한다.
- **앱 번역**: 유산소를 `calorie_burn` 하나로 평가하지 않고 저항운동 품질·하체 피로·주간 회복 예산과 함께 계산한다.
- **제한**: 실무 review이며 운동선수 대회 준비 맥락이 강하다.

## 9. 04 린매스업·바디리컴포지션 — 원문 근거 검증 배치

### 04#1. Slater et al. (2019) — 에너지 surplus의 필요성

- **원문**: [PubMed PMID 31482093](https://pubmed.ncbi.nlm.nih.gov/31482093/), [공개 전문 PMC6710320](https://pmc.ncbi.nlm.nih.gov/articles/PMC6710320/), DOI `10.3389/fnut.2019.00131`.
- **상태**: `verified_full_text`.
- **결론**: 에너지 surplus가 근비대에 미치는 이론·실무를 검토했지만 최적 surplus의 “sweet spot”은 검증되지 않았다고 명시한다. 훈련경험·성별·에너지 상태에 따라 반응이 달라진다.
- **앱 번역**: `energy_balance`, `weekly_weight_change`, `fat_mass_delta`, `lean_mass_delta`로 surplus를 조정하고 `+500 kcal`를 고정 규칙으로 만들지 않는다.
- **제한**: review이며 직접적인 최적 surplus RCT가 아니다.

### 04#2. Murphy & Koehler (2022) — 적자에서의 리컴포지션 한계

- **원문/상태**: 03#4와 동일한 [PMID 34623696](https://pubmed.ncbi.nlm.nih.gov/34623696/), `cross_verified_abstract`.
- **결과**: 에너지 적자는 제지방 증가를 낮췄지만 근력 차이는 통계적으로 유의하지 않았다. 약 500 kcal/day 추정 경계는 개인 처방이 아니라 평균 메타회귀 결과다.
- **앱 번역/제한**: 리컴포지션 가능성 추정에 `training_status`, 체지방, 단백질, 적자 크기를 입력하되 보장 문구는 금지한다.

### 04#3. Barakat et al. (2020) — 훈련자도 가능한 바디리컴포지션

- **원문**: [출판사 DOI 10.1519/SSC.0000000000000584](https://doi.org/10.1519/SSC.0000000000000584).
- **상태**: `verified_abstract`, DOI 기반 서지 확인.
- **성격/결론**: 훈련자에서도 근육 증가와 지방 감소가 함께 관찰된 문헌을 검토한 review다. 가능한 조건으로 저항훈련, 충분한 단백질, 에너지 상태·수면·훈련경험을 논의하지만, 모든 훈련자가 동시에 변화한다는 효과크기를 제시하지 않는다.
- **앱 번역**: `recomp_candidate`는 이진 보장값이 아니라 조건부 상태로 표시하고, 체중·허리·체지방·근력·근육 측정 추세를 함께 요구한다.
- **제한**: body recomposition 정의와 체성분 측정법이 연구마다 다르다.

### 04#4. Morton et al. (2018) — 단백질 보충과 저항훈련

- **원문**: [PubMed PMID 28698222](https://pubmed.ncbi.nlm.nih.gov/28698222/), [공개 전문 PMC5867436](https://pmc.ncbi.nlm.nih.gov/articles/PMC5867436/), DOI `10.1136/bjsports-2017-097608`.
- **상태**: `verified_full_text`; 2020 correction(PMID 32943392)도 식별.
- **결과**: 49개 연구, 1,863명. 단백질 보충은 1RM `+2.49 kg`, 제지방 `+0.30 kg`, 근섬유 CSA `+310 µm²`의 평균 추가 변화를 보였다. 총 단백질 섭취가 약 `1.62 g/kg/day`를 넘으면 제지방 추가 이득이 관찰되지 않았다.
- **앱 번역**: 단백질 보충제 사용 자체가 아니라 `total_protein_g_per_kg`, 훈련기간, 목표 outcome을 우선 평가한다.
- **제한**: 평균 효과이며 식이·훈련경험·연령에 따라 달라진다. 보충제를 필수로 안내하지 않는다.

### 04#5. Desai et al. (2024) — 크레아틴과 체성분

- **원문**: [PubMed PMID 39074168](https://pubmed.ncbi.nlm.nih.gov/39074168/), DOI `10.1519/JSC.0000000000004862`.
- **상태**: `verified_abstract`.
- **결과**: 50세 미만 성인의 12개 연구에서 크레아틴+저항훈련은 훈련 단독 대비 제지방 `+1.14 kg`(95% CI `0.69 to 1.59`), 체지방률 `−0.88%`, 지방량 `−0.73 kg`이었다. 훈련경험·탄수화물 음료 하위군 차이는 없었고, 연구의 52%만 낮은 bias risk였다.
- **앱 번역**: `creatine_use`를 체성분 모델의 보조 입력으로만 사용하고, 수분에 영향을 받을 수 있는 제지방 측정값을 단일 측정으로 해석하지 않는다.
- **제한**: 연구 수가 적고 체성분 측정법·크레아틴 용량이 이질적이다.

### 04#6. Ashtary-Larky et al. (2025) — 초보·숙련자 크레아틴 비교

- **원문**: [PubMed PMID 41433021](https://pubmed.ncbi.nlm.nih.gov/41433021/), [공개 전문 PMC12777911](https://pmc.ncbi.nlm.nih.gov/articles/PMC12777911/), DOI `10.1080/15502783.2025.2586523`.
- **상태**: `verified_abstract`.
- **결과**: 61개 trial의 pooled 분석에서 크레아틴은 제지방 WMD `+1.39 kg`(95% CI `1.07 to 1.70`), 체중 `+0.89 kg`을 보였지만 지방량·BMI·체지방률은 유의하지 않았다. 숙련자의 제지방 증가가 1.82 kg 대 1.23 kg으로 컸으나 집단 차이는 유의하지 않았다.
- **앱 번역**: `training_status`를 크레아틴 효과의 조절 후보로 저장하되, 숙련도에 따른 확정 가중치는 두지 않는다.
- **제한**: 2025년까지의 문헌과 보충제 이해상충 가능성을 기록하고, 질·용량·기간의 차이를 표시한다.

### 04#7. Lopez et al. (2022) — 과체중·비만군의 리컴포지션 기반

- **원문/상태**: 03#3과 동일한 [PMID 35191588](https://pubmed.ncbi.nlm.nih.gov/35191588/), `cross_verified_full_text`.
- **결과**: 저항운동 단독은 제지방 증가, 저항운동+칼로리 제한은 제지방 유지와 지방 감소에 유리했다.
- **앱 번역/제한**: 과체중·초보자 맥락에서는 `recomp_candidate`를 높일 수 있지만, 고급 훈련자에게 그대로 일반화하지 않는다.

### 04#8. Deutz et al. (2014) — 고령자의 단백질·운동

- **원문**: [공개 전문 PMC4208946](https://pmc.ncbi.nlm.nih.gov/articles/PMC4208946/).
- **상태**: `verified_full_text`.
- **권고**: 건강한 고령자는 최소 단백질 `1.0–1.2 g/kg/day`, 질병·영양불량 위험이 있으면 `1.2–1.5 g/kg/day` 이상을 고려하고, 가능한 범위에서 매일 신체활동과 저항운동을 권한다.
- **앱 번역**: `age`, 건강상태·영양불량 위험, `protein_g_per_kg`, 기능 outcome을 별도로 입력하고 젊은 성인 기본값을 그대로 복사하지 않는다.
- **제한**: 전문가 권고이며 개인의 신장질환·의학적 상태는 앱의 일반 rule보다 의료진 확인이 우선이다.

### 04#9. ISSN (2017) — protein and exercise position stand

- **원문**: [공개 전문 PMC5477153](https://pmc.ncbi.nlm.nih.gov/articles/PMC5477153/), DOI `10.1186/s12970-017-0177-8`.
- **상태**: `verified_full_text`.
- **요지**: 운동인의 총 단백질 섭취를 중심으로 단백질 종류·품질·타이밍을 정리한다. 단백질 보충은 편의 수단이며 총 섭취가 충족되면 보충제 자체를 필수로 보지 않는다.
- **앱 번역**: `daily_total`을 1차 score로 하고 식사별 분배·운동 전후 섭취는 2차 개선으로 둔다.
- **제한**: position stand이며 저자·기관과 산업 이해상충 공개를 evidence metadata에 보존한다.

### 04#10. Iraki et al. (2019) — 오프시즌 보디빌더 영양

- **원문**: [PubMed PMID 31247944](https://pubmed.ncbi.nlm.nih.gov/31247944/), [공개 전문 PMC6680710](https://pmc.ncbi.nlm.nih.gov/articles/PMC6680710/), DOI `10.3390/sports7070154`.
- **상태**: `verified_full_text`.
- **권고**: 초·중급자의 체중 증가 목표를 주당 `0.25–0.5%`, 에너지 `+10–20%`, 단백질 `1.6–2.2 g/kg/day`, 지방 `0.5–1.5 g/kg/day`로 제시하며 고급자는 더 보수적으로 접근한다.
- **앱 번역**: `surplus_percent`, `weekly_weight_gain_percent`, `protein`, `fat`, `carbohydrate`를 분리하고, 실제 지방 증가가 과하면 surplus를 줄이는 feedback loop를 만든다.
- **제한**: 오프시즌 보디빌더 연구가 부족한 상황의 narrative review이며, 장기 대규모 RCT가 아니다.

04번의 결론은 “surplus·크레아틴·고단백만으로 리컴포지션을 보장한다”가 아니다. 앱은 체중·지방·제지방·근력의 방향을 동시에 추적하고, 측정 불확실성과 훈련경험·연령·에너지 상태를 evidence record의 조절 변수로 보존한다.

## 10. 05 무산소·파워·스프린트 — 원문 근거 검증 배치

### 05#1. Bai & Xu (2026) — 크레아틴·베타알라닌 병용

- **원문**: [PubMed PMID 42384726](https://pubmed.ncbi.nlm.nih.gov/42384726/), DOI `10.1080/15502783.2026.2695133`.
- **상태**: `verified_abstract`.
- **결과**: 52개 RCT의 네트워크 메타분석. 크레아틴은 스프린트(SMD `−0.64`), 점프(`0.33`), 반복스프린트(`−0.78`), 상체 근지구력(`0.43`)에서 placebo보다 유의했고, 베타알라닌 효과는 맥락 특이적이었다. 크레아틴+베타알라닌은 크레아틴 단독보다 시너지 이득을 보이지 않았다.
- **앱 번역**: `supplement`, `task_type`, `duration`, `performance_metric`을 분리하고 병용을 자동 우월 규칙으로 만들지 않는다.
- **제한**: 2025년 8월까지 검색된 연구이며, 연구·종목·용량 이질성이 있다.

### 05#2. Gu et al. (2026) — 젊은 남성의 크레아틴과 훈련 맥락

- **원문**: [PubMed PMID 42027564](https://pubmed.ncbi.nlm.nih.gov/42027564/), [공개 전문 PMC13099317](https://pmc.ncbi.nlm.nih.gov/articles/PMC13099317/), DOI `10.3389/fnut.2026.1800546`; [2026 correction PMID 42245548](https://pubmed.ncbi.nlm.nih.gov/42245548/)도 연결.
- **상태**: `metadata_corrected`, `verified_full_text`.
- **결과**: 18–30세 남성 39개 trial. 저항훈련 맥락에서 제지방 `+3.39 kg`, LBM `+2.70 kg`이었고 비저항훈련에서는 유의한 체성분 증가가 없었다. Wingate peak/mean power는 두 맥락 모두 개선됐고 CMJ는 2.87 cm였으나 이질성이 높았다(`I²=88.5%`).
- **앱 번역**: 크레아틴의 체성분 효과는 `resistance_training=true` 조건에 연결하고, 파워 outcome은 별도 표시한다.
- **제한**: 젊은 남성만 대상으로 하며, 2026 correction을 source chain에 보존해야 한다.

### 05#3. Kreider et al. (2017) — 크레아틴 position stand

- **원문**: [PubMed PMID 28615996](https://pubmed.ncbi.nlm.nih.gov/28615996/), [공개 전문 PMC5469049](https://pmc.ncbi.nlm.nih.gov/articles/PMC5469049/), DOI `10.1186/s12970-017-0173-z`.
- **상태**: `verified_full_text`.
- **요지**: 크레아틴이 고강도 수행·훈련 적응을 개선할 수 있고 건강한 사람에서 단·장기 섭취가 대체로 안전하다고 정리한다. 이는 pooled effect 하나가 아니라 안전성·기전·활용을 종합한 position stand다.
- **앱 번역**: `creatine_form`, `dose`, `duration`, `medical_context`, `adverse_event`를 저장하고, 질환·임신·약물 등은 의료 rule로 넘긴다.
- **제한**: position stand이며 저자 일부의 산업 관계를 metadata에 보존한다. 모든 개인에게 같은 수행 효과를 약속하지 않는다.

### 05#4. Trexler et al. (2015) — 베타알라닌 position stand

- **원문**: [PubMed PMID 26175657](https://pubmed.ncbi.nlm.nih.gov/26175657/), [공개 전문 PMC4501114](https://pmc.ncbi.nlm.nih.gov/articles/PMC4501114/), DOI `10.1186/s12970-015-0090-y`.
- **상태**: `verified_full_text`.
- **요지**: 4–6 g/day를 2–4주 이상 섭취하면 근육 카르노신이 증가하고, 1–4분 지속되는 고강도 과제에서 효과가 더 두드러질 수 있다고 정리한다. 감각 이상은 분할 섭취로 줄일 수 있다.
- **앱 번역**: `task_duration_seconds`가 60–240초에 가까운 경우에만 관련성 label을 높이고, 복용량·기간·paresthesia를 표시한다.
- **제한**: position stand이며 긴 지구력·최대근력에 대한 근거는 약하다.

### 05#5. Hobson et al. (2012) — 베타알라닌 메타분석

- **원문**: [공개 전문 PMC3374095](https://pmc.ncbi.nlm.nih.gov/articles/PMC3374095/), PMID `22270875`, DOI `10.1007/s00726-011-1200-z`.
- **상태**: `verified_full_text`.
- **결과**: 15개 논문, 57개 측정, 360명. 베타알라닌군의 중앙 효과크기 `0.374`가 placebo `0.108`보다 높았고, 총 섭취 중앙값 179 g에서 운동 측정치 중앙 개선은 `2.85%`였다. 60–240초 운동은 유의했지만 60초 미만은 유의하지 않았다.
- **앱 번역**: “무산소”를 단일 label로 두지 않고 `exercise_duration_band`와 `capacity_vs_performance`를 구분한다.
- **제한**: 대부분 레크리에이션 활동자이며, performance-based test에서는 효과가 제한적이었다.

### 05#6. Grgic et al. (2020) — 카페인 umbrella review

- **원문**: [PubMed PMID 30926628](https://pubmed.ncbi.nlm.nih.gov/30926628/), DOI `10.1136/bjsports-2018-100278`.
- **상태**: `verified_abstract`.
- **결과**: 11개 review와 21개 meta-analysis를 종합했다. 카페인은 유산소 지구력, 근력·근지구력, 파워, 점프, 속도에서 대체로 ergogenic이었으나 95% prediction interval은 일부 방향이 불확실했고 근거 질은 outcome별 중간~매우 낮음이었다.
- **앱 번역**: `caffeine_mg_per_kg`, 섭취 시각, 수면 손실·민감도를 순효과에 반영하고, 모든 사용자에게 동일한 용량을 권하지 않는다.
- **제한**: 연구가 젊은 남성에 편중됐다.

### 05#7. Mielgo-Ayuso et al. (2019) — 축구선수 카페인 review

- **원문**: [PubMed PMID 30791576](https://pubmed.ncbi.nlm.nih.gov/30791576/), [공개 전문 PMC6412526](https://pmc.ncbi.nlm.nih.gov/articles/PMC6412526/), DOI `10.3390/nu11020440`.
- **상태**: `verified_full_text`.
- **결과**: 17개 논문을 검토했다. 점프·반복스프린트·모의경기 달리기에서 개선을 보고한 연구가 있었지만, 피로 인지 감소는 보고되지 않았고 근손상 표지 변화도 일관되지 않았다.
- **앱 번역**: 카페인을 `sport_task`별로 보여주고 `RPE`가 내려갈 것이라고 약속하지 않는다.
- **제한**: 축구 특이 review라 일반 헬스장 무산소 세션에 직접 일반화하지 않는다.

### 05#8. Blazevich & Babault (2019) — PAP와 PAPE 구분

- **원문**: [PubMed PMID 31736781](https://pubmed.ncbi.nlm.nih.gov/31736781/), DOI `10.3389/fphys.2019.01359`.
- **상태**: `verified_full_text`.
- **성격/결론**: classic PAP(전기자극 twitch force)와 voluntary PAPE(자발적 수행 향상)는 시간경과와 기전이 다를 수 있다고 설명하는 narrative review다.
- **앱 번역**: 워밍업/conditioning set 후 `latency_seconds`, `conditioning_load`, `performance_delta`를 기록하고 PAP와 PAPE를 같은 effect field로 합치지 않는다.
- **제한**: 표준화된 처방 효과크기를 제공하지 않는다.

### 05#9. Sole et al. (2021) — 플라이오메트릭과 체력

- **원문**: [PubMed PMID 33717707](https://pubmed.ncbi.nlm.nih.gov/33717707/), [공개 전문 PMC7931718](https://pmc.ncbi.nlm.nih.gov/articles/PMC7931718/), DOI `10.7717/peerj.11004`.
- **상태**: `verified_full_text`.
- **결과**: 개인 종목 선수 26개 연구, 667명. 수직점프 ES `0.49`, 선형 스프린트 `0.23`, 최대근력 `0.50`, 지구력 `0.30`; 방향전환 스프린트는 유의하지 않았다(ES `0.34`, `p=.205`). 프로그램 중앙 기간은 8.5주였다.
- **앱 번역**: `jump_contacts`, `sprint_distance`, `landing_quality`, `session_count`를 파워 훈련 기록으로 저장하고, 방향전환·직선 스프린트를 분리한다.
- **제한**: 운동량·종목·대상자 차이가 크고 장기 최적 용량은 불명확하다.

### 05#10. Deng et al. (2025) — 컨디셔닝 중 보충제 네트워크 메타분석

- **원문**: [PubMed PMID 41323837](https://pubmed.ncbi.nlm.nih.gov/41323837/), [공개 전문 PMC12663695](https://pmc.ncbi.nlm.nih.gov/articles/PMC12663695/), DOI `10.1002/fsn3.71243`.
- **상태**: `verified_abstract`.
- **성격/결과**: 컨디셔닝 훈련 중 보충제의 peak/mean anaerobic power, VO₂max, 지구력 결과를 네트워크로 비교한다. outcome별 보충제 순위와 불확실성을 제공하는 분석이지, 하나의 종합 퍼포먼스 점수를 검증한 연구가 아니다.
- **앱 번역**: `supplement × outcome` 매트릭스로 저장하고, peak power·mean power·VO₂max·endurance를 합산하지 않는다.
- **제한**: 간접 비교와 보충제·훈련 프로토콜 이질성 때문에 개인 처방의 확정값으로 사용하지 않는다.

05번의 결론은 크레아틴·카페인·베타알라닌·플라이오메트릭을 하나의 “파워 부스터”로 묶지 않는 것이다. 앱은 과제 지속시간, 측정 outcome, 급성/만성 개입, 안전·수면·회복을 별도 축으로 저장한다.

## 11. 06 유산소·심폐지구력 — 원문 근거 검증 배치

### 06#1. Xiao et al. (2026) — HIIT와 SIT 비교

- **원문**: [PubMed PMID 41740126](https://pubmed.ncbi.nlm.nih.gov/41740126/), DOI `10.1519/JSC.0000000000005374`.
- **상태**: `verified_abstract`.
- **결과**: 12개 무작위 crossover trial, 460명. HIIT가 SIT보다 VO₂max SMD `0.56`(95% CI `0.23–0.88`), MAP/MAV `0.40`(`0.06–0.75`) 개선에 유리했다. 4분 초과 work interval·8주 초과 프로그램에서 신호가 커졌지만 대부분 outcome의 근거 질은 낮거나 매우 낮았다.
- **앱 번역**: `interval_type`, `work_seconds`, `recovery_seconds`, `session_duration`, `weekly_frequency`, `vo2max`를 저장하고 HIIT와 SIT를 같은 처방으로 취급하지 않는다.
- **제한**: 2026년 최신 분석이며 연구 프로토콜·훈련 수준 이질성이 높다.

### 06#2. Qi et al. (2026) — 훈련자 HIIT 메타분석

- **원문**: [PubMed PMID 41540436](https://pubmed.ncbi.nlm.nih.gov/41540436/), [공개 전문 PMC12857149](https://pmc.ncbi.nlm.nih.gov/articles/PMC12857149/), DOI `10.1186/s13102-025-01479-7`.
- **상태**: `verified_full_text`.
- **결과**: 훈련된 남녀 운동선수 18개 연구. VO₂max SMD `1.11`(95% CI `0.48–1.74`), VO₂peak MD `0.52`(`0.08–0.97`)였고 speed·agility도 개선됐지만 HRmax·jump·power는 유의하지 않았다.
- **앱 번역**: HIIT의 주된 기대 outcome을 심폐·속도·민첩성으로 두고, 파워·점프 향상을 자동 예측하지 않는다.
- **제한**: 훈련자 정의와 종목이 넓고 이질성이 크다.

### 06#3. Ma et al. (2023) — 엘리트 선수 VO₂max

- **원문**: [PubMed PMID 37346345](https://pubmed.ncbi.nlm.nih.gov/37346345/), [공개 전문 PMC10279791](https://pmc.ncbi.nlm.nih.gov/articles/PMC10279791/), DOI `10.1016/j.heliyon.2023.e16663`.
- **상태**: `verified_full_text`.
- **결과**: 엘리트 선수 9개 연구, 176명(여성 80명). HIIT가 conventional training보다 VO₂max를 SMD `0.58`(95% CI `0.30–0.87`) 개선했다. 회복 간격 2분 이상, recovery intensity 40% 이하 하위군에서 긍정적 효과가 있었다.
- **앱 번역**: `athlete_level`, `recovery_duration`, `recovery_intensity`를 심폐 적응 모델의 조절 변수로 저장한다.
- **제한**: 엘리트 선수에 한정되며 일반 사용자에게 동일한 효과를 적용하지 않는다.

### 06#4. Tabata et al. (1996) — 중강도 endurance와 고강도 간헐훈련

- **원문**: [PubMed PMID 8897392](https://pubmed.ncbi.nlm.nih.gov/8897392/), DOI `10.1097/00005768-199610000-00018`.
- **상태**: `verified_abstract`.
- **설계/결과**: 중강도군은 70% VO₂max, 60분/day, 주 5회, 6주; 간헐군은 약 170% VO₂max의 20초 운동/10초 휴식 7–8세트, 주 5회, 6주였다. VO₂max는 중강도군 53→58, 간헐군 약 7 ml/kg/min 증가했고, 간헐군 무산소 능력은 28% 증가했다.
- **앱 번역**: `aerobic_capacity`와 `anaerobic_capacity`를 분리하고 Tabata 프로토콜을 모든 사용자 기본값으로 복사하지 않는다.
- **제한**: 매우 작은 표본의 고전 실험이며 자전거 ergometer·젊은 남성 맥락이다.

### 06#5. Buchheit & Laursen (2013) — HIIT cardiopulmonary programming

- **원문**: [PubMed PMID 23539308](https://pubmed.ncbi.nlm.nih.gov/23539308/), DOI `10.1007/s40279-013-0029-x`.
- **상태**: `verified_abstract`.
- **성격/요지**: 효과크기 메타분석이 아닌 programming review다. work/recovery 강도·기간, 반복·세트, modality 등 최대 9개 변수를 조정하고 세션 중 VO₂max 90% 이상 시간과 회복·신경근 부하를 고려한다.
- **앱 번역**: 인터벌을 단순 “고강도 여부”로 기록하지 않고 `work_to_rest`, `time_above_90_vo2max`, `session_load`, `musculoskeletal_load`를 구조화한다.
- **제한**: 직접적인 개인별 rule weight를 제공하지 않는다.

### 06#6. Buchheit & Laursen (2013) Part II — 무산소·신경근 부하

- **원문**: [PubMed PMID 23832851](https://pubmed.ncbi.nlm.nih.gov/23832851/), *High-intensity interval training, solutions to the programming puzzle. Part II*.
- **상태**: `verified_abstract`.
- **요지**: HIT의 interval manipulation, periodization, glycolytic contribution, neuromuscular/musculoskeletal load와 실무 적용을 다루는 review다.
- **앱 번역**: HIIT session에 `glycolytic_demand`, `neuromuscular_fatigue`, `impact_load`, `recovery_need`를 함께 남긴다.
- **제한**: protocol review이며 특정 interval이 장기적으로 최고라는 pooled effect는 없다.

### 06#7. Stöggl & Sperlich (2014) — polarized training

- **원문**: [PubMed PMID 24550842](https://pubmed.ncbi.nlm.nih.gov/24550842/), [공개 전문 PMC3912323](https://pmc.ncbi.nlm.nih.gov/articles/PMC3912323/), DOI `10.3389/fphys.2014.00033`.
- **상태**: `verified_full_text`.
- **결과**: 잘 훈련된 지구력 선수 48명을 9주간 비교. polarized군은 VO₂peak `+6.8 ml·min⁻¹·kg⁻¹`(11.7%), time to exhaustion `+17.4%`, peak velocity/power `+5.1%`로 가장 큰 개선을 보였다. work economy 차이는 없었다.
- **앱 번역**: 주간 세션을 `zone1/zone2/zone3` 시간비율로 저장하고, polarized를 단일 강도 세션 규칙으로 오해하지 않는다.
- **제한**: 작은 표본·특정 훈련자 집단이며 후속 문헌은 intensity distribution 결론이 종목·시즌별로 다르다고 지적한다.

### 06#8. Stöggl & Sperlich (2015) — 엘리트의 intensity distribution

- **원문**: [PubMed PMID 26578968](https://pubmed.ncbi.nlm.nih.gov/26578968/), [공개 전문 PMC4621419](https://pmc.ncbi.nlm.nih.gov/articles/PMC4621419/), DOI `10.3389/fphys.2015.00295`.
- **상태**: `verified_full_text`.
- **요지**: well-trained~elite 선수의 zone 분포를 정리한 review. 많은 retrospective data는 pyramidal이고 일부 세계 수준 선수는 특정 시즌에 polarized pattern을 사용한다. 따라서 최적 분포는 종목·시즌·측정법에 의존한다.
- **앱 번역**: `season_phase`, `sport`, `zone_definition`, `time_in_zone`을 필수 metadata로 하고, 모든 사용자에게 80/20 같은 비율을 하드코딩하지 않는다.
- **제한**: retrospective 자료와 prospective 연구가 섞이며, review 자체가 개인별 최적 비율을 확정하지 않는다.

### 06#9. Oppert et al. (2021) — 과체중·비만 운동 권고

- **원문**: [PubMed PMID 34076949](https://pubmed.ncbi.nlm.nih.gov/34076949/), [공개 전문 PMC8365734](https://pmc.ncbi.nlm.nih.gov/articles/PMC8365734/), DOI `10.1111/obr.13273`.
- **상태**: `verified_full_text`.
- **권고**: 체중·총 지방·내장지방·간지방·혈압에는 중강도 유산소를 우선하고, 감량 중 제지방 보존에는 중~고강도 저항운동을 권고한다. 심폐체력은 유산소·저항·혼합·HIIT 모두 가능하지만 HIIT는 심혈관 위험 평가와 감독을 전제로 한다.
- **앱 번역**: `health_risk`, `goal`, `exercise_mode`, `cardiorespiratory_fitness`, `lean_mass_preservation`을 분리하고 고위험 사용자의 HIIT recommendation을 제한한다.
- **제한**: 전문가 권고이며 의료 위험 평가를 앱 rule이 대체할 수 없다.

### 06#10. Wen et al. (2019) — HIIT protocol별 VO₂max

- **원문**: [PubMed PMID 30733142](https://pubmed.ncbi.nlm.nih.gov/30733142/), DOI `10.1016/j.jsams.2019.01.013`.
- **상태**: `verified_abstract`.
- **결과**: 53개 연구의 RCT 메타분석. 짧은 interval(≤30초), 낮은 volume(≤5분), 단기(≤4주)도 VO₂max에 효과가 있었고(SMD `0.79–1.65`), MICT와 비교해 최대화를 원하면 긴 interval(≥2분), 높은 volume(≥15분), 4–12주가 더 유리했다(SMD `0.65–1.07`).
- **앱 번역**: 목표가 “시간 효율”인지 “최대 심폐 적응”인지에 따라 interval length·volume·기간을 다른 recommendation path로 둔다.
- **제한**: 건강·과체중·운동선수가 섞였고 protocol effect는 평균값이다.

06번의 결론은 HIIT가 모든 심폐 목표의 최고 해법이라는 것이 아니다. 앱은 운동 강도 분포, work/recovery 구조, 세션량, 위험도, VO₂max·time-trial·속도·파워 outcome을 분리해 평가한다.

## 12. 07 동시훈련·하이브리드 — 원문 근거 검증 배치

### 07#1. Held et al. (2026) — 동시훈련 umbrella review

- **원문**: [PubMed PMID 41762427](https://pubmed.ncbi.nlm.nih.gov/41762427/), DOI `10.1007/s40279-026-02401-y`.
- **상태**: `verified_abstract`.
- **결과**: 17개 meta-analysis, 144개 연구, 1,492명. 동시훈련은 endurance training과 유사한 유산소 향상을 보였고, RT와 비교하면 유산소 SMD `0.77`이었으며 근력·파워·근비대는 대체로 comparable했다. RT를 ET보다 먼저 하는 경향이 근력에 유리했지만 sequence의 유의한 차이는 없었다.
- **앱 번역**: `strength_goal`, `endurance_goal`, `sequence`, `session_gap`, `training_status`를 recommendation 입력으로 저장한다.
- **제한**: 고도로 훈련된 엘리트 자료가 부족하고, umbrella 결과는 개별 사용자의 최적 순서를 확정하지 않는다.

### 07#2. Ferraro-Farro et al. (2026) — SIT 간섭효과

- **원문**: [PubMed PMID 41734815](https://pubmed.ncbi.nlm.nih.gov/41734815/), DOI `10.1055/a-2820-4527`; [correction PMID 41927039](https://pubmed.ncbi.nlm.nih.gov/41927039/) 연결.
- **상태**: `metadata_corrected`, `verified_abstract`.
- **결과**: 9개 연구, 177명. SIT+RT와 RT 단독 간 하체 근력 SMD `0.01`(`p=.94`), 상체 근력 `−0.06`, 점프 `0.11`, 스프린트 `−0.01`로 차이가 없었고, VO₂max는 SIT+RT가 SMD `0.78`(`p=.001`) 높았다. 10초 이하 short sprint에서 점프 이득이 SMD `0.41`이었다.
- **앱 번역**: SIT를 endurance mode로 별도 저장하고, `lower_body_strength`와 `vo2max` outcome을 분리한다.
- **제한**: correction을 함께 보존해야 하며, 표본·프로토콜이 작고 짧다.

### 07#3. Conceição et al. (2026) — long-interval HIIT와 근비대

- **원문**: [PubMed PMID 41369592](https://pubmed.ncbi.nlm.nih.gov/41369592/), DOI `10.1152/japplphysiol.00642.2025`.
- **상태**: `verified_abstract`.
- **설계/결과**: 비훈련 젊은 남성 19명을 16주 concurrent 또는 RT로 배정했다. 두 군 모두 운동 후 단백질합성이 증가했고 type II fiber size도 증가했다. 근력 증가는 RT가 더 컸지만 VO₂peak는 concurrent에서만 개선됐다.
- **앱 번역**: 동시훈련의 `hypertrophy`, `strength`, `vo2peak`를 별도 outcome으로 저장하고, “분자 적응 보존”을 근력 동등성으로 오해하지 않는다.
- **제한**: 비훈련 젊은 남성, long-interval HIIT라는 특정 프로토콜이다.

### 07#4. Huiberts et al. (2024) — 성별·훈련상태

- **원문**: [PubMed PMID 37847373](https://pubmed.ncbi.nlm.nih.gov/37847373/), [공개 전문 PMC10933151](https://pmc.ncbi.nlm.nih.gov/articles/PMC10933151/), DOI `10.1007/s40279-023-01943-9`.
- **상태**: `verified_full_text`.
- **결과**: 59개 연구, 1,346명. 남성 하체 근력 적응은 concurrent에서 blunted(SMD `−0.43`, CI `−0.64 to −0.22`)였지만 여성은 `0.08`(CI `−0.34 to 0.49`)로 차이가 없었다. 상체 근력·파워·VO₂max 성별 차이는 없었고, 비훈련 endurance 참가자에서만 VO₂max 이득 저하가 관찰됐다.
- **앱 번역**: `sex`, `training_status`, `lower_body_strength`를 조절 변수로 기록하되 성별 기반 자동 제한은 하지 않는다.
- **제한**: 여성·고도로 훈련된 집단의 데이터가 적고 근비대 결론은 불충분하다.

### 07#5. Chen et al. (2024) — 동시훈련 endurance mode network meta-analysis

- **원문**: [PubMed PMID 38187085](https://pubmed.ncbi.nlm.nih.gov/38187085/), [공개 전문 PMC10767279](https://pmc.ncbi.nlm.nih.gov/articles/PMC10767279/), DOI `10.1016/j.jesf.2023.12.005`.
- **상태**: `verified_full_text`.
- **결과**: 40개 연구, 841명. 하체 최대근력은 모든 동시훈련 양식이 RT 단독보다 낮은 방향이었지만 HIIT running+RT가 다른 mode 대비 상대적으로 유리했다. MCSA는 HIIT running+RT SMD `0.15`(CI `−0.46 to 0.76`), moderate cycling+RT `0.07`이었다.
- **앱 번역**: `running`, `cycling`, `HIIT`, `moderate_continuous`를 mode별로 분리하고 하체 피로 비용을 별도 계산한다.
- **제한**: 네트워크 간접비교의 CI가 넓고, “유리”가 항상 유의한 우월을 뜻하지 않는다.

### 07#6. Wilson et al. (2012) — 고전 간섭 메타분석

- **원문**: [PubMed PMID 22002517](https://pubmed.ncbi.nlm.nih.gov/22002517/), DOI `10.1519/JSC.0b013e31823a3e2d`.
- **상태**: `verified_abstract`.
- **결과**: 21개 연구, 422개 effect size. concurrent strength ES `1.44`, hypertrophy `0.85`, power `0.55`; running을 포함한 concurrent는 cycling보다 근비대·근력 저하가 관찰됐다. endurance 빈도·기간이 늘수록 hypertrophy·strength·power effect와 음의 상관이 있었다.
- **앱 번역**: `endurance_modality`, 주간 빈도·기간, 하체 strength/power를 간섭 모델의 입력으로 저장한다.
- **제한**: 이후 연구와 방법론 차이가 있으므로 역사적 baseline으로 두고 최신 meta-analysis와 함께 해석한다.

### 07#7. Schumann et al. (2022) — aerobic+strength compatibility

- **원문**: [PubMed PMID 34757594](https://pubmed.ncbi.nlm.nih.gov/34757594/), [공개 전문 PMC8891239](https://pmc.ncbi.nlm.nih.gov/articles/PMC8891239/), DOI `10.1007/s40279-021-01587-7`.
- **상태**: `verified_full_text`.
- **결과**: 43개 연구. maximal strength SMD `−0.06`(CI `−0.20 to 0.09`), explosive strength `−0.28`(CI `−0.48 to −0.08`), hypertrophy `−0.01`(CI `−0.16 to 0.18`). 폭발적 힘 저하는 같은 세션에서 더 뚜렷했고, 세션을 3시간 이상 분리하면 유의하지 않았다.
- **앱 번역**: 사용자가 두 운동을 같은 날 해야 할 때 `session_gap_hours`를 추천 입력으로 사용하고, 근비대·최대근력·폭발력을 분리한다.
- **제한**: 세션 순서·간격 하위군은 연구 수와 프로토콜이 제한적이다.

### 07#8. Sabag et al. (2018) — HIIT+RT

- **원문**: [PubMed PMID 29658408](https://pubmed.ncbi.nlm.nih.gov/29658408/), DOI `10.1080/02640414.2018.1464636`.
- **상태**: `verified_abstract`.
- **결과**: HIIT+RT는 RT 단독과 비교해 근비대·상체 근력은 유사했고 하체 근력은 ES `−0.248`(`p=.049`) 낮았다. cycling HIIT에서 음의 방향이 더 컸고, running·긴 inter-modal rest가 완화 가능성을 보였다.
- **앱 번역**: HIIT 모달리티와 세션 간격에 따라 하체 strength warning을 조절하되, 운동 자체를 차단하지 않는다.
- **제한**: 하위분석의 통계적 안정성이 제한적이다.

### 07#9. Monserdà-Vilaró et al. (2023) — continuous vs intermittent endurance

- **원문**: [PubMed PMID 36508686](https://pubmed.ncbi.nlm.nih.gov/36508686/), DOI `10.1519/JSC.0000000000004304`.
- **상태**: `verified_abstract`.
- **결과**: 25개 연구. whole-muscle hypertrophy는 어떤 비교에서도 유의한 차이가 없었지만(SMD `<0.03`), HIIT가 포함된 경우 type I·II fiber hypertrophy는 RT 단독이 더 컸다(SMD `>0.33` 또는 `>0.27`). continuous ET만 포함한 비교에서는 차이가 없었다.
- **앱 번역**: `whole_muscle`과 `fiber_level` outcome을 분리하고, HIIT 추가가 전신 근육 크기를 감소시킨다고 단정하지 않는다.
- **제한**: 근섬유 측정 연구가 적고 비무작위 연구가 포함됐다.

### 07#10. seed 서지 미해결 — running/cycling endurance performance

- **seed 원문**: 제목 검색 [PubMed title query](https://pubmed.ncbi.nlm.nih.gov/?term=%22Concurrent%20aerobic%20and%20strength%20training%20for%20performance%20in%20running%20and%20cycling%20endurance%20athletes%3A%20A%20systematic%20review%20and%20meta-analysis%22%5BTitle%5D)에서 해당 제목·2017 meta-analysis의 고유 PMID를 확인하지 못했다.
- **상태**: `unresolved_seed_metadata`; 대체 가능한 공식 근거로 [Blagrove et al. systematic review, PMID 29249083](https://pubmed.ncbi.nlm.nih.gov/29249083/)와 [Rønnestad & Mujika review](https://onlinelibrary.wiley.com/doi/10.1111/sms.12104)를 연결한다.
- **확인된 내용**: 러너 24개 연구 review는 running economy가 대체로 2–8% 개선되고 time-trial·sprint가 좋아질 수 있지만 VO₂max·체성분은 대체로 변하지 않았다고 정리한다. 러닝·사이클 모두 근력훈련 추가가 경제성·경기 수행을 개선할 가능성이 있으나 모달리티별 근거가 다르다.
- **앱 번역**: `running_economy`, `time_trial`, `vo2max`, `cycling_power`, `strength_added`를 분리한다.
- **제한/조치**: seed는 원문 fingerprint 확정 전 rule evidence로 사용하지 않는다. 정확한 저자·DOI·PMID가 확인되면 별도 `07#10a`로 교체한다.

07번의 결론은 “동시훈련은 항상 간섭”도 “전혀 간섭하지 않음”도 아니다. 앱은 목표 outcome, 하체 중심 여부, endurance mode, 세션 간격, 훈련 수준, 성별과 실제 피로를 분리해 간섭 가능성을 표시한다.

## 13. 08 영양·보충제 — 원문 근거 검증 배치

### 08#1. Morton et al. (2018) — 단백질 보충

- **원문/상태**: 04#4와 동일한 [PubMed PMID 28698222](https://pubmed.ncbi.nlm.nih.gov/28698222/), `cross_verified_full_text`.
- **결과**: 49개 연구, 1,863명에서 단백질 보충의 추가 변화는 1RM `+2.49 kg`, 제지방 `+0.30 kg`이었고 총 단백질 약 `1.62 g/kg/day` 초과에서 제지방 추가 이득이 없었다.
- **앱 번역/제한**: 보충제 사용 여부보다 `total_protein_g_per_kg`를 우선 평가하고, 평균 효과를 개인 목표량 보장으로 표현하지 않는다. 2020 correction도 연결한다.

### 08#2. ISSN (2017) — protein and exercise

- **원문**: [공개 전문 PMC5477153](https://pmc.ncbi.nlm.nih.gov/articles/PMC5477153/), DOI `10.1186/s12970-017-0177-8`.
- **상태**: `verified_full_text`.
- **요지**: 운동인의 총 단백질, 단백질 품질·종류·타이밍을 종합한다. 적절한 식사로 총량을 채울 수 있으며 보충제는 실용적 수단이지 필수 조건이 아니다.
- **앱 번역**: `daily_total`, `meal_distribution`, `protein_source`, `supplement_reason`을 분리하고, 총량 미달을 보충제 광고성 추천으로 대체하지 않는다.
- **제한**: position stand 및 공개된 산업 이해상충을 metadata에 남긴다.

### 08#3. Kreider et al. (2017) — creatine safety and efficacy

- **원문/상태**: 05#3과 동일한 [PubMed PMID 28615996](https://pubmed.ncbi.nlm.nih.gov/28615996/), `cross_verified_full_text`.
- **요지**: 크레아틴은 고강도 운동 수행과 훈련 적응에 도움을 줄 수 있고 건강한 사람에서 일반적 단·장기 섭취가 잘 견딜 수 있다고 정리한다.
- **앱 번역/제한**: `creatine_form`, `dose`, `loading`, `maintenance`, `medical_context`, `adverse_event`를 저장하고, 의학적 안전성 판단은 앱이 하지 않는다.

### 08#4. Burke et al. (2023) — 크레아틴과 직접 영상 근비대

- **원문**: [PubMed PMID 37432300](https://pubmed.ncbi.nlm.nih.gov/37432300/), [공개 전문 PMC10180745](https://pmc.ncbi.nlm.nih.gov/articles/PMC10180745/), DOI `10.3390/nu15092116`.
- **상태**: `verified_full_text`.
- **결과**: 6주 이상인 10개 연구, 44개 outcome의 직접 MRI/CT/초음파 측정. pooled SMD `0.11`(95% CrI `−0.02 to 0.25`), 상·하체 근육 두께 추가 이득 약 `0.10–0.16 cm`, 젊은 성인 우세 추정 `0.17`(CrI `−0.09 to 0.45`)이었다.
- **앱 번역**: 체성분 프록시와 직접 근육두께 측정을 `measurement_method`로 구분하고, 크레아틴의 추가 근비대 효과는 작고 불확실하게 표시한다.
- **제한**: 연구 10개, 산업 이해상충 공개, 수분·측정법 영향 가능성이 있다.

### 08#5. Grgic et al. (2020) — 카페인 umbrella review

- **원문/상태**: 05#6과 동일한 [PubMed PMID 30926628](https://pubmed.ncbi.nlm.nih.gov/30926628/), `cross_verified_abstract`.
- **결과**: 21개 meta-analysis에서 유산소 지구력·근력·근지구력·파워·점프·속도에 대체로 ergogenic 신호가 있었지만, outcome별 근거 질은 중간~매우 낮음이고 연구가 젊은 남성에 편중됐다.
- **앱 번역/제한**: `dose`, 수면, 불안·민감도와 task를 함께 기록하고, 보충제 추천 점수 하나로 통합하지 않는다.

### 08#6. Guest et al. (2021) — caffeine position stand

- **원문**: [PubMed PMID 33388079](https://pubmed.ncbi.nlm.nih.gov/33388079/), [공개 전문 PMC7777221](https://pmc.ncbi.nlm.nih.gov/articles/PMC7777221/), DOI `10.1186/s12970-020-00383-4`.
- **상태**: `verified_full_text`.
- **요지**: 3–6 mg/kg에서 운동 수행 개선이 일관되며 9 mg/kg 같은 고용량은 부작용이 많고 필요하지 않다고 정리한다. 일반적인 타이밍은 운동 60분 전이나 제형에 따라 다르며, 수면·불안·개인 대사 차이가 반응을 바꾼다.
- **앱 번역**: `caffeine_mg_per_kg`, `pre_exercise_minutes`, 제형, 수면 시작 시각, 불안·심박 반응을 함께 기록하고 늦은 섭취에는 경고를 둔다.
- **제한**: position stand이며 의료적 카페인 금기 판단은 범위 밖이다.

### 08#7. Trexler et al. (2015) — beta-alanine position stand

- **원문/상태**: 05#4와 동일한 [PubMed PMID 26175657](https://pubmed.ncbi.nlm.nih.gov/26175657/), `cross_verified_full_text`.
- **요지**: 4–6 g/day, 2–4주 이상과 1–4분 고강도 과제에서 관련성이 높고, paresthesia를 분할 섭취로 줄일 수 있다.
- **앱 번역/제한**: `duration_band`, `dose`, `duration_days`, `tingling`을 저장하고 장시간 지구력·최대근력까지 일반화하지 않는다.

### 08#8. Burke et al. (2011) — 탄수화물 가용성과 경기

- **원문**: [PubMed PMID 21660838](https://pubmed.ncbi.nlm.nih.gov/21660838/), DOI `10.1080/02640414.2011.585473`, [출판사 전문](https://doi.org/10.1080/02640414.2011.585473).
- **상태**: `verified_full_text`.
- **요지**: 세션 전·중·후 탄수화물로 근육·중추신경계의 carbohydrate availability를 조절한다. 약 1시간 고강도는 소량·mouth rinse도 가능하고, 긴 운동은 대략 30–60 g/h, 2.5시간 초과는 최대 90 g/h가 제시된다.
- **앱 번역**: `session_duration`, `intensity`, `carb_before_during_after`, `grams_per_hour`를 훈련 요구량에 맞춰 계산한다. train-low를 기본값으로 만들지 않는다.
- **제한**: 운동선수·고강도/장시간 운동 중심의 review이며 개인 위장관 반응을 반영해야 한다.

### 08#9. Thomas, Erdman & Burke (2016) — ACSM/Academy/DC position paper

- **원문**: [PubMed PMID 26891166](https://pubmed.ncbi.nlm.nih.gov/26891166/), DOI `10.1249/MSS.0000000000000852`.
- **상태**: `verified_abstract`.
- **요지**: 운동 수행·회복을 위해 식품·수분·보충제의 종류·양·타이밍을 상황별로 설계해야 하며, 개인화는 스포츠 영양 전문가 의뢰를 권한다.
- **앱 번역**: `sport_type`, `training_load`, `competition_context`, 수분·탄수화물·단백질·보충제의 안전/근거 상태를 상위 schema로 사용한다.
- **제한**: broad position paper라 개별 보충제의 단일 효과크기를 제공하지 않는다.

### 08#10. Deng et al. (2025) — 보충제별 에너지 시스템 outcome

- **원문**: [PubMed PMID 41323837](https://pubmed.ncbi.nlm.nih.gov/41323837/), [공개 전문 PMC12663695](https://pmc.ncbi.nlm.nih.gov/articles/PMC12663695/), DOI `10.1002/fsn3.71243`.
- **상태**: `verified_full_text`.
- **결과**: 30개 RCT, 693명. peak anaerobic power는 protein SMD `0.85`, creatine `0.62`, HMB `0.60`, beta-alanine `0.58`; mean power는 beta-alanine `0.75`, protein `0.74`, creatine `0.74`; endurance는 protein만 SMD `0.99`였고 VO₂max에는 보충제 효과가 없었다. 일부 outcome은 확실성이 매우 낮았다.
- **앱 번역**: `supplement × outcome` evidence matrix를 사용하고 VO₂max까지 보충제로 올린다는 규칙은 생성하지 않는다.
- **제한**: 네트워크 간접비교, 작은 RCT, outcome별 확실성 차이를 보존한다.

08번의 결론은 보충제를 영양 섭취·훈련·수면의 대체물로 다루지 않는 것이다. 앱은 총 에너지·단백질·탄수화물·수분을 먼저 평가하고, 보충제는 과제 특이성·용량·타이밍·부작용·근거 확실성을 가진 선택 입력으로만 사용한다.

## 14. 09 회복·자기관리 — 원문 근거 검증 배치

검증 경계: `2026-08-14`, 09번 seed 문헌 10편. 회복기법은 “주관적 회복감”, “DOMS”, “단기 수행 회복”, “장기 훈련 적응”을 같은 effect로 합치지 않았다. 특히 massage·foam rolling·cryotherapy는 세션 직후의 체감 또는 단기 지표와 장기 근비대·근력 적응을 분리한다.

### 09#1. Gong et al. (2024) — 급성 수면박탈과 운동수행

- **원문**: [PubMed PMID 39006249](https://pubmed.ncbi.nlm.nih.gov/39006249/), [공개 전문 PMC11246080](https://pmc.ncbi.nlm.nih.gov/articles/PMC11246080/), DOI `10.2147/NSS.S467531`.
- **상태**: `verified_full_text`.
- **결과**: 27개 문헌, 75개 지표를 포함한 meta-analysis에서 급성 수면박탈의 전체 운동수행 효과는 ES `−0.56`이었다. 부분 수면박탈은 밤 후반 제한에서 `−1.17`로 더 컸고, 고강도 간헐운동 `−1.57`, 기술통제 `−1.06`, 속도 `−0.67`, 유산소 지구력 `−0.54`, 폭발력 `−0.39`였다. 오후 측정(`−1.11`)이 오전(`−0.30`)보다 불리했다.
- **앱 번역**: `sleep_duration`, `sleep_loss_window`, `measurement_time`, `session_type`, `readiness`를 저장하고 수면 부족을 “운동을 금지하는 진단”이 아니라 당일 강도·기술 복잡도·세션 시간 조절 신호로 사용한다.
- **제한**: 수면박탈 정의와 수행검사가 이질적이고 효과 이질성이 크므로 개인의 단일 세션 결과를 예측하는 확정 규칙으로 만들 수 없다.

### 09#2. Fullagar et al. (2015) — 수면과 운동수행 review

- **원문**: [PubMed PMID 25315456](https://pubmed.ncbi.nlm.nih.gov/25315456/), DOI `10.1007/s40279-014-0260-0`.
- **상태**: `verified_abstract`.
- **요지**: 수면 손실은 종종 sport-specific performance를 낮추고, 특히 인지·반응속도·기분·주관적 피로에 영향을 준다. 최대근력이나 gross motor 성능은 유지되는 경우도 있어 “모든 수행이 동일하게 붕괴한다”는 결론은 지지하지 않는다.
- **앱 번역**: `sleep_quality`, `sleep_quantity`, `cognitive_demand`, `gross_motor_demand`, `perceived_fatigue`를 분리한다. 수면 부족 당일에는 복합 기술·반응 기반 세션을 우선 낮추고 단순화한다.
- **제한**: narrative review이며 연구별 수면 조작·운동과제·측정 시점 차이가 크다.

### 09#3. Walsh et al. (2021) — athlete sleep consensus

- **원문**: [PubMed PMID 33144349](https://pubmed.ncbi.nlm.nih.gov/33144349/), DOI `10.1136/bjsports-2020-102025`.
- **상태**: `verified_abstract`.
- **요지**: 엘리트 선수는 7시간 미만 수면에 취약할 수 있고, 급성 수면박탈은 운동수행을 낮출 수 있다. 다만 부분 제한의 결과는 덜 일관되며 “모든 사람에게 7–9시간”만을 적용하기보다 개인의 수면 필요량, 훈련·경기 일정, 낮잠·수면위생 toolbox를 개별화해야 한다.
- **앱 번역**: `individual_sleep_need`, `sleep_opportunity`, `nap`, `sleep_hygiene`, `travel_or_schedule_disruption`을 기록하고, 최소시간 하나로 회복 점수를 결정하지 않는다.
- **제한**: consensus statement이므로 개별 intervention의 pooled effect size가 아니다. 앱은 의료적 수면장애 진단을 하지 않는다.

### 09#4. Hamlin et al. (2021) — 장기 수면·훈련·부상 관찰

- **원문**: [PubMed PMID 34568820](https://pubmed.ncbi.nlm.nih.gov/34568820/), [공개 전문 PMC8461238](https://pmc.ncbi.nlm.nih.gov/articles/PMC8461238/), DOI `10.3389/fspor.2021.705650`.
- **상태**: `verified_full_text`.
- **설계/결과**: 젊은 엘리트 대학선수 82명을 1년 관찰했다. 수면 8시간 이상은 기분·수면의 질·에너지·훈련의 질과 연관됐고, 수면시간 OR `0.8`, 수면의 질 OR `0.6`의 부상·질병 관련 연관이 보고됐다.
- **앱 번역**: `sleep_duration_rolling`, `sleep_quality_rolling`, `energy`, `training_quality`, `injury_or_illness`를 longitudinal trend로 저장한다. 이 자료를 이용해 수면이 부상을 “예방한다”고 단정하지 않고 위험 신호로만 사용한다.
- **제한**: 관찰연구이므로 인과관계를 증명하지 않는다. 자기보고와 특정 젊은 선수 집단의 결과다.

### 09#5. Pearcey et al. (2015) — foam rolling과 DOMS 후 동적 수행

- **원문**: [공개 전문 PMC4299735](https://pmc.ncbi.nlm.nih.gov/articles/PMC4299735/), PMID `25415413`, DOI `10.4085/1062-6050-50.1.01`.
- **상태**: `verified_full_text`.
- **설계/결과**: 신체활동 남성 8명이 10×10 back squat 후 foam rolling 20분을 즉시, 24시간, 48시간에 수행했다. 대조조건보다 압통 역치가 개선됐고, sprint·broad jump·dynamic strength-endurance 회복에 작은~큰 효과가 관찰됐다(Cohen d 대략 `0.48–0.87`).
- **앱 번역**: `doms_trigger`, `foam_roll_minutes`, `post_exercise_hours`, `pain_threshold`, `dynamic_performance`를 분리하고, 사용자가 느끼는 soreness와 실제 strength/performance를 별도 기록한다.
- **제한**: n=8의 교차 설계, 젊은 활동 남성, 강한 squat DOMS protocol이다. 20분 처방을 일반 사용자에게 최적량으로 하드코딩하지 않는다.

### 09#6. Cheatham et al. (2015) — self-myofascial release systematic review

- **원문**: [PubMed PMID 26618062](https://pubmed.ncbi.nlm.nih.gov/26618062/), [공개 전문 PMC4637917](https://pmc.ncbi.nlm.nih.gov/articles/PMC4637917/).
- **상태**: `verified_full_text`.
- **결과**: 14개 논문을 검토했다. foam roll 또는 roller massager는 단기간 ROM을 높이면서 근육수행을 악화시키지 않는 경향이 있었고, 강한 운동 뒤 DOMS와 수행 저하를 완화할 가능성이 있었다. 운동 전 짧은 SMR은 수행을 유의하게 바꾸지 않는 경향이었다.
- **앱 번역**: `recovery_tool = foam_rolling`, `rom`, `doms`, `pre_or_post_session`, `performance_change`를 구조화한다. “근막을 물리적으로 풀었다”는 기전 주장은 evidence field가 아니라 가설로 둔다.
- **제한**: 방법·압력·시간·근육군 이질성이 크고 최적 SMR program의 합의가 없다.

### 09#7. Dupuy et al. (2018) — recovery technique meta-analysis

- **원문**: [PubMed PMID 29755363](https://pubmed.ncbi.nlm.nih.gov/29755363/), [공개 전문 PMC5932411](https://pmc.ncbi.nlm.nih.gov/articles/PMC5932411/), DOI `10.3389/fphys.2018.00403`.
- **상태**: `verified_full_text`.
- **결과**: 99개 연구를 비교했다. active recovery·massage·compression·immersion·contrast water·cryotherapy는 DOMS를 `g −2.26 to −0.40` 범위로 낮추는 결과가 있었고, CK는 SMD `−0.37`(95% CI `−0.58 to −0.16`), IL-6는 `−0.36`, CRP는 `−0.38`이었다. massage는 DOMS·지각 피로에서 가장 강한 신호를 보였지만 비교 기법 간 직접 비교는 제한적이었다.
- **앱 번역**: `recovery_modality`, `target_outcome`, `time_since_exercise`, `doms`, `perceived_fatigue`, `ck`, `il6`, `crp`를 outcome별로 저장한다. biomarker 감소를 곧바로 performance·adaptation 향상으로 변환하지 않는다.
- **제한**: 세션 1회의 단기 회복에 대한 meta-analysis이고, 연구별 운동·회복 protocol·측정 시점이 이질적이다.

### 09#8. Poppendieck et al. (2016) — massage performance recovery

- **원문**: [PubMed PMID 26744335](https://pubmed.ncbi.nlm.nih.gov/26744335/), DOI `10.1007/s40279-015-0420-x`.
- **상태**: `verified_abstract`.
- **결과**: 22개 randomized controlled trial을 검토했다. 짧은 massage(5–12분)의 회복 효과가 더 긴 massage보다 큰 경향이 있었고, 고강도 mixed exercise의 단기 회복에서 중간 수준의 신호가 관찰됐지만 전체 효과는 작고 불명확했다. trained athlete보다 untrained participant에서 이득이 큰 경향도 있었다.
- **앱 번역**: `massage_duration`, `recovery_window_minutes`, `exercise_type`, `training_status`, `performance_outcome`을 입력으로 두고, massage를 성능 향상 보장으로 표현하지 않는다.
- **제한**: 연구 수가 작고 manual·automated massage가 섞였으며, 효과의 조건부 경향을 일반 규칙으로 확대할 수 없다.

### 09#9. White & Wells (2013) — cold-water immersion 생리 review

- **seed 교정**: seed의 2015 표기는 확인 결과 2013년 논문이다. [공개 전문 PMC3766664](https://pmc.ncbi.nlm.nih.gov/articles/PMC3766664/), PMID `24004719`, DOI `10.1186/2046-7648-2-26`.
- **상태**: `metadata_corrected`, `verified_full_text`.
- **요지**: cryotherapy는 조직 온도·혈류·세포 부종·대사·신경전도·심혈관·내분비 반응을 바꾸지만, 이러한 기전 변화가 실제 기능적 회복으로 이어지는지는 불명확하다. CWI protocol의 표준 온도·시간 지침도 확정되지 않았다.
- **앱 번역**: `cooling_modality`, `water_temperature`, `immersion_minutes`, `body_region`, `heat_or_exercise_context`, `next_session_gap`을 저장한다. CWI를 일반 recovery score에 자동 가산하지 않고, 더운 환경·짧은 회복창 등 목적이 명확할 때만 선택지로 노출한다.
- **제한**: physiology/mechanism review이며 장기 적응을 직접 확정하지 않는다. CWI의 급성 체감과 반복 사용의 장기 효과를 분리해야 한다.

### 09#10. Roberts et al. (2015) — CWI와 근력훈련 적응

- **원문**: [PubMed PMID 26174323](https://pubmed.ncbi.nlm.nih.gov/26174323/), [공개 전문 PMC4594298](https://pmc.ncbi.nlm.nih.gov/articles/PMC4594298/), DOI `10.1113/JP270570`.
- **상태**: `verified_full_text`.
- **설계/결과**: Study 1은 신체활동 남성 24명의 12주 하체 근력훈련에서 매 세션 후 10분 CWI와 active recovery를 비교했고, muscle mass·strength 증가가 active recovery에서 더 컸다. Study 2는 9명의 acute strength exercise에서 CWI가 p70S6K 활성과 satellite-cell 반응을 약화·지연시키는 결과를 보였다.
- **앱 번역**: `recovery_goal`을 `next_session_readiness`와 `long_term_adaptation`으로 분리한다. 근비대·근력 목표에서 매 세션 직후 CWI를 기본 recovery rule로 추천하지 않고, 경기 간 회복·열 스트레스 등 별도 목적을 요구한다.
- **제한**: 젊은 활동 남성 중심이며 CWI protocol·active recovery 비교다. 다른 연구와 meta-analysis가 모두 같은 방향을 보인다고 단정하지 않고, 장기 CWI 효과는 근거가 혼재된 상태로 표시한다.

09번의 결론은 회복 개입을 하나의 점수로 합치지 않는 것이다. 수면은 readiness와 인지·수행 조절 입력, foam rolling·massage는 주로 통증·ROM·단기 회복 입력, CWI는 상황별 급성 회복과 장기 적응의 trade-off 입력으로 분리한다.

## 15. 02–09 통합 판정 및 앱 모델 반영 규칙

### 15-1. 이번 배치의 판정

- 02–09 seed 80개를 번호 순서대로 확인했고, 01번과의 중복 source는 같은 논문의 다른 outcome으로 교차 연결했다.
- 공식 PubMed record와 공개 전문을 확인한 항목은 `verified_full_text`, 초록과 공식 metadata를 확인한 항목은 `verified_abstract`, 기존 domain에서 같은 원문을 다시 확인한 항목은 `cross_verified_*`, seed의 서지 오류를 바로잡은 항목은 `metadata_corrected`를 병기했다.
- 현재 유일하게 rule evidence로 보류하는 항목은 `07#10`이다. 제목만으로 정확한 2017 seed fingerprint를 확정하지 못했으므로 대체 review를 참고문헌으로만 연결했다.
- `09#9`는 제목의 연도를 2015에서 2013으로 교정했다. seed를 조용히 덮어쓰지 않고 원문 식별자와 교정 사실을 함께 보존했다.

### 15-2. evidence catalog에 추가해야 할 필드

기존 `rules.jsonl`의 효과 등급을 바로 바꾸지 않고, 아래 source-level record를 먼저 정규화한다.

```json
{
  "evidence_id": "09#10",
  "source_fingerprint": {
    "title": "Post-exercise cold water immersion attenuates acute anabolic signalling and long-term adaptations in muscle to strength training",
    "authors": ["Roberts", "Raastad", "Markworth"],
    "year": 2015,
    "pmid": "26174323",
    "pmcid": "PMC4594298",
    "doi": "10.1113/JP270570"
  },
  "verification": {
    "status": "verified_full_text",
    "checked_on": "2026-08-14",
    "source_url": "https://pubmed.ncbi.nlm.nih.gov/26174323/"
  },
  "study": {
    "design": "randomized_controlled_trial_plus_acute_mechanistic_study",
    "population": "physically_active_young_men",
    "sample_size": {"long_term": 24, "acute": 9},
    "duration": "12_weeks",
    "intervention": "post_exercise_cold_water_immersion",
    "comparator": "active_recovery",
    "outcomes": ["muscle_mass", "strength", "p70S6K", "satellite_cells"]
  },
  "claim": {
    "direction": "attenuation_of_long_term_adaptation_in_this_protocol",
    "effect": "between_group_difference_reported",
    "confidence": "moderate_context_specific",
    "limitations": ["small_sample", "young_men", "specific_protocol", "mixed_literature"]
  },
  "app_translation": {
    "inputs": ["recovery_goal", "next_session_gap", "heat_or_competition_context"],
    "outputs": ["short_term_readiness", "long_term_adaptation_tradeoff"],
    "rule_action": "do_not_default_recommend_after_hypertrophy_session"
  }
}
```

### 15-3. rule engine에 연결할 때 지켜야 할 분리

| evidence layer | 앱에서 허용할 사용 | 앱에서 금지할 사용 |
|---|---|---|
| 수면 부족 | 당일 readiness·기술 복잡도·세션량 조절 | 수면시간 하나로 훈련 가능/불가능 확정 |
| DOMS·통증 | perceived recovery와 실제 수행 기록 분리 | 통증 감소를 조직 회복·근비대 증가로 간주 |
| foam rolling·massage | ROM·통증·단기 수행 회복의 선택 입력 | 모든 근력·스프린트 향상 보장 |
| active recovery·compression·immersion | 세션 사이 회복창과 목표 outcome에 따른 선택지 | biomarker 효과를 performance 효과로 환산 |
| CWI/cryotherapy | 열 스트레스·짧은 회복창 등 맥락별 선택 | 근비대·근력 세션 후 기본값, 또는 장기 적응 저하의 보편 규칙 |
| position stand/consensus | 입력 정의·안전 gate·운영 가이드 | RCT meta-analysis와 같은 효과크기 가중치 |

### 15-4. 다음 구현 순서

1. `evidence/02_*.md`부터 `evidence/09_*.md`의 seed title/author/year/DOI/PMID를 이 문서의 fingerprint로 교정한다.
2. PMID/DOI 기준으로 01–09 중복 source를 deduplicate하고, domain별 `outcome_scope`만 분리한다.
3. `rules.jsonl`의 `evidence_refs`를 `07#10` 제외 상태로 교정한다. 이 단계는 rule 결과가 아니라 식별자와 추적성 수정이다.
4. `verified_abstract` 항목 중 실제 rule weight가 필요한 것만 full text Methods/Results/limitations를 추가 확인한다.
5. 그 후에만 effect direction·certainty·population match를 이용해 rule weight를 재검토한다.

## 16. 검증 실행 로그와 경계

- **실행일**: `2026-08-14` (Asia/Seoul).
- **문헌 경계**: 공식 PubMed metadata/abstract, PubMed Central 공개 전문, DOI 출판사 페이지를 사용했다. 검색 결과의 블로그·뉴스·SNS는 근거 record로 채택하지 않았다.
- **저장소 경계**: 현재 FitnessApp working tree의 `fitness_evidence_rule_engine_v2` 문서만 변경했다. 사용자 작업 중인 Java·Android·Supabase 변경은 수정하지 않았다.
- **검증하지 않은 층**: Android UI, SQLite 저장·조회, Gradle build/test, 실기기, Supabase/RLS, 운영 사용자 데이터는 이번 문헌 검증 범위가 아니다.
- **표현 규칙**: 원문에서 확인한 사실, 저자의 주장, 이 문서의 앱 번역 추론, 아직 계획인 구현을 서로 다른 필드로 분리한다. 평균 효과를 개인 처방으로 표현하지 않는다.
