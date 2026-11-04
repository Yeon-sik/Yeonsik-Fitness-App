# Fitness App — Wearable Common Interface Plan v0.1

- **Status:** PROPOSED — 구현 전 설계, PR0 관측 결과 대기
- **작성일:** 2026-11-04 (Asia/Seoul)
- **Repository:** Yeon-sik/Yeonsik-Fitness-App
- **Baseline:** `main@1df259296ce9c21e022edcd0e2d0154c6ff2d343`
- **문서 대상 브랜치:** `docs/wearable-common-interface-v1`
- **작업 유형:** Architecture decision + 단계별 실행 계획. 이 문서 커밋은 구현 완료를 뜻하지 않는다.
- **Source of Truth:** `AGENTS.md`, `docs/FitnessApp_정돈_리팩터링_규칙.md`, 현재 실제 코드. 해당 규칙 문서의 오래된 CURRENT 서술보다 이 문서의 기준 SHA에서 직접 확인한 구현 사실을 우선한다.

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
| Cardio 시각 | `cardio_sessions.started_at_epoch_ms`, `active_duration_ms`, `updated_at_epoch_ms`; 명시적 완료 시각 컬럼은 확인되지 않음 | `core/database/FitnessRoomLegacyEntities.kt` |
| 신체 | `BodyMetricsRepository`가 weight record를 owner scope로 관리 | `feature/body/data/BodyMetricsRepository.java` |
| 계정 | `AccountOwnershipService`에 anonymous→로그인 계정 로컬 행 claim 경로 존재 | `core/account/AccountOwnershipService.java` |
| 백업 | `LocalDataBackupService`는 고정 `TABLE_ORDER`, `fitness-os.local-backup` formatVersion 1 사용 | `backup/LocalDataBackupService.java` |
| 웨어러블 | 현행 검색/확인 범위에서 Health Connect/HealthKit Reader, 웨어러블 persistence 경로 확인되지 않음 | `AndroidManifest.xml`, `app/build.gradle`, 관련 코드 검색 |
| iOS | `iosApp`은 KMP shared host proof이며 HealthKit/production 영속 계층이 아님 | `iosApp/README.md` |

**중요 관측:** Cardio의 `updated_at_epoch_ms`를 곧바로 운동 종료 시각으로 사용하면 안 된다. 이후 수정으로 바뀔 수 있는 시각이며, `active_duration_ms`는 일시 정지 구간을 포함한 wall-clock 종료 시각과 같지 않다. PR3 전에 정확한 workout/cardio 완료 시각의 실제 영속 경로를 확인해야 한다.

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

**시각 규칙:** 저장·매칭은 UTC Instant 기반, 표시 날짜는 명시적 time zone 정책으로 계산한다. `date` 문자열만으로 운동과 관측의 동일성을 판정하지 않는다. start/end timezone offset은 확인 가능한 경우 원본 그대로 보존한다.

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

권장 논리 모델 (이름 및 DDL은 migration 검토 시 확정):

- `wearable_observation_records`: ownerId, transport, originPackage, dataType, providerRecordId, UTC interval, revision/update, source metadata, importedAt, deletedAt/tombstone. `UNIQUE(ownerId, transport, originPackage, dataType, providerRecordId)`. 원본 레코드의 안정적인 PK 유지.
- `wearable_heart_rate_samples`: parent observation ID, source sample sequence/time, bpm, optional quality. 부모 업데이트 시 한 transaction에서 samples 교체 가능; 다른 도메인은 sample PK를 직접 참조하지 않는다.
- `wearable_sync_states`: `(ownerId, transport, dataType)`의 sync cursor/changes token, bootstrap state, lastSuccessAt, lastError. 세부 origin 분리가 필요하면 먼저 이 조건을 검증한다.
- `wearable_workout_links`: ownerId, canonical Fitness workout ID, external record ID, state(`candidate`/`userConfirmed`/`userRejected`/`needsReview`), decidedAt. 한 외부 기록의 active 링크는 기본 1개만 허용하고 owner/record scope 모두 검증한다.

**쓰기/동기화 불변식:**
1. 외부 원본 중복 import는 동일 key에 대한 upsert여야 한다. `INSERT OR REPLACE`로 row identity/FK를 파괴하지 않는다.
2. record + samples 갱신 + 성공 cursor 갱신은 적절한 transaction에 묶는다. 페이지가 일부 실패하면 성공 cursor를 앞당기지 않는다.
3. 변경 피드에서 삭제가 오면 tombstone 처리하고 연결은 `needsReview`로 표시한다. 사용자가 기록 삭제를 선택하면 cache/links 삭제 범위를 명시적으로 확인한다.
4. 새 수집 결과가 오래된 계정/권한 상태로 도착하면 폐기한다. 계정 A → B 전환 시 A의 async import가 B 소유 DB·UI에 반영되지 않아야 한다.
5. 같은 기록의 Samsung SDK/Health Connect 중복을 시간 중복만으로 소거하지 않는다. origin별 활성 transport 지정 및 변경 이력 처리.
6. permissions revoke는 추후 읽기를 중단하되 사용자가 이미 가져온 로컬 cache를 무단 삭제하지 않는다. 별도의 '가져온 데이터 삭제' 명령을 제공한다.
7. offline/권한 거절/미지원/빈 데이터/일부 성공을 구분한다. 재시도 idempotent.
8. 첫 동기화는 허가된 접근 범위만 요청한다. 최초 권한 이전 30일보다 오래된 데이터를 읽으려면 별도 history permission이 필요할 수 있다.
9. 용량: 시계열 record/sample 수집량과 로컬 DB 크기 상한·기간 및 pruning 정책을 Fit3 관측 결과로 결정한다. v1에서 무제한 백그라운드 수집을 허용하지 않는다.

**Backup/restore gate (필수):** 현재 `LocalDataBackupService`는 고정 TABLE_ORDER의 formatVersion 1이다. 새 wearable 테이블이 자동으로 backup되는 것으로 가정하지 않는다. 선호 정책은 **기기 재수집 가능한 raw cache와 sync token은 export 제외**, **사용자 확인 링크 결정은 별도 versioned backup contract로 보존**이다. 복원 후 raw가 없으면 연결은 pending/needsReview로 보존하고 재수집 후 매칭한다. 이 정책을 실제로 구현할 수 없으면 PR2 merge를 보류한다. 백업 v1 reader의 의미를 바꾸지 말고 필요 시 새 버전을 설계한다.

**Account claim gate:** anonymous 사용자의 cache/links가 지원된다면 현재 `AccountOwnershipService.claimLocalRows`의 동일 transaction 및 owner 분리 검증을 추가한다. 누락하면 데이터 유실/타 계정 노출 가능성이 있으므로 PR2 merge 불가.

## 7. 운동 연결 규칙 / 분석 규칙 (PR3–PR4)

**v1은 사용자 확인 연결을 우선한다.** 후보 탐색 기준은 기록 ID + 시간 겹침 + activity type 보조 정보이며, 날짜 일치만으로 auto-confirm하지 않는다.

- Cardio 세션 시작 시각은 현재 존재한다. 정확한 완료 시각/중지·재개 구간의 영속 경로를 PR3에서 별도로 추적한다. `updated_at_epoch_ms` 또는 `start+activeDuration`을 종료 시각으로 대체하지 않는다.
- 시간이 불충분하면 사용자 선택으로만 연결하거나 추가 메타데이터 migration을 별도 결정한다.
- 근력 세션은 시간 정보가 제한적이면 v1 자동 후보 연결에서 제외한다. `workout_records.date`만으로 HR을 결합하지 않는다.
- 기존 `workout_records.average_heart_rate`의 null·수동 수정·legacy 동기화 의미를 변경하지 않는다.
- 연결된 HR time series의 평균·최대는 sensor-derived projection으로 제공하고 `value`, `source`, `method`, `algorithmVersion`, `qualityFlags`를 동반한다.
- 샘플 사이의 큰 공백/운동과 겹치는 실제 관측 구간을 확인하지 않은 채 '운동 평균 HR'로 표시하지 않는다. coverage 정의·최소 표본 기준은 Fit3 실제 sample 분포로 PR0/PR4에서 결정한다.
- 중복/원본 경로 전환/센서 모델 차이와 알고리즘 버전이 바뀌는 경우 분석 시계열을 조용히 이어 붙이지 않는다.
- P1 걸음 수 합계는 복수 원본 raw steps 합산을 기본값으로 사용하지 않는다. Health Connect aggregate API 및 우선순위 정책을 평가한다(중복 집계 방지).
- 수면/HRV 데이터와 운동 수행 간 상관은 인과로 표현하지 않는다.

## 8. 권한 / Privacy / UX

- Health Connect 권한은 필요한 데이터 타입의 **read-only**로 최소 요청한다. manifest, 시스템 권한 요청, Play Console 선언 일치 여부를 PR0/PR1에서 확인한다.
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

**PASS:** 실제 실기기에서 최소 하나의 관심 유형에 대해 데이터, 출처, 시간, 권한 흐름을 확인하고 재현 가능한 익명화된 결과 기록. 심박 시계열이 없으면 P0 기능 구현을 진행하지 않고 Samsung SDK/대체 경로의 필요성을 의사결정으로 보고.

### PR1 — Common Contract + Health Connect Adapter

- `shared` typed model / read port / provenance + Android adapter / permission gateway / mapping.
- ABI/compilation Android + iOS shared 대상 영향 점검.
- mapper 계약 테스트: null/unknown, duplicate samples, partial page, timezone, provenance 불명.
- **PASS:** mock fixture/실제 HC fixture에서 타입별 동일 의미, 권한/미지원/빈 기록 상태 분리, 기존 앱 기능 회귀 없음.

### PR2 — Room observation cache + sync + backup / account

- 새 Entity/DAO/migration, source identity upsert, 변경 token/cursor, tombstone, owner claim, 재시도, backup 계약.
- 기존 support migration chain과 신규 설치 Room schema 양쪽 테스트; rollback 없는 migration.
- **PASS:** repeated import 동일 row 수, sample update identity 유지, delete → needsReview, token/transaction 복구, A/B 격리 및 anonymous claim, backup/restore link 보존.

### PR3 — Cardio linking without rewriting workouts

- 유산소 시간 범위의 정확성 재확인 후 후보 제시·명시 확인·해제, 링크 투영, 관련 Source/Quality 표시.
- `CardioRepository`와 `WorkoutRepository` 사이 신규 직접 호출 금지; application service를 통해 통합.
- **PASS:** 수동 HR 원본 값 불변, 외부 삭제/재수집 후 링크 상태 보존, 중첩 시간/다중 세션 오연결 테스트.

### PR4 — Settings UI, cardio detail, source-aware insights

- 권한/연결/최근 동기화·재시도 UX, 심박 추세·평균/최대/품질, 추출 데이터 삭제.
- sample coverage 정책은 PR0 실측을 근거로 versioned metric에 고정.
- **PASS:** empty/error/loading/permission/process recreation, 실기기 수집→연결→UI 재조회, 수동 기록 회귀 없는 확인.

**Required checks (각 구현 PR):** `./gradlew testDebugUnitTest assembleDebug` (환경에 따른 Windows wrapper 대체), 관련 shared 테스트/compile, migration/instrumentation, 실기기. 실제 실행한 수준만 PASS; Codex 보고만으로 VERIFIED로 취급 금지. PR0 및 PR4는 실기기 근거 없이 완료라고 하지 않는다.

## 10. Unknown / UNVERIFIED — 구현 전 실측 대상

1. Galaxy Fit3가 Samsung Health를 통해 어떤 HR sample rate와 운동 데이터를 Health Connect에 전달하는지 (모델·OS·앱 버전별).
2. Health Connect metadata가 Galaxy Fit3 자체의 identity를 구분하기에 충분한지.
3. 읽기 권한, history permission, changes token 사용 흐름의 현재 실제 SDK 동작.
4. Cardio 종료와 pause/resume wall-clock 구간을 현행 저장 구조만으로 복원할 수 있는지.
5. 장기간 HR cache 크기, 변경 피드의 삭제/수정 이벤트 형식 및 cursor 만료 처리.
6. 백업의 링크 결정 보존 및 anonymous account claim 경로의 실제 구현·테스트.
7. iOS HealthKit adapter 및 production iOS persistence 여부(현재 미구현).
8. Samsung Health Data SDK의 실사용 접근 조건·데이터 범위; Health Connect 충족 시 추가하지 않음.

## 11. Rejected Alternatives / Revisit Conditions

| 선택하지 않은 대안 | 이유 / 재검토 조건 |
| --- | --- |
| Samsung SDK를 첫 번째 구현 | 제조사 결합·SDK 조건 증가. PR0에서 HC 데이터가 충분하지 않을 때 재검토 |
| 웨어러블 측정값으로 수동 평균 심박 자동 대체 | 출처/품질 정보 소실, 기존 record 의미 변경. 별도 사용자 확정 워크플로가 필요할 때만 재검토 |
| HeartRate/Steps/Body 모두 하나의 numeric Value 테이블 | 의미·단위·측정 구간 차이 손실. 타입별 모델 유지 |
| 시간 겹침만으로 자동 운동 연결 | 동일성 불명, 현재 Cardio 종료 시각 미확정. 추후 정량 근거 확보 후 rule version 추가 |
| HealthTrace/Fitness 공용 DB 또는 wearable microservice | 실제 독립 소비/운영 문제가 확인되지 않은 과설계. 단일 계약 재사용 요구가 생길 때 검토 |
| 곧바로 Background/Real-time 스트리밍 | 별도 permission/lifecycle 및 기기 지원 필요. 실제 실사용 요구가 생길 때 검토 |

## 12. Acceptance / Implementation Boundary

**설계 승인 조건:** 현재 데이터 소유권·Cardio 완료 경계·local-first 보존, PR0 실측 결과를 반영할 수 있는 capability/read port, 원본 identity/프로비넌스, source-aware projection, 명시적 링크 정책, 동기화/계정/백업 failure matrix가 정의돼 있을 것.

**구현 완료 주장 금지:** 이 문서와 브랜치가 존재해도 Health Connect 접근, DB migration 적용, 테스트 성공, 실기기 연동이 증명되지 않는다. 구현별 검증 증거는 별도 PR/작업 계약과 현재 HEAD에 남긴다.

**Codex conflict rule:** 실제 코드/SDK 동작이 본 계획과 충돌하면 범위를 임의 확장하거나 관련 없는 대규모 리팩터링을 하지 말고 `CONTRACT CONFLICT`로 파일·이유·최소 대안을 보고한다.

## 13. References

- Fitness repository source: https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/1df259296ce9c21e022edcd0e2d0154c6ff2d343
- Fitness project rules: https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/1df259296ce9c21e022edcd0e2d0154c6ff2d343/AGENTS.md
- Fitness architecture policy: https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/1df259296ce9c21e022edcd0e2d0154c6ff2d343/docs/FitnessApp_%EC%A0%95%EB%8F%88_%EB%A6%AC%ED%8C%A9%ED%84%B0%EB%A7%81_%EA%B7%9C%EC%B9%99.md
- HealthTrace reference proposal (별도 저장소, 구현 완료 근거 아님): https://github.com/Yeon-sik/HealthTrace/blob/5fbc01df520fb510a2ff9a7199c2c92b8687251a/docs/WEARABLE_SLEEP_TDD.md
- Google Android Developers, Health Connect get started: https://developer.android.com/health-and-fitness/health-connect/get-started
- Google Android Developers, read data / history limits: https://developer.android.com/health-and-fitness/health-connect/read-data
- Samsung Developer, Accessing Samsung Health Data through Health Connect: https://developer.samsung.com/health/blog/en/accessing-samsung-health-data-through-health-connect
- Samsung Developer, Health Connect FAQ: https://developer.samsung.com/health/health-connect-faq.html

---

**Document status:** PROPOSED. PR0 검증 결과와 최신 기준 SHA를 재고정한 후 PR1 구현 Task Contract를 확정한다.
