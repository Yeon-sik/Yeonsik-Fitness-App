# KMP Shared Core Extraction Plan

## 1. CURRENT confirmation

- Baseline was refreshed from origin/main before branching. Both HEAD and origin/main resolve to 6a6107598350d1c6310fcc7bbe067e4c43a1cfc2 (Merge pull request #72).
- Work branch: feat/kmp-shared-core-foundation.
- K0 scope is every production Java/Kotlin source under app/src/main: 263 files total (152 Java, 111 Kotlin). Generated assets, tests, and .understand-anything outputs are excluded.
- The pre-existing untracked Fitness-Image-Pipeline directory is outside this task and remains untouched.
- The existing refactoring rule document still describes a single-app target. The user's explicit KMP/iOS request is treated as a narrowly scoped exception: K1 adds an empty shared boundary only; it does not move Android code, change the migration plan, or change the target ownership rules.

The current source is already a Java/Kotlin mixed Android app, with Room, Compose, Android ViewModels, SQLite compatibility code, Supabase clients, and platform services. The classification below is based on direct imports plus transitive project references. A static scan found 80 files with Android/AndroidX/Google-platform references, 56 with java.time, and 32 with org.json. Those counts are risk indicators, not a reason to move every apparently pure file.

## 2. K0 classification rules

| Code | Current dependency profile | Classification consequence / K2 prerequisite |
|---|---|---|
| P0 | Kotlin values/rules with Kotlin standard-library collections and no platform import | COMMON candidate; keep current behavior and package compatibility in K2. |
| P1 | Java source or java.util / Locale / UUID / collection APIs | CONVERT_FIRST. Rewrite only the named type in Kotlin using common-safe primitives/collections; preserve IDs, nullability, labels, and lookup behavior. |
| P2 | java.time / formatter / zone APIs | CONVERT_FIRST. Do not silently replace date semantics; use existing ISO strings or an explicit shared time abstraction after behavior tests. |
| P3 | org.json parsing/serialization | Split model/rule from codec. Codec and external payload shape remain Android adapter code. |
| P4 | Android Context, assets, preferences, keystore, SQLite, Room, AndroidX | ANDROID_ADAPTER. It stays in app because it owns platform storage/security/implementation. |
| P5 | HTTP, BuildConfig, Supabase, filesystem, backup/import/export | ANDROID_ADAPTER. Network, credentials, files, and external contract codecs remain in app. |
| P6 | Activity, Compose, Android ViewModel, SavedState, navigation, renderer | ANDROID_UI. Presentation remains Android in this phase even if a displayed value object is otherwise pure. |
| P7 | Location service, Google Maps, foreground service | ANDROID_ADAPTER. Shared code may later receive normalized samples, never platform locations/services. |
| P8 | Legacy DB metadata, Room entity/DAO, external v1/v2 contract semantics | ANDROID_ADAPTER until a separately versioned common boundary is deliberately approved. |

A row may list several files. Every comma-separated file in a row has the same classification and dependency profile. Paths below are relative to app/src/main/java or app/src/main/kotlin under com/yeonsik/fitnessapp.

Within one comma-separated sequence, an omitted directory prefix inherits the most recent explicit directory prefix; this expands every listed item to one production file path.
## 3. Full K0 extraction inventory

### 3.1 Training, routine, and exercise catalog

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| exercise/BodyPart.java, EquipmentType.java, LoadState.java, UiEquipmentCategory.java | CONVERT_FIRST | P1 | Convert together as Kotlin enums/value mappings; retain every persisted ID and fromId fallback before Workout models change. |
| exercise/ExerciseCategory.java, ExerciseFamilyIdentity.java, ExerciseMasterCatalog.java, ExercisePerformanceKey.java, ExercisePrimaryMuscleLabel.java, RoutineExercise.java, RuntimeExerciseFamily.java, RuntimeExercisePicker.java, RuntimeExercisePreset.java, WeightExercise.java | CONVERT_FIRST | P1 | Java collections/Locale and Java source block commonMain. Split only named model/query rules from catalog loading. |
| exercise/ExerciseVolumeCalculator.java | CONVERT_FIRST | P1, P8 | It imports FitnessRecordContract. First isolate the record-type normalization contract without changing volume semantics. |
| exercise/ExerciseMasterAdapter.java | CONVERT_FIRST | P1 | Depends on Workout/Routine model types; convert after their common values are established. |
| exercise/RuntimeExerciseCatalog.java | CONVERT_FIRST | P1, P3 | Separate JSON decoding from the runtime catalog model; the codec stays Android. |
| exercise/ExerciseFamilyCatalog.java, ExerciseIllustrationLookup.java, ExerciseMasterRepository.java | ANDROID_ADAPTER | P3, P4, P5 | Android assets/Context and illustration lookup must stay in app. |
| feature/exercise/api/ExerciseMasterRepositoryApi.kt | CONVERT_FIRST | P1 | Its return type is the Java runtime catalog; move only after that model closure converts. |
| feature/exercise/ui/ExercisePickerModels.kt, ExercisePickerScreen.kt, ExercisePickerViewModel.kt | ANDROID_UI | P6 | Picker state, Compose, lifecycle, and navigation remain Android presentation. |
| routine/RoutineExerciseInstance.java, feature/routine/model/RoutineModels.kt | CONVERT_FIRST | P1 | Values depend on Java exercise identity; convert values before API migration. |
| feature/routine/api/RoutineRepositoryApi.kt | CONVERT_FIRST | P1 | Public contract is platform-free in shape but closes over Java routine/exercise values. |
| feature/routine/data/RoutineRepository.java | ANDROID_ADAPTER | P2, P4, P8 | Room, Context, and persisted routine rows stay Android. |
| feature/routine/ui/RoutineEntryViewModel.kt, RoutineScreen.kt | ANDROID_UI | P6 | Android ViewModel and Compose remain in app. |
| feature/workout/model/WorkoutPerformanceCalculator.kt | COMMON | P0 | Leaf pure Kotlin rule. First K2 candidate; no project or platform dependency. |
| feature/workout/model/WorkoutReadModels.kt | COMMON | P0 | Pure Kotlin read values; move only after consumer package/import plan is approved. |
| feature/workout/model/WorkoutModels.kt | CONVERT_FIRST | P1 | Uses Java LoadState, MassUnit, BodyPart, EquipmentType, and ExerciseFamilyIdentity. |
| feature/workout/api/WorkoutReadApi.kt, WorkoutRepositoryApi.kt | CONVERT_FIRST | P1 | API shape is clean but its model closure is not common yet. |
| feature/workout/application/CompleteWorkout.kt, InitializeWorkoutExercise.kt | COMMON | P0 | Pure Kotlin orchestration over repository APIs; move after the API/model closure, not before. |
| feature/workout/data/WorkoutReadSemantics.kt | ANDROID_ADAPTER | P8 | It interprets legacy metadata JSON text at the storage boundary. |
| feature/workout/data/WorkoutReadRepository.kt, WorkoutRepositoryImplementation.kt, WorkoutRoomStorage.kt | ANDROID_ADAPTER | P2, P3, P4, P8 | Room/Context/legacy JSON and SQL semantics stay Android. |
| feature/workout/api/WorkoutInterchangeApi.java, WorkoutSummaryApi.java, feature/workout/application/WorkoutSessionApplicationService.java, feature/workout/data/WorkoutInterchangeRepository.java, WorkoutSummaryRepository.java | ANDROID_ADAPTER | P2, P3, P4, P5, P8 | Transfer/Summary payload and Room transaction behavior remain existing Android contracts. |
| feature/workout/ui/ManualPastWorkoutDialog.kt, WorkoutDeleteDialog.kt, WorkoutExerciseDetailViewModel.kt, WorkoutPresentation.kt, WorkoutScreenActions.kt, WorkoutScreens.kt, WorkoutSessionViewModel.kt | ANDROID_UI | P6 | Compose/ViewModel/dialog/state rendering stays Android. |
| state/FitnessNavigationHistory.java, FitnessScreen.java, WorkoutSessionState.java | ANDROID_UI | P1, P6 | Navigation and screen-owned state are not a shared domain contract. |

### 3.2 Cardio

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| cardio/CardioActivityType.java, CardioLocationSample.java, CardioDistanceFilter.java, CardioRouteProjection.java | CONVERT_FIRST | P1 | Pure GPS-normalized values/calculations, but Java source/collections/Locale block commonMain. Convert after preserving distance/filter test vectors exactly. |
| cardio/CardioMetrics.java | ANDROID_UI | P1, P6, P8 | Formatting and Android repository GPS-status labels are presentation/storage-facing. |
| cardio/CardioTrackingService.java | ANDROID_ADAPTER | P4, P7 | Foreground service, permissions, Fused Location, notification, and Android lifecycle remain Android. |
| feature/cardio/model/CardioSessionSnapshot.kt | COMMON | P0 | Leaf pure Kotlin snapshot and elapsed-time rule. Second K2 candidate. |
| feature/cardio/api/CardioRepositoryApi.kt | CONVERT_FIRST | P1 | API depends on Java CardioActivityType and CardioRouteProjection. |
| feature/cardio/application/CompleteCardio.java | CONVERT_FIRST | P1 | Application rule can become shared only after Cardio and Workout API closures convert. |
| feature/cardio/application/CardioSessionApplicationService.java, feature/cardio/data/CardioRepository.java | ANDROID_ADAPTER | P2, P4, P7, P8 | Coordinates shared workout storage with GPS rows, Room/SQLite, and transactions. |
| feature/cardio/ui/CardioEditorDialogs.kt, CardioPresentation.kt, CardioRouteMap.kt, CardioScreenActions.kt, CardioScreens.kt, CardioSessionViewModel.kt | ANDROID_UI | P6, P7 | Compose, Maps rendering, Android ViewModel, and user-action timing remain Android. |

### 3.3 Nutrition catalog and dining-out catalog

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| data/AthleteNutritionGoal.java, AthleteNutritionPolicy.java, CompositionGroup.java, CompositionGroupType.java, CompositionMember.java, CompositionSelection.java, CompositionTemplate.java | CONVERT_FIRST | P1 | Domain values/rules use Java collections/Locale; convert nullable and collection immutability behavior deliberately. |
| data/NutrientCode.java, NutritionCalculator.java, NutritionFood.java, NutritionProfile.java, NutritionReadV1.java, NutritionTotals.java, NutritionUnit.java | CONVERT_FIRST | P1 | Core nutrition values/calculator are shareable only after focused Java-to-Kotlin conversion and existing unit tests pass unchanged. |
| data/ProductNutritionLink.java, ProductReadV1.java, TextValuePolicy.java | CONVERT_FIRST | P1, P2 | Preserve opaque canonical IDs, UUID/text/date semantics; no product identity inference in shared code. |
| data/DiningOutConsumption.java, DiningOutFulfillmentMode.java, DiningOutIdentity.java, DiningOutOption.java, DiningOutProvisionType.java, MealCompositionItem.java, MealMenuSelection.java, MealRecordKind.java | CONVERT_FIRST | P1 | Domain facts are Java values/enums; retain null/unknown and quantity semantics. |
| data/DiningOutComponent.java, MealEntryPolicy.java | CONVERT_FIRST | P2, P3 | Split JSON codec and java.time parsing from pure validation; do not change date/meal policy behavior. |
| data/VerifiedReceiptConsumptionPolicy.java, VerifiedReceiptItem.java | CONVERT_FIRST | P1, P8 | Possible shared rules only after external PriceTrace/Nutrition evidence semantics are frozen and covered by contract tests. |
| data/CashOsVerifiedReceiptContract.java, FitnessRecordContract.java, FitnessSummaryProjectionV2.java | ANDROID_ADAPTER | P3, P5, P8 | They encode external CashOS/Personal OS payload contracts; K2 must not relocate or rewrite them. |
| data/VerifiedFoodCatalogSeed.java | ANDROID_ADAPTER | P2, P3, P4, P5 | Android asset/SQLite seed and canonical import implementation stay Android. |
| feature/nutrition/api/NutritionCatalogRepositoryApi.kt, NutritionTemplateRepositoryApi.kt | CONVERT_FIRST | P1 | Interfaces close over Java nutrition/dining values. |
| feature/nutrition/api/NutritionCatalogBackupApi.kt, NutritionCatalogSyncStore.kt | ANDROID_ADAPTER | P5, P8 | Backup/sync contracts remain Android implementation boundaries. |
| feature/nutrition/model/NutritionRecipeComponent.kt | CONVERT_FIRST | P1 | Depends on Java NutritionFood. |
| feature/nutrition/model/NutritionCatalogSyncRows.kt | ANDROID_ADAPTER | P8 | Sync rows are external/persistence transport shapes, not shared domain models. |
| feature/nutrition/data/NutritionCatalogRepository.java, NutritionTemplateRepository.java | ANDROID_ADAPTER | P2, P3, P4, P8 | Room/Context/JSON/provenance and catalog storage stay Android. |
| integration/nutrition/NutritionCatalogSyncClient.java, NutritionIntegrationService.java, NutritionPublicationClient.java, NutritionPublicNutritionClient.java | ANDROID_ADAPTER | P2, P3, P5, P8 | Supabase/HTTP codecs and publication behavior remain in app. |

### 3.4 Diet journal

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| data/MealItemSnapshot.java | CONVERT_FIRST | P1 | Snapshot value is Java collection-based; preserve unknown nutrient values and immutable historical snapshot behavior. |
| feature/meal/model/MealReadModels.kt | COMMON | P0 | Pure Kotlin read DTOs; can move only when body/nutrition dependencies remain package-compatible. |
| feature/meal/api/MealReadApi.kt | COMMON | P0 | Platform-free read contract over pure read DTOs. |
| feature/meal/api/MealRecordRepositoryApi.kt | CONVERT_FIRST | P1 | Write API depends on Java dining-out values. |
| feature/meal/data/MealReadRepository.kt, MealRecordRepository.java | ANDROID_ADAPTER | P2, P3, P4, P8 | Room snapshots, JSON metadata, timestamps, and atomic local writes remain Android. |
| feature/meal/ui/MealScreen.kt, MealViewModel.kt | ANDROID_UI | P2, P3, P6 | Compose/editor/ViewModel/PriceTrace presentation remain Android. |
| ui/MealPresentation.java | ANDROID_UI | P6 | Android presentation model/policy remains with Android screen. |

### 3.5 Body, home, records, insights, analysis, development, statistics, supplement

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| data/BodyMetricEntry.java, feature/body/model/BodyReadEntry.kt | CONVERT_FIRST / COMMON | P1 / P0 | BodyReadEntry is already common-safe; BodyMetricEntry needs a focused Java conversion before one shared model can be selected. |
| feature/body/api/BodyMetricsReadApi.kt, BodyMetricsRepositoryApi.kt | COMMON | P0 | Platform-free APIs using AccountScope/read values; keep write/storage implementation Android. |
| feature/body/application/BodyMetricsApplicationService.java, feature/body/data/BodyMetricsRepository.java, BodyMetricsReadRepository.kt | ANDROID_ADAPTER | P2, P3, P4, P8 | Existing local DB ownership and repository façade stay Android. |
| feature/body/ui/BodyMetricsEditorScreen.kt, BodyMetricsViewModel.kt | ANDROID_UI | P6 | Compose and Android ViewModel stay Android. |
| development/BodyProfile.java, DevelopmentGoal.java, DevelopmentInsight.java, DevelopmentInsightRules.java, DevelopmentReport.java, PaperAdvice.java, PaperAdviceAssessment.java, PaperAdviceEngine.java, PaperAdviceInput.java | CONVERT_FIRST | P1, P2 | Pure-looking development rules/models use Java collections/time; preserve advice order, coverage, and date logic in Kotlin tests. |
| feature/development/api/DevelopmentReadApi.kt, DevelopmentReportApi.kt, DevelopmentRepositoryApi.kt, feature/development/application/DevelopmentReportService.kt, feature/development/model/DevelopmentNutritionGoal.kt, DevelopmentReadModels.kt | CONVERT_FIRST | P1, P2 | Their closure includes Java development models/date semantics. |
| feature/development/application/DevelopmentApplicationService.java, PaperAdviceSnapshotAssembler.java, feature/development/data/DevelopmentRepository.java, DevelopmentReadRepository.kt | ANDROID_ADAPTER | P2, P4, P8 | Repository/Room-backed assembly remains Android. |
| feature/development/ui/DevelopmentScreen.kt, DevelopmentViewModel.kt, PaperAdviceSection.kt, RecoveryEditorDialogs.kt | ANDROID_UI | P2, P6 | Presentation and Android lifecycle stay Android. |
| feature/home/api/HomeRepositoryApi.kt, feature/home/data/HomeReadSources.kt, feature/home/model/HomeModels.kt, HomeSnapshot.kt | COMMON | P0 | Platform-free read composition models/APIs; defer movement until dependent domain model packages are stable. |
| feature/home/data/HomeReadRepository.kt | ANDROID_ADAPTER | P2, P4, P8 | Room read composition remains Android. |
| feature/home/ui/ComposeHomeScreen.kt, HomeViewModel.kt | ANDROID_UI | P6 | Compose/ViewModel stay Android. |
| feature/records/api/RecordsReadApi.kt, feature/records/model/RecordsModels.kt | COMMON | P0 | Pure read models/api, with future dependency on BodyReadEntry and MealReadSummary package plan. |
| feature/records/data/RecordsReadRepository.kt | ANDROID_ADAPTER | P2, P4, P8 | Room/date query behavior remains Android. |
| feature/records/ui/RecordsScreen.kt, RecordsViewModel.kt, ui/RecordsAnalysis.java | ANDROID_UI | P2, P6 | Calendar/rendering presentation remains Android. |
| feature/statistics/api/StatisticsReadApi.kt, feature/statistics/model/StatisticsModels.kt | CONVERT_FIRST | P2 | StatisticsPeriodWindow uses java.time; choose a shared date abstraction only after reference-date tests freeze behavior. |
| feature/statistics/data/StatisticsReadRepository.kt | ANDROID_ADAPTER | P2, P4, P8 | Existing Room aggregation remains Android. |
| feature/statistics/ui/StatisticsScreen.kt, StatisticsViewModel.kt | ANDROID_UI | P6 | Compose/ViewModel remain Android. |
| feature/nutrition/analysis/api/NutritionAnalysisApi.kt, feature/nutrition/analysis/application/NutritionAnalysisService.kt, feature/nutrition/analysis/model/NutritionAnalysisModels.kt | CONVERT_FIRST | P1, P2, P3 | Split JSON read/input and Locale formatting from calculation; preserve explicit UNKNOWN/ESTIMATED/RECORDED provenance. |
| supplement/SupplementCatalog.java, SupplementEvidence.java, SupplementEvidenceCatalog.java, SupplementPlan.java, feature/supplement/api/SupplementRepositoryApi.kt, feature/supplement/model/SupplementModels.kt | CONVERT_FIRST | P1, P2 | Values may become common after Java/time conversion; preserve evidence and schedule semantics. |
| feature/supplement/data/SupplementRepository.java | ANDROID_ADAPTER | P2, P4, P8 | Room implementation remains Android. |
| feature/supplement/ui/SupplementScreen.kt, SupplementViewModel.kt | ANDROID_UI | P2, P6 | Compose/ViewModel stay Android. |
| feature/recovery/api/RecoveryRepositoryApi.kt | CONVERT_FIRST | P2 | Future shared API only after recovery date/model closure is converted. |
| feature/recovery/data/RecoveryRepository.java | ANDROID_ADAPTER | P2, P4, P8 | Local storage behavior remains Android. |

### 3.6 Cross-cutting Android app, database, security, integration, and UI

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| MainActivity.java, app/navigation/AppViewModels.java, app/AppViewModelFactory.kt, app/navigation/AppNavigationViewModel.kt, app/navigation/ComposeAppScreen.kt | ANDROID_UI | P2, P4, P6 | Activity, ViewModels, navigation, and Compose remain Android. |
| app/AppContainer.kt | ANDROID_ADAPTER | P4, P5, P8 | Android composition root wires Room, repositories, sync, and platform services. |
| core/account/AccountScope.kt | COMMON | P0 | Common-safe value but high fan-out; move only after leaf candidates prove package/interop strategy. |
| core/account/AccountOwnershipService.java | ANDROID_ADAPTER | P2, P4, P8 | Owner persistence/read behavior remains Android. |
| core/database/FitnessDatabaseContract.kt, FitnessPrimaryKeyCompatibility.kt, FitnessRoomDatabase.kt, FitnessRoomDatabaseProvider.kt, FitnessRoomFreshSchema.kt, FitnessRoomLegacyEntities.kt, FitnessRoomMigrations.kt, FitnessRoomOpenHelperFactory.kt | ANDROID_ADAPTER | P4, P8 | Room schema/entities/migrations must not move or change. |
| core/database/RoomTransactionRunner.java, core/database/backup/BackupDatabaseStorage.java, RoomBackupDatabaseStorage.java, data/FitnessDatabaseHelper.java, LegacyMigrationDatabase.java | ANDROID_ADAPTER | P3, P4, P8 | SQLite/Room compatibility and migration logic remain Android. |
| backup/LocalDataBackupService.java | ANDROID_ADAPTER | P2, P3, P4, P5, P8 | Filesystem, JSON, backup format, and SQLite ownership stay Android. |
| config/AccountOwnerPolicy.java | CONVERT_FIRST | P1 | Pure owner validation is movable only with a focused Kotlin conversion and identity tests. |
| config/AppSurfacePolicy.java, SupabaseConfig.java, SupabaseConnectionPolicy.java, SupabaseStoreScope.java | ANDROID_ADAPTER | P5, P8 | Surface/network configuration remains platform/external-integration policy. |
| config/MassUnitPreferences.java, NutritionSupabaseConfigStore.java, PriceTraceSupabaseConfigStore.java, SecureTokenStore.java, SupabaseConfigStore.java, ThemeModePreferences.java | ANDROID_ADAPTER | P4, P5 | Preferences, token encryption, and Context-bound configuration stay Android. |
| sync/LegacyFitnessSyncStore.java, LegacySyncCursor.java, LegacySyncRow.java, RoomLegacyFitnessSyncStore.java, SupabaseAuthManager.java | ANDROID_ADAPTER | P3, P4, P5, P8 | Auth, legacy cursor/payload, and Room sync storage remain Android. |
| integration/personalos/FitnessSummaryPublisher.java, LegacyFitnessSyncAdapter.java, SupabaseSyncManager.java, integration/sync/SyncApplicationService.java | ANDROID_ADAPTER | P2, P3, P4, P5, P8 | Personal OS legacy and summary contracts are untouched. |
| integration/pricetrace/ProductReadV1Client.java, RestaurantMenuReadV1Client.java | ANDROID_ADAPTER | P3, P5, P8 | PriceTrace HTTP/JSON contract must not be moved or modified. |
| integration/transfer/FleekCsvImporter.java, LocalDataTransferApplicationService.java, WorkoutTransferCodec.java, WorkoutTransferService.java, integration/workout/WorkoutInterchangeResult.java | ANDROID_ADAPTER | P2, P3, P5, P8 | Filesystem/CSV/transfer v1-v2 codecs remain Android. |
| core/ui/FitnessUiTokens.java, NutritionRow.java, AppComposeComponents.kt, FitnessComposeComponents.kt, FitnessComposeTheme.kt, FitnessExerciseInterop.kt, FitnessVisualComponents.kt, FitnessVisualModels.kt | ANDROID_UI | P2, P6 | Android presentation/theming/interoperability stays Android. |
| ui/AppUiActions.java, ExerciseIllustrationPreview.java, ExerciseMuscleModelRenderer.java, FitnessUi.java, SettingsUiPolicy.java, UiState.java, WorkoutSetPresentation.java, WorkoutSummaryAnalytics.java | ANDROID_UI | P2, P3, P4, P6 | Android view/rendering/presentation state remains Android. |
| feature/settings/application/SettingsSessionCoordinator.kt | ANDROID_ADAPTER | P4, P5, P8 | Coordinates settings, auth, sync, and backup through Android implementations. |
| feature/settings/ui/SettingsPresentation.kt, SettingsScreen.kt, SettingsViewModel.kt | ANDROID_UI | P2, P5, P6 | Compose/ViewModel and Android file/session UI remain Android. |

## 4. Dependency closure for the first K2 extraction candidates
### 3.7 File-level additions

| Files / principal types | Class | Current dependencies | K2 prerequisite or Android reason |
|---|---|---|---|
| data/AthleteDailyCheckIn.java (AthleteDailyCheckIn) | CONVERT_FIRST | P2 | Java time-based daily check-in; preserve calendar-day semantics before moving. |
| data/MassFormatter.java (MassFormatter) | CONVERT_FIRST | P1 | Java Locale formatting needs a focused common-safe conversion with display-output tests. |


K2 is intentionally not performed by this change. The first candidates are ordered by smallest behavior and interoperability surface, not by feature visibility.

1. feature/workout/model/WorkoutPerformanceCalculator.kt
   - Direct closure: Kotlin primitives only.
   - Current behavior: Epley estimate returns 0.0 for non-finite/non-positive load or non-positive reps.
   - Required K2 work: move the object and its existing unit test to shared; change Android import only. No model, DB, or contract change.

2. feature/cardio/model/CardioSessionSnapshot.kt
   - Direct closure: Kotlin primitives/String only.
   - Current behavior: elapsedSeconds uses activeDurationMillis plus a non-negative active tracking delta.
   - Required K2 work: move the data class and unit test; Android repository and ViewModel become consumers. GPS remains adapter code.

3. feature/body/model/BodyReadEntry.kt and BodyWeightWindow
   - Direct closure: Kotlin primitives/String only.
   - Required K2 work: decide one shared Body read namespace and update Records consumers. Do not alter SQLite body row mapping.

4. core/account/AccountScope.kt
   - Direct closure: Kotlin String plus validation.
   - Required K2 work: high-fan-out package migration only after leaf moves establish Java/Kotlin interop and package policy. It is not a first commit despite being common-safe.

5. Training type closure: LoadState.java, MassUnit.java, BodyPart.java, EquipmentType.java, ExerciseFamilyIdentity.java, then WorkoutModels.kt and RoutineModels.kt
   - Required K2 work: focused Kotlin conversions with compatibility tests for every stored ID, nullable state, fallback, and Java caller; no automatic Java conversion.

6. Repository API/application closure: WorkoutReadApi.kt, WorkoutRepositoryApi.kt, CompleteWorkout.kt, InitializeWorkoutExercise.kt; then CardioRepositoryApi.kt and CompleteCardio.java
   - Required K2 work: only interfaces/models/rules enter shared. Room classes, Context, transaction runners, and implementations remain Android adapters.

7. Cardio calculator closure: CardioActivityType.java, CardioLocationSample.java, CardioDistanceFilter.java, CardioRouteProjection.java
   - Required K2 work: Kotlin conversion plus the existing filter/route tests. Android Location is converted to normalized samples in androidMain only.

8. Nutrition and insight closures
   - Required K2 work: first convert nutrition values/calculator and Java-time/JSON boundaries, then move read models and calculation rules. External catalog/sync/import codecs remain Android.

## 5. Recommended movement order and risks

| Order | Scope | Why this order | Risk gate |
|---|---|---|---|
| K1 (this change) | Empty KMP boundary | Proves project configuration without behavior movement. | App debug build and tests must continue to pass. |
| K2-A | WorkoutPerformanceCalculator | No type closure; lowest behavior risk. | Existing calculator test runs from commonTest. |
| K2-B | CardioSessionSnapshot | Primitive-only model with explicit elapsed rule. | Shared test covers status/delta boundaries; Android UI unchanged. |
| K2-C | BodyReadEntry | Primitive-only read DTO. | Records read path compiles with unchanged DB mapping. |
| K2-D | Common low-level type conversions | Unlocks Workout/Routine/Cardio API closure. | Preserve enum IDs, null semantics, Java source compatibility adapters, and unit tests. |
| K2-E | Repository API and pure application rules | Lets iOS call contracts without moving implementation. | No Room, Context, network, or external DTO leaks in shared APIs. |
| Later | Nutrition/insights/development rules | Broadest time/JSON/provenance closure. | Keep unknown distinct from zero; retain history snapshots and external provenance semantics. |

Main risks are not Gradle wiring. They are semantic drift in java.time conversion, JSON null/missing behavior, Java/Kotlin nullability and numeric interop, persisted enum IDs, legacy metadata parsing, and accidental migration of external DTOs. K2 must add targeted behavior tests before each conversion and must not combine a package move with a DB/schema/contract change.

## 6. K1 module boundary

K1 creates a minimal :shared module with:

- Kotlin Multiplatform plugin.
- Android KMP library target using com.android.kotlin.multiplatform.library.
- iOS targets: iosX64, iosArm64, iosSimulatorArm64.
- commonMain configured with no production source and no dependencies.
- commonTest with a source-set smoke test.
- app has implementation project(":shared"); shared declares no project dependency and does not reference app.

The Android KMP library plugin is single-variant. This affects only empty :shared; existing :app build types, application ID, Room setup, signing, BuildConfig, assets, and behavior are unchanged.

## 7. Structural blockers and hard-gate assessment

No P0 blocker prevents K1.

- The single-module recommendation in the earlier refactoring plan is a governance conflict, not a technical blocker; the direct user request authorizes this empty, one-way KMP boundary.
- Most reusable existing domain code is Java and/or uses java.time. That blocks K2 moves, not K1.
- org.json types conflate codecs with models in several places. K2 requires codec/model splitting, not replacement of existing payload contracts.
- Room/SQLite, migrations, backup, Context, GPS/Maps, ViewModels, Compose, credentials, Supabase, Personal OS, PriceTrace, and Nutrition clients are explicitly retained as Android adapters/UI.
- On this Windows host, the iOS compile task is configured and executes with no source. Meaningful iOS simulator/device linking requires a macOS/Xcode environment after common production source exists; that is not a K1 failure.

Hard-gate state after K1 is expected to be GO only when the final diff and test section below remain true:

- No Android production behavioral source was changed.
- No Room schema, migration, or external contract file was changed.
- :shared has no :app dependency.
- commonMain has no Android/JVM-only production dependency.
- The K2 closure above is documented before any shared production movement.
