# Fitness App | Project Detail

> 이 문서는 현재 `main` 커밋 `d14f49f2dcbbc0f16f51f651fefbdc2311c4bdeb`의 Android 구조, 로컬 우선 데이터 흐름, 발전 탭 MVP, 공식 식품 카탈로그, 검증·운영 경계를 설명한다.

| 항목 | 내용 |
| --- | --- |
| 문서 상태 | Active — repository verified 기준 |
| 적용 범위 | `main` `d14f49f2dcbbc0f16f51f651fefbdc2311c4bdeb` |
| 최종 갱신 | 2026-08-11 |
| 진실 원천 | Java 코드, SQLite 스키마·migration, asset seed, Android/unit 테스트, Gradle·GitHub Actions 결과 |
| 운영 경계 | 로컬 빌드·APK 생성은 확인했지만 실기기·운영 Supabase·Notion Actions read-back은 별도 확인 대상 |

## 1. 문서 목적과 범위

### 포함

- Java 17 + Android View 기반 화면과 `MainActivity`/`ScreenHost` 전환 구조
- SQLiteOpenHelper 기반 운동·신체·식단·백업 데이터 모델과 v19 upgrade path
- `발전` 탭 MVP의 목표·신체정보·최근 기록 분석 규칙
- 단일 식품 등록과 공식 K-FIND 영양 카탈로그 seed/search 흐름
- Fitness Record Contract v1 요약 공유, 두 Supabase 연결, Auth·Keystore 경계
- debug 검증, Android 테스트 APK, lint, 실기기·운영 미검증 범위

### 제외

- Personal OS 전체 기능과 그 운영 배포
- 운영 Supabase RLS가 실제로 적용됐다는 주장
- 의료 진단, 영양 처방, 횟감 안전성 또는 운동 효과 보장
- Play Store 배포와 signed release 성공 주장
- `.understand-anything/` 생성 그래프 산출물

## 2. 시스템 아키텍처

```text
MainActivity
  ├─ ScreenHost / FitnessScreen navigation
  ├─ Home · Workout · Records · 발전 · Settings screens
  ├─ FitnessRepository · RoutineRepository · ExerciseMasterRepository
  ├─ DevelopmentRepository → DevelopmentScreen
  ├─ NutritionCatalogRepository → MealManagementScreen
  │    └─ VerifiedFoodCatalogSeed → verified_food_catalog_v3.json
  └─ FitnessDatabaseHelper v19
       └─ SQLite (운동·체중·신체 프로필·목표·식단·백업)

Settings / sync boundary
  ├─ SupabaseAuthManager → SecureTokenStore(Android Keystore)
  ├─ SupabaseSyncManager → Personal OS shared project
  └─ Nutrition sync/config → FitnessApp-only Nutrition project

main push
  └─ project-docs-notion.yml → validate → dry run → Notion mirror publish
```

### 화면과 책임

| 컴포넌트 | 책임 | 실패 시 영향 |
| --- | --- | --- |
| `MainActivity` | 탭·화면 전환, 발전 입력 dialog, Auth 상태 반영 | 전체 navigation과 입력 흐름 중단 |
| `ScreenHost` | 화면이 공유하는 repository·navigation·dialog 계약 | 화면 간 결합 오류 |
| `DevelopmentScreen` | 최근 기록 보고서, 목표·신체정보·우선 행동 UI | 발전 점검만 사용 불가 |
| `DevelopmentRepository` | SQLite에서 목표·체중·운동·식사·체크인 집계 | 발전 근거 계산 오류 |
| `MealManagementScreen` | 단일 식품 검색·선택·끼니 추가, 영양·출처 표시 | 식단 입력 흐름 중단 |
| `NutritionCatalogRepository` | 공식 seed 식품 필터·검색·공개/소유권 필터 | 공식 식품 검색 불가 |
| `VerifiedFoodCatalogSeed` | asset 파싱, 출처·조리상태·결측·공식 ID 검증, upsert | 카탈로그 생성·migration 실패 |
| `FitnessRepository` | 운동·체중·끼니·체크인 로컬 기록과 요약 생성 | 기록 무결성 저하 |
| `FitnessDatabaseHelper` | SQLite 생성·index·migration·v19 seed/repair | 로컬 데이터 접근 불가 |
| `SupabaseSyncManager` | Auth 사용자 소유 행 pull/push와 충돌 경계 | 원격 반영 지연 |
| `FitnessUi` | 공통 View, 홀로그램 테두리 drawable lifecycle | 공통 UI·애니메이션 영향 |

## 3. 데이터 모델과 불변식

### 운동과 공유

| 데이터 | 소유 경계 | 공유 여부 |
| --- | --- | --- |
| `workout_records` | Fitness App 상세·요약 원천 | 완료 시 요약 계약으로 공유 가능 |
| `workout_exercises`, `workout_sets` | Fitness App | Personal OS에 상세 직접 노출하지 않음 |
| `cardio_sessions`, `cardio_route_points` | Fitness App 로컬 상세 | 공유 범위 계약에 따름 |
| 루틴·루틴 종목 | Fitness App | 앱 내부 사용 |
| 완료 요약 | Fitness Record Contract v1 | `contract_version`, `category_codes`, `scope`를 가진 요약만 공유 |

### 발전과 신체정보

`body_profiles`는 사용자별 키를 50~300cm 범위의 정수로 저장한다. 체중은 기존 날짜별 `weight_records` 흐름을 이용하므로 발전 화면은 기준일 이전 가장 최근 체중을 읽는다. `development_goals`는 다음 계약을 SQLite CHECK로 제한한다.

| 필드 | 허용값 |
| --- | --- |
| `objective` | `muscle_gain`, `strength`, `fat_loss`, `endurance`, `maintenance` |
| `weekly_sessions_target` | 1~7 |
| `focus_body_part` | `chest`, `back`, `legs`, `shoulders`, `arms`, `abs` |

`DevelopmentRepository.buildReport()`는 기준일을 중심으로 최근 14일, 현재 주 월요일부터 기준일까지를 집계한다. 화면은 완료된 운동 세트, 집중 부위, 식사 기록일, 체크인 기록일, 에너지/준비도 2 이하 반복, 체중 기록일과 전체 데이터 커버리지를 사용한다. `DevelopmentInsightRules`는 최대 3개의 우선 행동을 만들며 각 항목에 근거와 판단 한계를 붙인다. 기록이 없다는 사실을 의료적 결론이나 영양 처방으로 확장하지 않는다.

### 영양 카탈로그

`nutrition_foods`와 `nutrition_food_nutrients`는 단일 식품 선택에 사용된다. 공식 asset `app/src/main/assets/verified_food_catalog_v3.json`은 다음 불변식을 가진다.

- 총 54행: 생것 51행, 구이 3행.
- 각 행의 기준량은 가식부 100g이다.
- 생것은 `prep_state=raw`, `cooking_method=raw`; 구이는 `prep_state=cooked`, `cooking_method=grilled`다.
- 공식 ID는 `kfind:<FOOD_CD>`, 소유자는 `NULL`, visibility는 `public`, source type은 `kfind_official`이다.
- 출처 링크는 K-FIND 상세 URL과 공식 데이터 생성일(`source_version`)을 보존한다.
- 공식 공란은 `NULL`로 유지한다. 현재 허용된 결측은 생선 당류 5행과 부산 생고등어의 나트륨·포화지방 2개 필드다.
- 참다랑어 생것처럼 원본에 같은 코드가 중복되는 경우 generator가 대표 공식명 행을 선택한다.
- raw 생선의 표시명에 `회`를 포함할 수 있지만 이는 검색 별칭이며 횟감·생식 안전성 인증이 아니다.

현재 생선 6행은 다음처럼 구분된다.

| 표시 흐름 | 공식 상태 | 데이터 주의 |
| --- | --- | --- |
| 연어회(홍연어·생것 기준) / 연어구이(홍연어) | 생것 / 구운것 | 동일 홍연어 계열의 공식 raw·grilled 행 |
| 참치회(참다랑어·생것 기준) / 참치구이(참다랑어) | 생것 / 구운것 | 참치는 통칭이므로 종을 표시 |
| 고등어회(생것·부산 평균) / 고등어구이(수입·일본 평균) | 생것 / 구운것 | 같은 원산지 짝이 아닌 독립 공식 평균값 |

## 4. 핵심 기술 의사결정

### 결정 1. Java View·SQLiteOpenHelper 경계 유지

현재 앱은 Java 17 단일 Android 모듈과 programmatic View로 구성되어 있다. MVP에서는 Kotlin·Compose·Room 전면 재작성 대신 기존 `ScreenHost`·repository·SQLite 경계를 유지해 기능을 작게 추가했다. 구조 전환은 화면 복잡도와 migration 비용이 현재 생산성을 지속적으로 초과할 때 재검토한다.

### 결정 2. 발전 탭은 처방기가 아니라 기록 기반 지휘판

발전 탭은 최근 기록의 누락·반복·주간 목표 진행률을 보여 주고 사용자가 운동·식사·체크인·기록 화면으로 이동하게 한다. 영양소 권장량이나 질병 판단을 생성하지 않는 이유는 현재 데이터와 검증 범위가 그 결론을 지지하지 않기 때문이다.

### 결정 3. 공식 식품은 로컬 seed + 검색 계약으로 제공

단일 식품 등록의 즉시 검색성과 오프라인 기록을 위해 curated JSON을 앱 asset으로 포함하고 DB v19 create/upgrade/backup restore에서 reconcile한다. Supabase에 공식 owner-null seed 전체를 업로드하는 구조가 아니며, private/manual 식품과 recipe 동기화 경계는 기존 Nutrition repository 계약을 따른다.

### 결정 4. 조리상태를 행별로 보존

모든 식품을 단일 `조리 전 100g`으로 환산하면 공식 구이 행의 의미가 사라진다. 따라서 raw와 grilled를 같은 종의 변환값으로 계산하지 않고, 각 공식 행의 조리상태 기준 100g을 그대로 저장한다. meal snapshot에는 표시명과 preparation state가 남으므로 표시명에도 생것 기준·구이를 포함해 과거 기록의 모호성을 줄였다.

### 결정 5. 완료 요약만 Personal OS에 공유

Fitness App이 상세 세트와 루틴을 소유하고 Personal OS에는 완료 운동의 stable category code·contract version·scope 요약만 발행한다. 두 앱이 동일한 상세 테이블을 수정하지 않게 해 소유권과 UX 책임을 분리한다.

### 결정 6. 직접 Auth·REST와 두 Supabase 연결

앱은 공통 Personal OS shared project와 FitnessApp-only Nutrition project를 분리한다. Java 앱의 작은 의존성 표면을 유지하기 위해 Auth·REST 매핑을 직접 관리하며, 토큰은 Android Keystore 기반 저장소에 둔다. 이 구조는 편리하지만 token refresh·오류·재시도와 운영 RLS를 별도로 검증해야 한다.

## 5. 외부 연동과 실패 경계

| 대상 | 목적 | 인증·비밀값 | 실패 처리 | 현재 검증 |
| --- | --- | --- | --- | --- |
| Personal OS shared Supabase | 운동·체중·식사·체크인 공유 | Auth access/refresh token | 로컬 기록 유지, 수동 동기화 결과 표시 | 코드·계약 검증, 운영 미검증 |
| FitnessApp Nutrition Supabase | private food/recipe와 nutrition 연동 | 별도 URL·anon key·Auth session | 로컬 카탈로그·기록 우선 | 코드 검증, 운영 미검증 |
| Android Keystore | 세션 token 보호 | 기기별 키 alias | 저장·복호화 실패 처리 | 코드 검증 |
| K-FIND·공공데이터 원본 | 공식 식품 asset 생성 | generator의 공개 URL | 생성 실패 시 asset 갱신 중단 | 2026-08-11 재생성 통과 |
| Notion mirror | reviewed Markdown의 generated copy | GitHub Environment secrets | validate 실패 시 publish 차단 | workflow 구성 검증, Actions read-back 필요 |

네트워크가 실패해도 운동·식단의 로컬 기록 자체는 SQLite에 남는다. 원격 read/write 성공을 로컬 build 성공으로 간주하지 않는다.

## 6. 데이터 보호와 보안

- 사용자 ID는 임의 입력이 아니라 Auth 설정과 local account policy에서 결정한다.
- access/refresh token은 Android Keystore 기반 저장소와 분리된 preferences namespace를 사용한다.
- Android backup과 cleartext traffic 차단 설정을 유지한다.
- owner-null 공식 식품 행은 공개 로컬 카탈로그로 사용하고, private/manual 행과 recipe 구성요소는 별도 소유권 필터를 따른다.
- 백업 복원은 대상 DB transaction 안에서 발전 테이블·공식 카탈로그를 재조정하고, meal snapshot의 과거 영양값을 임의 변경하지 않는다.
- release signing 값은 `FITNESS_RELEASE_STORE_FILE`, `FITNESS_RELEASE_STORE_PASSWORD`, `FITNESS_RELEASE_KEY_ALIAS`, `FITNESS_RELEASE_KEY_PASSWORD` 환경변수가 없으면 `assembleRelease`가 실패하도록 구성된다.
- 실제 Supabase RLS 적용, 두 계정 격리, 토큰 만료·재발급, signed release 설치는 별도 운영 게이트다.

## 7. 테스트와 검증 전략

검증 source boundary는 기능 병합 커밋 `25081edf8aa4b656ab084ec9a6b4c120f0e542fb`다. 이후 문서 보정만 포함한 현재 `main`은 `d14f49f2dcbbc0f16f51f651fefbdc2311c4bdeb`이며, 2026-08-11에 다음 명령을 실행했다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug --rerun-tasks
```

| 계층 | 대상 | 결과 | 경계 |
| --- | --- | --- | --- |
| Java unit test | 발전 모델·규칙·기존 계산 | 통과 | 로컬 JVM, Android framework 일부 미실행 |
| Java compile | main + Android test source | 통과 | 기기 런타임 미실행 |
| Android debug build | manifest·resource·APK | 통과 | 설치·화면 smoke 미실행 |
| Android test APK | 발전·애니메이션·식품·migration·backup 테스트 패키징 | 통과 | `adb devices -l` 연결 기기 0대 |
| lint | debug lint 및 test lint model | 통과 | deprecated Gradle warning은 별도 남음 |
| 공식 카탈로그 generator | 원본 다운로드·행 선택·null 정책 | 54행 생성 통과 | 원본 제공 시점과 API 응답 변화는 재생성 시 확인 |
| Supabase integration | Auth·RLS·동기화 | 미검증 | 운영 URL·두 계정 필요 |
| physical device E2E | 발전 입력·식품 선택·animation·backup | 미실행 | 연결 기기 필요 |

Android instrumentation을 실행하려면 먼저 다음 결과에 device가 나타나야 한다.

```powershell
adb devices -l
```

## 8. 배포·운영·복구

```text
feature branch
  → unit test + debug APK + Android test APK + lint
  → main merge/push
  → project-docs workflow validate + render dry run
  → on-main-push Notion publish job
  → Actions 결과·두 mirror page·source revision read-back
```

### DB와 백업

- 현재 `FitnessDatabaseHelper.DATABASE_VERSION`은 19이다.
- `onCreate()`는 운동·영양·발전 테이블과 curated catalog를 생성한다.
- `onUpgrade()`는 발전 테이블을 idempotent하게 보장하고 v19 이전 DB의 official catalog를 reconcile한다.
- backup export/import에는 `body_profiles`, `development_goals`가 포함된다. 이전 backup에 해당 테이블이 없으면 호환 검증에서 선택적으로 생략할 수 있다.
- restore는 transaction 안에서 catalog seed를 다시 적용한다. 공식 식품 행의 영양값과 meal snapshot을 혼동하지 않는다.
- DB 버전이 더 높은 backup은 현재 앱에서 거부한다. 운영 migration 전에는 백업과 legacy owner backfill을 확인해야 한다.

### Release와 Notion

- debug APK는 로컬에서 생성됐지만 signed release APK는 아직 생성하지 않았다.
- 운영 Supabase migration·RLS는 앱 build와 독립된 검증 단계다.
- `project-docs.config.json`의 `publicationMode`는 `on-main-push`다. 문서 변경이 canonical `main`에 병합되면 workflow가 먼저 tracked 문서 검증과 Notion render dry run을 수행하고, 조건을 만족하면 `notion-production` environment에서 두 mirror 페이지를 replace-only 방식으로 갱신한다.
- 문서 branch나 pull request는 Notion에 쓰지 않는다. Actions 실패나 부분 발행 시 mirror를 수동 편집하지 않고 같은 canonical revision을 재실행한다.

## 9. 한계, 기술 부채, 다음 단계

| 우선순위 | 항목 | 영향 | 다음 행동 |
| --- | --- | --- | --- |
| P0 | 실기기 계측·설치 미검증 | 실제 화면·animation·입력·복원 동작 미확인 | ADB 기기 연결 후 targeted instrumentation 실행 |
| P0 | 운영 RLS·두 계정 격리 미검증 | 원격 개인 데이터 경계 미확인 | shared/Nutrition 프로젝트별 Auth 계정 2개 CRUD 테스트 |
| P0 | signed release 미검증 | 배포 가능 여부 미확인 | release 환경변수 주입 후 `assembleRelease`·설치 smoke |
| P1 | 공식 식품 범위 54행 | 사용자 검색 범위 제한 | 같은 provenance·조리·결측 계약으로 curated rows 확장 |
| P1 | REST 매핑·재시도 직접 관리 | API 변화 시 유지보수 비용 | 오류·재시도·충돌 계약을 추가 테스트로 고정 |
| P1 | Gradle deprecated feature 경고 | Gradle 10 업그레이드 위험 | `--warning-mode all`로 원인별 갱신 |
| P1 | 발전 규칙의 기록 의존성 | 기록이 적으면 판단 커버리지 낮음 | 날짜별 기록·신체정보·목표 입력을 먼저 확보하고 규칙을 작은 단위로 확장 |

현재 MVP의 다음 구현 단위는 자동 처방을 추가하는 것이 아니라, 실기기에서 발전 탭·생선 raw/grilled 선택·홀로그램 lifecycle·백업 복원을 하나의 smoke 흐름으로 검증하는 것이다.

## 10. 관련 문서와 원본

- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [Fitness Record Contract v1](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [Release Readiness](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
- [공식 원재료성 식품 영양성분 표준데이터](https://www.data.go.kr/data/15100065/standard.do)
- [K-FIND 식품영양성분 DB](https://various.foodsafetykorea.go.kr/nutrient/)
