# Fitness App Model Skeleton

## 0. Purpose

이 문서는 Personal OS 안의 Fitness App 구조를 다시 정리하기 위한 설계 문서다.

이번 작업 범위:

- 코드 수정 없음
- DB 수정 없음
- migration 작성 없음
- 컴포넌트 구현 없음
- 기존 기록 삭제 없음

목표는 하단 탭, 운동 수행 화면, 루틴 템플릿, 실제 운동 기록, 체중/식사 기록의 역할을 명확히 나누는 것이다.

## 1. Core Principles

Fitness App은 오늘 실행하는 신체 관리의 중심이다.

핵심 원칙:

- 메인 탭은 요약과 빠른 접근만 담당한다.
- 피트니스 탭은 오늘의 운동, 체중, 식사 실행을 담당한다.
- 기록 탭은 과거 기록 조회와 재확인을 담당한다.
- 설정 탭은 연결, 단위, 환경 설정을 담당한다.
- 루틴 템플릿과 실제 운동 기록은 분리한다.
- 운동 시작 화면은 하단 탭이 아니라 fullscreen route로 취급한다.
- 운동 편집, 운동 수행, 운동 기록 조회는 같은 운동 구성 UI를 `mode`만 바꿔 재사용한다.
- DB 부모는 기존 OS 기록 구조를 우선한다. 독립적인 `workout_sessions` 부모 테이블을 새로 전제하지 않는다.

## 2. Bottom Tabs

하단 탭은 다음 4개로 구성한다.

- 메인
- 피트니스
- 기록
- 설정

### 2.1 메인

역할: 이번 달 피트니스 현황 요약과 빠른 시작 허브.

메인 탭은 사용자가 앱을 켰을 때 가장 먼저 보는 대시보드다. 직접 입력이나 세부 수정이 아니라 현재 상태 판단과 빠른 이동을 담당한다.

주요 기능:

- 이번 달 운동 수행일 수
- 이번 달 총 운동 횟수
- 이번 달 총 볼륨
- 체중 변화
- 최근 수행 루틴 3개
- 저장된 루틴 빠른 시작
- 오늘 체중 기록 바로가기
- 오늘 식사 기록 바로가기
- 진행 중인 운동 세션 이어하기

메인 탭에서 하지 않는 것:

- 루틴 편집
- 운동 종목 세부 수정
- 세트 입력
- 과거 기록 상세 수정

### 2.2 피트니스

역할: 오늘의 운동, 체중, 식사 기록을 실행하는 중심 탭.

피트니스 탭은 3개 섹션으로 구성한다.

#### A. 오늘의 몸 상태

기능:

- 오늘 체중 기록 추가
- 오늘 체중 표시 및 수정
- 오늘 식사 기록 추가
- 오늘 식사 목록 표시
- 식사별 단백질, 칼로리 표시
- 식사 상세 수정

이 섹션은 피트니스 탭 상단에 둔다.

#### B. 운동 시작

기능:

- 저장된 루틴 빠른 시작
- 최근 수행 루틴 표시
- 빈 운동 시작
- 진행 중인 운동 세션 이어하기

루틴으로 시작하면 루틴 템플릿을 기반으로 실제 운동 기록을 생성한다.

빈 운동 시작은 루틴 템플릿 없이 실제 운동 기록을 생성한다.

운동 시작 버튼을 누르면 하단 탭이 숨겨진 fullscreen route로 이동한다.

#### C. 루틴 관리

기능:

- 루틴 목록 표시
- 루틴 추가
- 루틴 편집
- 루틴 삭제
- 루틴 복제
- 루틴 내 운동 종목 추가
- 운동 종목 순서 변경
- 목표 세트, 목표 무게, 목표 횟수, 휴식 시간 설정

루틴은 계획 데이터다. 실제 운동 기록은 수행 데이터다. 루틴 수정이 과거 운동 기록을 변경하면 안 된다.

### 2.3 기록

역할: 과거 운동, 체중, 식사 기록을 날짜 기준으로 조회하는 아카이브.

기록 탭은 달력을 중심으로 구성한다.

주요 기능:

- 월간 달력 표시
- 운동 수행일 표시
- 날짜별 운동 부위 마커 표시
- 날짜 선택 시 운동, 체중, 식사 기록 표시
- 운동 기록 클릭 시 상세 화면 이동
- 체중 기록 확인 및 수정
- 식사 기록 확인 및 수정

운동 부위 카테고리:

- 가슴
- 등
- 하체
- 어깨
- 팔
- 복근
- 유산소

달력 날짜 셀에는 큰 분할 영역보다 작은 컬러 스트립, 점, 미니 블록을 사용한다.

날짜 선택 시 표시 정보:

- 운동 기록 목록
- 운동 시작 시간
- 운동 시간
- 총 볼륨
- 주요 운동 부위
- 체중 기록
- 식사 기록

기록 탭은 읽기 중심이다. 수정은 명시적인 상세 화면에서만 허용한다.

### 2.4 설정

역할: 앱 환경 설정과 연결 관리.

우선 기능:

- Supabase 연결 상태
- Supabase URL / anon key / user id 저장
- 수동 동기화
- 기본 단위 설정
- 앱 테마 설정

향후 확장:

- 기본 휴식 시간
- 운동 부위 색상 커스터마이징
- 데이터 백업
- 데이터 내보내기
- 알림 설정
- 프로필 설정

## 3. Routes

### Bottom Tab Routes

- `/main`
- `/fitness`
- `/records`
- `/settings`

### Fitness Fullscreen Routes

- `/fitness/workout/start`
- `/fitness/workout/session/:recordId`
- `/fitness/workout/summary/:recordId`
- `/fitness/routine/new`
- `/fitness/routine/edit/:routineId`
- `/fitness/weight/new`
- `/fitness/diet/new`

주의:

- `WorkoutSessionScreen`에서는 하단 탭을 숨긴다.
- `RoutineEditorScreen`은 필요하면 하단 탭을 숨기고 상단 뒤로가기 중심으로 처리한다.
- route 이름의 `session`은 화면 개념이다. DB에 독립 부모 `workout_sessions`를 만든다는 뜻이 아니다.

### Records Routes

- `/records/calendar`
- `/records/day/:date`
- `/records/workout/:recordId`
- `/records/weight/:recordId`
- `/records/diet/:recordId`

## 4. Screen Flow

### 메인 -> 피트니스

메인에서 저장 루틴 빠른 시작을 누르면 피트니스 탭의 시작 흐름과 동일하게 실제 운동 기록을 생성하고 `WorkoutSessionScreen`으로 이동한다.

메인에서 체중 또는 식사 버튼을 누르면 피트니스 탭의 오늘 몸 상태 입력 흐름으로 이동한다.

### 피트니스 -> WorkoutSessionScreen

운동 시작을 누르면 fullscreen `WorkoutSessionScreen`으로 이동한다.

WorkoutSessionScreen 기능:

- 운동 종목 추가
- 운동 종목 삭제
- 운동 종목 순서 변경
- 세트 추가
- 세트 삭제
- 무게 입력
- 횟수 입력
- RPE 입력
- 세트 완료 체크
- 운동 완료
- 임시 저장 후 나가기

뒤로가기 선택지:

- 계속 운동하기
- 임시 저장하고 나가기
- 기록 삭제하고 나가기

진행 중인 운동 기록은 `status=in_progress`로 유지한다.

### WorkoutSessionScreen -> WorkoutSummaryScreen

운동 완료 시 저장할 값:

- `ended_at`
- `duration_sec`
- `total_volume`
- 운동 부위 요약
- 운동별 볼륨
- 세트별 수행 내역

WorkoutSummaryScreen 표시 정보:

- 운동 날짜
- 운동 시간
- 총 운동 시간
- 총 볼륨
- 수행한 운동 종목
- 각 운동별 세트, 무게, 횟수
- 부위별 볼륨
- 메모

### 기록 -> WorkoutRecordDetail

기록 탭에서 운동 기록을 선택하면 `WorkoutRecordDetailScreen`으로 이동한다.

기본 mode는 `readonly`다. 필요할 때만 “기록 수정” 버튼으로 `edit` mode에 진입한다.

## 5. Shared Workout UI Modes

운동 구성 UI는 중복 구현하지 않는다.

### mode = template

루틴 편집 모드.

사용 화면:

- RoutineEditorScreen

가능 기능:

- 운동 종목 추가
- 운동 종목 삭제
- 운동 순서 변경
- 목표 세트 추가
- 목표 세트 삭제
- 목표 무게 입력
- 목표 횟수 입력
- 목표 휴식 시간 입력

불가능 기능:

- 세트 완료 체크
- 운동 완료
- 실제 수행 기록 저장

### mode = session

운동 수행 모드.

사용 화면:

- WorkoutSessionScreen

가능 기능:

- 운동 종목 추가
- 운동 종목 삭제
- 운동 순서 변경
- 실제 세트 추가
- 실제 세트 삭제
- 무게 입력
- 횟수 입력
- RPE 입력
- 세트 완료 체크
- 운동 완료

### mode = readonly

운동 기록 조회 모드.

사용 화면:

- WorkoutSummaryScreen
- WorkoutRecordDetailScreen

가능 기능:

- 운동 기록 조회
- 운동별 세트 확인
- 총 볼륨 확인
- 운동 시간 확인
- 부위별 요약 확인

### mode = edit

완료 기록 수정 모드.

사용 화면:

- WorkoutRecordDetailScreen

가능 기능:

- 운동 종목 수정
- 세트 수정
- 메모 수정
- 총 볼륨과 운동 시간 재계산

기록 수정은 루틴 템플릿을 수정하지 않는다.

## 6. Data Model Concept

이 섹션은 개념 설계다. 실제 DB 이름은 현재 프로젝트의 기존 테이블과 migration 정책을 우선한다.

### 6.1 Common Record Parent

역할:

- 언제, 누가, 어떤 타입의 기록을 남겼는지 나타내는 상위 사건
- 운동, 체중, 식사, 메모, 할 일 등 Personal OS 공통 기록과 연결

개념 필드:

- `id`
- `type`
- `title`
- `summary`
- `recorded_at`
- `created_at`
- `updated_at`
- `source_app`
- `scope`
- `metadata`

피트니스 type:

- `workout`
- `body_weight`
- `diet`

현재 구조와의 매핑:

- 현재 구현이 `workout_records`, `weight_records`, `meal_records`를 사용한다면 이를 유지한다.
- 새 독립 부모 `workout_sessions`를 만들기보다 기존 부모 기록의 `id`를 하위 상세 테이블이 참조한다.

### 6.2 Exercise Catalog

운동 종목 마스터 데이터.

현재 Android Fitness App에서는 `Fitness_Weight.json`이 웨이트 운동 마스터 데이터의 source of truth다.

개념 필드:

- `id`
- `name_ko`
- `name_en`
- `ui_part`
- `equipment`
- `primary_sub_part`
- `secondary_sub_parts`
- `record_type`
- `mechanic_type`
- `is_active`

최상위 UI 부위:

- 가슴
- 등
- 하체
- 어깨
- 팔
- 복근

### 6.3 Routine Template

저장된 루틴 템플릿.

개념 구조:

```text
routine_templates
-> routine_template_exercises
-> optional routine_template_sets
```

현재 Android app에서는 `routines`와 `routine_exercises`가 이 역할을 한다.

루틴은 계획 데이터이며 과거 운동 기록을 변경하지 않는다.

### 6.4 Workout Record Detail

실제 운동 기록 상세 구조:

```text
parent workout record
-> workout_exercises
-> workout_sets
```

`workout_exercises` 개념 필드:

- `id`
- `record_id`
- `exercise_id`
- `exercise_name_snapshot`
- `ui_part`
- `primary_sub_part_snapshot`
- `equipment_snapshot`
- `record_type`
- `position`
- `note`

`workout_sets` 개념 필드:

- `id`
- `workout_exercise_id`
- `position`
- `target_reps`
- `actual_reps`
- `weight_kg`
- `duration_seconds`
- `distance_meters`
- `rest_seconds`
- `assisted_weight_kg`
- `added_weight_kg`
- `volume`
- `rpe`
- `is_completed`
- `set_type`
- `memo`

volume 기본 계산:

```text
volume = weight_kg * actual_reps
```

record type에 따라 맨몸, 보조 중량, 추가 중량, 시간 기반 운동은 별도 계산 규칙을 둔다.

### 6.5 Body Weight

체중 기록은 기존 OS 기록 구조와 연결한다.

개념 필드:

- `id`
- `record_id`
- `measured_at`
- `weight`
- `memo`

현재 프로젝트가 `weight_records`를 사용한다면 이를 유지한다.

### 6.6 Diet

식사 기록은 기존 OS 기록 구조와 연결한다.

개념 필드:

- `id`
- `record_id`
- `eaten_at`
- `meal_type`
- `food_name`
- `calories`
- `protein`
- `carbs`
- `fat`
- `memo`

현재 프로젝트가 `meal_records`를 사용한다면 이를 유지한다. MVP에서는 무리하게 새 상세 테이블을 늘리지 않고 `metadata` 확장을 우선한다.

## 7. User Flows

### 루틴으로 운동 시작

1. 사용자가 메인 또는 피트니스 탭에서 저장 루틴을 선택한다.
2. 시작 버튼을 누른다.
3. 루틴 템플릿 기반으로 부모 운동 기록을 생성한다.
4. 루틴 운동 종목을 `workout_exercises`로 복사한다.
5. 필요한 경우 목표 세트를 `workout_sets` 초기값으로 생성한다.
6. `WorkoutSessionScreen`으로 이동한다.
7. 사용자가 세트 입력 및 완료 체크를 한다.
8. 운동 완료를 누른다.
9. `total_volume`, `duration_sec`, `ended_at`을 계산한다.
10. 상태를 `completed`로 변경한다.
11. `WorkoutSummaryScreen`을 표시한다.
12. 기록 탭 달력에 반영한다.

### 빈 운동 시작

1. 피트니스 탭에서 빈 운동 시작을 누른다.
2. 루틴 템플릿 없는 부모 운동 기록을 생성한다.
3. `WorkoutSessionScreen`으로 이동한다.
4. 사용자가 운동 종목을 직접 추가한다.
5. 세트를 입력한다.
6. 운동 완료를 누른다.
7. 기록을 저장한다.

### 오늘 체중 기록

1. 피트니스 탭에 진입한다.
2. 오늘의 몸 상태 섹션에서 체중을 입력한다.
3. 공통 기록 또는 `weight_records`에 체중 기록을 저장한다.
4. 메인 탭과 기록 탭에 반영한다.

### 오늘 식사 기록

1. 피트니스 탭에 진입한다.
2. 오늘의 몸 상태 섹션에서 식사를 추가한다.
3. 공통 기록 또는 `meal_records`에 식사 기록을 저장한다.
4. 메인 탭과 기록 탭에 반영한다.

### 기록 조회

1. 기록 탭에 진입한다.
2. 달력에서 날짜를 선택한다.
3. 해당 날짜의 운동, 체중, 식사 기록을 조회한다.
4. 운동 기록 선택 시 `WorkoutRecordDetailScreen`을 표시한다.

## 8. UX Rules

### 운동 중 집중 모드

`WorkoutSessionScreen`에서는 하단 탭을 숨긴다. 상단에는 뒤로가기, 운동명 또는 경과 시간, 완료 버튼 정도만 둔다.

### 기록 보존

운동 중 뒤로가기 또는 앱 종료 상황을 고려한다.

진행 중인 운동 기록은 `status=in_progress`로 보존한다. 앱 재실행 시 진행 중인 세션이 있으면 이어하기를 제안한다.

### 빠른 세트 입력

이전 세트의 무게와 횟수를 다음 세트 기본값으로 복사한다. 사용자는 필요한 값만 빠르게 수정할 수 있어야 한다.

### 루틴과 기록 분리

루틴 수정이 과거 운동 기록을 변경하면 안 된다. 과거 운동 기록은 당시 수행한 데이터로 고정된다.

### 기록 화면은 읽기 중심

완료된 운동 기록은 기본적으로 `readonly`로 보여준다. 수정은 명시적인 “기록 수정” 액션을 통해서만 가능하게 한다.

## 9. Current Code Conflict Analysis

현재 코드와 충돌할 수 있는 지점:

- 현재 하단 탭 이름이 `HOME / 운동 / 기록 / 설정`이면 목표 구조인 `메인 / 피트니스 / 기록 / 설정`과 이름이 다르다.
- 현재 `운동` 탭이 루틴 추가와 세트 추가를 직접 품고 있다면, 장기적으로 fullscreen `WorkoutSessionScreen`으로 분리해야 한다.
- 현재 루틴 추가가 탭 내부 화면으로만 구현되어 있다면, 이후 `RoutineEditorScreen` 또는 `/fitness/routine/edit/:routineId`로 분리할 필요가 있다.
- 현재 운동 시작 후 하단 탭이 유지된다면 운동 수행 집중 모드 원칙과 충돌한다.
- UI의 `session`과 DB의 세션 부모 개념을 혼동하면 안 된다. DB 부모는 기존 `records` 또는 `workout_records` 계층을 우선한다.
- 총 볼륨과 운동 시간이 화면에서만 계산된다면, 운동 완료 시점에 저장되는 집계 필드 계획이 필요하다.
- 기록 탭에 선택 날짜 텍스트 카드처럼 조회 판단에 필요 없는 UI가 있으면 달력과 날짜별 기록 중심으로 정리한다.

## 10. Minimum Implementation Plan

실제 코드 변경 전 순서:

1. Android `MainActivity`, repository, SQLite schema, Supabase sync 구조를 확인한다.
2. 하단 탭 이름과 역할을 `메인 / 피트니스 / 기록 / 설정`으로 맞춘다.
3. 메인 탭에서 입력/편집 기능을 제거하고 요약과 빠른 시작만 남긴다.
4. 피트니스 탭을 `오늘의 몸 상태`, `운동 시작`, `루틴 관리` 섹션으로 재구성한다.
5. 운동 시작 흐름을 fullscreen `WorkoutSessionScreen`으로 분리할 수 있는 route boundary를 만든다.
6. 루틴 편집, 운동 수행, 기록 조회를 `template/session/readonly/edit` mode로 재사용할 수 있게 컴포넌트 경계를 설계한다.
7. 운동 완료 액션에서 `ended_at`, `duration_sec`, `total_volume` 저장 위치를 확정한다.
8. 기록 탭은 달력과 날짜별 기록 조회 중심으로 정리한다.
9. Supabase sync 대상 테이블과 컬럼 변경이 필요하면 migration 문서를 먼저 작성한다.
10. 빌드와 에뮬레이터 확인으로 UI 흐름을 검증한다.

## 11. Non-goals

이번 설계 문서에서 제외하는 것:

- 코드 구현
- DB migration 작성
- 화면 스타일 고도화
- 차트 구현
- AI 운동 추천
- Health API 연동
- 식단 이미지 분석
- 기존 OS 기록 삭제 또는 강제 마이그레이션

## 12. Decision Summary

최종 목표 구조:

```text
Bottom tabs
메인 -> 피트니스 -> 기록 -> 설정

Fitness execution
피트니스 탭 -> WorkoutSessionScreen(fullscreen) -> WorkoutSummaryScreen

Workout data
parent workout record -> workout_exercises -> workout_sets

Routine data
routine_templates -> routine_template_exercises -> optional routine_template_sets

Read modes
template / session / readonly / edit
```

핵심 판단:

- 하단 탭은 탐색 구조이고, 운동 수행 화면은 집중 실행 화면이다.
- 루틴은 계획 데이터이고, 운동 기록은 수행 데이터다.
- 메인은 판단과 빠른 시작만 담당한다.
- 피트니스 탭은 오늘 기록과 실행을 담당한다.
- 기록 탭은 과거 조회와 재확인을 담당한다.
