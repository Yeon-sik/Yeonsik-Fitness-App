# 논문 기반 조언 엔진 초안

## 목적

FitnessApp의 로컬 기록을 사용자의 현재 상태로 정규화하고, 검증된 evidence rule을 조건부 조언으로 변환한다.

이 초안의 핵심은 “논문 결과를 사용자에게 바로 처방한다”가 아니다.

```text
SQLite 기록
  → PaperAdviceSnapshotAssembler
  → PaperAdviceInput
  → PaperAdviceEngine
  → 관찰 · 근거 · 조건부 권고 · 제한사항
  → DevelopmentScreen 표시 후보
```

엔진은 진단, 치료, 운동 허가, 개인별 효과 보장을 수행하지 않는다.

## 현재 앱과의 경계

현재 FitnessApp은 `DevelopmentRepository.buildReport()`를 통해 `DevelopmentReport`와 `DevelopmentInsight`를 만든다. 기존 insight는 기록 커버리지·주간 빈도·집중 부위·저에너지 체크인 같은 앱 내부 해석이다.

이번 초안의 `PaperAdviceEngine`은 기존 insight를 교체하지 않는다.

- `DevelopmentInsight`: 앱 기록의 사실 요약과 운영상 다음 행동
- `PaperAdvice`: 논문 evidence ref가 붙은 조건부 해석
- `PaperAdviceEngine`: DB를 직접 읽지 않는 순수 Java rule matcher
- `PaperAdviceSnapshotAssembler`: `DevelopmentRepository`와 SQLite 조회 결과를 입력 모델로 변환하는 adapter

현재 구현은 `app/src/main/java/com/yeonsik/fitnessapp/development/`에 있으며, 실제 화면 연결은 아직 계획 상태다.

## 입력 모델

`PaperAdviceInput`은 DB row나 Android View를 직접 노출하지 않는 snapshot이다.

| 입력 | FitnessApp의 현재 근거 | 엔진 용도 |
|---|---|---|
| `goal` | `development_goals.objective` | 근비대·근력·리컴포지션 등 outcome 선택 |
| `bodyWeightKg` | `weight_records.weight_kg` | 단백질 g/kg 계산의 분모 |
| `proteinGPerKg` | 최근 14일 `meal_records.protein_grams`의 기록일 평균과 최신 체중 | 총 단백질 검토 후보 |
| `proteinRecordedDays` | 최근 14일 단백질 기록이 있는 날짜 수 | 기록일이 충분할 때만 단백질 rule 활성화 |
| `sleepHours` | `nutrition_daily_checkins.sleep_hours` | 수면·readiness 조절 |
| `energyScore`, `readinessScore` | `AthleteDailyCheckIn` | 당일 회복 신호 |
| `resistanceTrainingSessionsPerWeek` | 완료된 `workout_records` | 목표별 훈련 노출 |
| `weeklyHardSetsPerMuscle` | `workout_exercises`·`workout_sets` | 근육군별 자극량의 후속 rule 입력 |
| `failureSetsRatio` | 완료 세트 metadata/후속 set aggregation | 실패훈련 해석 |
| `coldWaterImmediatelyPostResistance` | 현재 SQLite에 해당 명시 필드 없음 | `null` 유지, 장기 적응 rule 비활성화 |
| `painReported` | 현재 SQLite에 해당 명시 필드 없음 | `null` 유지, 안전 gate 오작동 방지 |
| `recentDataDays` | `DevelopmentReport.DataCoverage` | 데이터 부족 시 조언 차단 |

`proteinGPerKg`는 adapter가 최근 14일 기록일 평균으로 계산하지만, 미기록 식사를 섭취하지 않은 것으로 간주하지 않는다. `failureSetsRatio`는 현재 set schema만으로 실패를 확정할 수 없어 `null`이다. 두 값 모두 `DevelopmentReport`에 직접 들어 있지 않으며, 화면 연결 시 snapshot과 계산 기간을 함께 보존해야 한다.

## 현재 구현된 pilot rules

| advice ID | 조건 | 근거 | 결과 |
|---|---|---|---|
| `DATA_COVERAGE_001` | 최근 기록 0일 | 앱 데이터 경계 | 조언 보류 |
| `SAFETY_PAIN_001` | 통증 flag | 앱 안전 정책 | 일반 rule 중지, 의료 평가 안내 |
| `REC_SLEEP_001` | 수면 < 7h 또는 energy/readiness ≤ 2 | `09#1`, `09#2`, `09#3` | 기술 복잡도·세션량 조절 후보 |
| `NUT_PRO_001` | 적응 목표이며 protein < 1.6 g/kg/day | `08#1`, `08#2` | 총량·기록 누락·에너지 섭취 검토 |
| `TRAIN_FAIL_001` | 근비대/최대근력이며 실패 세트 비율 > 50% | `02#3`, `01#3` | RIR·수행 품질·회복 점검 |
| `REC_COLD_003` | 적응 목표이며 RT 직후 CWI | `09#9`, `09#10` | 기본값 추천 금지, 상황별 trade-off 표시 |

수치 조건은 개인의 생리적 최소·최대 기준이 아니라 pilot trigger다. 특히 `1.6 g/kg/day`, `7h`, `50%`는 rule의 후보 조건이며, 사용자에게 절대선으로 표시하지 않는다.

## 출력 계약

모든 `PaperAdvice`는 다음 순서를 유지한다.

1. `observationKo`: 사용자의 실제 기록에서 관찰된 값
2. `evidenceRefs`: `02#3`처럼 검증 문서의 source fingerprint
3. `recommendationKo`: 조건부 행동 후보
4. `limitationKo`: 연구·입력·개인화의 한계
5. `confidence`: 내부 충돌 해결용 등급. GRADE나 개인 성공확률이 아니다.
6. `status`: `ACTIONABLE`, `INFORMATIONAL`, `INSUFFICIENT_DATA`, `SAFETY_REVIEW`

화면은 `recommendationKo`만 단독으로 보여주면 안 된다. 최소한 관찰·근거·제한사항을 펼쳐 볼 수 있어야 한다.

## 안전 및 실패 모드

- 입력이 없으면 조언하지 않는다.
- 통증 flag가 있으면 일반 훈련·영양 조언보다 안전 검토를 먼저 표시한다.
- 결측은 0으로 대체하지 않는다. 단백질 미기록은 저단백, 운동 미기록은 미운동이 아니다.
- 논문 평균효과를 개인에게 보장하지 않는다.
- position stand·consensus·관찰연구를 RCT effect weight와 동일하게 취급하지 않는다.
- 수면·DOMS·biomarker·주관적 회복을 근비대나 근력 향상으로 합산하지 않는다.
- source fingerprint가 `unresolved_seed_metadata`인 `07#10`은 엔진 근거로 연결하지 않는다.
- 의료 rule은 만들지 않는다. 지속·악화 통증이나 기능 저하는 의료 전문가 평가 경로로 보낸다.

## 다음 연결 단계

1. `DevelopmentReport`에 `List<PaperAdvice>`를 추가하되 기존 `DevelopmentInsight`와 별도 섹션으로 표시한다.
2. 화면에서 evidence ref를 검증 문서의 사람이 읽을 수 있는 제목으로 매핑한다.
3. 실패 세트·CWI·통증의 원천 필드를 확정하고, 기간·결측·source를 snapshot에 기록한다.
4. 공식 원문 검증이 끝난 rule만 catalog에서 활성화하고, 먼저 단위 테스트·로컬 UI 확인을 수행한다.
5. 실제 기기에서 기록 → snapshot → advice → 화면 표시를 검증한다. 현재는 adapter snapshot 생성과 engine 반환까지 검증했고, 화면 표시·운영/의료 정확성은 미검증이다.

## 현재 검증 상태

- 구현 상태: `PaperAdviceInput`, `PaperAdvice`, `PaperAdviceEngine`, `PaperAdviceSnapshotAssembler`와 단위/instrumentation test가 추가된 초안.
- 연결 상태: adapter가 `DevelopmentRepository`와 로컬 SQLite를 읽어 snapshot/조언을 만들며, `DevelopmentScreen` 표시에는 아직 미연결.
- 문헌 상태: 02–09 80개 seed가 [EVIDENCE_PAPER_VERIFICATION_V1.md](./EVIDENCE_PAPER_VERIFICATION_V1.md)에 기록됨.
- 검증한 것: 순수 Java 입력 검증·pilot rule matching, SM-A256N Android 기기에서 SQLite 기록 → snapshot → 조언 반환 경로 2개 테스트, 현재 설치 앱 DB를 대상으로 한 읽기 전용 smoke test.
- 실기기 실제 데이터 결과 (2026-08-14): 테스트는 PASS했고 DB 변경은 없었다. 다만 세션/식별자 미구성, 현재 사용자 소유 목표·운동·식사·체중·체크인 0건, 전체 운동·식사 0건으로 확인되어 `DATA_COVERAGE_001:INSUFFICIENT_DATA`만 반환됐다. 개인화 조언의 내용은 검증되지 않았다.
- 아직 검증하지 않은 것: 실제 사용자의 기록이 입력된 상태에서의 개인화 조언, Android UI 표시, 운영 배포, 의료적 안전성.
