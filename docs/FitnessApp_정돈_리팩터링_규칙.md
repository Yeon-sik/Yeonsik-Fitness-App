# FitnessApp 정돈 리팩터링 규칙

2026-11-19 갱신. Android 구조·로컬 데이터·외부 계약을 변경할 때 적용한다. 양쪽에 제공하는 기능은 [Android·iOS 공통 개발 기준](architecture/ANDROID_IOS_DEVELOPMENT.md)도 따른다.

## 1. 현재 구조와 이행 범위

| 구분 | 구조 |
| --- | --- |
| CURRENT | [main@0b47010c](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/0b47010c3afbca14792dff162ddc46bf1819563b)는 Java·Kotlin JVM 17과 Compose·Room을 쓰는 Android `:app`, KMP `:shared`, SwiftUI 검증 호스트를 같은 저장소에 둔다. Android의 `fitness_mvp.db`는 Room v61이다. v50은 기존 SQLite 경계이며 v51부터 Room이 인수한다. |
| PROPOSED | 변경하는 기능 단위로 화면 상태·업무·저장을 정리한다. Android 의존성이 없는 코드와 공통으로 제공할 UI는 공통 개발 기준에 따라 옮기고, 양쪽 플랫폼에 호환 어댑터를 연결한다. |
| TARGET | Android 저장과 플랫폼 구현은 `:app`, 양쪽의 순수 규칙과 계약은 기존 `:shared`에 둔다. 공통 UI와 상태를 두 호스트에서 실행하고, 별도 UI 모듈의 필요성은 첫 기능의 호환성 검증으로 판단한다. |

SQLite v50에서 Kotlin·Room·Compose로 옮겼던 단계별 작업과 단일 `:app` 목표는 [이전 리팩터링 계획](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/b158404165f80b287ca958eca32f2e2c462e1dd5/docs/FitnessApp_%EC%A0%95%EB%8F%88_%EB%A6%AC%ED%8C%A9%ED%84%B0%EB%A7%81_%EA%B7%9C%EC%B9%99.md)에 기록돼 있다. 당시 전환 순서와 첫 PR 범위는 그 작업의 기록이다. [Stage 0 기준선](FitnessApp_Stage0_기준선.md)은 구버전 호환성에 필요한 비교 근거다.

## 2. 데이터 소유권

| 경계 | 소유 데이터와 규칙 |
| --- | --- |
| Fitness 운동 | 세션·종목·세트·루틴·GPS와 운동 identity를 관리한다. 세션 수정은 루틴 원본과 이미 완료한 기록에 영향을 주지 않는다. |
| Fitness Meal | 실제 섭취 시각·양·메뉴·항목별 영양 스냅샷을 관리한다. Nutrition 변경·삭제 뒤에도 과거 섭취값을 유지한다. |
| Fitness Nutrition | 음식·레시피·영양 identity·canonical provenance·공개 상태를 관리한다. 음식 등록과 섭취 기록은 별도 동작이다. |
| Fitness 신체·보충제 | Fitness가 보유한 체중·체성분·목표·체크인·복용 상세 기록의 소유자와 기존 조회 경로를 유지한다. |
| Personal OS | 신규 공유는 [완료 요약 v2](FITNESS_SUMMARY_PROJECTION_V2.md)의 허용 필드를 사용한다. 기존 상세 v1 호환 경로의 변경은 소비자 전환과 원격 기록 복구 경로를 검증한 뒤 별도로 진행한다. |
| PriceTrace, OCR, CashOS | 각 서비스가 소유한 상품·식당·지점·메뉴·구매·영수증 등의 원본은 식별자와 계약된 읽기·연결·발행 경로를 사용한다. 구매나 OCR 결과를 섭취로 처리할 때는 검증된 [Meal ingest 계약](fitness-meal-verified-import.v1.md)을 적용한다. |

동일 물리 DB에 있는 데이터도 업무 소유권에 따라 분리한다. Shared·Nutrition·PriceTrace의 인증 대상과 migration 대상, 사용자 식별자는 해당 프로젝트를 기준으로 확인한다.

## 3. 의존성과 상태

```text
화면 → 화면 상태·업무 서비스 → 기능별 저장 계약 → 플랫폼 저장소
                                           Android → DAO → Room / SQLite
                                           iOS     → 영구 저장 어댑터(이식 대상)
외부 연동 → 계약 DTO·매퍼 → 기능 API
홈·기록·발전 조회 → 기능 읽기 API → 화면용 집계 모델
```

- 기존 namespace·applicationId·기능 진입점을 기준으로 유지한다. 계층과 인터페이스는 책임을 분리하거나 두 플랫폼이 소비할 때 필요한 만큼 둔다.
- 저장 계약은 도메인 모델을 사용한다. `Cursor`, `ContentValues`, Room Entity, 외부 API JSON과 플랫폼 SDK 타입은 구현 내부에서 다룬다.
- 기능은 공개 API로 다른 기능에 접근한다. 각 DAO 쓰기는 소유 기능을 통해 수행한다. 여러 저장을 조정해야 하는 운동·유산소 완료 같은 작업은 업무 서비스가 담당하고, 동일 DB에 속한 로컬 완료 작업은 트랜잭션으로 묶는다.
- 기존 호환 어댑터는 호출 경로와 동작이 검증될 때까지 유지한다. 안정된 Java와 Android View는 필요한 범위에서 사용하고, 언어 변환과 동작 수정은 검토 가능한 단위로 나눈다.
- 화면은 입력 초안과 검증된 저장 모델을 구분한다. DB·네트워크 I/O는 메인 스레드 밖에서 수행한다. 세트 쓰기 순서와 작업 시작 당시의 소유자·프로젝트를 유지하고, 완료 동작은 앞선 쓰기의 결과를 반영한다.
- 화면 렌더링은 상태를 표현한다. 세트 초기화와 저장·동기화는 명시적인 동작과 적절한 수명주기에서 처리한다. 화면 복구에는 ID·초안을 쓰고 저장된 기록은 저장소에서 조회한다.
- 로컬 쓰기·동기화·요약 발행의 성공 상태를 각각 기록한다. 네트워크·발행 실패 후에도 로컬 기록과 재시도에 필요한 식별자를 보존한다.

## 4. Android DB·계약 보존

- [FitnessDatabaseContract](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessDatabaseContract.kt)에서 현재 버전과 v50→v51 인수 경계를 확인한다. Room이 같은 파일의 열기와 버전 변경을 관리한다. 실제 migration과 스키마 스냅샷으로 신규 설치 및 기존 버전 업그레이드를 검증한다.
- DB 이름, 소유자, 레코드·외부 참조 ID, 시각·날짜 의미, 삭제 상태와 sync cursor를 보존한다. kg 환산값과 입력값·단위, 운동 identity와 LoadState를 유지한다.
- Meal·Nutrition 스냅샷의 미확인 `NULL` 값, 실제 0, 구 payload의 누락 필드를 구분한다. 합의된 계약과 migration 없이 최신 카탈로그 값이나 삭제 cascade를 기존 Meal에 적용할 수 없다.
- 테이블 변경에는 PK·nullability·기본값·index·참조 검증을 포함한다. 테이블 재구성 시 같은 트랜잭션에서 행을 복사하고 ID·행 수·값을 대조한다. `fallbackToDestructiveMigration`과 `allowMainThreadQueries`는 금지한다.
- DB 스키마와 `fitness-os.local-backup`의 형식 버전을 독립적으로 관리한다. 지원하는 기존 백업·`yeonsik.workout-transfer` v1/v2는 실제 표본으로 재조회·복원·중복 처리를 확인한다. 새 백업의 구버전 호환이나 운동 전송의 전체 DB 복원은 해당 계약이 제공하는 범위를 확인한다.
- 외부 계약 변화가 필요하면 영향을 받는 소비자, 구버전 reader, 버전 이행과 복구를 별도 변경으로 다룬다. Android 기록을 iOS에 연동할 때도 원본 DB와 소유자·삭제·정렬·단위 의미를 보존한다.

## 5. 작업 순서와 검증

1. 최신 `origin/main`과 변경 대상의 코드·테스트·migration을 확인하고 기능 브랜치를 사용한다.
2. 기존 진입점의 입력 → 업무 검증 → 저장 → 재조회와 실패 시 동작을 확인한다. 영향 범위에 맞춰 운동 유형·LoadState, 루틴과 운동 식별자, 단위, 진행·완료 상태, Meal 스냅샷과 두 계정 데이터를 기준 입력으로 잡는다.
3. 작은 기능 하나에서 기존 동작을 보존하며 구현한다. 공통 UI·상태는 공통 개발 기준의 호스트 연결과 비교 검증을 적용한다. Android 전면 재작성과 추가 모듈은 실제 사용 경계와 별도의 이행 검증이 필요하다.
4. 기능 변경은 `.\gradlew.bat testDebugUnitTest assembleDebug`를 실행한다. DB·Repository 변경은 관련 instrumentation에서 신규 설치, 영향받는 기존 DB 및 레거시 fixture 업그레이드, 복구를 확인한다. 실기기에 업데이트할 때는 호환 서명과 기존 데이터를 확인한 뒤 덮어 설치한다.
5. Compose 변경은 입력·포커스·뒤로 가기·재진입·빠른 저장을 확인한다. KMP·iOS 변경은 해당 공통 테스트, iOS Simulator, 실기기용 빌드와 직접 실행을 확인한 범위대로 기록한다.

단위 테스트, 패키지 빌드, instrumentation, 양쪽 실기기, 출시 서명, 운영 동기화·RLS의 근거를 각각 기록한다. 릴리스 보고에는 두 계정 격리와 기록 보존의 확인 결과를 포함한다.
