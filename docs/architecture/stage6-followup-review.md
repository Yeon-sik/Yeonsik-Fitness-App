# Stage 6 Follow-up Architecture Decision

## 1. Verdict

**Stage 6 TARGET 미달. 후속 보완이 필수다.** Room의 DB 관리 인수, 기능 API·ViewModel·Compose 화면 도입, production `FitnessRepository` 제거는 확인됐다. 그러나 업무 SQL의 DAO 우회, 기능 소유권 침범, Activity 중심 화면 조립·업무 실행이 남아 있다. Stage 3~5에서 허용한 이행 구조를 Stage 6 완료로 인정하지 않는다.

- 기준: 2026-09-08 `git fetch origin main`으로 확인한 [`a320f1e9f68a9ffea825ab8af04288e25fd6eed9`](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/a320f1e9f68a9ffea825ab8af04288e25fd6eed9). 아래 현재 코드 판단은 이 커밋의 `app/src/main`을 직접 읽은 저장소 검증이다. 생성 그래프와 과거 완료 보고서는 판정 근거에서 제외했다.
- 상위 기준: [정돈 리팩터링 규칙](../FitnessApp_정돈_리팩터링_규칙.md)의 TARGET 및 §§4, 5, 6, 10.2, 11.3. 이 문서는 그 TARGET을 달성하는 후속 변경의 범위·예외·종료 조건을 확정한다. 아래 수정은 아직 구현되지 않았다.
- 반박: **raw SQL 문자열 자체, Java 파일, `Legacy`라는 이름 자체는 결함이 아니다.** Room DAO의 SQL과 DB 생성·migration SQL은 유지한다. `WorkoutRoomStorage`는 기능 전용 저장 경계라는 점은 맞지만 DAO가 아니다. `FitnessDatabaseHelper`는 과거 migration에 실제로 필요하며, production 범용 `FitnessRepository`는 이미 없다.
- 이번 검증: Windows/JDK 17 대상 `./gradlew.bat testDebugUnitTest assembleDebug` **성공**. 테스트·APK 관련 작업은 `UP-TO-DATE`였으므로 새 테스트 실행으로 표현하지 않는다. instrumentation·실기기·운영 RPC/RLS는 **미수행**이다. 구조 미달 판정과 운영 장애 발생 여부는 별개다.

## 2. Required Changes

아래 ID는 구현·검증 단위의 식별자다. 모든 항목에 공통으로 `fitness_mvp.db`, Room v51, schema·데이터·migration·외부 계약을 고정한다. 새 기능, UI 재설계, multi-module, 의존성 업그레이드, Hilt·이벤트 버스·outbox, 일괄 Kotlin 변환을 포함하지 않는다.

### D1. 업무 저장을 실제 Room DAO로 전환

- **현재 코드:** [AppContainer](../../app/src/main/kotlin/com/yeonsik/fitnessapp/app/AppContainer.kt#L71)는 `FitnessDatabaseConnection.fromRoom`을 조립한다. [FitnessDatabaseConnection](../../app/src/main/java/com/yeonsik/fitnessapp/core/database/FitnessDatabaseConnection.java#L39)은 `getOpenHelper().getWritableDatabase()`를 열어 CRUD·SQL·트랜잭션을 노출한다. [WorkoutRoomStorage](../../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/workout/data/WorkoutRoomStorage.kt#L98)도 같은 연결에 직접 `query/insert/update`한다. [FitnessRoomDatabase](../../app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessRoomDatabase.kt#L99)의 DAO는 Body·Routine 두 개이며 production 호출은 없다. [BodyMetricsRepository](../../app/src/main/java/com/yeonsik/fitnessapp/data/BodyMetricsRepository.java#L48)도 선언된 Body DAO를 사용하지 않는다.
- **판정: MUST FIX.** `Repository → SQL wrapper/SupportSQLiteDatabase → SQLite`는 Room이 파일을 소유하더라도 `Repository → DAO → Room` TARGET을 충족하지 않는다.
- **이유:** 연결 관리와 업무 쿼리 소유권은 별개다. DAO 선언만 추가하거나 기존 wrapper에 `Dao`라는 이름을 붙이는 것으로 완료할 수 없다.
- **정확한 수정 범위:** `BodyMetricsRepository`, `RoutineRepository`, `SupplementRepository`, `WorkoutRoomStorage`, `CardioRepository`, `MealRecordRepository`, `NutritionCatalogRepository`, `DevelopmentRepository`의 도달 가능한 업무 CRUD·집계 SQL을 `core/database/{body,routine,supplement,workout,cardio,meal,nutrition,development,exercise}`의 Room `@Dao`로 옮긴다. 기존 Entity를 재사용하고 Body·Routine DAO를 확장한다. 검증·계산·ID/시각 생성·모델 매핑은 기능의 Repository/저장 구현에 유지한다. `WorkoutRoomStorage`는 DAO를 사용하는 기능 내부 저장 구현으로 **KEEP**한다. 기존 API·동기 실행 서명은 가능한 한 유지하고 호출자는 백그라운드에서 실행한다. 조립 때 실제 DB를 열거나 seed/claim을 실행하지 않는다.
- **금지사항:** API에 Entity·Cursor·ContentValues·Supabase JSON 노출, 범용 SQL 실행 DAO, `@RawQuery`로 업무 CRUD를 통째로 우회, 쿼리 정렬·필터·충돌 정책의 편의상 변경. `INSERT OR IGNORE/REPLACE/ABORT`, 부분 UPDATE, 누락 필드와 명시적 NULL을 기계적인 `@Upsert`/전체 Entity 덮어쓰기로 치환하지 않는다.

소유권은 다음으로 고정한다. DAO 분리는 테이블마다 Repository를 만드는 지시가 아니다.

| 소유 기능/기반 | 테이블 및 수정 경계 |
|---|---|
| workout | `workout_records`, `workout_exercises`, `workout_sets`. Cardio의 공통 기록 행도 workout 쓰기 API가 저장한다. |
| cardio | `cardio_sessions`, `cardio_route_points`. 위치 수집·거리 계산·상태 정책은 유지한다. |
| routine / exercise | `routines`, `routine_exercises` / `exercise_picker_preferences`. 즐겨찾기 등 식별 선호 저장이 필요할 때 exercise API가 소유한다. |
| supplement | `supplement_items`, `supplement_schedules`, `supplement_schedule_slots`, `supplement_intake_records`, `supplement_effect_checkins`. 복용 snapshot·기간별 schedule 의미 유지. |
| body / development | `weight_records`, `body_profiles` / `development_goals`, `nutrition_goals`, `nutrition_daily_checkins`. 뒤 두 테이블은 카탈로그가 아닌 사용자 목표·체크인이며 shared 계정 범위를 유지한다. |
| meal | `meal_records`, `meal_record_items`, `meal_record_item_nutrients`, `meal_record_item_components`, `meal_record_item_component_nutrients`, `meal_record_item_consumptions`, `meal_menu_presets`, `verified_receipt_items`. 원격 Meal 수신 기능을 새로 만들지 않는다. |
| nutrition | `nutrition_foods`, `nutrition_food_nutrients`, `nutrition_food_components`, `composition_templates`, `composition_groups`, `composition_members`, `dining_out_menu_component_links`, `dining_out_menu_add_on_links`, `product_nutrition_links`, `pricetrace_product_cache`. 각 테이블의 기존 `owner_id/user_id`와 프로젝트 범위를 그대로 사용한다. |
| core account / sync 기반 | `devices` / `sync_state`. 업무 데이터 claim은 D3의 기능 API를 통한다. cursor의 scope key·방향·버전·ID 비교는 유지한다. |

현재 production에서 사용하지 않는 테이블은 backup/compatibility 저장에 필요한 DAO만 둔다. 이를 이유로 CRUD 화면이나 신규 기능 Repository를 만들지 않는다.

DAO의 owner 조건에는 작업 시작 시 캡처한 값을 전달한다. 변경 가능한 Repository의 현재 사용자 값을 비동기 실행 시 다시 읽지 않는다. 같은 기능 Service의 구체 Repository 의존은 기존 기능 API로 교체하며, Body에는 빠진 `feature/body/api/BodyMetricsRepositoryApi`를 추가한다. Repository 구현을 호출자가 직접 만들지 않는다.

### D2. 교차 기능 조회와 Cardio 쓰기 소유권 정리

- **현재 코드:** [RoomHomeReadSources](../../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/home/data/HomeReadSources.kt#L33)는 `WorkoutRoomStorage`를 직접 만들고 Meal·Body·목표 SQL도 실행한다. [DevelopmentRepository](../../app/src/main/java/com/yeonsik/fitnessapp/development/DevelopmentRepository.java#L163)와 [PaperAdviceSnapshotAssembler](../../app/src/main/java/com/yeonsik/fitnessapp/development/PaperAdviceSnapshotAssembler.java#L144)는 다른 기능 테이블을 직접 집계한다. [CardioRepository](../../app/src/main/java/com/yeonsik/fitnessapp/cardio/CardioRepository.java#L372)의 완료 트랜잭션에는 `workout_records` 쓰기가 있다. [WorkoutSessionApplicationService.delete](../../app/src/main/java/com/yeonsik/fitnessapp/feature/workout/application/WorkoutSessionApplicationService.java#L62)는 두 저장 작업을 호출하지만 공통 트랜잭션이 없다.
- **판정: MUST FIX.** 읽기 전용이라는 이유로 다른 기능의 저장 구현·DAO에 직접 접근할 수 없다. Cardio↔Workout Repository 상호 호출이 없다는 점은 맞지만, 공통 기록 쓰기 책임은 아직 분리되지 않았다.
- **이유:** SQL을 DAO로 옮겨도 Home/Development가 모든 DAO를 받거나 Cardio DAO가 운동 기록을 쓰면 기능 경계는 완성되지 않는다.
- **정확한 수정 범위:** `HomeReadSources` 인터페이스와 `HomeReadRepository` 집계 모델은 유지하되 구현은 Workout·Meal·Body·Development의 읽기 API를 주입받도록 교체하고 `RoomHomeReadSources`는 제거한다. `DevelopmentRepository`의 보고서 조립과 `PaperAdviceSnapshotAssembler`는 `feature/development/application/DevelopmentReportService` 아래 조회 조립 책임으로 옮긴다. Body profile CRUD는 Body API로 이동한다. 각 집계 SQL은 원 소유 기능 DAO에 두고 집계에 필요한 중립 모델만 반환한다. 기존 Home 데이터를 사용하는 Records 등은 읽기 모델을 재사용할 수 있지만 다른 기능 ViewModel을 조회 창구로 삼지 않는다. `CardioSessionApplicationService`는 Cardio·Workout API를 조정하고 완료 부분은 `CompleteCardio`로 분리한다. 시작·완료·심박 수정·취소의 공통 기록 쓰기는 Workout API, GPS 상태/좌표 쓰기는 Cardio API가 수행한다. `WorkoutSessionApplicationService.delete`도 같은 DB 트랜잭션으로 묶는다. `core/database/RoomTransactionRunner` 한 개를 AppContainer에서 주입해 기존 업무 트랜잭션과 이 교차 작업을 `RoomDatabase.runInTransaction`으로 실행한다.
- **금지사항:** Home 전용 만능 DAO, 기능 간 `.data`/DAO 접근, Cardio↔Workout Repository 호출, 집계 공식·날짜 구간·NULL 처리 수정, DB 트랜잭션 안의 네트워크·파일 I/O. GPS 서비스 동작과 거리 계산을 Compose로 옮기지 않는다.

읽기 API는 `feature/{workout,meal,body,development}/api`의 `{Workout,Meal,BodyMetrics,Development}ReadApi`로 고정한다. 기존 집계 helper의 입력/결과를 유지해 해당 소유자로 옮기고, 여러 기능을 합치는 집합/보고서 조립만 소비 Service에 둔다. `HomeReadSources`의 Workout 관련 4개 메서드는 Workout, 식사 3개는 Meal, 신체 2개는 Body, 목표 1개는 Development API로 위임한다. `DevelopmentRepositoryApi.buildReport`는 `DevelopmentReportService`의 읽기 계약으로 이동하며 저장 Repository에 보고서 위임 façade를 남기지 않는다. Cardio의 기존 읽기 전용 API는 lifecycle·route 작업까지 확장한다.

### D3. Backup·Transfer·Summary·Sync·계정·seed의 SQL 제거

- **현재 코드:** [LocalDataTransferApplicationService](../../app/src/main/java/com/yeonsik/fitnessapp/integration/transfer/LocalDataTransferApplicationService.java#L26)는 연결을 보관해 backup을 구성한다. [WorkoutInterchangeStore](../../app/src/main/java/com/yeonsik/fitnessapp/integration/workout/WorkoutInterchangeStore.java#L43), [FitnessSummaryStore](../../app/src/main/java/com/yeonsik/fitnessapp/integration/personalos/FitnessSummaryStore.java#L31), [LegacyFitnessSyncAdapter](../../app/src/main/java/com/yeonsik/fitnessapp/sync/LegacyFitnessSyncAdapter.java), [AccountOwnershipService](../../app/src/main/java/com/yeonsik/fitnessapp/core/account/AccountOwnershipService.java#L33)는 직접 SQL을 실행한다. [LocalDataBackupService.restoreBackup](../../app/src/main/java/com/yeonsik/fitnessapp/data/LocalDataBackupService.java#L135)는 복원 트랜잭션 안에서 `reconcileVerifiedFoodCatalog()`를 호출한다. 따라서 이 seed 경로는 migration 전용이 아니다.
- **판정: MUST FIX** 저장 접근, **KEEP** 계약·codec·동기화 알고리즘, **REMOVE** 마지막 production 사용자를 옮긴 뒤 `FitnessDatabaseConnection`과 `fromLegacy` 생성 경로.
- **이유:** 연동 Service로 파일을 분리한 것만으로 기능 API/DAO 경계가 완성되지 않는다. 반대로 legacy sync 프로토콜을 제거하면 호환성을 깨뜨린다.
- **정확한 수정 범위:** Transfer/FLEEK의 DTO 해석·매핑은 `integration/transfer`에 두고, `WorkoutInterchangeStore`의 SQL·정규화 저장은 `feature/workout/data`로 옮겨 `feature/workout/api`의 import/export API로 노출한다. `FitnessSummaryStore`의 조회·기존 summary 보정 쓰기도 Workout 읽기/보정 API로 옮기며 `integration/personalos`에는 v2 DTO 매핑·발행을 남긴다. legacy sync의 전송·충돌/NULL/누락/로컬 보존 정책은 유지하되 각 기능의 명시적 sync 저장 API와 `SyncState` 저장 기반을 호출한다. Backup은 `backup`의 형식/검증/파일 Service와 `core/database/backup`의 저장 구현으로 분리한다. 이 저장 구현만 기존 `TABLE_ORDER` 전체를 Room DAO로 읽고 복원할 수 있다. `AccountOwnershipService`는 기존 순서·조건을 유지해 기능별 claim API와 Device DAO 저장 구현을 한 트랜잭션으로 조정한다. restore 시 seed는 Nutrition의 DAO 기반 catalog reconciliation을 같은 트랜잭션에서 호출한다. 과거 migration의 SQL seed와 검증된 seed 데이터/파싱은 유지한다.
- **금지사항:** legacy v1 중단·공유 범위 확대, v2 실패로 로컬 저장 롤백, backup 형식/테이블 목록 변경, 전송 중복 키 변경, 카탈로그로 Meal snapshot 갱신, 로그인 claim 정책 재설계. DAO 교체와 외부 계약 변경을 묶지 않는다.

**Backup 예외의 한계:** 여러 테이블의 원형 보존을 위한 `core/database/backup`은 기능 API 경유의 유일한 데이터 복원 예외다. 기존 allowlist의 schema 확인(`PRAGMA table_info`)·동적 행 읽기에 한해 Room `@RawQuery`와 내부 Cursor를 허용한다. 파라미터 바인딩과 기존 테이블/열 allowlist를 사용하고 Cursor를 저장 경계 밖으로 반환하지 않는다. 쓰기는 테이블별 선언 DAO로 실행한다. 일반 화면·Repository·Sync에 이 예외를 재사용하지 않는다. DB 생성·migration 외의 `SupportSQLiteDatabase` 직접 CRUD는 최종 production에서 허용하지 않는다.

호환 저장 API는 기능별 `api`에 두고 기존 Repository/저장 구현이 구현한다. Workout에는 `WorkoutInterchangeApi`(전송/FLEEK), `WorkoutSummaryApi`(기존 보정·발행용 읽기)를 둔다. v1 sync는 실제 `TABLES`의 Workout·Meal·Body·Device만 대상으로 `*SyncStoreApi`를 사용하며 새 동기화 테이블을 추가하지 않는다. JSON 누락/명시적 NULL은 mapper의 필드 존재 정보로 보존한다. Backup typed row 변환도 누락 열의 기존 SQLite default와 명시적 NULL을 구분하고 현행 IGNORE/ABORT 동작을 보존한다. 파일 형식의 JSON은 파일 Service 경계에 남긴다.

### D4. Nutrition의 실제 네트워크 책임 이동

- **현재 코드:** [NutritionIntegrationService](../../app/src/main/java/com/yeonsik/fitnessapp/integration/nutrition/NutritionIntegrationService.java)는 조정 역할을 하지만 [NutritionCatalogRepository](../../app/src/main/java/com/yeonsik/fitnessapp/data/NutritionCatalogRepository.java#L1898)의 공개 메서드와 `syncRemote`, `fetchPublicProductNutrition`, `openConnection`에 HTTP·RPC 실행이 남아 있다. 이 Repository는 단순한 안정 Java 로컬 저장 코드가 아니다.
- **판정: MUST FIX.** 로컬 카탈로그와 네트워크 계약 어댑터를 분리한다.
- **이유:** 외부 Service가 다시 네트워크를 포함한 Repository를 호출하는 구조는 TARGET §5 및 Stage 1의 책임 분리를 충족하지 못한다.
- **정확한 수정 범위:** 위 공개/동기화/원격 조회 메서드의 HTTP·RPC·전송 DTO/JSON 매핑과 private 네트워크 helper를 `integration/nutrition`으로 옮긴다. PriceTrace 요청 부분은 `integration/pricetrace`의 기존 client/추출 어댑터가 담당하고 `NutritionIntegrationService`가 둘을 조정한다. 로컬 조회·캐시·링크 승인/거절·공개 상태 반영은 Nutrition의 중립 API로 호출한다. `MealRecordRepository`의 구체 `NutritionCatalogRepository` 의존성도 카탈로그 읽기 API로 교체한다. 기존 UI용 통합 결과 모델은 재사용하며 JSON은 기능 API를 통과시키지 않는다.
- **금지사항:** Nutrition/Personal OS/PriceTrace 세션 합치기, 사용자 ID 동일 가정, RPC 이름·payload·인증 갱신·fallback·공개 순서 변경. 이 보완은 운영 RPC/RLS 변경을 요구하지 않는다.

### U1. Stage6 공통 UI 어댑터 제거

- **현재 코드:** [Stage6ComposePrimitives](../../app/src/main/kotlin/com/yeonsik/fitnessapp/app/navigation/Stage6ComposePrimitives.kt)는 참조 없는 `Stage6* → Fitness*` 래퍼와 실제 사용하는 `App*` 구현을 함께 담는다. [FitnessComposeComponents](../../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessComposeComponents.kt)는 별도 구현이다. 예를 들어 `AppTextField`의 기본 IME/포커스 동작은 `FitnessTextField`와 다르며 Header·버튼 여백·색·shape도 같지 않다.
- **판정: REMOVE** `Stage6*` 래퍼·별칭·원본 파일, **KEEP** 현재 `App*`의 표시/입력 동작.
- **이유:** `Stage6*`는 불필요한 전환 코드다. 그러나 `App*`를 일괄 `Fitness*`로 치환하면 구조 변경에 UI/입력 변경을 섞게 된다.
- **정확한 수정 범위:** `AppSpacing`, `AppHeader/Card/Button/OutlinedButton/TextField/DataRow` 구현을 그대로 `core/ui/AppComposeComponents.kt`로 이동한다. [ComposeAppScreen](../../app/src/main/kotlin/com/yeonsik/fitnessapp/app/navigation/ComposeAppScreen.kt)의 공통 `StateMessage`도 core UI로 이동한다. 호출부 import만 바꾼다. 두 컴포넌트 스타일은 기존 화면 호환 변형으로 의도적으로 공존한다.
- **금지사항:** 새 wrapper/alias로 Stage6 이름 유지, `App*`와 `Fitness*`의 강제 통합, 시각 디자인·IME·키보드 액션·접근성 의미 변경.

### U2. ScreenHost의 업무 책임을 기능 ViewModel로 이동

- **현재 코드:** [ScreenHost](../../app/src/main/java/com/yeonsik/fitnessapp/ui/ScreenHost.java)는 VM 제공, 운동/유산소 실행, body/development 편집, 인증·공개·동기화·파일 작업을 모두 노출한다. [MainActivity](../../app/src/main/java/com/yeonsik/fitnessapp/MainActivity.java#L1730)는 executor로 Service를 실행하고 결과 상태·폼을 소유한다. [SettingsScreen](../../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/settings/ui/SettingsScreen.kt#L45)은 Host에서 업무 상태를 읽고, [MealScreen](../../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/meal/ui/MealScreen.kt#L204)은 Host 원격 callback을 직접 받는다.
- **판정: MUST FIX** Activity의 업무/폼 상태 소유, **REMOVE** 최종 `ScreenHost` 인터페이스. 현재 이름으로 축소 façade를 영구 보존하지 않는다.
- **이유:** 화면 → Host → Activity → Service 경로는 화면 → ViewModel → API/Service TARGET을 우회한다. 이미 ViewModel을 사용하는 세트 저장·초기화 로직은 재작성 대상이 아니다.
- **정확한 수정 범위:** 아래 표대로 기존 VM을 확장하고 빠진 VM만 만든다. 각 Compose 화면은 명시적인 UiState·사용자 액션·화면 이동 callback을 받는다. `app/navigation`의 route 조립부가 VM을 연결하며 기능 UI는 다른 기능 VM, AppContainer, Activity, ScreenHost를 받지 않는다. 비동기 결과는 시작 당시 owner·프로젝트·요청 세대로 확인하고 현재 화면에 한 번만 반영한다. 미완성 입력과 저장 모델을 구분하며 복구용 ID·초안은 SavedStateHandle에 둔다.
- **금지사항:** 거대 Host를 거대 `AppViewModel`로 이름만 바꾸기, Compose 재구성에서 저장 실행, 폼 저장 실패 시 초안 폐기, 완료 전에 제출된 세트 쓰기를 추월하기, scope를 실행 도중 다른 계정으로 치환하기.

| MainActivity/Host에서 옮길 책임 | 확정 소유자 |
|---|---|
| `openRecord`, 이어하기, 빈/루틴/과거 세션 생성, 삭제, 완료 및 관련 확인/입력 상태 | `feature/workout/ui/WorkoutSessionViewModel`; 기존 `WorkoutSessionApplicationService`·`CompleteWorkout` 사용. 읽기/상세 화면은 기존 VM 유지. |
| 시작/재개/정지/완료/취소, route 로딩, 심박 입력·수정 및 권한 결과 후의 업무 판단 | `feature/cardio/ui/CardioSessionViewModel`; `CardioSessionApplicationService`·`CompleteCardio` 사용. OS 권한 요청·서비스 Intent 실행만 플랫폼으로 전달. |
| 체중·신장 profile 폼/저장/삭제 | 새 `feature/body/ui/BodyMetricsViewModel`; `BodyMetricsApplicationService` 확장. Development 화면도 이 편집 경로를 연다. |
| development goal 폼/저장, insight 액션 판단 | 기존 `DevelopmentViewModel`·`DevelopmentApplicationService`; 보고서는 D2 읽기 Service 사용. |
| Meal의 PriceTrace 검색/상세/선택 비동기 상태 | 기존 `MealViewModel`에 통합 Service 주입. Nutrition 원격 공개/검색의 미사용 Host 메서드는 삭제하며 새 화면은 만들지 않는다. |
| 테마·단위 설정, 프로젝트별 인증/설정, manual sync, backup/CSV/FLEEK/Transfer 진행·결과·복원 확인 | 새 `feature/settings/ui/SettingsViewModel`; 기존 설정 저장소와 통합 Service 사용. URI 선택/stream 취득은 플랫폼 어댑터가 제공하고 읽기·쓰기는 백그라운드 Service가 수행. |
| 휴식 타이머 만료 시각·시작/정지·표시 상태 | `WorkoutSessionViewModel`; 현행 절대 만료 시각·기본 90초·화면별 표시 규칙 유지. 새 백그라운드 타이머 기능은 추가하지 않음. |

세트 저장과 완료는 AppContainer가 주입하는 **동일 workout 직렬 작업 큐**를 사용한다. 이미 제출된 쓰기 성공 후 완료를 실행하고, 실패하면 완료하지 않고 실패 상태를 유지한다. 자동 저장이나 미제출 초안의 자동 완료는 추가하지 않는다. 현행 별도 executor 사이 순서를 보장한다고 가정하지 않는다.

### U3. Activity의 View/Compose 이중 화면 소유 제거

- **현재 코드:** [MainActivity.buildRootView](../../app/src/main/java/com/yeonsik/fitnessapp/MainActivity.java#L848)는 ScrollView·상하 세션 바·타이머·하단 탐색을 생성한다. [render/composeViewFor](../../app/src/main/java/com/yeonsik/fitnessapp/MainActivity.java#L1372)는 화면별 ComposeView를 캐시·탈착하고 `prepareScreenEntry`를 호출한다. `ComposeAppScreen`은 전달받은 화면을 표시할 뿐 전체 내비게이션 소유자가 아니다.
- **판정: MUST FIX** Compose 루트/내비게이션 전환, **REMOVE** 화면별 ComposeView 캐시·View shell·수동 rerender, **KEEP** Java `MainActivity`의 플랫폼 진입점.
- **이유:** 주요 화면이 Compose인 것과 앱 전체 내비게이션이 Compose로 이동한 것은 다르다. 여기의 일반 View shell·업무 다이얼로그는 지도/해부학 같은 특수 View 예외가 아니다.
- **정확한 수정 범위:** `ComposeAppScreen.install`을 Activity 수명 동안 한 개의 루트 ComposeView를 설치하는 진입점으로 바꾼다. HOME도 같은 root destination에 포함한다. `app/navigation/AppNavigationViewModel`은 SavedStateHandle로 현재 화면/이력·선택 날짜·routine/record/exercise ID만 소유하고 기존 `FitnessNavigationHistory` push/back/replace 정책을 재사용한다. route 진입 초기화는 owner·대상 ID가 바뀔 때만 수행한다. 상하 세션 바·타이머·하단 탐색·U2 폼/확인은 기존 외형과 조작을 보존해 Compose로 옮긴다. Activity에는 lifecycle, window/insets, permissions, Intent/URI 결과, 시스템 설정 열기, GPS 서비스 명령 전달, debug 진입 처리의 플랫폼 부분만 남긴다. AppContainer가 VM factory와 Activity/Service용 의존성을 조립한다.
- **금지사항:** 새 내비게이션 라이브러리 도입, 탭/Back/replace 의미 변경, Activity의 업무 executor 유지, 이동 때마다 root 재설치, 특수 View 재작성. GPS 서비스는 같은 `FitnessRoomDatabaseProvider`의 DB를 사용하도록 유지한다.

### L1. Java·패키지·미사용 코드의 처리

- **현재 코드:** `feature`의 Kotlin API와 `data/cardio/routine/development/supplement/ui`의 Java 구현이 공존한다. [FitnessDatabaseHelper.migrateHistoricalSchema](../../app/src/main/java/com/yeonsik/fitnessapp/data/FitnessDatabaseHelper.java#L46)는 Room migration이 실제 호출한다. 반면 `CompositionTemplateRepository`는 production 조립/호출이 없고 androidTest fixture가 사용한다. `ui`의 구형 입력 컴포넌트에도 production 진입점이 없는 묶음이 남아 있다.
- **판정: MUST FIX** 수정되는 실행 책임의 소유 패키지, **KEEP** 안정된 Java leaf/계약/특수 렌더러, **REMOVE** 불필요한 production 전환 코드. Java 수·파일 줄 수·옛 디렉터리 존재를 종료 지표로 삼지 않는다.
- **이유:** 실제 부채는 D1~D4·U1~U3의 의존성/책임이다. 안정 모델·계산기·fixture까지 변환하거나 이동하면 API 차이와 회귀 위험만 늘어난다.
- **정확한 수정 범위:** 수정한 `BodyMetrics/Cardio/Routine/Supplement/NutritionCatalog/DevelopmentRepository`는 해당 `feature/*/data`로, 보고서 조립은 `feature/development/application`으로 이동한다. `LocalDataBackupService`는 `backup`, `WorkoutTransferService/Codec`, `FleekCsvImporter`, interchange DTO/mapper는 `integration/transfer`, `ProductReadV1Client/RestaurantMenuReadV1Client`는 `integration/pricetrace`로 이동한다. `SupabaseSyncManager/LegacyFitnessSyncAdapter`의 프로토콜 조정은 `integration/personalos`로 모은다. 패키지 이동은 동작/DAO 변경과 **별도 커밋**이다. 아래 제거/보존 목록 외의 안정 Java 모델·정책·계약·계산기는 현재 package와 언어를 유지하며 새 실행 책임을 추가하지 않는다.
- **금지사항:** production 호환 클래스 재생성, 의미 없는 Java→Kotlin 변환, 모든 파일의 TARGET 디렉터리 기계적 재배치, 미사용 기능을 복구하는 목적의 production 재연결, 테스트가 필요하다는 이유로 runtime SQL 어댑터 존치.

| production에서 제거할 대상 | 처리 |
|---|---|
| `FitnessDatabaseConnection` 및 소비자의 legacy helper 생성자 | D1~D3 전환 후 제거. 과거 fixture에 필요한 연결은 `androidTest`에 한정하고, 새 Repository 검증은 실제 Room을 조립한다. |
| `CompositionTemplateRepository` | 현재 소비자가 있는 `androidTest`의 호환 fixture로 이동. 운영 DAO 전환 대상으로 오인하지 않는다. 관련 테이블·백업은 보존. |
| `ui/ExerciseVariantPickerDialog`, `NutritionInputField`, `NutritionInputSection`, `NutritionUnitPreview`, `NutritionUnitSelector` | production 진입점 없는 구형 View 묶음 제거. 현재 Compose의 기능을 재구현하지 않는다. |
| `ui/FormSystem`, `MealProductSelectionDraft`, `routine/WorkoutRoutineMapper` | 각각 참조하는 androidTest 또는 test source set으로 옮겨 기존 회귀 fixture 유지. |
| `FitnessUi`·`NutritionRow`의 구형 View 생성 부분 | U3 뒤 retained renderer의 실제 호출 부분만 남긴다. 사용 중인 색/간격/shape token·formatting·`NutritionRow.displayLabel/displayUnit`은 동일 값/서명으로 `core/ui`의 공통 기반으로 추출한다. 클래스 전체를 미사용으로 간주하지 않는다. |

위 제거는 production source set 기준이다. `FormSystemStateTest` 등 보존 테스트가 필요로 하는 옛 View의 전이 의존성은 함께 androidTest fixture로 옮긴다. 순수 label/unit 추출 후의 구형 `NutritionRow`도 여기에 포함한다. fixture가 참조하는 View helper를 없애 테스트를 삭제하거나, 테스트만을 위해 production helper를 되살리지 않는다.

## 3. Implementation Order

각 행은 별도 검증·커밋 가능한 최소 단위이며 여러 기능을 한꺼번에 교체하지 않는다. 데이터 경로와 UI 경로는 서로의 구현을 선행 조건으로 삼지 않고 기존 API로 진행한다.

| 순서/단위 | 선행 조건과 범위 |
|---|---|
| 0 | 아래 기준 fixture를 실제 production Room 조립에도 실행하는 검증 경로 확보. 기존 legacy fixture는 보존. |
| D1-a → b → c | Body → Routine → Supplement를 각각 DAO로 교체. |
| D1-d | Workout DAO 교체. 세트/세션/삭제/집계 쿼리와 기존 트랜잭션 유지. |
| D1-e + D2-cardio | Workout API 준비 후 Cardio DAO와 교차 기록 쓰기를 함께 전환. 시작/완료/취소·삭제 원자성을 분리 커밋으로 깨뜨리지 않음. |
| D1-f → g → h | Meal → Nutrition → Development 저장을 각각 전환. Body profile 이전은 Body 완료 후. |
| D2-read | 해당 소유 기능 API 준비 후 Home 읽기, Development/PaperAdvice 읽기를 각각 전환. SQL 식·결과는 유지. |
| D4 | Nutrition 네트워크 추출. DAO 교체와 독립 실행 가능하며 외부 동작 변경은 없음. |
| D3-a → b → c → d | Workout Transfer/FLEEK → Summary/legacy sync → account claim → backup/restore seed. 해당 기능 DAO/API 완료 후 각각 전환하고 마지막에 connection 제거. |
| U1 | 공통 UI 이동·Stage6 래퍼 제거. 데이터 작업과 독립. |
| U2-a → b → c → d → e | Workout/타이머 → Cardio → Body/Development 편집 → Meal 원격 선택 → Settings/파일/인증. 각 흐름의 상태/액션을 옮기며 Host를 줄임. VM은 다른 흐름 변경 없이 검증 가능해야 함. |
| U3 | U1/U2 후 단일 Compose root·내비게이션으로 전환하고 ScreenHost/View shell 제거. |
| L1 | 각 책임 이동 직후 package-only 커밋. 미사용 View/fixture 이동은 독립 수행 가능. 최종으로 아래 구조 gate 실행. |

D2의 삭제 원자성 보강과 U2의 세트 쓰기/완료 순서 보강은 별도 fix 단위로 검증·커밋한다. 기존 성공 결과를 보존하되 실패/경합 동작의 보완을 단순 DAO 치환이나 package 이동에 숨기지 않는다.

## 4. Acceptance Criteria

| 대상 | 검증 가능한 종료 조건 |
|---|---|
| D1 각 기능 | production 조립이 해당 DAO를 실제 호출한다. API의 SQL/Entity 누출 및 해당 구현의 직접 DB CRUD가 0이다. 변경 전후 같은 fixture에서 정렬된 모든 열 값·NULL·ID·owner·tombstone·단위·영양 snapshot·cursor가 같고 업무 입력/결과가 같다. |
| D2 | Home/Development/Records 조회가 공개 읽기 API만 사용한다. DB/다른 기능 data import가 0이다. 고정 날짜의 Home/보고서 결과가 동일하다. Cardio 시작/완료/취소 및 workout 삭제 중 두 번째 저장 실패를 주입하면 첫 저장도 rollback한다. 두 Repository는 서로 참조하지 않는다. |
| D3 | 각 전송/계정/복원 흐름이 새 API/DAO를 사용한다. `TABLE_ORDER`·충돌/누락 필드·중복 처리·계정 재매핑·영양 NULL/과거 0 의미·Summary v2 tombstone가 동일하다. 복원 도중 seed 실패도 전체 rollback한다. production `FitnessDatabaseConnection/fromLegacy`가 0이다. |
| D4 | Nutrition Repository의 HTTP/RPC 실행과 인증 설정 의존이 0이다. 동일 fixture의 요청 method/path/payload·응답 매핑·실패/fallback 동작이 동일하고 프로젝트 세션이 분리된다. |
| U1 | `Stage6ComposePrimitives` 파일·`Stage6*` 참조가 0이다. UI 공통 컴포넌트를 위해 feature가 `app.navigation`을 import하지 않는다. 기존 화면별 외형·텍스트·버튼 상태·IME/포커스가 동일하다. |
| U2 각 흐름 | 대응 MainActivity/Host 업무 메서드와 업무 상태가 사라진다. VM 단위로 성공·실패·계정 전환·뒤늦은 응답을 검증한다. 빠른 세트 저장 직후 완료가 쓰기를 추월하지 않으며 저장 실패 시 완료되지 않는다. 회전/재생성 후 미완성 초안은 유지되고 완료/삭제/내비게이션 이벤트는 중복 실행되지 않는다. |
| U3 | Activity 수명 동안 root ComposeView는 1개다. `composeEntries`, `removeAllViews` 기반 화면 전환, `rerender`, ScreenHost, 일반 View shell/업무 폼이 없다. HOME 포함 모든 destination, 탭 재선택, Back, replace, 선택 ID·날짜 복구, 키보드/insets, 타이머, GPS permission/서비스 복귀를 검증한다. |
| L1/최종 gate | main·manifest·서비스에서 legacy SQL fixture나 제거 View로 도달하는 경로가 없다. 예외는 §5만 허용한다. 정적 import/호출 검사와 실제 AppContainer/Service 조립 테스트를 함께 사용하며 금지어 grep만으로 완료하지 않는다. |

검증 실행 조건:

- 매 구현 단위에서 `./gradlew.bat testDebugUnitTest assembleDebug` 성공. DB/Repository 변경은 연결된 테스트 기기의 `./gradlew.bat connectedDebugAndroidTest`로 관련 instrumentation을 실행한다. 장치가 없으면 해당 단위는 검증 미완료로 기록하고 TARGET 완료를 선언하지 않는다.
- Room 기준은 [FitnessRoomHandoffMigrationTest](../../app/src/androidTest/java/com/yeonsik/fitnessapp/core/database/FitnessRoomHandoffMigrationTest.java)를 확장해 신규 v51, v50 인수, v8 업그레이드, 등록된 v8~v50 경로를 검사한다. 기존 DB 이름·v51 DDL, CHECK·COLLATE·partial/unique index·PK nullability, `sqlite_sequence`를 보존한다. 지원 버전 fixture의 행/값 비교 없이 migration 성공으로 간주하지 않는다.
- `FitnessStageZeroBaselineTest`, `FitnessDatabaseMigrationTest`, `LocalDataBackupServiceTest`, `WorkoutTransferRepositoryTest`, `SupabaseSyncManagerLocalPreservationTest`, `MealRecordRepositoryTest`의 보존 시나리오를 새 Room production 구현에도 연결한다. **legacy helper/테스트 전용 FitnessRepository를 통과한 결과만으로 새 runtime을 검증했다고 주장하지 않는다.** Transfer v1/v2 왕복·중복, LoadState/Family/Preset/variant·kg/lb·원래 입력값, Meal snapshot, 로그아웃 및 서로 다른 두 계정, v1 sync 로컬 보존, v2 실패/tombstone를 포함한다.
- U2/U3는 입력 초안·키보드·포커스·뒤로가기·재생성·빠른 연속 입력·운동 완료와 프로세스 재시작 후 DB 복구를 확인한다. 에뮬레이터 instrumentation과 실기기 GPS/입력 확인은 구분해서 기록한다.
- 이 문서의 완료 조건은 로컬 아키텍처 보완이다. 단위 테스트 / Android 빌드 / instrumentation / 실기기 / 운영 RPC·RLS 상태를 별도 기록한다. 운영 미검증을 release 완료나 두 원격 계정 격리 검증으로 대체하지 않는다.

## 5. Intentional Legacy / Compatibility

| 의도적 유지 대상 | 이유와 허용 경계 |
|---|---|
| `FitnessRoomDatabaseProvider`, `FitnessRoomOpenHelperFactory`, `FitnessRoomFreshSchema` | 같은 DB의 단일 소유자와 신규 설치 DDL 보존. custom factory는 Room callback에 위임하며 별도 runtime DB 관리자가 아니다. 제거하면 현재 CHECK/COLLATE/추가 index가 달라질 수 있다. |
| `FitnessRoomMigrations`, `FitnessPrimaryKeyCompatibility`, `FitnessDatabaseContract`, `FitnessDatabaseHelper`, `LegacyMigrationDatabase` | 과거 upgrade·PK 정규화·schema 검증을 위해 KEEP. Helper의 main 잔존은 허용하되 Room migration이 공급한 연결만 사용하고 runtime에서 helper로 파일을 열지 않는다. 이름 변경/파일 분할은 필수 아님. |
| `FitnessRoomLegacyEntities` 및 기존 Body/Routine Entity | 실제 v51 테이블 매핑이다. 이름의 Legacy는 삭제 사유가 아니며 schema 재생성·버전 증가가 필요 없다. |
| migration용 `VerifiedFoodCatalogSeed` SQL·seed assets/파싱 | 과거 migration을 보존한다. runtime restore reconciliation만 D3의 DAO 구현 사용. seed 내용/계산 수정 금지. |
| `src/test`, `src/androidTest`의 `FitnessRepository`와 이전한 compatibility fixture | 구형 계약/DB를 재현하는 기준. production 의존은 금지. |
| `LegacyFitnessSyncAdapter`, `SupabaseSyncManager`의 v1/v2 조정 정책, `FitnessSummaryPublisher` | 소비자 전환·복구·계약 검증 없이는 v1을 제거하지 않는다. v2 실패 상태/재시도와 기존 전송 순서는 유지한다. 저장 SQL만 교체한다. |
| Fitness Record v1/`sync_fitness_data_v1`, Summary v2, Workout Transfer v1/v2, local backup v1, Nutrition/Meal/PriceTrace/CashOS 계약 및 codec | 외부 호환 Source of Truth. DTO/JSON 필드·ID·누락/NULL·버전은 유지한다. 새 상세 데이터를 Personal OS 공유에 추가하지 않는다. |
| Java 17 모델·계산·정책·카탈로그 | `LoadState`, `MassUnit/Formatter`, `ExerciseVolumeCalculator`, exercise identity/runtime catalog, `NutritionProfile/Calculator`, Meal snapshot/policy, `PaperAdviceEngine`, supplement evidence 등은 안정 leaf 코드로 KEEP. 현행 package 참조를 허용하되 다른 기능의 실행 구현을 호출하는 구실로 쓰지 않는다. |
| `FitnessNavigationHistory`, `FitnessScreen`, `WorkoutSessionState`의 순수 상태/정책 | Java 구현과 기존 enum·복구 키 의미 유지. UI 상태 소유자는 U2/U3 VM으로 옮긴다. |
| `CardioTrackingService`, `CardioRouteMap`의 지도 View, `ExerciseIllustrationPreview/ExerciseMuscleModelRenderer`, 생성 이미지 카탈로그 | 플랫폼 수집·특수 렌더링은 안정 상호운용 경계로 KEEP. 필요한 FitnessUi View helper도 이 경계 안에 유지한다. 이미지 소스·생성 drawable·Java catalog 수정 금지. |
| `core/ui`로 옮긴 App*와 기존 Fitness* | 실제 화면의 서로 다른 표시/입력 동작을 보존하는 공통 컴포넌트. Stage6 forwarding façade는 유지하지 않음. |

## 6. Final Target

```text
Java MainActivity / CardioTrackingService: lifecycle·OS 입출력
    AppContainer: 동일 FitnessRoomDatabaseProvider + API/Service/VM factory 조립
    Compose root: navigation state + 화면별 UiState/사용자 액션
        → 기능 ViewModel → 기능 API 또는 복합 Application Service
            → 기능 Repository/저장 구현 → 기능 Room DAO → 단일 RoomDatabase → fitness_mvp.db v51

Home/Records/Development 조회 Service → 각 기능 읽기 API → 화면용 집계 모델
Cardio 복합 작업 → Cardio API + Workout API → 동일 Room transaction
외부 연동 → 계약 DTO/Mapper → 기능 API → DAO
Backup 형식/파일 Service → 전용 backup 저장 구현 → Room DAO
DB create/migration만 → SupportSQLiteDatabase 직접 접근 허용
업무/backup SQL → 선언된 Room DAO 내부에서만 실행
```

production은 **SQL 연결 façade, 화면 Host façade, Activity 업무 실행, 다른 기능 저장 구현 직접 접근 없이** 동작해야 한다. 사용자 데이터·외부 계약·화면 동작은 그대로 유지한다. 단일 모듈·Kotlin 중심 기능 코드와 §5의 Java/특수 View/호환 코드 공존을 최종 구조로 확정한다. §2의 모든 MUST FIX/REMOVE와 §4의 검증을 충족해야 Stage 6 TARGET 완료로 판정한다.
