# Fitness App | Project Detail

> 이 문서는 Fitness App `main`의 네이티브 Android 구조, 로컬 데이터 모델, 수동 동기화 경계와 검증 결과를 설명한다.

| 항목 | 내용 |
| --- | --- |
| 문서 상태 | Active |
| 적용 범위 | 원격 `main` |
| 최종 갱신 | 2026-07-27 |
| 앱 코드 기준 | `3e6a440294bb464c5c23349717ee1729559122ad` |
| 문서 진실 원천 | Git 저장소의 `docs/Project_Detail.md` |

## 1. 문서 목적과 범위

### 포함

- 운동 루틴, 세션, 종목, 세트, 식단, 체중 기록
- Android SQLite schema와 repository 경계
- Supabase REST 기반 수동 push/pull
- debug APK 빌드와 현재 경고

### 제외

- 실기기 UX 완료, 운영 Supabase 적용, 사용자 데이터 격리 완료 주장
- 자동 동기화·충돌 해결 완료 주장
- AI, 이미지, Health API, 자동 운동 판정

## 2. 문제 맥락과 제약

운동 중에는 연결 상태보다 빠른 입력과 저장 성공이 우선이다. 1인 MVP이므로 Compose·복잡한 서버 계층을 새로 도입하지 않고 Java Android SDK, SQLite, 작은 repository 구조로 동작 경로를 먼저 완성했다.

## 3. 범위와 구현 현황

| 기능 | 구현 상태 | 검증 수준 | 근거 | 남은 위험 |
| --- | --- | --- | --- | --- |
| 루틴·세션·세트 | 구현 완료 | 컴파일·빌드 | Java source, APK build | 실기기 미검증 |
| 식단·체중 | 구현 완료 | 컴파일·빌드 | repository·schema | 입력 정확성 테스트 없음 |
| SQLite migration | 구현 | 코드 검토 | `FitnessDatabaseHelper` | 업그레이드 E2E 없음 |
| Supabase 수동 sync | 구현 | 미검증 | `SupabaseSyncManager` | 인증·충돌·네트워크 오류 |
| 단위 테스트 | 미구현 | `NO-SOURCE` | Gradle 결과 | 회귀 탐지 부족 |

## 4. 시스템 아키텍처

```text
MainActivity
  → 화면 상태와 사용자 입력
  → FitnessRepository / RoutineRepository
  → FitnessDatabaseHelper
  → SQLite

설정 화면의 수동 동기화
  → SupabaseSyncManager
  → Supabase REST
```

codebase-memory-mcp의 `main` 기반 moderate 그래프는 문서 런타임을 포함해 828개 노드와 2,428개 엣지를 식별했다. 호출 중심은 `MainActivity` 렌더링, `FitnessRepository` 데이터 조작, SQLite schema/migration, 수동 sync로 모인다.

### 컴포넌트 책임

| 컴포넌트 | 책임 | 실패 시 영향 |
| --- | --- | --- |
| `MainActivity` | 탭·세션·루틴·설정 UI 조립 | 주요 화면 사용 불가 |
| `FitnessRepository` | 기록 CRUD와 세션 집계 | 운동·식단·체중 기록 불가 |
| `RoutineRepository` | 루틴과 종목 순서 관리 | 루틴 시작 불가 |
| `FitnessDatabaseHelper` | SQLite 생성·업그레이드 | 로컬 데이터 접근 불가 |
| `SupabaseSyncManager` | 원격 push/pull | 원격 동기화만 실패 |

## 5. 도메인 모델과 불변식

| 엔터티 | 관계 | 저장소 |
| --- | --- | --- |
| workout record | 세션 상위 기록 | SQLite |
| workout exercise | record의 정렬된 종목 | SQLite |
| workout set | exercise의 정렬된 세트 | SQLite |
| routine / routine exercise | 기본 운동 구성 | SQLite |
| meal / weight record | 날짜별 생활 기록 | SQLite |

1. 세트의 `workout_exercise_id`는 상위 운동 종목을 가리킨다.
2. 운동 종목은 `record_id`, 세트는 `set_index` 순서를 가진다.
3. 삭제는 `deleted_at` 기반으로 하위 기록에도 전파한다.
4. 완료 세트의 중량×반복 합계가 세션 볼륨 집계에 사용된다.

## 6. 핵심 기술 의사결정

### 결정 1. SQLite local-first

- **선택**: 모든 핵심 입력은 먼저 SQLite repository를 통과한다.
- **근거**: 운동 중 네트워크 대기와 원격 장애를 핵심 기록에서 제거한다.
- **비용과 위험**: 다중 기기 충돌 해결 정책이 별도로 필요하다.

### 결정 2. 수동 동기화

- **선택**: 설정 화면에서 사용자가 명시적으로 push/pull을 실행한다.
- **근거**: MVP에서 백그라운드 작업과 lifecycle 복잡도를 줄인다.
- **비용과 위험**: 사용자가 동기화를 잊을 수 있고 장애 복구 UX가 제한적이다.

## 7. 외부 연동과 실패 경계

| 연동 대상 | 목적 | 인증/비밀값 | 실패 처리 | 실환경 확인 |
| --- | --- | --- | --- | --- |
| SQLite | 로컬 기록 | 앱 내부 DB | repository 예외 | debug build만 확인 |
| Supabase REST | 원격 동기화 | URL·anon key·user ID 로컬 설정 | sync 오류 반환 | 미검증 |
| Notion API | 문서 미러 | GitHub Environment secrets | workflow 실패 | 시크릿 입력 전 |

현재 `main`의 원격 소유권은 설정된 `userId`에 의존한다. 인증 세션과 RLS 격리를 증명하는 테스트가 없으므로 운영 보안 완료로 간주하지 않는다.

## 8. 데이터 보호와 보안

- 실제 Notion token과 page ID는 저장소에 커밋하지 않는다.
- Supabase 설정 저장 방식은 운영 비밀 저장소로 검증되지 않았다.
- 원격 동기화는 사용자별 RLS와 인증 세션이 확인되기 전까지 개인 개발 환경 범위로 제한한다.
- SQLite 원본과 migration을 삭제하는 복구 절차는 자동화하지 않았다.

## 9. 테스트와 검증 전략

| 수준 | 도구 | 현재 상태 |
| --- | --- | --- |
| 컴파일 | javac/Android Gradle Plugin | 통과 |
| 단위 테스트 | Gradle testDebugUnitTest | 테스트 소스 없음 |
| APK | assembleDebug | 통과 |
| 실기기 | 수동 설치·사용 | 미검증 |
| 원격 통합 | Supabase | 미검증 |

빌드에는 Java 8 source/target 및 deprecated API 경고와 Gradle 10 호환성 경고가 남아 있다.

## 10. 배포·운영·복구

```text
기능 브랜치
  → Gradle 검증
  → PR
  → main
  → debug/release APK
  → 실기기 확인
```

- 문서 변경이 `main`에 반영되면 Notion 미러는 자동 갱신된다.
- 첫 발행과 장애 복구는 workflow dispatch의 `PUBLISH` 확인값을 사용한다.
- 앱 데이터 복구는 SQLite 백업·restore 절차가 별도로 필요하다.
- 원격 sync 전에는 로컬 원본 보존과 실패 후 재시도 정책을 검증해야 한다.

## 11. 한계, 기술 부채, 다음 단계

| 우선순위 | 항목 | 영향 | 다음 행동 |
| --- | --- | --- | --- |
| P0 | 단위 테스트 없음 | 계산·migration 회귀 탐지 불가 | repository JVM 테스트 추가 |
| P0 | 인증·RLS 미검증 | 사용자 데이터 격리 불확실 | 실제 두 계정 격리 테스트 |
| P1 | 단일 Activity 집중 | UI 변경 영향 범위 증가 | 화면 책임 단계적 분리 |
| P1 | Java/Gradle deprecation 경고 | 향후 빌드 호환성 | toolchain과 API 갱신 |

지금 해야 하는 한 단계는 세트 볼륨, soft delete 전파, 동기화 row 매핑을 자동 테스트로 고정하는 것이다.

## 12. 관련 문서

- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [Original OS Spec](./ORIGINAL_OS_SPEC.md)
- [Model Skeleton](./fitness-app-model-skeleton.md)
