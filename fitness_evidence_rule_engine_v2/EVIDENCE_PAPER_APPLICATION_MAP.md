# Evidence Paper Application Map

> 작성일: 2026-08-14
> 범위: `fitness_evidence_rule_engine_v2/evidence/`의 9개 분야 문헌 요약, 총 90개 항목
> 목적: 논문 요약을 FitnessApp의 로컬 데이터 모델과 rule engine 입력으로 연결하기 위한 개발용 설계 문서

## 0. 읽기 범위와 신뢰 경계

이 문서는 `README.md`를 먼저 읽고 `evidence/01`부터 `evidence/09`까지 각 항목의 제목, 연도·근거 유형, 핵심 내용, 앱 적용 원칙, 현재 신뢰도를 대조해 작성했다.

현재 폴더에는 논문 원문 PDF나 원문 표가 아니라 분야별 문헌 **요약**이 들어 있다. 따라서 이 파일은 원문 전체 검증본이 아니다. 원문에서 확인하지 않은 효과크기, 95% CI, 이질성, RoB/GRADE, 복용량·운동량의 hard threshold를 새로 만들지 않는다. 아래의 `현재 신뢰도`는 폴더의 seed 문서가 부여한 앱 내부 신뢰도이며 GRADE 점수가 아니다.

앱에서의 기본 원칙은 다음과 같다.

- 단일 논문으로 원인을 확정하거나 강한 처방을 내리지 않는다.
- 메타분석·체계적 문헌고찰·엄브렐러 리뷰·포지션 스탠드를 우선하되, 효과 방향과 근거 확실성을 별도 저장한다.
- 연구 대상과 사용자의 목표·훈련경력·운동 방식·결과변수가 맞는지 먼저 확인한다.
- `unknown`은 0점이 아니라 데이터 부족으로 남긴다.
- 모든 권고는 `rule_id`, `evidence_refs`, 관찰 기간, 결측 여부를 함께 반환한다.
- 통증·급격한 변화·비정상적 피로는 진단 점수가 아닌 `risk_flag`로 표시한다.

## 1. 앱 적용용 evidence 레코드

논문 한 편을 다음 레코드로 정규화한다. 현재는 Markdown seed를 사람이 검토하기 위한 설계이며, 이후 JSON/SQLite seed로 옮길 수 있다.

```json
{
  "evidence_id": "01#1",
  "domain": "hypertrophy",
  "source_file": "evidence/01_근비대_근성장.md",
  "paper_index": 1,
  "title": "The Resistance Training Dose Response...",
  "year": 2026,
  "source_type": "meta_regression",
  "population_fit": ["resistance_trained_or_mixed"],
  "outcomes": ["hypertrophy", "strength"],
  "effect_direction": "positive_or_context_dependent",
  "claim": "주당 볼륨은 근비대의 핵심 부하이고 빈도는 볼륨 배분 변수로 본다.",
  "app_translation": "근육군별 주당 유효 세트와 세션 품질을 추세로 계산한다.",
  "input_fields": ["goal", "weekly_hard_sets_per_muscle", "weekly_frequency_per_muscle"],
  "confidence": "high",
  "limits": ["원문 효과크기·대상별 moderator 미검증"],
  "linked_rules": ["HYP_VOL_001", "HYP_FREQ_002"]
}
```

권장 필드의 의미:

| 필드 | 앱에서의 역할 |
|---|---|
| `evidence_id` | `01#1`처럼 분야와 논문 항목을 안정적으로 참조 |
| `population_fit` | 초보·숙련·선수·고령·과체중 등 적용 가능성 필터 |
| `outcomes` | 근비대·1RM·파워·VO2max·지방량 등을 섞지 않기 위한 결과변수 키 |
| `effect_direction` | `positive`, `negative`, `null`, `context_dependent`; 수치 효과가 없으면 방향만 저장 |
| `input_fields` | 현재 로컬 기록에서 계산해야 하는 원천 입력 |
| `app_translation` | 사용자 행동 후보로 바꾸는 문장. 자동 처방이 아님 |
| `confidence` | `high`, `moderate`, `low`; 내부 충돌 해결용 weight만 결정 |
| `limits` | 대상·기간·측정·근거 유형의 한계와 원문 재검증 필요사항 |
| `linked_rules` | `rules.jsonl`의 seed rule과의 연결 |

## 2. 논문별 정리: 01 근비대·근성장

공통 rule seed: `HYP_VOL_001`, `HYP_FREQ_002`, `HYP_FAIL_003`, `HYP_LOAD_004`.

### 01#1 — The Resistance Training Dose Response: Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and Strength Gains

- **메타데이터:** 2026 / 메타회귀 / 신뢰도 높음
- **핵심 주장:** 주당 세트 볼륨과 빈도의 용량-반응을 종합하며, 볼륨은 특히 근비대와 밀접하고 빈도는 볼륨 배분 수단으로 해석한다.
- **앱 변환:** 근육군별 `weekly_hard_sets`, `weekly_frequency`, 수행 추세를 분리 집계한다. 빈도만으로 성장 점수를 올리지 않는다.
- **제한:** 원문 표본·효과크기·대상별 차이는 별도 검증이 필요하다.

### 01#2 — Resistance Training Recommendations to Maximize Muscle Hypertrophy in an Athletic Population: Position Stand of the IUSCA

- **메타데이터:** 2021 / 포지션 스탠드 / 신뢰도 중간~높음
- **핵심 주장:** 근비대의 부하·볼륨·빈도·휴식·운동 선택·ROM·세트 구성을 실무적으로 통합한다.
- **앱 변환:** 근비대 rule catalog의 상위 설명과 입력 필드 후보를 정하는 참고문헌으로 사용한다.
- **제한:** 포지션 스탠드는 메타분석과 동일한 인과 근거로 점수화하지 않는다.

### 01#3 — Effects of Resistance Training Performed to Repetition Failure or Non-Failure on Muscular Strength and Hypertrophy: A Systematic Review and Meta-Analysis

- **메타데이터:** 2021 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 매 세트 실패가 근비대에 필수라는 일관된 우월성은 확인되지 않으며 실패의 피로 비용을 함께 봐야 한다.
- **앱 변환:** `failure_sets_ratio`, RIR, 세션 후 피로를 사용해 RIR 0 강제를 피하는 조건부 안내를 만든다.
- **제한:** 실패 정의와 운동·대상별 차이를 원문에서 확인하기 전 hard threshold를 만들지 않는다.

### 01#4 — Muscle Hypertrophy and Strength Gains after Resistance Training with Different Volume-Matched Loads: A Systematic Review and Meta-Analysis

- **메타데이터:** 2021 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 충분한 노력과 볼륨이 있으면 근비대는 넓은 부하 범위에서 가능하지만 최대근력은 고중량에 더 특이적이다.
- **앱 변환:** 근비대 평가에서 반복수 하나를 정답으로 고정하지 않고 `load_pct_1rm`, RIR, 총 유효 세트를 함께 본다.
- **제한:** 사용자의 운동 기술·운동 선택·세트 질을 기록하지 않으면 적용성이 낮다.

### 01#5 — Resistance Training Load Effects on Muscle Hypertrophy and Strength Gain: Systematic Review and Network Meta-analysis

- **메타데이터:** 2023 / 네트워크 메타분석 / 신뢰도 높음
- **핵심 주장:** 저·중·고부하의 최적화 조건은 근비대와 근력에서 완전히 같지 않다.
- **앱 변환:** `goal`에 따라 같은 세트 기록의 `stimulus_score` 가중치를 달리한다.
- **제한:** 네트워크 비교의 직접·간접 비교 구조와 대상 특성을 원문에서 확인한다.

### 01#6 — Dose-response relationship between weekly resistance training volume and increases in muscle mass: A systematic review and meta-analysis

- **메타데이터:** 2017 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 주당 저항운동 세트 증가와 근육량 증가의 용량-반응 관계를 대표적으로 제시한다.
- **앱 변환:** 근육군별 주당 유효 세트를 핵심 추세 feature로 만들되 회복·수행 저하를 함께 평가한다.
- **제한:** 평균 관계를 개인의 최소·최대 세트 기준으로 변환하지 않는다.

### 01#7 — Effects of Resistance Training Frequency on Measures of Muscle Hypertrophy: A Systematic Review and Meta-Analysis

- **메타데이터:** 2019 / 체계적 문헌고찰·메타분석 / 신뢰도 중간
- **핵심 주장:** 총 볼륨을 통제하면 빈도의 독립 효과는 제한적이다.
- **앱 변환:** 빈도는 볼륨 배분·세션 품질·회복 간격 feature로 사용하고 독립 성장 보너스로 사용하지 않는다.
- **제한:** 근육군·운동별 분배와 연구 기간을 원문에서 확인한다.

### 01#8 — Effects of Range of Motion on Muscle Development During Resistance Training Interventions: A Systematic Review

- **메타데이터:** 2021 / 체계적 문헌고찰 / 신뢰도 중간
- **핵심 주장:** 완전 ROM은 일반적 기본값이지만 근육 길이와 운동별 유효 ROM이 중요하다.
- **앱 변환:** `rom_quality`를 단순 깊이 점수로 만들지 말고 운동별 기록·통증·가동 범위의 결측을 구분한다.
- **제한:** 영상·센서 없이 ROM을 정밀 판정하지 않는다.

### 01#9 — Effects of Inter-Set Rest Interval Duration on Muscle Hypertrophy and Strength: A Systematic Review

- **메타데이터:** 2017 / 체계적 문헌고찰 / 신뢰도 중간
- **핵심 주장:** 지나치게 짧은 휴식은 후속 세트의 반복·볼륨과 수행을 제한할 수 있다.
- **앱 변환:** `rest_sec`와 세트 간 `rep_dropoff`를 함께 저장하여 휴식 증가를 행동 후보로 제시한다.
- **제한:** 휴식시간 자체를 독립적인 근성장 점수나 절대 기준으로 취급하지 않는다.

### 01#10 — The Effects of Creatine Supplementation Combined with Resistance Training on Regional Measures of Muscle Hypertrophy: A Systematic Review with Meta-Analysis

- **메타데이터:** 2023 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 크레아틴 병행의 직접 근비대 추가 효과는 전체적으로 작지만 긍정적으로 보고된다.
- **앱 변환:** 크레아틴은 훈련·에너지·단백질 이후의 보조 feature로 두고, 체성분 측정의 수분 변화 가능성을 설명한다.
- **제한:** 작은 평균 효과를 개인 보장으로 표현하지 않는다.

## 3. 논문별 정리: 02 근력·최대근력

공통 rule seed: `STR_SPEC_001`, `STR_FAIL_002`, `STR_REST_003`.

### 02#1 — The Resistance Training Dose Response: Meta-Regressions Exploring the Effects of Weekly Volume and Frequency on Muscle Hypertrophy and Strength Gains

- **메타데이터:** 2026 / 메타회귀 / 신뢰도 높음
- **핵심 주장:** 근력에서는 근비대보다 빈도와 기술 연습의 의미가 더 커질 수 있다.
- **앱 변환:** 리프트별 `exposure_frequency`, 고중량 노출, e1RM 추세를 별도 계산한다.
- **제한:** 빈도 효과가 볼륨·기술·운동 선택과 분리되는지 원문 검증이 필요하다.

### 02#2 — Resistance Training Load Effects on Muscle Hypertrophy and Strength Gain: Systematic Review and Network Meta-analysis

- **메타데이터:** 2023 / 네트워크 메타분석 / 신뢰도 높음
- **핵심 주장:** 최대근력 향상은 상대적으로 고부하 훈련이 유리하다.
- **앱 변환:** `goal=max_strength`일 때 고강도 특이적 연습을 `stimulus_score`에 반영한다.
- **제한:** 고부하 권고보다 기술·통증·안전 gate를 먼저 평가한다.

### 02#3 — Effects of Resistance Training Performed to Repetition Failure or Non-Failure on Muscular Strength and Hypertrophy: A Systematic Review and Meta-Analysis

- **메타데이터:** 2021 / 메타분석 / 신뢰도 높음
- **핵심 주장:** 실패하지 않는 훈련도 근력 적응을 만들 수 있다.
- **앱 변환:** 높은 `failure_sets_ratio`에는 실패를 늘리라는 대신 기술 품질과 회복을 점검하는 설명을 낸다.
- **제한:** 통증·부상 위험은 이 논문만으로 판단하지 않는다.

### 02#4 — Effect of Resistance Training Frequency on Gains in Muscular Strength: A Systematic Review and Meta-Analysis

- **메타데이터:** 2018 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 총 볼륨을 분리하면 빈도의 효과가 줄어든다.
- **앱 변환:** 빈도를 볼륨 배분·리프트 숙련·세션 회복 변수로 기록한다.
- **제한:** 단순 주당 횟수만으로 개인의 최적 빈도를 결정하지 않는다.

### 02#5 — Effects of Resistance Training Frequency on Measures of Muscle Hypertrophy: A Systematic Review and Meta-Analysis

- **메타데이터:** 2019 / 메타분석 / 신뢰도 중간
- **핵심 주장:** 빈도와 근비대의 관계는 근력 목표에 그대로 복사할 수 없다.
- **앱 변환:** `goal`별 rule profile을 분리하여 근비대와 1RM의 빈도 해석을 다르게 한다.
- **제한:** 교차 근거이며 최대근력의 직접 근거로 과대해석하지 않는다.

### 02#6 — Muscle Hypertrophy and Strength Gains after Resistance Training with Different Volume-Matched Loads: A Systematic Review and Meta-Analysis

- **메타데이터:** 2021 / 메타분석 / 신뢰도 높음
- **핵심 주장:** 볼륨이 같아도 최대근력은 고부하 특이성이 뚜렷하다.
- **앱 변환:** `heavy_exposure`와 고중량 세트의 품질·e1RM 추세를 함께 본다.
- **제한:** 1RM 측정 경험과 운동 기술의 영향을 분리한다.

### 02#7 — Autoregulation in Resistance Training: Addressing the Inconsistencies

- **메타데이터:** 2021 / 리뷰 / 신뢰도 중간
- **핵심 주장:** RPE/RIR·속도 기반 자기조절의 개념과 적용 불일치를 정리한다.
- **앱 변환:** 고정 중량 추천 대신 일일 RPE/RIR와 최근 수행을 이용한 조건부 부하 조절 skeleton을 둔다.
- **제한:** 자기보고 RPE/RIR의 측정오차와 학습효과를 표시한다.

### 02#8 — Velocity-Based Training: From Theory to Application

- **메타데이터:** 2017 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 바벨 속도로 상대강도와 피로를 추정하고 부하를 조절할 수 있다.
- **앱 변환:** 센서가 있을 때만 `mean_velocity`, `velocity_loss`를 optional advanced input으로 활성화한다.
- **제한:** 센서가 없으면 값을 추정해 채우지 않는다.

### 02#9 — Effects of Inter-Set Rest Interval Duration on Muscle Hypertrophy and Strength: A Systematic Review

- **메타데이터:** 2017 / 체계적 문헌고찰 / 신뢰도 중간
- **핵심 주장:** 고강도 세트 품질을 유지하려면 충분한 휴식이 유리하다.
- **앱 변환:** 복합운동의 `compound_rest_sec`와 `rep_dropoff`를 연결해 휴식 증가 후보를 제시한다.
- **제한:** `<120 sec`는 현재 seed rule의 보수적 조건일 뿐 보편적 생리 임계값이 아니다.

### 02#10 — American College of Sports Medicine position stand. Progression models in resistance training for healthy adults

- **메타데이터:** 2009 / ACSM 포지션 스탠드 / 신뢰도 중간
- **핵심 주장:** 초보부터 숙련까지 부하·빈도·볼륨·진행 방식의 고전적 체계를 제시한다.
- **앱 변환:** `training_age_months`에 따른 설명·진행 모델의 기본 참고로 사용한다.
- **제한:** 최신 메타분석과 결합하고 단독 hard rule로 고정하지 않는다.

## 4. 논문별 정리: 03 체지방 감소·다이어트

공통 rule seed: `FAT_RT_001`, `FAT_DEF_002`, `FAT_RATE_003`.

### 03#1 — Effect of resistance exercise on body composition, muscle strength and cardiometabolic health during dietary weight loss in people living with overweight or obesity: a systematic review and meta-analysis

- **메타데이터:** 2025 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 감량에 저항운동을 추가하면 체중량보다 제지방 보존·지방량·근력에 이점이 있다.
- **앱 변환:** 감량 KPI를 체중 하나로 두지 않고 `fat_mass`, 허리, 근력, 제지방 추세로 분리한다.
- **제한:** 과체중·비만 대상 결과를 일반 사용자에게 그대로 일반화하지 않는다.

### 03#2 — Comparing exercise modalities during caloric restriction: a systematic review and network meta-analysis on body composition

- **메타데이터:** 2025 / 네트워크 메타분석 / 신뢰도 높음
- **핵심 주장:** 칼로리 제한 중 운동 양식별 체중·지방량·체지방률·제지방 결과가 다를 수 있다.
- **앱 변환:** `energy_deficit`와 운동 양식을 별도 기록하고 제지방 보존을 KPI로 둔다.
- **제한:** 네트워크 순위를 개인 처방 순위로 사용하지 않는다.

### 03#3 — Resistance training effectiveness on body composition and body weight outcomes in individuals with overweight and obesity across the lifespan: A systematic review and meta-analysis

- **메타데이터:** 2022 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 과체중·비만군에서 저항운동 단독 및 식이 제한과 조합이 체성분에 유효하다.
- **앱 변환:** 감량 중 저항운동 부재를 `FAT_RT_001` 후보로 표시한다.
- **제한:** 연령대·기초 체력·의학적 상태의 차이를 분리해야 한다.

### 03#4 — Energy deficiency impairs resistance training gains in lean mass but not strength: A meta-analysis and meta-regression

- **메타데이터:** 2022 / 메타분석·메타회귀 / 신뢰도 높음
- **핵심 주장:** 에너지 부족은 제지방 증가를 억제하지만 근력은 상대적으로 보존될 수 있다.
- **앱 변환:** 큰 `energy_deficit`에서 근육 증가 정체를 훈련 실패로 확정하지 않고 영양·회복을 함께 검사한다.
- **제한:** 적자 계산의 오차와 기간을 저장한다.

### 03#5 — Time-restricted eating shows a modest reduction in fat mass in resistance-trained individuals: a systematic review and meta-analysis

- **메타데이터:** 2026 / 체계적 문헌고찰·메타분석 / 신뢰도 중간
- **핵심 주장:** 저항훈련자에서 시간제한 식사의 지방량 감소 이점은 소폭이다.
- **앱 변환:** TRE를 에너지 균형을 대체하는 규칙이 아니라 식사 구조·순응도 선택지로 저장한다.
- **제한:** 최신 항목이므로 원문과 연구기간을 우선 재검증한다.

### 03#6 — Slow versus fast weight loss: effects on body composition and strength and power-related performance in elite athletes

- **메타데이터:** 2011 / 무작위 중재연구 / 신뢰도 중간
- **핵심 주장:** 엘리트 선수에서 완만한 감량이 제지방과 수행 보존에 유리할 가능성을 제시한다.
- **앱 변환:** `weekly_weight_change_pct`를 추세로 계산하고 빠른 감량에는 속도 조절 후보를 낸다.
- **제한:** 엘리트 운동선수의 결과를 일반 사용자 임계값으로 복사하지 않는다.

### 03#7 — Achieving an Optimal Fat Loss Phase in Resistance-Trained Athletes: A Narrative Review

- **메타데이터:** 2021 / 내러티브 리뷰 / 신뢰도 중간
- **핵심 주장:** 감량기 적자·단백질·훈련·속도·식사 전략을 실무적으로 통합한다.
- **앱 변환:** 감량 모드의 설명과 점검 순서를 구성하는 참고로 사용한다.
- **제한:** 실무 제안은 메타분석·RCT와 합의될 때만 강한 rule로 승격한다.

### 03#8 — Evidence-based recommendations for natural bodybuilding contest preparation: nutrition and supplementation

- **메타데이터:** 2014 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 자연 보디빌딩 감량의 에너지·단백질·탄수화물·지방·속도·보충제를 정리한다.
- **앱 변환:** 극단적 대회 준비를 일반 사용자 기본값이 아니라 경고·상한 사례로 사용한다.
- **제한:** 대회 준비 표본의 외적 타당도가 낮을 수 있다.

### 03#9 — Intermittent dieting: theoretical considerations for the athlete

- **메타데이터:** 2017 / 리뷰 / 신뢰도 낮음~중간
- **핵심 주장:** 연속 제한과 간헐 제한·diet break의 이론적 장단점을 다룬다.
- **앱 변환:** diet break는 필수 기능이 아니라 순응도·수행 저하 시 선택 가능한 계획 상태로 둔다.
- **제한:** 이론적 논의만으로 지방 감소 효과를 확정하지 않는다.

### 03#10 — Helms et al. Evidence-based recommendations for natural bodybuilding contest preparation: resistance and cardiovascular training

- **메타데이터:** 2015 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 대회 감량기의 저항운동과 유산소 처방을 함께 통합한다.
- **앱 변환:** 칼로리 소모를 늘리면서 저항운동 품질을 과도하게 희생하는지 점검한다.
- **제한:** 대회 준비 맥락을 일반 건강 앱의 자동 처방으로 일반화하지 않는다.

## 5. 논문별 정리: 04 린매스업·바디 리컴포지션

공통 rule seed: `RECMP_001`, `LEAN_SURPLUS_002`.

### 04#1 — Is an Energy Surplus Required to Maximize Skeletal Muscle Hypertrophy Associated With Resistance Training

- **메타데이터:** 2019 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 근비대에 surplus가 필요한지와 적정 규모를 검토하지만 정확한 최적 surplus는 확정되지 않았다.
- **앱 변환:** `+500 kcal` 같은 고정 처방 대신 체중 증가율·허리·수행 반응으로 섭취를 조절한다.
- **제한:** 내러티브 성격이므로 수치 임계값을 만들지 않는다.

### 04#2 — Energy deficiency impairs resistance training gains in lean mass but not strength: A meta-analysis and meta-regression

- **메타데이터:** 2022 / 메타분석·메타회귀 / 신뢰도 높음
- **핵심 주장:** 적자가 커질수록 제지방 증가 가능성이 낮아지며 근력은 상대적으로 보존될 수 있다.
- **앱 변환:** 리컴포지션 가능성을 체지방·훈련경력·적자 규모·단백질·수행으로 조건부 추정한다.
- **제한:** 리컴포지션 성공을 보장하지 않는다.

### 04#3 — Body Recomposition: Can Trained Individuals Build Muscle and Lose Fat at the Same Time?

- **메타데이터:** 2020 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 훈련 경험자에서도 근육 증가와 지방 감소가 동시에 나타나는 조건과 사례가 있다.
- **앱 변환:** `recomposition`을 불가능/보장 이분법이 아닌 조건부 목표 상태로 표시한다.
- **제한:** 사례와 조건을 평균 효과처럼 사용하지 않는다.

### 04#4 — A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults

- **메타데이터:** 2018 / 체계적 문헌고찰·메타분석·메타회귀 / 신뢰도 높음
- **핵심 주장:** 저항훈련과 단백질 보충의 근육량·근력 효과를 종합하며 총 단백질 섭취가 중요하다.
- **앱 변환:** `protein_g_kg`와 결측·기간을 리컴포지션 준비도 입력으로 둔다.
- **제한:** 평균 포화점은 절대 최소·최대 기준이 아니다.

### 04#5 — The Effect of Creatine Supplementation on Resistance Training-Based Changes to Body Composition: A Systematic Review and Meta-analysis

- **메타데이터:** 2024 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 50세 미만 성인에서 저항훈련+크레아틴의 제지방·지방 지표 변화를 분석한다.
- **앱 변환:** 크레아틴을 보조 가중치로 두고 체성분의 수분 변화 가능성을 설명한다.
- **제한:** 연령 범위 밖의 사용자에게 같은 기대값을 적용하지 않는다.

### 04#6 — Creatine supplementation and resistance training: a comparison between novice and experienced lifters - a systematic review and dose-response meta-analysis

- **메타데이터:** 2025 / 체계적 문헌고찰·용량반응 메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 초보·숙련자의 크레아틴+저항훈련 체성분 효과가 다를 수 있다.
- **앱 변환:** `training_age_months`를 모델 moderator로 저장해 동일 성장곡선을 피한다.
- **제한:** 훈련경력 분류 기준을 원문에서 확인한다.

### 04#7 — Resistance training effectiveness on body composition and body weight outcomes in individuals with overweight and obesity across the lifespan: A systematic review and meta-analysis

- **메타데이터:** 2022 / 메타분석 / 신뢰도 높음
- **핵심 주장:** 체지방이 높은 초보자는 체중 정체 중에도 체성분이 개선될 수 있다.
- **앱 변환:** 체중 추세와 `fat_mass`·허리·근력 추세를 분리하여 리컴포지션 후보를 판정한다.
- **제한:** 체성분 측정 오차를 결과 설명에 포함한다.

### 04#8 — Protein intake and exercise for optimal muscle function with aging: recommendations from the ESPEN Expert Group

- **메타데이터:** 2014 / 전문가 권고 / 신뢰도 중간
- **핵심 주장:** 고령에서 단백질과 운동을 통한 근기능 유지·증진과 연령별 필요량 차이를 강조한다.
- **앱 변환:** `age_group=older_adult`를 별도 moderator로 두고 젊은 성인 기본값을 자동 복사하지 않는다.
- **제한:** 의료·신장 질환 등 개인 상태는 앱이 진단하지 않고 전문가 상담 gate로 분리한다.

### 04#9 — International Society of Sports Nutrition Position Stand: protein and exercise

- **메타데이터:** 2017 / ISSN 포지션 스탠드 / 신뢰도 중간~높음
- **핵심 주장:** 운동인의 총량·1회량·분배·품질·타이밍을 종합한다.
- **앱 변환:** 일일 총 단백질을 1차, 끼니별 분배를 2차 nutrition feature로 계산한다.
- **제한:** 포지션 스탠드 수치를 모든 사용자에게 의무 기준으로 만들지 않는다.

### 04#10 — Nutrition Recommendations for Bodybuilders in the Off-Season: A Narrative Review

- **메타데이터:** 2019 / 내러티브 리뷰 / 신뢰도 중간
- **핵심 주장:** 오프시즌 surplus·단백질·탄수화물·지방·체중 증가 속도를 실무적으로 제안한다.
- **앱 변환:** `lean_gain`의 체중 증가 속도 조절 후보와 설명 순서를 구성한다.
- **제한:** 보디빌더 맥락의 제안이므로 상한·초기값을 원문 검증 없이 고정하지 않는다.

## 6. 논문별 정리: 05 무산소 수행·파워·스프린트

공통 rule seed: `ANA_CRE_001`, `ANA_BA_002`.

### 05#1 — Effects of combined versus single supplementation of creatine and beta-alanine on aerobic and anerobic performance: a systematic review and network meta-analysis

- **메타데이터:** 2026 / 체계적 문헌고찰·네트워크 메타분석 / 신뢰도 높음
- **핵심 주장:** 크레아틴·베타알라닌·병용을 비교하며 크레아틴 효과가 가장 일관적이었다.
- **앱 변환:** 크레아틴은 강한 근거, 베타알라닌은 과제 특이적 보조 입력으로 분리한다.
- **제한:** 네트워크 순위만으로 개인 수행 향상을 보장하지 않는다.

### 05#2 — Creatine supplementation in young men under resistance versus non-resistance training: a systematic review and meta-analysis of strength, performance, and lean mass

- **메타데이터:** 2026 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 젊은 남성에서 크레아틴 효과가 1RM·Wingate·점프 등 과제별로 다를 수 있다.
- **앱 변환:** `performance_outcome`을 1RM·sprint·jump·Wingate로 분리하여 보충제 결과를 기록한다.
- **제한:** 젊은 남성 결과를 성별·연령 전체에 일반화하지 않는다.

### 05#3 — International Society of Sports Nutrition position stand: creatine supplementation and exercise

- **메타데이터:** 2017 / ISSN 포지션 스탠드 / 신뢰도 높음
- **핵심 주장:** 크레아틴의 효능·안전성·로딩·유지 전략과 고강도 수행 근거를 종합한다.
- **앱 변환:** 개인 제한이 없는 경우 크레아틴 모노하이드레이트를 `supplement_candidate`로 표시한다.
- **제한:** 금기·질환·약물 상호작용 판단은 의료 gate로 남긴다.

### 05#4 — International society of sports nutrition position stand: Beta-Alanine

- **메타데이터:** 2015 / ISSN 포지션 스탠드 / 신뢰도 중간~높음
- **핵심 주장:** 근육 카르노신과 수십 초~수분대 고강도 수행에서 베타알라닌의 범위를 정리한다.
- **앱 변환:** `high_intensity_duration_sec`와 반복 고강도 과제일 때만 후보를 활성화한다.
- **제한:** 모든 파워 종목의 기본 보충제로 표시하지 않는다.

### 05#5 — Effects of beta-alanine supplementation on exercise performance: a meta-analysis

- **메타데이터:** 2012 / 메타분석 / 신뢰도 중간
- **핵심 주장:** 베타알라닌 효과가 운동 지속시간에 따라 달라진다.
- **앱 변환:** 짧은 1회 최대파워와 산성화가 큰 반복·지속 고강도 과제를 구분한다.
- **제한:** 운동시간 분류와 효과크기를 원문에서 재확인한다.

### 05#6 — Caffeine ingestion and physical performance: an umbrella review of 21 published meta-analyses

- **메타데이터:** 2020 / 엄브렐러 리뷰 / 신뢰도 높음
- **핵심 주장:** 카페인이 근력·파워·지구력 등 여러 수행 지표에 영향을 줄 수 있다.
- **앱 변환:** `caffeine_dose`, 복용시각, 개인 반응, 수면 손실을 순효과 입력으로 둔다.
- **제한:** 개인 민감도·수면이 이득을 상쇄할 수 있다.

### 05#7 — Caffeine supplementation and physical performance, muscle damage and perception of fatigue in soccer players: a systematic review

- **메타데이터:** 2019 / 체계적 문헌고찰 / 신뢰도 중간
- **핵심 주장:** 간헐적 고강도 스포츠에서 스프린트·점프·피로감 결과를 검토한다.
- **앱 변환:** 팀스포츠형 반복 고강도 결과를 1RM과 다른 `performance_outcome`으로 기록한다.
- **제한:** 축구 선수 연구를 일반 저항운동에 직접 복사하지 않는다.

### 05#8 — Post-activation potentiation versus post-activation performance enhancement in humans: historical perspective, underlying mechanisms, and current issues

- **메타데이터:** 2019 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 고강도 사전 수축 뒤 폭발적 수행 개선과 피로의 시간적 균형을 설명한다.
- **앱 변환:** 워밍업 추천에 `potentiation_exposure`, 회복 간격, 직전 피로를 함께 둔다.
- **제한:** 기전 설명을 장기 적응 효과로 확대하지 않는다.

### 05#9 — Plyometric jump training effects on physical fitness attributes in sport: A systematic review and meta-analysis

- **메타데이터:** 2023 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 플라이오메트릭 훈련이 점프·스프린트·방향전환 등 폭발적 수행에 효과가 있다.
- **앱 변환:** 파워 목표에서 `jump_volume`, `sprint_exposure`, 탄성 훈련 노출을 중량운동과 별도 기록한다.
- **제한:** 착지 기술·통증·부하 회복이 확인되지 않으면 자동 증량하지 않는다.

### 05#10 — Dietary Supplement Strategies During Conditioning Training in Athletes: A Network Meta-Analysis of Peak and Mean Anaerobic Power, VO2max, and Endurance Performance

- **메타데이터:** 2025 / 네트워크 메타분석 / 신뢰도 중간
- **핵심 주장:** 보충제별 peak/mean anaerobic power·VO2max·지구력 효과를 비교한다.
- **앱 변환:** 보충제 종합점수 대신 결과변수별 근거와 불확실성을 반환한다.
- **제한:** 네트워크 순위는 직접 비교·개인 적합성보다 우선하지 않는다.

## 7. 논문별 정리: 06 유산소 수행·심폐지구력

공통 rule seed: `AER_HIIT_001`, `AER_DIST_002`.

### 06#1 — Effects of High-Intensity Interval Training Versus Sprint Interval Training on Factors Related to Endurance Performance: A Systematic Review and Meta-Analysis

- **메타데이터:** 2026 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** HIIT와 SIT는 VO2max·역치·러닝 이코노미·타임트라이얼 결과가 다를 수 있고 장시간 HIIT가 일부 지표에서 우세했다.
- **앱 변환:** `goal=vo2max`와 스프린트 목표를 구분해 인터벌 유형을 추천한다.
- **제한:** 프로토콜·종목·훈련경력 차이를 분리한다.

### 06#2 — Effects of high-intensity interval training on aerobic capacity and athletic performance in trained athletes: a systematic review and meta-analysis

- **메타데이터:** 2026 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 훈련된 선수에서도 HIIT가 유산소 능력·경기 수행에 영향을 줄 수 있다.
- **앱 변환:** 숙련 사용자에게도 HIIT 후보를 열되 종목 특이성과 전체 피로를 확인한다.
- **제한:** 선수 연구의 기대 향상률을 일반 사용자에게 복사하지 않는다.

### 06#3 — VO2max (VO2peak) in elite athletes under high-intensity interval training: A meta-analysis

- **메타데이터:** 2023 / 메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 엘리트 선수의 HIIT와 기존 훈련 간 VO2max/VO2peak 변화를 분석한다.
- **앱 변환:** `training_status`를 moderator로 저장해 엘리트·일반 사용자의 기대값을 분리한다.
- **제한:** 천장효과와 측정오차를 함께 표시한다.

### 06#4 — Effects of moderate-intensity endurance and high-intensity intermittent training on anaerobic capacity and VO2max

- **메타데이터:** 1996 / 중재연구 / 신뢰도 낮음~중간
- **핵심 주장:** 짧은 고강도 인터벌의 유·무산소 적응 가능성을 보인 고전 연구다.
- **앱 변환:** 역사적 근거로만 연결하고 현대 일반 사용자 처방의 단독 근거로 쓰지 않는다.
- **제한:** 작은 표본·단일 연구이며 최신 프로토콜과 다를 수 있다.

### 06#5 — High-intensity interval training solutions to the programming puzzle: Part I: cardiopulmonary emphasis

- **메타데이터:** 2013 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 인터벌 강도·지속·회복·반복 조합으로 심폐 자극을 설계한다.
- **앱 변환:** `work_sec`, `recovery_sec`, 반복 수, 강도·심박 영역을 별도 저장한다.
- **제한:** 리뷰의 프로그래밍 프레임을 효과크기처럼 취급하지 않는다.

### 06#6 — High-intensity interval training solutions to the programming puzzle: Part II: anaerobic energy, neuromuscular load and practical applications

- **메타데이터:** 2013 / 리뷰 / 신뢰도 중간
- **핵심 주장:** HIIT의 심폐 자극이 같아도 무산소 대사·신경근 피로 비용이 다를 수 있다.
- **앱 변환:** 심폐 점수와 `neuromuscular_load`를 분리해 다음 세션 배치를 조절한다.
- **제한:** 신경근 부하는 직접 측정하지 못하면 unknown으로 둔다.

### 06#7 — Polarized training has greater impact on key endurance variables than threshold, high intensity, or high volume training

- **메타데이터:** 2014 / 무작위 중재연구 / 신뢰도 중간
- **핵심 주장:** 강도 분포 전략 중 polarized 접근의 가능성을 보인다.
- **앱 변환:** 장기 `intensity_distribution`을 기록하되 80/20을 절대 규칙으로 고정하지 않는다.
- **제한:** 한 연구의 분포를 모든 종목·기간에 일반화하지 않는다.

### 06#8 — The training intensity distribution among well-trained and elite endurance athletes

- **메타데이터:** 2015 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 숙련 지구력 선수의 저·중·고강도 훈련 분포 근거를 정리한다.
- **앱 변환:** Zone 2 단일 지표 대신 전체 `zone_minutes`와 세션 목적을 본다.
- **제한:** 관찰 분포는 원인·최적 처방을 증명하지 않는다.

### 06#9 — Exercise Training in the Management of Overweight and Obesity in Adults: Synthesis of the Evidence and Recommendations from the European Association for the Study of Obesity Physical Activity Working Group

- **메타데이터:** 2021 / 체계적 근거 종합·권고 / 신뢰도 높음
- **핵심 주장:** 유산소·저항·인터벌이 체중·지방·심폐체력에 미치는 근거를 종합한다.
- **앱 변환:** 체중 감량과 `vo2max`를 서로 다른 KPI로 관리한다.
- **제한:** 건강 상태·운동 금기·초기 체력은 안전 gate에서 별도 확인한다.

### 06#10 — Effects of different protocols of high intensity interval training for VO2max improvements in adults: A meta-analysis of randomised controlled trials

- **메타데이터:** 2019 / 메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 성인의 HIIT 프로토콜별 VO2max 개선을 비교한다.
- **앱 변환:** HIIT 효과를 프로토콜·기간·대상 조건에 연결하여 추천한다.
- **제한:** 평균 효과를 매 세션의 즉시 반응이나 보장된 증가량으로 말하지 않는다.

## 8. 논문별 정리: 07 동시훈련·하이브리드

공통 rule seed: `CON_INT_001`, `CON_POWER_002`.

### 07#1 — Maximizing Adaptations in Concurrent Training: An Umbrella Review of Meta-analyses

- **메타데이터:** 2026 / 엄브렐러 리뷰 / 신뢰도 높음
- **핵심 주장:** 전반적으로 동시훈련이 저항훈련 대비 근력·근비대를 크게 훼손한다는 단순 명제는 지지되지 않는다.
- **앱 변환:** `modality`, 하체 피로, 파워 우선순위, 세션 배치를 조건부 간섭 모델로 평가한다.
- **제한:** 개인의 총부하·회복·목표 특이성을 생략하면 결론이 달라질 수 있다.

### 07#2 — Does Sprint Interval Training Cause Interference in Concurrent Training? A Meta-Analysis Study

- **메타데이터:** 2026 / 메타분석 / 신뢰도 중간~높음
- **핵심 주장:** SIT의 간섭 여부는 일반 지속 유산소와 다른 신경근·심폐 피로 프로파일로 봐야 한다.
- **앱 변환:** `sprint_interval_sessions`, 하체 피로, 파워 추세를 별도 feature로 둔다.
- **제한:** SIT 프로토콜 차이를 통합한 평균을 개인 일정에 직접 적용하지 않는다.

### 07#3 — Concurrent training with long-interval HIIT does not impair skeletal muscle protein synthesis or hypertrophy: little evidence of an interference effect

- **메타데이터:** 2026 / 중재연구 / 신뢰도 중간
- **핵심 주장:** 장시간 인터벌 HIIT 병행이 근단백질 합성·근비대를 저해하는지 직접 조사했으며 간섭 근거가 적다.
- **앱 변환:** 분자 신호가 아닌 장기 `hypertrophy`와 `performance` 결과를 우선 연결한다.
- **제한:** 단기·특정 프로토콜의 직접 연구일 수 있으므로 전체 동시훈련으로 확대하지 않는다.

### 07#4 — Concurrent Strength and Endurance Training: A Systematic Review and Meta-Analysis on the Impact of Sex and Training Status

- **메타데이터:** 2024 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 성별·훈련 상태가 동시훈련의 근력·파워·VO2max 결과를 조절할 수 있다.
- **앱 변환:** `sex`와 `training_status`를 moderator로 저장하고 표본 불균형 불확실성을 표시한다.
- **제한:** 성별을 기계적 처방 기준으로 사용하지 않는다.

### 07#5 — Comparative efficacy of concurrent training types on lower limb strength and muscular hypertrophy: A systematic review and network meta-analysis

- **메타데이터:** 2024 / 네트워크 메타분석 / 신뢰도 높음
- **핵심 주장:** 러닝·사이클·HIIT 등 지구력 방식별 하체 근력·근비대 결과가 다를 수 있다.
- **앱 변환:** `modality`와 하체 피로 예산을 구분한다.
- **제한:** 네트워크 순위를 사용자에게 최적 방식으로 직접 노출하지 않는다.

### 07#6 — Concurrent training: a meta-analysis examining interference of aerobic and resistance exercises

- **메타데이터:** 2012 / 메타분석 / 신뢰도 중간
- **핵심 주장:** 러닝·빈도·기간 등이 동시훈련 간섭의 moderator로 논의된다.
- **앱 변환:** 역사적 기준점으로 저장하고 최신 메타분석과 충돌할 때 최신·직접 결과를 우선한다.
- **제한:** 오래된 연구의 장비·프로토콜과 현재 사용자 기록이 다를 수 있다.

### 07#7 — Compatibility of Concurrent Aerobic and Strength Training for Skeletal Muscle Size and Function: An Updated Systematic Review and Meta-Analysis

- **메타데이터:** 2022 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 최대근력·근비대의 전반적 간섭은 제한적이며 폭발적 힘은 더 민감할 수 있다.
- **앱 변환:** 근비대·1RM·파워를 한 점수로 합치지 않고 별도 outcome으로 평가한다.
- **제한:** 파워 outcome의 측정·훈련 배치 차이를 확인한다.

### 07#8 — The compatibility of concurrent high intensity interval training and resistance training for muscular strength and hypertrophy: a systematic review and meta-analysis

- **메타데이터:** 2018 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 상체·근비대는 대체로 보존되지만 하체 근력에서 일부 간섭 가능성이 있다.
- **앱 변환:** 하체 저항운동과 HIIT를 같은 `fatigue_budget`과 배치 규칙으로 관리한다.
- **제한:** 간섭은 세션 간격·순서·총량에 의존할 수 있다.

### 07#9 — Effects of Concurrent Resistance and Endurance Training Using Continuous or Intermittent Protocols on Muscle Hypertrophy: Systematic Review With Meta-Analysis

- **메타데이터:** 2023 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 지속·인터벌 지구력 운동을 포함한 동시훈련의 근비대 결과를 검토한다.
- **앱 변환:** 지구력 방식과 전체 근육·근섬유 등 측정 수준을 구분해 evidence match를 계산한다.
- **제한:** outcome 측정 수준이 서로 다르면 한 지표로 합치지 않는다.

### 07#10 — Concurrent aerobic and strength training for performance in running and cycling endurance athletes: A systematic review and meta-analysis

- **메타데이터:** 2017 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 지구력 선수에서 근력훈련 추가가 러닝·사이클 수행과 경제성에 영향을 줄 수 있다.
- **앱 변환:** 하이브리드 목표에서 `strength_maintenance`뿐 아니라 러닝·사이클 경기 수행을 기록한다.
- **제한:** 종목별 수행 지표를 서로 대체하지 않는다.

## 9. 논문별 정리: 08 영양·보충제

공통 rule seed: `NUT_PRO_001`, `NUT_CAFF_002`, `NUT_CARB_003`.

### 08#1 — A systematic review, meta-analysis and meta-regression of the effect of protein supplementation on resistance training-induced gains in muscle mass and strength in healthy adults

- **메타데이터:** 2018 / 체계적 문헌고찰·메타분석·메타회귀 / 신뢰도 높음
- **핵심 주장:** 총 단백질 섭취가 핵심이며 약 1.6 g/kg/day 부근 이후 평균 추가 이득이 작아지는 결과로 알려져 있다.
- **앱 변환:** `protein_g_kg`를 충분도 구간으로 평가하고 1.6을 개인 절대 최소·최대값으로 쓰지 않는다.
- **제한:** 원문 효과크기·섭취 범위·대상별 차이를 재검증한다.

### 08#2 — International Society of Sports Nutrition Position Stand: protein and exercise

- **메타데이터:** 2017 / ISSN 포지션 스탠드 / 신뢰도 중간~높음
- **핵심 주장:** 총량·1회량·분배·품질·타이밍을 종합한다.
- **앱 변환:** 일일 총량을 1차 지표, 식사별 분배를 2차 최적화 지표로 저장한다.
- **제한:** 포지션 스탠드 범위를 사용자의 의료·식이 조건에 자동 적용하지 않는다.

### 08#3 — International Society of Sports Nutrition position stand: creatine supplementation and exercise

- **메타데이터:** 2017 / ISSN 포지션 스탠드 / 신뢰도 높음
- **핵심 주장:** 크레아틴의 수행·근육 효과·안전성·로딩·유지 섭취를 정리한다.
- **앱 변환:** 크레아틴을 evidence-backed 후보로 표시하되 개인 제한 입력이 없으면 권고를 보류한다.
- **제한:** 의료 안전성을 rule engine이 판정하지 않는다.

### 08#4 — The Effects of Creatine Supplementation Combined with Resistance Training on Regional Measures of Muscle Hypertrophy: A Systematic Review with Meta-Analysis

- **메타데이터:** 2023 / 체계적 문헌고찰·메타분석 / 신뢰도 중간~높음
- **핵심 주장:** 크레아틴의 직접 근비대 추가 효과는 작을 수 있다.
- **앱 변환:** `supplement_effect`를 작은 보조 효과로 저장하고 훈련·에너지·단백질보다 우선하지 않는다.
- **제한:** 평균 작은 효과를 사용자별 확정 효과로 표시하지 않는다.

### 08#5 — Caffeine ingestion and physical performance: an umbrella review of 21 published meta-analyses

- **메타데이터:** 2020 / 엄브렐러 리뷰 / 신뢰도 높음
- **핵심 주장:** 카페인의 운동 수행 효과를 상위 수준에서 종합한다.
- **앱 변환:** 복용량뿐 아니라 반응·시각·수면 손실을 순효과에 포함한다.
- **제한:** 수면 저하와 민감도 때문에 개인 순효과가 음수가 될 수 있다.

### 08#6 — International society of sports nutrition position stand: caffeine and exercise performance

- **메타데이터:** 2021 / ISSN 포지션 스탠드 / 신뢰도 높음
- **핵심 주장:** 용량·타이밍·운동 유형·개인차를 정리한다.
- **앱 변환:** `caffeine_time`, `sleep_duration_h`, 민감도와 수행 로그를 함께 평가한다.
- **제한:** 늦은 섭취의 수면 영향이 우선 안전·회복 신호가 될 수 있다.

### 08#7 — International society of sports nutrition position stand: Beta-Alanine

- **메타데이터:** 2015 / ISSN 포지션 스탠드 / 신뢰도 중간~높음
- **핵심 주장:** 카르노신 증가·섭취 전략·고강도 수행 효과를 정리한다.
- **앱 변환:** 운동 지속시간·종목이 맞는 경우에만 보충제 후보를 활성화한다.
- **제한:** 따끔거림 등 부작용과 개인 제한을 별도 확인한다.

### 08#8 — Carbohydrates for training and competition

- **메타데이터:** 2011 / IOC·스포츠영양 리뷰 / 신뢰도 높음
- **핵심 주장:** 운동 전·중·후 탄수화물 가용성과 지구력 수행 전략을 다룬다.
- **앱 변환:** `carb_g_kg`, 세션 강도·시간·주간 훈련량에 따라 탄수화물을 periodize한다.
- **제한:** 고정 g/kg 하나를 모든 세션에 적용하지 않는다.

### 08#9 — Nutrition and Athletic Performance

- **메타데이터:** 2016 / Academy·ACSM·Dietitians of Canada 포지션 페이퍼 / 신뢰도 높음
- **핵심 주장:** 에너지·탄수화물·단백질·지방·수분·경기 전후 영양을 폭넓게 다룬다.
- **앱 변환:** 영양 rule의 상위 분류와 누락 데이터 목록을 정한다.
- **제한:** 세부 수치는 최신 메타분석과 대상 특이성으로 갱신한다.

### 08#10 — Dietary Supplement Strategies During Conditioning Training in Athletes: A Network Meta-Analysis of Peak and Mean Anaerobic Power, VO2max, and Endurance Performance

- **메타데이터:** 2025 / 네트워크 메타분석 / 신뢰도 중간
- **핵심 주장:** 여러 보충제를 peak/mean anaerobic power·VO2max·지구력 결과별로 비교한다.
- **앱 변환:** 보충제 하나의 종합점수가 아니라 outcome별 evidence card를 반환한다.
- **제한:** 네트워크 순위와 사용자 적합성·안전성을 분리한다.

## 10. 논문별 정리: 09 회복·자기관리

공통 rule seed: `REC_SLEEP_001`, `REC_FOAM_002`, `REC_COLD_003`.

### 09#1 — Effects of Acute Sleep Deprivation on Sporting Performance in Athletes: A Comprehensive Systematic Review and Meta-Analysis

- **메타데이터:** 2024 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 급성 수면박탈이 다양한 운동 수행 지표에 영향을 줄 수 있다.
- **앱 변환:** 수면 부족일 `readiness`를 낮추고 RPE 상승·세션 목표 조절 후보를 제시한다.
- **제한:** 하루 수면값만으로 훈련 취소·질환 판정을 자동화하지 않는다.

### 09#2 — Sleep and Athletic Performance: The Effects of Sleep Loss on Exercise Performance, and Physiological and Cognitive Responses to Exercise

- **메타데이터:** 2015 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 수면 손실은 운동 수행·인지·생리 반응에 영향을 줄 수 있다.
- **앱 변환:** 수면을 웰니스 점수가 아니라 수행 준비도 입력으로 모델링한다.
- **제한:** 리뷰의 기전 설명을 개인의 원인 진단으로 사용하지 않는다.

### 09#3 — Sleep and the athlete: narrative review and 2021 expert consensus recommendations

- **메타데이터:** 2021 / 전문가 합의문 / 신뢰도 중간~높음
- **핵심 주장:** 수면시간 외에도 규칙성·질·타이밍·낮잠·여행·경기 일정이 중요하다.
- **앱 변환:** `sleep_duration_h`, `sleep_quality`, `sleep_regularity`, 취침 타이밍을 분리 저장한다.
- **제한:** 합의 권고와 인과 효과크기를 같은 weight로 다루지 않는다.

### 09#4 — The Effect of Sleep Quality and Quantity on Athlete's Health and Perceived Training Quality

- **메타데이터:** 2018 / 관찰연구 / 신뢰도 낮음~중간
- **핵심 주장:** 수면 양·질과 건강·훈련 체감 품질의 연관성을 다룬다.
- **앱 변환:** 개인 내 수면-세션 품질 추세를 표시하되 상관관계로 남긴다.
- **제한:** 관찰연구이므로 원인으로 단정하지 않는다.

### 09#5 — Foam Rolling for Delayed-Onset Muscle Soreness and Recovery of Dynamic Performance Measures

- **메타데이터:** 2015 / 무작위 중재연구 / 신뢰도 중간
- **핵심 주장:** 폼롤링이 DOMS와 운동 후 동적 수행 회복에 영향을 줄 수 있다.
- **앱 변환:** `foam_rolling`을 근성장 점수가 아닌 DOMS·ROM·주관적 회복 보조 feature로 기록한다.
- **제한:** 효과가 핵심 훈련을 대체하거나 근성장을 보장하지 않는다.

### 09#6 — Effects of Self-Myofascial Release Using a Foam Roller on Range of Motion and Performance: A Systematic Review

- **메타데이터:** 2015 / 체계적 문헌고찰 / 신뢰도 중간
- **핵심 주장:** 폼롤링의 급성 ROM·수행·회복 효과를 정리한다.
- **앱 변환:** 워밍업에서 ROM 개선 선택지로 제공하되 핵심 훈련과 분리한다.
- **제한:** 일시적 ROM 개선을 장기 적응으로 표현하지 않는다.

### 09#7 — An Evidence-Based Approach for Choosing Post-exercise Recovery Techniques to Reduce Markers of Muscle Damage, Soreness, Fatigue, and Inflammation: A Systematic Review With Meta-Analysis

- **메타데이터:** 2018 / 체계적 문헌고찰·메타분석 / 신뢰도 높음
- **핵심 주장:** 마사지·냉수욕 등 회복 기법의 DOMS·피로·염증 지표 효과를 비교한다.
- **앱 변환:** `perceived_recovery`와 `long_term_adaptation`을 별도 outcome으로 저장한다.
- **제한:** 급성 marker 개선과 장기 적응 개선을 동일하게 취급하지 않는다.

### 09#8 — Massage and Performance Recovery: A Meta-Analytical Review

- **메타데이터:** 2020 / 메타분석 / 신뢰도 높음
- **핵심 주장:** 마사지의 직접 수행 회복 효과는 제한적일 수 있으나 통증·유연성 이점이 있을 수 있다.
- **앱 변환:** 마사지 기록은 퍼포먼스 향상보다 DOMS·유연성·회복감 feature에 연결한다.
- **제한:** 주관적 회복감과 객관적 수행을 같은 점수로 합치지 않는다.

### 09#9 — Cold Water Immersion and Other Forms of Cryotherapy: Physiological Changes Potentially Affecting Recovery from High-Intensity Exercise

- **메타데이터:** 2015 / 리뷰 / 신뢰도 중간
- **핵심 주장:** 냉수욕의 회복 기전과 잠재적 효과를 정리한다.
- **앱 변환:** 경기 사이 급속 회복과 근비대 훈련 후 반복 사용을 서로 다른 context로 저장한다.
- **제한:** 기전·급성 회복 효과만으로 장기 적응을 판단하지 않는다.

### 09#10 — Post-exercise cold water immersion attenuates acute anabolic signalling and long-term adaptations in muscle to strength training

- **메타데이터:** 2015 / 중재연구 / 신뢰도 중간
- **핵심 주장:** 저항훈련 직후 반복 냉수욕이 동화 신호와 장기 근육 적응을 약화시킬 가능성을 보인다.
- **앱 변환:** `goal=hypertrophy`에서 `cold_water_immediately_post_rt=true`이면 기본 회복법 추천을 낮추고 상황별 설명을 낸다.
- **제한:** 특정 연구 프로토콜의 결과이며 모든 냉수욕·모든 사용자에 대한 금지 규칙이 아니다.

## 11. FitnessApp에 연결하는 최소 상태 모델

현재 앱의 로컬 소유권과 구현 방향(Java 17, Android View, `SQLiteOpenHelper`)을 유지한다. 기존 운동·식사 기록 테이블을 재작성하지 않고, 먼저 읽기 전용 adapter와 파생 feature 계산을 추가하는 것이 최소 단위다.

### 11.1 원천 입력 모델

```json
{
  "user_state": {
    "goal": "hypertrophy",
    "age_group": "adult",
    "training_age_months": 24,
    "body_weight_kg": 89.0,
    "weekly_weight_change_pct": 0.0,
    "energy_balance_status": "unknown",
    "protein_g_kg": 1.25,
    "carb_g_kg": null
  },
  "training_state": {
    "window_start": "2026-08-08",
    "window_end": "2026-08-14",
    "weekly_hard_sets_per_muscle": {"chest": 8},
    "weekly_frequency_per_muscle": {"chest": 2},
    "failure_sets_ratio": 0.25,
    "heavy_exposure": "unknown",
    "compound_rest_sec": null,
    "rep_dropoff": "unknown",
    "hiit_sessions_per_week": 0,
    "intensity_distribution": "unknown",
    "power_priority": false
  },
  "recovery_state": {
    "sleep_duration_h": 6.1,
    "sleep_quality": null,
    "sleep_regularity": null,
    "fatigue_score": null,
    "pain_flag": false
  },
  "supplement_state": {
    "creatine_use": false,
    "caffeine_use": true,
    "beta_alanine_use": false,
    "caffeine_time": null,
    "sleep_cost_observed": null
  },
  "outcome_state": {
    "e1rm_4wk_pct": null,
    "body_weight_4wk_pct": null,
    "waist_4wk_pct": null,
    "fat_mass_4wk_pct": null,
    "vo2max_4wk_pct": null,
    "data_completeness": 0.0
  }
}
```

### 11.2 기존 테이블과 파생 feature의 매핑

| evidence 입력 | 현재 로컬 원천 | 파생 feature |
|---|---|---|
| 운동 날짜·세션 | `workout_records`, `workout_exercises`, `workout_sets` | 주간 세션 수, 근육군별 유효 세트, 반복·부하 추세, e1RM |
| 유산소 | `cardio_sessions`, `cardio_route_points` | 유산소 시간, 인터벌 수, 강도 분포, 하체 피로 후보 |
| 식사·탄단지 | `meal_records`, `meal_record_items`, `meal_record_item_nutrients` | 일일 kcal·단백질·탄수화물·지방, 단백질 g/kg, 기록 완전성 |
| 수면·수분·주관 기록 | `nutrition_daily_checkins` 및 기존 check-in 경로 | 수면 평균·최저, 질·규칙성, readiness 입력 |
| 체중·체성분 | `body_profiles` 및 기존 체중 기록 경로 | 7일/28일 체중·허리·체지방 추세 |
| 보충제·회복 행동 | 신규 local log 또는 기존 check-in 확장 | 크레아틴·카페인·폼롤링·냉수욕 context |

`nutrition_foods`와 PriceTrace 원격 제품 경로는 이 rule engine의 evidence 원천이 아니다. 영양 계산에 사용된 음식의 출처·결측·검증 상태만 local snapshot으로 남기고, 원격 카탈로그 연결 여부를 운동·회복 근거로 추론하지 않는다.

### 11.3 새로 필요한 최소 저장 구조

기존 기록을 변경하기 전에 다음 3개만 별도 추가하는 것을 권장한다.

```sql
CREATE TABLE evidence_rule_catalog (
    rule_id TEXT PRIMARY KEY,
    domain TEXT NOT NULL,
    question TEXT NOT NULL,
    condition_json TEXT NOT NULL,
    recommendation_ko TEXT NOT NULL,
    confidence TEXT NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    hard_gate INTEGER NOT NULL DEFAULT 0,
    medical_rule INTEGER NOT NULL DEFAULT 0,
    last_reviewed TEXT NOT NULL
);

CREATE TABLE fitness_trend_features (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    window_start TEXT NOT NULL,
    window_end TEXT NOT NULL,
    feature_json TEXT NOT NULL,
    completeness REAL,
    created_at TEXT NOT NULL
);

CREATE TABLE evidence_rule_evaluations (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    evaluated_at TEXT NOT NULL,
    window_start TEXT NOT NULL,
    window_end TEXT NOT NULL,
    rule_id TEXT NOT NULL,
    status TEXT NOT NULL,
    score REAL,
    missing_fields_json TEXT NOT NULL,
    evidence_refs_json TEXT NOT NULL,
    explanation_json TEXT NOT NULL,
    FOREIGN KEY(rule_id) REFERENCES evidence_rule_catalog(rule_id)
);
```

`status`는 최소 `matched`, `not_matched`, `unknown`, `blocked_by_safety`로 둔다. 결측이면 `not_matched`가 아니라 `unknown`이다.

### 11.4 Java 뼈대

기존 코드와 같은 Java/Android View 구조에서 순수 Java 모델을 먼저 만든다. rule evaluation은 UI나 SQLite cursor에 직접 묶지 않는다.

```java
public final class FitnessEvidenceInput {
    public final UserState user;
    public final TrainingState training;
    public final RecoveryState recovery;
    public final NutritionState nutrition;
    public final OutcomeState outcomes;
    public final DateRange window;

    // Constructor only; null means unknown, not zero.
}

public final class EvidenceRuleEvaluation {
    public enum Status { MATCHED, NOT_MATCHED, UNKNOWN, BLOCKED_BY_SAFETY }

    public final String ruleId;
    public final Status status;
    public final Double score;
    public final List<String> evidenceRefs;
    public final List<String> missingFields;
    public final String observation;
    public final String conditionalInterpretation;
    public final List<String> actionCandidates;
    public final String uncertainty;
}

public interface EvidenceRuleRepository {
    List<EvidenceRule> loadRules();
    void saveEvaluation(EvidenceRuleEvaluation evaluation);
}

public final class EvidenceRuleEngine {
    public List<EvidenceRuleEvaluation> evaluate(FitnessEvidenceInput input,
                                                  List<EvidenceRule> rules) {
        // 1. safety/pain gate
        // 2. validate goal and population fit
        // 3. evaluate known conditions only
        // 4. preserve UNKNOWN for missing inputs
        // 5. attach evidence refs and observation window
        // 6. render observation -> evidence -> conditional meaning -> action -> uncertainty
        return Collections.emptyList();
    }
}
```

## 12. 점수와 설명의 구현 순서

`raw logs → normalized metrics → trend features → goal context → rule matching → evidence weighting → explanation` 순서를 지킨다.

### 12.1 첫 구현에서 계산할 것

1. `weekly_hard_sets_per_muscle`, `weekly_frequency_per_muscle`
2. `failure_sets_ratio`, `rep_dropoff`, `heavy_exposure`
3. `protein_g_kg`, `weekly_weight_change_pct`
4. `sleep_duration_h`의 7일 평균·최저와 결측률
5. e1RM·체중·허리의 4주 추세
6. 세션과 식사 기록의 실제 관찰 기간

첫 단계에서는 24개 seed rule을 모두 UI 처방으로 노출하지 말고, `matched`/`unknown` 평가와 감사 로그를 먼저 검증한다. 이후 설명 카드로 승격한다.

### 12.2 내부 점수

현재 명세의 weight만 사용한다.

| confidence | 내부 weight |
|---|---:|
| high | 1.00 |
| moderate | 0.70 |
| low | 0.40 |

이 값은 GRADE나 임상 확률이 아니다. 충돌 해결용이다. 충돌 우선순위는 `안전/통증 → 목표 specificity → 직접 outcome → evidence confidence → 최신 고수준 종합근거`다.

### 12.3 설명 템플릿

```text
관찰: {기간 동안 관찰된 입력과 추세}
근거: {evidence_refs와 해당 논문의 결과 방향}
조건부 해석: {현재 목표·대상에 맞을 때의 의미}
행동 후보: {증량·배치·기록 보완 등 되돌릴 수 있는 선택지}
불확실성: {결측·측정오차·대상 차이·원문 재검증 필요사항}
```

원인을 하나로 귀속하지 않는다. 예를 들어 벤치 정체가 있으면 단백질 하나를 원인으로 확정하지 않고, 고중량 특이성·총 볼륨·세션 품질·수면·에너지·통증을 함께 검사한다.

## 13. 다음 구현 단위

1. `rules.jsonl`을 앱 assets에 넣고 `rule.schema.json` 검증 결과를 테스트 fixture로 고정한다.
2. `FitnessEvidenceInput`을 생성하는 read-only adapter를 `FitnessRepository`와 기존 check-in 경로 위에 만든다.
3. 7일·28일 window 계산과 `unknown`/결측률 테스트를 먼저 추가한다.
4. `HYP_VOL_001`, `NUT_PRO_001`, `REC_SLEEP_001` 3개만 end-to-end로 연결한다.
5. 평가 결과를 local SQLite에 저장하고 설명·evidence ref를 read-back 테스트한다.
6. 이후 나머지 seed rule을 domain별로 확장한다.

원문 논문에서 PICO, 효과크기, 95% CI, 이질성, RoB/GRADE를 확인한 뒤에만 수치 threshold, medical rule, 자동 처방을 추가한다. release 완료나 임상적 안전성을 이 문서만으로 주장하지 않는다.

## 14. 원 출처

각 논문의 제목·PubMed 검색 링크·분야별 원 요약은 다음 파일에 있다.

- `evidence/01_근비대_근성장.md`
- `evidence/02_근력_최대근력.md`
- `evidence/03_체지방감소_다이어트.md`
- `evidence/04_린매스업_바디리컴포지션.md`
- `evidence/05_무산소_파워_스프린트.md`
- `evidence/06_유산소_심폐지구력.md`
- `evidence/07_동시훈련_하이브리드.md`
- `evidence/08_영양_보충제.md`
- `evidence/09_회복_자기관리.md`
