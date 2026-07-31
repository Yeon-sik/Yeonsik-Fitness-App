# Fitness App | 상세 운동 기록을 소유하고 Personal OS에 요약을 공유하는 Android 앱

> Fitness App은 운동 루틴·세션·종목·세트를 로컬에서 빠르게 기록하고, 완료된 운동의 요약만 Personal OS와 공유하는 네이티브 Android 애플리케이션이다.

| 항목 | 내용 |
| --- | --- |
| 프로젝트 형태 | 개인 프로젝트 |
| 담당 범위 | Android UI, SQLite 데이터 계층, 운동 계약, Supabase Auth·동기화 |
| 현재 상태 | 1.0 코드 후보, 로컬 단위 테스트·debug APK 통과, 배포 게이트 미검증 |
| 문서 기준 | `84b8cd5ac077` 기반 `feat/fitness-ux-restructure` dirty working tree |
| 주요 기술 | Java 17, Android View, SQLiteOpenHelper, Supabase REST/Auth, Gradle |
| Repository | [Yeon-sik/Yeonsik-Fitness-App](https://github.com/Yeon-sik/Yeonsik-Fitness-App) |
| 상세 문서 | [Project_Detail.md](./Project_Detail.md) |

## 1. 30초 요약

- **문제**: 운동 중에는 종목·세트·중량·횟수를 빠르게 기록해야 하지만, 상위 Personal OS에는 상세 세트보다 일상 요약이 필요하다.
- **해결**: Fitness App이 상세 데이터를 SQLite에 소유하고, 완료된 세션만 Fitness Record Contract v1 요약으로 승격해 Supabase에 동기화한다.
- **현재 결과**: 루틴, 세션, 종목별 기록 유형, 기록 달력, 주간 지표, 인증·수동 동기화 코드가 구현돼 있다.
- **검증 경계**: 2026-07-27 `testDebugUnitTest assembleDebug`가 통과했지만 운영 RLS, 두 계정 격리, 서명 release, 실기기 교차 앱 동기화는 확인되지 않았다.

## 2. 문제와 해결

| 사용자 문제 | 해결 방식 | 현재 근거 |
| --- | --- | --- |
| 운동 중 상세 세트 입력이 느리다 | 루틴에서 세션을 만들고 종목별 세트를 직접 기록한다 | `ui/WorkoutSessionScreen.java`, `ui/WorkoutExerciseDetailScreen.java` |
| 운동 유형마다 입력 필드가 다르다 | 6개 record type 계약으로 중량·횟수·시간 조합을 구분한다 | `data/FitnessRecordContract.java` |
| Personal OS에 상세 데이터까지 노출하면 경계가 흐려진다 | 완료 세션의 부위·종류 요약만 `scope=both`로 발행한다 | `data/FitnessRepository.java` |
| 네트워크 실패가 운동 기록을 막을 수 있다 | SQLite를 우선 사용하고 설정 화면에서 수동 동기화한다 | `data/FitnessDatabaseHelper.java`, `sync/SupabaseSyncManager.java` |

## 3. 핵심 기능과 결과

| 영역 | 구현 결과 | 검증 수준 |
| --- | --- | --- |
| 운동 루틴 | 루틴 생성·편집, 종목 선택, 최근 운동 표시 | 저장소 검증 |
| 세션 기록 | 운동 시작·완료, 종목·세트 상세, RPE·휴식 등 계약 필드 | 저장소·단위 테스트 |
| 기록 분석 | 달력, 주간 볼륨, 종목 최고 기록·추정 1RM 제한 적용 | 저장소·단위 테스트 |
| Personal OS 공유 | 완료 운동의 카테고리 코드·계약 버전·요약 범위 저장 | 저장소·계약 테스트 |
| 인증·동기화 | Supabase Auth access/refresh token, REST 동기화, 수동 실행 | 저장소 검증, 운영 미검증 |
| Android 보안 | Keystore 토큰 저장, 백업·cleartext 차단, release 서명 변수 요구 | 저장소 검증 |

## 4. 핵심 사용 흐름

```text
앱 실행
  → SQLite에서 루틴·최근 기록 로드
  → 루틴 선택 또는 직접 세션 시작
  → 종목별 세트 기록
  → 세션 완료
  → 상세 데이터는 Fitness App에 유지
  → 요약에 contract_version·category_codes·scope=both 기록
  → 로그인 상태에서 Supabase 수동 동기화
```

## 5. 검증 현황

| 검증 항목 | 상태 | 확인일 | 근거 |
| --- | --- | --- | --- |
| Java 단위 테스트 | 통과 | 2026-07-27 | `gradlew testDebugUnitTest` |
| Android debug APK | 통과 | 2026-07-27 | `gradlew assembleDebug` |
| release 빌드 | 미실행 | 2026-07-27 | 서명 환경변수 4개 필요 |
| 운영 Supabase·RLS | 미검증 | 2026-07-27 | 실제 프로젝트 적용 확인 필요 |
| 두 계정 격리 | 미검증 | 2026-07-27 | 운영 Auth 계정 2개 필요 |
| 실기기·교차 앱 동기화 | 미검증 | 2026-07-27 | Android와 Personal OS 동시 검증 필요 |

Gradle 9 빌드는 통과했지만 Gradle 10과 호환되지 않을 수 있는 deprecated feature 경고가 남아 있다.

## 6. 현재 한계와 다음 단계

- **현재 한계**: 로컬 build 성공은 배포 서명, 운영 RLS, 스토어 정책, 실제 기기 동작을 증명하지 않는다.
- **지금 해야 하는 한 단계**: 같은 운영 Supabase에 두 계정을 만들고 Fitness App 상세 행과 Personal OS 요약 행의 소유권 격리를 검증한다.
- **다음 릴리스 게이트**: Android release signing, 실기기 로그인·동기화, 개인정보·데이터 삭제 절차다.
- **범위 밖**: AI 운동 처방, 의료 판단, 자동 센서 연동은 현재 1.0 계약에 포함되지 않는다.

## 7. 관련 문서

- [README](../README.md)
- [프로젝트 상세](./Project_Detail.md)
- [Personal OS의 Fitness Record Contract](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [통합 릴리스 준비 기준](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
