# FitnessApp KMP 마일스톤

기준선: `main@ea465a2` — PR #73 병합 완료

| 단계 | 목표 | 통과 조건 | 상태 |
|---|---|---|---|
| M0 | KMP 기반 구성 | `:shared`, Android/iOS target, 추출 계획, 단방향 의존 | ✅ 완료 |
| M1 | Shared pipeline 증명 | `WorkoutPerformanceCalculator`를 `commonMain`/`commonTest`로 이동하고 Android 회귀 없음 | ✅ 완료 |
| M2 | Pure leaf model 추출 | `CardioSessionSnapshot`, `BodyReadEntry` 등 공통화 | 대기 |
| M3 | Core type closure | Java/JVM 의존 핵심 타입을 common Kotlin으로 변환 | 대기 |
| M4 | Domain/API 추출 | Repository API, UseCase, business rule 공통화 | 대기 |
| M5 | 기능별 shared 확대 | Workout → Cardio → Body → Meal/Nutrition → Statistics 등 | 대기 |
| M6 | iOS 연결/Adapter | SwiftUI에서 shared 호출, iOS persistence/network/platform adapter 연결 | 대기 |
| M7 | 양 플랫폼 기능 확장 | Android/iOS가 동일 shared contract/business logic 사용 | 대기 |
| M8 | QA/상용화 준비 | 실기기, migration, sync, security, parity 검증 | 대기 |

## 공통 원칙

- 공유: Domain model, business rule, Repository API, UseCase
- 플랫폼 유지: UI, DB 구현, GPS/Maps, secure storage, platform SDK
- Room schema/migration 및 외부 contract는 KMP 이동과 동시에 변경하지 않는다.
- 한 단계에서 구조 이동과 동작 변경을 섞지 않는다.
- 각 단계는 Android build/test와 shared test가 통과한 뒤 다음 단계로 진행한다.

## 현재 위치

`M1 ✅ → M2`

M1 성공 기준은 “실제 production 코드가 처음으로 commonMain에 들어가고 Android가 그대로 동작한다”는 것을 증명하는 것이다.
