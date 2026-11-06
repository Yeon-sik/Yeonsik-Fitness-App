# Fitness App — Wearable Common Interface Plan v0.3.1

- **Status:** PROPOSED — 설계 보정 v0.3, PR0 프로브 구현 중(실기기 관측 전)
- **작성일:** 2026-11-04 (Asia/Seoul) · **v0.2 갱신:** 2026-11-04 착수 가능성 검토 반영 · **v0.3 갱신:** 2026-11-04 계정 claim·링크 백업·PR0 게이트·백업 형식 보정 · **v0.3.1 갱신:** 2026-11-05 K-01·K-02 확정, PR0 프로브 코드 추가
- **Repository:** Yeon-sik/Yeonsik-Fitness-App
- **Baseline:** `main@1df259296ce9c21e022edcd0e2d0154c6ff2d343` (v0.2·v0.3 작업 시 `origin/main` HEAD와 동일함을 재확인)
- **문서 대상 브랜치:** `docs/wearable-common-interface-v1` (v0.1) · v0.2·v0.3: `claude/fitness-wearable-docs-review-xqntnb` (v0.3 작업 기준 HEAD `f7a9af68`)
- **작업 유형:** Architecture decision + 단계별 실행 계획. 이 문서 커밋은 구현 완료를 뜻하지 않는다.
- **Source of Truth:** `AGENTS.md`, `docs/FitnessApp_정돈_리팩터링_규칙.md`, 현재 실제 코드. 해당 규칙 문서의 오래된 CURRENT 서술보다 이 문서의 기준 SHA에서 직접 확인한 구현 사실을 우선한다.

## 상태 표기

- **CONFIRMED (v0.3):** 설계로 확정된 결정. 구현·테스트·실기기 검증이 끝났다는 뜻이 아니다.
- **DECISION PENDING:** 권장안은 있지만 아직 확정하지 않은 결정. 표시된 시점의 Task Contract에서 확정한다.
- **UNVERIFIED:** 코드·SDK·기기 동작을 아직 확인하지 못한 사실. 추측으로 채우지 않는다.

## v0.3 → v0.3.1 변경 요약 (2026-11-05)

설계 결정은 바꾸지 않는다. PR0 착수 조건이던 K-01·K-02를 권장안대로 확정하고, 그 결정대로 프로브 코드를 추가했다.

| 항목 | v0.3 | v0.3.1 |
| --- | --- | --- |
| K-01 프로브 위치 | DECISION PENDING | **CONFIRMED:** `app/src/debug/` 전용 소스셋, qa/release에는 권한·의존성 없음 |
| K-02 결과 기록 | DECISION PENDING | **CONFIRMED:** `docs/wearable/PR0_PROBE_RESULT.md`, 분포 요약과 마스킹된 식별자만 |
| PR0 구현 | 미착수 | 프로브 코드·debug manifest·테스트·결과 문서 템플릿 추가(§9). Android 빌드·lint·실기기 실행은 **NOT_RUN** |
| 실기기 설치 경로 | 언급 없음 | debug·qa·release가 같은 applicationId를 쓰는 문제를 발견해 K-13으로 추가(§9, §10.1) |

## v0.2 → v0.3 변경 요약

v0.3은 구현이 아니라 v0.2 설계의 정확성을 보정한다. 근거는 baseline SHA 소스의 정적 검토(v57 Room schema JSON 포함)와 Health Connect 공식 가이드(get-started, sync-data)다. 빌드·단위 테스트·instrumentation·실기기는 **NOT_RUN**이다.

| 항목 | v0.2 | v0.3 |
| --- | --- | --- |
| F-01 계정 claim 검사 | `sqlite_master`의 `user_id` 컬럼 기준 누락 검사 | 실제 소유권 컬럼(`user_id` 34, `owner_id` 5, `scope_key` 1, 없음 1)과 분산된 claim 경로를 확인하고, 컬럼명이 아니라 **명시적 분류 registry** 기준 검사로 교체. 전환 규칙, claim 시 identity 충돌 해소, sync cursor **초기화** 확정(§6.3) |
| F-02 링크 참조 무결성 | 복원 후 pending/needsReview로 보존 | 로컬 PK와 원본 identity 4요소를 분리하고, 링크를 identity로만 참조. `decision`과 `sourceState`를 분리하고 `pendingSource` 등 상태 전이표·수용 기준 추가(§6.1, §6.4) |
| F-03 PR0 PASS | 관심 유형 하나만 확인되면 PASS로 읽힐 수 있음 | 운동 중 HR 시계열을 핵심 게이트(G-HR)로 지정. PASS·PARTIAL·BLOCKED·INCONCLUSIVE 판정표와 판정별 다음 조치 추가(§9) |
| F-04 백업 형식 | formatVersion 2와 별도 섹션 중 미정 | **formatVersion 2** 확정. v1 읽기 유지, 구형 앱의 명시 거부, `tables` 호환 조건, 링크 범위·제외 대상 정의(§6.2) |
| 착수 판정 | PR1~PR4 일괄 착수 불가 | PR0 / PR1-a·PR1-b / PR2 / PR3~PR4 단계별 조건으로 세분화(§12.1) |
| 결정표 | K-01~K-07, 모두 권장안 | 상태 열 추가, K-05·K-06 확정, K-08~K-12 추가(§10.1) |
| 새 구조 관측 | — | S-01~S-03 추가(§6.3, §6.4) |

## v0.1 → v0.2 변경 요약

v0.2는 "바로 구현해도 되는가" 검토 결과를 반영한다. 검토 범위는 baseline SHA 소스의 **정적 검토**와 Health Connect 공식 가이드 확인이며, 빌드·단위 테스트·instrumentation·실기기는 실행하지 않았다(검토 세션에 Android SDK가 없고 `dl.google.com`이 네트워크 정책으로 차단됨).

| 영역 | v0.1 | v0.2 |
| --- | --- | --- |
| 착수 판정 | 명시 없음 | PR1~PR4 착수 불가, PR0만 §10.1 결정 후 착수 (v0.3 §12.1로 대체됨) |
| Cardio 시각 | 완료 시각 컬럼 미확인, pause/resume 복원 여부 Unknown | 코드로 확정: 완료 시각은 이후 수정으로 덮어써지고 pause/resume 구간 이력은 저장되지 않음(§2, §7) |
| 백업 | 고정 TABLE_ORDER 언급 | 복원 시 테이블 집합 **정확 일치** 검증을 근거로 links 저장 형식 제약 명시(§6) |
| 계정 claim | 경로 존재 | 테이블별 수동 나열, 자동 커버리지 검사 미확인 → PR2 테스트 요구(§6) |
| Health Connect manifest | Play Console 선언 일치만 언급 | 공식 가이드 기준 필수 선언 체크리스트 추가(§8) |
| PR0 | 프로브 위치·결과 기록 위치 미정 | 권장안과 결정 항목 명시(§9, §10.1) |
| PR1 | ABI 확인만 언급 | 시각 표현·suspend 브리지 결정 항목 명시(§4, §10.1) |
| 계정 귀속 | 계정 A→B async 경합만 다룸 | Health Connect 데이터가 기기 사용자 단위라는 점에서 계정 귀속 정책 결정 항목 추가(§8, §10.1) |

## 1. Goal / Scope

Fitness App이 Galaxy Fit3 등 외부 웨어러블에서 **운동 관련 관측**을 가져오되, Samsung 전용 API나 특정 제조사에 도메인 모델이 종속되지 않는 공통 읽기 계약을 둔다. 기존 Fitness 운동·유산소·신체 기록의 의미와 로컬 우선 저장을 보존하고, 외부 측정값은 출처·측정 방법·품질을 유지한 채 분리 저장한 뒤 필요한 경우 운동 기록에 연결한다.

**v1 사용자 가치:** (1) 실제 웨어러블 심박 관측을 가져와 유산소 세션과 비교, (2) 수동 입력값 보존, (3) 연결 상태·출처·측정 누락의 가시화.

- **P0 / v1:** 운동 중 심박 시계열, Health Connect 운동 세션(있는 경우), 유산소 기록 연결, 출처 및 품질.
- **P1:** 걸음 수/거리의 일일 활동 집계, 외부 체중·체성분 후보와 기존 Body 기록의 명시적 연결.
- **P2:** 안정 시 심박, HRV, 회복 관련 지표. 데이터 접근 및 임상적 의미·기기 간 비교 가능성을 별도로 검증.
- **별도 경계:** 수면 기록 소유·해석은 HealthTrace. Fitness는 필요할 때 버전 명시된 요약 계약을 소비하며 수면 DB를 복제하지 않는다.
- **Out of scope (v1):** 실시간 Bluetooth/센서 스트리밍, 의료 진단, 자동 운동 세트 생성, 자동 평균 심박 overwrite, background 상시 동기화, 새로운 백엔드 서비스, Apple Watch Android 직접 연결, HealthKit 생산 구현, Supabase/Personal OS로 원시 웨어러블 자동 업로드.

## 2. BASELINE — 직접 관측된 현재 구현 (OBSERVED)

기준: 위 baseline SHA의 GitHub 소스를 읽은 **정적 검토**. 빌드·instrumentation·실기기·운영 검증은 이번 문서 작성에서 **수행하지 않았다**.

| 영역 | 관측 사실 | 근거 |
| --- | --- | --- |
| 앱 아키텍처 | Java/Kotlin, Compose, 단일 Android app, shared KMP, 수동 AppContainer 조립 | `AGENTS.md`, `app/src/main/kotlin/com/yeonsik/fitnessapp/app/AppContainer.kt` |
| Local DB | `fitness_mvp.db`, Room `ROOM_VERSION=57` | `app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessDatabaseContract.kt` |
| Android SDK | minSdk 26 / targetSdk 36 / compileSdk 36 | `app/build.gradle` |
| 유산소 | CardioRepository → Room, CardioSessionApplicationService/CompleteCardio가 Workout과의 완료 작업을 조정 | `feature/cardio/.../CardioRepository.java`, `CardioSessionApplicationService.java`, `CompleteCardio.java` |
| 평균 심박 | `workout_records.average_heart_rate` nullable, 유산소 완료 및 사후 수정 API 존재 | `core/database/FitnessRoomLegacyEntities.kt`, `shared/.../WorkoutRepositoryApi.kt` |
| Cardio 시각 | `cardio_sessions.started_at_epoch_ms`, `last_resumed_at_epoch_ms`, `active_duration_ms`, `updated_at_epoch_ms`; 명시적 완료 시각 컬럼 없음. pause/resume은 `last_resumed_at_epoch_ms` 한 값만 덮어쓰며 구간 이력을 남기지 않음 | `core/database/FitnessRoomLegacyEntities.kt`, `feature/cardio/data/CardioRepository.java` (`pause`/`resume`/`completeSessionInTransaction`) |
| 근력 시각 | `workout_records`는 `date`, `duration_seconds`, `created_at`, `updated_at`만 보유; 운동 시작·종료 시각 없음 | `core/database/FitnessRoomLegacyEntities.kt` |
| 신체 | `BodyMetricsRepository`가 weight record를 owner scope로 관리 | `feature/body/data/BodyMetricsRepository.java` |
| 계정 | `AccountOwnershipService.claimLocalRows`가 단일 transaction에서 anonymous→로그인 계정 로컬 행을 claim. 대상 테이블은 `claimSimpleRows`에 테이블별 DAO 호출로 직접 나열됨. claim은 이전 owner가 anonymous이고 다음 owner가 anonymous가 아닐 때만 실행 | `core/account/AccountOwnershipService.java:44`, `:70`, `config/AccountOwnerPolicy.java` |
| claim 경로 분산 | Fitness 기록 도메인은 `AppContainer.applyAuthenticatedSharedConfig`가 `claimLocalRows`를 호출한 뒤 각 Repository의 owner를 하나씩 바꾼다(claim transaction과 owner 전환이 원자적이지 않음). Nutrition 인증 도메인은 `applyAuthenticatedNutritionConfig` → `NutritionCatalogRepository.normalizeLocalUserId`가 별도 transaction에서 claim하며 승인 링크 충돌을 먼저 해소함 | `app/AppContainer.kt:279-293`, `:318-324`, `feature/nutrition/data/NutritionCatalogRepository.java:258` |
| 소유권 컬럼 (v57) | 41개 테이블: `user_id` 34, `owner_id` 5, `scope_key` 1(`sync_state`), owner 없음 1(`pricetrace_product_cache`). `scope`·`consumer_scope`·`remote_scope`는 계정 소유자가 아닌 분류·범위 값. 분류는 §6.3 | `app/schemas/.../FitnessRoomDatabase/57.json` |
| 기존 sync cursor | `sync_state` PK `(scope_key, table_name, direction)`, `scope_key = supabaseUrl + "\|" + userId`. claim 대상도 백업 대상도 아님 | `integration/personalos/LegacyFitnessSyncAdapter.java:105`, `core/database/FitnessRoomLegacyEntities.kt` |
| 백업 | `LocalDataBackupService`는 고정 `TABLE_ORDER`, `fitness-os.local-backup` formatVersion 1 사용. 복원 시 백업의 테이블 집합이 (DB 버전별 예외를 뺀) 기대 집합과 **정확히 같아야** 하며 다르면 거부 | `backup/LocalDataBackupService.java:42-51`, `validateCompatibleTables` `:629`, `:670` |
| 백업 파싱·복원 | `formatVersion != 1`이면 "Backup format version is not supported."로 거부하고, `databaseVersion > ROOM_VERSION`도 거부. 파싱·검증은 transaction 전에 끝나고, 복원은 단일 transaction 안에서 `TABLE_ORDER` 순서로 `CONFLICT_IGNORE` 삽입(같은 PK의 기존 행 유지). 상한 32 MiB / 250,000행. Room 테이블 41개 중 7개(`devices`, `sync_state`, `exercise_picker_preferences`, `dining_identity_proposals`, `dining_out_menu_add_on_links`, `pricetrace_product_cache`, `verified_receipt_items`)는 `TABLE_ORDER`에 없음 | `LocalDataBackupService.java:44-45`, `:150-211`, `:520-567` |
| 백업 owner 처리 | 내보낼 때 `user_id`·`owner_id`를 빼고, 복원할 때 **컬럼명으로 도메인을 추론**해 `user_id`에는 현재 기록 owner, `owner_id`에는 현재 Nutrition owner를 주입. 둘 다 없는 테이블은 예외 | `LocalDataBackupService.java` `schemaFor` `:904`, `toContentValues` `:829` |
| 참조 무결성 | Room Entity에 `ForeignKey` 선언이 없고 SQL에도 `REFERENCES`가 없음. 테이블 간 참조는 애플리케이션 코드가 유지 | `core/database/*.kt` 정적 검색 |
| 빌드 도구 | AGP 8.11.0, Kotlin 2.3.0, JVM target 17. `kotlinx-coroutines`는 직접 의존성이 아니며 room-ktx·lifecycle-ktx를 통한 전이 의존성만 존재 | `build.gradle`, `app/build.gradle` |
| shared | `commonMain`에 외부 라이브러리 의존성 없음(kotlinx-datetime/coroutines 없음). Android library + iosX64/iosArm64/iosSimulatorArm64 static framework | `shared/build.gradle` |
| 웨어러블 | 현행 검색/확인 범위에서 Health Connect/HealthKit Reader, 웨어러블 persistence 경로 확인되지 않음 | `AndroidManifest.xml`, `app/build.gradle`, 관련 코드 검색 |
| iOS | `iosApp`은 KMP shared host proof이며 HealthKit/production 영속 계층이 아님 | `iosApp/README.md` |

**중요 관측:** Cardio의 `updated_at_epoch_ms`를 곧바로 운동 종료 시각으로 사용하면 안 된다. 완료 시 현재 시각으로 기록되지만(`CardioRepository.completeSessionInTransaction`) 이후 실내 수동 거리 수정(`FitnessRoomDatabase.kt` `updateManualDistance`)이 같은 컬럼을 덮어쓴다. `active_duration_ms`는 일시 정지 구간을 제외한 활동 시간이라 wall-clock 종료 시각과 같지 않고, pause/resume 구간 이력은 저장되지 않는다. 따라서 **현행 저장 구조만으로는 기존 Cardio 기록의 정확한 종료 시각과 일시 정지 구간을 복원할 수 없다**(v0.2 확정, §7·§10 #4).

**기존 canonical 구조 제약:** 화면 → ViewModel → 업무 Service/Repository API → Repository/DAO, Cardio/Workout 저장은 `CompleteCardio`가 조정한다. 외부 연동은 `integration/`에 두고, 도메인 Repository가 직접 다른 기능 DAO를 수정하거나 새 범용 Repository/Hilt/별도 모듈을 만들지 않는다.

## 3. DECISION — 구조

~~~text
Galaxy Fit3 → Samsung Health → Health Connect ─┐
                                                ├─ Android Wearable Adapter
[후속 검증] Samsung Health Data SDK ────────────┘       │
                                                        ▼
shared: Wearable Common Read Contract + Typed Observations
                                                        │
                                                        ▼
app: Wearable Sync Coordinator → Observation Repository → Room
                                                        │
                                    [explicit link / source-aware projection]
                                                        │
                                      Workout / Cardio / Body / Insights
후속 iOS: HealthKit Adapter ────────────────────────────┘ (현재 구현 범위 아님)
~~~

- **D-01:** 플랫폼 API는 Android 전용 adapter가 소유한다. `shared`에는 Android Context/Health Connect 타입/Room Entity를 넣지 않는다.
- **D-02:** 외부 데이터를 기존 `workout_records`/수동 Cardio 평균 심박에 즉시 쓰지 않는다. 독립 관측 저장 + 연결 결정 + 읽기 projection으로 소비한다.
- **D-03:** v1의 1차 transport는 Health Connect. 삼성 SDK는 Fit3 실제 데이터가 빠지거나 메타데이터가 부족함이 확인된 후 재방문한다.
- **D-04:** 같은 원본이 서로 다른 transport로 들어올 경우 시간 중복만으로 같은 사건이라고 확정하지 않는다. v1은 같은 origin에 대해 **한 가지 활성 transport**만 선택하고 기존 경로 관측은 비활성 상태로 보존한다. origin 식별 불가 시 자동 결합하지 않는다.
- **D-05:** Fitness 운동 세션은 canonical. 외부 ExerciseSessionRecord가 들어와도 Fitness 세션을 자동 생성/완료/삭제하지 않는다.
- **D-06:** 초기 동기화는 foreground에서 사용자 명시 실행 또는 화면 진입 시 실행한다. background 상시 서비스와 새로운 원격 backend는 만들지 않는다.
- **D-07:** HealthTrace 수면 TDD v0.4는 **별도 Proposed 문서**다. 모델 원칙을 참조할 수 있으나 구현된 공유 컴포넌트나 DB 의존성으로 취급하지 않는다.
- **D-08:** 기존 `shared` KMP 모듈을 사용한다. 제품 수준의 iOS adapter가 만들어지기 전이라도 공통 타입은 iOS compile-compatible해야 한다. 플랫폼 전용 permission/cursor/token은 Android adapter에만 둔다.

### 왜 하나의 범용 Measurement(value: Double)로 합치지 않는가

심박 sample, 지속 시간 구간, 운동 세션, 하루 걸음 합계는 같은 자료형이 아니다. 단위·시간 범위·집계 방식·출처를 타입별로 표현하며 임의의 0, 빈 목록, null을 서로 바꿔 쓰지 않는다.

## 4. Common Contract v1 (PROPOSED)

권장 책임/경로. 실제 API 시그니처와 패키지는 PR1에서 현재 shared의 Kotlin/Swift ABI 제약을 확인한 뒤 동일 의미를 보존하며 확정한다.

~~~text
shared/src/commonMain/.../wearable/
  model/
    WearableCapability
    ObservationProvenance
    WearableObservation (typed: HeartRateSeries / ExerciseSession / ...)
    ObservationReadRequest / ObservationReadResult
  api/
    WearableObservationReader

app/src/main/.../integration/wearable/
  healthconnect/
    HealthConnectObservationReader
    HealthConnectPermissionGateway
    HealthConnectObservationMapper
  data/
    WearableObservationRepository
    WearableSyncCoordinator
~~~

`WearableObservationReader` 역할: capability 조회, 기간 및 타입에 대한 read-only 조회, 페이지/부분 실패 처리. 권한 요청 화면·Health Connect 설정 전환은 Android의 `PermissionGateway`가 담당하고, 공통 API에는 플랫폼 Permission 객체를 노출하지 않는다.

**비동기 경계 (PR1 결정):** Health Connect client는 suspend API다. 앱에는 `kotlinx-coroutines` 직접 의존성이 없고 Cardio·Workout 조정 계층은 Java다. PR1은 (1) Android adapter에 coroutines를 명시 의존성으로 추가할지, (2) Java `ApplicationService`에서 호출할 브리지(콜백/Executor/Future)를 무엇으로 할지, (3) 공통 `WearableObservationReader`를 suspend로 둘지(Swift export 영향 포함)를 확정한다. 공통 API에 coroutines 타입을 노출하려면 `shared` 의존성 추가가 함께 필요하다.

`ObservationReadResult`: `Success(records, nextPage?, complete)`, `PermissionRequired`, `Unavailable`, `Unsupported`, `Failure(retryable, reason)`처럼 **빈 성공·권한 없음·미지원·부분 실패**를 구별한다. 중간 페이지만 성공했을 때 전체 성공으로 표시하지 않는다.

`ObservationProvenance` 최소 필드:

| 필드 | 의미 |
| --- | --- |
| `transport` | 전달 경로: `health_connect` 등 |
| `originPackage` | 데이터를 Health Connect에 작성한 앱 패키지. 센서 모델을 뜻하지 않음 |
| `providerRecordId` / `dataType` | 공급자 기록 identity / 타입 |
| `recordingMethod` | 제공되는 경우 raw recording method |
| `deviceType` / `deviceModel` | 제공되는 경우. 없으면 unknown, Fit3이라고 추정 금지 |
| `sourceUpdatedAt` / `importedAt` | 원본 수정 시각 / 수집 시각 |
| `observedStartAt` / `observedEndAt` | UTC 절대 시각, 유효 구간 검증 |
| `sourceZoneOffset` | 원본에서 알 수 있는 경우 시간대 정보 |
| `sourceClassification` | sensorObserved / sensorInferred / userEntered / unknown. 원본 provenance에서 계산하는 버전 관리된 분류 |
| `qualityFlags` | 출처 불확실, 관측 공백, 부분 수집 등 |

실제 source record ID가 미존재/불안정한 provider를 발견하면 임의의 시각 해시로 canonical identity를 확정하지 말고 PR0 결과를 근거로 별도 identity 규칙을 결정한다.

**Source identity (v0.3):** 외부 원본의 안정 identity는 `(transport, originPackage, dataType, providerRecordId)` 4요소이며 로컬 저장 PK와 구분한다(§6.1).
- `transport`와 `dataType`은 공통 계약의 안정 문자열 enum이다. 플랫폼 클래스명을 쓰지 않으며, 값 목록은 PR1에서 확정한다.
- Health Connect의 `providerRecordId`는 HC record의 `metadata.id`다. 공식 sync 가이드상 `DeletionChange`는 record id만 제공하므로 삭제를 처리하려면 이 값을 저장해야 한다.
- `clientRecordId`·`clientRecordVersion`은 작성 앱이 넣는 보조 메타데이터로 기록만 하고 identity로 쓰지 않는다.
- **UNVERIFIED:** `metadata.id`가 Samsung Health 재동기화, 앱 재설치, 기기 변경 뒤에도 유지되는지는 PR0에서 관측한다(K-09).

**시각 규칙:** 저장·매칭은 UTC Instant 기반, 표시 날짜는 명시적 time zone 정책으로 계산한다. `date` 문자열만으로 운동과 관측의 동일성을 판정하지 않는다. start/end timezone offset은 확인 가능한 경우 원본 그대로 보존한다. `shared` commonMain에는 날짜·시각 라이브러리가 없으므로 공통 타입의 Instant 표현(UTC epoch millis `Long` 또는 `kotlin.time.Instant`)은 PR1에서 Kotlin 2.3.0 안정성·Swift 노출 형태를 확인해 하나로 확정한다. 기존 Cardio 컬럼이 epoch millis라는 점을 고려한다.

**측정 방법:** `RECORDING_METHOD_AUTOMATICALLY_RECORDED` 같은 메타데이터 자체가 Fit3 장치를 확증하지 않는다. `unknown`은 관측을 보존하되 센서 전용 비교에서 별도 취급한다. 분류 규칙 변경 시 알고리즘 버전을 올린다.

## 5. 데이터 소유권 / Integration boundary

| 정보 | 책임 |
| --- | --- |
| 세트/루틴/운동 완료/유산소/GPS 및 수동 평균 HR | Fitness Training/Cardio |
| 웨어러블 측정 원본 | 기기 및 원본 제공 앱/플랫폼 |
| Fitness 앱 내 로컬 외부 관측 cache, 수집 상태 | Wearable Integration |
| 수동 운동과 외부 관측의 사용자 연결 결정 | Fitness App |
| 체중 등 canonical Body 기록 | Fitness Body; 외부 관측 수입만으로 canonical 등록하지 않음 |
| 수면 원본·수면 분석 | HealthTrace |
| Personal OS 일일 완료 요약 | Fitness가 발행한 허용 Projection만 |

- 외부 record가 삭제돼도 기존 Fitness 운동 기록/세트는 삭제하지 않는다.
- Fitness의 외부 관측 cache와 타 앱의 DB를 직접 공유하지 않는다.
- Personal OS 또는 Supabase에 원시 웨어러블/심박 시계열을 자동 전송하지 않는다. 별도 versioned contract와 동의가 필요하다.
- 운동에 연결된 외부 측정이 바뀌거나 사라지면 읽기 projection에서 `needsReview` 또는 unavailable 상태로 나타내며 이전 수동 값은 보존한다.

## 6. Room Persistence & Sync (PR2 제안; PR0 gate 이후 확정)

현재 schema v57. PR2 착수 당시 최신 버전을 다시 확인하고 다음 버전 migration을 추가한다. **v58은 v57이 여전히 HEAD일 때에만 사용한다.**

권장 논리 모델 (테이블·컬럼 이름과 DDL은 migration 검토 시 확정. 단 owner 컬럼은 K-08로 확정):

- **owner (CONFIRMED, K-08):** 문서의 논리 `ownerId`는 Fitness 기록 도메인(`AccountScope.ownerId`, `AccountOwnershipService` claim 대상) 소유자다. 물리 컬럼은 기존 Fitness 기록 테이블과 같은 `user_id`를 쓴다. `owner_id`는 Nutrition 인증 도메인 관례이고, 기존 백업 `schemaFor`가 `owner_id`에 Nutrition owner를 주입하므로(S-02) wearable 테이블에 쓰지 않는다.
- `wearable_observation_records`:
  - 로컬 surrogate PK `id`는 기기 로컬 값이다. export하지 않고 cache 밖에서 영속 참조하지 않는다.
  - 컬럼: owner, source identity 4요소(§6.1), UTC interval, `sourceUpdatedAt`·revision, source metadata, `importedAt`, `deletedAt`(tombstone).
  - `UNIQUE(user_id, transport, originPackage, dataType, providerRecordId)`. 원본이 갱신되면 같은 행을 update해 로컬 PK를 유지한다.
- `wearable_heart_rate_samples`: 부모 observation 로컬 PK(cache 내부 참조), source sample sequence/time, bpm, optional quality. 부모를 갱신할 때 같은 transaction에서 samples를 교체할 수 있다. 다른 도메인은 sample PK를 참조하지 않는다. cache 내부 참조에 SQLite FK를 쓸지는 K-10.
- `wearable_sync_states`: `(user_id, transport, dataType)`의 changes token/cursor, bootstrap 상태, lastReadAt, lastSuccessAt, lastError. 백업하지 않고, claim 때 승계하지 않고 초기화한다(K-11). 세부 origin 분리가 필요하면 먼저 이 조건을 검증한다.
- **연결 동의 상태:** `(user_id, transport)` 단위로 계정별 연결 동의·중지를 저장한다(K-05). cursor 초기화로 지워지면 안 된다. 테이블 배치는 PR2에서 확정(**DECISION PENDING**).
- `wearable_workout_links`:
  - 컬럼: 로컬 PK, owner, `workout_record_id`(Fitness canonical `workout_records.id`), source identity 4요소(**cache 로컬 PK 아님**), `decision`, `sourceState`, `decidedAt`, verification snapshot(`observedStartAt`·`observedEndAt`, 결정 시점의 원본 구간).
  - `decision ∈ {candidate, userConfirmed, userRejected}`, `sourceState ∈ {resolved, pendingSource, sourceDeleted, identityConflict}`.
  - owner당 같은 source identity의 `userConfirmed` 링크는 최대 1개다. 한 운동에 여러 source identity를 연결하는 것은 허용한다.
  - `needsReview`는 저장 상태가 아니라 projection 상태다: `decision = userConfirmed`이고 `sourceState ∈ {sourceDeleted, identityConflict}`. 사용자 결정과 원본 가용성을 분리해, 원본이 없어도 결정이 사라지지 않게 한다.

**쓰기/동기화 불변식:**
1. 외부 원본 중복 import는 동일 key에 대한 upsert여야 한다. `INSERT OR REPLACE`로 row identity/FK를 파괴하지 않는다.
2. record + samples 갱신 + 성공 cursor 갱신은 적절한 transaction에 묶는다. 페이지가 일부 실패하면 성공 cursor를 앞당기지 않는다.
3. 변경 피드에서 삭제가 오면(`DeletionChange`는 record id만 제공) tombstone 처리하고, 해당 identity 링크의 `sourceState`를 `sourceDeleted`로 바꾼다. `userConfirmed`이면 projection은 `needsReview`가 되고, `candidate`는 제거한다. 사용자가 기록 삭제를 선택하면 cache/links 삭제 범위를 명시적으로 확인한다.
4. 새 수집 결과가 오래된 계정/권한 상태로 도착하면 폐기한다. 계정 A → B 전환 시 A의 async import가 B 소유 DB·UI에 반영되지 않아야 한다. 모든 wearable 쓰기 transaction은 작업 시작 때 캡처한 `AccountScope.ownerId`가 commit 직전의 현재 owner와 같은지 **transaction 안에서** 확인하고, 다르면 rollback·폐기한다. Repository의 가변 owner 필드만으로 판단하지 않는다(S-01).
5. 같은 기록의 Samsung SDK/Health Connect 중복을 시간 중복만으로 소거하지 않는다. origin별 활성 transport 지정 및 변경 이력 처리.
6. permissions revoke는 추후 읽기를 중단하되 사용자가 이미 가져온 로컬 cache를 무단 삭제하지 않는다. 별도의 '가져온 데이터 삭제' 명령을 제공한다.
7. offline/권한 거절/미지원/빈 데이터/일부 성공을 구분한다. 재시도 idempotent.
8. 첫 동기화는 허가된 접근 범위만 요청한다. 최초 권한 이전 30일보다 오래된 데이터를 읽으려면 별도 history permission이 필요할 수 있다.
9. 용량: 시계열 record/sample 수집량과 로컬 DB 크기 상한·기간 및 pruning 정책을 Fit3 관측 결과로 결정한다. v1에서 무제한 백그라운드 수집을 허용하지 않는다.
10. 링크, 백업, 다른 도메인은 cache 로컬 PK를 영속 참조하지 않는다. 외부 원본 참조는 owner + source identity 4요소로만 한다.
11. partial 읽기, 권한 없음, 미지원, HC 사용 불가 결과에서 원본이 보이지 않는 것을 삭제 증거로 쓰지 않는다. 삭제 판정 근거는 두 가지뿐이다.
    - `DeletionChange`
    - complete로 끝난 구간 재조회에서 identity가 없음. 단 그 구간이 현재 읽기 가능 범위(history 권한이 없으면 최근 30일) 안에 있을 때만 유효하다.

**Backup/restore gate (필수):**
- 새 wearable 테이블이 자동으로 백업된다고 가정하지 않는다.
- 기기에서 다시 수집할 수 있는 raw cache와 sync token은 export하지 않는다.
- 사용자 링크 결정은 formatVersion 2로 보존한다(§6.2).
- 복원 후 raw가 없으면 링크는 `pendingSource`로 보존하고, 재수집 후 같은 identity에만 복구한다(§6.1).
- 이 정책을 실제로 구현할 수 없으면 PR2 merge를 보류한다. 백업 v1 reader의 의미는 바꾸지 않는다.

### 6.1 원본 identity와 링크 참조 (F-02, CONFIRMED 설계 / identity 안정성 UNVERIFIED)

| 계층 | 값 | 범위 | 백업 | 다른 행이 참조해도 되는가 |
| --- | --- | --- | --- | --- |
| 로컬 surrogate PK | `wearable_observation_records.id` 등 | 이 기기의 이 DB | 제외 | cache 내부(samples→observation)만 |
| Source identity | `(transport, originPackage, dataType, providerRecordId)` | 원본 공급자 기준. 저장 시 owner와 함께 유일 | 링크 결정 행 안에 포함 | 예, owner와 함께 |
| Fitness canonical ID | `workout_records.id` | Fitness 기록 도메인 | `tables`에 포함(기존) | 예 |

**보존 규칙**
- 4요소는 원본 값을 그대로 저장한다. 대소문자·공백 정규화나 값 재작성을 하지 않는다.
- 4요소 중 하나라도 없거나, PR0에서 불안정하다고 판정된 값이면 그 관측은 cache에 저장할 수 있다. 하지만 `userConfirmed` 링크 대상이 될 수 없고 백업되지 않는다.
- 시각 해시나 내용 해시로 대체 identity를 만들지 않는다. identity를 확정할 수 없으면 K-09를 PR0 이후 결정으로 남긴다.
- **UNVERIFIED:** Health Connect `metadata.id`가 Samsung Health 재동기화, 앱 재설치, 기기 변경·HC 데이터 이전 뒤에도 유지되는가. 유지되지 않으면 새 기기에서는 복원된 링크가 영구히 `pendingSource`로 남을 수 있다. 이것은 허용되는 결과이며, 사용자가 해제할 수 있어야 한다.

**링크 상태 전이**

| 상황 | 처리 | 결과 `sourceState` |
| --- | --- | --- |
| 복원 시 현재 owner cache에 같은 identity가 있고 snapshot 구간이 겹침 | 백업 결정 적용 | `resolved` |
| 복원 시 현재 owner cache에 같은 identity가 없음 | 결정 보존 | `pendingSource` |
| 재수집에서 현재 owner·같은 identity 발견, snapshot 구간 겹침 | 보존한 결정으로 복구 | `resolved` |
| 같은 identity인데 snapshot 구간이 전혀 겹치지 않음(ID 충돌·재사용 의심) | 자동 복구 금지 | `identityConflict` (`userConfirmed`이면 projection `needsReview`) |
| 재수집 실패(권한 없음, 미지원, HC 사용 불가, partial) | 변경 없음, 다음 동기화에서 재시도 | `pendingSource` 유지 |
| 원본 삭제(불변식 11의 근거) | 결정 보존, 운동 기록·수동 HR 불변 | `sourceDeleted` |
| 원본 구간이 읽기 가능 범위 밖(예: history 권한 없이 30일 초과) | 판정 불가 | `pendingSource` 유지. UI는 "확인 불가"로 표시 |
| 다른 owner의 cache에만 같은 identity가 있음 | 매칭 금지 | `pendingSource` 유지 |
| 복원 링크의 `workout_record_id`가 현재 owner의 `workout_records`에 없음 | 링크를 복원하지 않고 skipped로 집계 | — |
| 복원 링크와 로컬 링크가 같은 identity·같은 결정·같은 운동 | 중복 제거 | 로컬 상태 유지 |
| 복원 링크와 로컬 링크가 같은 identity인데 결정 또는 운동이 다름 | 로컬 유지(기존 `CONFLICT_IGNORE` 의미와 동일), 백업 결정 미적용, 충돌 건수를 복원 결과에 보고 | 로컬 상태 유지 |

- snapshot 구간 비교는 identity 판정이 아니다. 같은 ID가 다른 사건을 가리키는 경우를 막는 보호 장치다. identity는 4요소가 정확히 같을 때만 같다고 보며, 시간 겹침만으로 identity를 정하지 않는 D-04 원칙은 그대로다.
- `pendingSource`는 자동 만료하지 않는다. 사용자 결정을 시스템이 지우지 않으며, 사용자가 명시적으로 해제할 수 있다.

### 6.2 Backup formatVersion 2 (F-04, CONFIRMED 설계 / 코드 변경 없음)

v1 코드 사실은 §2에 있다: `formatVersion`·`databaseVersion` 사전 거부, `TABLE_ORDER` 집합 정확 일치, 컬럼명 기반 owner 주입, 단일 transaction `CONFLICT_IGNORE`. 이를 기준으로 PR2는 아래 형식을 구현한다.

~~~text
{
  "format": "fitness-os.local-backup",
  "formatVersion": 2,
  "databaseVersion": <ROOM_VERSION>,
  "exportedAt": "<ISO-8601>",
  "tables": { ...v1과 같은 TABLE_ORDER 계약... },
  "wearableLinkDecisions": {
    "version": 1,
    "rows": [
      { "workoutRecordId", "transport", "originPackage", "dataType", "providerRecordId",
        "decision", "decidedAt", "observedStartAt", "observedEndAt" }
    ]
  }
}
~~~

1. **v1 읽기 유지:** 새 앱은 formatVersion 1과 2를 모두 읽는다. v1 경로의 파싱·검증·복원 의미는 바꾸지 않으며, v1 복원은 wearable 링크를 만들거나 바꾸지 않는다. 기존 v1 fixture 복원 결과가 바뀌지 않는지 회귀 테스트로 고정한다.
2. **v2 쓰기:** PR2가 들어간 릴리스부터 `writeBackup`은 항상 v2를 쓴다. 링크가 0건이어도 `rows: []`로 쓴다. 링크 유무에 따라 v1/v2를 골라 쓰는 방식은 출력 형식이 둘로 늘어 쓰지 않는다(§11).
3. **구형 앱 명시 거부:** 현행·구형 앱의 `parseBackup`은 `formatVersion != 1`을 transaction 전에 "Backup format version is not supported."로 거부하므로 DB는 바뀌지 않는다. 이는 정적 확인 결과이며 PR2에서 v57 파서 fixture 테스트로 고정한다. 새 v2 reader는 `formatVersion > 2`를 거부한다.
4. **`tables` 호환 조건:** v2의 `tables`는 v1과 똑같이 `TABLE_ORDER`, `validateCompatibleTables`(DB 버전별 예외 포함), `validateRows`, owner 주입 규칙을 따른다.
   - wearable observation·sample·sync state·동의·링크 테이블은 `TABLE_ORDER`에 넣지 않는다. 의도된 제외임을 테스트의 명시적 제외 목록으로 고정한다.
   - 이후 추가되는 핵심 테이블은 형식 버전과 무관하게 기존 `databaseVersion < N` 예외 방식을 따른다.
5. **링크 백업 범위:**
   - `decision ∈ {userConfirmed, userRejected}`이면서 source identity 4요소가 모두 있는 행만 백업한다.
   - `userRejected`를 포함하는 이유는 복원 뒤 같은 후보를 다시 제안하지 않기 위해서다.
   - `candidate`는 다시 계산할 수 있으므로 제외한다.
   - `sourceState`는 저장하지 않고, 복원할 때 다시 판정한다(`resolved` 또는 `pendingSource`).
6. **owner:** 링크 행에는 owner를 넣지 않는다. 복원 시 `tables`의 `user_id` 주입과 같은 현재 Fitness 기록 owner를 주입하며, public scope marker는 허용하지 않는다.
7. **기본 제외:** raw observation, HR sample, sync state·changes token, 연결 동의 상태는 백업하지 않는다. 기존 `sync_state` 제외 선례와 같다. 새 기기에서는 다시 동의하고 다시 수집한다.
8. **검증 실패 시 파일 전체 거부:** v2에서 다음 중 하나라도 있으면 transaction 전에 파일 전체를 거부한다.
   - `wearableLinkDecisions` 누락, 또는 `version ≠ 1`
   - 알 수 없는 최상위 키·행 키
   - 필수 필드 누락, 잘못된 enum
   - `observedEndAt < observedStartAt`

   링크 행 수도 250,000행 상한에, 파일 전체는 32 MiB 상한에 포함한다.
9. **원자성:** `tables` 복원 후 링크 복원을 같은 DB transaction 안에서 수행한다(기존 `restoreBackup` transaction 확장). 어느 단계든 실패하면 전체를 rollback한다. 복원 중에는 현재 cache에 이미 있는 identity만 즉시 판정하고, 나머지 재매칭은 이후 동기화 transaction에서 한다.
10. **병합 의미와 결과 보고:** 기존 행 우선(`CONFLICT_IGNORE`) 의미를 따른다. 링크 충돌·skipped 건수는 복원 결과에 따로 집계한다. 화면 문구와 UI 노출은 PR4(**DECISION PENDING**).

### 6.3 Account claim / 계정 귀속 (F-01)

**현행 소유권 분류 (v57, 정적 확인).** 컬럼명만으로는 도메인을 알 수 없다. `owner_id`는 Nutrition 인증 도메인 테이블이거나 claim 경로가 없는 테이블이고, `user_id`인 `devices`는 claim하지 않고 다시 만든다.

| 분류 | 소유 컬럼 | 테이블 | 현행 정책 |
| --- | --- | --- | --- |
| Fitness 기록 도메인 | `user_id` | 33개 (`workout_*`, `meal_*`, `cardio_*`, `routine*`, `supplement_*`, `composition_*`, `weight_records`, `dining_out_menu_*_links`, `exercise_picker_preferences`, `verified_receipt_items`, `body_profiles`, `nutrition_goals`, `nutrition_daily_checkins`, `development_goals`. 이름에 nutrition이 있어도 이 도메인임) | `AccountOwnershipService.claimLocalRows`, 단일 transaction. 이 중 `workout_setup_notes`, `nutrition_goals`, `body_profiles`, `development_goals`, `nutrition_daily_checkins`, `meal_menu_presets`는 claim 뒤 남은 anonymous 행을 삭제 |
| 기기 등록 | `user_id` | `devices` | claim하지 않음. anonymous 행 삭제 후 `ensureDevice`로 재생성 |
| Nutrition 인증 도메인 | `owner_id` | `nutrition_foods`, `nutrition_food_nutrients`, `nutrition_food_components`, `product_nutrition_links` | `NutritionCatalogRepository.normalizeLocalUserId`. Nutrition 로그인 시 별도 transaction, 승인 링크 충돌을 먼저 해소 |
| 원격 동기화 cursor | `scope_key` | `sync_state` | claim·백업 없음. 새 owner는 새 scope key를 씀 |
| owner 없음 | — | `pricetrace_product_cache` | 공용 cache |
| 분류 미확인 | `owner_id` + `remote_scope` | `dining_identity_proposals` | claim·백업 경로 모두 없음. 의도인지 누락인지 **UNVERIFIED**(wearable 범위 밖, 별도 확인) |

**검사 계약 (CONFIRMED, v0.2의 `user_id` 컬럼 기준 검사를 대체)**
- **C-1 Registry:** 테스트 소스에 모든 Room 테이블의 명시적 분류 registry를 둔다. 항목은 `ownerColumn`, `ownerDomain`(fitnessRecord / nutrition / remoteSyncScope / deviceRegistry / none), `claimPolicy`(claim / claimThenDeleteAnonymous / deleteAndRecreate / resetOnClaim / notOwnerScoped)와 사유다.
- **C-2 완전성:** 현재 Room exported schema의 테이블 집합과 registry 키 집합이 정확히 같아야 한다. 분류 없는 새 테이블이 생기면 테스트가 실패한다.
  - 컬럼명은 교차 확인에만 쓴다. 예: `user_id`·`owner_id`·`scope_key` 등을 가진 테이블을 `notOwnerScoped`로 두려면 사유를 적어야 한다.
  - 컬럼명으로 도메인이나 정책을 자동 추론하지 않는다.
- **C-3 동작:** 정책별 instrumentation 테스트에서 anonymous·대상 owner·제3 owner 행을 심는다. 그리고 실제 진입점(`claimLocalRows`, 해당하면 `NutritionCatalogRepository.normalizeLocalUserId`)을 실행해 다음을 확인한다.
  - anonymous 행이 정책대로 옮겨지고, 문서화된 삭제 외에는 행 수가 줄지 않는다.
  - 제3 owner 행은 바뀌지 않는다.
  - claim 대상 테이블에 anonymous 잔여 행이 없다.
  - 두 번째 실행은 아무것도 바꾸지 않는다.
  - 중간에 실패를 주입하면 전체가 rollback된다.
- **C-4 wearable 등록:**
  - observation·sample·link·연결 동의는 `fitnessRecord` + claim이며, 아래 충돌 해소를 적용한다.
  - `wearable_sync_states`는 `fitnessRecord` + `resetOnClaim`이다.

**전환 규칙 (CONFIRMED)**

| 전환 | 처리 |
| --- | --- |
| anonymous → authenticated (`shouldClaimLocalRows` true) | (1) wearable sync coordinator를 멈추고 진행 중인 작업을 취소한 뒤 끝날 때까지 기다린다. (2) 기존 `claimLocalRows` transaction 안에서 observation·sample·link·동의를 claim하고 `wearable_sync_states`의 anonymous 행을 삭제한다. (3) owner 전환이 끝난 뒤 새 scope로 동기화를 재개하고 bootstrap 재조회를 한다. claim 대상 데이터는 anonymous 사용자가 곧 로그인 사용자라는 기존 정책을 따른다. |
| authenticated A → authenticated B (claim 없음) | A의 cache·link·동의를 옮기거나 노출하지 않는다. B는 B의 동의 전까지 동기화하지 않으며 cursor도 없다. |
| logout (A → anonymous) | 동기화를 멈춘다. anonymous는 자체 동의 전까지 수집하지 않는다. A의 데이터는 A 소유로 남는다. |
| anonymous가 동의 후 수집한 데이터 | 다음 로그인 계정의 claim 대상이다(기존 기록과 같은 정책). 공유 기기에서의 위험도 기존 수동 기록과 같으며, 별도 완화는 범위 밖이다. |

**유실·노출 방지 조건**
- **claim 시 identity 충돌:** 대상 owner에 같은 `(transport, originPackage, dataType, providerRecordId)` 행이 이미 있을 수 있다. 예: 같은 기기에서 B로 동기화한 뒤 로그아웃하고, anonymous로 동의·수집한 다음 다시 B로 로그인한 경우.
  - 이때 일괄 `UPDATE ... SET user_id`를 하면 UNIQUE 위반으로 claim transaction 전체(모든 테이블)가 실패한다.
  - 그래서 UPDATE 전에 충돌을 해소한다. observation은 `sourceUpdatedAt`이 더 최신인 행을 남기고(같으면 대상 owner 행), 다른 쪽 행과 그 samples를 삭제한다. 다시 수집할 수 있는 cache이므로 삭제해도 된다.
  - link는 identity별로 `decidedAt`이 더 최신인 결정을 남긴다(같으면 대상 owner). `userConfirmed`가 identity당 1개라는 조건을 유지한다.
  - 기존 선례: `NutritionCatalogRepository.resolveApprovedLinkClaimConflicts`.
  - link는 cache 로컬 PK가 아니라 identity로 참조하므로, observation 행이 바뀌어도 link는 깨지지 않는다.
- **S-01 비원자적 owner 전환:** `applyAuthenticatedSharedConfig`는 claim transaction 뒤에 Repository owner를 하나씩 바꾼다.
  - 그 사이에 anonymous scope로 비동기 쓰기가 commit되면, claim되지 않은 anonymous 행이 남는다. 이 행은 나중의 다른 로그인 계정에 claim될 수 있다.
  - 그래서 coordinator를 claim 전에 멈추는 것과, 쓰기 transaction 안에서 scope를 확인하는 것(불변식 4)을 **둘 다** 요구한다.
- **UNVERIFIED:** `claimLocalRows`가 예외를 던질 때 로그인 흐름이 계속 진행되는지(`applyAuthenticatedSharedConfig` 호출자의 예외 처리). PR2에서 확인하고, 계속 진행된다면 wearable 동기화를 재개하지 않는다.

**sync cursor 결정 (CONFIRMED, K-11): claim 시 승계하지 않고 초기화한다.**
- 근거 1, 기존 선례: `sync_state`는 `supabaseUrl|userId` 범위로 저장되며 claim·백업 대상이 아니다.
- 근거 2, 복구 경로 통일: 공식 sync 가이드상 쓰지 않은 changes token은 30일 안에 만료되며, 권장 복구 방법은 마지막 읽기 시각 이후 또는 최근 30일 재조회 뒤 identifier로 dedupe하는 것이다. claim 후 초기화도 이 token 만료 복구 경로를 그대로 재사용하므로, 검증할 경로가 하나로 줄어든다.
- 근거 3, 중복 방지: claim 후 재조회한 결과는 `(user_id, 4요소)` upsert로 claim된 행과 합쳐지므로 중복이 생기지 않는다.
- 한계: 재조회 범위 밖에서 일어난 원본 삭제는 감지하지 못할 수 있다. 그런 행은 마지막으로 알던 상태로 남고, 링크 결정은 유지된다. token 만료 때와 같은 한계로 수용한다.
- bootstrap 재조회 범위의 구체값은 PR0 데이터량을 보고 PR2에서 확정한다. 기본값은 현재 읽기 가능 범위이며, history 권한이 없으면 최근 30일이다.

### 6.4 PR2 수용 기준 (Acceptance)

**구조 관측**
- **S-02 컬럼명 기반 owner 주입:** 기존 백업 `schemaFor`는 컬럼명으로 owner 도메인을 추론한다. wearable 테이블에 `owner_id`를 쓰거나 `TABLE_ORDER`에 넣으면 잘못된 owner가 주입된다. K-08과 §6.2-4로 차단한다.
- **S-03 SQLite FK 미사용:** 현행 DB는 SQLite FOREIGN KEY 제약을 쓰지 않는다. 그래서 아래 무결성 조건은 테스트 query로 검증한다. cache 내부에 FK를 도입할지는 K-10이고, 링크는 어떤 경우에도 cache에 FK를 걸지 않는다.

**Claim**
- **AC-C1:** C-2 registry 완전성 테스트 통과.
- **AC-C2:** C-3 정책별 동작 테스트 통과(제3 owner 불변, 재실행 무변경, 실패 주입 시 rollback).
- **AC-C3:** claim 시 wearable identity 충돌이 claim 전체를 실패시키지 않고 위 규칙대로 해소되며, identity당 `userConfirmed` 1개가 유지된다.
- **AC-C4:** claim 후 sync state가 초기화되고, bootstrap 재조회 뒤 observation 중복 행이 0이다.
- **AC-C5:** owner 전환 전에 캡처한 scope로 시도한 쓰기는 transaction 안에서 거부되고, claim 이후 anonymous wearable 행이 새로 생기지 않는다.

**Backup / restore**
- **AC-B1:** 기존 v1 fixture(DB v57 이하)를 새 앱에서 복원한 결과가 PR2 이전과 행 단위로 같다.
- **AC-B2:** raw cache 없이 링크만 있는 v2를 복원하면 링크는 모두 `pendingSource`이고, `workout_records`와 수동 평균 HR은 바뀌지 않는다.
- **AC-B3:** 재수집으로 같은 owner·같은 identity가 들어오면 보존한 결정으로 복구된다. 다른 owner cache에만 있으면 `pendingSource`로 남는다.
- **AC-B4:** 같은 identity인데 snapshot 구간이 겹치지 않으면 `identityConflict`이고 자동 확정되지 않는다.
- **AC-B5:** `DeletionChange`가 오면 `sourceDeleted`가 된다. partial 읽기나 권한 없음은 상태를 바꾸지 않는다.
- **AC-B6:** 로컬 링크와 충돌하면 로컬 링크가 유지되고, 충돌 건수가 보고되며, identity당 `userConfirmed` 중복이 없다.
- **AC-B7:** 손상된 v2(필수 필드 누락, 알 수 없는 enum·키·섹션 version)는 파일 전체가 거부되고, 복원 전후 DB 행 수와 내용이 같다.
- **AC-B8:** `tables` 복원 뒤 링크 단계에서 실패를 주입하면 아무것도 commit되지 않는다.
- **AC-B9:** v57 파서(현행 `parseBackup`)가 v2 파일을 "Backup format version is not supported."로 거부한다.
- **AC-B10:** v2 파일에 raw observation, sample, sync state, 동의 상태가 없다.
- **AC-B11:** 현재 owner에 없는 `workout_record_id`를 가리키는 링크는 복원되지 않고 skipped로 집계된다.

**무결성 query (각 시나리오 commit 뒤 실행)**
- **I-1:** 모든 링크의 `workout_record_id`가 같은 owner의 `workout_records` 행을 가리킨다(`deleted_at` tombstone 포함).
- **I-2:** `(owner, identity)`당 `userConfirmed` 링크가 최대 1개다.
- **I-3:** 부모 observation이 없거나 owner가 다른 sample이 0이다.
- **I-4:** cache 테이블 밖에서 cache 로컬 PK를 저장한 컬럼이 없다(스키마 검사).
- **I-5:** `sourceState = resolved`인 링크에는 같은 owner·identity의 삭제되지 않은 observation이 있다.

## 7. 운동 연결 규칙 / 분석 규칙 (PR3–PR4)

**v1은 사용자 확인 연결을 우선한다.** 후보 탐색 기준은 기록 ID + 시간 겹침 + activity type 보조 정보이며, 날짜 일치만으로 auto-confirm하지 않는다.

- Cardio 세션 시작 시각은 현재 존재한다. 완료 시각과 중지·재개 구간은 현행 구조에 영속되지 않음이 v0.2에서 확인됐다(§2). `updated_at_epoch_ms` 또는 `start+activeDuration`을 종료 시각으로 대체하지 않는다. 실외 세션의 `cardio_route_points.captured_at_epoch_ms`는 관측 범위의 보조 근거일 뿐 종료 시각이 아니며, 실내 세션에는 없다.
- 따라서 PR3의 시간 기반 자동 후보 제시는 완료 시각(및 필요하면 pause 구간)을 기록하는 별도 migration을 전제로 한다. 그 migration 이전에 만들어진 기존 Cardio 기록은 사용자 선택으로만 연결한다. migration 여부·범위는 PR3 착수 시 별도로 결정하며 PR2 migration에 섞지 않는다.
- 근력 세션은 시간 정보가 제한적이면 v1 자동 후보 연결에서 제외한다. `workout_records.date`만으로 HR을 결합하지 않는다.
- 기존 `workout_records.average_heart_rate`의 null·수동 수정·legacy 동기화 의미를 변경하지 않는다.
- 연결된 HR time series의 평균·최대는 sensor-derived projection으로 제공하고 `value`, `source`, `method`, `algorithmVersion`, `qualityFlags`를 동반한다.
- 샘플 사이의 큰 공백/운동과 겹치는 실제 관측 구간을 확인하지 않은 채 '운동 평균 HR'로 표시하지 않는다. coverage 정의·최소 표본 기준은 Fit3 실제 sample 분포로 PR0/PR4에서 결정한다.
- 중복/원본 경로 전환/센서 모델 차이와 알고리즘 버전이 바뀌는 경우 분석 시계열을 조용히 이어 붙이지 않는다.
- P1 걸음 수 합계는 복수 원본 raw steps 합산을 기본값으로 사용하지 않는다. Health Connect aggregate API 및 우선순위 정책을 평가한다(중복 집계 방지).
- 수면/HRV 데이터와 운동 수행 간 상관은 인과로 표현하지 않는다.

## 8. 권한 / Privacy / UX

- Health Connect 권한은 필요한 데이터 타입의 **read-only**로 최소 요청한다. manifest, 시스템 권한 요청, Play Console 선언 일치 여부를 PR0/PR1에서 확인한다.
- **Manifest 체크리스트** (Health Connect 공식 get-started 가이드, 2026-11-04 확인). 누락하면 권한 요청·개인정보 처리방침 링크 흐름이 정상 동작하지 않는다.
  - P0 타입 read 권한만 선언: `android.permission.health.READ_HEART_RATE`, `android.permission.health.READ_EXERCISE`. 30일보다 오래된 데이터가 필요할 때만 `READ_HEALTH_DATA_HISTORY`를 별도 검토한다. background read 권한은 v1에서 선언하지 않는다(D-06).
  - Health Connect 설치 확인용 `<queries><package android:name="com.google.android.apps.healthdata" /></queries>`.
  - Android 13 이하: `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`를 처리하는 exported activity.
  - Android 14 이상: 같은 activity를 가리키는 `activity-alias`, action `android.intent.action.VIEW_PERMISSION_USAGE`, category `android.intent.category.HEALTH_PERMISSIONS`, `android:permission="android.permission.START_VIEW_PERMISSION_USAGE"`.
  - 해당 activity는 Play Console에 제출하는 것과 같은 개인정보 처리방침을 표시해야 한다.
  - `connect-client` 1.1.0 이상은 compileSdk 36, AGP 8.9.1 이상을 요구한다. 현재 compileSdk 36, AGP 8.11.0으로 충족한다. 버전은 PR0/PR1 착수 시 최신 안정판을 다시 확인한다.
- **계정 귀속 (CONFIRMED, K-05):** Health Connect 권한과 데이터는 앱 계정이 아니라 기기 사용자 단위다. 같은 기기에서 앱 계정을 A에서 B로 바꾸면 B도 같은 원본을 읽을 수 있다. 그래서 Fitness 계정별로 연결 동의를 따로 받고, 동의한 계정에서만 동기화한다. 전환별 처리(anonymous claim, A→B, logout)는 §6.3을 따른다. A가 가져온 cache·links는 B 소유로 옮기거나 노출하지 않는다.
- Android OS/Health Connect availability 확인 → 권한 안내 → 시스템 승인 → 첫 수집 → 최근 동기화 시각/오류를 구분하여 표시한다.
- UI states: `notSupported`, `permissionRequired`, `readyEmpty`, `syncing`, `partial`, `synced`, `errorRetryable`, `permissionRevoked`; 서로 무의미하게 합치지 않는다.
- 동기화 실패로 수동 운동 기록 저장을 실패시키지 않는다.
- Health Connect 데이터는 개인 건강정보이므로 로그, Crash report, analytics, 외부 전송에 생심박 sample 또는 access token을 기록하지 않는다. 디버그 probe 결과에서도 ID·계정 식별자를 익명화한다.
- 사용자가 관측 캐시 삭제, 동기화 중단, 권한 변경을 이해할 수 있는 설정 경로를 제공한다.
- 실기기 권한 화면, 화면 재생성, 앱 복귀, 설정 전환과 프로세스 재시작 복구를 테스트한다.

## 9. Execution Plan (branch별 단계 / 완료 게이트)

### PR0 — Fit3 → Samsung Health → Health Connect feasibility probe

**Goal:** A25 실기기에서 관측 가능한 데이터 종류와 구조를 증명한다. 프로덕션 DB migration 없음.

- Android OS, Samsung Health, Health Connect 버전/사용자 동의 설정 기록.
- 심박 `HeartRateRecord` 원본 id·origin·recordingMethod·device·샘플 time range·count·gap 분포 관측.
- `ExerciseSessionRecord` 존재/부재, 실제 세션 시간과 Fit3 운동 결과 비교.
- 걸음 수는 aggregate 경로와 raw origin 중복 여부 검토.
- 기기 동기화 지연과 동일 구간 재조회 수정·삭제 여부 확인.
- 읽기 권한 거부/회수·Health Connect 미지원·빈 수집을 기록하고, 외부로 데이터를 전송하지 않는다.
- **프로브 위치 (CONFIRMED, K-01, 2026-11-05):** 프로브 화면·Health Connect 의존성·권한 선언은 `debug` 빌드 타입 전용 소스셋 `app/src/debug/`에만 둔다. `qa`·`release` manifest에는 health 권한을 추가하지 않는다. Play Console 선언과 개인정보 처리방침이 준비되기 전에 배포 빌드에 권한이 들어가지 않게 하기 위함이다. 프로브는 Room DB를 열거나 쓰지 않는다.
- **결과 기록 (CONFIRMED, K-02, 2026-11-05):** `docs/wearable/PR0_PROBE_RESULT.md`. 기기 모델, Android·One UI·Samsung Health·Health Connect 버전, 타입별 record 수, sample 간격·공백 분포, origin/recordingMethod/device 메타데이터 존재 여부, 권한 흐름 결과를 남긴다. 원시 심박 값과 record ID 원문은 커밋하지 않고 분포 요약과 마스킹된 식별자만 기록한다.
- **실행 경계:** 프로브 코드 작성과 빌드는 클라우드에서 가능하다(Android SDK와 `dl.google.com` 접근 필요). 실측과 PASS 판정은 실기기에서만 가능하다.

#### PR0 프로브 구현 (v0.3.1)

| 파일 | 역할 |
| --- | --- |
| `app/build.gradle` | `debugImplementation "androidx.health.connect:connect-client:1.1.0"` (2025-10-08 안정판). 다른 구성에는 추가하지 않음 |
| `app/src/debug/AndroidManifest.xml` | `READ_HEART_RATE`·`READ_EXERCISE`·`READ_STEPS` 읽기 권한, Health Connect·Samsung Health `<queries>`, 프로브 launcher, rationale activity(Android 13 이하)와 `VIEW_PERMISSION_USAGE` alias(Android 14 이상). 쓰기·background 권한 없음 |
| `app/src/debug/kotlin/.../probe/wearable/HealthConnectProbeReader.kt` | 읽기 전용 HC 접근. HR은 sample 시각만 남기고 bpm 값은 버림. 결과를 SUCCESS·EMPTY_SUCCESS·PERMISSION_DENIED·HC_UNAVAILABLE·HC_UPDATE_REQUIRED·UNSUPPORTED·FAILURE로 구분. 예외는 클래스 이름만 기록 |
| `app/src/debug/kotlin/.../probe/wearable/WearableProbeActivity.kt` | 세션 구간(수동 기록) 입력, HR·ExerciseSession·Steps 조회, 최근 7일 ExerciseSession 목록, HR changes token 발급·조회, 익명화 보고서 표시·복사 |
| `app/src/debug/kotlin/.../probe/wearable/WearableProbeRationaleActivity.kt` | Health Connect 권한 화면에서 여는 데이터 사용 안내 |
| `app/src/debug/kotlin/.../probe/wearable/analysis/` | Android 의존이 없는 순수 로직: 구간 파서, 간격·coverage·최대 공백 계산, 잠정 K-12 기준, salt 기반 id 마스킹, 백업 제외 상태 파일, 익명화 보고서 |
| `app/src/testDebug/kotlin/.../probe/wearable/analysis/` | 순수 로직 단위 테스트 |
| `app/src/test/kotlin/.../probe/WearableProbeBuildBoundaryTest.kt` | 모든 변형에서 도는 경계 테스트: main manifest·main 소스에 HC 없음, qa/release manifest에 health 권한 없음, HC 의존성은 `debugImplementation` 한 줄뿐 |
| `docs/wearable/PR0_PROBE_RESULT.md` | 결과 기록 템플릿과 실행 절차. 현재 상태 NOT_RUN |

**프로브의 개인정보 처리**
- 보고서의 시각은 모두 세션 시작·종료 기준 상대값이다.
- record id는 기기 안 무작위 salt로 만든 12자리 마스크로만 나온다. salt·마스크·changes token은 `noBackupFilesDir`의 상태 파일에만 있다.
- 네트워크 경로가 없다. 보고서는 사용자가 누를 때만 클립보드로 복사하며, 민감 데이터 표시를 붙인다.

**실기기 설치 경로 (K-13, DECISION PENDING):** debug·qa·release가 모두 같은 applicationId `com.yeonsik.fitnessapp`를 쓴다.
- 실기기에 데이터가 든 qa/release 앱이나 다른 키로 서명한 debug 앱이 이미 있으면, 프로브가 든 debug APK는 서명 불일치로 설치되지 않는다.
- 이때 **기존 앱을 제거하고 재설치하면 로컬 기록이 지워지므로 금지한다**(Android Studio의 "uninstall and retry" 포함).
- 설치 전에 `adb shell pm list packages com.yeonsik.fitnessapp`로 기존 설치 여부를 확인한다. 설치돼 있으면 K-13을 결정하기 전까지 실기기 실행을 멈춘다.

#### PR0 판정 (F-03)

**핵심 게이트 G-HR (P0 필수):** Galaxy Fit3로 기록한 **운동 중 심박 시계열**을 Health Connect에서 읽을 수 있는가. 걸음 수, `ExerciseSessionRecord`, 일일 집계는 보조 관측일 뿐이고 G-HR을 대신하지 못한다.

**최소 관측 프로토콜**
- **운동 세션:** Fit3에서 운동 세션을 3회 이상 기록한다.
  - 각 세션은 20분 이상이다.
  - 1회 이상은 실외 걷기 또는 달리기로 한다.
  - 1회 이상은 일시정지·재개를 포함한다.
  - 세션마다 시작·일시정지·재개·종료 wall-clock 시각을 수동으로 기록한다. 프로브는 Fitness DB를 읽지 않는다.
- **세션별 HR 기록:** Samsung Health → HC 동기화가 끝난 뒤, 세션 구간으로 `HeartRateRecord`를 조회해 다음을 남긴다.
  - record 수, sample 수, sample 시각 범위
  - sample 간격 분포: 중앙값, p95, 최대 공백
  - 활동 구간(일시정지 제외) 대비 coverage
  - `dataOrigin` 패키지, `recordingMethod`, device type·model 존재 여부
  - `metadata.id`와 `clientRecordId` 존재 여부
- **동기화 지연:** 운동 종료부터 HC에서 조회되기까지 걸린 시간. 같은 구간을 24시간 뒤 다시 조회했을 때 record id와 sample 수가 바뀌는지.
- **상태 구분 시나리오:** 권한 거절, 권한 회수 후 재조회, 권한 허용 상태에서 운동이 없는 구간(빈 성공), 미지원 타입, HC 미설치 또는 업데이트 필요(재현 가능한 경우). 각 시나리오가 서로 다른 결과로 구분되는지 확인한다.
- **잠정 coverage 기준 (DECISION PENDING, K-12):**
  - 활동 구간의 1분 단위 구간 중 80% 이상에 sample이 있다.
  - 활동 구간 안의 최대 공백이 5분 이하다.
  - 결과 보고 때 실측 분포와 함께 사용자가 승인하거나 조정한다. 승인 전에는 PR4 coverage metric으로 고정하지 않는다.

| 판정 | 조건 | 다음 조치 |
| --- | --- | --- |
| **PASS** | 프로토콜의 모든 세션에서 세션 구간 안 HR sample이 있고 잠정 coverage 기준을 충족함. origin, sample 시각, 간격 분포, identity 요소가 기록됨. 권한 거절, 빈 성공, 미지원이 서로 다른 결과로 구분됨 | PR0 결과로 baseline을 재고정하고, K-09·K-12를 확정한 뒤 PR1-b 착수 |
| **PARTIAL** | HR sample은 있으나 다음 중 하나: 일부 세션에서만 있음, coverage 기준 미달(예: 수 분 간격의 간헐 측정), origin·`metadata.id` 등 identity 요소가 누락·불안정 | 사용자 결정 필요: (a) 기기·Samsung Health 측정 설정을 바꿔 재측정, (b) 축소된 v1 가치를 수용(예: 평균만), (c) Samsung Health Data SDK 평가. 결정 전에는 PR1-b·PR2 보류 |
| **BLOCKED — 대체 수집 경로 결정 필요** | 권한 허용과 Samsung Health 동기화 완료를 확인했는데도 운동 구간 HR record가 0건이거나, 기기에서 HC를 쓸 수 없음. **걸음 수나 운동 세션만 확보된 경우도 여기에 해당** | P0 HR 구현 중지. Samsung Health Data SDK 평가, 범위 변경, 중단 중에서 결정. PR1-b~PR4 착수 금지 |
| **INCONCLUSIVE** (판정 아님) | 프로브 결함(예: manifest 누락으로 권한 흐름 실패), 동기화 미완료, 프로토콜 미충족 | 프로브를 고치거나 프로토콜을 채운 뒤 재측정. BLOCKED로 기록하지 않음 |

PR0 결과 문서에는 판정 하나, 증거 수준(실기기), 위 관측값을 함께 남긴다. 실기기 근거가 없으면 PASS·PARTIAL·BLOCKED 중 어느 것도 기록하지 않는다.

### PR1 — Common Contract + Health Connect Adapter

- `shared` typed model / read port / provenance + Android adapter / permission gateway / mapping.
- ABI/compilation Android + iOS shared 대상 영향 점검.
- mapper 계약 테스트: null/unknown, duplicate samples, partial page, timezone, provenance 불명.
- **PR1-a (PR0 전 설계·브랜치 구현 가능):**
  - shared 순수 모델: `ObservationReadResult` 상태, typed observation, provenance 필드의 unknown/nullable 표현.
  - 합성 fixture 기반 mock 테스트.
  - K-03·K-04 결정.
  - identity·`sourceClassification`·quality 관련 의미는 **DECISION PENDING**으로 두고 확정하지 않는다.
- **PR1-b (PR0 PASS 후, 또는 PARTIAL이고 사용자 결정이 끝난 뒤):**
  - source identity 규칙(K-09), `sourceClassification` 규칙, quality flag·coverage 정의 확정.
  - 익명화한 실제 HC fixture로 mapper 테스트.
  - 실제 HC adapter 연결.
- **main 병합:** PR1은 PR1-b까지 끝난 뒤 병합한다. PR1-a만 먼저 병합하지 않는다. PR0 결과로 계약이 바뀌었을 때 이미 병합된 계약을 뒤집는 일을 막기 위해서다.
- **PASS:** mock fixture/실제 HC fixture에서 타입별 동일 의미, 권한/미지원/빈 기록 상태 분리, 기존 앱 기능 회귀 없음.

### PR2 — Room observation cache + sync + backup / account

- **착수 조건:**
  - PR0 판정과 K-09(identity)·K-10(cache FK) 확정.
  - §6.1~§6.3 계약은 v0.3에서 설계 확정(K-05·K-06·K-08·K-11)됐지만, identity 안정성이 PR0에 걸려 있어 그전까지 보류한다.
- 새 Entity/DAO/migration, source identity upsert, 변경 token/cursor, tombstone, owner claim, 재시도, backup 계약.
- 기존 support migration chain과 신규 설치 Room schema 양쪽 테스트; rollback 없는 migration.
- 백업 테이블 제외 목록 고정 테스트, 소유권 분류 registry 테스트(§6.3 C-1~C-4).
- **PASS:**
  - repeated import 동일 row 수
  - sample update identity 유지
  - delete → `sourceDeleted`(projection `needsReview`)
  - token/transaction 복구
  - §6.4의 AC-C1~C5, AC-B1~B11, 무결성 query I-1~I-5 전부 통과

### PR3 — Cardio linking without rewriting workouts

- **착수 조건:** PR2 병합과 AC 통과, K-07(Cardio 완료 시각 migration) 결정.
- 유산소 시간 범위의 정확성 재확인 후 후보 제시·명시 확인·해제, 링크 투영, 관련 Source/Quality 표시.
- `CardioRepository`와 `WorkoutRepository` 사이 신규 직접 호출 금지; application service를 통해 통합.
- **PASS:** 수동 HR 원본 값 불변, 외부 삭제/재수집 후 링크 상태 보존, 중첩 시간/다중 세션 오연결 테스트.

### PR4 — Settings UI, cardio detail, source-aware insights

- **착수 조건:** PR3 병합, K-12 coverage 기준 승인.
- 권한/연결/최근 동기화·재시도 UX, 심박 추세·평균/최대/품질, 추출 데이터 삭제.
- sample coverage 정책은 PR0 실측과 K-12 승인값을 근거로 versioned metric에 고정.
- `pendingSource`, `sourceDeleted`, `identityConflict` 링크 상태와 복원 충돌 건수를 사용자에게 구분해 보여준다.
- **PASS:** empty/error/loading/permission/process recreation, 실기기 수집→연결→UI 재조회, 수동 기록 회귀 없는 확인.

**Required checks (각 구현 PR):** `./gradlew testDebugUnitTest assembleDebug` (환경에 따른 Windows wrapper 대체), 관련 shared 테스트/compile, migration/instrumentation, 실기기. 실제 실행한 수준만 PASS; Codex 보고만으로 VERIFIED로 취급 금지. PR0 및 PR4는 실기기 근거 없이 완료라고 하지 않는다.

## 10. Unknown / UNVERIFIED — 구현 전 실측 대상

1. **UNVERIFIED (PR0):** Galaxy Fit3가 Samsung Health를 통해 어떤 HR sample rate와 운동 데이터를 Health Connect에 전달하는지 (모델·OS·앱 버전별). G-HR 판정의 대상이다.
2. **UNVERIFIED (PR0):** Health Connect metadata가 Galaxy Fit3 자체의 identity를 구분하기에 충분한지.
3. **UNVERIFIED (PR0):** 읽기 권한, history permission, changes token 사용 흐름의 현재 실제 SDK 동작. 공식 가이드상 쓰지 않은 token은 30일 안에 만료되며, 실제 만료 응답 형태는 확인 전이다.
4. ~~Cardio 종료와 pause/resume wall-clock 구간을 현행 저장 구조만으로 복원할 수 있는지.~~ **v0.2 확정: 복원 불가**(§2). PR3 착수 시 완료 시각 migration 여부를 결정한다(§7).
5. **UNVERIFIED (PR0/PR2):** 장기간 HR cache 크기, 변경 피드의 삭제/수정 이벤트 형식 및 cursor 만료 처리.
6. **UNVERIFIED (PR2):** §6.1~§6.4의 링크 백업·claim 계약은 v0.3에서 설계만 확정했다. 구현과 테스트는 없다.
7. **UNVERIFIED:** iOS HealthKit adapter 및 production iOS persistence 여부(현재 미구현).
8. **UNVERIFIED:** Samsung Health Data SDK의 실사용 접근 조건·데이터 범위. Health Connect로 충분하면 추가하지 않는다. PR0이 PARTIAL·BLOCKED일 때 평가 대상이다.
9. **UNVERIFIED (PR0):** Health Connect `metadata.id`가 Samsung Health 재동기화, 앱 재설치, 기기 변경·HC 데이터 이전 뒤에도 유지되는지(K-09).
10. **UNVERIFIED (PR2):** `claimLocalRows`가 예외를 던질 때 `applyAuthenticatedSharedConfig` 호출 흐름이 로그인을 계속 진행하는지.
11. **UNVERIFIED (범위 밖):** `dining_identity_proposals`(`owner_id`)에 claim·백업 경로가 없는 것이 의도인지 누락인지. wearable 작업에서 바꾸지 않으며, 소유권 registry(C-1)에는 사유를 적어 등록한다.
12. **UNVERIFIED (v0.3.1, 빌드 환경 필요):** 프로브의 Android·Health Connect 의존 코드(`HealthConnectProbeReader`, 두 Activity), debug manifest 병합, `connect-client 1.1.0` 해석, `lintDebug`. 작성 세션은 Android SDK가 없고 `dl.google.com`이 차단돼 Gradle Android 빌드를 실행하지 못했다. 순수 분석 계층과 경계 테스트만 독립 JVM 프로젝트(Kotlin 2.3.0)에서 컴파일·실행했다.

### 10.1 결정 항목 (v0.3)

| # | 결정 | 상태 | 필요 시점 | 내용 |
| --- | --- | --- | --- | --- |
| K-01 | PR0 프로브 코드 위치 | **CONFIRMED (2026-11-05)** | — | `app/src/debug/` 전용 소스셋. qa/release에는 권한·의존성 미포함(경계 테스트로 고정) |
| K-02 | PR0 결과 기록 위치·익명화 수준 | **CONFIRMED (2026-11-05)** | — | `docs/wearable/PR0_PROBE_RESULT.md`, 분포 요약과 마스킹된 식별자만 |
| K-03 | 공통 타입 Instant 표현 | DECISION PENDING | PR1-a | 권장: 기존 Cardio 컬럼과 같은 UTC epoch millis `Long`. `kotlin.time.Instant`는 Swift 노출 확인 후 재검토 |
| K-04 | suspend/coroutines 경계와 Java 브리지 | DECISION PENDING | PR1-a | 권장: coroutines는 Android adapter에만 명시 의존성으로 두고, Java 호출부는 결과 콜백 브리지로 연결 |
| K-05 | Health Connect 데이터의 앱 계정 귀속 | **CONFIRMED (v0.3)** | — | 계정별 연결 동의. 전환 규칙은 §6.3 |
| K-06 | 링크 백업 형식 | **CONFIRMED (v0.3)** | — | formatVersion 2(§6.2). v1 `TABLE_ORDER`에는 추가하지 않음 |
| K-07 | Cardio 완료 시각 migration | DECISION PENDING | PR3 착수 시 | 권장: PR2와 분리된 migration. 그 전의 기존 기록은 사용자 선택 연결만 |
| K-08 | wearable owner 도메인·물리 컬럼 | **CONFIRMED (v0.3)** | — | Fitness 기록 도메인, 물리 컬럼 `user_id`. `owner_id` 사용 금지(§6, S-02) |
| K-09 | `providerRecordId` 안정성과 대체 identity 규칙 | DECISION PENDING | PR0 이후 | HC `metadata.id` 사용이 전제(§4). PR0에서 불안정하다고 나오면 대체 규칙을 근거와 함께 결정. 추측 해시 금지 |
| K-10 | cache 내부 SQLite FK 사용 여부 | DECISION PENDING | PR2 | 현행 DB는 FK를 쓰지 않음(S-03). 어떤 경우에도 링크는 cache에 FK를 걸지 않음 |
| K-11 | claim 시 wearable sync cursor | **CONFIRMED (v0.3)** | — | 승계하지 않고 초기화한 뒤 bootstrap 재조회(§6.3) |
| K-12 | G-HR coverage 기준 | DECISION PENDING | PR0 결과 보고 시 | 잠정: 활동 1분 구간 80% 이상에 sample, 최대 공백 5분 이하. 실측 분포와 함께 사용자 승인 |
| K-13 | PR0 프로브 실기기 설치 경로 | DECISION PENDING | PR0 실기기 실행 전 | debug·qa·release가 같은 applicationId라 데이터가 든 앱 위에 debug APK를 설치할 수 없음. 선택지: (a) `com.yeonsik.fitnessapp`가 없는 기기·사용자에서 실행(같은 사용자 공간에 Samsung Health·Fit3 연결 필요) (b) 기기에 같은 debug 키로 서명된 debug 빌드만 있으면 그 키로 빌드해 업데이트 설치 (c) 프로브 전용 applicationId(debug `applicationIdSuffix` 또는 별도 프로브 앱, K-01과 단일 모듈 규칙 재검토 필요). 권장: 먼저 기기 상태를 확인해 (a)·(b)를 쓰고, 둘 다 안 되면 (c)를 별도로 결정. **기존 앱 제거·데이터 초기화는 어떤 경우에도 금지** |

CONFIRMED는 설계 확정이며 구현·테스트 검증을 뜻하지 않는다. DECISION PENDING은 표시된 시점의 Task Contract에서 확정하고 이 표를 갱신한다.

## 11. Rejected Alternatives / Revisit Conditions

| 선택하지 않은 대안 | 이유 / 재검토 조건 |
| --- | --- |
| Samsung SDK를 첫 번째 구현 | 제조사 결합·SDK 조건 증가. PR0에서 HC 데이터가 충분하지 않을 때 재검토 |
| 웨어러블 측정값으로 수동 평균 심박 자동 대체 | 출처/품질 정보 소실, 기존 record 의미 변경. 별도 사용자 확정 워크플로가 필요할 때만 재검토 |
| HeartRate/Steps/Body 모두 하나의 numeric Value 테이블 | 의미·단위·측정 구간 차이 손실. 타입별 모델 유지 |
| 시간 겹침만으로 자동 운동 연결 | 동일성 불명, 현재 Cardio 종료 시각 미확정. 추후 정량 근거 확보 후 rule version 추가 |
| HealthTrace/Fitness 공용 DB 또는 wearable microservice | 실제 독립 소비/운영 문제가 확인되지 않은 과설계. 단일 계약 재사용 요구가 생길 때 검토 |
| 곧바로 Background/Real-time 스트리밍 | 별도 permission/lifecycle 및 기기 지원 필요. 실제 실사용 요구가 생길 때 검토 |
| 링크가 cache 로컬 PK를 참조 | raw cache를 백업하지 않으므로 복원 후 참조가 깨지고, claim 충돌을 해소할 때도 참조가 끊김. source identity 참조로 대체(§6.1) |
| v1 `TABLE_ORDER`에 링크 테이블 추가 | v57 이하 백업에 예외가 필요하고 v1 의미가 바뀜. 컬럼명 기반 owner 주입(S-02)과도 충돌 |
| 링크가 없을 때만 v1로 쓰는 조건부 형식 | 출력 형식이 둘이 되어 테스트 행렬과 구형 앱 동작이 복잡해짐. v2를 항상 씀(§6.2-2) |
| claim 시 sync cursor 승계 | token이 다른 owner scope의 cache 진행 상태를 전제하게 됨. 기존 `sync_state` 선례와도 다름. 초기화 + bootstrap으로 대체(K-11) |
| 컬럼명(`user_id`) 기반 claim 커버리지 검사 | `owner_id`·`scope_key`·owner 없는 테이블과 claim하지 않는 `user_id` 테이블(`devices`)을 잘못 분류함. 명시적 registry로 대체(§6.3) |

## 12. Acceptance / Implementation Boundary

**설계 승인 조건:** 현재 데이터 소유권·Cardio 완료 경계·local-first 보존, PR0 실측 결과를 반영할 수 있는 capability/read port, 원본 identity/프로비넌스, source-aware projection, 명시적 링크 정책, 동기화/계정/백업 failure matrix가 정의돼 있을 것.

**구현 완료 주장 금지:** 이 문서와 브랜치가 존재해도 Health Connect 접근, DB migration 적용, 테스트 성공, 실기기 연동이 증명되지 않는다. 구현별 검증 증거는 별도 PR/작업 계약과 현재 HEAD에 남긴다.

**Codex conflict rule:** 실제 코드/SDK 동작이 본 계획과 충돌하면 범위를 임의 확장하거나 관련 없는 대규모 리팩터링을 하지 말고 `CONTRACT CONFLICT`로 파일·이유·최소 대안을 보고한다.

### 12.1 착수 판정 (2026-11-04, v0.3에서 v0.2 판정을 대체)

| 단계 | 판정 | 조건 |
| --- | --- | --- |
| PR0 | **디버그 프로브 빌드 검증 완료 · 실기기 실측 대기 (2026-11-06)** | Android 빌드·단위 테스트·lint와 빌드 타입별 매니페스트 확인 결과는 [PR0 검증 기록](wearable/PR0_PROBE_RESULT.md)에 있다. 남은 단계는 K-13 설치 경로 결정과 Galaxy A25 + Fit3 실측이다. PR0 판정은 실기기 실측 후 기록한다. |
| PR1 | **PR1-a 설계·브랜치 구현 가능 / 최종 확정은 PR0 이후** | PR1-a: 순수 모델·합성 fixture mock 테스트, K-03·K-04. PR1-b와 main 병합: PR0 PASS 후, 또는 PARTIAL이고 사용자 결정이 끝난 뒤. 원본 identity(K-09)·품질 규칙은 그때까지 DECISION PENDING |
| PR2 | **착수 보류** | PR0 판정, K-09·K-10 확정 전까지. 백업 형식·계정 귀속·cursor 계약(K-05·K-06·K-08·K-11)은 v0.3에서 설계 확정 |
| PR3 | **선행 계약 검증 후 착수** | PR2 병합과 §6.4 AC 통과, K-07 결정 |
| PR4 | **선행 계약 검증 후 착수** | PR3 병합, K-12 승인 |

- PR0 판정이 BLOCKED면 PR1-b~PR4는 착수하지 않는다. PR1-a도 병합하지 않고 보류한다.
- **검토 증거 수준:** v0.2·v0.3 모두 소스 정적 검토와 공식 문서 확인만 했다. 단위 테스트·Android 빌드·instrumentation·실기기는 **NOT_RUN**이다(검토 세션에 Android SDK가 없고 `dl.google.com`이 차단됨).

## 13. References

- Fitness repository source: https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/1df259296ce9c21e022edcd0e2d0154c6ff2d343
- Fitness project rules: https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/1df259296ce9c21e022edcd0e2d0154c6ff2d343/AGENTS.md
- Fitness architecture policy: https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/1df259296ce9c21e022edcd0e2d0154c6ff2d343/docs/FitnessApp_%EC%A0%95%EB%8F%88_%EB%A6%AC%ED%8C%A9%ED%84%B0%EB%A7%81_%EA%B7%9C%EC%B9%99.md
- HealthTrace reference proposal (별도 저장소, 구현 완료 근거 아님): https://github.com/Yeon-sik/HealthTrace/blob/5fbc01df520fb510a2ff9a7199c2c92b8687251a/docs/WEARABLE_SLEEP_TDD.md
- Google Android Developers, Health Connect get started: https://developer.android.com/health-and-fitness/health-connect/get-started
- Google Android Developers, read data / history limits: https://developer.android.com/health-and-fitness/health-connect/read-data
- Google Android Developers, sync data (changes token 만료, `DeletionChange` id 저장): https://developer.android.com/health-and-fitness/health-connect/sync-data
- Samsung Developer, Accessing Samsung Health Data through Health Connect: https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
- Samsung Developer, Health Connect FAQ: https://developer.samsung.com/health/health-connect-faq.html

---

**Document status:** PROPOSED v0.3.1. PR0 검증 결과와 최신 기준 SHA를 재고정한 후 PR1 구현 Task Contract를 확정한다.
