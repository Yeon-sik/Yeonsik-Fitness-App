# 활동 정보 검토함: 현재 범위와 다음 작업

2026-11-06. 기준: main `1df25929`와 현재 구현.

## 이번 구현

설정의 **활동 정보 검토함**에서 `information-record.v1` 자료를 작성하거나 JSON 파일로 가져온다. 현재 로컬 사용자와 연결 프로젝트가 소유 범위이며 파일의 사용자 식별자를 신뢰하지 않는다. Fitness 소유 자료만 받는다. 날짜·타입·출처 연결·다른 앱의 명시적 필드 namespace를 검증하고, 소유 범위를 사용자가 확인해야 저장한다.

자료는 검토 대기로 저장한다. 검토함/보관함 상태는 수신 메타데이터이며 원문을 바꾸지 않는다. 같은 ID와 같은 내용은 중복 성공, 다른 내용은 충돌이다. 정정은 새 ID와 `supersedes_record_id`로 보존한다. 임의 JSON·미확인 정보·원래 decimal 문자열과 출처 텍스트가 저장/내보내기에 남는다. 파일 출처의 해시를 실제 파일과 비교한 것으로 표현하지 않는다. 출처 URL을 자동 요청하지 않는다.

Room v57→v58은 `fitness_information_inbox`, `fitness_information_drafts`만 추가한다. 기존 운동·식단·체중·보충제 테이블의 행은 변경하지 않는다. 두 테이블은 기존 로컬 백업에 포함하고 구버전 백업은 계속 읽는다. 외부 공개, Supabase 동기화, 운동/칼로리/체중 합계에는 이 검토함을 연결하지 않았다. 초안은 별도 보관 버튼으로 유지한다. 저장 전 수정 중인 내용은 화면 회전에서는 유지되지만 프로세스 종료 전에 초안 보관해야 한다.

이 구조는 표현 가능한 정보를 유실 없이 보관하는 확장 경로다. 모든 매체의 자동 이해나 언어 내용만으로 완전한 소유권 판별을 보장하지 않는다.

JSON 파서가 같은 객체의 중복 키를 마지막 값으로 덮어쓰기 전에 원문 구조를 검사한다. 최상위·중첩·배열 안 객체·Unicode escape로 다르게 적은 동일 키 모두 거절한다. 서로 다른 객체에 같은 키가 있는 것은 허용한다. Nutrition 원격 응답도 같은 검사를 거쳐 자료 손실을 막는다. 공통 raw fixture 16개 거절/4개 허용 및 별도 원격 회귀를 포함한 JVM 648개가 통과했다(실패 0, 기존 live test 2개 skipped).

검증: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` 통과. JVM 637개 중 실패 0, 기존 live test 2개 skipped. 격리된 Android emulator에서 새 검토함 migration/backup/restart/계정·프로젝트 격리/중복·충돌/집계 불변, 소유 확인 UI, 기존 migration·backup 회귀까지 15개 instrumentation test 통과. 검토함/초안 화면 캡처를 확인했다. 물리 기기 설치와 원격 DB 검증은 수행하지 않았다.

## 기존 수용 범위와 빈 구조

| 영역 | 확인한 현재 구조 | 남은 구조 |
|---|---|---|
| 운동·활동 | 세션/세트/루틴, GPS/시간/거리/심박, 중량 단위·RPE/RIR | 임의 활동 정보는 검토함까지 수용. 전용 모델·집계로의 명시적 변환은 별도 작업 |
| 식단 | 구성 항목별 섭취량·영양 snapshot, 카탈로그·출처, 원격 Meal 검토 수신 | 검토된 nullable 원문을 전용 식단 모델·집계로 명시적으로 변환하는 경로 |
| 신체 | 체중·신장·메모 | 그 밖의 활동 관련 측정값의 전용 화면·시계열 집계 |
| 외부 참조 | PriceTrace 상품/식당/메뉴 ID, CashOS 영수증/원장 ID | 앱 간 인증된 참조 해석과 삭제/수정 통지 |
| 건강 영역 | 보충제 일정·복용·효과, 수면시간·소화·에너지 체크인 | HealthTrace 소유로 이행하는 검증된 전송과 읽기 projection |

## 구현된 원격 Meal 검토 수신

`import_verified_meal_v1`은 Nutrition Supabase에 원격 상세 식단을 저장한다. Android `NutritionCatalogSyncClient`는 음식/영양소/구성요소/제품 연결만 받는다. 원격 저장 성공을 Android 수신 성공으로 취급하면 안 된다.

원격 Meal은 알 수 없는 영양값을 NULL로 보존하며 섭취 원입력·confidence·provenance를 가진다. 현재 로컬 핵심 영양 필드는 NOT NULL인 부분이 있다. 수신 시 0으로 채우면 자료의 의미가 바뀐다. 따라서 검토함의 **Nutrition 식사 가져오기**는 `NutritionMealReviewClient`에서 읽기만 수행하고 `NutritionMealReviewImporter`가 `fitness.remote_meal_review` 원문을 검토 대기로 저장한다. 기존 카탈로그 동기화와 로컬 식사 테이블·집계는 변경하지 않는다.

Nutrition 계정은 `NutritionSupabaseConfigStore`, 저장 대상은 현재 Fitness 로컬 프로필이다. 두 ID가 같다고 가정하지 않으며 매번 확인창에 양쪽 프로젝트와 계정을 표시한다. 확인한 Nutrition 계정의 기존 access token만 사용한다. 요청 전과 로컬 transaction 전후에 프로필·프로젝트·원격 계정·세션이 그대로인지 확인한다. 만료된 토큰(HTTP 401)은 설정의 Nutrition 재로그인을 안내하며 초안·기존 목록·다음 페이지 위치를 유지한다. 이 경로는 인증 갱신이나 원격 쓰기를 수행하지 않는다.

GET `/rest/v1/meal_records`의 `select=*,meal_record_items(*,meal_record_item_nutrients(*)),meal_verified_imports(*)`로 한 요청의 snapshot을 받는다. `owner_id=eq.{인증 계정}`과 서버 RLS를 사용하고, 반환된 부모·자식의 owner/parent ID를 다시 검증한다. 관계의 고유 FK 때문에 `meal_verified_imports`는 객체 또는 null이다. 이러한 중첩 조회는 [PostgREST의 FK 기반 resource embedding 계약](https://docs.postgrest.org/en/v13/references/api/resource_embedding.html)을 사용한다. 원래 ID·nullable 수치·정확한 JSON 숫자 문자열·출처·수신 요청·삭제 상태를 보존한다. 관계 배열은 ID 순으로 정규화하여 응답 배열 순서 변경만으로 새 revision을 만들지 않는다.

한 페이지는 ID 내림차순 최대 10건이며 다음 페이지는 마지막 ID보다 작은 keyset으로 조회한다. 시간순 목록이 아니며, 진행 중 먼저 지나간 행이 바뀐 경우 처음부터 다시 가져와야 새 내용을 확인할 수 있다. 응답은 UTF-8 12 MiB, 개별 기록은 계약의 1 MiB 한도다. 페이지의 일부를 먼저 저장하지 않는다. 파싱·크기·소유·계정 확인 실패 시 transaction 전체를 되돌린다.

원격 프로젝트·계정·전체 snapshot의 fingerprint로 UUID를 결정한다. 첫 수신 시각은 실제 읽은 시각으로 한 번 보존하고 같은 원문 재수신은 그 시각을 재사용한다. 원문이 바뀌면 별도의 immutable 검토 기록이 생긴다. 기존 초안과 검토 상태는 유지한다. 기존 검토함 테이블과 백업을 사용하므로 추가 DB migration은 없다. 원격 참조는 원래 Meal ID와 Nutrition 프로젝트를 명시한다. 전용 식단으로 반영하거나 총량에 반영하는 기능은 후속 작업이다.

수신 추가 후 검증: JVM 646개 중 실패 0, 기존 live test 2개 skipped; debug 및 instrumentation APK 빌드 통과. Mock HTTP에서 GET·인증·소유 필터·관계 조회·페이지·401/403/redirect 오류 처리를 확인했다. 격리 emulator instrumentation 20개가 통과했으며 nullable/정밀 수치/출처 보존, 재시도, 내용 revision, profile 격리, 계정 변경 rollback, 초안과 native 식사 불변을 포함한다. 확인창 가독성 수정 후 해당 UI 2개를 다시 통과했다. OCR 작업트리의 `scripts/verify-nutrition-meal-read.mjs`가 실제 Nutrition 로그인 계정에서 동일한 embedded SELECT를 읽기 전용 호출하여 HTTP 200과 빈 배열(0건)을 확인했다. 실제 조회 계약의 접근 가능성은 확인했지만 운영 계정의 비어 있지 않은 식사 수신은 검증하지 못했다. 운영 자료를 만들거나 원격 DB를 변경하지 않았다.

## 구현된 건강 정보 소유권 경계

현재 Fitness의 보충제·건강 체크인 원본은 legacy 자료로 유지한다. 보충제 화면과 앱의 runtime repository port는 신규/수정/삭제/되돌리기/복용 계획/경과 기록을 허용하지 않는다. 새 기록 버튼은 HealthTrace(`com.yeonsik.health.health_observation_app`)를 연다. 저수준 기존 저장 코드는 과거 백업·migration 호환성을 위해 남긴다.

체크인은 Fitness 소유의 수분 섭취와 운동 준비도만 편집한다. 기존 sleep/energy/hunger/digestion/note를 변경하거나 지우는 요청은 runtime port에서 거절한다. 이전 건강값은 화면에 읽기 전용으로 표시한다. 기존 행을 삭제하거나 건강값을 0으로 초기화하지 않는다.

설정·보충제·체크인에서 **이전 건강 자료 내보내기**로 전체 snapshot 또는 개별 행을 JSON 파일로 내보낸다. `healthtrace.legacy_fitness_health_snapshot`의 `legacy_rows`에 이전 보충제 5개 테이블과 건강 체크인 부분만 보존하며, 수분/운동 준비도와 다른 사용자 행은 제외한다. 원래 ID·시각·값·삭제 상태를 유지하고 정확한 과거 관찰 시각이 없으면 `occurred_at`을 만들지 않는다. 이 파일은 HealthTrace의 검토 자료이며 의료·복용 집계로 자동 반영하지 않는다.

Room v58→v59는 `fitness_health_export_receipts`만 추가한다. 이는 도메인 건강 기록이 아닌 **내보내기 영수증**이다. 최초 사용자의 내보내기 요청 때 snapshot fingerprint로 결정한 UUID와 실제 수집 시각을 한 번 저장한다. 같은 snapshot의 재시도는 동일 payload를 사용하며, 영수증도 로컬 백업에 포함된다. 전체가 1 MiB보다 크면 쓰기 없이 거절하고 개별 행 내보내기를 안내한다.

추가 검증: JVM 639개 중 실패 0, 기존 2개 skipped; debug 및 instrumentation APK 빌드 통과. 격리 emulator instrumentation 18개 통과: v57→59 보존, owner 격리, 건강 원본 불변, export 재시도/backup, 크기 초과 원자성·개별 행 fallback, UI, 기존 migration·backup 회귀. Android가 실제 생성한 합성 fixture는 `contracts/fitness-health-handoff.v1.example.json`이다.

HealthTrace 수신기에서도 이 Android fixture를 가져와 원문 바이트 문자열, 동일 ID 중복 처리, 로컬 profile 격리, 건강·복용·수면 집계와 mirror 불변을 검증했다(HealthTrace commit `49fbc83`).

HealthTrace 전용 복용·건강 모델로의 명시적 변환, 인증된 읽기 projection, 실제 운영 계정 간 전송, 물리 기기 설치는 별도 검증이 필요하다. 원격 DB 변경은 하지 않았다.
