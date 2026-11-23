# FitnessApp Android·iOS 공통화 마일스톤

2026-11-23 제안. 목표는 현재 휴대폰에 설치된 Android 앱의 사용법과 기록을 보존하면서 같은 제품 화면과 기능을 iPhone에 제공하는 것이다. 아래 단계의 완료 여부는 각 단계의 검증 결과로 판단한다. 기존 KMP M0~M8 결과는 문서 끝에 보관한다.

## 출발점

| 확인 대상 | 이번 계획의 기준 |
| --- | --- |
| 설치된 Android | `com.yeonsik.fitnessapp` 1.1.0 (2)의 설치 APK가 로컬 `feat/health-connect-workout-import-20261109` 작업 트리의 `app-qa.apk`와 SHA-256까지 일치했다. 지문은 `92699d4206fee4ebc40abe3ba22f1672834f28807f3689d82c02e0b98a3b2057`이다. 작업 트리 HEAD `e7b992ac`는 Room 62와 Health Connect 운동 조회·검토·연결을 포함한다. 이 확인에서 사용자의 DB는 열지 않았다. |
| 체중 공통화 | `feat/shared-body-weight@ad4b4e66`는 Room 61을 사용한다. 공통 Compose 체중 카드·편집창·동작, Android Room 연결, iOS 파일 저장이 구현됐다. iOS는 **운동·기록·체중** 세 탭이고 체중은 kg 입력·최근 20건 범위다. [빌드 305 CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/44573855797)에서 Android·공통 검사, Android 에뮬레이터, iOS 시뮬레이터·ARM64 IPA가 통과했다. [iOS 설치 및 검증 범위](../../iosApp/README.md) |
| 두 브랜치의 관계 | 공통 조상은 `0b47010c`다. 설치본 계열의 후속 커밋 하나와 체중 계열의 후속 커밋 13개가 갈라져 있다. 따라서 통합 전 빌드 305의 Android APK는 현재 기기의 업데이트 후보에서 제외한다. 빌드 297에서 사용자가 iPhone 앱 재실행과 동일 IPA 덮어 설치 후 진행 중 세트 유지를 확인한 범위는 후속 빌드의 실기기 검증과 구분한다. |

## 한 번 구현할 부분과 플랫폼별 부분

| 책임 | 구현과 검증 |
| --- | --- |
| 화면과 업무 규칙 | 기존 `:shared/commonMain`에서 화면 본문·카드·입력 순서·문구·오류·버튼 상태, 초안, 저장·취소·복귀 결정, 검증·환산·집계를 소유한다. 기존 Android 컴포넌트와 KMP 모델·서비스·저장 계약을 기능별로 옮기거나 재사용한다. 예를 들어 일반 세트의 저장 가능 조건을 고치면 두 호스트가 같은 Kotlin 코드를 실행한다. |
| 화면 이동과 시스템 표현 | 목적지 ID, 탭 이름·순서, 재선택·재진입·뒤로 가기 결과는 공통 상태가 소유한다. 플랫폼 호스트는 선택된 목적지와 사용자 이벤트만 주고받는다. Android 하단 바의 기존 글래스 효과와 시스템 뒤로 가기를 보존한다. iOS 탭 바·툴바에는 지원 OS와 SDK에서 시스템 Liquid Glass를 쓰고 이전 iOS에서는 해당 OS의 기본 바를 쓴다. 스와이프 취소·완료, 시스템 키보드, 안전 영역, 투명도·동작 줄이기를 각 기기에서 확인한다. |
| 저장·연동 | 공통 기능은 기존 저장 계약과 데이터 의미, 요청·응답·오류·재시도 정책을 소유한다. Android는 기존 Repository → DAO → `fitness_mvp.db`, iOS는 호환되는 영구 저장 어댑터를 쓴다. 권한, 파일 선택·공유, 보안 저장, 위치·건강 SDK는 호스트가 담당한다. 저장 작업당 쓰기 경로는 하나다. API·저장 형식 변경은 양쪽 어댑터와 구버전 호환 검증이 함께 필요하다. |

## 구현 순서와 완료 조건

| 단계 | 구현 범위 | 다음 단계로 넘어갈 증거 |
| --- | --- | --- |
| 0. 설치본 기준 고정과 통합 | 현 Android 메뉴·하위 화면·독립 Activity·설정·외부 진입점을 목록화한다. 같은 기기 크기·글자·테마에서 합성 기록으로 주요 화면과 터치 순서를 남긴다. 기능 브랜치에서 설치본 계열과 체중 공통화 계열을 결합한다. | Android applicationId·서명 호환성·DB 파일과 Room 62를 확인한다. 복제 가능한 테스트 DB로 61→62의 기존 마이그레이션 및 62→62 재개방을 검증한다. 운동·체중 ID와 값, 소유자, 웨어러블 연결을 대조하고 새 체중 저장 후 기존 운동을 다시 읽는다. |
| 1. 탭과 설정의 뼈대 | 제품 탭 **메인·피트니스·기록·설정**의 목적지·순서·복귀를 공통화한다. Android 외형을 보존하고 iOS 시스템 바를 연결한다. 체중을 기존 제품 진입점에 연결하고 kg/lb 및 시스템·라이트·다크 설정의 의미·기본값·기존 저장값을 맞춘다. | Android 전후 스크린샷과 조작 순서가 일치한다. 양쪽 모두 체중 편집 중 달력부터 닫히며 설정 변경 뒤 같은 단위와 테마로 재진입한다. 미이식 목적지는 목록에 표시하고 동작이 완성된 경로만 수용 처리한다. |
| 2. 일상 운동 한 번 완주 | 피트니스 → 루틴·종목 선택 → 진행 중 운동 → 일반 세트의 중량·단위·횟수·RIR 추가·수정·삭제 → 재개 → 완료 → 기록 상세를 옮긴다. 첫 구현 PR은 한 종목의 일반 세트 편집기로 제한한다. | 같은 시나리오를 기존 Android와 비교한 뒤 두 실기기에서 완주한다. 키보드·포커스·연속 저장·취소·뒤로 가기·강제 종료 후 재개, 완료 시점의 세트 순서·원래 입력 단위·kg 환산값·이력을 대조한다. 297/305의 기존 iOS 기록도 다시 읽힌다. |
| 3. 근력 운동 전체 | 기존 루틴 편집·카탈로그·운동 연결, 기록 유형·LoadState, 드롭세트·슈퍼세트·그룹, 메모·휴식 타이머와 기존 운동 상세를 각 PR로 옮긴다. | 단계 0에서 열거한 동작마다 Android 전후와 iOS 결과가 일치한다. 루틴 수정 후 과거 완료 기록은 유지되고, 특수 세트 관계·정렬·타이머 복귀가 재실행 뒤에도 기존 규칙을 따른다. |
| 4. 일상 기능과 조회 화면 | 홈·기록 달력과 필터, 체성분·목표, 식사·영양·보충제, 유산소 수동 기록, 통계·발전 화면을 기능별로 이식한다. 홈과 통계에 표시할 값은 소유 기능의 읽기 API를 사용한다. | 같은 합성 이력의 선택 날짜·정렬·합계·빈 상태와 상세 화면 복귀가 일치한다. 식사 시점 영양 스냅샷, 미상과 0의 구분, 기존 목표·설정값을 그대로 읽는다. |
| 5. 계정·동기화·파일 | 설정의 Personal OS·영양·PriceTrace 연결별 인증과 수동 동기화, 활동 정보 검토함·건강 자료 내보내기, Fleek CSV·운동 전송·CSV 내보내기·백업 복원을 포맷별로 검증한다. 파일 선택과 보안 저장은 각 OS에 연결한다. | 구버전 샘플과 두 테스트 계정으로 소유자 격리·재가져오기 중복·오프라인 저장·실패 후 재시도·설정 복구를 확인한다. Android → iOS 기록 이관은 지원 포맷별 왕복·누락 필드·충돌 정책을 명시한다. 운동 전송 v1/v2, 전체 로컬 백업, 서버 동기화는 각각 별도의 계약이다. 운영 연동은 실제 왕복 결과로 판정한다. |
| 6. 센서·건강·백그라운드 | GPS·지도·추적, 알림·휴식, Android Health Connect와 iOS HealthKit의 입력을 기능별로 연결한다. 기존 웨어러블 흐름인 조회 → 검토 → 선택 보관 또는 기존 운동에 연결을 유지하고 지원 가능한 데이터 항목과 OS 차이를 문서화한다. | 같은 외부 운동 재수신 시 중복이 생기지 않고, 원본 운동·수동 심박·세트가 보존된다. 웨어러블 측정값의 기존 계정별 기기 저장 및 전송 제외 정책을 유지한다. 권한 거부·철회, 앱 잠금·복귀, 무료 계정으로 재서명된 iPhone의 실제 권한과 동작을 각각 확인한다. |
| 7. 교체 승인 | 단계 0 목록의 각 행을 **양쪽 실기기 통과 / 근거 있는 OS 차이 / 미구현**으로 판정한다. 지원하는 iOS 버전과 접근성 설정에서 겹침·가독성·포커스를 확인한다. | 공통 필수 흐름의 미구현 및 미해결 Android 회귀가 0건이어야 한다. Android 기존 설치 위 업데이트, iPhone 같은 Apple 계정·최종 번들 ID로 덮어 설치 및 재서명 뒤 같은 기록을 열어 본다. CI, IPA 생성, 설치·실행, 데이터 보존 및 서버 검증은 각각 근거를 남긴다. |

## 병렬 개발과 회귀 처리

단계 0의 첫 산출물은 설치본과 체중 브랜치 양쪽을 재현할 수 있는 기준 커밋과 APK 지문이다. 확인 시점에 GitHub에는 설치본 작업 트리와 같은 이름의 원격 브랜치가 없었다. 통합 브랜치는 로컬 `e7b992ac`를 명시적으로 포함해야 한다. Android와 iOS에서 같은 Core를 사용해도 설치된 앱에는 각 플랫폼의 새 빌드를 설치한 뒤 변경이 반영된다. 동일 계정의 두 기기에서 같은 기록을 보는 것은 단계 5의 전송·동기화 범위에 속한다.

1. **기능 하나의 계약을 먼저 고정한다.** 기존 진입점, 합성 입력, 예상 화면·DB 결과, 허용된 OS 차이를 한 행에 적는다. 예를 들어 “lb로 세트 수정 → 저장 → 재진입”에는 표시 단위와 원본·환산값, 같은 세트 ID가 함께 들어간다.
2. **그 후 세 작업을 병렬로 진행한다.** 공통 담당은 화면·상태·규칙과 공통 테스트를 맡고, Android 담당은 이전 화면과 기존 Room·SDK를 대조하며, iOS 담당은 영구 저장·시스템 UI·구버전 파일 호환을 맡는다. 양쪽 어댑터에 같은 입력과 기대값을 적용한다. 한 기능의 계약과 공통 화면은 한 담당자가 소유한다.
3. **작은 기능 단위로 연결한다.** 기존 Android 진입점을 유지하고 두 경로를 테스트로 비교한 뒤 공통 구현으로 교체한다. UI 차이·초안 유실·ID 변경이 발견되면 그 기능의 전환을 보류하고 기존 진입점을 유지한다. 복구는 화면 연결 변경으로 처리하며 DB 다운그레이드나 앱 삭제를 수단으로 삼지 않는다.
4. **병렬 작업의 선후관계를 지킨다.** 0 이후 1과 함께 iOS 저장 호환 및 외부 연동 샘플을 준비할 수 있다. 2의 공통 저장·복귀가 검증되면 3과 4의 개별 기능, 5의 파일·계정 계약, 6의 기기 권한 실험을 병행할 수 있다. 집계 화면의 완료는 원천 기능 검증 이후, 데이터 이관은 소유자·포맷 검증 이후다. 진행 중 Android에 새로 추가된 기능도 설치본 대비 목록에 반영한다.
5. **각 기능 PR에 증거를 남긴다.** Android 전후 스크린샷·조작, 공통 테스트, Android 단위·빌드·해당 instrumentation, iOS 시뮬레이터·ARM64 IPA 결과를 첨부한다. 실기기 확인 전에는 해당 칸을 대기로 남긴다. 양쪽 실기기 통과 후에도 운영 계정 연동은 별도로 표시한다. 샘플 기록은 합성 데이터만 사용한다.

iOS 저장 형식을 확장할 때는 빌드 297의 운동 파일과 빌드 305의 체중 파일을 재현한 표본부터 읽는다. 형식 변경이 필요한 기능은 이전 파일에서 ID·값·진행 중 상태를 읽어 새 형식으로 원자적으로 저장하고 다시 읽는 검사를 추가한다. 읽기 실패·쓰기 중단·지원하지 않는 버전에서 원본을 보존하는 경우도 검증한다.

설계 근거는 [공통 개발 기준](ANDROID_IOS_DEVELOPMENT.md), [Android 데이터·계약 보존 규칙](../FitnessApp_정돈_리팩터링_규칙.md), [SwiftUI·Compose 연결](https://kotlinlang.org/docs/multiplatform/compose-swiftui-integration.html), [Apple Liquid Glass 적용 가이드](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass), [무료 Apple 계정의 iOS 기능 지원표](https://developer.apple.com/help/account/reference/supported-capabilities-ios/)를 따른다.

## 과거 KMP 기반 작업 M0~M8

당시 기준 커밋은 `main@c922c24727521b375cec1c9a4cf25dd7173810ed`다. 아래 판정은 당시 커밋에 해당한다.

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

### 당시 공통 원칙

- 공유: Domain model, business rule, Repository API, UseCase
- 플랫폼 유지: UI, DB 구현, GPS/Maps, secure storage, platform SDK
- Room schema/migration 및 외부 contract는 KMP 이동과 동시에 변경하지 않는다.
- 한 단계에서 구조 이동과 동작 변경을 섞지 않는다.
- 각 단계는 Android build/test와 shared test가 통과한 뒤 다음 단계로 진행한다.

### M8 종료 시점

`M8 → commercial iOS persistence + device/runtime verification`

M1 성공 기준은 “실제 production 코드가 처음으로 commonMain에 들어가고 Android가 그대로 동작한다”는 것을 증명하는 것이다.

M8의 macOS host compile 결과는 해당 commit의 `ios-host` GitHub Actions job으로만 확정한다. Windows local 결과 또는 APK compile만으로 iOS runtime success를 주장하지 않는다.
