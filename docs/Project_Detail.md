# Yeonsik-Fitness-App | Project Detail

2026-10-05 (Asia/Seoul) 갱신. Primary source boundary는 [main@65adc62](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/65adc622fe4893cc5bf194d4445c0117db1a31bc)이다. source 설명과 실제 검증 결과를 구분한다. [빠른 소개](./Project_Intro.md)를 참고한다.

## 1. 문서 목적과 범위

근력, 실내·야외 유산소, 식사와 발전 기록을 로컬에서 관리하는 Android 앱이다. 현재 main은 Compose 화면, Room 저장소와 Kotlin Multiplatform shared 모듈을 사용하며 Java·View 호환 경로를 유지한다.

상세 기록을 계정 범위의 로컬 DB에 먼저 저장한다. 화면·application·repository를 분리하고 Personal OS에는 완료 운동 요약만 공유하며 외부 Nutrition·PriceTrace를 정확한 계약으로 연결한다.

- 최근 main에는 실내 유산소·검색 종목, 운동 취소 toolbar, 차트 포인트 상세, 추적 알림 lifecycle/thread 안정화와 canonical Nutrition 연계 보강과 owner-scoped private 외식 제안·명시적 공개 동의가 포함된다.

미병합 branch, 사용자 미커밋 작업과 명시하지 않은 운영 검증은 기능 완료 근거에 포함하지 않는다.

## 2. 시스템 아키텍처

```text
MainActivity / AppContainer -> ComposeAppScreen
  -> feature ViewModel / state / action
  -> application·repository adapters -> FitnessRoomDatabase (SQLite)
shared/commonMain -> account scope / models / APIs / 순수 계산
iosApp -> SwiftUI host + shared framework
Shared Supabase -> legacy sync + completed-workout summary v2
Nutrition Supabase -> Nutrition catalog / canonical provenance
PriceTrace -> exact product / restaurant / menu identity
Fitness-Image export -> sync -> generated Android assets
```

## 3. 데이터 모델과 불변식

- 입력과 상세 운동·세트의 진실 원천은 로컬 저장소다. 네트워크 실패와 로컬 쓰기 실패를 합치지 않는다.
- AccountScope와 프로젝트별 Auth UUID를 확인한다. 같은 이메일이 다른 Supabase 계정 identity를 대신하지 않는다.
- Workout record type과 LoadState는 별도 의미다. 반복수·추가 중량·외부 부하·보조 중량을 섞지 않는다.
- Personal OS summary v2에는 완료 운동 요약만 보낸다. 상세 세트·GPS route·식사 영양을 v2 summary로 확장하지 않는다.
- 라벨 OCR·food estimate·external reference의 출처 경계를 유지한다. 상품 identity는 이름으로 추정하지 않는다.
- Room·Compose·KMP와 Java/View·SQLiteOpenHelper 호환 코드가 공존한다. 과거 기술 설명을 현재 구현 전체로 일반화하지 않는다.
- 외식 제안은 owner·remote account scope와 저장된 idempotency request에 결합한다. 관리자 승인이 자동 공개를 일으키지 않고 승인 exact identity 재조회와 사용자 공개 동의를 요구한다.
- 이미지 소비는 검수된 export와 fallback을 사용한다. 미완성 자산을 READY로 취급하지 않는다.

## 4. 핵심 기술 의사결정

### 결정 1. 점진적 플랫폼 분리

공통 모델·계산·API는 shared에, Android 저장·위치·인증과 iOS host는 adapter에 둔다.

### 결정 2. 화면 상태와 DB I/O 분리

Compose는 상태를 표현하고 ViewModel/application/repository가 owner 범위와 쓰기를 관리한다.

### 결정 3. 도메인별 authority 유지

Fitness는 상세 기록과 Nutrition, PriceTrace는 상품·식당 identity, Personal OS는 요약 조회를 소유한다.


## 5. 테스트와 검증 전략

| 검사 | 결과 | 근거·환경과 한계 |
| --- | --- | --- |
| 현재 main KMP CI | 통과 | [KMP release readiness](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/37213991994): Android/shared tests·APK/test APK·lint와 macOS Simulator tests·framework·SwiftUI host build. |
| 실사용 환경 | 이번 갱신에서 미실행 | 실기기 instrumentation, interactive iOS 화면, release 서명·운영 RLS·교차 앱 수렴은 CI와 별도다. |

이번 문서 변경의 순차 검증 명령은 다음과 같다.

```text
node .github/project-docs/validate-project-docs.mjs --config project-docs.config.json --require-tracked
node .github/project-docs/sync-project-docs-to-notion.mjs --config project-docs.config.json
```

두 번째 명령은 render-only dry run이다. source·required sections·Git tracked links와 렌더링을 검증하며 Notion에 쓰지 않는다. 과거 테스트 수와 운영 상태를 현재 revision의 성공 수치로 재사용하지 않는다. 실제 기기·원격 권한·사용자 흐름은 표에 명시한 환경에서 따로 확인한다.

## 6. 배포·운영·복구

- Android 기본 gate는 gradlew.bat testDebugUnitTest assembleDebug다. shared·iOS는 kmp-release-readiness를 따른다.
- Shared, Nutrition, PriceTrace migration은 supabase/README.md의 대상 프로젝트 경계를 확인한 뒤 적용한다.
- 버전이 있는 JSON backup을 명시적으로 사용한다. 업데이트 전 서명 인증서를 비교하고 호환 APK만 adb install -r로 적용한다.
- 서명 불일치를 우회하려고 uninstall 또는 pm clear로 기존 데이터를 삭제하지 않는다.

**문서 발행**: 검토한 문서를 main에 병합하면 on-main-push workflow가 발행한다. GitHub Environment는 notion-production이고 canonical branch는 main이다. 발행용 token과 page map은 Environment secret으로 관리하고 Git에 넣지 않는다. 신규 연결은 dedicated mirror를 만들고 본문 갱신은 설정된 GitHub Actions 정책을 따른다.

발행은 모든 페이지 preflight 뒤 configured Intro·Detail만 교체한다. 동일 source SHA·fingerprint면 skip하고 일부 실패는 같은 revision을 재실행해 수렴시킨다. 수동 메모와 원본 데이터는 미러 밖에 둔다.

## 7. 한계, 기술 부채, 다음 단계

- iOS shared tests·host build를 Android 전체의 iOS 기능 동등성이나 interactive runtime 증거로 확대하지 않는다.
- Shared/Nutrition/PriceTrace 운영 RPC·RLS와 여러 계정·기기 수렴은 프로젝트별 증거가 필요하다.
- 다음 우선 작업은 호환 서명 APK로 기존 DB를 유지하며 실내 유산소·알림·운동 취소를 실기기에서 확인하는 것이다.

## 8. 근거와 관련 문서

- [기준 source revision](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/65adc622fe4893cc5bf194d4445c0117db1a31bc)
- [Project Intro](./Project_Intro.md)
- [README](../README.md)
- [KMP readiness](architecture/KMP_M8_RELEASE_READINESS.md)
- [완료 운동 summary v2](FITNESS_SUMMARY_PROJECTION_V2.md)
- [Supabase 경계](../supabase/README.md)
- [Room source](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessRoomDatabase.kt)
