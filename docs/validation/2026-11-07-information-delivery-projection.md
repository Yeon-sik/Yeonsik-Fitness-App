# Fitness 직접 수신과 실제 기록 반영 검증

검증일: 2026-11-07 (Asia/Seoul). 작업 브랜치: `feat/information-delivery-projection-20261106`. 구현 기준: `941ffdb1` 이후의 격리 worktree. 기존 별도 checkout과 물리 기기 데이터는 변경하지 않았다.

## 사용자 흐름과 경계

설정 → 활동 정보 검토함 → OCR 활동 자료 가져오기에서 Nutrition 계정/프로젝트와 로컬 Fitness 프로필을 함께 확인한다. 수신 성공은 검토 대기 저장이며, 체중·식사·완료 운동 매핑을 선택하고 누락값을 확인한 뒤 미리보기에서 별도로 승인해야 기존 기록과 통계가 변한다. `fitness.observation`을 포함한 임의 Fitness 소유 유형도 이 매핑을 사용할 수 있다.

OCR 원문은 불변으로 남고 반영 영수증에 확인한 값, 소스 fingerprint, 실제 native ID와 정정 계열 ID를 저장한다. 실제 기록·영수증·검토 상태는 같은 Room transaction에서 확정된다. 같은 ID의 재수신, 파일/직접 전송 경로 변경, 연결 프로젝트 변경, 같은 정정 계열의 재반영은 이중 집계를 만들지 않는다. 모르는 필수 영양값, 단위가 없는 양, 불명확한 시간은 사용자 확인 대상으로 남는다. 다른 앱의 실제 기록이나 공개 카탈로그를 수정하지 않는다.

지원 및 한계의 정확한 필드 계약은 [fitness-native-projection.v1](../../contracts/fitness-native-projection.v1.md)을 따른다. 여러 운동/서로 다른 세트를 한 번에 변환하거나 원문에서 GPS·심박·canonical exercise identity를 추정하지 않는다. 이미 반영된 자료의 정정은 기존 실제 기록 화면에서 처리한다.

## 실행 증거

- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: 성공. JVM 659개, 실패 0, 기존 opt-in live test 2개 skipped.
- 전용 Android emulator `emulator-5584`, `adb install -r`: 최종 앱/계측 APK 업데이트 성공. 관련 계측 30개 성공. 실제 A25와 다른 에뮬레이터에는 설치하지 않았다.
- 체중 72.4 kg가 기존 신체 읽기 모델에 한 번만 나타난다. 같은 날짜의 기존 90 kg 기록은 덮어쓰지 않는다.
- 식사 500 kcal / 단백질 30 g / 탄수화물 60 g / 지방 15 g가 기존 식사 읽기·통계에 반영되며 선택 영양값의 미상은 NULL로 남는다.
- 중량 운동 3세트 × 10회 × 20 kg의 실제 completed 세트와 volume 600 kg, 시간 운동 120초의 실제 session을 기존 운동 통계가 읽는다. 심박/GPS는 생성하지 않는다.
- 승인 누락, 변경된 매핑, 계정 변경 중 transaction, 같은 날짜 충돌, 미수신 정정 원본, 순환, 64개를 넘는 계열, 보관 상태 신규 반영을 거절한다. 실패 시 native/receipt가 남지 않는다. 보관 후 동일 영수증 재시도는 새 사실을 생성하지 않는다.
- 파일→직접 전송→다른 inbox namespace 경로에서도 로컬 사용자+record ID UNIQUE가 한 번 반영을 강제한다. sibling/ancestor/descendant 계열 UNIQUE도 검사했다.
- Room v57→현재 및 v60→v61 migration, v60/current backup restore, 기존 5개 migration/backup 회귀를 포함한다. v60 원문·기존 체중 행·기존 매핑은 그대로이고 lineage만 보완되는 것을 행 단위로 비교했다. 중복·불완전 계열을 삭제해서 migration을 통과시키지 않는다.
- `node scripts/test-fitness-information-sql.mjs <@electric-sql/pglite>`: `INFORMATION_INBOX_CONFORMANCE_RLS_IMMUTABILITY_PRECISION_NATIVE_ISOLATION_PASS`. strict raw JSON, 정밀도, 동일/충돌 재시도, 다른 사용자/anon 격리, 원문 불변, 기존 native 수 불변 확인.

## 독립 리뷰 수정

단위가 생략된 `weight_kg`/`calories_kcal` 등에서 이름만 보고 단위를 확정하던 초안 추천을 제거했다. 명시적 단위가 맞는 경우만 자동 보완한다. `occurred_at` 및 원격 `eaten_at`은 offset을 버리지 않고 같은 instant를 기기 시간대로 변환한 다음 날짜·시각을 함께 추천한다. 명시 날짜와 충돌하면 시간을 임의 조합하지 않는다. UTC↔Asia/Seoul 자정 경계 및 단위 누락 회귀가 통과했다.

## 운영/설치 상태

통합 작업의 root가 Nutrition 운영 migration `20261106135251_fitness_information_inbox.sql` 적용과 실제 authenticated/foreign/anon rollback 시험, OCR production gateway의 실제 수신/재시도 검증을 수행했다. 합성 원격 fixture는 제거했다. 이 문서의 Android 통계·migration 증거는 로컬 SQLite/에뮬레이터 증거이며 물리 기기 또는 production native 기록의 검증으로 확대하지 않는다.

최종 debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

SHA-256: `375F473C5B7B2542FB334ACDDF33782F02B7371CA090C9A78375903C13E7DC29`.

출시 서명 검증, 물리 기기 보존 설치, 원격 push는 하지 않았다. 앱 build 로그의 기존 Gradle deprecation 경고는 남아 있다.
