# Fitness Evidence → Rule Engine v2

v1의 9개 분야·90개 evidence seed를 앱이 읽을 수 있는 규칙 구조로 변환한
개발용 패키지다.

## 구성

-   `evidence/`: v1의 분야별 문헌 요약
-   `rules.jsonl`: 기계 판독용 seed rules
-   `rules.csv`: 사람 검토용 규칙표
-   `rule.schema.json`: JSON Schema
-   `SCORING_SPEC.md`: 판정·점수·충돌해결 원칙
-   `IMPLEMENTATION.md`: 앱 적용 구조
-   `PAPER_ADVICE_ENGINE_DRAFT.md`: 현재 FitnessApp에 연결하기 위한 논문 기반 조언 엔진 초안
-   `EVIDENCE_PAPER_VERIFICATION_V1.md`: 01–09 원문·공식초록 검증 기록

현재 24개의 보수적 seed rule을 포함한다. 이 버전은 의료 진단 엔진이나
최종 임상 검증본이 아니다. 원문 PICO, 효과크기, 95% CI, RoB/GRADE가
완전히 추출되지 않은 규칙에는 hard threshold를 최소화했다.

현재 앱 코드는 `DevelopmentRepository`의 로컬 SQLite 기록을
`PaperAdviceSnapshotAssembler`가 `PaperAdviceInput`으로 변환하고
`PaperAdviceEngine → PaperAdvice`로 전달하는 초안을 포함한다. `DevelopmentScreen`
표시는 아직 연결하지 않았다. 결측 기록은 유지하며, 통증 flag가 명시된 경우
일반 조언을 제한하는 gate를 둔다.
