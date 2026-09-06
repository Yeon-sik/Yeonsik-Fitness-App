# FitnessApp 정돈 리팩터링 규칙

## 0. 문서 작성 기준

- 기준 커밋: 원격 최신 `main`과 일치하는 `cedaf614`
- 적용 대상: FitnessApp 구조 리팩터링
- 기본 방향: **단일 Git 저장소·단일 `:app` 모듈 안에서 책임을 먼저 분리하고, Kotlin → Room → Compose 순서로 전환**
- 멀티모듈: 현재 목표에 포함하지 않음
- 상태 구분:
  - **CURRENT**: 실제 소스
  - **PROPOSED**: 호환성을 유지하는 이행 구조
  - **TARGET**: 이번 판단으로 정한 도착점
- 검증 범위: 설계 분석
- 미수행 검증: 빌드·실기기·운영 검증

---

## 1. 최상위 아키텍처 결정

| 항목 | CURRENT | PROPOSED | TARGET |
|---|---|---|---|
| Git Repository | Android와 Fitness 소유 backend migration이 한 저장소에 존재 | 유지 | **분리하지 않음**. 서비스 소유권과 배포 대상은 별도로 관리 |
| 데이터 Repository | 일부 기능은 분리됐지만 `FitnessRepository`에 운동·식사·체중·전송·집계 집중 | 기존 클래스를 호환 façade로 남기고 책임별 구현 추출 | **도메인별 Repository**, 범용 `FitnessRepository` 제거 |
| 패키지 | `data`, `ui` 중심 구조와 `cardio`, `routine` 등 기능 구조 혼재 | 변경하는 기능부터 수직 분리 | **기능별 패키지 + 최소 공통 기반 + 외부 연동 어댑터** |
| Kotlin | Java 17 | 책임 분리 후 새 코드부터 도입 | Kotlin 중심, 안정된 Java와 생성 코드는 공존 가능 |
| 저장소 | `SQLiteOpenHelper`, `fitness_mvp.db`, schema v50 | 기존 SQL을 격리한 뒤 Room이 같은 파일을 관리 | **단일 RoomDatabase**, 기능별 DAO와 명시적 migration |
| UI | `Activity`·View·`ScreenHost`, 화면에서 Repository 직접 호출 | View 유지, ViewModel·UiState·사용자 액션 분리 | **Compose 중심 UI**, 특수 View는 상호운용 |
| 멀티모듈 | `:app` 하나 | 유지 | **단일 모듈 유지**. 실제 재사용·빌드 병목이 확인될 때만 재판단 |

### 1.1 현재 구조상 핵심 문제

- `FitnessRepository.java`: 8,317줄
- `MainActivity.java`: 3,167줄
- 문제는 분량 자체가 아니라 **저장·검증·집계·외부 호환·화면 진행 책임이 함께 변경되는 구조**
- 파일 분할과 언어 변환만으로는 해결하지 않음

관련 경로:

- `C:/github/PersonalOS/Yeonsik-Fitness-App/app/src/main/java/com/yeonsik/fitnessapp/data/FitnessRepository.java`
- `C:/github/PersonalOS/Yeonsik-Fitness-App/app/src/main/java/com/yeonsik/fitnessapp/MainActivity.java`

---

## 2. 데이터 소유권 고정 규칙

| 경계 | 소유 데이터·권한 | 구현 제약 |
|---|---|---|
| Fitness 운동 | 세션·종목·세트·루틴·GPS 기록·운동 식별 체계 | 세션 수정이 루틴 원본을 변경하지 않음 |
| Fitness Meal | 실제 섭취 시각·양·메뉴·항목별 영양 스냅샷 | Nutrition 변경·삭제로 과거 섭취값을 재계산하지 않음 |
| Fitness Nutrition | 음식·레시피·영양 identity·canonical provenance·공개 상태 | 음식 등록 자체가 Meal 생성을 의미하지 않음 |
| Fitness 신체·보충제 | 체중·체성분·목표·체크인·복용 기록 | 상세 데이터의 소유권을 Personal OS로 옮기지 않음 |
| Personal OS | Fitness가 발행한 완료 요약의 조회 모델 | 신규 공유는 Summary Projection v2의 허용 필드로 제한 |
| PriceTrace | 상품·식당·지점·메뉴 identity와 가격 관련 데이터 | Fitness는 확정된 ID와 계약된 연결·발행 API 사용 |
| OCR·CashOS | OCR 검증 과정, 구매·영수증 등 각 서비스 원본 | 구매를 섭취로 간주하지 않고 검증된 projection만 수용 |

### 2.1 물리 DB와 Bounded Context

- Meal과 Nutrition이 같은 Supabase 프로젝트에 있다는 이유로 같은 Repository에 넣지 않음
- **물리 DB 배치와 Bounded Context는 별개**
- 확정된 Meal ingest 계약도 이를 구분

관련 문서:

- `C:/github/PersonalOS/Yeonsik-Fitness-App/docs/fitness-meal-verified-import.v1.md`

---

## 3. TARGET 패키지 구조

기존 namespace와 applicationId는 유지한다.

```text
com.yeonsik.fitnessapp
├─ app/
│  ├─ AppContainer                 # 수동 의존성 조립
│  └─ navigation/
├─ core/
│  ├─ database/
│  │  ├─ FitnessDatabase
│  │  ├─ migration/
│  │  └─ {workout,meal,nutrition,...}/  # Entity·DAO
│  ├─ account/                     # 소유자·프로젝트별 세션 경계
│  ├─ network/                     # 전송 기반만
│  ├─ time/
│  └─ ui/                          # 공통 테마·입력 컴포넌트
├─ feature/
│  ├─ workout/
│  ├─ cardio/
│  ├─ exercise/
│  ├─ routine/
│  ├─ meal/
│  ├─ nutrition/
│  ├─ body/
│  ├─ supplement/
│  ├─ development/
│  ├─ home/
│  ├─ records/
│  └─ settings/
├─ integration/
│  ├─ personalos/                  # Summary v2, legacy v1 분리
│  ├─ nutrition/                   # 카탈로그 동기화·공개·검증 import
│  ├─ pricetrace/
│  ├─ cashos/
│  └─ transfer/                    # 운동 전송 v1/v2
└─ backup/                         # 로컬 백업 형식·복원
```

### 3.1 기능 내부 계층 규칙

각 기능에는 필요한 경우에만 다음을 둔다.

- `model`
- `api`
- `application`
- `data`
- `ui`

다음은 만들지 않는다.

- 빈 계층
- CRUD마다 하나씩 있는 UseCase

---

## 4. 의존성 규칙

```text
화면 → ViewModel → Repository API 또는 복합 작업 Service
                               ↓
                       Repository 구현 → DAO → SQLite

외부 연동 → 계약 DTO·Mapper → 기능 API
홈·기록·발전 조회 → 각 기능의 읽기 API → 화면용 집계 모델
```

### 4.1 금지 및 제한 사항

- Repository API에는 `Cursor`, `ContentValues`, Room Entity, Supabase JSON을 노출하지 않음
- 기능 간 접근은 공개 API로 제한
- 다른 기능의 DAO에 직접 쓰지 않음
- `AppContainer`가 구현체를 조립
- 현재 규모에서 Hilt와 범용 이벤트 버스는 도입하지 않음
- 영양 카탈로그 Entity, Meal snapshot, 외부 DTO는 분리
- 모든 화면 모델까지 기계적으로 복제하지 않음

---

## 5. Repository 및 Service 책임

| Repository·Service | 책임 |
|---|---|
| `WorkoutRepository` | 세션·종목·세트 저장, 기록 수정·완료·삭제 |
| `CardioRepository` | GPS 세션 상태·좌표·측정값. 기존 분리 유지 |
| `MealRepository` | 섭취 기록과 메뉴·구성요소·영양 스냅샷의 원자적 저장 |
| `NutritionCatalogRepository` | 음식·레시피·영양 카탈로그의 로컬 조회·저장 |
| `BodyMetricsRepository` | 체중·신체 프로필·체성분 |
| 기존 Routine·Supplement·Exercise Repository | 현재 책임 유지, 저장 구현 의존성 정리 |
| `DevelopmentRepository` | 목표·체크인 등 자체 저장 데이터. 보고서 조립은 별도 조회 Service |
| `RecordMeal`, `CompleteWorkout`, `CompleteCardio` | 여러 저장 작업과 검증을 묶는 실제 업무 단위 |
| 동기화·공개·전송·백업 Service | 네트워크와 파일 작업 조정. 업무 Repository에 포함하지 않음 |

### 5.1 교차 Repository 규칙

- `CardioRepository`와 `WorkoutRepository`가 서로 호출하는 구조는 만들지 않음
- `CompleteCardio`가 양쪽을 조정
- 로컬 완료에 필요한 변경은 같은 DB 트랜잭션으로 묶음

---

# 6. 단계별 마이그레이션

| 단계 | Codex 구현 범위 | 다음 단계 진입 조건 |
|---|---|---|
| 0. 기준 고정 | 현재 schema·외부 Contract·백업·대표 동작을 fixture로 고정 | 변경 전 기준 테스트와 데이터 비교 기준 확보 |
| 1. Java에서 책임 분리 | Repository 추출, 외부 연동 분리, 호환 façade 도입 | 기존 화면·SQL·payload 동작 유지 |
| 2. Kotlin·화면 상태 도입 | 새 API·Service·ViewModel부터 Kotlin 적용, View는 유지 | 화면 렌더링에서 DB 쓰기 제거, 수명주기·입력 보존 확인 |
| 3. Room 준비 | 기존 SQL·migration 접근 격리, 전체 schema 매핑 | 기존 DB와 Room 예상 schema 차이 해소 |
| 4. Room 전환 | 동일 DB 파일의 관리 주체 교체, DAO 순차 전환 | 구버전 업그레이드·백업 복원·동기화 보존 검증 |
| 5. Compose 전환 | 읽기 화면부터 기록 화면까지 순차 교체 | 각 화면의 기능·상태 복구·입력 회귀 검증 |
| 6. 이행 코드 제거 | façade·구형 SQL 어댑터·사용하지 않는 View 제거 | 기존 Java 잔존 여부와 무관하게 TARGET 책임 구조 완성 |

---

## 7. 단계 0 — 데이터와 계약 고정

### 7.1 작업 시작

- 원격 `main`을 다시 확인
- `feat/` 브랜치 생성

### 7.2 DB 기준 기록

- `fitness_mvp.db` 신규 설치 schema 기록
- 과거 버전에서 업그레이드한 schema 기록

### 7.3 대표 데이터 fixture

운동:

- 운동 유형 6종
- LoadState
- Family/Preset/variant
- kg·lb 입력
- 진행 중 세션
- 과거 기록
- 삭제 tombstone

Meal:

- 메뉴
- 구성요소
- 공동 섭취
- 미확인 영양값
- 출처 정보

계정:

- 로그아웃 상태
- 서로 다른 두 계정의 데이터

### 7.4 구조 변경과 함께 바꾸지 않는 계약

- Fitness Record Contract v1 및 `sync_fitness_data_v1`
- Fitness Summary Projection v2
- Nutrition verified import v1·canonical provenance v2
- Meal verified ingest v1·component estimate v1
- 기존 PriceTrace·CashOS 연동 계약
- `yeonsik.workout-transfer` v1/v2
- `fitness-os.local-backup` v1

---

## 8. 단계 1 — Java 상태에서 Repository와 연동 책임 분리

### 8.1 Repository 추출 순서

1. Body
2. Meal
3. Workout
4. Nutrition의 네트워크 작업
5. 보고서 집계

### 8.2 구현 규칙

- 기존 `FitnessRepository` 공개 메서드는 새 구현에 위임
- 새 Repository가 다시 `FitnessRepository`를 호출하지 않음
- SQL·검증·트랜잭션을 업무 단위별로 함께 이동
- 세트·영양소 테이블마다 Repository를 만들지 않음
- `MainActivity`의 의존성 생성은 `AppContainer`로 이동
- `ScreenHost`는 점차 내비게이션·플랫폼 기능만 제공하도록 축소
- 패키지 이동과 동작 수정은 별도 커밋으로 분리

### 8.3 Supabase 동기화 분리

현재 `SupabaseSyncManager.java`는 다음 책임을 함께 수행한다.

- 상세 운동 테이블 legacy 동기화
- Meal legacy 동기화
- 체중 legacy 동기화
- Summary v2 발행

따라서 CURRENT를 “이미 요약만 전송하는 구조”로 간주하지 않는다.

관련 경로:

- `C:/github/PersonalOS/Yeonsik-Fitness-App/app/src/main/java/com/yeonsik/fitnessapp/sync/SupabaseSyncManager.java`

다음 두 경로로 분리한다.

#### `LegacyFitnessSyncAdapter`

- 기존 v1 payload
- 충돌 처리
- cursor
- fallback 보존

#### `FitnessSummaryPublisher`

- 완료 요약 v2 발행
- 삭제 tombstone 발행

### 8.4 Sync 실패 규칙

- v2 실패가 로컬 저장을 실패시키지 않음
- 두 경로의 성공 상태를 구분
- 초기에는 현재처럼 로컬 완료 기록에서 요약을 재구성해 재시도
- 별도 이벤트 저장소나 범용 outbox는 만들지 않음

### 8.5 Legacy 제거 조건

Legacy 제거는 별도 이행 조건으로 취급한다.

다음이 끝나기 전에는 제거하지 않는다.

- 소비자 전환
- 기존 원격 데이터 접근·복구 경로 확보
- 계약 검증

새로운 상세 데이터를 legacy 공유 범위에 추가하지 않는다.

---

## 9. 단계 2 — Kotlin과 ViewModel 도입

### 9.1 Kotlin 전환 순서

1. 신규 기능 API·순수 모델·업무 Service
2. 화면별 ViewModel·UiState
3. Room Entity·DAO·Repository 구현
4. Compose 화면과 얇아진 Activity
5. 수정 필요가 있는 기존 Java 유틸리티

### 9.2 Kotlin 전환 규칙

- Java 전체 자동 변환 금지
- 기존 Java 공개 API를 Kotlin으로 바꿀 때 다음을 확인
  - nullability
  - Java 호출 방식
  - 숫자 변환
  - JSON 누락 의미
  - JSON `null` 의미
- Java 17 유지

### 9.3 화면 상태 분리 규칙

- DB·네트워크 작업은 메인 스레드 밖에서 실행
- 세트 입력 저장 순서를 보존
- 완료 동작 전에 진행 중 저장을 처리
- 미완성 입력과 검증된 저장 모델 분리
- `SavedStateHandle`에는 화면 복구용 ID·초안을 보관
- 저장된 기록은 DB에서 재조회
- 계정 전환 시 기존 작업 결과가 새 계정 화면이나 DB에 적용되지 않도록 작업 시작 당시 소유자·프로젝트 범위를 고정

### 9.4 운동 상세 화면 쓰기 규칙

현재 운동 상세 화면은 렌더링 중 빈 세트를 생성하기도 한다.

이를:

- 명시적인 화면 진입 초기화 작업으로 이동

금지:

- **Compose 재구성이 쓰기를 반복하는 구조를 그대로 이전**

---

## 10. 단계 3~4 — Room 전환

### 10.1 DB 인수 기준

현재 DB:

- 파일: `fitness_mvp.db`
- schema: v50
- 관련 경로:
  - `C:/github/PersonalOS/Yeonsik-Fitness-App/app/src/main/java/com/yeonsik/fitnessapp/data/FitnessDatabaseHelper.java`

Room 전환 시점에도 v50이면 최초 Room 버전은 **51**로 정한다.

그전에 schema가 변경됐다면:

- 당시 최신 버전의 다음 번호를 사용

### 10.2 구현 순서

#### 1. 기존 SQL 접근과 migration 격리

- Android `SQLiteDatabase` 직접 의존을 저장 구현 내부로 제한
- 다음 영역 포함:
  - 백업
  - 동기화
  - seed
  - GPS 서비스

#### 2. 전체 현행 schema를 Room에 매핑

- 일부 기능의 Entity만 선언하고 DB 전체를 인수하지 않음
- 모든 현행 테이블 조사
- 필요한 index 조사
- 필요한 제약 조사

#### 3. 과거 업그레이드 경로 보존

- 기존 migration SQL을 Room에서 실행 가능한 migration 코드로 이식
- 지원하는 구버전에서 기준 schema를 거쳐 Room 버전으로 직접 업그레이드 가능해야 함
- 중간 APK 설치를 요구하지 않음

#### 4. Room이 DB 열기·버전 변경을 단독 관리

- 앱 범위 인스턴스 하나를 Activity와 서비스가 공유
- 기존 `SQLiteOpenHelper`와 Room이 같은 파일을 동시에 관리하지 않음

#### 5. SQL을 DAO로 순차 교체

초기에는 Room이 관리하는 연결의 `SupportSQLiteDatabase`를 통한 호환 SQL을 허용한다.

교체 순서:

1. Body·Routine·Supplement
2. Workout·Cardio
3. Meal·Nutrition
4. Backup·Sync

### 10.3 Room 도입 기준

- 안정판 `androidx.room` 2.8.4
- Kotlin용 KSP
- 구현 시 호환되는 빌드 도구 버전 고정
- 동일 DB 인수 후 DAO를 점진적으로 교체

참조 기준:

- SQLite → Room 이행 지침
- Room 안정판 문서

### 10.4 필수 보존 조건

- DB 이름 유지
- 레코드 ID 유지
- 소유자 유지
- 외부 참조 ID 유지
- 시각·날짜 의미 유지
- 삭제 상태 유지
- sync cursor 유지
- 스냅샷의 `NULL`을 0으로 채우지 않음
- 스냅샷을 최신 카탈로그로 덮어쓰지 않음
- 기존 legacy 요약의 0을 임의로 `NULL`로 정정하지 않음
- kg 저장값 유지
- 원래 입력값 유지
- 입력 단위 유지
- Room Entity와 실제 schema의 PK nullability 차이 명시적 해결
- 기본값 차이 명시적 해결
- index 차이 명시적 해결
- 테이블 재구성이 필요하면 트랜잭션 안에서 행 복사
- 테이블 재구성 시 ID·행 수·값 검증
- `fallbackToDestructiveMigration` 사용 금지
- `allowMainThreadQueries` 사용 금지
- Meal 스냅샷에 Nutrition 삭제 cascade를 새로 추가하지 않음

### 10.5 백업 호환성

- 백업 `formatVersion`과 DB schema version은 별개로 유지
- 구형 백업은 새 앱에서 읽을 수 있어야 함
- 새 schema 백업을 구형 앱이 읽는 역호환성은 별도 계약 없이 보장하지 않음

---

## 11. 단계 5 — Compose 전환

### 11.1 전환 선행 조건

- Room 안정화 후 Compose 도입

### 11.2 화면 전환 순서

1. 공통 테마·표시 컴포넌트, 홈·완료 요약
2. 기록 조회·발전 보고서
3. 설정·신체·보충제·루틴 편집
4. Nutrition 검색·편집과 Meal 입력
5. 운동 중 세트 입력·휴식 타이머·GPS 진행 화면

### 11.3 Compose 전환 규칙

- 전환 화면에는 이미 준비된 ViewModel과 UiState 사용
- Compose 도입과 기록 정책 변경을 같은 작업에서 하지 않음
- 현재 플랫폼 `Activity`는 먼저 AndroidX `ComponentActivity` 기반으로 변경
- 수명주기·상태 복구 검증
- 이후 `ComposeView`로 화면을 교체
- 주요 화면 전환 후 앱 전체 내비게이션을 Compose로 이동
- View와 Compose는 점진적으로 공존

### 11.4 유지 대상

- GPS 수집은 계속 `CardioTrackingService`가 담당
- 지도 렌더러는 필요하면 `AndroidView` 유지
- 운동 해부학 렌더러는 필요하면 `AndroidView` 유지
- Compose 전환 때문에 이미지 소스나 생성 카탈로그를 다시 만들지 않음

---

## 12. 멀티모듈 규칙

이번 TARGET은 `:app` 하나로 완료한다.

멀티모듈 추출 조건:

- 독립 소비자가 실제로 생김
- 측정된 빌드 병목이 생김

첫 추출 후보:

- Android 의존성이 없는 운동 계산·검증 영역

선제적으로 만들지 않는 모듈:

- `:core`
- `:domain`
- `:data`
- 기능별 모듈

---

# 13. Codex 구현 운영 규칙

## 13.1 PR 원칙

- 각 단계는 별도 PR로 구현
- 큰 단계는 업무 단위별 PR로 추가 분할
- 앞 단계의 데이터·Contract 보존 조건을 통과한 뒤 다음 단계로 진행

## 13.2 첫 구현 PR

첫 구현 PR은 다음으로 제한한다.

- `BodyMetricsRepository` 추출
- 기존 façade 위임

포함하지 않는다.

- Kotlin
- Room
- Compose
- DB schema 변경

## 13.3 모든 변경의 확인 경로

모든 변경에서 다음 경로를 확인한다.

```text
입력 → 업무 검증 → Repository → SQLite
```

다음을 자동으로 동일시하지 않는다.

- 외부 DTO
- DB Entity
- 도메인 모델

## 13.4 프로젝트 간 경계

- Nutrition 프로젝트와 Personal OS 프로젝트의 인증 대상을 합치지 않음
- Nutrition 프로젝트와 Personal OS 프로젝트의 migration 대상을 합치지 않음
- 프로젝트별 사용자 ID가 동일하다고 가정하지 않음
- OCR 원격 Meal ingest를 Android 카탈로그 동기화에 추가하지 않음
- Android의 원격 Meal 수신이 필요해지면 별도 Meal 연동 작업으로 구현

## 13.5 리팩터링 중 오류 발견 처리

- 리팩터링 중 발견한 동작 오류는 보존해야 할 기존 동작과 구분
- 별도 수정으로 처리
- 별도 회귀 테스트로 처리
- 계약 변경이 필요하면 새 버전 생성
- 구버전 reader 유지 계획 수립

---

# 14. PR 검증 규칙

## 14.1 모든 PR의 최소 확인 명령

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

## 14.2 DB·Repository 변경 PR

연결된 테스트 기기에서 관련 instrumentation test를 추가 실행한다.

## 14.3 Room 전환 PR 필수 검증

- 신규 설치
- 기존 v50 DB 인수
- 기존 v8 fixture 업그레이드
- 지원하는 배포 DB 버전의 업그레이드
- Meal 스냅샷 보존
- 운동 identity 보존
- LoadState 보존
- 단위 보존
- 계정 격리
- 백업 복원
- 전송 v1/v2 중복 처리
- legacy 동기화의 로컬 보존
- Summary v2 tombstone 처리
- 저장 실패 후 기록 복구
- 프로세스 재시작 후 기록 복구

## 14.4 Compose PR 필수 검증

- 입력 초안
- 키보드
- 포커스
- 뒤로가기
- 화면 재생성
- 빠른 연속 입력
- 운동 완료

## 14.5 결과 기록 항목

각 결과에는 다음 상태를 각각 기록한다.

- **단위 테스트**
- **Android 빌드**
- **instrumentation**
- **실기기**
- **운영 RPC·RLS**
