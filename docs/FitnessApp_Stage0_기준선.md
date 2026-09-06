# FitnessApp Stage 0 기준선

이 문서는 `FitnessApp_정돈_리팩터링_규칙.md`의 Stage 0에서 고정한 기준선이다. 이후 Java 책임 분리는 아래의 데이터·계약·표현을 변경하지 않은 채 기존 파사드가 새 구현에 위임하는 방식으로만 진행한다.

## 기준

- 기준 main: `cedaf614b642196bf7eeeda6ad4f259c095d725d`
- 로컬 DB: `fitness_mvp.db`, `FitnessDatabaseHelper.DATABASE_VERSION = 50`
- 백업: `fitness-os.local-backup`, format version `1`
- 금지: Kotlin, Room, Compose, DB schema 및 외부 Contract 변경

## schema 기준

- 신규 설치 v50의 테이블·핵심 column·index는 `FitnessStageZeroBaselineTest.newInstallMatchesV50SchemaBaseline`이 고정한다.
- v8 fixture에서 현재 schema로의 업그레이드와 기존 행 보존은 `FitnessDatabaseMigrationTest.testV8UpgradeAddsLateColumnsBeforeTheirIndexesAndPreservesLocalOwnership`이 고정한다.
- 이 단계에서는 `onCreate`, `onUpgrade`, `DATABASE_NAME`, `DATABASE_VERSION`을 변경하지 않는다.

## 외부 Contract 기준

| 경계 | 고정된 구현/회귀 테스트 |
|---|---|
| Fitness Record Contract v1, `sync_fitness_data_v1` | `FitnessRecordContractTest`, `SupabaseSyncManagerTest` |
| Fitness Summary Projection v2 | `FitnessSummaryProjectionV2Test`, `FitnessRepositorySummaryProjectionV2Test` |
| Nutrition verified import v1·canonical provenance v2 | `NutritionVerifiedImportContractTest`, `NutritionCanonicalProvenanceContractTest` |
| Meal verified ingest v1·component estimate v1 | `VerifiedMealImportContractTest`, `MealItemSnapshotTest` |
| PriceTrace·CashOS | `ProductNutritionLinkContractTest`, `CashOsVerifiedReceiptContractTest` |
| workout-transfer v1/v2 | `WorkoutTransferCodecTest`, `WorkoutTransferRepositoryTest` |
| local backup v1 | `LocalDataBackupServiceTest` |

## 대표 fixture 및 회귀 기준

| 데이터·동작 | 기준 fixture/테스트 |
|---|---|
| 6가지 운동 기록 유형, Family/Preset/variant, kg·lb 입력, LoadState | `FitnessRecordContractTest`, `LoadStateContractTest`, `FitnessRepositoryLoadStateTest`, `WorkoutTransferCodecTest` |
| 진행 중 세션·과거 기록·삭제 tombstone | `FitnessRepositoryRecentVolumesTest`, `FitnessRepositorySummaryProjectionV2Test` |
| Meal 메뉴·구성요소·공동 섭취·미확인 영양값·출처 | `FitnessRepositoryMealTimeTest`, `DiningOutSharedConsumptionTest`, `MealItemSnapshotTest`, `NutritionVerifiedImportContractTest` |
| 로그아웃·두 계정 격리 | `AccountOwnerPolicyTest`, `FitnessDatabaseMigrationTest`, `LocalDataBackupServiceTest` |
| backup export/restore, 공개 영양 closure, 중복 복원 | `LocalDataBackupServiceTest.roundTripExportsPublicNutritionClosureAndSecondRestoreSkipsDuplicates` |

`FitnessStageZeroBaselineTest`는 위 분산 fixture가 의존하는 새 설치 DB의 공통 기반을 명시적으로 검증한다. 각 후속 책임 분리 PR은 최소 `testDebugUnitTest`, `assembleDebug`를 실행하고, DB/Repository 변경인 경우 연결된 기기에서 해당 instrumentation test를 추가 실행한다.

## 검증 상태

| 항목 | 상태 |
|---|---|
| 단위 테스트 | Stage 0 작업 시 실행 기록 |
| Android 빌드 | Stage 0 작업 시 실행 기록 |
| instrumentation | 컴파일 확인, 기기 실행은 별도 기록 |
| 실기기 | 미수행 |
| 운영 RPC·RLS | 미수행 |
