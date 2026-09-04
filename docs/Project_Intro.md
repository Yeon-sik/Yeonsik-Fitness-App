# Fitness App | 운동 기록·식단·발전 점검을 한곳에서 관리하는 Android 앱

> Fitness App은 운동 상세 기록과 식단·보충제·발전 점검을 로컬에 소유하고, 완료된 운동 요약만 Personal OS 공유 경계로 내보내는 Java 17 기반 네이티브 Android 앱이다.

| 항목 | 내용 |
| --- | --- |
| 현재 기준 | `main` `89672f52cf4cd5219e6e1cd5df71bcefb3792885` |
| 문서 기준일 | 2026-09-05 |
| 제품 구조 | Java 17, Android View, SQLiteOpenHelper v49, 로컬 우선 저장 |
| 주요 경계 | 공유 Supabase, 독립 Nutrition Supabase, PriceTrace 외부 읽기 경계, 선택적 위치·지도 |
| 검증 요약 | 단위 테스트·debug APK·Android 테스트 APK 통과; lint 4 errors/54 warnings; 계측·release·원격 운영 미검증 |
| 상세 문서 | [Project_Detail.md](./Project_Detail.md) |

## 30초 요약

- 운동 탭에서 루틴 또는 자유 세션을 시작하고, 종목·세트·중량·횟수·시간·RIR·휴식을 기록한다.
- 기록 탭은 날짜별 운동·식사·체중과 추세를 보여 주고, 발전 탭은 최근 기록·목표·훈련·영양·회복 근거를 묶어 다음 행동을 최대 3개까지 제시한다.
- 식단은 로컬 SQLite 카탈로그를 우선 검색한다. 공식 식품 68개 행은 생것 64개·조리 4개로 분리하며, 공식 결측값은 `null`로 유지한다.
- 외식은 매장·배달·포장, 기본 제공·유료·리뷰·서비스·쿠폰·프로모션과 섭취 비율을 분리해 기록한다. PriceTrace 항목은 이름이 아니라 검증된 ID와 스냅샷으로 연결한다.
- 상세 운동 데이터는 Fitness App이 소유하고, 공유 대상은 `Fitness Record Contract v1`의 완료 요약 범위다. 로컬 백업·복원은 별도 명시 작업이다.

## 문제와 해결

| 문제 | 해결 방식 | 현재 근거 |
| --- | --- | --- |
| 운동 중 입력 필드가 종목마다 달라진다 | 6개 record type과 6개 `LoadState`에 따라 헤더·입력·검증을 바꾼다 | `FitnessRecordContract.java`, `WorkoutExerciseDetailScreen.java` |
| 종목 이름만으로는 기록·이미지를 안정적으로 연결하기 어렵다 | legacy 종목을 103개 family, preset, visual variant ID로 정규화하고 정확한 fallback 순서를 둔다 | `exercise-family-mapping-v1.json`, 운동 picker/이미지 lookup 코드 |
| 최근 기록에서 다음 행동을 찾기 어렵다 | 기록·요약·발전 화면이 로컬 기록과 근거 상태를 함께 계산한다 | `RecordsScreen.java`, `WorkoutSummaryScreen.java`, `DevelopmentScreen.java` |
| 영양값의 출처·조리상태·결측이 섞인다 | 공식 원본 행, 100g 가식부, `raw`/`cooked`, `null`을 독립 필드로 보존한다 | `verified_food_catalog_v3.json`, `NutritionCatalogRepository.java` |
| 외식·상품 연결을 이름으로 하면 동명이인 충돌이 난다 | PriceTrace namespace의 정확한 UUID, 위치·메뉴·상품 snapshot, `consumedFraction`을 저장한다 | `DiningOutIdentity.java`, `DiningOutConsumption.java`, `ProductNutritionLink.java` |
| 네트워크 장애가 기록을 막을 수 있다 | SQLite 기록을 먼저 완료하고, 수동 동기화에서 RPC 후 제한된 legacy REST fallback을 사용한다 | `FitnessDatabaseHelper.java`, `SupabaseSyncManager.java` |

## 핵심 기능과 결과

| 영역 | 현재 구현 | 확인 경계 |
| --- | --- | --- |
| 앱 셸 | `메인`·`피트니스`·`기록`·`발전`·`설정` 5개 하단 탭과 내부 화면 전환 | 코드·debug 빌드 |
| 근력 기록 | 루틴 CRUD, 루틴 없는 시작, 과거 수동 입력, 종목 추가·교체, 세트·RIR·휴식·완료 요약 | 단위 테스트·debug 빌드 |
| 부하 상태 | `BODYWEIGHT`, `EXTERNAL_LOAD`, `ADDED_WEIGHT`, `ASSISTED`, `BAND_ASSISTED`, `BAND_RESISTED`별 입력 보존 | 계약·UI 코드 |
| 운동 카탈로그 | legacy 340/340 매핑, family 103개, 승인 preset 27개, 미매핑·모호 항목 0 | JSON·카탈로그 테스트 |
| 운동 이미지 | exact visual variant → family default → placeholder 순서; picker는 exact-only | lookup 코드·자산 구조 |
| 유산소 | 걷기·달리기·자전거, foreground location, 거리 필터, 로컬 경로점·요약 | 코드·APK; 실기기 미실행 |
| 기록·요약 | 월간 달력, 체중 추세, 근력 volume/set/time, 유산소 거리·시간, 근육 분포 | 코드·APK; 실기기 미실행 |
| 발전 | 목표·신체 프로필·최근 기록·훈련/영양/회복 근거와 review insight 최대 3개 | 단위 테스트·APK |
| 식단 | ingredient·recipe·external menu, 공식·개인·공개 식품, 식사 구성과 조리상태 | 카탈로그·SQLite 코드 |
| 공식 식품 | 총 68행: raw 64, cooked 4; 모든 항목은 100g 가식부 기준 | asset·시드 코드 |
| 외식·상품 | 구성 그룹, 제공 방식, 섭취 인원·비율, PriceTrace product nutrition link | 계약·SQLite 코드 |
| 보충제 | 계획·시간대·복용/건너뜀·수정·효과/부작용 check-in·근거 카드 | catalog·UI 코드 |
| 보호·복구 | Keystore token 분리, owner/public scope, 명시적 JSON backup/restore, CSV 요약 export | 보안·backup 코드 |
| Personal OS 공유 | 완료 운동의 요약만 공유; 상세 식단·GPS·로컬 전용 확장 필드는 공유 테이블에 넣지 않음 | sync table allowlist·계약 |

### 공식 식품 데이터의 기준

공식 원본은 [공공데이터포털 전국통합식품영양성분정보(원재료성식품) 표준데이터](https://www.data.go.kr/data/15100065/standard.do)와 [식품안전나라 식품영양성분 데이터베이스](https://various.foodsafetykorea.go.kr/nutrient/)를 기준으로 선별한다. 현재 asset의 source version은 `2025-12-23`이다.

- 모든 행은 가식부 100g 기준이다.
- `raw`/생것과 `cooked`/조리 상태는 별도 행이다. 조리 행을 생것 수율 계산으로 만들어내지 않는다.
- 공식 공란은 `null`로 보존한다. 모르는 영양값을 0으로 채우지 않는다.
- 생선의 `회`는 검색·표시용 별칭이며, raw 행이 회 섭취 안전성이나 횟감 등급을 보증하지 않는다.

## 핵심 사용 흐름

```text
앱 실행
  → SQLite에서 오늘 기록·루틴·목표·식품 카탈로그 로드
  → 피트니스에서 루틴/자유 세션·근력 세트 또는 유산소 기록
  → 기록에서 날짜별 운동·식사·체중과 요약 확인
  → 발전에서 목표·최근 근거·다음 행동 확인
  → 식단·보충제·신체 프로필을 로컬에 입력
  → 필요할 때 설정에서 수동 sync 또는 명시적 local backup/restore 실행
  → 운동 완료 요약만 Fitness Record Contract v1 공유 경계로 전송
```

## 검증 현황

아래 결과는 코드 변경 전 동일한 `main` SHA에서 2026-09-05에 실행한 로컬 검증이다.

| 검증 항목 | 상태 | 근거와 경계 |
| --- | --- | --- |
| Java 단위 테스트 | 통과 | `./gradlew.bat testDebugUnitTest --no-daemon` |
| Android debug APK | 통과 | `./gradlew.bat assembleDebug --no-daemon` |
| Android 테스트 APK | 통과 | `./gradlew.bat assembleDebugAndroidTest --no-daemon`; 패키징·컴파일만 확인 |
| lint | 실패 | `./gradlew.bat lintDebug --no-daemon`; API 27 요구 resource 오류 4개와 warning 54개 |
| ADB 대상 확인 | 관찰 | `adb devices -l`에서 4개 ADB endpoint/emulator 확인; 계측 테스트는 실행하지 않음 |
| 실제 계측 테스트 | 미실행 | `connectedDebugAndroidTest`를 수행하지 않음 |
| release 서명 빌드 | 미실행 | `FITNESS_RELEASE_*` 설정과 외부 keystore 필요 |
| Supabase Auth/RLS/두 계정 격리 | 미검증 | 실제 원격 프로젝트·계정·read-back 필요 |
| PriceTrace·Personal OS 교차 앱 동기화 | 미검증 | 두 앱 로그인과 원격 결과 확인 필요 |

로컬 build 성공은 실기기 동작, 원격 RLS, release 서명, 배포 완료를 의미하지 않는다.

## 현재 한계와 다음 단계

- lint의 API 27 resource 오류 4개와 경고 54개를 별도 수정하고 재검증한다.
- ADB 대상에서 설치·업데이트·발전 입력·식품 조리상태·유산소 경로·backup/restore를 계측 테스트와 함께 확인한다.
- QA/release keystore로 서명한 variant를 만들고, 같은 인증서의 `adb install -r` 업데이트와 데이터 보존을 확인한다.
- 공유 Supabase·Nutrition Supabase의 Auth/RLS, 두 계정 격리, 동기화 RPC와 legacy fallback을 실제 원격 read-back으로 확인한다.
- Nutrition·외식·보충제 콘텐츠를 늘리더라도 출처, exact identity, 결측값, snapshot 경계를 유지한다.
- 발전·paper advice는 기록 기반 점검과 근거 상태만 제공한다. 의료 진단·자동 영양 처방·복용 안전 승인을 제품 결과로 표현하지 않는다.
- 문서가 `main`에 반영된 뒤에만 configured GitHub Actions가 Notion mirror를 갱신한다. 현재 브랜치의 문서 검증과 Notion dry-run은 발행 증거가 아니다.

## 관련 문서

- [README](../README.md)
- [프로젝트 상세](./Project_Detail.md)
- [Supabase 경계와 migration](../supabase/README.md)
- [Personal OS Fitness Record Contract v1](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [통합 릴리스 준비 기준](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
- [공식 원재료성 식품 영양성분 표준데이터](https://www.data.go.kr/data/15100065/standard.do)
