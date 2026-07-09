# Fitness App Claude Handoff — Revised Model Skeleton

## 0. 문서 목적

이 문서는 Personal OS 안의 Fitness App을 Claude Code가 오해 없이 정리하도록 전달하는 기준 문서다.

이 문서의 핵심 목적은 다음 혼동을 제거하는 것이다.

1. `피트니스` 탭과 `운동 수행 화면`을 혼동하지 않는다.
2. `루틴 관리`와 `실제 운동 기록`을 혼동하지 않는다.
3. 운동 시작 후 화면을 루틴 관리 화면으로 만들지 않는다.
4. `workout_session`이라는 화면/상태 개념을 DB의 새 부모 테이블로 오해하지 않는다.
5. 체중/식사는 새 독립 테이블을 먼저 만드는 것이 아니라 기존 Personal OS 기록/item 구조에 편입한다.

이번 작업의 1차 범위는 구조 정리와 UX 흐름 고정이다.

- 코드베이스를 먼저 조사한다.
- 현재 테이블, 모델, repository, navigation 구조를 확인한다.
- DB migration은 바로 작성하지 않는다.
- 기존 기록 삭제 또는 강제 마이그레이션을 하지 않는다.
- 구현 전, 현재 구조와 목표 구조의 매핑표를 먼저 만든다.

---

## 1. 현재 문제 진단

현재 Codex가 만든 결과는 사용자의 의도와 다르게 흘러가고 있다.

원래 의도:

```text
피트니스 탭
-> 운동 시작
-> fullscreen WorkoutSessionScreen
-> 운동 종목별 세트 입력/수정
-> 운동 완료
-> 기록 저장
```

잘못된 방향:

```text
피트니스 탭 또는 운동 시작 후 화면
-> 루틴 관리 / 루틴 추가 / 운동 목록 관리 중심
```

이것은 Fitness App의 중심이 `운동 수행`인지, `루틴 관리`인지 구분하지 못해서 생긴 문제다.

정확한 구조는 다음과 같다.

- 루틴 관리는 계획을 만드는 화면이다.
- 운동 수행 화면은 오늘 실제 세트, 중량, 횟수, RPE, 완료 체크를 입력하는 화면이다.
- 운동 시작 후 사용자가 가장 먼저 봐야 하는 것은 루틴 목록이 아니라 세트 입력 UI다.
- 루틴으로 시작했더라도, 시작 순간 루틴 템플릿은 실제 운동 기록으로 복사되어야 한다.
- 운동 중 수정은 현재 운동 기록만 수정해야 하며 원본 루틴 템플릿을 바꾸면 안 된다.

---

## 2. 절대 유지해야 할 결정사항

### 2.1 하단 탭

하단 탭은 4개로 고정한다.

```text
메인 / 피트니스 / 기록 / 설정
```

기존 `운동` 탭 이름은 `피트니스`로 바꾼다.

이유:

- 해당 탭은 운동만 다루지 않는다.
- 오늘 체중, 오늘 식사, 운동 시작, 루틴 관리를 함께 담당한다.
- `피트니스`가 운동, 체중, 식사를 포괄하는 더 정확한 이름이다.

### 2.2 메인 탭의 역할

메인 탭은 대시보드와 빠른 접근만 담당한다.

메인 탭에서 해야 하는 것:

- 이번 달 운동 수행일 수 표시
- 이번 달 총 운동 횟수 표시
- 이번 달 총 볼륨 표시
- 체중 변화 요약
- 최근 수행 루틴 표시
- 저장 루틴 빠른 시작
- 오늘 체중 기록 바로가기
- 오늘 식사 기록 바로가기
- 진행 중인 운동 이어하기

메인 탭에서 하면 안 되는 것:

- 루틴 편집
- 운동 종목 세부 수정
- 세트 입력
- 과거 운동 기록 상세 수정

### 2.3 피트니스 탭의 역할

피트니스 탭은 오늘 실행하는 신체 관리의 중심이다.

피트니스 탭은 3개 섹션으로 구성한다.

```text
A. 오늘의 몸 상태
B. 운동 시작
C. 루틴 관리
```

중요한 우선순위:

1. 오늘 체중과 식사
2. 오늘 운동 시작
3. 루틴 관리

루틴 관리는 피트니스 탭 안에 존재하지만, 피트니스 탭의 본질은 루틴 관리가 아니라 오늘 실행이다.

### 2.4 기록 탭의 역할

기록 탭은 과거 운동, 체중, 식사 기록 조회 중심이다.

주요 구성:

- 월간 달력
- 날짜별 운동 수행 여부
- 날짜별 주요 운동 부위 마커
- 선택 날짜의 운동/체중/식사 기록
- 운동 기록 상세 조회
- 명시적 수정 모드

기록 탭은 기본적으로 읽기 중심이다. 수정은 상세 화면에서 `기록 수정` 액션을 눌렀을 때만 허용한다.

### 2.5 설정 탭의 역할

설정 탭은 환경 설정과 연결 관리만 담당한다.

우선 기능:

- Supabase 연결 상태
- Supabase URL / anon key / user id 저장
- 수동 동기화
- 기본 단위 설정
- 앱 테마 설정

---

## 3. 운동 수행 화면의 핵심 UX

### 3.1 운동 시작 후 첫 화면

운동 시작 후 이동하는 화면은 반드시 fullscreen `WorkoutSessionScreen`이어야 한다.

이 화면에서는 하단 탭을 숨긴다.

상단에는 최소한의 요소만 둔다.

- 뒤로가기
- 경과 시간 또는 운동명
- 운동 완료 버튼

화면 본문은 세트 입력이 중심이어야 한다.

예시 구조:

```text
[상단]
←  00:42:13                           운동 완료

[운동 카드 1]
벤치프레스
세트 | 이전 | kg | reps | RPE | 완료
1    | 80x8 | 90 | 7    | 8   | ✓
2    | 90x7 | 90 | 6    |     | □
+ 세트 추가

[운동 카드 2]
인클라인 덤벨 프레스
세트 | 이전 | kg | reps | RPE | 완료
1    | 30x10 | 32 | 8 |     | □
+ 세트 추가

+ 운동 추가
```

운동 시작 후 나오면 안 되는 것:

- 루틴 목록 중심 화면
- 루틴 생성 화면
- 운동 마스터 데이터 관리 화면
- 단순한 `오늘 운동` 카드만 있고 세트 입력이 없는 화면

### 3.2 루틴으로 운동 시작

루틴으로 운동을 시작하면 다음 순서로 처리한다.

1. 사용자가 저장 루틴을 선택한다.
2. `운동 시작`을 누른다.
3. 기존 Personal OS의 부모 기록에 `type=workout` 운동 기록을 만든다.
4. 선택한 루틴의 운동 종목을 현재 운동 기록의 `workout_exercises`로 복사한다.
5. 루틴의 목표 세트가 있으면 현재 운동 기록의 `workout_sets` 초기값으로 복사한다.
6. `WorkoutSessionScreen`으로 이동한다.
7. 사용자는 세트별 중량, 횟수, RPE, 완료 여부를 수정한다.
8. 운동 완료 시 총 볼륨, 운동 시간, 종료 시각을 계산한다.
9. 기록 탭 달력에 반영한다.

중요:

- 현재 운동 중 운동 종목을 추가/삭제/수정해도 원본 루틴은 바뀌면 안 된다.
- 원본 루틴을 바꾸려면 루틴 관리의 `RoutineEditorScreen`에서만 해야 한다.

### 3.3 빈 운동 시작

빈 운동 시작은 루틴 템플릿 없이 실제 운동 기록을 만드는 흐름이다.

1. 피트니스 탭에서 `빈 운동 시작`을 누른다.
2. 기존 Personal OS의 부모 기록에 `type=workout` 운동 기록을 만든다.
3. `WorkoutSessionScreen`으로 이동한다.
4. 사용자가 운동 종목을 직접 추가한다.
5. 사용자가 세트를 추가하고 중량/횟수/RPE를 입력한다.
6. 운동 완료 시 기록을 저장한다.

### 3.4 진행 중인 운동

운동 중 뒤로가기 또는 앱 종료를 고려한다.

진행 중인 운동 기록은 `status=in_progress`로 유지한다.

앱 재실행 또는 피트니스 탭 진입 시 진행 중인 운동이 있으면 다음 버튼을 표시한다.

```text
진행 중인 운동 이어하기
```

뒤로가기 선택지는 다음과 같이 둔다.

- 계속 운동하기
- 임시 저장하고 나가기
- 기록 삭제하고 나가기

---

## 4. 루틴 관리의 정확한 역할

루틴은 계획 데이터다.

루틴 관리에서 하는 것:

- 루틴 목록 표시
- 루틴 추가
- 루틴 편집
- 루틴 삭제
- 루틴 복제
- 루틴 내 운동 종목 추가
- 운동 종목 순서 변경
- 목표 세트 설정
- 목표 무게 설정
- 목표 횟수 설정
- 목표 휴식 시간 설정

루틴 관리에서 하면 안 되는 것:

- 오늘 실제 수행 세트 입력
- 실제 운동 완료 처리
- 과거 운동 기록 수정
- 진행 중인 운동 기록 직접 변경

루틴 수정이 과거 운동 기록을 변경하면 안 된다.

---

## 5. 공통 운동 UI 모드

운동 구성 UI는 중복 구현하지 않는 방향이 좋다. 단, mode별 책임을 명확히 나눠야 한다.

```text
template  = 루틴 편집
session   = 실제 운동 수행
readonly  = 완료 기록 조회
edit      = 완료 기록 수정
```

### 5.1 mode = template

사용 화면:

- `RoutineEditorScreen`

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

### 5.2 mode = session

사용 화면:

- `WorkoutSessionScreen`

가능 기능:

- 운동 종목 추가
- 운동 종목 삭제
- 운동 종목 순서 변경
- 실제 세트 추가
- 실제 세트 삭제
- 무게 입력
- 횟수 입력
- RPE 입력
- 세트 완료 체크
- 운동 완료
- 임시 저장 후 나가기

핵심 제약:

- 이 모드는 루틴 관리 화면이 아니다.
- 이 모드에서는 실제 수행 기록만 다룬다.
- 화면의 중심은 세트 입력/수정이다.

### 5.3 mode = readonly

사용 화면:

- `WorkoutSummaryScreen`
- `WorkoutRecordDetailScreen`

가능 기능:

- 운동 기록 조회
- 운동별 세트 확인
- 총 볼륨 확인
- 운동 시간 확인
- 부위별 요약 확인

### 5.4 mode = edit

사용 화면:

- `WorkoutRecordDetailScreen`

가능 기능:

- 완료된 운동 기록의 운동 종목 수정
- 완료된 운동 기록의 세트 수정
- 메모 수정
- 총 볼륨과 운동 시간 재계산

제약:

- 기록 수정은 루틴 템플릿을 수정하지 않는다.

---

## 6. 데이터 모델 원칙

이 섹션은 개념 설계다. Claude Code는 실제 코드베이스를 먼저 확인한 뒤 현재 구조에 맞춰야 한다.

### 6.1 최상위 원칙

현재 단계의 최우선 원칙:

```text
새 DB 구조를 마음대로 만들지 말고, 기존 Personal OS 기록 구조를 우선한다.
```

운동 기록은 기존 OS의 부모 기록을 참조한다.

```text
existing parent record
-> workout_exercises
-> workout_sets
```

주의:

- 새 독립 부모 테이블 `workout_sessions`를 먼저 만들지 않는다.
- `session`은 화면/상태/사용자 흐름 개념이다.
- DB 부모는 기존 `records` 또는 현재 프로젝트가 이미 쓰고 있는 상위 기록 구조를 우선한다.

### 6.2 운동 기록 상세

운동 상세는 다음 구조를 목표로 한다.

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

기본 볼륨 계산:

```text
volume = weight_kg * actual_reps
```

단, 다음 유형은 별도 계산 규칙을 둔다.

- 맨몸 운동
- 보조 중량 운동
- 추가 중량 운동
- 시간 기반 운동
- 거리 기반 유산소 운동

### 6.3 운동 종목 마스터

웨이트 운동 마스터 데이터는 현재 프로젝트의 JSON 또는 seed 데이터를 우선한다.

현재 Android Fitness App에서 `Fitness_Weight.json`이 있다면 이를 웨이트 운동 마스터 데이터의 source of truth로 본다.

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

운동 종류 분류:

- 맨몸
- 머신
- 덤벨
- 바벨
- 케이블
- 유산소
- 기타

### 6.4 루틴 템플릿

루틴 템플릿은 계획 데이터다.

개념 구조:

```text
routine_templates
-> routine_template_exercises
-> optional routine_template_sets
```

현재 Android app에서 `routines`와 `routine_exercises`가 이미 있다면 그것을 우선한다.

루틴을 시작할 때는 템플릿을 실제 기록으로 복사한다.

```text
routine template
-> copied workout record
-> copied workout_exercises
-> copied workout_sets
```

복사 시점에 운동명, 부위, 장비 등 주요 정보는 snapshot으로 저장하는 것을 권장한다.

이유:

- 나중에 운동 마스터 데이터 이름이나 부위가 바뀌어도 과거 기록은 당시 의미를 보존해야 한다.

### 6.5 체중 기록

체중은 새 독립 테이블을 먼저 만들지 않는다.

현재 Personal OS에 기존 `records` 또는 `items` 구조가 있다면, 체중은 그 구조 안에 새로운 item/type으로 추가한다.

권장 개념:

```text
record/item type = body_weight
metadata = {
  "weight_kg": 72.4,
  "measured_at": "...",
  "memo": "..."
}
```

이미 프로젝트에 `weight_records`가 존재하고 실제로 사용 중이라면 유지할 수 있다.

하지만 존재하지 않는다면 Claude Code가 임의로 `weight_records`를 새로 만들면 안 된다.

### 6.6 식사 기록

식사도 새 독립 테이블을 먼저 만들지 않는다.

현재 Personal OS에 기존 `records` 또는 `items` 구조가 있다면, 식사는 그 구조 안에 새로운 item/type으로 추가한다.

권장 개념:

```text
record/item type = meal 또는 diet
metadata = {
  "meal_type": "lunch",
  "food_name": "닭가슴살 덮밥",
  "calories": 650,
  "protein_g": 42,
  "carbs_g": 75,
  "fat_g": 18,
  "eaten_at": "...",
  "memo": "..."
}
```

이미 프로젝트에 `meal_records`가 존재하고 실제로 사용 중이라면 유지할 수 있다.

하지만 존재하지 않는다면 Claude Code가 임의로 `meal_records`를 새로 만들면 안 된다.

### 6.7 운동 / 체중 / 식사의 관계

피트니스 앱에서 운동, 체중, 식사는 같은 `오늘 신체 관리` 도메인에 속한다.

하지만 데이터 모델상 동일한 상세 구조를 강요하지 않는다.

```text
운동: parent record + workout_exercises + workout_sets
체중: existing item/record type extension
식사: existing item/record type extension
```

이 차이를 유지해야 한다.

운동은 세부 세트 구조가 필요하다.

체중과 식사는 MVP 단계에서 세부 하위 테이블보다 기존 item metadata 확장이 더 적합하다.

---

## 7. 라우팅 구조

### 7.1 Bottom Tab Routes

실제 프로젝트의 navigation 방식에 맞추되, 의미상 다음 구조를 유지한다.

```text
/main
/fitness
/records
/settings
```

### 7.2 Fitness Routes

```text
/fitness/workout/session/:recordId
/fitness/workout/summary/:recordId
/fitness/routine/new
/fitness/routine/edit/:routineId
/fitness/weight/new
/fitness/diet/new
```

주의:

- `/fitness/workout/session/:recordId`의 `session`은 화면 이름이다.
- DB에 독립 부모 `workout_sessions`를 만들라는 뜻이 아니다.
- `WorkoutSessionScreen`은 하단 탭을 숨긴 fullscreen route다.

### 7.3 Records Routes

```text
/records/calendar
/records/day/:date
/records/workout/:recordId
/records/weight/:recordId
/records/diet/:recordId
```

---

## 8. 구현 전 Claude Code가 반드시 해야 할 조사

Claude Code는 코드 수정 전에 다음을 먼저 조사하고 보고해야 한다.

### 8.1 파일 구조 조사

확인할 것:

- Android 진입점
- MainActivity 또는 Navigation Host
- 하단 탭 구현 위치
- 현재 Fitness/Workout 관련 screen
- repository 계층
- local DB schema
- Supabase sync 코드
- JSON seed 또는 exercise catalog 위치

보고 형식:

```text
## Current Code Map

- Navigation:
  - ...
- Screens:
  - ...
- Data Models:
  - ...
- Repositories:
  - ...
- Local DB:
  - ...
- Supabase Sync:
  - ...
- Exercise Catalog:
  - ...
```

### 8.2 현재 구조와 목표 구조 매핑

코드 수정 전에 다음 표를 작성한다.

```text
## Current -> Target Mapping

| Target concept | Existing file/table/model | Reuse? | Change needed |
|---|---|---:|---|
| 메인 탭 | ... | yes/no | ... |
| 피트니스 탭 | ... | yes/no | ... |
| WorkoutSessionScreen | ... | yes/no | ... |
| RoutineEditorScreen | ... | yes/no | ... |
| parent workout record | ... | yes/no | ... |
| workout_exercises | ... | yes/no | ... |
| workout_sets | ... | yes/no | ... |
| body_weight item | ... | yes/no | ... |
| meal item | ... | yes/no | ... |
```

### 8.3 충돌 지점 보고

Claude Code는 다음 충돌 가능성을 특히 확인해야 한다.

- 현재 하단 탭이 `HOME / 운동 / 기록 / 설정`인지
- `운동` 탭이 루틴 관리와 세트 입력을 섞고 있는지
- 운동 시작 후 하단 탭이 그대로 남아 있는지
- 운동 시작 후 화면이 루틴 관리로 이동하는지
- `workout_sessions` 같은 새 부모 테이블을 만들고 있는지
- 체중/식사를 새 테이블로 만들고 있는지
- 기존 records/items 구조를 우회하고 있는지
- 완료된 운동 기록과 루틴 템플릿이 같은 데이터를 공유하고 있는지

---

## 9. 최소 구현 순서

### Phase 0. 조사만 수행

목표:

- 코드베이스 구조 파악
- 현재 DB/모델 파악
- 현재 navigation 파악
- 수정 계획 작성

이 단계에서는 코드 수정 금지.

### Phase 1. 명칭과 탭 역할 정리

목표:

- 하단 탭 이름을 `메인 / 피트니스 / 기록 / 설정`으로 정리
- 기존 `운동` 탭을 `피트니스`로 변경
- 메인 탭은 요약과 빠른 시작만 남김
- 피트니스 탭은 오늘 체중/식사, 운동 시작, 루틴 관리 섹션으로 구성

### Phase 2. 운동 수행 화면 분리

목표:

- `WorkoutSessionScreen`을 fullscreen route로 분리
- 하단 탭 숨김
- 운동 시작 시 세트 입력 화면으로 이동
- 루틴 관리 화면으로 이동하지 않게 수정

성공 기준:

- 저장 루틴에서 시작해도 첫 화면은 세트 입력 화면이어야 한다.
- 빈 운동 시작을 눌러도 첫 화면은 세트 입력 화면이어야 한다.
- 화면 안에서 운동 종목 추가, 세트 추가, 중량/횟수/RPE 수정, 완료 체크가 가능해야 한다.

### Phase 3. 루틴과 실제 기록 분리

목표:

- 루틴 템플릿은 계획 데이터로 유지
- 운동 시작 시 루틴을 실제 운동 기록으로 복사
- 운동 중 수정이 원본 루틴에 반영되지 않게 처리

성공 기준:

- 루틴 A로 운동 시작 후 세트를 수정해도 루틴 A의 기본 목표값은 변하지 않는다.
- 완료된 운동 기록을 수정해도 루틴 템플릿은 변하지 않는다.

### Phase 4. 운동 상세 데이터 연결

목표:

- 기존 부모 기록 구조에 `type=workout` 또는 이에 준하는 방식으로 운동 기록 연결
- `workout_exercises`와 `workout_sets`가 부모 기록을 참조하도록 정리
- `workout_session`이라는 새 부모 테이블을 임의로 만들지 않음

성공 기준:

- 하나의 운동 기록은 여러 운동 종목을 가진다.
- 하나의 운동 종목은 여러 세트를 가진다.
- 세트별 중량/횟수/RPE/완료 여부가 저장된다.
- 운동 완료 시 총 볼륨과 운동 시간이 계산된다.

### Phase 5. 체중/식사 item 확장

목표:

- 체중과 식사는 기존 Personal OS 기록/item 구조에 편입
- 새 `weight_records`, `meal_records` 테이블을 임의로 만들지 않음
- 기존 테이블이 이미 있다면 재사용

성공 기준:

- 피트니스 탭에서 오늘 체중을 입력할 수 있다.
- 피트니스 탭에서 오늘 식사를 입력할 수 있다.
- 메인 탭과 기록 탭에서 해당 데이터가 조회된다.
- DB 구조가 기존 OS 기록 체계를 우회하지 않는다.

### Phase 6. 기록 탭 정리

목표:

- 기록 탭을 달력 중심으로 정리
- 날짜 선택 시 운동/체중/식사 기록 표시
- 운동 기록 상세는 기본 readonly
- 수정은 명시적 edit mode로만 진입

---

## 10. UI 수용 기준

### 10.1 피트니스 탭

피트니스 탭은 다음 순서를 따른다.

```text
오늘의 몸 상태
- 오늘 체중
- 오늘 식사
- 빠른 추가 버튼

운동 시작
- 진행 중인 운동 이어하기
- 저장 루틴 빠른 시작
- 최근 수행 루틴
- 빈 운동 시작

루틴 관리
- 루틴 목록
- 루틴 추가/편집
```

### 10.2 WorkoutSessionScreen

필수 요소:

- fullscreen
- 하단 탭 없음
- 경과 시간 표시
- 운동 완료 버튼
- 운동 종목 카드
- 세트 row
- 중량 입력
- 횟수 입력
- RPE 입력
- 완료 체크
- 세트 추가
- 운동 추가
- 임시 저장 후 나가기

금지 요소:

- 루틴 목록이 메인인 화면
- 루틴 생성이 메인인 화면
- 단순 기록 카드만 있는 화면
- 세트 수정 없이 완료만 있는 화면

### 10.3 기록 탭

필수 요소:

- 달력
- 운동 수행일 표시
- 부위별 마커
- 선택 날짜의 운동/체중/식사 기록
- 운동 기록 상세 조회
- 명시적 수정 액션

---

## 11. 기존 Skeleton 문서에서 수정한 지점

이전 문서의 큰 방향은 맞지만 다음 지점이 약했다.

### 11.1 운동 시작 후 화면 제약이 약함

이전 문서에는 fullscreen `WorkoutSessionScreen`이 언급되어 있지만, Codex가 실제로는 루틴 관리 중심 UI를 만들 여지가 있었다.

수정:

- 운동 시작 후 첫 화면은 반드시 세트 입력/수정 화면이라고 명시했다.
- 루틴 목록, 루틴 생성, 운동 마스터 관리가 나오면 안 된다고 명시했다.

### 11.2 체중/식사 데이터 모델이 애매함

이전 문서에는 `weight_records`, `meal_records`를 유지할 수 있다고 되어 있었다.

이 표현은 새 테이블 생성을 허용하는 것처럼 오해될 수 있다.

수정:

- 현재 단계에서는 체중/식사를 기존 records/items 구조의 item/type 확장으로 처리한다고 명시했다.
- 이미 존재하는 테이블만 재사용 가능하다고 제한했다.
- 존재하지 않는 `weight_records`, `meal_records`를 임의로 만들지 말라고 명시했다.

### 11.3 `workout_session` 혼동 가능성

이전 문서도 새 독립 부모 `workout_sessions`를 만들지 말라고 했지만, route 이름과 화면 이름에서 여전히 혼동 가능성이 있었다.

수정:

- `session`은 화면/상태 개념이라고 반복 명시했다.
- DB 부모는 기존 `records` 또는 현재 프로젝트의 상위 기록 구조를 우선한다고 명시했다.

### 11.4 Claude Code 작업 순서가 부족함

이전 문서에는 최소 구현 계획이 있었지만, Claude Code가 바로 코드를 고칠 가능성이 있었다.

수정:

- Phase 0을 `조사만 수행`으로 분리했다.
- 코드 수정 전에 현재 구조와 목표 구조 매핑표를 작성하게 했다.

---

## 12. Claude Code에 줄 첫 명령

아래 프롬프트를 Claude Code에 그대로 전달한다.

```text
너는 이 프로젝트를 정리하는 시니어 Android/Kotlin 풀스택 개발자다.

먼저 첨부한 `Fitness App Claude Handoff — Revised Model Skeleton` 문서를 읽어라.
이 문서는 피트니스 앱의 목표 구조와 절대 유지해야 할 UX/DB 원칙이다.

중요:
- 지금 바로 코드 수정하지 마라.
- DB migration 만들지 마라.
- 새 테이블을 임의로 만들지 마라.
- 기존 기록 삭제 또는 강제 마이그레이션 하지 마라.
- 먼저 현재 코드베이스를 조사하고 보고하라.

특히 확인할 것:
1. 현재 하단 탭 구조와 이름
2. 현재 운동/피트니스 관련 screen 구조
3. 운동 시작 버튼이 실제로 어디로 이동하는지
4. 운동 시작 후 화면이 세트 입력 중심인지, 루틴 관리 중심인지
5. 기존 records/items 구조
6. workout_exercises / workout_sets 존재 여부와 연결 방식
7. 체중/식사가 기존 item 구조에 들어갈 수 있는지
8. Supabase sync와 local DB schema의 현재 구조

보고 형식:

## Current Code Map
- Navigation:
- Screens:
- Data Models:
- Repositories:
- Local DB:
- Supabase Sync:
- Exercise Catalog:

## Current -> Target Mapping
| Target concept | Existing file/table/model | Reuse? | Change needed |
|---|---|---:|---|
| 메인 탭 | | | |
| 피트니스 탭 | | | |
| WorkoutSessionScreen | | | |
| RoutineEditorScreen | | | |
| parent workout record | | | |
| workout_exercises | | | |
| workout_sets | | | |
| body_weight item | | | |
| meal item | | | |

## Conflict List
- 목표 구조와 충돌하는 현재 구현을 구체적으로 나열하라.

## Minimal Patch Plan
- 파일별로 어떤 변경이 필요한지 제안하라.
- 각 변경의 목적을 설명하라.
- 변경 순서를 Phase 단위로 나눠라.

내가 승인하기 전까지 코드는 수정하지 마라.
```

---

## 13. 최종 성공 기준

이 작업이 성공한 상태는 다음과 같다.

```text
앱 실행
-> 하단 탭: 메인 / 피트니스 / 기록 / 설정

피트니스 탭
-> 오늘 체중/식사 표시
-> 운동 시작
-> 루틴 관리

운동 시작
-> fullscreen WorkoutSessionScreen
-> 하단 탭 숨김
-> 운동 종목별 세트 입력/수정
-> 중량/횟수/RPE/완료 체크
-> 운동 완료
-> WorkoutSummaryScreen
-> 기록 탭 달력 반영

데이터
-> 운동은 기존 parent record를 참조
-> 운동 상세는 workout_exercises / workout_sets
-> 체중과 식사는 기존 records/items 구조에 item/type으로 편입
-> 루틴은 계획 데이터
-> 운동 기록은 수행 데이터
```

실패한 상태는 다음과 같다.

```text
운동 시작 후 루틴 관리 화면이 나온다.
운동 시작 후 세트 입력 UI가 없다.
루틴 수정이 과거 운동 기록을 바꾼다.
운동 중 수정이 원본 루틴을 바꾼다.
workout_sessions 새 부모 테이블을 임의로 만든다.
체중/식사를 위해 새 독립 테이블을 임의로 만든다.
기존 Personal OS 기록 구조를 우회한다.
```
