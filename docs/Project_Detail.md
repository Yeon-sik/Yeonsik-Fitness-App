# Project Detail | FitnessApp 기술·운영 상세

> 이 문서는 `main`의 현재 코드와 로컬 검증 결과를 기준으로 FitnessApp의 책임, 데이터 흐름, 외부 경계, 미검증 영역을 정리한다.

| 항목 | 기준 |
| --- | --- |
| 기준 커밋 | `main` `89672f52cf4cd5219e6e1cd5df71bcefb3792885` |
| 기준일 | 2026-09-05 |
| 플랫폼 | Java 17, Android View, min/target SDK 26/36 |
| 저장소 | `SQLiteOpenHelper` local schema v49 |
| 빌드 | Android Gradle Plugin 8.11.0, Gradle 9.0.0 |
| 앱 surface | `personal`, `test-friends`, `commercial` |

## 문서 목적과 범위

이 문서는 화면 기능을 나열하는 문서가 아니라, 현재 구현된 데이터 소유권과 검증 가능한 경계를 설명하는 기술 기준이다.

포함 범위:

- `MainActivity`와 하단 탭, 루틴·근력·유산소·기록·발전·식단·보충제·설정 흐름
- SQLite v49 스키마, local-first 저장, backup/restore, 마이그레이션 호환성
- 운동 종목 identity, load state, volume·근육 요약, 운동 이미지 lookup
- 공식 식품·recipe·external menu, 외식 섭취 비율, PriceTrace 상품 영양 연결
- Supabase Auth·공유 sync·Nutrition 경계, 위치·지도·Android Keystore 경계
- 실제로 실행한 로컬 테스트와 아직 실행하지 않은 기기·원격·release 검증

제외 범위:

- 운영 Supabase/RLS가 실제로 통과했다는 주장
- release 서명·스토어 배포·실기기 실행 완료 주장
- 의료 진단, 자동 영양 처방, 보충제 복용 안전 승인
- `.understand-anything/` 생성물과 문서에 없는 추정 지표

## 시스템 아키텍처

```text
MainActivity / ScreenHost
  ├─ 메인: 오늘 입력과 주요 진입점
  ├─ 피트니스: 루틴·근력 세션·유산소 추적
  ├─ 기록: 날짜별 기록·추세·요약
  ├─ 발전: 목표·신체 프로필·훈련/영양/회복 근거
  └─ 설정: 계정·동기화·surface·백업/복원
        ↓
  Repository / contract / validation
        ↓
  FitnessDatabaseHelper (SQLiteOpenHelper, v49)
        ├─ workout / routine / cardio
        ├─ meal / nutrition / dining / product
        ├─ development / supplement / sync state
        └─ explicit local backup/restore
        ↓ 수동 동기화 경계
  SupabaseSyncManager → shared Supabase tables
  Nutrition repositories → independent Nutrition Supabase boundary
  PriceTrace config/cache → external product read boundary
```

기본 데이터 흐름은 `입력 → repository/contract 검증 → SQLite`다. 네트워크는 기록 완료의 선행 조건이 아니며, 설정에서 수동 동기화를 실행할 때만 공유 경계를 통과한다.

### 화면과 책임

| 표면 | 책임 | 데이터 경계 |
| --- | --- | --- |
| 메인 | 오늘 기록, 운동·식단·체중으로의 빠른 진입 | local read/write |
| 피트니스 | 루틴 CRUD, 루틴/자유 세션, 종목·세트, cardio tracking | workout/cardio local tables |
| 기록 | 월간 날짜, 선택일 운동·식사·체중, 체중 추세 | local aggregation |
| 발전 | 목표, 키·체중, 최근 기록, 훈련·영양·회복, review insight | local derived view; 처방 아님 |
| 설정 | shared/Nutrition/PriceTrace config, Auth, manual sync, backup/restore | 외부 연결은 명시적 실행 |

## 데이터 모델과 저장 경계

`FitnessDatabaseHelper.DATABASE_VERSION`은 49다. `onUpgrade()`는 이전 버전에서 순차적으로 새 열·인덱스·테이블을 추가하고, 기존 local 기록을 삭제하지 않는 방향으로 동작한다.

| 그룹 | 대표 테이블 | 보존 원칙 |
| --- | --- | --- |
| 공유 요약 후보 | `devices`, `workout_records`, `workout_exercises`, `workout_sets`, `meal_records`, `weight_records` | user/scope/date와 부모-자식 관계를 보존하며 sync allowlist로 제한 |
| 동기화 상태 | `sync_state` | cursor와 방향을 local에 보존; 원격 전체 재조회로 대체하지 않음 |
| 루틴·운동 | `routines`, `routine_exercises`, `exercise_picker_preferences` | 루틴과 실제 세션을 분리하고 family/variant를 별도 identity로 보존 |
| 유산소 | `cardio_sessions`, `cardio_route_points` | 위치 원시 경로는 local 전용; 요약 거리·시간과 분리 |
| 식단·영양 | `nutrition_foods`, `nutrition_food_nutrients`, `nutrition_food_components`, `meal_records`, `meal_record_items`, `meal_record_item_nutrients`, `meal_record_item_components` | ingredient·recipe·external menu와 영양 snapshot을 분리 |
| 조합·외식 | `composition_templates`, `composition_groups`, `composition_members`, `dining_out_menu_add_on_links`, `dining_out_menu_component_links`, `meal_record_item_consumptions` | 구성 그룹, 제공 방식, diner count, 실제 섭취 비율을 분리 |
| 상품·영수증 | `product_nutrition_links`, `pricetrace_product_cache`, `verified_receipt_items` | exact external ID와 local snapshot을 유지; 이름 추정으로 승인하지 않음 |
| 발전·보충제 | `body_profiles`, `development_goals`, `nutrition_goals`, `nutrition_daily_checkins`, `supplement_items`, `supplement_schedules`, `supplement_schedule_slots`, `supplement_intake_records`, `supplement_effect_checkins` | 원시 기록과 계산된 review 상태를 혼동하지 않음 |

상세 식단 구성·영양값·GPS 경로·발전·보충제 확장은 현재 shared sync table allowlist에 포함되지 않는다. remote migration과 계약이 확인되기 전까지 local에 남기는 것이 현재 호환성 경계다.

## 운동 도메인

### 기록 계약과 입력 상태

`FitnessRecordContract.VERSION`은 1이다. 기록 유형은 다음 여섯 가지다.

- `weight_reps`
- `reps_only`
- `time`
- `weight_time`
- `assisted_weight_reps`
- `bodyweight_added_weight_reps`

`LoadState`는 `BODYWEIGHT`, `EXTERNAL_LOAD`, `ADDED_WEIGHT`, `ASSISTED`, `BAND_ASSISTED`, `BAND_RESISTED`로 구분한다. 최근 입력 화면은 load state에 따라 헤더·필드·필수값을 바꾸며, 상태 전환에서 reps와 load를 섞어 덮어쓰지 않는다. RIR은 rep 기반 기록에만 적용한다.

완료 세션은 종목·세트·volume·시간·근육 분포를 local에서 계산한다. 근육 분포 요약은 primary 기여를 1.0, secondary 기여를 0.5로 계산하며, 이 계산 결과가 의료적 신체 판단을 뜻하지는 않는다.

### 종목 identity와 이미지

`exercise-family-mapping-v1.json`의 현재 매핑은 legacy 340개 중 340개이며 미매핑·모호 항목은 0개다. family 정의는 103개, 승인 preset은 27개다. 저장·검색에서 이름보다 family/preset/visual variant ID를 기준으로 삼는다.

이미지 조회 순서는 다음과 같다.

```text
exact visual variant → family default → placeholder
```

picker는 exact-only 정책을 사용한다. 고정 기구·카메라·인체 비율이 검수되지 않은 장면을 제품 이미지 근거로 승격하지 않는다.

## 유산소·기록·발전

- `CardioActivityType`은 걷기·달리기·자전거다.
- foreground location service와 거리 필터가 `cardio_sessions` 및 `cardio_route_points`를 갱신한다.
- 지도 키가 구성된 경우 Google Map에 route를 투영하며, 지도 오류가 나도 local 거리·시간 요약은 보존한다.
- `RecordsScreen`은 월간 달력, 선택일 workout/weight/meal, 체중 추세를 제공한다. 추세는 충분한 점이 없을 때 억지로 선을 만들지 않고 목록으로 남긴다.
- `WorkoutSummaryScreen`은 근력 volume·완료 세트·시간 또는 유산소 거리·시간을 보여 준다.
- `DevelopmentScreen`은 목표·신체 프로필·최근 기록·훈련/영양/회복 coverage와 최대 3개 review insight를 보여 준다.
- `PaperAdviceEngine`의 결과 상태는 `ACTIONABLE`, `INFORMATIONAL`, `INSUFFICIENT_DATA`, `SAFETY_REVIEW`다. 근거가 부족한 경우 처방 대신 상태와 한계를 표시한다.

## 영양·외식·상품

### 영양 catalog

`NutritionFood`는 ingredient, recipe, external menu를 구분한다. `NutritionCatalogRepository`는 local verified/public/private catalog, packaged product variant, 저장한 외식 메뉴·구성, recipe와 product link를 한 검색 흐름에서 다루되 소유자·공개 범위를 분리한다.

`app/src/main/assets/verified_food_catalog_v3.json`의 현재 공식 catalog는 68행이다.

| 항목 | 값 |
| --- | --- |
| source version | `2025-12-23` |
| 기준량 | 가식부 100g |
| raw | 64행 |
| cooked | 4행 |
| 결측 정책 | 공식 공란은 `null`; 알 수 없는 값을 0으로 추정하지 않음 |

생것과 조리 행은 독립된 공식 측정값이다. 생선의 `회`는 표시 alias일 뿐 안전성·횟감 등급을 뜻하지 않는다.

### 외식 identity와 섭취량

외식은 `dine_in`/매장, `delivery`/배달, `takeout`/포장을 구분한다. 제공 항목은 included/basic, paid, review event, service, coupon, promotion으로 구분한다.

`DiningOutIdentity`는 `dining-out-identity.v1`을 사용하고, restaurant/location/menu/catalog product의 exact UUID, snapshot name, `locationSourceNamespace`를 보존한다. `pricetrace` namespace의 ID를 이름이나 AI 추정으로 대체하지 않는다.

`DiningOutConsumption`은 `dining-out-sharing.v1`을 사용한다. diner count와 `consumedFraction`, `equal_by_diners`/`manual` 방식을 별도 저장해 메뉴 전체 영양값과 실제 섭취량을 분리한다.

`ProductNutritionLink`의 상태는 suggested/approved/rejected이며, 영양 row마다 승인 연결은 하나만 허용한다. 승인에는 exact PriceTrace product identity와 영양 snapshot이 필요하다.

## 보충제·증거 카드

보충제 흐름은 catalog 선택 → plan/schedule slot → taken/skipped 기록 → correction → effect/side-effect check-in 순서다. `SupplementEvidenceCatalog`의 코드상 검토일은 `2026-08-19`이며, creatine·caffeine·beta-alanine은 직접 검토 카드로, 다른 항목은 제한적·혼합·맥락 필요 상태로 구분한다.

이 기능은 복용량 안전성이나 의료 승인을 자동 생성하지 않는다. 근거 카드는 정보 상태와 검토 범위를 보여 주며, 사용자의 임상 판단을 대체하지 않는다.

## 핵심 기술 의사결정

| 결정 | 이유 | 현재 결과 |
| --- | --- | --- |
| Java 17 + Android View 유지 | 기존 제품 코드와 화면 흐름의 호환성 유지 | Kotlin/Compose/Room 전면 재작성 없이 단계적 확장 |
| SQLite local-first | 오프라인 기록과 데이터 소유권을 네트워크에서 분리 | 기록 완료가 원격 상태에 종속되지 않음 |
| contract·identity 분리 | 운동·외식·상품을 이름으로 연결할 때의 충돌 방지 | version, exact ID, snapshot, status를 별도 보존 |
| null과 0 구분 | 공식 결측값의 의미를 보존 | 영양값과 review 상태에서 추측을 차단 |
| shared sync allowlist | 원격 migration·계약이 없는 local 확장을 보호 | 상세 식단·GPS·일부 운동 확장은 local에 유지 |
| surface별 설정·token namespace | personal/test-friends/commercial 간 데이터 혼합 방지 | unknown surface는 fail closed; Keystore alias도 분리 |
| 증거 상태 기반 조언 | 기록만으로 의료·영양 처방을 만들지 않기 위함 | `SAFETY_REVIEW`·`INSUFFICIENT_DATA` 등으로 제한 |

## 테스트와 검증 전략

검증은 build/test, lint, ADB/계측, release 서명, 원격 운영을 별도 축으로 기록한다. 2026-09-05에 `main`과 동일한 코드에서 실행한 결과는 다음과 같다.

| 명령 | 결과 | 의미 |
| --- | --- | --- |
| `./gradlew.bat testDebugUnitTest --no-daemon` | 통과 | Java 단위 테스트와 debug test task 완료 |
| `./gradlew.bat assembleDebug --no-daemon` | 통과 | debug APK 생성 |
| `./gradlew.bat assembleDebugAndroidTest --no-daemon` | 통과 | Android 테스트 APK 컴파일·패키징; 계측 실행 아님 |
| `./gradlew.bat lintDebug --no-daemon` | 실패 | API 27 요구 styles resource 오류 4개, warning 54개 |
| `adb devices -l` | 4개 대상 관찰 | ADB endpoint/emulator가 보였지만 `connectedDebugAndroidTest`는 실행하지 않음 |

아직 실행하지 않은 검증은 release keystore 빌드, 실제 앱 설치·로그인·동기화, Supabase Auth/RLS 두 계정 격리, PriceTrace/Personal OS 원격 read-back이다. 따라서 현재 상태를 release 또는 운영 완료로 표현하지 않는다.

## 외부 연동과 실패 경계

| 연동 | 현재 역할 | 실패 시 보존되는 것 |
| --- | --- | --- |
| shared Supabase | `sync_fitness_data_v1` RPC 우선, 불가하면 legacy REST fallback; `devices`, workout 3종, meal/weight summary allowlist | local SQLite와 cursor |
| Nutrition Supabase | Nutrition catalog·private food/recipe의 독립 원격 대상 | local catalog와 local meal record |
| PriceTrace | exact product identity·nutrition 후보를 읽는 외부 경계; 이 저장소에는 PriceTrace migration 없음 | local snapshot·pending link |
| Android location | 유산소 위치 기록 | 세션의 거리·시간; 지도 오류와 분리 |
| Google Maps | 구성된 key로 route 표현 | local route/session data |
| Android Keystore | shared/Nutrition/PriceTrace token 암호화 저장 | 로그인 전 local data; token 자체는 평문 저장하지 않음 |
| GitHub Actions/Notion | canonical `main` 문서가 반영된 뒤 mirror 갱신 | Git Markdown 원본 |

동기화는 RPC batch 500, 최대 RPC call 1000, cursor 기반이다. Auth는 password/refresh session을 사용하며, 반환된 user UUID가 local user와 다르면 계정 전환을 임의로 진행하지 않는다.

## 보안·복구

- access/refresh token은 AES/GCM과 AndroidKeyStore를 사용하고 shared, Nutrition, PriceTrace마다 alias와 preferences namespace를 분리한다.
- manifest는 `allowBackup=false`, cleartext traffic disabled 정책이다. Android 자동 백업을 제품 복구 경로로 간주하지 않는다.
- `LocalDataBackupService`는 사용자가 명시적으로 실행한 `fitness-os.local-backup` JSON을 사용한다. format version은 1, 크기 제한은 32 MiB/250,000 rows다.
- 백업에는 owner/public scope를 구분하고, restore는 transaction으로 적용하며 route 중복 방지와 catalog reconcile을 수행한다. CSV summary export도 별도 제공한다.
- 설정·로그·문서에 secret, token, 실제 user UUID를 기록하지 않는다.

## 배포·운영·복구

### Build surface와 서명

`personal`, `test-friends`, `commercial` surface가 있고 variant는 debug, qa, release다. qa/release는 외부 signing 설정을 요구하며, release 완료를 말하려면 서명·기기·원격 운영 게이트를 각각 통과시켜야 한다.

업데이트는 기존 설치본의 인증서와 새 APK의 인증서가 일치해야 한다. 데이터 보존이 필요한 QA 검증에서는 먼저 backup과 인증서를 확인하고 호환되는 APK를 `adb install -r`로 설치한다. `INSTALL_FAILED_UPDATE_INCOMPATIBLE`를 우회하려고 uninstall이나 `pm clear`를 routine으로 사용하지 않는다.

### 원격 migration과 문서 발행

공유 Supabase와 Nutrition Supabase는 서로 다른 migration target이다. Nutrition migration을 shared project에 적용하지 않는다. PriceTrace는 이 저장소의 migration 소유 대상이 아니다. 자세한 경계는 [`supabase/README.md`](../supabase/README.md)에 둔다.

Git Markdown의 canonical branch는 `main`이고, configured publication mode는 `on-main-push`다. 문서 브랜치에서는 validator와 Notion render-only dry-run만 수행하며, merge 후 Actions와 Notion read-back을 별도로 확인해야 발행을 완료로 판단한다.

## 한계, 기술 부채, 다음 단계

| 항목 | 현재 한계 | 다음 검증/작업 |
| --- | --- | --- |
| lint | API 27 resource 오류 4개와 warning 54개 | styles resource 호환성 수정 후 lint 재실행 |
| 계측 | 테스트 APK만 컴파일·패키징 | ADB 대상 설치 후 connected instrumentation 실행 |
| release | 외부 keystore와 `FITNESS_RELEASE_*` 설정 미검증 | QA/release 서명·`adb install -r` 데이터 보존 확인 |
| remote | Auth/RLS, 두 계정 격리, RPC/fallback read-back 미검증 | 실제 프로젝트에서 독립 계정과 migration 상태 확인 |
| sync 계약 | local-only 확장이 shared table에 반영되지 않음 | remote schema가 준비될 때만 별도 versioned contract로 확장 |
| 콘텐츠 | 공식 식품·운동 이미지·보충제 근거가 큐레이션 범위 | 출처·identity·결측·검수 상태를 유지하며 범위 확장 |
| 조언 | 발전/paper advice는 기록 기반 상태 안내 | 처방·진단으로 확장하지 않고 evidence coverage 개선 |
| 문서 mirror | 문서 브랜치는 발행 대상이 아님 | `main` 반영 후 Actions 성공과 두 페이지 read-back 확인 |

## 관련 파일과 문서

- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [Supabase migration 경계](../supabase/README.md)
- [Fitness Record Contract v1](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [통합 릴리스 준비 기준](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
