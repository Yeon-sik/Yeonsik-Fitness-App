# Fitness App | Project Detail

> 이 문서는 Fitness App의 네이티브 Android 구조, 로컬 우선 기록 경계, Personal OS 요약 계약과 릴리스 전 검증 항목을 설명한다.

| 항목 | 내용 |
| --- | --- |
| 문서 상태 | Active draft |
| 적용 범위 | `feat/fitness-ux-restructure`의 현재 dirty working tree |
| 최종 갱신 | 2026-07-27T00:09:00+09:00 |
| 기준 커밋 | `84b8cd5ac07771fee655835f287ca815b9657c05` |
| 진실 원천 | Android 코드, SQLite 스키마, 계약 테스트, Gradle 결과 |

## 1. 문서 목적과 범위

### 포함

- Java Android View 기반 화면 흐름
- SQLite 운동·루틴 데이터 모델
- Fitness Record Contract v1
- Supabase Auth와 REST 동기화
- Android 보안·서명 설정과 로컬 검증

### 제외

- Personal OS 전체 기능
- 운영 Supabase 적용 완료 주장
- 의료·운동 처방 정확도 주장
- Play Store 배포 완료 주장

## 2. 시스템 아키텍처

```text
MainActivity
  → ScreenHost
  → Home / Workout / Session / Records / Settings screens
  → FitnessRepository / RoutineRepository / ExerciseMasterRepository
  → FitnessDatabaseHelper
  → SQLite

Settings
  → SupabaseAuthManager
  → SecureTokenStore
  → SupabaseSyncManager
  → Supabase Auth / REST
```

코드 그래프는 `FitnessUi` 공통 View 생성기와 `FitnessRepository`를 가장 높은 fan-in 경계로 보여 준다. 실제 기능 군집은 UI 렌더링, 세션·기록 저장, 루틴 편집, DB 마이그레이션, 인증·동기화로 분리된다.

### 컴포넌트 책임

| 컴포넌트 | 책임 | 실패 시 영향 |
| --- | --- | --- |
| `MainActivity`, `ScreenHost` | 앱 생명주기와 화면 전환 | 전체 UI 사용 불가 |
| `ui/*Screen` | 상태 표시와 사용자 입력 | 해당 운동 흐름 사용 불가 |
| `FitnessRepository` | 세션·종목·세트·요약 규칙 | 기록 무결성 손상 |
| `RoutineRepository` | 루틴과 종목 구성 | 반복 운동 흐름 손상 |
| `FitnessDatabaseHelper` | SQLite 생성·업그레이드 | 로컬 데이터 접근 불가 |
| `SupabaseAuthManager` | 로그인·토큰 갱신 | 원격 동기화 불가 |
| `SupabaseSyncManager` | REST pull·push와 충돌 대상 | 교차 기기 데이터 지연 |

## 3. 데이터 모델과 불변식

| 데이터 | 소유 경계 | 공유 여부 |
| --- | --- | --- |
| `workout_records` | 공통 요약 계약 | 완료 후 Personal OS와 공유 가능 |
| `workout_exercises` | Fitness App 상세 | Personal OS에 직접 노출하지 않음 |
| `workout_sets` | Fitness App 상세 | Personal OS에 직접 노출하지 않음 |
| 루틴·루틴 종목 | Fitness App | 앱 내부 사용 |
| exercise master JSON | 저장소 asset 원본 | 빌드 시 Android asset으로 복사 |

핵심 불변식은 다음과 같다.

1. 상세 운동은 Fitness App이 소유하고 Personal OS에는 완료 요약만 보낸다.
2. 공유 요약은 `contract_version`과 안정된 `category_codes`를 가진다.
3. 미완료 세션은 `scope=both`로 승격하지 않는다.
4. record type별 필수 중량·횟수·시간 조합을 검증한다.
5. 추정 1RM과 외부 중량 볼륨은 일반 `weight_reps`에만 적용한다.
6. 원격 데이터는 Supabase Auth 사용자 소유권과 RLS를 전제로 한다.

## 4. 핵심 기술 의사결정

### 결정 1. Java View·SQLite 구조 유지

- **상황**: 현재 앱은 단일 Android 모듈의 Java·programmatic View·SQLiteOpenHelper 구조다.
- **선택**: 1.0 범위에서 Kotlin·Compose·Room 전면 재작성 대신 기존 경계 안에서 기능을 완성한다.
- **결과**: 변경 범위와 학습 비용을 줄이고 현재 빌드 체인을 유지한다.
- **재검토 조건**: 화면 복잡도나 데이터 마이그레이션 비용이 현재 구조의 생산성을 지속적으로 초과할 때다.

### 결정 2. 완료 요약만 Personal OS에 공유

- **상황**: 두 앱이 같은 상세 테이블을 수정하면 소유권과 UX 책임이 충돌한다.
- **선택**: Fitness App이 상세를 소유하고 완료 시 부위·유형 요약을 공유한다.
- **결과**: 상위 OS는 판단·타임라인, Fitness App은 상세 입력이라는 경계를 유지한다.

### 결정 3. 서버 SDK 없이 Auth·REST 직접 연동

- **상황**: 현재 Java 앱은 추가 프레임워크 없이 작은 의존성 표면을 유지한다.
- **선택**: `HttpURLConnection` 기반 Auth·REST와 명시적 JSON 매핑을 사용한다.
- **비용**: 토큰 갱신·오류·행 매핑을 직접 관리해야 한다.
- **재검토 조건**: API 범위가 늘어 수동 매핑과 재시도 로직이 병목이 될 때다.

## 5. 외부 연동과 실패 경계

| 대상 | 목적 | 인증·비밀값 | 실패 처리 | 현재 검증 |
| --- | --- | --- | --- | --- |
| Supabase Auth | 로그인·토큰 갱신 | URL·anon key·사용자 자격 증명 | 세션 오류 반환 | 코드 검증 |
| Supabase REST | 공통 기록 동기화 | access token | 수동 동기화 결과 표시 | 코드 검증 |
| Android Keystore | 세션 토큰 암호화 | 기기 키 | 저장·복호화 실패 처리 | 코드 검증 |
| Personal OS | 완료 요약 소비 | 같은 사용자 계약 | 상세는 공유하지 않음 | 저장소 간 계약 존재, 운영 미검증 |

## 6. 데이터 보호와 보안

- 비밀번호는 저장하지 않고 access/refresh token은 Android Keystore 기반 저장소를 사용한다.
- Android backup과 cleartext traffic을 비활성화한다.
- 사용자 ID를 임의 입력으로 받지 않고 Auth 세션에서 결정한다.
- release 빌드는 서명 환경변수가 없으면 실패하도록 구성한다.
- 서비스 역할 키는 앱에 포함하지 않는다.
- 실제 RLS 정책 적용과 계정 간 격리는 운영 Supabase에서 별도 검증해야 한다.

## 7. 테스트와 검증 전략

| 계층 | 도구 | 대상 | 현재 결과 |
| --- | --- | --- | --- |
| 계약 단위 테스트 | JUnit | record type, category code, 계약 버전 | 통과 |
| repository 단위 테스트 | JUnit | 기록 규칙과 계산 | 통과 |
| Android debug build | Gradle 9 | Java·리소스·manifest·APK | 통과 |
| release build | Gradle | R8·resource shrink·서명 | 미실행 |
| 운영 통합 | Supabase·두 계정 | Auth·RLS·동기화 | 미검증 |
| 실기기 E2E | Android + Personal OS | 완료 세션 교차 앱 확인 | 미검증 |

검증 명령은 `gradlew testDebugUnitTest assembleDebug`이며 2026-07-27T00:09:00+09:00에 성공했다.

## 8. 배포·운영·복구

```text
기능 브랜치 검토
  → 단위 테스트 + debug APK
  → 운영 migration·RLS 사전 검증
  → release 서명 변수 주입
  → minified release APK 생성
  → 실기기 로그인·기록·동기화 smoke test
  → 단계 배포
```

- 운영 DB 변경 전 백업과 legacy user backfill 검토가 필요하다.
- release signing 값은 GitHub Environment 또는 안전한 로컬 비밀 저장소에서만 주입한다.
- 앱 롤백과 DB 롤백은 분리한다. RLS·스키마 변경은 사전 백업과 전방 호환 경로가 필요하다.
- `main`에 검토된 문서 변경이 반영되면 Notion 미러를 자동 갱신하며, 수동 `PUBLISH`는 첫 발행과 복구에만 사용한다.

## 9. 한계, 기술 부채, 다음 단계

| 우선순위 | 항목 | 영향 | 다음 행동 |
| --- | --- | --- | --- |
| P0 | 운영 RLS·두 계정 격리 미검증 | 개인 운동 데이터 노출 위험 | 실제 Auth 계정 2개로 CRUD 격리 |
| P0 | release 서명·실기기 미검증 | 배포 불가 | 서명 키 주입 후 설치 smoke test |
| P1 | 직접 REST 매핑 | API 확장 시 유지보수 비용 | 오류·재시도 계약을 테스트로 고정 |
| P1 | Gradle deprecated feature 경고 | Gradle 10 업그레이드 위험 | warning 원인 확인 후 빌드 스크립트 갱신 |

지금 해야 하는 단 하나는 운영 RLS 격리와 완료 세션의 교차 앱 요약 흐름을 같은 테스트에서 확인하는 것이다.

## 10. 관련 문서

- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [Fitness Record Contract](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [Release Readiness](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
