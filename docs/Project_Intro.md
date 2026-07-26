# Fitness App | 운동·식단·체중을 로컬 우선으로 기록하는 Android 앱

> Fitness App은 루틴을 선택해 운동 세션과 세트를 기록하고, 식단·체중 이력을 날짜별로 관리하는 네이티브 Android MVP다. 네트워크 설정이 없어도 SQLite에 기록하며 Supabase는 수동 동기화 경계로 분리되어 있다.

| 항목 | 내용 |
| --- | --- |
| 프로젝트 형태 | 개인 프로젝트 |
| 담당 범위 | Android UI, 운동 도메인, SQLite, Supabase 수동 동기화 |
| 현재 상태 | MVP |
| 문서 기준 | `main` 기준 앱 커밋 `3e6a440294bb464c5c23349717ee1729559122ad` |
| 주요 기술 | Java, Android SDK, SQLite, Gradle, Supabase REST |
| Demo | 공개 배포 미검증 |
| Repository | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 상세 문서 | [Project_Detail.md](./Project_Detail.md) |

## 1. 30초 요약

- **문제**: 운동 세트, 식사, 체중이 분리되면 한 날짜의 생활 기록과 진행 상황을 함께 보기 어렵다.
- **해결**: 루틴→운동 세션→운동 종목→세트 계층과 식단·체중 기록을 하나의 SQLite 데이터 모델로 구성했다.
- **핵심 결과**: `main`에서 Android debug APK 빌드가 성공했다.
- **기술적 차별점**: 로컬 기록을 기본값으로 두고 Supabase push/pull을 명시적 수동 동기화로 분리했다.

## 2. 문제와 해결

| 사용자 문제 | 해결 방식 | 확인된 가치 |
| --- | --- | --- |
| 운동 중 종목과 세트를 빠르게 기록해야 한다 | 기본 루틴, 세션, 종목, 세트 화면 | 운동 흐름을 계층적으로 기록 |
| 운동·식단·체중 기록을 날짜별로 보고 싶다 | 로컬 SQLite와 기록 탭 | 오프라인에서도 조회 가능 |
| 원격 연결 실패가 기록 자체를 막으면 안 된다 | local-only 기본 상태와 수동 sync | 네트워크와 기록 흐름 분리 |

## 3. 핵심 기능과 결과

| 영역 | 구현 결과 | 근거 |
| --- | --- | --- |
| 운동 | 루틴 생성, 세션 시작·종료, 운동·세트 기록 | `RoutineRepository`, `FitnessRepository` |
| 생활 기록 | 식단과 체중 기록·날짜별 조회 | `FitnessRepository` |
| 저장 | workout·meal·weight·routine SQLite 테이블 | `FitnessDatabaseHelper` |
| 동기화 | 5개 핵심 테이블 수동 push/pull | `SupabaseSyncManager` |
| 빌드 | debug APK 생성 성공 | 2026-07-27 Gradle 실행 |

## 4. 담당 범위와 기여

- **제품**: 수동 기록 중심 MVP와 제외 범위 정의
- **Android**: 단일 Activity의 탭·세션·루틴·설정 흐름 구현
- **데이터**: SQLite schema, repository, soft delete와 legacy migration 구성
- **동기화**: Supabase REST push/pull과 설정 저장 경계 구현
- **문서/자동화**: 저장소 문서를 Notion에 자동 미러하는 워크플로 구성

## 5. 핵심 사용자 흐름

```text
기본 루틴 선택
  → 운동 세션 시작
  → 종목과 세트 기록
  → 세션 종료
  → 날짜별 기록 확인
```

## 6. 핵심 기술적 판단

### 로컬 저장을 필수, 원격 동기화를 선택으로 둔다

- **상황**: 운동 기록은 네트워크 상태와 무관하게 즉시 저장되어야 한다.
- **선택**: SQLite를 진입점으로 사용하고 Supabase 동기화는 설정 화면의 수동 작업으로 분리했다.
- **결과**: 원격 설정이 없어도 핵심 기록 흐름이 유지된다.
- **남은 비용**: 충돌 해결, 인증된 사용자 소유권, 실제 원격 장애 복구는 검증되지 않았다.

## 7. 검증 현황

| 검증 항목 | 상태 | 마지막 확인 | 근거 |
| --- | --- | --- | --- |
| Android debug 빌드 | 통과 | 2026-07-27 | `gradlew.bat testDebugUnitTest assembleDebug` |
| 단위 테스트 | 테스트 소스 없음 | 2026-07-27 | Gradle `NO-SOURCE` |
| Java 컴파일 | 통과, Java 8 target·deprecated API 경고 | 2026-07-27 | Gradle 출력 |
| 실기기 | 미검증 | 2026-07-27 | 설치·사용 흐름 미실행 |
| Supabase 동기화 | 미검증 | 2026-07-27 | 코드 존재만 확인 |

## 8. 현재 한계와 다음 단계

- **현재 한계**: 자동화된 도메인 테스트와 인증 기반 사용자 격리 증거가 없다.
- **다음 한 단계**: repository의 세트 합계·soft delete·동기화 매핑을 JVM 테스트로 고정한다.
- **하지 않는 것**: AI 식단 분석, 이미지 첨부, Health API 연동은 현재 범위가 아니다.

## 9. 관련 문서

- [Project Detail](./Project_Detail.md)
- [README](../README.md)
- [Original OS Spec](./ORIGINAL_OS_SPEC.md)
- [Model Skeleton](./fitness-app-model-skeleton.md)
