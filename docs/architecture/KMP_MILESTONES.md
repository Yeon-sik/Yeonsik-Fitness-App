# FitnessApp KMP 마일스톤

기준선: `main@c922c24727521b375cec1c9a4cf25dd7173810ed`

| 단계 | 목표 | 통과 조건 | 상태 |
|---|---|---|---|
| M0 | KMP 기반 구성 | `:shared`, Android/iOS target, 추출 계획, 단방향 의존 | ✅ 완료 |
| M1 | Shared pipeline 증명 | `WorkoutPerformanceCalculator`를 `commonMain`/`commonTest`로 이동하고 Android 회귀 없음 | ✅ 완료 |
| M2 | Pure leaf model 추출 | `CardioSessionSnapshot`, `BodyReadEntry` 등 공통화 | ✅ 완료 |
| M3 | Core type closure | Java/JVM 의존 핵심 타입을 common Kotlin으로 변환 | ✅ 완료 |
| M4 | Domain/API 추출 | Repository API, UseCase, business rule 공통화 | ✅ 완료 |
| M5 | Shared feature domain/read contract 확대 | Cardio 변환/필터/read contract, Body read, Meal/Records read model을 shared로 확장; JVM 의존 Home/Nutrition Analysis/Statistics는 후속으로 보류 | ✅ 완료 |
| M6 | iOS shared integration proof | SwiftUI에서 shared framework import·domain/business logic 호출; M8 macOS CI가 framework·Swift host compile을 검증 | ✅ HOST COMPILE VERIFIED / DEVICE UNVERIFIED |
| M7 | Body cross-platform vertical slice | Android Room adapter와 iOS in-memory adapter가 동일 shared Body contract/application logic 사용; M8 macOS CI가 Body adapter test와 Swift host compile을 검증 | ✅ HOST COMPILE VERIFIED / DEVICE UNVERIFIED |
| M8 | KMP release readiness hardening | CI, Android/KMP gate, iOS host compile gate, data/contract/security review; durable iOS persistence는 별도 상용 blocker | ⚠ HARDENING COMPLETE / COMMERCIAL-IOS BLOCKED |

## 공통 원칙

- 공유: Domain model, business rule, Repository API, UseCase
- 플랫폼 유지: UI, DB 구현, GPS/Maps, secure storage, platform SDK
- Room schema/migration 및 외부 contract는 KMP 이동과 동시에 변경하지 않는다.
- 한 단계에서 구조 이동과 동작 변경을 섞지 않는다.
- 각 단계는 Android build/test와 shared test가 통과한 뒤 다음 단계로 진행한다.

## 현재 위치

`M8 → commercial iOS persistence + device/runtime verification`

M1 성공 기준은 “실제 production 코드가 처음으로 commonMain에 들어가고 Android가 그대로 동작한다”는 것을 증명하는 것이다.

M8의 macOS host compile 결과는 해당 commit의 `ios-host` GitHub Actions job으로만 확정한다. Windows local 결과 또는 APK compile만으로 iOS runtime success를 주장하지 않는다.
