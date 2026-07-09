# Original OS Spec

## 1. Product Identity

개인 OS는 메모, 할 일, 빠른 기록, 도메인 요약을 한 화면에서 연결하는 개인용 컨트롤 타워다.

기존 메모앱은 개인 OS의 원본 앱이다. 이 앱은 빠른 입력, 오늘의 상태 확인, 최근 기록 조회, 여러 도메인 앱으로 이어지는 상위 레이어 역할을 맡는다.

OS가 해야 할 것:

- 빠른 메모와 빠른 할 일을 즉시 저장한다.
- 오늘 할 일, 최근 메모, 최근 운동, 금융 요약을 한곳에서 조회한다.
- 각 도메인 앱이 만든 원본 데이터를 summary view, timeline view로 읽는다.
- 사용자가 오늘 무엇을 해야 하는지, 최근에 무엇을 했는지 빠르게 판단하게 한다.
- Supabase 공통 DB에서 도메인별 테이블을 연결하는 기준 앱 역할을 한다.

OS가 하지 말아야 할 것:

- 모든 세부 기능을 직접 품지 않는다.
- 운동 세트 수정, 금융 거래 세부 수정 같은 도메인 전용 편집을 OS에 넣지 않는다.
- 모든 데이터를 하나의 범용 records 테이블에 몰아넣지 않는다.
- MVP 단계에서 거래소 API, 온체인 추적, 이미지 첨부, AI 임베딩을 시작하지 않는다.

## 2. Current App Snapshot

현재 기술 스택:

- Desktop: Tauri v2
- Frontend: React 18 + TypeScript + Vite
- Styling: Tailwind CSS
- Cloud sync: Supabase Postgres + Supabase Realtime, `@supabase/supabase-js`
- Local storage: 현재는 browser `localStorage`
- Package manager: 현재 `package-lock.json`이 있어 npm 기반 흔적이 있으며, 장기적으로는 pnpm 전환 가능
- Test: Vitest

Supabase 연결 위치:

- `src/lib/sync/supabaseSyncClient.ts`: 실제 Supabase client 생성, pull, push, realtime, heartbeat 담당
- `src/lib/sync/syncClientFactory.ts`: Supabase 설정이 있으면 Supabase client, 없으면 local-only client 선택
- `src/lib/config/runtimeConfig.ts`: Supabase URL, anon key, user id 설정 로드 및 저장
- `src-tauri/src/lib.rs`: Tauri 런타임에서 `.env`, `yeonsik-note.env`, config dir 환경값 로드
- `supabase/schema.sql`: 현재 원격 동기화용 DB 스키마 기준

주요 폴더/파일 역할:

- `src/app/App.tsx`: 화면 라우팅에 가까운 최상위 UI 조립
- `src/app/useLocalSyncMemo.ts`: 앱 상태, local-first 저장, Supabase pull/push/realtime 조율
- `src/features/notes/`: 메모 UI와 메모 엔티티 서비스
- `src/features/tasks/`: 체크리스트 UI와 할 일 엔티티 서비스
- `src/features/quick-capture/`: 빠른 입력 패널, parser, 저장 연결
- `src/features/fitness/`: 현재 운동/식사/체중 기록 UI와 엔티티 서비스
- `src/features/records/`: 일별 기록 조회, 달력, aggregation
- `src/lib/storage/`: localStorage 기반 snapshot 저장 어댑터
- `src/lib/sync/`: sync client 인터페이스, merge, Supabase 구현
- `supabase/`: schema와 migration 초안

현재 메모/할 일/빠른 기록 저장 흐름:

- 앱 시작 시 `useLocalSyncMemo`가 localStorage snapshot을 먼저 읽는다.
- Supabase 설정과 네트워크가 있으면 `pull()`로 원격 row를 가져와 로컬 snapshot과 merge한다.
- 메모 생성/수정/삭제는 `noteService`에서 엔티티를 만들고 `useLocalSyncMemo` 상태에 반영한다.
- 할 일 생성/수정/삭제는 `taskService`에서 엔티티를 만들고 `useLocalSyncMemo` 상태에 반영한다.
- Quick Capture는 `useQuickCapture`에서 입력을 `memo` 또는 `task`로 파싱한다.
- Quick Capture memo는 `addNoteForDate`, task는 `addTask`로 들어간다.
- 상태 변경 후 400ms debounce로 localStorage에 저장하고, 이후 Supabase `push()`가 각 테이블에 upsert한다.
- 삭제는 hard delete가 아니라 `deleted_at` 기반 soft delete로 전파한다.
- Realtime은 다른 기기에서 온 row를 받아 현재 snapshot에 반영한다.

반드시 보존해야 할 기존 기능:

- 메모 CRUD
- 할 일 CRUD, 완료, 순서, 날짜/시간/오늘 계획 필드
- Quick Capture memo/task 입력
- local-first 실행
- offline 작성 후 online push
- Supabase pull/push/realtime
- device heartbeat와 active device 표시
- soft delete 기반 삭제 전파
- Tauri tray, autostart, quick capture shortcut 관련 기존 동작

## 3. Core Architecture

개인 OS는 Supabase 하나를 공통 DB로 사용한다. 단, 앱별 코드는 분리 가능해야 한다. MemoNote는 OS 원본 앱이고, 운동 앱과 금융 Hub는 별도 root 또는 별도 앱으로 개발할 수 있다.

테이블은 도메인별로 분리한다. notes/tasks, fitness, finance, dashboard/view 계층을 명확히 나눈다. OS는 모든 테이블을 직접 소유하지 않고, 빠른 기록과 통합 조회를 담당한다.

도메인 앱은 세부 기록의 원본 데이터 소유자다. 운동 앱은 운동 세션, 운동 종목, 세트, 체중, 식단의 세부 기록을 소유한다. 금융 Hub는 계좌, 자산, 보유량, 거래, 스냅샷을 소유한다.

연결 방식은 양방향 복제가 아니다. 하나의 원본 데이터가 있고, OS는 그 데이터를 view, summary, timeline으로 읽는다. OS에 필요한 요약은 view 또는 계산된 summary 레이어로 제공한다.

## 4. Fast Record to Detail Record Rules

OS의 빠른 기록은 기존 방식으로 먼저 저장한다. 입력 속도를 위해 구조화가 불완전해도 저장을 막지 않는다.

운동, 금융처럼 구조화가 필요한 기록은 이후 각 도메인 테이블과 연결할 수 있어야 한다. 이때 원본 빠른 기록을 삭제하지 않는다. 빠른 기록은 사용자가 처음 입력한 원문이자 감사 trail이다.

연결 패턴:

- `linked_entity_type`: 연결된 도메인 엔티티 타입. 예: `workout_session`, `finance_transaction`
- `linked_entity_id`: 연결된 도메인 엔티티 id
- `created_from_quick_record_id`: 도메인 세부 기록이 어떤 빠른 기록에서 만들어졌는지 추적하는 id

권장 흐름:

- OS Quick Capture가 빠른 기록을 저장한다.
- 사용자가 운동 앱 또는 금융 Hub에서 해당 빠른 기록을 구조화한다.
- 도메인 앱은 세부 테이블에 원본 row를 만든다.
- 빠른 기록 row에는 `linked_entity_type`, `linked_entity_id`를 채운다.
- 도메인 row에는 `created_from_quick_record_id`를 채운다.
- 세부 앱에서 직접 입력한 기록은 빠른 기록 없이도 원본 도메인 row로 저장된다.
- OS timeline/summary는 빠른 기록과 도메인 원본 row를 함께 조회한다.

상위/하위 레이어 개념:

- 기존 MemoNote/OS: 빠른 입력, 통합 조회, 오늘의 컨트롤 타워
- 도메인 앱: 세부 생성, 수정, 삭제, 검증, 전문 UI
- 도메인 앱에서 직접 만든 기록도 OS timeline/summary에 보여야 한다.

## 5. Data Ownership Rules

OS/MemoNote 소유:

- 기존 `notes`
- 기존 `tasks`
- OS quick record 계층
- OS dashboard preference
- OS activity timeline 조회 규칙

운동 앱 소유:

- `workout_*`
- `body_metrics`
- `meals`
- 운동 세션, 운동 종목, 세트, 체중, 식단 세부 입력/수정/삭제

금융 Hub 소유:

- `finance_*`
- 계좌, 자산, 보유량, 거래, 스냅샷 세부 입력/수정/삭제

OS는 도메인 데이터를 직접 세부 수정하지 않는다. OS는 summary view, timeline view, recent activity view를 통해 조회하고, 필요하면 도메인 앱으로 이동시키는 역할만 한다.

## 6. Proposed DB Domains

`core` 후보:

- `devices`: 기기 식별, heartbeat, active device 판단
- `users` 또는 config user profile: Auth 전환 전후의 사용자 기준
- `quick_records`: 빠른 입력 원문, 연결 상태, source app, device id
- `activity_events`: timeline 구성용 이벤트 원본 또는 materialized event

`notes/tasks` 후보:

- `notes`: 메모
- `tasks`: 할 일, 완료 상태, 계획일, 마감일
- `task_events`: 필요 시 완료/재개 기록

`fitness` 후보:

- `workout_sessions`: 운동 1회 단위
- `workout_exercises`: 세션 안의 운동 종목
- `workout_sets`: 세트, 반복 수, 무게, 완료 여부
- `body_metrics`: 체중 등 신체 지표
- `meals`: 식단 텍스트 기록, 칼로리, 단백질 등 수동 입력

`finance` 후보:

- `finance_accounts`: 계좌, 거래소, 지갑, 현금 계정
- `finance_assets`: KRW, USD, BTC, ETH, 주식, 기타 자산 정의
- `finance_holdings`: 계좌별 현재 보유량
- `finance_transactions`: 입금, 출금, 매수, 매도, 이체, 수동 조정
- `finance_snapshots`: 특정 시점의 평가액, 순자산, 요약 스냅샷

`dashboard/views` 후보:

- `daily_dashboard_view`: 오늘 할 일, 최근 메모, 운동 여부, 금융 요약
- `activity_timeline_view`: 빠른 기록과 도메인 이벤트를 시간순으로 조회
- `weekly_review_view`: 주간 완료, 운동 빈도, 지출/자산 변화 요약
- `fitness_summary_view`: 최근 운동, 주간 운동량, 체중 변화
- `finance_summary_view`: 순자산, 계좌 요약, 최근 거래

## 7. Fitness Integration

Fitness 앱은 Android 전용 MVP로 시작한다. iPhone 확장 가능성을 고려해 DB와 API는 모바일 플랫폼에 종속되지 않게 설계한다.

주요 테이블 후보:

- `workout_sessions`: 날짜, 시작/종료 시각, 운동 유형, 메모
- `workout_exercises`: 세션별 운동 종목, 순서, 부위, 이름
- `workout_sets`: 세트 번호, 반복 수, 무게, 완료 여부, 휴식 시간
- `body_metrics`: 체중, 측정일, 기타 신체 지표
- `meals`: 식단 텍스트, 칼로리, 단백질, 탄수화물, 지방

Fitness 앱이 담당할 것:

- 어떤 운동을 했는지 기록한다.
- 몇 세트로 수행했는지 기록한다.
- 어느 정도의 무게와 반복 수로 수행했는지 기록한다.
- 체중과 식단을 기록한다.
- 식단은 사진 없이 텍스트 형식으로만 기록한다.
- 기존의 체중 및 식단 빠른 기록 테이블은 유지하고, 필요 시 구조화된 fitness 테이블과 연결한다.

OS에서 조회할 것:

- 오늘 운동 여부
- 최근 운동
- 주간 운동 횟수와 주요 운동 요약
- 최근 체중 변화
- 최근 식단 입력 여부

OS에서 하지 않을 것:

- 세트별 무게 수정
- 운동 루틴 상세 편집
- 식단 영양소 상세 보정
- 운동 앱이 소유해야 할 검증 로직

## 8. Finance Integration

금융 Hub는 별도로 개발 중인 도메인 앱이며, 개인 OS는 연결고리만 확보한다. 모바일과 PC를 모두 지원하는 방향으로 설계한다.

MVP는 수동 입력 기반으로 시작한다. 거래소 API 자동 연동, 은행 자동 연동, 온체인 추적은 MVP 이후다.

주요 테이블 후보:

- `finance_accounts`: 계좌, 거래소, 지갑, 현금 계정
- `finance_assets`: 화폐, 코인, 주식, 기타 자산 master
- `finance_holdings`: 계좌별 현재 보유량
- `finance_transactions`: 수동 입력 거래, 이체, 매수, 매도, 입출금
- `finance_snapshots`: 시점별 평가액, 순자산, 요약

Finance Hub가 담당할 것:

- 계좌와 자산 등록
- 수동 거래 입력
- 보유량과 평가액 관리
- 스냅샷 생성
- 추후 API 연동

OS에서 조회할 것:

- 순자산
- 계좌 요약
- 최근 거래
- 최근 스냅샷
- 주간 또는 월간 변화 요약

OS에서 하지 않을 것:

- 거래 상세 수정
- 계좌 reconciliation
- 거래소 API 키 관리
- 온체인 주소 추적
- 자동 평가액 계산의 원본 로직 소유

## 9. OS Dashboard Scope

OS Dashboard MVP 범위:

- 빠른 기록
- 오늘 할 일
- 최근 메모
- 최근 운동 요약
- 금융 요약
- activity timeline
- weekly review

Dashboard 원칙:

- 첫 화면은 기록 입력과 오늘 판단에 집중한다.
- 상세 수정은 각 도메인 앱으로 보낸다.
- 조회는 가볍고 빠르게 유지한다.
- 느린 계산은 summary table 또는 view로 분리한다.
- offline 상태에서도 기존 메모와 할 일은 유지되어야 한다.

## 10. Non-goals

- OS에 모든 세부 수정 기능 넣지 않기
- 기존 메모앱 전체 리팩토링 금지
- 모든 데이터를 하나의 records 테이블에 몰아넣지 않기
- 운동/금융 자동화부터 시작하지 않기
- 거래소 API는 MVP 이후
- 온체인 추적은 MVP 이후
- 이미지 첨부는 MVP 이후
- AI 임베딩은 MVP 이후
- Supabase Auth/RLS 전환은 별도 구현 단계에서 진행
- DB migration은 이 문서 단계에서 작성하지 않기

## 11. Roadmap

Phase 0: 문서화

- 개인 OS 정체성, 데이터 소유권, 도메인 연결 규칙을 문서화한다.
- 기존 메모앱의 원본 역할을 고정한다.

Phase 1: quick_records 정리

- 기존 빠른 기록 개념을 명확히 정의한다.
- `linked_entity_type`, `linked_entity_id`, `created_from_quick_record_id` 연결 규칙을 구현 가능한 수준으로 정리한다.

Phase 2: fitness DB 설계

- Android Fitness MVP에 필요한 테이블과 관계를 설계한다.
- 기존 체중/식단 기록과 신규 fitness 테이블의 연결 방식을 결정한다.

Phase 3: fitness app MVP

- 별도 앱/root에서 Android 전용 MVP를 만든다.
- 운동 세션, 운동 종목, 세트, 체중, 식단 텍스트 기록을 지원한다.

Phase 4: OS fitness summary 연결

- OS에서 오늘 운동 여부, 최근 운동, 주간 운동 요약을 조회한다.
- 세부 수정은 Fitness 앱으로 이동시킨다.

Phase 5: finance DB 설계

- 금융 Hub용 계좌, 자산, 보유량, 거래, 스냅샷 테이블을 설계한다.
- 수동 입력 MVP 기준으로 시작한다.

Phase 6: finance hub MVP

- 모바일과 PC에서 수동 입력 가능한 금융 Hub를 만든다.
- 자동 연동 없이 계좌/자산/거래/스냅샷을 관리한다.

Phase 7: OS finance summary 연결

- OS에서 순자산, 계좌 요약, 최근 거래/스냅샷을 조회한다.
- 세부 수정은 Finance Hub로 이동시킨다.

Phase 8: activity timeline / weekly review 고도화

- 빠른 기록, notes/tasks, fitness, finance 이벤트를 timeline으로 묶는다.
- 주간 회고 view를 만든다.

## 12. Codex Working Rules

- 새 앱은 별도 폴더/root에서 개발한다.
- 새 Codex 세션은 작업 전에 이 문서를 먼저 읽는다.
- 코드 수정 전 반드시 범위를 확인한다.
- 기존 기능 삭제 금지.
- 대규모 리팩토링 금지.
- DB migration은 구현 단계에서만 별도 작성한다.
- 문서화 단계에서는 코드와 schema를 수정하지 않는다.
- 기존 local-first, soft delete, device heartbeat, realtime 동작을 깨지 않는다.
- Supabase Auth/RLS 전환은 별도 계획과 backfill 전략 없이는 적용하지 않는다.
- 작업 후 요약은 `Changed / DB / Test / Risk` 형식을 사용한다.

작업 후 보고 형식:

```text
Changed:
- ...

DB:
- ...

Test:
- ...

Risk:
- ...
```

## Release Notes

아직 구현 변경은 없습니다. 이 문서는 개인 OS 확장을 위한 기준 설계 문서입니다.
