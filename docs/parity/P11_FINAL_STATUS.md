# P11 — Integration and parity closure

검증 기준일: 2026-09-17 (Asia/Seoul)

코드 근거의 immutable source boundary는 P10이 병합된 b2f488f00a9a83783ecb44ae01f44ff8c729ab92다. P11에서는 이 boundary의 production API, repository, ViewModel, runtime navigation/UI 연결과 자동화 결과를 확인했다. 이 문서 자체는 해당 결과를 기록하는 Git Markdown 원장이다.

## 최종 상태

~~~
CODE COMPLETE
AUTOMATED TEST PASS
DEVICE QA REQUIRED
~~~

CODE COMPLETE는 P2B부터 P10까지의 phase-local 구현과 P11 통합 판정을 코드 기준으로 마쳤다는 뜻이다. 전체 사용자 완료를 의미하지 않는다. 현재 환경에서는 adb devices -l에 연결된 장치가 없었고 emulator 실행 파일도 확인되지 않아 실기기·에뮬레이터 QA를 수행하지 못했다.

## 판정 규칙

- PASS: 현재 production API/Repository/runtime path가 존재하고, 해당 계약을 확인하는 소스·테스트·빌드 근거가 있다. 이 상태만으로 실기기 시각 QA나 원격 Supabase/RLS 동작을 증명하지 않는다.
- FOLLOW-UP: 구현 범위가 명시적으로 남아 있거나, 현재 환경에서 필요한 device/remote 증거를 확보하지 못했다. 각 항목의 이유와 다음 owner를 이 문서에 남긴다.
- BLOCKED: 안전한 다음 작업을 막는 P0 blocker. 이번 재검증에서는 발견하지 않았다.

## P0 RESTORE 재검증

| ID | P11 status | 현재 production 근거 | 남은 불확실성 또는 다음 작업 |
|---|---|---|---|
| H-002 | PASS | HomeRequestIdentity와 HomeViewModel이 account/date request gate를 사용하고, archive는 RecordsReadRepository와 RecordsUiState가 소유한다. | Home은 오늘 요약 consumer로 유지한다. archive query로 재사용하지 않는지 device QA에서 확인한다. |
| EX-001 | FOLLOW-UP | ExercisePickerScreen은 canonical variant exact image, family fallback, 이미지 없음 상태와 body/subpart 필터를 production path에서 사용한다. | 앞·뒤 해부학 모델 FitnessExerciseMuscleModel은 interop primitive로 존재하지만 picker의 직접 조작 selector로 연결되지 않았다. P2A visual selector 범위의 후속 UI 작업으로 남긴다. |
| EX-002 | PASS | ExercisePickerViewModel → ExerciseMasterRepositoryApi → RuntimeExercisePicker → ExercisePickerScreen이 query, body part, primary subpart, equipment, recent/name sort를 연결한다. | 실제 catalog가 많은 계정에서 chip overflow와 scroll은 device QA가 필요하다. |
| EX-003 | PASS | 현재 add/replace 계약은 한 번에 하나의 canonical preset을 선택한다. family의 변형 수, selected state, variant identity, record type이 표시되고 add/replace mode가 분리된다. | legacy batch multi-select가 별도 요구사항으로 재확정될 경우 별도 UX scope가 필요하다. 현재 P2A add/replace flow에서는 selected count가 적용되지 않는다. |
| RT-002 | PASS | RoutineScreen이 stable order, family identity image exact/fallback, body/subpart/equipment/record type metadata를 렌더링한다. | 다수 종목과 대형 글꼴에서 실제 레이아웃은 device QA가 필요하다. |
| WS-002 | PASS | WorkoutSessionScreen과 AppWorkoutSessionContent가 current exercise image, record type, elapsed timer, completed progress, total volume, displayed unit, recent volume을 session snapshot에서 소비한다. | 센서나 원격 의존 없는 session UI의 실기기 시각 확인이 남아 있다. |
| WD-001 | PASS | WorkoutDetailScreen의 WorkoutSetEditor가 record type/load state별 입력, 저장 당시 입력 단위, kg canonical conversion, RIR, rest, completion, add/edit/delete를 WorkoutRepositoryApi로 보낸다. | kg/lb 입력·재생성 화면의 device QA가 필요하다. |
| WD-002 | PASS | detail이 WorkoutExerciseNavigation으로 stable exercise identity를 이동하고 WorkoutExerciseImage가 exact/fallback/placeholder를 사용한다. | animation frame과 다양한 legacy identity의 시각 결과는 device QA가 필요하다. |
| WD-003 | PASS | detail이 WorkoutPreviousHistory, allowedLoadStates, best/E1RM, recent volume trend를 owner-scoped WorkoutRoomStorage read model로 표시하고 지난 기록 적용 action을 연결한다. | 실제 삭제·variant 혼합 데이터의 화면 확인은 device QA가 필요하다. |
| WSUM-002 | PASS | WorkoutSummaryScreen이 completed snapshot, muscle distribution, same-routine comparison, volume trend, exact/fallback image, save-as-routine, Records navigation을 연결한다. | 풍부한 snapshot의 시각 밀도는 device QA가 필요하다. |
| C-002 | PASS | CardioSessionScreen/CardioSummaryScreen이 GPS status, accepted point count, route load, HR, CardioRouteMap과 rejected-point 설명을 owner path에서 표시한다. | GPS on/off 센서 상태와 지도 key가 없는 환경의 device QA가 필요하다. |
| C-004 | PASS | CardioPresentation과 CardioScreens가 source-backed elapsed/distance로 pace/speed를 계산하고 값 부족 시 명시적 unknown 문구를 표시한다. | 실제 GPS track의 pace/speed 시각 검증은 device QA가 필요하다. |
| B-002 | PASS | BodyMetricsReadApi/BodyMetricsReadRepository가 date/period read를 제공하고 RecordsReadRepository와 RecordsScreen이 selected-day body detail과 weight trend를 소비한다. | 실기기에서 월 경계와 빈 trend 상태를 확인하지 못했다. |
| M-001 | PASS | MealScreenActions.selectDate, MealViewModel, HomeReadRepository와 selected-date route가 날짜별 read/save/edit/delete refresh를 연결한다. | 과거 날짜를 빠르게 이동하는 실제 UI 확인은 device QA가 필요하다. |
| M-003 | PASS | MealRecordRepositoryApi가 updateMealTime/deleteMeal을 제공하고 MealScreen과 MealViewModel이 edit/delete/time dialog와 reload를 연결한다. | 기존 사용자 DB에서 tombstone 후 화면 갱신은 device QA가 필요하다. |
| M-004 | PASS | NutritionCatalogRepositoryApi와 NutritionTemplateRepositoryApi가 AppContainer에 owner-scoped public boundary로 조립되고 recipe/template/component identity를 storage 구현과 분리한다. | 현재 기본 Meal 화면은 catalog boundary 전체를 편집 UI로 노출하지 않는다. API boundary 복구 범위를 넘어서는 editor는 별도 scope다. |
| M-004B | PASS | MealRecordRepositoryApi.saveComplexDiningOutMeal과 MealRecordRepository가 multiple menu/component, serving, diner count, consumption fraction, template revision을 MealItemSnapshot으로 복사하고 MealReadRepository가 snapshot으로 읽는다. | 복합 intake 작성 UI의 전체 조합은 device QA와 별도 product scope가 필요하다. |
| M-005 | PASS | NutritionCatalogRepository, Meal search UI, NutritionFood display, PriceTrace client/apply path가 verified food/product/variant와 source metadata를 보존한다. | 외부 PriceTrace 운영 RPC/RLS 응답은 이 로컬 검증 범위에 포함되지 않는다. |
| M-006 | PASS | MealReadRepository와 nutrition analysis models가 nullable nutrition, recorded/estimated/unknown provenance, snapshot fallback을 보존한다. | 실제 legacy row 변환 결과의 device migration 확인은 별도다. |
| M-007 | PASS | RecoveryRepositoryApi, RecoveryRepository, DevelopmentViewModel, RecoveryEditorDialogs가 nutrition goal/check-in entry, owner/date read-after-write를 연결한다. | 원격 동기화 이후 check-in 충돌은 remote QA가 필요하다. |
| M-007B | PASS | NutritionAnalysisApi → NutritionAnalysisService → MealViewModel → NutritionAnalysisSection이 protein distribution, detailed nutrients, target comparison, day/period aggregation을 표시한다. | 입력 데이터가 매우 큰 계정의 시각 밀도와 성능은 device QA가 필요하다. |
| RCV-001 | PASS | AthleteDailyCheckIn, DevelopmentReadApi, DevelopmentReadRepository가 nullable check-in facts와 recorded-day summary를 owner-scoped read로 제공한다. | production device에서 기존 row가 화면에 보이는지 확인하지 못했다. |
| RCV-002 | PASS | RecoveryRepository가 water, energy, readiness, hunger, digestion, note를 저장하고 RecoveryEditorDialogs와 DevelopmentViewModel이 write → read path를 연결한다. | 입력 validation과 재생성 후 복원은 device QA가 필요하다. |
| RCV-003 | PASS | nutrition goal은 RecoveryRepositoryApi owner가 저장하고 Development read/report와 NutritionAnalysis target이 같은 source를 소비한다. | 원격 계정·동기화 경계는 remote QA가 필요하다. |
| RC-002 | PASS | RecordsReadRepository가 YearMonth, 월 42칸 calendar, month navigation, today, selected date와 workout/body/meal marker를 만들고 RecordsScreen이 렌더링한다. | 월 첫날·마지막날·이전/다음 달의 실제 touch QA가 남아 있다. |
| RC-003 | PASS | RecordsScreen이 selected-day muscle labels, workout/body/meal detail navigation과 Body owner weight trend를 표시한다. | rich-history account의 실제 스크롤·상세 이동은 device QA가 필요하다. |
| SUP-001 | PASS | SupplementRepositoryApi, SupplementViewModel, SupplementScreen이 plan create/edit/archive와 type/brand/form/dose/ingredient/purpose/timing metadata를 연결한다. | 외부 sync 이후 plan revision은 remote QA가 필요하다. |
| SUP-002 | PASS | selected date와 request identity가 SupplementViewModel에 저장되고 taken/skipped/undo/adherence/history가 repository reload를 통해 갱신된다. | 날짜별 touch flow와 빠른 날짜 이동은 device QA가 필요하다. |
| SUP-003 | PASS | SupplementScreen과 SupplementViewModel이 history correction/delete, effect score, adverse reaction, note write를 public API로 연결한다. | 실제 legacy history row correction은 device QA가 필요하다. |
| SUP-004 | PASS | SupplementEvidenceCatalog와 SupplementScreen이 evidence status/source/limitation/safety와 duplicate warning을 표시하고 PaperAdvice와 별도 영역을 유지한다. | evidence 카드의 large-font layout은 device QA가 필요하다. |
| DEV-002 | PASS | DevelopmentScreen과 PaperAdviceSection이 source coverage, evidence refs, limitation, qualitative confidence, data sufficiency를 표시한다. | device에서 긴 evidence 문장의 가독성은 확인하지 못했다. |
| DEV-003 | PASS | PaperAdviceSnapshotAssembler → PaperAdviceEngine → DevelopmentReportApi → DevelopmentViewModel → ComposeAppScreen → PaperAdviceSection이 연결된다. | 실제 계정 입력이 없는 경우의 production 화면은 device QA가 필요하다. |
| DEV-004 | PASS | DevelopmentInsightRules의 evidence/nextAction과 PaperAdvice recommendation이 Development facts만 소비하고 Statistics VM/UI를 호출하지 않는다. | remote evidence source 자체의 학술 검토는 별도 운영 범위다. |
| SET-003 | PASS | SettingsScreen이 account status, sync status, data safety, import/export, privacy/security, app info, friendly theme/unit labels를 계층별 카드로 표시하고 raw secret/error text를 차단한다. | connected/disconnected와 import/export failure의 실제 touch 결과는 device QA가 필요하다. |
| VIS-002 | PASS | FitnessSemanticStatus, FitnessComposeTheme, FitnessUiTokens와 shared components가 success/warning/error/unknown, macro/status, disabled/insufficient semantics를 사용한다. | 실제 화면 전체의 semantic color contrast는 device visual QA가 필요하다. |
| VIS-003 | PASS | FitnessCalendarDayCell, FitnessTrendChart, progress primitives가 Records/Summary/Workout/Statistics production consumers에 연결된다. | light/dark 및 large font에서 chart/marker clipping은 device QA가 필요하다. |

BLOCKED 행은 없다. FOLLOW-UP은 EX-001의 실제 기능 범위와 device/remote 증거 부재로 한정되며, 이를 PASS로 위장하지 않는다.

## P8 신규 feature closure

STAT-001과 STAT-002는 P0 RESTORE가 아니라 P8 NEW 항목이다. StatisticsReadApi → StatisticsReadRepository → StatisticsViewModel → StatisticsScreen → ComposeAppScreen 경로가 존재한다. write/persistence path가 없고 Workout, Body, Meal, Cardio owner read API만 소비하며 Development ViewModel/UI를 참조하지 않는다. period comparison, trends, observed pattern, data sufficiency와 empty state가 자동화 테스트에 포함된다. P8은 PASS로 판정한다.

## 필수 QA matrix

| Scenario | 현재 자동화·소스 증거 | P11 실행 결과 | 최종 상태 |
|---|---|---|---|
| Light | FitnessVisualModelsTest, shared theme, FitnessVisualQaTest opt-in path | 장치 없음으로 visual test 미실행 | FOLLOW-UP — disposable emulator 필요 |
| Dark | dark token/theme와 동일 visual QA path | 장치 없음으로 미실행 | FOLLOW-UP — disposable emulator 필요 |
| normal font | 공통 Compose typography와 normal layout path | 장치 없음으로 실제 layout 미실행 | FOLLOW-UP — device QA 필요 |
| large font | shouldStackFitnessMetrics와 picker fontScale 1.3 reflow path가 존재 | 장치 없음으로 picker 전체 layout 미실행 | FOLLOW-UP — large-font picker QA 필요 |
| empty account | empty/unknown state unit와 repository tests | Compose destination no-crash 미실행 | FOLLOW-UP — device QA 필요 |
| rich-history account | Records, Workout, Nutrition, Supplement read tests | rich-history scroll/detail 미실행 | FOLLOW-UP — device QA 필요 |
| active workout | session state/service/presentation tests | 실제 입력·resume 미실행 | FOLLOW-UP — device QA 필요 |
| completed workout | completion/summary tests와 completed-only summary guard | 실제 summary navigation 미실행 | FOLLOW-UP — device QA 필요 |
| today | Home/today route and date policy source | device date action 미실행 | FOLLOW-UP — device QA 필요 |
| historical date | Records/Meal/Supplement selected-date tests | device date navigation 미실행 | FOLLOW-UP — device QA 필요 |
| month boundary | YearMonth repository tests와 42-cell calendar path | 월 경계 touch 미실행 | FOLLOW-UP — device QA 필요 |
| kg | mass conversion and settings/workout tests | 실제 입력/표시 미실행 | FOLLOW-UP — device QA 필요 |
| lb | mass conversion and saved input-unit path | 실제 입력/표시 미실행 | FOLLOW-UP — device QA 필요 |
| Cardio GPS on | accepted-point, route, metric tests | GPS sensor 미실행 | FOLLOW-UP — device QA 필요 |
| Cardio GPS off | unknown/unavailable GPS presentation path | GPS unavailable device state 미실행 | FOLLOW-UP — device QA 필요 |
| Supabase connected | settings/sync policy and client paths | live provider/RLS 미실행 | FOLLOW-UP — remote QA 필요 |
| Supabase disconnected | local-only/login-required/failed sync safe state paths | network disconnect toggle 미실행 | FOLLOW-UP — device/remote QA 필요 |
| online | sync/import/export source paths | online provider flow 미실행 | FOLLOW-UP — remote QA 필요 |
| offline | local-first owner paths and safe local-only copy | real offline retry/sync 미실행 | FOLLOW-UP — device/remote QA 필요 |
| process recreation/state restore | SavedStateHandle, rememberSaveable, state/request tests; opt-in visual test includes recreate | device process recreation 미실행 | FOLLOW-UP — disposable emulator 필요 |
| legacy existing data | Room legacy entities/migrations and backup/import compatibility source | existing installed DB migration suite/device update 미실행 | FOLLOW-UP — data-preserving install/migration QA 필요 |

## P11 hard gate

| Gate | Status | Evidence boundary |
|---|---|---|
| P0 RESTORE 중 설명 없는 미완료 없음 | PASS | 36개 RESTORE 행을 모두 이 문서에서 PASS 또는 FOLLOW-UP으로 분류했다. |
| architecture freeze | PASS | Room/SQLiteOpenHelper compatibility 경계와 Compose/View interop을 유지했다. |
| DB owner | PASS | FitnessRoomDatabaseProvider가 production owner이고 legacy helper를 새 runtime owner로 재도입하지 않았다. |
| external contracts | PASS | Personal OS, legacy sync, Nutrition import, PriceTrace contract 변경이 없다. |
| legacy data regression 없음 | FOLLOW-UP | source migration/backup compatibility는 유지되지만 installed legacy DB/device regression은 미실행이다. |
| process recreation 핵심 state 보존 | PASS with device follow-up | SavedStateHandle/rememberSaveable와 state tests가 근거다. 실제 process recreation은 미실행이다. |
| empty/rich account crash 없음 | FOLLOW-UP | empty/rich read/state test는 통과했지만 production Compose no-crash device 증거가 없다. |
| Light/Dark usable | FOLLOW-UP | theme/token/visual test path는 있으나 device screenshot 검증이 없다. |
| kg/lb semantics 유지 | PASS with device follow-up | canonical kg 저장과 display/input unit conversion source/tests가 근거다. |
| offline/disconnected graceful state | PASS with device/remote follow-up | local-only, login-required, failed-sync safe copy/policy source/tests가 근거다. 실제 network toggle는 미실행이다. |

이번 P11 재검증에서는 P0 blocker가 없으므로 BLOCKED로 분류한 항목이 없다. Device/remote follow-up은 환경 증거의 한계이며 architecture나 데이터 계약을 훼손해 해소할 항목이 아니다.

## 실행 검증

환경: Windows PowerShell, local workspace, source boundary b2f488f00a9a83783ecb44ae01f44ff8c729ab92.

| Command | Result |
|---|---|
| .\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest | PASS — BUILD SUCCESSFUL in 22s; unit-test XML 집계 335 tests, failures 0, errors 0, skipped 0; debug APK와 instrumentation APK compile task 통과. 이번 실행에서 대부분 task는 UP-TO-DATE였다. |
| adb devices -l | SKIPPED — List of devices attached 아래 device 없음. |
| emulator -list-avds | SKIPPED — local environment에서 emulator command 없음. |
| connectedDebugAndroidTest | SKIPPED — 연결된 device/emulator가 없어 실행하지 않음. |
| git diff --cached --check | PASS — staged P11 문서에서 whitespace error 없음. |
| node .github/project-docs/validate-project-docs.mjs --config project-docs.config.json --require-tracked | PASS — 0 error(s), 0 warning(s). |
| node .github/project-docs/sync-project-docs-to-notion.mjs --config project-docs.config.json | BLOCKED BY EXECUTION POLICY — 기본 CLI 실행은 외부 Notion egress 가능성이 있어 거부됨. 실제 외부 write는 시도하지 않음. |
| exported syncProjectDocuments with dryRun=true and generated placeholder environment | PASS — local render/hash only; 프로젝트 소개 6064 chars, 프로젝트 상세 12000 chars. 실제 token/page ID와 외부 egress를 사용하지 않음. |

## Non-blocking follow-up ledger

~~~
[FOLLOW-UP]
Origin: P2A
Issue: picker의 앞·뒤 해부학 모델을 직접 조작하는 visual selector는 현재 interop primitive와 분리되어 있다.
Why not blocker: canonical body/subpart filter와 exercise image fallback production path는 유지되고, 새 DB/API 계약이 필요하지 않다.
Target phase: 다음 UI polish scope

[FOLLOW-UP]
Origin: P11
Issue: disposable emulator/device가 없어 Light/Dark, font scale, process recreation, GPS 상태, empty/rich no-crash를 실제 runtime에서 실행하지 못했다.
Why not blocker: local code/test/build gate는 통과했고, 장치 증거만 부재하다.
Target phase: device QA

[FOLLOW-UP]
Origin: P11
Issue: Supabase connected/disconnected 및 online/offline의 live provider/network toggle를 실행하지 못했다.
Why not blocker: local-first graceful state와 contract-preserving policy가 source/test에 있고, live RLS/network proof는 별도 환경이 필요하다.
Target phase: remote/device QA

[FOLLOW-UP]
Origin: P11
Issue: legacy installed DB migration and data-preserving update regression을 실행하지 못했다.
Why not blocker: Room legacy entities/migrations and backup/import paths remain in source; destructive uninstall or clear operation is not allowed.
Target phase: data-preserving device QA
~~~
