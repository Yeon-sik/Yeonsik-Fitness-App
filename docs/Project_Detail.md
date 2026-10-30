# Yeonsik-Fitness-App | Project Detail

2026-10-30 (Asia/Seoul) 갱신. Primary source boundary는 [main@9d05980](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/9d05980b243552c4503743bb2306d510cea03ca7)이다. source 설명과 실제 검증 결과를 구분한다. [빠른 소개](./Project_Intro.md)를 참고한다.

## 1. 문서 목적과 범위

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

상세 기록을 계정 범위의 로컬 DB에 먼저 저장한다. 화면·application·repository를 분리하고 Personal OS에는 완료 운동 요약만 공유하며 Nutrition·PriceTrace를 명시적 계약으로 연결한다.

2026-10-05 기준 이후 main에는 Room schema 54~57, exercise catalog·검수 자산 확대, 운동별 메모·휴식·세트 구조, 식사/외식 component와 영양 publication, PriceTrace 브랜드 identity, Personal OS incremental summary publication, 설정·인증·수동 sync와 cloud readiness가 반영됐다.

미병합 branch, 사용자 미커밋 작업과 명시하지 않은 운영 검증은 기능 완료 근거에 포함하지 않는다.

## 2. 시스템 아키텍처

```text
MainActivity / AppContainer -> ComposeAppScreen
  -> feature ViewModel / state / action
  -> application·repository adapters -> FitnessRoomDatabase (SQLite, schema 57)
shared/commonMain -> account scope / models / APIs / 순수 계산
iosApp -> SwiftUI host + shared framework
Shared Supabase -> legacy sync + completed-workout summary v2
Nutrition Supabase -> Nutrition catalog / canonical provenance / publication
PriceTrace -> product / brand / restaurant / menu identity
Fitness-Image export -> reviewed generated Android assets + fallback
```

## 3. 데이터 모델과 불변식

- 입력과 상세 운동·세트의 진실 원천은 로컬 저장소다. 네트워크 실패와 로컬 쓰기 실패를 합치지 않는다.
- AccountScope와 프로젝트별 Auth UUID를 확인한다. 같은 이메일이 다른 Supabase 계정 identity를 대신하지 않는다.
- Workout record type과 LoadState는 별도 의미다. 운동별 메모·휴식·세트 구조와 canonical exercise link를 기존 기록 identity와 분리해 migration한다.
- Personal OS summary v2에는 완료 운동 요약만 보낸다. 상세 세트·GPS route·식사 영양을 summary 계약으로 확장하지 않는다.
- 라벨 OCR·food estimate·external reference의 출처 경계를 유지한다. 상품·브랜드·식당·메뉴 identity는 이름으로 확정하지 않는다.
- 외식 제안은 owner·remote account scope와 저장된 idempotency request에 결합한다. 관리자 승인이 자동 공개를 일으키지 않고 사용자 동의를 요구한다.
- Room migration 파일과 schema snapshot 존재는 실제 사용자 DB upgrade 성공과 동일하지 않다.
- 이미지 소비는 검수된 export와 fallback을 사용한다. 미완성 자산을 READY로 취급하지 않는다.

## 4. 핵심 기술 의사결정

### 결정 1. 점진적 플랫폼 분리
공통 모델·계산·API는 shared에, Android 저장·위치·인증과 iOS host는 adapter에 둔다.

### 결정 2. 로컬 상세 기록 우선
Compose는 상태를 표현하고 ViewModel/application/repository가 owner 범위, migration, 쓰기와 복구를 관리한다.

### 결정 3. 도메인별 authority 유지
Fitness는 상세 기록을, Nutrition은 영양 provenance/publication을, PriceTrace는 상품·브랜드·식당 identity를, Personal OS는 요약 조회를 소유한다.

### 결정 4. identity와 display label 분리
수동 운동·상품·브랜드·외식 항목은 표시명만으로 기존 canonical identity에 합치지 않고 명시적 link·migration·검증 경로를 둔다.

## 5. 테스트와 검증 전략

| 검사 | 결과 | 근거·환경과 한계 |
| --- | --- | --- |
| 최신 main source 감사 | 저장소 검증 | `main@9d05980`까지 source, Room schema/migration, unit·instrumented test와 CI 설정을 확인했다. 실행 검증은 아니다. |
| 이전 KMP CI | 통과 기록 | [KMP release readiness](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/37213991994). 최신 HEAD의 모든 변경을 재실행한 증거로 사용하지 않는다. |
| 실사용 환경 | 미검증 | 최신 HEAD의 실기기 DB upgrade, instrumentation, interactive iOS, release 서명, 운영 RLS, Nutrition·PriceTrace·Personal OS 교차 앱 수렴은 별도 검증이 필요하다. |

문서 변경 검증 경로는 저장소의 `project-docs.config.json`과 `.github/project-docs` 정책을 따른다. validator·render-only 결과가 실제 기기나 운영 backend 검증을 대신하지 않는다.

## 6. 배포·운영·복구

- Android 기본 gate는 Gradle unit/build와 저장소 CI 정책을 따른다. shared·iOS는 별도 KMP readiness 경계로 본다.
- Shared, Nutrition, PriceTrace migration은 대상 프로젝트와 migration ownership을 확인한 뒤 적용한다.
- 버전이 있는 JSON backup과 Room migration을 명시적으로 사용한다. 서명 불일치를 우회하려고 uninstall 또는 `pm clear`로 기존 데이터를 삭제하지 않는다.
- sync·publication 실패 시 마지막 로컬 기록과 canonical IDs를 보존하고 retry가 기존 기록을 중복 생성하지 않게 한다.

**문서 발행**: Git Markdown이 canonical이다. main 병합 후 설정된 project-docs workflow가 검증·발행하며, Notion은 read-only mirror로 취급한다.

## 7. 한계, 기술 부채, 다음 단계

- 최신 schema 57까지의 migration은 저장소에서 관측했지만 실제 장기 사용자 DB upgrade는 이번 작업에서 미검증이다.
- Shared/Nutrition/PriceTrace 운영 RPC·RLS와 여러 계정·기기 수렴은 별도 실환경 검증이 필요하다.
- 다음 우선 작업은 호환 서명 APK에서 기존 DB 보존 upgrade, 운동 기록·식사/영양·브랜드 identity·알림·Personal OS summary publication을 종단 확인하는 것이다.

## 8. 근거와 관련 문서

- [기준 source revision](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/9d05980b243552c4503743bb2306d510cea03ca7)
- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [KMP readiness](architecture/KMP_M8_RELEASE_READINESS.md)
- [완료 운동 summary v2](FITNESS_SUMMARY_PROJECTION_V2.md)
- [Supabase 경계](../supabase/README.md)
- [Room source](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessRoomDatabase.kt)
