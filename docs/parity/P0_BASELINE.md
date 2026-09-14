# P0 — Post-Stage6 Parity Baseline

검증 기준은 문서의 기능 설명이 아니라 fetch 직후 확인한 GitHub `main`의 실제 production 코드다. 이 단계는 복구 구현이 아니라 기준선과 후속 Phase 경계를 고정하는 단계다.

## A. Baseline

| 항목 | 기준 |
|---|---|
| Current source | `origin/main` |
| Current main SHA | `e78605f41e716c8a0243d91eed58a94f0bb13329` |
| Stage6 직전 비교 SHA | `cd51b77e6ea3a70dbdf365bc2181297e2a7f8e2a` |
| fetch 결과 | 지정된 Current SHA와 일치. fetch 후 기준 변경 없음 |
| 검증일 | 2026-09-14 (Asia/Seoul) |
| 코드 범위 | Java 17, Android View, Room/SQLiteOpenHelper 공존, Compose feature UI |
| 이 문서의 판정 | `origin/main` production path 기준. legacy 및 `androidTest` fixture는 기능 존재의 증거로만 사용 |

비교 커밋은 현재 Architecture가 아니라 Stage6 전 기능 존재 여부와 동작 의도를 확인하는 자료로 사용했다. 현재 작업 트리에는 P0 전에 존재하던 `FitnessDatabaseHelper.java` 수정, `model_image.zip`, 첨부된 parity 사양서가 남아 있었으며 모두 이 Phase에서 변경하지 않았다.

### Verification baseline

임시 detached worktree를 `e78605f41e716c8a0243d91eed58a94f0bb13329`에서 만들어 로컬 `local.properties`를 연결한 뒤 다음 두 명령을 각각 실행했다.

```text
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

두 명령 모두 `BUILD SUCCESSFUL`이다. `testDebugUnitTest` XML 결과 집계는 62 suites, 291 tests, failures 0, errors 0, skipped 0이었다. instrumentation, 실기기, 시각 QA, GPS 센서, Supabase/RLS 운영 연결, offline/process recreation은 실행하지 않았다.

## B. Architecture Freeze

### Runtime path

현재 기준 경로는 다음으로 고정한다.

```text
Compose UI / specialized View interop
  → ViewModel
  → Feature API 또는 Application Service
  → Feature Repository / Read Repository
  → DAO / Room storage
  → Room (`fitness_mvp.db`, version 51)
```

생산 DB owner는 `FitnessRoomDatabaseProvider.get()`가 반환하는 Room singleton이다. `FitnessDatabaseHelper`와 `LegacyMigrationDatabase`는 legacy migration/compatibility 경계이며 새 화면의 runtime owner로 되살리지 않는다. 현재 schema contract는 `core/database/FitnessDatabaseContract.kt`에 있고 `LEGACY_VERSION=50`, `ROOM_VERSION=51`을 보존한다.

### Owner boundaries

| Owner | 소유 사실/쓰기 | 주요 소비자 |
|---|---|---|
| Workout | 세션, 운동, 세트, performance 사실, workout lifecycle | Workout UI, Home, Records, Statistics, Development |
| Cardio | GPS, route point, 거리, 시간, pace/speed, HR | Cardio UI, Workout lifecycle, Records, Statistics |
| Body | 체중 및 body profile | Body UI, Records, Statistics, Development |
| Meal | 실제 섭취 기록 및 섭취 당시 snapshot | Meal UI, Records, Statistics, Development |
| Nutrition | food/product/variant/recipe catalog와 provenance | Meal write/read, nutrition analysis |
| Recovery | `AthleteDailyCheckIn`, recovery/check-in facts | Development, future Statistics read |
| Supplement | plan, dose status, history, effect check-in, evidence | Supplement UI, limited Development presentation |
| Records | 날짜 중심 read composition만 소유 | Records UI |
| Statistics | 기간 비교와 추세를 계산하는 신규 read-only feature | Statistics UI |
| Development | readiness, evidence, limitation, confidence, recommendation | Development UI |

`AppContainer.kt`는 위 repository/API들을 조립하고 `DevelopmentReportService`에 workout/meal/body/development read API를 주입한다. UI/ViewModel이 타 feature DAO를 직접 호출하거나 범용 legacy `FitnessRepository`를 재도입하지 않는다.

### Navigation and state

`FitnessScreen.java`와 `ComposeAppScreen.kt`가 현재 navigation 경계를 구성한다. 하단 탭은 Home, Workout, Records, Development, Settings이고, Exercise Picker/ Routine/ Workout Detail/ Summary/ Cardio/ Meal/ Supplement는 route state로 진입한다. `Statistics` route, ViewModel, feature package는 현재 없다.

`AppNavigationViewModel`은 `today`, `selectedMealDate`, `selectedRecordsDate`, `selectedRoutineId`를 `SavedStateHandle`에 보존한다. 그러나 `ComposeAppScreen.kt`의 Home entry effect와 Home snapshot 재사용은 선택 날짜 query와 분리되어 있어 P1A의 첫 재검증 대상이다.

### Specialized renderer interop

`ExerciseIllustrationPreview`, `ExerciseMuscleModelRenderer`, `CardioRouteMap`은 현재도 재사용 가능한 specialized renderer다. 이미지/근육/경로 기능을 복구할 때 Compose 전체 renderer 재작성이나 legacy full-screen rollback 대신 AndroidView/Compose interop과 adapter를 우선한다. 제품 asset source는 `model_image/exercise-images/scenes/*.scene.json` 및 manifest가 참조하는 `final/` PNG다.

### External contracts

현재 contract는 유지한다.

- Personal OS summary projection: `/rest/v1/rpc/upsert_fitness_summary_projection_v2`
- legacy sync: `/rest/v1/rpc/sync_fitness_data_v1`
- nutrition canonical/product hierarchy import contracts
- PriceTrace read RPCs

P0 근거만으로 schema, Room version, Supabase RPC, OCR/Nutrition/PriceTrace contract를 변경할 필요는 확인되지 않았다. 새 원격 Meal ingestion이나 cross-project 데이터 공유는 별도 설계 대상이다.

## C. Producer → Consumer Map

| Producer | Read/API boundary | Current consumers | P0 finding |
|---|---|---|---|
| Workout records, sets, performance | `WorkoutReadApi`, `WorkoutReadRepository`, `WorkoutRoomStorage` | Home, Records, Development; future Statistics | 기본 fact는 존재하지만 `WorkoutReadApi`가 `DevelopmentWeekProgress`/`DevelopmentBodyPartSets`를 직접 반환해 owner가 역전돼 있다. P1A에서 fact model로 격리한다. |
| Body profile/weight | `BodyMetricsReadApi` / `BodyMetricsReadRepository` | Home, Records, Development; future Statistics | 날짜별 read와 기간 window는 존재. Records 전용 composition과 trend UI는 별도다. |
| Meal rows/snapshots | `MealReadApi` / `MealReadRepository` | Home, Records, Development; future Statistics | nullable/unknown macro semantics는 보존하지만 날짜 query와 full edit lifecycle을 검증해야 한다. |
| Nutrition catalog/provenance | `NutritionCatalogRepositoryApi`, `NutritionCatalogRepository` | Meal search/save, PriceTrace integration | 하위 repository가 풍부해도 현재 public Meal flow가 모든 catalog hierarchy를 노출하지 않는다. |
| Recovery/check-in | `DevelopmentRoomDao`, `DevelopmentReadApi` | Development read | `AthleteDailyCheckIn` model/read facts는 있으나 현재 production write flow는 확인되지 않는다. |
| Supplement plan/dose/evidence | `SupplementRepositoryApi`, `SupplementRepository`, `SupplementEvidence` | Supplement UI | active plan/adherence는 노출되나 CRUD/history/effect/evidence의 API→VM→UI 연결이 단절됐다. |
| Cardio route/metrics | `CardioRepositoryApi`, `CardioRoomStorage`, `CompleteCardio` | Cardio session/summary, Workout linkage, Records | recording/paused/finish transaction은 유지. pace/speed와 GPS quality presentation을 보강한다. |
| Home aggregate | `HomeReadRepository` / `HomeViewModel` | Home and currently reused by Records/Routine | Home is a consumer, not a general archive query model. `RecordsUiState` 분리가 필요하다. |
| Derived records | future Records read composition | Records | 현재 `RecordsScreen`은 `HomeUiState`를 소비하며 monthly calendar/markers/trend가 없다. |
| Derived statistics | no current API/package | future Statistics | Statistics는 `NEW`; source facts만 공유하고 Development를 호출하지 않는다. |
| Development evidence/decision | `DevelopmentReportService`, `PaperAdviceSnapshotAssembler`, `PaperAdviceEngine` | Development | report는 연결됐지만 PaperAdvice assessment가 public API→VM→UI까지 연결되지 않았다. |

## D. Known Correctness Risks (P0에서 수정하지 않음)

| ID | 코드 근거와 위험 | 영향 | Target |
|---|---|---|---|
| P0-RISK-001 | `ComposeAppScreen.kt`의 `routeDate`는 Records/Meals 선택 날짜를 전달하지만 `homeEntryEffectKey`/`getHome().enter(...)`는 `today`만 사용한다. | 과거 날짜 Records/Meal 화면에 오늘 snapshot이 재사용될 수 있음. | P1A |
| P0-RISK-002 | `HomeUiState`가 Records에서 재사용되고 `HomeReadRepository`가 aggregate consumer로 사용된다. | archive query와 home refresh가 분리되지 않아 날짜/marker/detail이 stale할 수 있음. | P1A/P6 |
| P0-RISK-003 | `HomeViewModel` 및 여러 effect가 account/date 결과를 비동기로 적용한다. request identity guard가 코드상 명시적으로 고정되지 않은 경로가 있다. | 계정 전환 또는 빠른 날짜 이동 뒤 stale result가 화면을 덮을 가능성. | P1A |
| P0-RISK-004 | `WorkoutReadApi.kt`가 `DevelopmentWeekProgress`, `DevelopmentBodyPartSets`를 반환한다. | Workout producer가 Development presentation model에 결합돼 Statistics/Development 책임이 섞인다. | P1A/P8/P9 |
| P0-RISK-005 | `WorkoutRoomStorage.dayMetrics()`와 DAO `visibleRecordsForDate`가 날짜/deleted/scope 조건을 사용하지만 완료 status filter가 명시되지 않는다. | 진행 중 record가 completed archive/summary로 집계될 가능성. | P1A |
| P0-RISK-006 | `WorkoutRoomStorage.bestSetRows` 조회 fragment에서 `wr.deleted_at IS NULL`/scope guard가 다른 history path와 일관되지 않다. | 삭제/타 scope 기록이 PR/history에 섞일 가능성. | P1A |
| P0-RISK-007 | `WorkoutRoomStorage.allowedLoadStates` map key는 exercise id로 구성되지만 Detail UI lookup은 `recordType`을 사용한다. | LoadState selector가 비어 있거나 잘못된 상태를 표시할 수 있다. | P1A/P3A |
| P0-RISK-008 | best/e1RM 계산이 모든 set의 e1RM이 아니라 max comparable load 중심으로 구성된다. | PR 표기와 history 비교가 실제 최고 성과와 다를 수 있다. | P1A/P3A |
| P0-RISK-009 | recent history/volume read는 exercise identity, deleted/scope, recordType 경계가 여러 query에 분산돼 있다. | 이름이 같거나 variant가 다른 운동의 이력이 합쳐질 수 있다. | P1A/P2A/P3A |
| P0-RISK-010 | `MealRecordRepositoryApi`는 저장 중심이며 edit/delete/time edit public operation이 현재 UI flow에 없다. | 저장 후 수정/삭제와 날짜 refresh parity가 끊긴다. | P4A |
| P0-RISK-011 | `DevelopmentRoomDao`에는 check-in read가 있으나 production write API/UI가 확인되지 않는다. | model/repository 존재를 사용자가 기록 가능한 Recovery flow로 오판할 수 있다. | P4B |
| P0-RISK-012 | `development/PaperAdviceEngine.java`와 `feature/development/application/PaperAdviceSnapshotAssembler.java`는 존재하지만 `DevelopmentReportApi`와 `DevelopmentViewModel`이 assessment를 노출하지 않는다. | “논문 기반 점검” 제목만 있고 engine 결과가 사용자에게 도달하지 않는다. | P9 |
| P0-RISK-013 | `SupplementRepository`에 CRUD/history/effect 메서드가 남아도 `SupplementRepositoryApi`/VM/UI는 active plan와 dose 중심이다. | legacy implementation 존재를 현재 Supplement parity로 잘못 판정할 수 있다. | P7 |
| P0-RISK-014 | Cardio accepted/rejected point filtering과 Workout completion transaction은 존재하나 GPS sensor/quality와 pace/speed UI는 runtime 검증되지 않았다. | 센서 상태별 사용자 의미와 completion/delete linkage를 실기기에서 확인하지 못함. | P3C/P11 |
| P0-RISK-015 | `RecordsAnalysis`와 `WorkoutSummaryAnalytics` 순수 helper는 존재하지만 현재 Records/Summary Compose가 모든 chart/marker/analytics를 소비하지 않는다. | helper 존재만으로 visual parity를 PRESENT로 판정하면 누락을 숨긴다. | P1B/P3B/P6 |
| P0-RISK-016 | `FitnessComposeTheme`/공통 components는 있으나 화면별 semantic status, chart, macro, marker 사용이 일관된다는 증거가 없다. | palette/token 존재와 실제 visual semantics가 어긋날 수 있다. | P1B/P10 |

## E. Regression Scenario Baseline

`AUTOMATED`는 production UI end-to-end 통과를 의미하지 않는다. 아래의 unit/state/fixture 표시는 해당 계층까지만 검증됐다는 뜻이다.

| Scenario | Baseline status | Evidence/limit |
|---|---|---|
| empty account | AUTOMATED (unit/state scope) | repository/empty-state tests 범위; 실제 Compose 화면 미검증 |
| rich-history account | AUTOMATED (read/helper scope) | workout/records helper read tests; full archive rendering 미검증 |
| active workout | AUTOMATED (unit/state scope) | Workout session/service tests; instrumentation 미실행 |
| completed workout | AUTOMATED (unit/state scope) | completion/summary repository tests; actual summary navigation 미검증 |
| historical date | AUTOMATED (policy/read scope) | date/read tests; Records production selected-date flow 미검증 |
| meal on another date | AUTOMATED (repository scope) | meal read/write tests; Home snapshot/date refresh UI 미검증 |
| weight history | AUTOMATED (repository/policy scope) | body metric read tests; chart UI 미검증 |
| kg / lb | AUTOMATED (domain scope) | mass unit tests; large-screen UI conversion 미검증 |
| cardio GPS available/unavailable | AUTOMATED (logic scope) | `CardioDistanceFilter`/metrics tests; device GPS 상태 미검증 |
| logged in/out | AUTOMATED (policy scope) | ownership/account policy tests; two-account UI isolation 미검증 |
| Supabase connected/disconnected | AUTOMATED (client/policy scope) | sync/connection unit paths; live RLS/network 미검증 |
| offline | NOT COVERED | real offline retry/sync behavior 실행 안 함 |
| light/dark | NOT COVERED | Compose visual QA instrumentation 실행 안 함 |
| large font scale | NOT COVERED | 실제 device font-scale layout 실행 안 함 |
| process recreation/state restore | AUTOMATED (state unit scope) | SavedState/VM state tests; instrumented recreation 미검증 |
| existing legacy data | NOT COVERED | migration/instrumentation suite를 이번 검증에서 실행하지 않음 |

## F. Verification and Evidence Limits

실행한 검증은 `testDebugUnitTest`와 `assembleDebug`이며 둘 다 성공했다. 이 결과는 compile/unit baseline을 고정할 뿐 실제 사용자 flow, visual parity, database migration on device, GPS, RLS, account isolation을 증명하지 않는다. 이번 문서의 `PRESENT`는 해당 production path가 존재하고 연결돼 있다는 뜻이며, 동작 품질이 모든 시나리오에서 검증됐다는 뜻이 아니다. `PARTIAL`은 하위 model/repository/helper가 있어도 public API→ViewModel→production UI flow가 단절되거나 기능 범위가 축소된 경우다.

## G. Scope Guard and Merge Gate

P0에서 변경한 것은 이 baseline 문서와 parity matrix뿐이다. Production Kotlin/Java, Room schema/version, DAO, API contract, navigation, Compose behavior, asset, legacy screen은 변경하지 않았다. 발견한 오류는 위 risk와 matrix의 후속 Phase로만 배정한다.

후속 구현은 다음 merge sequence를 따른다.

### Confirmed phase contract

| Phase | Boundary | Required before branch | Deliverable |
|---|---|---|---|
| P0 | 기준선 문서만 | fetched main SHA | baseline, matrix, risks, regression checklist |
| P1A | 날짜/계정/record identity, read owner와 fact semantics | P0 merged | identity-safe read APIs, status/deleted/history/load-state tests |
| P1B | shared image/muscle/chart/calendar/status/evidence primitives | P1A merged | interop adapters and shared visual primitives |
| P2A | Exercise Picker add/replace selection contract | P1A/P1B | image, variant, body/subpart/equipment, recent sort, selection metadata |
| P2B | Routine exercise presentation | P2A | order, image, body/equipment/record type, picker wiring |
| P3A | Workout Session + Exercise Detail | P2A/P2B and stable set contract | session/detail input, navigation, history/PR/load-state UI |
| P3B | Workout Summary | P3A completed snapshot | totals, muscle distribution, trend/comparison, save-as-routine/navigation |
| P3C | Cardio lifecycle and summary | P1A/P1B; Workout transaction preserved | recording/paused, GPS/route/pace/HR, finish/cancel linkage |
| P4A | Body + basic Meal lifecycle | P1A | date-safe body/meal CRUD, edit/delete/time refresh |
| P4B | Nutrition goal + Recovery writes | P4A | owner-correct goal/check-in write→read→Development path |
| P5A | Nutrition catalog/provenance | P4A | verified food/product/variant/recipe/template public flow |
| P5B | Complex consumed Meal snapshot | P5A | immutable menu/component/serving/diner/consumed snapshot |
| P5C | Nutrition analysis | P5B | macro/micro, target comparison, unknown/estimated semantics |
| P6 | Records read composition | P1A plus P3/P4 facts | dedicated `RecordsUiState`, monthly calendar/markers/trends/detail |
| P7 | Supplement plan/history/evidence | P1A/P1B; no PaperAdvice merge | CRUD, adherence, correction, evidence/effect UI |
| P8 | New read-only Statistics feature | P1A and P3/P4 fact APIs; P7 merge order | period trends/comparison/sufficiency and observed preference signal |
| P9 | Development decision/evidence layer | P1A, P4B facts, P8 complete | PaperAdvice assembler→engine→API→VM→UI, limitation/confidence/recommendation |
| P10 | Settings/global presentation hierarchy | P1A–P9 merged | account/sync/safety/import/theme/unit/status presentation |
| P11 | Integration and parity closure | all prior phases merged | matrix closure and full regression/instrumentation/device evidence |

```text
P0
→ P1A Read Correctness / Ownership
→ P1B Shared Visual Foundation
→ P2A Exercise Picker
→ P2B Routine Presentation
→ P3A Workout Session + Detail
→ P3B Workout Summary
→ P3C Cardio
→ P4A Body + Meal Basic Lifecycle
→ P4B Nutrition Goal + Recovery
→ P5A Nutrition Catalog
→ P5B Complex Meal Snapshot
→ P5C Nutrition Analysis
→ P6 Records
→ P7 Supplements
→ P8 Statistics
→ P9 Development
→ P10 Settings / Global Presentation
→ P11 Integration QA / Parity Closure
```

각 Phase는 최신 `main`에서 별도 branch/PR로 시작하고 해당 Phase 검증, `testDebugUnitTest`, `assembleDebug`, merge 후 다음 branch를 만든다. `FitnessRoomDatabase`, `AppContainer`, `ComposeAppScreen`, `AppNavigationViewModel`, `FitnessScreen`, `HomeReadRepository/HomeSnapshot`, `Workout` shared models/storage, `FitnessComposeTheme`, shared Compose components, Meal/Nutrition integration은 global hot spot이므로 동시에 수정하지 않는다. 전체 legacy Java 화면 rollback과 범용 `FitnessRepository` 재도입은 architecture guardrail상 의도적으로 하지 않는다.
