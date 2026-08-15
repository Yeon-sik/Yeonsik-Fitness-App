# Implementation

## 최소 상태 모델

``` json
{
  "user_state": {
    "goal": "hypertrophy",
    "training_age_months": 24,
    "body_weight_kg": 89,
    "weekly_weight_change_pct": 0.0,
    "protein_g_kg": 1.25,
    "sleep_duration_h": 6.1
  },
  "training_state": {
    "weekly_hard_sets_per_muscle": {"chest": 8},
    "weekly_frequency_per_muscle": {"chest": 2},
    "failure_sets_ratio": 0.25,
    "performance_trend": {"bench_e1rm_4wk_pct": 0.0}
  }
}
```

엔진은 여러 rule을 동시에 발화시킨다. 예컨대 벤치 정체가 있어도 단백질
하나에 원인을 귀속하지 않는다.

모든 사용자 권고에는 `rule_id`, `evidence_refs`, `confidence`, 입력
데이터의 기간과 결측 여부를 붙여 감사 가능하게 만든다.

## 논문 기반 조언 엔진 초안

현재 pilot 구현은 `com.yeonsik.fitnessapp.development`의
`PaperAdviceInput`, `PaperAdvice`, `PaperAdviceEngine`이다.

```text
DevelopmentRepository와 SQLite 조회 결과
  → PaperAdviceSnapshotAssembler
  → PaperAdviceInput
  → PaperAdviceEngine.evaluate()
  → PaperAdvice 목록
```

기존 `DevelopmentInsightRules`와 병렬로 두며, 기존 insight를 논문 근거라고
재표현하지 않는다. `PaperAdviceSnapshotAssembler`는 목표·체중·식사 기록·체크인·
완료 저항운동·부위별 세트를 읽고, 현재 schema에 없는 실패 세트·냉수욕·통증은
`null`로 보존한다. 자세한 입력 매핑·pilot 조건·안전 경계는
`PAPER_ADVICE_ENGINE_DRAFT.md`에 기록한다.

초안의 검증 경계는 순수 Java unit test와 SM-A256N 실기기 instrumentation
test의 SQLite snapshot 경로다. 현재 설치 앱 DB를 대상으로 한 읽기 전용 smoke
test도 통과했지만, 세션/식별자와 기록이 없어 개인화 조언은 검증되지 않았다.
화면 표시, 기록이 입력된 상태의 개인화 조언, 운영 데이터, 의료적 안전성은
아직 검증하지 않았다.

주의: `connectedDebugAndroidTest`는 실행 종료 과정에서 대상 앱 패키지를 제거할
수 있어 앱 내부 SQLite 기록을 함께 삭제할 수 있다. 개인 기록이 있는 실기기에서는
이 작업을 실행하지 말고, 별도 테스트 기기·에뮬레이터 또는 먼저 내보낸 백업을 사용한다.
