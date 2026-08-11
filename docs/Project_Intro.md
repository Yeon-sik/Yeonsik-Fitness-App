# Fitness App | 운동 기록·식단·발전 점검을 한곳에서 관리하는 Android 앱

> Fitness App은 운동의 상세 기록을 로컬에 소유하고, 식단과 최근 기록을 함께 조회해 다음 행동을 정리하는 Java 17 기반 네이티브 Android 앱이다. 완료된 운동 요약만 Personal OS와 공유한다.

| 항목 | 내용 |
| --- | --- |
| 프로젝트 형태 | 개인 Android 프로젝트 |
| 담당 범위 | Android View UI, SQLite 데이터 계층, 운동 기록 계약, 식단 카탈로그, Supabase Auth·동기화 |
| 현재 기준 | `main` `25081edf8aa4b656ab084ec9a6b4c120f0e542fb` |
| 현재 상태 | 발전 탭 MVP·신체 기본정보·공식 식품 검색 구현, debug 검증 통과, 실기기·운영 게이트 미검증 |
| 주요 기술 | Java 17, Android View, SQLiteOpenHelper, Gradle 9, Supabase REST/Auth |
| 문서 기준일 | 2026-08-11 |
| Repository | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 상세 문서 | [Project_Detail.md](./Project_Detail.md) |

## 1. 30초 요약

- **문제**: 운동 중에는 세트·중량·횟수를 빠르게 남겨야 하고, 운동 이후에는 최근 기록을 바탕으로 무엇을 보완할지 확인해야 한다. 식단 입력은 검증 가능한 영양 데이터와 조리상태를 함께 구분해야 한다.
- **해결**: Fitness App이 운동 상세와 로컬 식단 기록을 SQLite에 저장하고, 하단 `발전` 탭에서 최근 14일 운동·식사·체중·체크인 기록과 주간 목표를 한 화면으로 점검한다.
- **현재 결과**: 발전 목표와 집중 부위, 키·최근 체중 입력, 우선 행동 최대 3개, 훈련 부위 근거, 영양·회복 기록 커버리지, 단일 식품 검색, 공식 영양 카탈로그가 구현됐다.
- **공유 경계**: 운동 종목·세트 같은 상세 데이터는 Fitness App이 소유하고, 완료된 운동의 요약만 Fitness Record Contract v1 범위로 Personal OS에 공유한다.
- **검증 경계**: `main` 기준 로컬 단위 테스트·debug APK·Android 테스트 APK·lint는 통과했다. ADB 연결 기기가 없어 계측 테스트와 실제 설치·로그인·동기화는 아직 실행하지 않았다.

## 2. 문제와 해결

| 사용자 문제 | 해결 방식 | 현재 근거 |
| --- | --- | --- |
| 운동 중 상세 입력이 복잡하다 | 루틴·세션·종목·세트를 programmatic View로 기록하고 record type별 필수값을 검증한다 | `app/src/main/java/com/yeonsik/fitnessapp/ui/WorkoutSessionScreen.java`, `WorkoutExerciseDetailScreen.java`, `FitnessRecordContract.java` |
| 최근 기록에서 다음 행동을 찾기 어렵다 | `발전` 탭이 최근 14일과 이번 주 진행률을 계산해 근거·다음 행동·판단 한계를 함께 보여 준다 | `DevelopmentScreen.java`, `DevelopmentRepository.java`, `DevelopmentInsightRules.java` |
| 키와 체중을 별도 화면에서 관리해야 한다 | 발전 탭에서 키와 오늘 체중을 입력한다. 키는 발전 프로필에, 체중은 기존 날짜별 체중 기록 흐름에 저장한다 | `MainActivity.java`, `BodyProfile.java`, `FitnessRepository` |
| 식단 입력값의 출처와 조리상태가 불명확하다 | 공식 원재료성 식품 데이터에서 선별한 54개 행을 로컬 SQLite 카탈로그로 시드하고 100g·생것/구이 상태를 행별로 표시한다 | `VerifiedFoodCatalogSeed.java`, `verified_food_catalog_v3.json` |
| 회와 구이 식품을 같은 이름으로 선택하면 기록이 모호해진다 | 표시명에 생것 기준·구이 상태를 포함하고, 내부 조리상태는 기존 `raw`·`grilled` 계약을 사용한다 | `NutritionCatalogRepository.java`, `MealManagementScreen.java` |
| 네트워크 실패가 기록을 막을 수 있다 | 로컬 SQLite를 우선 사용하고 설정에서 원격 동기화를 수동 실행한다 | `FitnessDatabaseHelper.java`, `SupabaseSyncManager.java` |

## 3. 핵심 기능과 결과

| 영역 | 구현 결과 | 검증 수준 |
| --- | --- | --- |
| 운동 루틴·세션 | 루틴 편집, 운동 시작·완료, 종목·세트·RPE·휴식 기록 | 저장소·계약 단위 테스트, debug 빌드 |
| 기록 화면 | 날짜별 기록, 주간 지표, 종목별 기록과 홀로그램 테두리 애니메이션 | 코드·Android 테스트 소스, APK 빌드; 기기 실행 미검증 |
| 발전 탭 MVP | 목표(근비대·근력·감량·지구력·유지), 주간 횟수 1~7회, 집중 부위, 우선 행동, 훈련·영양·회복 근거 | 모델·repository 단위 테스트, Android 테스트 APK 빌드 |
| 신체 기본정보 | 키 50~300cm, 오늘 체중 kg 입력. 기존 체중 기록과 함께 최근값을 표시 | 입력 검증 코드·Android 테스트 소스, 기기 실행 미검증 |
| 단일 식품 등록 | 이름 검색, 공식 DB 배지, 100g 영양값, 조리상태 선택 후 현재 끼니에 추가 | 카탈로그·검색 테스트 소스, Android 테스트 APK 빌드 |
| 공식 식품 카탈로그 | 닭·소·돼지·곡물·채소와 생선 6행(연어·참치·고등어 생것/구이), 총 54행 | 원본 재생성 명령과 Android 시드 테스트 소스 |
| 데이터 보호 | Auth 토큰 Keystore 저장, 로컬 소유권 정규화, 백업·복원 시 발전 테이블과 카탈로그 재조정 | 코드·migration/backup 테스트 소스 |
| Personal OS 공유 | 완료 운동의 category code·contract version·scope 요약만 발행 | 저장소 계약 테스트, 운영 연결 미검증 |

### 공식 식품 데이터의 기준

공식 원본은 [공공데이터포털 전국통합식품영양성분정보(원재료성식품) 표준데이터](https://www.data.go.kr/data/15100065/standard.do)와 [K-FIND 식품영양성분 데이터베이스](https://various.foodsafetykorea.go.kr/nutrient/)다.

- 모든 행은 가식부 100g 기준으로 저장한다.
- 생것 행은 `raw`/`생것`, 구이 행은 `grilled`/`구이`로 저장한다. 구이 수치는 생것에서 수율을 계산한 값이 아니라 공식 구이 행의 독립 측정값이다.
- 공식 공란은 `null`로 유지한다. 생선의 당류 또는 부산 생고등어의 나트륨·포화지방을 임의로 0으로 보정하지 않는다.
- `회`는 공식 조리상태가 아니라 검색·표시용 별칭이다. 생선 raw 행이 회 섭취 안전성이나 횟감 등급을 보증하는 것은 아니다.

## 4. 핵심 사용 흐름

```text
앱 실행
  → SQLite에서 오늘 기록·루틴·목표·식품 카탈로그 로드
  → 운동 탭에서 세션·종목·세트 기록
  → 식단 탭에서 단일 식품 검색 또는 직접 등록
  → 발전 탭에서 최근 14일 근거·주간 목표·신체 기본정보 확인
  → 필요한 카드의 다음 행동으로 운동·식사·체크인·기록 화면 이동
  → 운동 완료 시 요약만 Fitness Record Contract v1로 동기화
```

## 5. 검증 현황

검증 기준은 `main` 커밋 `25081edf8aa4b656ab084ec9a6b4c120f0e542fb`이며 2026-08-11에 실행했다.

| 검증 항목 | 상태 | 근거와 경계 |
| --- | --- | --- |
| 공식 카탈로그 생성 | 통과 | `scripts/generate-verified-food-catalog.ps1` 실행 결과 54행(생것 51·구이 3) |
| Java 단위 테스트 | 통과 | `./gradlew.bat testDebugUnitTest` 포함 전체 명령 |
| Android debug APK | 통과 | `./gradlew.bat assembleDebug` |
| Android 테스트 APK·Java 계측 컴파일 | 통과 | `./gradlew.bat assembleDebugAndroidTest`, `compileDebugAndroidTestJavaWithJavac` |
| lint | 통과 | `./gradlew.bat lintDebug` |
| 실제 계측 테스트 | 미실행 | `adb devices -l`에 연결 기기 0대 |
| release 서명 빌드 | 미실행 | `FITNESS_RELEASE_*` 4개 환경변수와 서명 키 필요 |
| 운영 Supabase·RLS·두 계정 격리 | 미검증 | 실제 프로젝트와 Auth 계정 2개 필요 |
| Personal OS 교차 앱 동기화 | 미검증 | 두 앱 로그인·완료 세션·원격 read-back 필요 |
| Notion 발행 | 문서 main 병합 후 Actions 확인 필요 | 저장소 workflow는 `on-main-push` 모드로 구성됨 |

빌드에는 Gradle deprecated feature 경고가 남아 있어 Gradle 10 호환성은 별도 확인이 필요하다. 로컬 빌드 성공은 배포나 실기기 동작의 증거가 아니다.

## 6. 현재 한계와 다음 단계

- **실기기 게이트**: 연결된 Android 기기에서 앱 설치, 발전 탭 입력, 생것·구이 식품 선택, 홀로그램 애니메이션, 백업 복원을 한 번에 검증해야 한다.
- **운영 데이터 게이트**: 공유 Personal OS 프로젝트와 FitnessApp 전용 Nutrition 프로젝트의 Auth·RLS·소유권 격리를 두 계정으로 확인해야 한다.
- **식품 범위**: 현재는 선별한 공식 54행이다. 추가 품목은 동일한 원본 코드·공식명·조리상태·100g 기준·결측 정책을 갖춰야 한다.
- **발전 범위**: MVP는 기록 기반 점검이다. 의료 진단, 자동 처방, 영양소 목표 계산, 센서 기반 생체 판단은 구현하지 않았다.
- **운영 문서**: 문서 변경을 `main`에 병합하면 GitHub Actions가 검증 후 Notion 전용 mirror 페이지를 갱신하도록 구성돼 있다. Actions와 두 페이지 read-back을 확인해야 문서 발행을 완료로 판단한다.

## 7. 관련 문서

- [README](../README.md)
- [프로젝트 상세](./Project_Detail.md)
- [Personal OS Fitness Record Contract v1](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [통합 릴리스 준비 기준](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
- [공식 원재료성 식품 영양성분 표준데이터](https://www.data.go.kr/data/15100065/standard.do)
