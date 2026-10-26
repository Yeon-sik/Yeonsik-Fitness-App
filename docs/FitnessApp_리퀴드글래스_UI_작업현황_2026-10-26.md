# 리퀴드 글래스 UI 구현 현황과 재개 지점

작성일: 2026-10-26. 코드 근거는 **`287e2e193e2c7b2a4a1b3ad19c87198873851e0b`**다.

**다음 시작점은 원 명세 6장의 PR-C(edge-to-edge)다. A·B의 코드 구현과 로컬 검증은 완료했다. A·B를 다시 구현하지 않는다.**

## 1. 연결 문서와 작업 기준

- 구현 지시: [FitnessApp 리퀴드 글래스 UI 개선 구현 명세](FitnessApp_리퀴드글래스_UI_개선_구현명세_2026-10-26.md)
- 필수 구조 규칙: [FitnessApp 정돈 리팩터링 규칙](FitnessApp_정돈_리팩터링_규칙.md)
- 디자인 기준: [UI 디자인 시스템](UI_DESIGN_SYSTEM.md)
- 원 명세 출처: `docs/liquid-glass-ui-spec`의 `5bef90233e0387b19be59523a908a8e5a9cf118c`. 이 브랜치에도 원 명세를 가져오고 위 현황 링크를 연결했다.
- 작업 브랜치: `feat/liquid-glass-home-navigation`
- 분기 기준: `main`의 `f15d22dd15aa5be3a22412b4495600953a959714`
- A의 로컬 분기점: `feat/liquid-glass-ui-foundation` / `139be22`. 현재 작업 브랜치는 해당 커밋을 포함한다.

원 명세의 `CURRENT`는 작성 당시 코드이며, 이 문서는 그 이후의 변경을 기록한다. 아래의 **코드 구현**, **로컬 검증**, **계획**은 서로 다른 상태다. 배포·운영·화면 품질 검증 완료를 뜻하지 않는다.

### 사용자 지정 검증 범위

- 에뮬레이터·실기기 검증과 계측 테스트 실행은 하지 않는다.
- 최신 마감 지시는 **추가 APK 생성 없이 현재 작업을 커밋·푸시**하는 것이다. 별도 요청 전까지 APK 생성·설치를 다시 실행하지 않는다.
- 후속 검증은 코드 컴파일과 JVM 단위 테스트로 제한한다. 아래의 debug 빌드 성공은 추가 APK 생성 금지 지시 전에 확보한 결과다. 원 명세 11장의 기기·화면 검증 항목은 미수행으로 남긴다.
- 메모리 절감을 위해 빌드는 작업자 1개, Kotlin 컴파일러의 in-process 실행, JVM 힙 상한 1536MB, 일회성 Gradle 데몬으로 실행한다.
- 기기 확인용 ADB 서버와 중단된 이전 컴파일 프로세스는 정리했다. 후속 작업에서 기기 확인이나 에뮬레이터 실행을 다시 시도하지 않는다.
- DB schema, 기록 저장 정책, 동기화와 외부 계약 변경은 이번 구현 범위에 포함하지 않는다.

## 2. 명세 대비 현재 상태

| 원 명세 | 항목 | 코드 상태 | 후속 작업 |
|---|---|---|---|
| PR-A / 4장 | P0-1, P0-3, P2-11 | 구현, 로컬 검증 통과 | 재구현하지 않음 |
| PR-B / 5장 | P0-2, P1-6, P2-8, P2-10 | 구현, 로컬 검증 통과 | 다음은 PR-C |
| PR-C / 6장 | P1-5 edge-to-edge | 미구현 | **다음 시작점** |
| PR-D / 7장 | P1-4 바·타이머, P2-12 라이브러리 격리 | 미구현 | C 이후, 6.1절 보완 반영 |
| PR-E / 8장 | 다이얼로그·시트 | 미구현 | 창 획득·블러 대체 경로부터 확인 |
| PR-F / 9장 | P2-7 휴식 종료 알림 | 미구현 | 6.3절의 예약·수명주기 설계를 먼저 수정 |
| PR-G / 10장 | P2-9 로딩 통일 | 미구현 | A 기반 구현 가능, 지연 중 콘텐츠 조건 보완 |

PR-A부터 PR-G까지의 이름은 원 명세의 구현 묶음 이름이다. GitHub PR을 생성하거나 main에 병합했다는 의미가 아니다.

## 3. 구현한 내용과 커밋

| 커밋 | 내용 | 원 명세 연결 |
|---|---|---|
| `9ff3a0b` | 이미지 카탈로그 테스트의 오래된 drawable·상수·별칭 참조 수정 | 구현 전 기준 테스트 복구, UI 코드와 별도 커밋 |
| `139be22` | 전경색 대비, Orb 팔레트·발광, 글래스 수치 토큰화 | PR-A |
| `287e2e1` | 홈 표면, 상단 카드, 뒤로가기·기간 이동 아이콘, 고대비 공통 감지 | PR-B |

### PR-A

- [FitnessUiTokens.java](../app/src/main/java/com/yeonsik/fitnessapp/core/ui/FitnessUiTokens.java)에 라이트 전경용 `#0369A1`과 그 위 글자색 `#FFFFFF`를 추가했다. 브랜드 채움색 `#38BDF8`은 유지한다.
- [FitnessComposeTheme.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessComposeTheme.kt)에서 라이트 `primary`·`onPrimary`를 분리했다. 채움용 `LocalFitnessColors.action`·`onAction`과 다크 테마의 기존 역할색은 유지한다.
- [ThinkingOrb.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/ThinkingOrb.kt)에 전용 4색 팔레트와 중심 발광을 적용했다. 그라데이션은 draw cache에서 만들고 애니메이션이 꺼지면 정적으로 그린다. 입자 88개와 회전 주기는 유지한다.
- 내비게이션의 기존 글래스 효과 수치와 피드백 수치를 공통 토큰으로 옮겼다. 이 단계에서 효과 값은 바꾸지 않았다.
- [FitnessColorContrastTest.java](../app/src/test/java/com/yeonsik/fitnessapp/core/ui/FitnessColorContrastTest.java)는 Android graphics 실행 없이 정수 색상값의 WCAG 상대 휘도를 계산한다.
- `NAV_GLASS_BLUR_RADIUS_DP`는 `GLASS_PLAIN_BLUR_DP`의 deprecated 별칭으로 남아 있다. 제거 시점은 PR-D다.

### PR-B

<!-- Repository package segment 'home' is URI-encoded for the documentation validator. -->
- [ComposeHomeScreen.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/%68ome/ui/ComposeHomeScreen.kt)의 `HomeGlassPanel`과 홈의 backdrop import를 제거했다. 빠른 이동 카드는 `FitnessCard(outlined = true)`를 사용한다.
- 빈 “빠른 이동” 제목을 제거하고 등장 순서를 0~4로 정리했다. 기존 빠른 이동 액션과 테스트 태그를 유지한다.
- [FitnessHeroColors.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessHeroColors.kt)에 상단 카드 전용 색 역할을 정의하고 테마에서 제공한다. 라이트 표면은 `#F0F9FF`, 다크 표면은 `#12394D` → `#10324A`다.
- [HomeTodayHero.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/%68ome/ui/HomeTodayHero.kt)의 전면 파랑 채움을 제거하고 `n/3 달성` 브랜드 칩을 추가했다. 체중 증감색은 새 표면의 의미색을 사용한다.
- 카테고리별 채움색은 유지하고 진행 막대·완료 마커에 외곽선을 두었다. 밝은 표면에서 노란색 표시의 경계가 사라지는 문제를 줄이기 위한 구현 보완이다.
- [FitnessAccessibility.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessAccessibility.kt)에 고대비 감지를 모았다. 최초 구성과 시스템 설정에서 돌아오는 `ON_RESUME` 때 다시 읽는다. 공개 API 확인 조건인 API 36 이상을 유지한다.
- [FitnessComposeComponents.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessComposeComponents.kt)에 공통 `FitnessBackButton`과 카드 테두리 옵션을 추가했다. 뒤로가기 버튼의 최소 터치 영역은 48dp이며 접근성 설명은 “뒤로”다.
- [FitnessActionIcons.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessActionIcons.kt)의 이전·다음 아이콘은 RTL에서 자동 반전된다. 헤더, 월·날짜·기간 이동, 루틴 진입 표시의 문자 화살표를 교체했다. 기존 클릭 액션·비활성 조건·접근성 설명을 유지한다.
- 상단 카드의 글자색은 라이트 표면과 다크 그라데이션 양 끝을 모두 대비 테스트에 포함했다.
- 기존 Android UI 테스트의 색상 역할·뒤로가기 조회·제목 조회를 새 계약에 맞췄다. 이 테스트의 APK 컴파일·실행 결과는 확보하지 않았다.

## 4. 검증 기록

실행일은 2026-10-26이며, 로컬 Windows·JDK 17·debug 구성을 사용했다.

| 구분 | PR-A (`139be22`로 고정한 구현) | PR-B (`287e2e1`) |
|---|---|---|
| JVM 단위 테스트 | 총 578개: 576 통과, 2 건너뜀, 실패·오류 0 | 총 579개: 577 통과, 2 건너뜀, 실패·오류 0 |
| Android debug 빌드 | 성공 | 성공 |
| 계측 테스트 | 사용자 지정 범위에 따라 미실행 | 사용자 지정 범위에 따라 미실행 |
| 에뮬레이터·실기기 | 미실행 | 미실행 |
| 운영 RPC·RLS | 해당 없음 | 해당 없음 |

PR-A 실행은 `BUILD SUCCESSFUL`(17분 52초), PR-B 실행은 `BUILD SUCCESSFUL`(12분 31초)로 종료됐다. 테스트 수는 각각의 실행 직후 `app/build/test-results/testDebugUnitTest/TEST-*.xml`에서 집계했다. 건너뛴 2개를 통과 수에 포함하지 않았다.

### 실행 명령과 이미지 산출물 재사용

아래는 **이미 수행한 검증의 기록**이며 재실행 지시가 아니다. 당시 검증 목표는 `testDebugUnitTest assembleDebug`였다. 전체 빌드에서 운동 이미지 산출물을 생성한 후, 이미지와 관련 없는 UI 코드 재검증에서는 그 산출물을 재사용했다. `JAVA_HOME`은 설치된 JDK 17로 설정했다.

```powershell
$uiBuildArgs = @(
    'testDebugUnitTest', 'assembleDebug',
    '--offline', '--no-daemon', '--max-workers=1',
    '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
    '-Pkotlin.compiler.execution.strategy=in-process',
    '-x', 'syncExerciseIllustrations',
    '-x', 'generateExerciseIllustrationAssets',
    '-x', 'generateExerciseCatalog'
)
& .\gradlew.bat @uiBuildArgs
```

**새 checkout, `clean` 이후, 이미지 원본·manifest·카탈로그 변경 후에는 위 제외 옵션을 쓰지 않는다.** 아래는 저장소의 원형 검증 명령이지만 최신 사용자 지시가 적용되는 동안 `assembleDebug`는 실행하지 않는다. 현재 후속 검증에는 `testDebugUnitTest`와 필요한 컴파일까지만 사용한다. offline 실행은 필요한 의존성이 이미 캐시에 있을 때만 사용한다.

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

### 중간 실패와 처리

- 기존 이미지 테스트가 존재하지 않는 drawable·상수 4개를 참조해 컴파일되지 않았다. `9ff3a0b`에서 현행 이름과 별칭으로 수정했다.
- Orb의 draw cache 좌표 참조 오류와 B의 `LocalContext` import 누락은 현재 코드에서 수정했다. 실패한 중간 실행을 최종 통과 결과로 취급하지 않는다.
- 중단된 컴파일 출력 때문에 Kotlin 출력 폴더 정리가 실패한 실행이 있었다. 당시 실행 중인 Java 프로세스가 없는 것을 확인하고 `app/build` 안의 Kotlin 컴파일 출력만 정리했다. 소스나 DB를 삭제하지 않았다.
- PowerShell이 점이 포함된 Gradle 옵션을 분리한 실행은 검증 단계에 진입하지 못했다. 위처럼 문자열 배열로 인수를 전달하도록 수정했다.

## 5. 다음 시작점: PR-C

1. 현재 브랜치에 `139be22`와 `287e2e1`이 포함돼 있는지 확인한다. A·B가 포함된 상태에서 PR-C를 시작한다. 이후 다른 코드가 추가됐으면 이 문서의 검증 결과를 새 변경의 검증 결과로 재사용하지 않는다.
2. [원 명세 6장](FitnessApp_리퀴드글래스_UI_개선_구현명세_2026-10-26.md#6-pr-c-p1-5-edge-to-edge)을 읽고 edge-to-edge를 구현한다. 현재 루트의 `systemBarsPadding()`은 아직 제거하지 않았다.
3. [MainActivity.java](../app/src/main/java/com/yeonsik/fitnessapp/MainActivity.java)의 `setContentView` 이전 창 설정과 `applySystemBarAppearance`를 함께 확인한다. API 26~34에서 불투명 시스템 바 색이 콘텐츠를 덮지 않도록 처리하고 아이콘 밝기 규칙을 보존한다.
4. [ComposeAppScreen.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/app/navigation/ComposeAppScreen.kt)의 콘텐츠 패딩, 하단 탭, 기록 허브 탭, 기존 세션 상단 바와 휴식 바에 inset을 한 번씩만 적용한다. 스크롤하지 않는 종목 선택·유산소·루틴 화면도 포함한다.
5. 상태바 스크림을 추가한다. IME 패딩과 내비게이션 바 패딩 중복, 가로 방향 cutout·좌우 inset, 상단 바가 이미 차지한 상태바 높이의 중복을 코드에서 점검한다.
6. 입력 초안·포커스·뒤로가기 콜백, 소유자·날짜 필터, `SaveableStateProvider` 키와 기록 저장 경로를 보존한다. 이 변경과 휴식 종료 알림을 섞지 않는다.
7. 사용자 지정 범위에 따라 코드 컴파일·단위 테스트만 실행한다. 추가 APK 생성·설치, 기기·계측 검증은 하지 않는다. 이 미수행 범위는 다음 결과 기록에도 유지한다.

원 명세의 원칙대로 실제 PR은 선행 변경이 main에 반영된 후 main에서 분기한다. 미병합 상태에서 로컬 후속 구현을 계속할 경우에는 A·B가 포함된 현재 브랜치를 기반으로 별도 `feat/` 브랜치를 만들고 선행 의존성을 명시한다. 최신 main만 checkout해서 A·B가 적용됐다고 가정하지 않는다.

## 6. 이후 단계에서 먼저 보완할 점

### 6.1 PR-D: 글래스 타입과 누락 범위

- 실제 라이브러리 `layerBackdrop`은 `LayerBackdrop`을 요구한다. 원 명세 예시의 `fitnessBackdropSource(backdrop: Backdrop)`를 그대로 위임하면 타입이 맞지 않는다. `FitnessBackdrop` 같은 앱 소유 래퍼가 내부 `LayerBackdrop`을 보관하도록 설계하고 외부 파일의 vendor import를 격리한다.
- 현재 직접 backdrop import가 남아 있는 파일은 `ComposeAppScreen.kt`와 [MealOverviewSurface.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/feature/meal/ui/MealOverviewSurface.kt)다. 홈만 정리하고 라이브러리 격리를 완료했다고 표현하지 않는다.
- 식단 요약도 실제 콘텐츠 대신 자체 그라데이션을 샘플링하는 콘텐츠 카드다. 원 명세의 콘텐츠/조작 층 원칙에 맞춰 일반 표면으로 전환하는 범위를 D에 포함한다.
- 타이머와 세션 바는 샘플링하는 콘텐츠 레이어의 형제 오버레이로 둔다. 자기 자신을 포함한 레이어를 샘플링하지 않는다.
- `FitnessSessionTopBar` 내부의 기존 불투명 배경도 확인한다. 외부 modifier에 글래스만 추가하면 내부 배경에 가려질 수 있다.
- 라이브러리 버전은 `0.0.1-alpha02`를 유지한다. import 격리 JVM 테스트와 API별 불투명 대체 경로를 구현한다. 기기 실행은 수행하지 않는다.

### 6.2 PR-E: 별도 창의 블러

원 명세대로 앱 루트 backdrop을 별도 다이얼로그 창에서 샘플링하지 않는다. 실제 resolved Material3 버전, `DialogWindowProvider`를 통해 창을 얻는 위치, 창의 투명성·블러 영역·모서리를 확인한다. 창 획득 또는 블러가 불가능하면 불투명 대체 표면을 사용한다. 사용자 지정 범위에서는 기기에서 블러가 보인다고 승인하거나 전체 화면 품질 검증 완료로 기록하지 않는다.

### 6.3 PR-F: 예약 정밀도와 종료 처리

- `setWindow(..., 10_000L, ...)`에 대해 “수 초~수십 초 지연”을 보장할 수 없다. Android 12 이상에서는 작은 창이 일반적으로 600,000ms로 늘어나며 절전 정책에 따른 지연도 가능하다. [Android 알람 공식 문서](https://developer.android.com/develop/background-work/services/alarms#deliver-an-alarm-during-a-time-window)
- 정확 알람 권한 정책과 허용 지연을 먼저 정하고 예약 명세를 수정한다.
- 종료 `LaunchedEffect`를 루트로 옮기는 것만으로 포그라운드 실행이 보장되지 않는다. 백그라운드에서 `stopRestTimer()`가 예약을 취소해 수신기 알림을 없애지 않도록 수명주기와 단일 종료 처리 소유자를 설계한다.
- 현재 종료 판정은 여전히 `RestTimerBar` 안에 있다. 다른 탭에서 종료 시점을 놓치는 기존 결함을 수정한 것으로 기록하지 않는다.
- 알림 변경은 UI 정리와 별도 커밋·회귀 테스트로 처리한다.

### 6.4 PR-G: 로딩 중 표시 조건

`rememberDelayedLoadingVisible()`의 반환값이 false라는 이유만으로 준비되지 않은 데이터를 렌더링하지 않는다. 300ms 대기, 표시 중, 결과·오류 상태의 구분과 소유자·날짜 변경 시 초기화 조건을 먼저 정한다. 최소 500ms 표시 중에는 준비된 결과의 노출도 함께 제어한다. 접근성 대표 문구는 첫 문구로 고정하고 순환 문구의 반복 낭독을 막는다. 기존 테스트 태그를 유지하며 계측 테스트는 실행하지 않는다.
