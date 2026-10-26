# 리퀴드 글래스 UI 구현 현황과 재개 지점

작성일: 2026-10-26. 갱신일: 2026-10-27. C–G 구현 근거는 **`64ea924`**, 후속 PriceTrace 통합 근거는 **`9388c44`**다. A·B의 기존 검증 기록은 아래에 보존한다.

**A–G 코드 구현 후 보관한 PriceTrace 변경도 통합했다. 허용된 로컬 검증은 통과했으며 실기기·계측 실행·APK 생성은 미수행이다. 기기 수용 기준과 운영 PT 연동은 별도 검증 대상으로 남는다.**

## 1. 연결 문서와 작업 기준

- 구현 지시: [FitnessApp 리퀴드 글래스 UI 개선 구현 명세](FitnessApp_리퀴드글래스_UI_개선_구현명세_2026-10-26.md)
- 필수 구조 규칙: [FitnessApp 정돈 리팩터링 규칙](FitnessApp_정돈_리팩터링_규칙.md)
- 디자인 기준: [UI 디자인 시스템](UI_DESIGN_SYSTEM.md)
- 원 명세 출처: `docs/liquid-glass-ui-spec`의 `5bef90233e0387b19be59523a908a8e5a9cf118c`. 이 브랜치에도 원 명세를 가져오고 위 현황 링크를 연결했다.
- 작업 브랜치: `feat/fitness-glass-completion`
- 이어받은 원격 기준: `origin/feat/liquid-glass-home-navigation`의 `3dfdbb02703ee8bfbc63e791f6951debb66ea77c`
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
| PR-B / 5장 | P0-2, P1-6, P2-8, P2-10 | 구현, 기존 로컬 검증 통과 | 기기 검증 미수행 |
| PR-C / 6장 | P1-5 edge-to-edge | 구현, 컴파일·JVM 통과 | IME·회전·cutout 기기 검증 미수행 |
| PR-D / 7장 | P1-4 바·타이머, P2-12 라이브러리 격리 | 구현, 컴파일·JVM 통과 | 글래스·대체 표면 시각 검증 미수행 |
| PR-E / 8장 | 다이얼로그·시트 | 구현, 컴파일·JVM 통과 | 창 블러·포커스·뒤로가기 기기 검증 미수행 |
| PR-F / 9장 | P2-7 휴식 종료 알림 | 구현, 정책 JVM 통과·계측 소스 컴파일 | 실제 알람·권한·Doze 검증 미수행 |
| PR-G / 10장 | P2-9 로딩 통일 | 구현, 컴파일·JVM 통과·계측 소스 컴파일 | 시간 경계 UI 테스트·TalkBack 실행 미수행 |

PR-A부터 PR-G까지의 이름은 원 명세의 구현 묶음 이름이다. GitHub PR을 생성하거나 main에 병합했다는 의미가 아니다.

## 3. 구현한 내용과 커밋

| 커밋 | 내용 | 원 명세 연결 |
|---|---|---|
| `9ff3a0b` | 이미지 카탈로그 테스트의 오래된 drawable·상수·별칭 참조 수정 | 구현 전 기준 테스트 복구, UI 코드와 별도 커밋 |
| `139be22` | 전경색 대비, Orb 팔레트·발광, 글래스 수치 토큰화 | PR-A |
| `287e2e1` | 홈 표면, 상단 카드, 뒤로가기·기간 이동 아이콘, 고대비 공통 감지 | PR-B |
| `32afc9e` | 시스템 바와 화면별 inset 분리, 상태바 scrim | PR-C |
| `964a584` | 앱 소유 글래스 어댑터, 세션·휴식 바 형제 오버레이, 식단 요약 일반 표면 | PR-D |
| `0515a20` | 별도 창 블러와 불투명 대체 표면 래퍼, 기존 모달 연결 | PR-E |
| `8b8d4e0` | 다른 탭에서도 STARTED 상태에서 휴식 종료 처리 | PR-F 기존 결함 수정 |
| `dc4b69c` | 영속 예약 토큰, 백그라운드 알림, 계정 경계·중복 처리 방지 | PR-F 알림 |
| `64ea924` | 여섯 로딩 화면의 공통 variant, 지연·최소 표시·접근성 | PR-G |
| `03ef344` | C–G 완료 범위와 검증 기록, 알람 정책 명세 보완 | PT 적용 전 기준점 |
| `9388c44` | 보관한 PT 제품 등록을 원격 브랜드 카탈로그와 통합 | C–G 이후 PT 적용 |

### PR-A

- [FitnessUiTokens.java](../app/src/main/java/com/yeonsik/fitnessapp/core/ui/FitnessUiTokens.java)에 라이트 전경용 `#0369A1`과 그 위 글자색 `#FFFFFF`를 추가했다. 브랜드 채움색 `#38BDF8`은 유지한다.
- [FitnessComposeTheme.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessComposeTheme.kt)에서 라이트 `primary`·`onPrimary`를 분리했다. 채움용 `LocalFitnessColors.action`·`onAction`과 다크 테마의 기존 역할색은 유지한다.
- [ThinkingOrb.kt](../app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/ThinkingOrb.kt)에 전용 4색 팔레트와 중심 발광을 적용했다. 그라데이션은 draw cache에서 만들고 애니메이션이 꺼지면 정적으로 그린다. 입자 88개와 회전 주기는 유지한다.
- 내비게이션의 기존 글래스 효과 수치와 피드백 수치를 공통 토큰으로 옮겼다. 이 단계에서 효과 값은 바꾸지 않았다.
- [FitnessColorContrastTest.java](../app/src/test/java/com/yeonsik/fitnessapp/core/ui/FitnessColorContrastTest.java)는 Android graphics 실행 없이 정수 색상값의 WCAG 상대 휘도를 계산한다.
- A에서는 `NAV_GLASS_BLUR_RADIUS_DP`를 deprecated 별칭으로 남겼고, 후속 D에서 제거했다.

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

### 기존 A·B 기록

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

### C–G 후속 검증 (2026-10-26~27)

Windows, Android Studio JBR 21.0.10 런타임, Java/Kotlin target 17, Gradle 9.0.0, 캐시된 Android SDK를 사용했다. 기존 A·B의 JDK 17 실행과 구분한다.

| 검증 시점 | JVM 결과 | 컴파일 결과 |
|---|---|---|
| C·D | 581개: 579 통과, 2 건너뜀, 실패·오류 0 | 앱 소스 통과 |
| E | 582개: 580 통과, 2 건너뜀, 실패·오류 0 | 앱 소스 통과 |
| F | 587개: 585 통과, 2 건너뜀, 실패·오류 0 | 앱·계측 테스트 소스 통과 |
| G (`64ea924`) | 587개: 585 통과, 2 건너뜀, 실패·오류 0 | 앱·계측 테스트 소스 통과 |

`testDebugUnitTest` XML 집계이며 계측 테스트를 통과 수에 포함하지 않았다. C·D 최종 실행 1분 18초, E 1분 10초, F 1분 50초, G 1분 14초에 `BUILD SUCCESSFUL`을 확인했다.

```powershell
# JAVA_HOME: Android Studio JBR 21, ANDROID_HOME: 설치된 SDK
# FITNESS_IMAGE_PIPELINE_DIR: 보존된 Fitness-Image-Pipeline 원본 checkout
.\gradlew.bat testDebugUnitTest compileDebugJavaWithJavac `
  compileDebugAndroidTestKotlin compileDebugAndroidTestJavaWithJavac `
  --offline --no-daemon --max-workers=1 `
  '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' `
  '-Pkotlin.compiler.execution.strategy=in-process' `
  -x syncExerciseIllustrations -x generateExerciseIllustrationAssets -x generateExerciseCatalog
```

새 작업 트리의 첫 실행에서는 제외 옵션 없이 외부 이미지 파이프라인 원본을 연결해 산출물을 생성했다. 이후 C–G는 이미지 변경이 없으므로 같은 산출물을 재사용했다. 최초 기본 fallback 생성으로 이미지 테스트 상수 4개가 누락된 시도는 실패이며, 테스트를 완화하지 않고 올바른 파이프라인 입력으로 해결했다. 내비게이션 어댑터 이동 중 누락된 `dark` 지역값도 첫 컴파일에서 발견해 수정했다.

## 5. C–G 구현 경계

- C: `setDecorFitsSystemWindows(false)`를 콘텐츠 생성 전에 설정했다. 페이지의 시스템 바·cutout inset, 하단 탭과 휴식 바의 navigation inset, 루트 IME inset을 나누고 상태바 scrim을 별도 draw layer로 둔다.
- D: `FitnessBackdrop`이 내부 `LayerBackdrop`을 보관한다. vendor import는 `FitnessGlass.kt` 한 파일뿐이며 JVM 구조 테스트로 검사한다. API 31 미만·고대비·backdrop 부재에는 불투명 표면을 쓴다. 세션 바와 휴식 바는 페이지 backdrop을 샘플링하는 형제 오버레이이며, 유산소 바는 일반 표면을 유지한다. 식단 요약의 자체 그라데이션 샘플링은 제거했다.
- E: resolved Material3 **1.3.1** sources에서 `ModalBottomSheetDialogLayout`의 `DialogWindowProvider` 구현을 확인했다. 모달 내부에서 실제 창을 찾으며 Activity 창으로 대체하지 않는다. 제한된 alert 창에는 background blur, 전체 창을 쓰는 시트·다이얼로그에는 blur-behind를 사용한다. 시스템 블러 변경 리스너 해제와 이전 창 상태 복원을 포함한다. 창·API·블러 지원이 없으면 불투명 표면과 테두리로 복귀한다. 입력 콜백과 DatePickerDialog 기본 구현은 유지한다.
- F: `WorkoutRestTimerHost`가 화면과 무관하게 Lifecycle STARTED에서 종료를 소비한다. `RestTimerSchedule(ownerId, endsAtMillis, totalSeconds)`를 SharedPreferences에 저장하고 수신기와 UI가 동일 토큰을 한 번만 소비한다. 이미 수신기에서 소비한 타이머는 SavedStateHandle로 재생하지 않는다. 건너뛰기·세션 종료·계정 변경에서 예약을 취소하고 앱 재진입에서 오래된 알림을 지운다.
- F 예약: API 31+ 정확 알람 허용 시와 API 30 이하에서는 `setExactAndAllowWhileIdle`, 미허용 또는 권한 회수 시에는 `setAndAllowWhileIdle`을 쓴다. **대체 예약은 정확한 종료 시각을 보장하지 않으며 OS·절전 정책으로 크게 지연될 수 있다.** 정확 알람 설정 화면으로 자동 이동하지 않는다. 새 `lifecycle-process` 의존성 대신 단일 MainActivity의 시작·정지 수를 사용해 지연된 프로세스 ON_STOP 경계를 피했다. [알람 정책](https://developer.android.com/develop/background-work/services/alarms), [ProcessLifecycleOwner의 지연](https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner)
- G: `FitnessLoading.kt`가 FULL_SCREEN 96dp, SECTION 72dp, INLINE_CARD 52dp를 담당한다. 시작 화면은 즉시 표시하고 나머지 대상 화면은 300ms 지연·최소 500ms를 적용한다. 대기 중 빈 공간을 유지하고 `loading || showLoading` 동안 결과를 노출하지 않는다. 계정·날짜 키 변경 시 표시 상태를 버린다. 대표 접근성 문구는 고정하고 회전 문구는 시각 전용이다. 기존 직접 Orb 호출은 공통 컴포넌트에만 남았다. 저장 버튼·수치 진행률 같은 동작 진행 표시는 이 화면 로딩 범위와 구분한다.

수용 기준 중 기기에서만 확인 가능한 창 블러, 픽셀·성능, IME/회전, 실제 알람·Doze, TalkBack은 **미검증**이다. 신규 `WorkoutRestTimerHostTest`, 예약 주입 ViewModel 테스트, `FitnessLoadingTest`와 수정된 기존 UI 테스트는 소스 컴파일만 확인했다. APK 생성·설치나 테스트 실행 결과로 표현하지 않는다.

## 6. PriceTrace 통합 완료 범위

사용자 지시에 따라 기존 로컬 checkout과 PT 파일·패치·SHA-256 manifest를 별도 보관한 뒤, 원격 `3dfdbb0`에서 새 기능 브랜치를 만들었다. 기존 `feat/records-ux-clarity`의 커밋 및 관련 없는 미커밋 UI 작업은 보존한다.

C–G 완료 기록 `03ef344` 이후 PT 변경을 적용했다. 보관 목록의 14개 후보 경로를 비교했고, PT와 무관한 `NutritionFormComponents`의 footer 배치 변경은 제외했다. MealScreen의 날짜·관리 버튼 배치와 기존 테스트의 다른 수정도 가져오지 않았다. 실제 화면 진입에 필요한 `ComposeAppScreen`의 `openProductRegistration` 콜백은 추가로 연결했다. 결과적으로 제품 기능 14개 경로와 통합 회귀 테스트 2개 경로를 `9388c44`에 기록했다.

### 소유권과 데이터 흐름

1. 식단의 제품 직접 입력 또는 내 식단·식품 관리에서 같은 `NutritionEditorController`와 `NutritionProductPicker`를 사용한다. 기존 식단 장바구니의 owner·date를 저장해 복귀 대상을 제한한다.
2. `NutritionProductLookup`은 기존 `NutritionIntegrationService`의 공개 제품 검색과 공개 영양정보 읽기를 사용한다. 정확한 catalog ID·revision·용량·단위·개수가 일치하는 영양정보만 미리 채운다. 대기 중 입력이 바뀌면 늦은 응답을 버린다.
3. 선택한 제품 ID와 표준상품의 모든 variant를 SavedStateHandle에 보존한다. 동명이거나 여러 규격인 결과에서 임의의 ID를 만들지 않는다. 그룹 선택은 기준량에 유일하게 일치하는 규격만 exact link로 저장하며 모호하면 standard 참조만 보관한다.
4. `NutritionCatalogRepository.savePriceTraceProductInput`이 로컬 식품·영양값과 정확한 제품 연결을 같은 SQLite 트랜잭션에서 저장한다. 계정 변경·식별자 불일치·잘못된 metadata에서는 실패하며 수동 등록으로 조용히 전환하지 않는다.
5. 저장 성공은 기존 owner·date의 식단 초안에 식품을 추가하고 입력창으로 복귀한다. 식품 등록 자체로 Meal을 저장하지 않는다.

원격에 이미 있던 브랜드 카탈로그를 유지했다. 제품 선택 중에는 별도의 수동 브랜드 연결을 잠시 보관하고, 제품 선택 해제 시 원래 이름·브랜드·기준량·용량과 명시적 브랜드 ID를 복원한다. 재생성 후 복원도 계측 테스트 소스에 포함했다. 저장한 제품의 브랜드 편집 가능 여부는 repository 정책으로 다시 판단한다.

### PT 검증 및 보존 근거

- 최종 `testDebugUnitTest`: **591개 중 589 통과, 2 건너뜀, 실패·오류 0**. 위 C–G와 동일한 소스 검증 명령으로 `BUILD SUCCESSFUL`(37초)을 확인했다.
- `compileDebugJavaWithJavac`, `compileDebugAndroidTestKotlin`, `compileDebugAndroidTestJavaWithJavac` 통과. 신규 제품 선택·저장·장바구니 복귀와 브랜드 통합 계측 테스트는 **컴파일만** 확인했다.
- 신규 JVM codec 테스트 4개는 정확한 ID·revision·용량 복원, 여러 규격 유지, 다른 표준상품 혼합 거부, 손상된 SavedState 거부를 확인한다. 최초 실행은 신규 테스트의 JUnit Double 비교에 delta 인수가 빠져 1개 실패했고, 올바른 비교 인수를 넣은 뒤 전체 591개를 다시 실행했다.
- 원래 checkout과 별도 보관 스냅샷의 **60개 파일 SHA-256 전부 일치**를 재확인했다. 기존 `feat/records-ux-clarity` HEAD `8c06a28`과 미커밋 작업은 보존했다. 백업에는 파일별 manifest, 전체 tracked diff, PT 범위 diff와 원본 파일이 있다.
- 새 작업 트리에 `app/build/outputs/apk`가 생성되지 않았다. 추가 APK 생성·설치, 실기기 DB 접근, 계측 실행, 운영 서버 RPC·RLS 검증은 수행하지 않았다.
- 서버 schema·RLS·RPC 변경, 외부 카탈로그 원본 수정은 없다. 공개 API의 실제 운영 가용성과 계정별 권한 검증은 이 로컬 통합 결과에 포함하지 않는다.
