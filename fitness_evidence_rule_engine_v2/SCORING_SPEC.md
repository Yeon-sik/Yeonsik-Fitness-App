# Scoring Specification

## 핵심 구조

`raw logs → normalized metrics → trend features → goal context → rule matching → evidence weighting → explanation`

## 5개 평가축

1.  `stimulus_score`: 목표에 맞는 훈련 자극
2.  `progress_score`: 실제 수행·체성분 추세
3.  `recovery_score`: 수면·피로·세션 품질
4.  `nutrition_score`: 에너지·단백질·탄수화물 적합성
5.  `risk_flag`: 통증·급격한 변화·비정상적 피로. 진단 점수가 아니다.

unknown은 0점이 아니다. 데이터 부족 상태로 유지한다.

## Evidence confidence 내부 weight

-   high: 1.00
-   moderate: 0.70
-   low: 0.40

이는 GRADE 점수가 아니라 규칙 충돌 해결용 내부 가중치다.

## 충돌 우선순위

안전/통증 → 목표 specificity → 직접 outcome → evidence confidence → 최신
고수준 종합근거.

## 출력 원칙

원인을 단정하지 않는다.
`관찰 → 근거 → 조건부 해석 → 행동 후보 → 불확실성` 순서로 설명한다.

예: "최근 단백질 추정치가 낮은 구간이고 근비대 목표다. 현재 근거상
증가를 검토할 가치가 있다. 다만 에너지 섭취, 훈련 자극, 측정오차도 함께
확인해야 한다."
