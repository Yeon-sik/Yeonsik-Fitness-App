# PriceTrace 브랜드를 선택하는 제품·재료 직접 등록

## 기준과 범위

- 원본 체크아웃의 `feat/records-ux-clarity`에는 대상 파일의 미커밋 변경이 있었다. 사용자가 선택한 최신 `feat/fitness-glass-completion`에서 `feat/pricetrace-brand-registration`을 분기했다.
- 먼저 기존 작업을 `2e287caa`(사진 추정 식단), `bf163ad2`(홈 UI)로 커밋했다.
- 원본 `C:/Github/personal-os/FitnessApp`의 미커밋 작업은 보존했다. 구현 위치는 이 채팅의 연결된 FitnessApp 작업 트리다.
- 구조 기준 문서의 CURRENT는 과거 조사 시점의 상태다. 이번 작업은 실제 현재 소스의 Room·Compose·Repository 경계를 따르며 전면 이행을 추가하지 않는다.

## 현재 구현

- 제품·재료 모두 이름과 영양정보를 직접 입력한다.
- 브랜드는 선택 사항이다. 텍스트 입력 → 검색 버튼 또는 키보드 검색 → 결과 직접 선택 순서로 연결한다.
- `get_brand_read_v1`의 `brand_read.v1`만 소비한다. 잘못된 UUID·빈 정식 이름·잘못된 필드 타입의 행은 버리고 계약 버전 불일치는 조회 실패로 처리한다.
- 선택하면 `PriceTrace 브랜드 · 정식 이름`과 연결 해제를 표시한다. 별칭은 검색 근거이며 저장 이름은 정식 이름이다.
- 조회 실패·결과 없음·연결 미설정이어도 텍스트 브랜드로 로컬 등록할 수 있다. 검색은 로컬 저장 작업과 별도로 실행한다.
- 선택한 브랜드 ID·정식 이름은 기존 SavedStateHandle 초안 키 방식으로 보존한다. 검색 결과는 복원하지 않고 다시 조회한다.
- 요청 버전, 소유자, 편집 화면, PriceTrace 설정 범위로 늦은 결과와 선택을 제한한다. 식단 등록은 영양 계정 변경 시 이전 등록 초안을 폐기하고 이전 계정 초안의 저장을 거부한다.

## 저장·마이그레이션·동기화

- 연결된 직접 등록은 `brand = brand_name = canonical_name`과 `pricetrace_brand_id = brand_id`를 저장한다. 텍스트가 정식 이름과 다르면 거부한다.
- 미연결 입력은 텍스트를 `brand`에 저장하고 브랜드 ID는 null이다.
- 기존 PriceTraceBrandSelection을 identity 원본으로 유지하며 NutritionFood·Builder·NutritionFoodInput·동기화 행에서 브랜드 ID를 노출한다. 중복된 독립 identity 상태는 만들지 않는다.
- **기존 반영 확인:** 선택한 기준 브랜치의 Room 버전은 이미 57이다. `V56_TO_V57`, export된 `57.json`, `PriceTraceBrandMigrationTest`가 브랜드 ID와 표시 스냅샷 컬럼을 이미 정의한다. 기존 migration을 다시 쓰거나 중복 컬럼을 추가하지 않는다.
- **기존 원격 migration 재사용:** Nutrition 저장소의 `20261023121311_pricetrace_brand_catalog_consumer.sql`에 nullable UUID 컬럼과 스냅샷 컬럼이 이미 있다. PriceTrace DB로 향하는 FK는 없다. 동일 컬럼을 중복 생성하는 새 migration은 추가하지 않았다.
- 기존 NutritionCatalogSyncClient의 브랜드 ID·표시 스냅샷 push/pull 매핑을 재사용한다. 구형 응답에 해당 값들이 없으면 null로 읽으며, 새로운 push는 값을 명시적으로 전송한다.
- 브랜드만 선택한 직접 등록은 `product_nutrition_links`에 쓰지 않으며 브랜드 선택 때문에 `source_type`을 바꾸지 않는다. 기존 제품 묶음 키와 식단 스냅샷 구조도 유지한다.

## 배포 순서와 실패 처리

1. 별도 PriceTrace 작업에서 `get_brand_read_v1`을 준비한다. 준비 전에도 로컬 텍스트 등록은 가능하다.
2. Nutrition DB에 기존 브랜드 컬럼 migration을 먼저 적용하고 API 스키마 캐시를 확인한다.
3. 앱을 배포한다.

원격 Nutrition DB에 컬럼이 없으면 upsert는 실패할 수 있다. `PGRST204` 또는 `42703`에서 브랜드 컬럼 누락을 감지하면 DB 업데이트 후 재동기화하라는 오류로 중단한다. 전송에서 ID를 빼거나 성공으로 처리하지 않으며 로컬 기록은 유지한다.

이번 작업에서는 PriceTrace·Nutrition 원격 DB의 상태를 조회하거나 migration을 적용하지 않았다. 실제 원격 RPC·RLS·서버 배포 완료를 주장하지 않는다.

## 제거한 코드와 유지한 경계

사용처를 확인한 뒤 직접 등록 전용 코드를 제거했다.

- `NutritionProductPicker.kt`
- `NutritionProductLookup.kt`의 lookup, 영양 미리보기, picker 상태, 제품 선택 초안 codec
- NutritionEditorController의 제품 질의·검색·선택·선택 해제·영양 자동 읽기·식단 복귀 콜백과 전용 SavedState 키
- `openProductRegistration → openProductForMeal` 전달 경로와 ManualFoodEditor의 제품 검색 버튼
- 전용 `NutritionProductPickerUiTest`, `NutritionProductDraftCodecTest`, 폐기된 화면 경로만 검증하던 테스트

ProductReadV1Client, 제품 연결·해제 repository API, 제품 링크 동기화, 식당 PriceTrace 흐름과 해당 repository 회귀 테스트는 유지했다. 기존 식품 라이브러리의 별도 브랜드 변경 기능도 유지했다.

## 검증

- JVM: 627개 중 625개 통과, 기존 조건부 2개 건너뜀, 실패·오류 0.
- Android: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` 통과.
- 계측: 전용 API 36·16KB 임시 에뮬레이터에서 영향받는 등록·상태·저장·마이그레이션·동기화 테스트 57개 통과. 초기 그래픽 환경 지연을 해소하고 Vulkan을 비활성화한 SwiftShader에서 검증했다.
- 화면: 별칭 검색 결과, 연결·해제 표시, 제품 직접 등록 화면 캡처를 확인했다.
- 실기기: 추가 요청으로 Galaxy A25(SM-A256N)에 동일 QA 서명의 `adb install -r --user 0` 업데이트 성공. APK는 소스 `734434e0`의 버전 1.1.0(2)이다.
- 데이터 보존: 설치 직후 DB·설정 12개 파일의 해시가 모두 동일했다. 실행 후에도 기존 43개 테이블의 행이 동일하고 schema v57, `integrity_check=ok`였다. 실행 후 변경 파일은 Android 프레임워크의 `android.app.ActivityThread.IDS.xml`뿐이다.
- 실기기 실행: 실행 명령 성공, 수집한 시작 로그의 fatal marker 0건. 기기가 보안 잠금 상태여서 실제 등록 화면의 직접 검증은 수행하지 못했다.
- 원격 Supabase: 이번 작업에서 미조회·미적용·미검증.
