# 활동 정보 검토함: 현재 범위와 다음 작업

2026-11-06. 기준: main `1df25929`와 현재 구현.

## 이번 구현

설정의 **활동 정보 검토함**에서 `information-record.v1` 자료를 작성하거나 JSON 파일로 가져온다. 현재 로컬 사용자와 연결 프로젝트가 소유 범위이며 파일의 사용자 식별자를 신뢰하지 않는다. Fitness 소유 자료만 받는다. 날짜·타입·출처 연결·다른 앱의 명시적 필드 namespace를 검증하고, 소유 범위를 사용자가 확인해야 저장한다.

자료는 검토 대기로 저장한다. 검토함/보관함 상태는 수신 메타데이터이며 원문을 바꾸지 않는다. 같은 ID와 같은 내용은 중복 성공, 다른 내용은 충돌이다. 정정은 새 ID와 `supersedes_record_id`로 보존한다. 임의 JSON·미확인 정보·원래 decimal 문자열과 출처 텍스트가 저장/내보내기에 남는다. 파일 출처의 해시를 실제 파일과 비교한 것으로 표현하지 않는다. 출처 URL을 자동 요청하지 않는다.

Room v57→v58은 `fitness_information_inbox`, `fitness_information_drafts`만 추가한다. 기존 운동·식단·체중·보충제 테이블의 행은 변경하지 않는다. 두 테이블은 기존 로컬 백업에 포함하고 구버전 백업은 계속 읽는다. 외부 공개, Supabase 동기화, 운동/칼로리/체중 합계에는 이 검토함을 연결하지 않았다. 초안은 별도 보관 버튼으로 유지한다. 저장 전 수정 중인 내용은 화면 회전에서는 유지되지만 프로세스 종료 전에 초안 보관해야 한다.

이 구조는 표현 가능한 정보를 유실 없이 보관하는 확장 경로다. 모든 매체의 자동 이해나 언어 내용만으로 완전한 소유권 판별을 보장하지 않는다.

검증: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` 통과. JVM 637개 중 실패 0, 기존 live test 2개 skipped. 격리된 Android emulator에서 새 검토함 migration/backup/restart/계정·프로젝트 격리/중복·충돌/집계 불변, 소유 확인 UI, 기존 migration·backup 회귀까지 15개 instrumentation test 통과. 검토함/초안 화면 캡처를 확인했다. 물리 기기 설치와 원격 DB 검증은 수행하지 않았다.

## 기존 수용 범위와 빈 구조

| 영역 | 확인한 현재 구조 | 남은 구조 |
|---|---|---|
| 운동·활동 | 세션/세트/루틴, GPS/시간/거리/심박, 중량 단위·RPE/RIR | 임의 활동 정보는 검토함까지 수용. 전용 모델·집계로의 명시적 변환은 별도 작업 |
| 식단 | 구성 항목별 섭취량·영양 snapshot, 카탈로그·출처 | OCR 원격 Meal을 Android로 가져오는 전용 수신 경로 |
| 신체 | 체중·신장·메모 | 그 밖의 활동 관련 측정값의 전용 화면·시계열 집계 |
| 외부 참조 | PriceTrace 상품/식당/메뉴 ID, CashOS 영수증/원장 ID | 앱 간 인증된 참조 해석과 삭제/수정 통지 |
| 건강 영역 | 보충제 일정·복용·효과, 수면시간·소화·에너지 체크인 | HealthTrace 소유로 이행하는 검증된 전송과 읽기 projection |

## 우선순위 1: 원격 Meal 수신

`import_verified_meal_v1`은 Nutrition Supabase에 원격 상세 식단을 저장한다. Android `NutritionCatalogSyncClient`는 음식/영양소/구성요소/제품 연결만 받는다. 원격 저장 성공을 Android 수신 성공으로 취급하면 안 된다.

원격 Meal은 알 수 없는 영양값을 NULL로 보존하며 섭취 원입력·confidence·provenance를 가진다. 현재 로컬 핵심 영양 필드는 NOT NULL인 부분이 있다. 수신 시 0으로 채우면 자료의 의미가 바뀐다. 별도 Meal 수신 계약, nullable 모델/표시/집계의 보존 계획, 원격 snapshot과 출처 저장, owner/project 인증 매핑, 재전송·충돌·실제 기기 검증이 선행되어야 한다. Nutrition 카탈로그 동기화 안에 몰래 추가하지 않는다.

## 구현된 건강 정보 소유권 경계

현재 Fitness의 보충제·건강 체크인 원본은 legacy 자료로 유지한다. 보충제 화면과 앱의 runtime repository port는 신규/수정/삭제/되돌리기/복용 계획/경과 기록을 허용하지 않는다. 새 기록 버튼은 HealthTrace(`com.yeonsik.health.health_observation_app`)를 연다. 저수준 기존 저장 코드는 과거 백업·migration 호환성을 위해 남긴다.

체크인은 Fitness 소유의 수분 섭취와 운동 준비도만 편집한다. 기존 sleep/energy/hunger/digestion/note를 변경하거나 지우는 요청은 runtime port에서 거절한다. 이전 건강값은 화면에 읽기 전용으로 표시한다. 기존 행을 삭제하거나 건강값을 0으로 초기화하지 않는다.

설정·보충제·체크인에서 **이전 건강 자료 내보내기**로 전체 snapshot 또는 개별 행을 JSON 파일로 내보낸다. `healthtrace.legacy_fitness_health_snapshot`의 `legacy_rows`에 이전 보충제 5개 테이블과 건강 체크인 부분만 보존하며, 수분/운동 준비도와 다른 사용자 행은 제외한다. 원래 ID·시각·값·삭제 상태를 유지하고 정확한 과거 관찰 시각이 없으면 `occurred_at`을 만들지 않는다. 이 파일은 HealthTrace의 검토 자료이며 의료·복용 집계로 자동 반영하지 않는다.

Room v58→v59는 `fitness_health_export_receipts`만 추가한다. 이는 도메인 건강 기록이 아닌 **내보내기 영수증**이다. 최초 사용자의 내보내기 요청 때 snapshot fingerprint로 결정한 UUID와 실제 수집 시각을 한 번 저장한다. 같은 snapshot의 재시도는 동일 payload를 사용하며, 영수증도 로컬 백업에 포함된다. 전체가 1 MiB보다 크면 쓰기 없이 거절하고 개별 행 내보내기를 안내한다.

추가 검증: JVM 639개 중 실패 0, 기존 2개 skipped; debug 및 instrumentation APK 빌드 통과. 격리 emulator instrumentation 18개 통과: v57→59 보존, owner 격리, 건강 원본 불변, export 재시도/backup, 크기 초과 원자성·개별 행 fallback, UI, 기존 migration·backup 회귀. Android가 실제 생성한 합성 fixture는 `contracts/fitness-health-handoff.v1.example.json`이다.

HealthTrace 전용 복용·건강 모델로의 명시적 변환, 인증된 읽기 projection, 실제 운영 계정 간 전송, 물리 기기 설치는 별도 검증이 필요하다. 원격 DB 변경은 하지 않았다.
