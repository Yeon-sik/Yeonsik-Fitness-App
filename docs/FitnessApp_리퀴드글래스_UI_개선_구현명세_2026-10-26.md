# FitnessApp 리퀴드 글래스 UI 개선 구현 명세

## 0. 문서 정보

| 항목 | 내용 |
|---|---|
| 작성일 | 2026-10-26 |
| 기준 커밋 | 원격 `main` `f15d22d` |
| 상태 | 구현 명세. PR-A·PR-B의 구현 및 검증 현황은 아래 작업 현황 문서에서 추적 |
| 근거 | 2026-10-26 코드 기반 UI/UX 점검 결과와 소유자 검토 결정 |
| 검증 상태 | A·B 단위 테스트·debug 빌드 통과. 계측·기기 검증 미수행(사용자 지정). 상세는 작업 현황 문서 참조 |
| 함께 읽을 문서 | `docs/FitnessApp_정돈_리팩터링_규칙.md`(필수), `docs/UI_DESIGN_SYSTEM.md` |
| 작업 현황·다음 시작점 | [구현 현황과 재개 지점](FitnessApp_리퀴드글래스_UI_작업현황_2026-10-26.md) |
| 최신 실행 제한 | 추가 APK 생성·설치 및 에뮬레이터·실기기 검증을 하지 않음. 이후 작업은 현황 문서의 사용자 지정 범위를 우선 |

이 문서는 다른 작업자가 추가 질문 없이 구현할 수 있도록 작성했다. 줄 번호는 기준 커밋 기준이며 이후 바뀔 수 있다. 위치는 **파일과 심볼 이름**으로 찾는다.

**읽기 기준:** 이 문서의 `CURRENT` 설명은 작성 기준인 `f15d22d`의 상태다. 이후 구현한 내용, 검증 결과, 사용자 지정 검증 범위와 다음 시작점은 [작업 현황 문서](FitnessApp_리퀴드글래스_UI_작업현황_2026-10-26.md)를 함께 읽는다. 모든 PR을 미구현으로 간주하거나 A·B를 다시 구현하지 않는다.

### 0.1 용어

- **CURRENT**: 기준 커밋의 실제 코드
- **TARGET**: 이 문서가 정한 도착 상태
- **글래스 지원 환경**: `Build.VERSION.SDK_INT >= 31`이고 시스템 고대비 텍스트가 꺼진 상태. 현재 코드의 `useBackdropGlass` 조건과 같다.
- **브랜드 파랑**: `#38BDF8` (`FitnessUiTokens.COLOR_BRAND_BLUE`)

---

## 1. 디자인 원칙

목표 디자인은 iOS 계열 리퀴드 글래스를 Android Compose로 옮긴 것이다. 아래 원칙을 모든 항목에 적용한다.

1. **글래스는 콘텐츠 위에 떠 있는 조작 층에만 쓴다.**
   - 대상: 하단 탭바, 기록 허브 탭, 휴식 타이머, 세션 상단 바, 시트·다이얼로그
   - 콘텐츠 카드는 평평한 표면을 쓰고, 필요하면 아주 약한 테두리만 둔다.
2. **글래스 뒤에는 실제 콘텐츠가 있어야 한다.**
   - 단색 배경을 흐리게 하는 글래스는 만들지 않는다.
   - 콘텐츠는 시스템 바와 플로팅 바 아래까지 흐른다(edge-to-edge).
3. **브랜드 파랑은 채움(fill)과 강조에만 쓴다.**
   - 라이트 모드의 글자와 아이콘에는 접근성 대비를 통과한 진한 파랑을 쓴다.
   - 큰 면을 브랜드 파랑으로 칠하지 않는다.
4. **대비 기준**
   - 본문 글자 4.5:1 이상
   - 큰 글자(18sp 이상, 또는 14sp 이상 굵게)와 의미 있는 아이콘·테두리·진행 표시 3:1 이상
5. **대체 경로를 항상 둔다.**
   - API 30 이하, 고대비 텍스트, 창 블러 비활성(절전 모드 등)에서는 불투명 표면과 테두리로 대체한다.
   - 대체 표면에서도 위계와 터치 동작이 같아야 한다.
6. **기능·데이터·내비게이션 동작은 바꾸지 않는다.**
   - 예외: 7번 휴식 알림과, 그 안의 "다른 화면에 있을 때 휴식 종료를 놓치는 문제" 수정. 별도 PR로 분리한다(리팩터링 규칙 11.3, 13.5).

---

## 2. 결정 요약

| 번호 | 항목 | 결정 | PR |
|---|---|---|---|
| P0-1 | 라이트 모드 파랑 대비 | 글자·아이콘용 진한 파랑 `#0369A1` 도입, `colorScheme.primary` 교체 | PR-A |
| P0-2 | 홈 카드 가짜 글래스 | 글래스 제거, **아주 약한 테두리 카드**로 교체 | PR-B |
| P0-3 | Thinking Orb 색 | 파랑 계열 4색 팔레트와 중심 발광 | PR-A |
| P1-4 | 떠 있는 요소 글래스화 | 공통 글래스 컴포넌트 신설, 휴식 타이머·세션 상단 바·다이얼로그·시트에 적용 | PR-D, PR-E |
| P1-5 | edge-to-edge | 루트 `systemBarsPadding` 제거, inset을 콘텐츠 패딩으로 이동, 상태바 스크림 | PR-C |
| P1-6 | 홈 상단 카드 | 브랜드 파랑 전면 채움 제거, 테마별 톤 표면과 브랜드 칩 | PR-B |
| P2-7 | 휴식 종료 | 앱이 실행 중이 아닐 때 **시스템 알림과 메시지만** 보냄 | PR-F |
| P2-8 | 뒤로가기 표기 | 모든 뒤로가기를 **방향 아이콘 하나로 통일, 글자 제거** | PR-B |
| P2-9 | 로딩 상태 | 공통 로딩 컴포넌트, 지연 표시, 크기·문구·접근성 통일 | PR-G |
| P2-10 | 홈 "빠른 이동" | 섹션 제목 **제거** | PR-B |
| P2-11 | 글래스 수치 | `FitnessUiTokens`로 토큰화(값은 현재 그대로) | PR-A |
| P2-12 | 글래스 라이브러리 | 버전 고정 유지, 라이브러리 사용을 한 파일로 격리, 대체 경로 테스트, 폐기 예정 API 교체 | PR-D |

---

## 3. PR 분할과 순서

리팩터링 규칙 13.1에 따라 PR을 업무 단위로 나눈다. 각 PR은 `main`에서 새 `feat/` 브랜치로 시작한다.

| 순서 | PR | 포함 항목 | 선행 PR |
|---|---|---|---|
| 1 | **PR-A** 토큰과 대비 | P0-1, P0-3, P2-11 | 없음 |
| 2 | **PR-B** 홈과 뒤로가기 정리 | P0-2, P1-6, P2-8, P2-10 | PR-A |
| 3 | **PR-C** edge-to-edge | P1-5 | PR-A |
| 4 | **PR-D** 공통 글래스 컴포넌트와 바 | P1-4의 컴포넌트·휴식 타이머·세션 상단 바, P2-12 | PR-A, PR-C |
| 5 | **PR-E** 글래스 다이얼로그·시트 | P1-4의 다이얼로그·시트 | PR-D |
| 6 | **PR-F** 휴식 종료 알림 | P2-7 | 없음(독립) |
| 7 | **PR-G** 로딩 상태 통일 | P2-9 | PR-A |

- PR-B, PR-C, PR-G는 PR-A 이후 병렬로 진행할 수 있다. 단, PR-B와 PR-C는 `ComposeHomeScreen.kt`의 같은 부분을 건드리지 않도록 조정한다.
- PR-F는 플랫폼 알림과 기록 흐름을 다루므로 UI PR과 섞지 않는다.

---

## 4. PR-A: 토큰과 대비

### 4.1 P0-1 라이트 모드 파랑 대비

#### CURRENT

- `core/ui/FitnessComposeTheme.kt`의 `colors()`가 라이트·다크 모두 `primary = #38BDF8`로 둔다.
- M3 `TextButton`(전체 약 143곳), `Checkbox`, `LinearProgressIndicator`, `OutlinedTextField` 포커스 테두리, `colorScheme.primary`를 쓰는 아이콘·글자가 라이트 모드에서 이 색을 전경색으로 쓴다.
- 측정 대비: `#38BDF8`은 흰 배경에서 2.14:1, `#F5F5F7` 배경에서 1.97:1이다. 글자와 UI 요소 기준 모두 미달이다.

#### TARGET 색 역할

| 역할 | 라이트 | 다크 | 비고 |
|---|---|---|---|
| 채움(버튼 배경, 선택 표시, 칩) | `#38BDF8` | `#38BDF8` | `LocalFitnessColors.current.action` |
| 채움 위 글자 | `#173B55` (5.47:1) | `#0E2938` (7.04:1) | `onAction` |
| 글자·아이콘·테두리·진행 표시 | **`#0369A1`** | `#38BDF8` | `colorScheme.primary` |
| `colorScheme.primary` 위 글자 | `#FFFFFF` (5.93:1) | `#0E2938` | `onPrimary` |

`#0369A1` 측정 대비: 흰색 5.93, `#F5F5F7` 5.45, `#EEEFF2` 5.16, `#E0F2FE` 5.17. 모두 4.5:1 이상이다.

#### 구현 절차

1. `app/src/main/java/com/yeonsik/fitnessapp/core/ui/FitnessUiTokens.java`에 토큰을 추가한다.
   ```java
   /** Text, icon and outline blue for light surfaces (WCAG AA on #FFFFFF·#F5F5F7·#EEEFF2·#E0F2FE). */
   public static final int COLOR_BRAND_BLUE_TEXT = 0xFF0369A1;
   public static final int COLOR_ON_BRAND_BLUE_TEXT = 0xFFFFFFFF;
   ```
2. `FitnessComposeTheme.kt`의 `colors(dark)`를 바꾼다.
   - 라이트: `primary = COLOR_BRAND_BLUE_TEXT`, `onPrimary = COLOR_ON_BRAND_BLUE_TEXT`
   - 다크: 지금 값(`#38BDF8`, `COLOR_D_ON_PASTEL_BLUE`)을 유지한다.
   - `inversePrimary`는 브랜드 파랑을 유지한다.
   - `LightSemantic.action`은 브랜드 파랑을 그대로 둔다. `FitnessButton`, `AppButton`처럼 채움에 `action`을 쓰는 코드는 바꾸지 않는다.
3. `colorScheme.primary`를 **채움**으로 쓰는 곳을 찾아 `LocalFitnessColors.current.action`으로 바꾼다. 아래 명령으로 찾는다.
   ```bash
   grep -rnE "(color|containerColor|background\()[^\n]*colorScheme\.primary\b|colors\.primary\b" app/src/main/kotlin --include=*.kt
   ```
   - 확인된 대상: `ComposeHomeScreen.kt`의 `HomeTodayHero`(`color = colors.primary`), `FitnessVisualComponents.kt`의 기록 마커 기본값, `StatisticsScreen.kt`의 증감 점.
   - `HomeTodayHero`는 PR-B(P1-6)에서 다시 설계하므로, PR-A에서는 `color = LocalFitnessColors.current.action`, `contentColor = LocalFitnessColors.current.onAction`로 바꿔 지금 모양을 유지한다. 이 단계에서 상단 카드 내부의 `colorScheme.onPrimary` 참조도 `LocalFitnessColors.current.onAction`으로 바꾼다.
   - 기록 마커와 통계 점은 진행 표시이므로 진한 파랑(`primary`)으로 바뀌어도 된다. 바꾸지 않는다.
4. 전경색으로 쓰이는 `TextButton`은 별도 수정이 필요 없다. M3 기본값이 `primary`라 자동으로 진한 파랑이 된다.
5. View 기반 코드(`ui/FitnessUi.java`)에서 `COLOR_BRAND_BLUE`를 글자색으로 쓰는 곳이 있는지 확인한다. 현재 화면에서 렌더링되는 View라면 같은 토큰으로 바꾸고, 렌더링되지 않는 잔존 코드는 범위에서 제외한다고 PR 설명에 적는다.

#### 테스트

- JVM 단위 테스트 `app/src/test/.../core/ui/FitnessColorContrastTest.kt`를 추가한다.
  - WCAG 상대 휘도 공식으로 토큰 정수값의 대비를 계산한다. Android 의존성 없이 작성한다.
  - 검증 쌍(전경, 배경, 최소 대비):
    - 라이트 `primary` 대 `COLOR_SURFACE`, `COLOR_BACKGROUND`, `COLOR_SUBTLE`, `COLOR_BLUE_CONTAINER`: 4.5
    - 라이트 `onAction` 대 `action`: 4.5
    - 다크 `primary` 대 `COLOR_D_SURFACE`, `COLOR_D_SUBTLE`: 4.5
    - 다크 `onAction` 대 `action`: 4.5
    - PR-B에서 상단 카드 토큰이 생기면 그 쌍을 추가한다(6.6절).

#### 수용 기준

- 라이트 모드의 모든 `TextButton` 글자, 포커스된 입력칸 테두리, 체크박스, 진행 표시가 `#0369A1`로 보인다.
- 채움 버튼과 하단 탭 선택 표시는 브랜드 파랑을 유지한다.
- 다크 모드 화면에는 변화가 없다.

### 4.2 P0-3 Thinking Orb 색

#### CURRENT

- `core/ui/ThinkingOrb.kt`의 `ThinkingOrb`는 `primary`, `secondary`, `tertiary`, `onSurfaceVariant`를 쓴다.
- 테마에서 `secondary`, `tertiary`, `onSurfaceVariant`가 모두 muted 회색이라 입자의 3/4이 회색이다.

#### TARGET

- 테마 역할색 대신 Orb 전용 팔레트를 쓴다.

  | 인덱스 | 라이트 | 다크 | 의미 |
  |---|---|---|---|
  | 0 | `#38BDF8` | `#38BDF8` | 브랜드 |
  | 1 | `#06B6D4` | `#22D3EE` | 청록(활동 히트맵 `#00FFFF` 계열) |
  | 2 | `#6366F1` | `#A5B4FC` | 남보라 |
  | 3 | `#0EA5E9` | `#E0F2FE` | 밝은 하이라이트 |

  라이트 3번은 흰 배경에서 보이지 않는 연한 색 대신 `#0EA5E9`를 쓴다.
- 중심 발광을 추가한다.
  - 구 중심에 방사형 그라데이션: 브랜드 파랑 → 투명
  - 반지름은 `size.minDimension * 0.5f`
  - 중심 알파: 라이트 0.16, 다크 0.26
  - 회전 위상에 맞춰 ±15% 맥동: `alpha * (0.85f + 0.15f * sin(rotation))`
  - 애니메이션이 꺼지면 맥동 없이 정적으로 그린다.
- 입자 개수(88), 회전 주기(8초), `Paint` 재사용 구조는 유지한다.

#### 구현 절차

1. `FitnessUiTokens.java`에 `COLOR_ORB_0..3`, `COLOR_D_ORB_0..3`, `ORB_GLOW_ALPHA_LIGHT = 0.16f`, `ORB_GLOW_ALPHA_DARK = 0.26f`를 추가한다.
2. `ThinkingOrb`에서 `dark` 여부는 `MaterialTheme.colorScheme.background.luminance() < 0.5f`로 판단하거나, 테마가 제공하는 값이 있으면 그것을 쓴다. `colors` 배열을 팔레트 토큰으로 교체한다.
3. 발광은 `Canvas` 안에서 입자보다 먼저 그린다. `Brush.radialGradient` 객체는 `drawWithCache` 또는 `remember(size)`로 캐시해서 프레임마다 새로 만들지 않는다.
4. `testTag("thinking-orb")`는 유지한다.

#### 수용 기준

- 라이트·다크 모두에서 회색 입자가 없다.
- 시스템 애니메이션이 꺼져 있으면 정적 이미지로 보인다.
- 기존 `OrbLoadingStatusTest`, `DevelopmentLoadingUiTest`, `MealOverviewLoadingUiTest`, `RecordsLoadingUiTest`가 통과한다.

### 4.3 P2-11 글래스 수치 토큰화

#### CURRENT

`app/navigation/ComposeAppScreen.kt`의 private 함수에 글래스 수치가 직접 들어 있다.

| 위치 | 하드코딩 값 |
|---|---|
| `navigationGlassSurface` | 채도 1.04, 블러 `NAV_GLASS_BLUR_RADIUS_DP`(7), 표면 알파 0.72 |
| `bottomNavigationGlassSurface` | 광택 흰색 알파 라이트 0.28/다크 0.10, 하단 표면 알파 0.10, 블러 8dp, 렌즈 10dp/16dp, 하이라이트 0.65, 표면 알파 라이트 0.30/다크 0.34 |
| `bottomNavigationGlassSelection` | 블러 3dp, 렌즈 4dp/7dp, 하이라이트 0.55, 틴트 알파 0.72 |
| `glassTabFeedback` | 눌림 0.10/0.08, 포커스 0.09/0.07, 호버 0.05, 라이트 틴트 `#28313A`, 지속 시간 80/160ms |
| `BottomNavigation` | 진행 중 배지 테두리 `#343C43`(다크)/흰색 |

#### TARGET

- 위 값을 **값 변경 없이** `FitnessUiTokens.java`의 상수로 옮긴다. 이 항목은 순수 리팩터링이며 화면 결과가 같아야 한다.
- 이름 규칙: `GLASS_<용도>_<속성>[_LIGHT|_DARK]`

  ```java
  // Liquid glass — bar level (bottom navigation, records hub tabs, rest timer, session bar)
  public static final float GLASS_BAR_BLUR_DP = 8f;
  public static final float GLASS_BAR_LENS_HEIGHT_DP = 10f;
  public static final float GLASS_BAR_LENS_AMOUNT_DP = 16f;
  public static final float GLASS_BAR_HIGHLIGHT_ALPHA = 0.65f;
  public static final float GLASS_BAR_SURFACE_ALPHA_LIGHT = 0.30f;
  public static final float GLASS_BAR_SURFACE_ALPHA_DARK = 0.34f;
  public static final float GLASS_BAR_SHEEN_TOP_ALPHA_LIGHT = 0.28f;
  public static final float GLASS_BAR_SHEEN_TOP_ALPHA_DARK = 0.10f;
  public static final float GLASS_BAR_SHEEN_BOTTOM_ALPHA = 0.10f;
  // Liquid glass — selection level
  public static final float GLASS_SELECTION_BLUR_DP = 3f;
  public static final float GLASS_SELECTION_LENS_HEIGHT_DP = 4f;
  public static final float GLASS_SELECTION_LENS_AMOUNT_DP = 7f;
  public static final float GLASS_SELECTION_HIGHLIGHT_ALPHA = 0.55f;
  public static final float GLASS_SELECTION_TINT_ALPHA = 0.72f;
  // Liquid glass — plain level (legacy navigationGlassSurface)
  public static final float GLASS_PLAIN_SATURATION = 1.04f;
  public static final float GLASS_PLAIN_SURFACE_ALPHA = 0.72f;
  // Press feedback
  public static final float GLASS_PRESS_ALPHA_LIGHT = 0.10f;
  public static final float GLASS_PRESS_ALPHA_DARK = 0.08f;
  public static final float GLASS_FOCUS_ALPHA_LIGHT = 0.09f;
  public static final float GLASS_FOCUS_ALPHA_DARK = 0.07f;
  public static final float GLASS_HOVER_ALPHA = 0.05f;
  public static final int GLASS_PRESS_TINT_LIGHT = 0xFF28313A;
  public static final int GLASS_BADGE_STROKE_DARK = 0xFF343C43;
  public static final int GLASS_PRESS_IN_MILLIS = 80;
  public static final int GLASS_PRESS_OUT_MILLIS = 160;
  ```
- 기존 `NAV_GLASS_BLUR_RADIUS_DP`(7)는 `GLASS_PLAIN_BLUR_DP`로 이름을 바꾸고, 기존 이름은 같은 값을 가리키는 `@Deprecated` 별칭으로 한 PR 동안 남겼다가 PR-D에서 제거한다.
- PR-E에서 쓸 시트 토큰도 함께 정의한다.
  ```java
  public static final float GLASS_SHEET_SURFACE_ALPHA_LIGHT = 0.78f;
  public static final float GLASS_SHEET_SURFACE_ALPHA_DARK = 0.72f;
  public static final int GLASS_SHEET_WINDOW_BLUR_DP = 24;
  public static final float GLASS_STROKE_ALPHA_LIGHT = 0.55f;
  public static final float GLASS_STROKE_ALPHA_DARK = 0.18f;
  ```

#### 수용 기준

- `ComposeAppScreen.kt`에 글래스 관련 숫자 리터럴과 `Color(0x...)`가 남지 않는다.
- 변경 전후 하단 탭바·기록 허브 탭 스크린샷이 같다(라이트·다크).

---

## 5. PR-B: 홈과 뒤로가기 정리

### 5.1 P0-2 홈 카드: 약한 테두리 카드

#### CURRENT

- `feature.home.ui` 패키지의 `ComposeHomeScreen.kt`에서 `HomeGlassPanel`은 자기 아래에 단색 그라데이션(`surface` → `surfaceContainerHigh`)만 깔고 그것을 블러·렌즈 처리한다.
- 비쳐 보일 콘텐츠가 없어 결과가 그라데이션 카드와 거의 같고, RenderEffect 비용만 든다.
- 사용처: `HomeGlassWorkoutQuickActions`, `HomeGlassQuickActionCard`. 홈 안의 `highContrast`, `useGlass` 계산은 이 두 곳만 쓴다.

#### TARGET

- 홈의 빠른 이동 카드는 글래스를 쓰지 않는다.
- 평평한 `surface` 카드 + **아주 약한 1dp 테두리**를 쓴다.
  - 라이트: `outlineVariant`(`#E1E3E8`) 알파 0.60
  - 다크: `outlineVariant`(`#393D45`) 알파 0.50
  - 고대비 텍스트가 켜져 있으면 알파 1.0
  - 그림자(elevation) 없음
- 무산소·유산소 묶음 카드와 식단·체중 단일 카드는 같은 테두리를 쓴다.

#### 구현 절차

1. `core/ui/FitnessComposeComponents.kt`의 `FitnessCard`에 `outlined: Boolean = false` 인자를 추가한다. `true`이면 `Card`의 `border`에 위 테두리를 적용한다.
2. 테두리 알파를 토큰으로 정의한다: `CARD_HAIRLINE_ALPHA_LIGHT = 0.60f`, `CARD_HAIRLINE_ALPHA_DARK = 0.50f`.
3. 고대비 텍스트 감지 로직(`Build.VERSION.SDK_INT >= 36 && AccessibilityManager.isHighContrastTextEnabled`)이 `ComposeAppScreen.kt`와 `ComposeHomeScreen.kt`에 중복돼 있다. `core/ui`에 `rememberHighContrastTextEnabled(): Boolean`으로 추출해 두 곳 모두 이것을 쓴다.
4. `HomeGlassPanel`과 `com.kashif_e.backdrop` import를 홈에서 제거한다. `HomeGlassWorkoutQuickActions`는 `HomeWorkoutQuickActions`, `HomeGlassQuickActionCard`는 `HomeQuickActionCard`로 이름을 바꾼다.
5. 두 카드 모두 `FitnessCard(outlined = true)`를 쓴다. 클릭 카드는 `onClick`을 넘긴다.
6. 테스트 태그 `home-quick-workout`, `home-quick-strength`, `home-quick-cardio`, `home-quick-meal`, `home-quick-weight`는 그대로 둔다.

#### 수용 기준

- 홈에서 `drawBackdrop`, `layerBackdrop` 호출이 0개다.
- 기존 `HomeDestinationUiTest`, `HomeUsabilityUiTest`, `HomeActivityHistoryUiTest`의 빠른 이동 관련 검증이 통과한다(5.4절 변경 제외).

### 5.2 P1-6 홈 상단 카드

#### CURRENT

- `HomeTodayHero`가 카드 전체를 브랜드 파랑(`colors.primary`)으로 채운다. 다크 모드에서 큰 밝은 면이 눈부시다.
- `UI_DESIGN_SYSTEM.md`의 "브랜드 파랑은 액션·선택·중요 지표에만" 원칙과 맞지 않는다.
- `HomeTodayHero.kt`의 `HomeHeroWeightIncreaseColor`(`#0B4020`)와 `HomeHeroWeightDecreaseColor`(`#7F1D1D`)는 파랑 면을 전제로 고른 색이라 다른 표면에서는 대비가 맞지 않는다.

#### TARGET 디자인

브랜드 파랑을 면이 아니라 **칩 하나와 진행 표시 강조**로 줄인다. 같은 색을 적게 써서 더 기억에 남게 하는 방향이다.

| 요소 | 라이트 | 다크 |
|---|---|---|
| 카드 배경 | 단색 `#F0F9FF` | 세로 그라데이션 `#12394D` → `#10324A` |
| 카드 테두리 1dp | `#BAE6FD` | `#2A526A` (`COLOR_D_HERO_BORDER`) |
| 제목 "오늘" | `#1C1C1E` | `#D9F0FF` |
| "n/3 달성" | **브랜드 칩**: 배경 `#38BDF8`, 글자 `#173B55`, 높이 28dp, 좌우 패딩 10dp, 캡슐 모양 | 배경 `#38BDF8`, 글자 `#0E2938` |
| 영역 라벨·보조 글자 | `#686C75` (4.94:1) | `#AFBAC4` (6.19:1 / 6.75:1) |
| 지표 값 | `#1C1C1E` | `#D9F0FF` |
| 체중 증가 | `#2E7D5B` (4.69:1) | `#69D39E` (6.64:1) |
| 체중 감소 | `#C0453E` (4.73:1) | `#FF8A80` (5.35:1) |
| 진행 막대 트랙 | `#1C1C1E` 알파 0.10 | `#D9F0FF` 알파 0.18 |
| 진행 막대 채움·완료 마커 | 기존 `FitnessRecordMarkerColors` 유지 | 유지 |
| 미완료 마커 테두리 | `#686C75` | `#AFBAC4` |

- 대비 값은 모두 카드 배경 기준으로 측정했다. 다크 그라데이션은 아래로 갈수록 어두워져 아래쪽 대비가 더 높다.
- 라이트 배경에 발광·그라데이션을 넣지 않는다. 보조 글자 대비가 4.5:1 아래로 떨어지기 때문이다.
- 모양(`FitnessShape.hero`, 24dp), 패딩 20dp, 간격, 애니메이션, 테스트 태그는 유지한다.

#### 구현 절차

1. `FitnessUiTokens.java`에 추가한다.
   ```java
   public static final int COLOR_HERO_SURFACE = 0xFFF0F9FF;
   public static final int COLOR_HERO_BORDER = 0xFFBAE6FD;
   public static final int COLOR_D_HERO_SURFACE_TOP = 0xFF12394D;
   public static final int COLOR_D_HERO_SURFACE_BOTTOM = 0xFF10324A;
   ```
   - 기존 `COLOR_D_HERO_END`(`#214A63`)는 View 코드가 별칭으로 참조하므로 값을 바꾸지 않는다. Compose 상단 카드에서는 쓰지 않는다. `#214A63`에서는 `#FF8A80` 대비가 4.14:1로 미달이다.
2. `core/ui`에 상단 카드 색 묶음을 만든다.
   ```kotlin
   @Immutable
   data class FitnessHeroColors(
       val surface: Brush, val border: Color, val title: Color, val value: Color,
       val muted: Color, val increase: Color, val decrease: Color,
       val track: Color, val markerOutline: Color,
       val chipContainer: Color, val chipContent: Color
   )
   val LocalFitnessHeroColors = staticCompositionLocalOf { LightHeroColors }
   ```
   `FitnessComposeTheme`에서 `dark`에 따라 제공한다.
3. `HomeTodayHero`의 `Surface(color = colors.primary)`를 `Box` + `background(heroColors.surface, FitnessShape.hero)` + `border(1.dp, heroColors.border, FitnessShape.hero)`로 바꾼다. `CompositionLocalProvider(LocalContentColor provides heroColors.title)`로 기본 글자색을 준다.
4. `HomeTodayHero.kt`에서 모든 `colors.onPrimary` 참조를 위 표의 역할색으로 바꾼다.
5. "n/3 달성" `Text`를 칩으로 감싼다. 태그 `home-hero-completion-count`는 `Text`에 그대로 둔다.
6. `HomeHeroWeightIncreaseColor`, `HomeHeroWeightDecreaseColor`를 삭제하고 `heroColors.increase`, `heroColors.decrease`를 쓴다.
   - `androidTest/.../HomeTodayHeroUiTest.kt` 86~87행이 이 상수를 참조한다. 테스트를 `LightHeroColors.increase`, `LightHeroColors.decrease` 기준으로 바꾼다. 테스트 테마가 다크라면 다크 값으로 바꾼다.
7. `FitnessColorContrastTest`에 표의 글자 쌍을 모두 추가한다. 다크는 `SURFACE_TOP`과 `SURFACE_BOTTOM` 양쪽에 대해 검증한다.

#### 수용 기준

- 라이트·다크 상단 카드에서 브랜드 파랑 면적은 칩 하나뿐이다.
- 모든 글자 쌍이 4.5:1 이상이다(단위 테스트로 보장).
- `HomeTodayHeroUiTest`가 통과한다.

### 5.3 P2-8 뒤로가기 아이콘 통일

#### CURRENT

뒤로가기와 이전·다음 이동을 텍스트 문자로 그리고, 표기가 섞여 있다.

| 위치 | 현재 표기 | 의미 |
|---|---|---|
| `core/ui/AppComposeComponents.kt` `AppHeader` | `"뒤로"` | 뒤로가기 |
| `core/ui/FitnessComposeComponents.kt` `FitnessHeader` | `"‹ 뒤로"` | 뒤로가기 |
| `core/ui/FitnessComposeComponents.kt` `FitnessSessionTopBar` | `"←"` (접근성 설명 없음) | 뒤로가기 |
| `core/ui/FitnessVisualComponents.kt` 월 이동 | `"‹"`, `"›"` | 이전·다음 달 |
| `feature/records/ui/RecordsNavigationComponents.kt` | `"‹"`, `"›"` | 이전·다음 달 |
| `feature/meal/ui/MealScreen.kt` 날짜 이동 | `"‹"`, `"›"` | 이전·다음 날짜 |
| `feature.home.ui` 패키지의 `ComposeHomeActivityHistory.kt` | `"‹"`, `"›"` | 이전·다음 기간 |
| `feature/workout/ui/WorkoutScreens.kt` 166행 부근 | `"›"` | 다음·진입 표시 |

전체 목록은 아래 명령으로 다시 확인한다.

```bash
grep -rnE "\"(‹ )?뒤로\"|\"←\"|\"‹\"|\"›\"|\"→\"" app/src/main/kotlin --include=*.kt
```

#### TARGET

- **뒤로가기**는 글자 없이 왼쪽 방향 아이콘 하나로 통일한다.
  - 아이콘: `FitnessBackIcon` (24dp, 선 굵기 1.9, 둥근 끝). `FitnessActionNextIcon`과 좌우 대칭인 경로 `moveTo(15,6) lineTo(9,12) lineTo(15,18)`
  - 버튼: `IconButton`, 최소 48×48dp 터치 영역
  - 색: `colorScheme.onSurface`. 파랑을 쓰지 않는다.
  - 접근성 설명: `"뒤로"`
- **이전·다음 이동**도 같은 아이콘 계열을 쓴다. 이전은 `FitnessBackIcon`, 다음은 기존 `FitnessActionNextIcon`. 기존 접근성 설명("이전 달", "이전 날짜", "이전 기간" 등)과 비활성 알파 0.38 규칙은 유지한다.
- 두 아이콘은 `autoMirror = true`로 만들어 RTL에서 방향이 뒤집히게 한다.

#### 구현 절차

1. `core/ui/FitnessActionIcons.kt`에 `FitnessBackIcon`을 추가하고, `FitnessActionNextIcon`에 `autoMirror = true`를 준다.
2. `core/ui`에 공통 버튼을 만든다.
   ```kotlin
   @Composable
   fun FitnessBackButton(onClick: () -> Unit, modifier: Modifier = Modifier,
                         contentDescription: String = "뒤로")
   ```
3. `AppHeader`, `FitnessHeader`, `FitnessSessionTopBar`의 뒤로가기를 `FitnessBackButton`으로 바꾼다.
   - `FitnessHeader`는 지금처럼 제목 위 별도 줄에 둔다. 왼쪽 정렬, 시작 오프셋 `-12dp`로 아이콘 외곽을 페이지 여백과 맞춘다.
4. 이전·다음 `TextButton { Text("‹") }`은 `IconButton { Icon(FitnessBackIcon) }` 형태로 바꾼다. `enabled`와 기존 `semantics`, `testTag`(예: `home-activity-previous`)는 버튼에 그대로 둔다.
5. 테스트를 갱신한다.
   - `androidTest/.../cardio/ui/CardioStartUiTest.kt` 60행: `onNodeWithText("‹ 뒤로")` → `onNodeWithContentDescription("뒤로")`
   - `androidTest/.../routine/ui/RoutineExerciseHistoryUiTest.kt` 58행: `hasText("‹ 뒤로")` → `hasContentDescription("뒤로")`

#### 수용 기준

- 위 grep 결과가 0건이다.
- TalkBack에서 뒤로가기는 "뒤로, 버튼"으로 읽힌다.
- 모든 아이콘 버튼의 터치 영역이 48dp 이상이다.

### 5.4 P2-10 "빠른 이동" 제거

#### CURRENT

`ComposeHomeScreen.kt` 111행 `FitnessSection("빠른 이동") {}`: 내용 없는 섹션 제목이다. 섹션 상단 여백 26dp와 카드 간격 12dp가 겹친다.

#### TARGET과 절차

1. 해당 `TopLevelEntranceContent(entrance, order = 1) { FitnessSection("빠른 이동") {} }` 블록을 삭제한다.
2. 뒤의 `order` 값을 하나씩 당긴다: 2→1, 3→2, 4→3, 5→4. `TopLevelEntrance.kt`의 순차 등장 애니메이션이 빈 단계 없이 이어지게 하기 위해서다.
3. 오늘 상태 섹션과 첫 빠른 이동 카드 사이 간격은 `Column`의 `spacedBy(FitnessSpacing.gap)`(12dp)만 남는다. 시각적으로 상단 카드와 빠른 이동이 붙어 보이면 첫 빠른 이동 카드에 `padding(top = FitnessSpacing.small)`(8dp)를 더한다. 실기기 확인 후 결정하고 PR에 기록한다.
4. 테스트를 갱신한다.
   - `HomeDestinationUiTest.kt` 61행, `HomeActivityHistoryUiTest.kt` 105행의 `onNodeWithText("빠른 이동").assertExists()` → `assertDoesNotExist()`

---

## 6. PR-C: P1-5 edge-to-edge

### CURRENT

- `ComposeAppScreen.kt`의 루트 `Box`가 `.background(background).systemBarsPadding().imePadding()`를 쓴다(1096행).
- 콘텐츠가 상태바와 내비게이션 바 아래로 흐르지 않아, 글래스 바 뒤에 비칠 콘텐츠가 줄어든다.
- `MainActivity.applySystemBarAppearance`는 API 35 이상에서 바 색 지정을 생략하고 아이콘 밝기만 정한다. 이 부분은 이미 올바르다.

### TARGET

- 화면 배경과 스크롤 콘텐츠가 상태바와 내비게이션 바 아래까지 그려진다.
- 각 요소의 inset 처리:

  | 요소 | inset 처리 |
  |---|---|
  | 루트 `Box` | `systemBarsPadding()` 제거. `background`와 `imePadding()` 유지 |
  | 스크롤 콘텐츠(`destinationPage`의 `Column`) | 상단 패딩 = 상태바 높이 + 기존 값, 하단 패딩 = 내비게이션 바 높이 + 기존 값 |
  | `BottomNavigation` | `navigationBarsPadding()` 후 기존 하단 여백 |
  | `RecordsHubTabs` | `statusBarsPadding()` 후 기존 위치 |
  | 세션 상단 바(PR-D 이후 오버레이) | `statusBarsPadding()` |
  | 가로 모드 | `WindowInsets.displayCutout`과 `navigationBars`의 좌우 값을 페이지 좌우 패딩에 더한다 |
  | `StartupOrbScreen` | 변경 없음(자체 `systemBarsPadding` 유지) |
  | 다이얼로그·시트 | 변경 없음(별도 창) |

- **상태바 스크림**: 콘텐츠가 시계·아이콘과 겹치지 않도록 화면 최상단에 그라데이션을 둔다.
  - 높이: 상태바 높이 + 12dp
  - 색: `colorScheme.background` 알파 0.92 → 0.0 (세로)
  - 터치를 소비하지 않는다.
  - 글래스 지원 환경에서도 블러는 쓰지 않는다. 비용 대비 효과가 낮다.
- API 26~34에서도 같은 동작을 보장한다. `MainActivity.onCreate`에서 `setContentView` 전에 `WindowCompat.setDecorFitsSystemWindows(getWindow(), false)`를 호출한다. `MainActivity`는 `ComponentActivity`이므로 `androidx.activity.EdgeToEdge.enable(this)`를 써도 된다. 이 경우 `applySystemBarAppearance`의 아이콘 밝기 지정과 충돌하지 않는지 확인한다. 둘 중 하나만 쓴다.

### 구현 절차

1. `ComposeAppScreen.kt` 루트에서 `.systemBarsPadding()`을 제거한다.
2. `destinationPage`의 스크롤 `Column` `padding` 계산에 `WindowInsets.statusBars.asPaddingValues().calculateTopPadding()`과 `WindowInsets.navigationBars...calculateBottomPadding()`을 더한다. `bottomNavigationSpace` 계산에도 내비게이션 바 높이를 포함한다.
3. 스크롤하지 않는 화면(`isExercisePickerDestination`, `CARDIO`, `ROUTINE_DETAIL`)도 같은 상하 패딩을 받는지 확인한다.
4. `RestTimerBar`가 루트 `Column` 하단에 있으므로 `navigationBarsPadding()`을 준다. PR-D에서 오버레이로 바뀐다.
5. `core/ui`에 `FitnessStatusBarScrim()`을 만들고 루트 `Box`의 마지막 자식(`Alignment.TopCenter`)으로 둔다.
6. 키보드가 열린 입력 화면에서 `imePadding`과 하단 내비게이션 바 패딩이 이중으로 적용되지 않는지 확인한다. 필요하면 `WindowInsets.navigationBars.exclude(WindowInsets.ime)` 등으로 조정한다.

### 검증

- 제스처 내비게이션, 3버튼 내비게이션, 가로 모드, 펀치홀 기기에서 상단 제목·하단 탭이 가려지지 않는다.
- 키보드가 열린 식단 입력과 세트 입력에서 입력칸이 가려지지 않는다.
- 리팩터링 규칙 14.4 Compose PR 필수 검증 항목을 모두 기록한다.

---

## 7. PR-D: 공통 글래스 컴포넌트와 바 (P1-4, P2-12)

### 7.1 공통 글래스 컴포넌트

#### CURRENT

글래스 구현은 `ComposeAppScreen.kt` 안의 private 함수(`navigationGlassSurface`, `bottomNavigationGlassSurface`, `bottomNavigationGlassSelection`, `glassTabFeedback`)와 홈의 `HomeGlassPanel`에만 있다. 다른 화면은 재사용할 수 없다.

#### TARGET 구조

새 파일 `app/src/main/kotlin/com/yeonsik/fitnessapp/core/ui/FitnessGlass.kt`. **`com.kashif_e.backdrop`을 import하는 유일한 파일**이 된다.

```kotlin
enum class FitnessGlassLevel { BAR, SELECTION, PLAIN }

/** 글래스 지원 여부. 루트에서 한 번 계산해 제공한다. */
val LocalFitnessGlassEnabled = staticCompositionLocalOf { false }

/** 페이지 콘텐츠를 담은 backdrop. 루트 콘텐츠 레이어가 제공한다. */
val LocalFitnessPageBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun Modifier.fitnessGlassSurface(
    shape: Shape,
    level: FitnessGlassLevel = FitnessGlassLevel.BAR,
    backdrop: Backdrop? = LocalFitnessPageBackdrop.current
): Modifier

@Composable
fun Modifier.fitnessGlassPressFeedback(interactionSource: MutableInteractionSource): Modifier

@Composable
fun rememberFitnessLayerBackdrop(): Backdrop   // rememberLayerBackdrop 위임

fun Modifier.fitnessBackdropSource(backdrop: Backdrop): Modifier  // layerBackdrop 위임
```

동작 규칙:

- `LocalFitnessGlassEnabled`가 `false`이거나 `backdrop`이 `null`이면 **대체 표면**을 그린다.
  - `clip(shape)` + `background(surfaceContainerHigh)` + 1dp 테두리(`outlineVariant`, 고대비면 `onSurface`)
  - 현재 `navigationGlassSurface`의 대체 분기와 같다.
- `BAR`는 현재 `bottomNavigationGlassSurface`, `SELECTION`은 `bottomNavigationGlassSelection`, `PLAIN`은 `navigationGlassSurface`의 글래스 분기와 같은 효과다. 수치는 4.3절 토큰을 쓴다.
- API 33 미만에서는 지금처럼 `vibrancy`와 `lens`를 생략한다.
- 다크 여부는 `FitnessComposeTheme`이 제공하는 값으로 판단한다. `dark` 인자를 함수마다 넘기지 않는다.

#### 구현 절차

1. 위 파일을 만들고 `ComposeAppScreen.kt`의 private 글래스 함수 네 개를 옮긴다. `BottomNavigation`, `RecordsHubTabs`는 새 API를 쓴다.
2. 루트(`ComposeAppScreen.kt`의 `FitnessComposeTheme` 블록 안)에서 아래를 계산해 `CompositionLocalProvider`로 제공한다.
   - `glassEnabled = SDK_INT >= 31 && !rememberHighContrastTextEnabled()`
   - **`bottomNavigationVisible` 조건을 빼서** 세션 화면에서도 글래스가 켜지게 한다.
   - `LocalFitnessPageBackdrop`에는 루트 콘텐츠 `Column`의 backdrop을 넣는다.
3. 루트 `Column`의 `layerBackdrop` 조건을 `useBackdropGlass`에서 `glassEnabled`로 바꾼다.
4. 기록 허브 탭은 지금처럼 페이지별 `recordsBackdrop`을 명시적으로 넘긴다.

### 7.2 휴식 타이머: 떠 있는 글래스 캡슐

#### CURRENT

`RestTimerBar`(1329행)는 루트 `Column` 맨 아래에 붙은 불투명 `surfaceVariant` 띠다. 접힘·펼침, 건너뛰기, 남은 시간, 진행 막대가 있다.

#### TARGET

- 화면 하단에 떠 있는 캡슐로 바꾼다. 기능과 문구는 그대로 둔다.
  - 위치: 루트 `Box`의 `Alignment.BottomCenter`. `navigationBarsPadding()` 후 하단 12dp, 좌우 16dp, 최대 너비 400dp
  - 모양: 접힘 상태 캡슐(`RoundedCornerShape(50%)`), 펼침 상태 `RoundedCornerShape(28.dp)`. 모양 전환은 `animateContentSize(tween(180))`와 함께 반지름을 애니메이션한다.
  - 표면: `fitnessGlassSurface(shape, BAR)`
  - 접힘 레이아웃(최소 높이 56dp): `[휴식 · 펼치기 TextButton] [남은 시간 headlineSmall, tnum] [건너뛰기 TextButton]`. 진행 막대는 캡슐 안쪽 아래 가장자리에 높이 3dp로 둔다.
  - 펼침 레이아웃: 지금 내용 그대로(큰 시간, 안내 문구, 진행 막대)
  - 남은 시간 글자에 `fontFeatureSettings = "tnum"`을 적용해 숫자 폭이 흔들리지 않게 한다.
- **배치 제약**: 글래스는 자기를 포함한 레이어를 샘플링할 수 없다. 타이머는 반드시 `layerBackdrop`이 적용된 루트 `Column` **밖**, 즉 `BottomNavigation`과 같은 레벨의 형제 오버레이로 둔다.
- 타이머가 보이는 동안 스크롤 콘텐츠의 하단 패딩에 캡슐 높이 + 12dp를 더해 마지막 항목이 가려지지 않게 한다. 높이는 `onSizeChanged`로 측정한다(기록 허브 탭의 `recordsBarHeightPx`와 같은 방식).
- 종료 판정 로직은 PR-F에서 앱 레벨로 옮긴다. PR-D에서는 표시만 바꾼다.
- 폐기 예정 API `LinearProgressIndicator(progress = ratio, ...)`(1386행)를 `LinearProgressIndicator(progress = { ratio }, ...)`로 바꾼다.

### 7.3 세션 상단 바

#### CURRENT

- `SessionTopBar`(1259행)는 운동 세션 페이지 `Column` 맨 위에 불투명 `surface`로 붙어 있다.
- `CardioSessionTopBar`(1303행)는 루트 `Column` 맨 위에 있다.

#### TARGET

- **운동 세션 상단 바**: 페이지 콘텐츠 위 오버레이로 바꾸고 글래스를 적용한다.
  - 페이지의 `Column`을 `Box`로 바꾼다. 콘텐츠가 먼저, 상단 바는 `Alignment.TopCenter` 오버레이로 둔다.
  - 상단 바: `statusBarsPadding()` 후 좌우 12dp, 상단 8dp, 모양 `RoundedCornerShape(28.dp)`, `fitnessGlassSurface(shape, BAR)`
  - 콘텐츠 상단 패딩에 바 높이를 더한다(`onSizeChanged`로 측정).
  - 페이지 콘텐츠 `Column`에 페이지 전용 backdrop(`fitnessBackdropSource`)을 적용하고 상단 바에 넘긴다. 기록 허브 탭과 같은 패턴이다.
  - 뒤로가기는 PR-B의 `FitnessBackButton`, "운동 취소"는 `colorScheme.error` 글자를 유지한다.
- **유산소 세션 상단 바**: **글래스를 적용하지 않고 불투명 표면을 유지한다.**
  - 이유: 유산소 화면의 경로 지도는 `AndroidView(MapView)`다. MapView는 GL 표면에 그려지므로 Compose 레이어 backdrop에 캡처되지 않는다. 글래스를 적용하면 흐린 빈 화면만 보인다.
  - `statusBarsPadding()`만 추가한다.

### 7.4 P2-12 라이브러리 안정성

#### CURRENT

`app/build.gradle` 284행 `io.github.kashif-mehmood-km:backdrop:0.0.1-alpha02`. 알파 버전이고, 하단 탭바·기록 탭이 이 라이브러리에 의존한다.

#### TARGET과 절차

1. 정확한 버전 문자열을 유지한다. `+`, 범위, `latest.release`를 쓰지 않는다.
2. `build.gradle`의 의존성 위에 주석을 단다.
   ```groovy
   // Liquid glass. Alpha library: keep an exact version, import only from core/ui/FitnessGlass.kt,
   // and upgrade only in a dedicated PR with light/dark screenshots of every glass surface.
   ```
3. `com.kashif_e.backdrop` import가 `core/ui/FitnessGlass.kt`에만 있는지 검사하는 JVM 단위 테스트를 추가한다. 테스트는 `app/src/main/kotlin` 아래 소스 파일을 텍스트로 읽어 다른 파일의 import를 찾으면 실패한다.
4. 대체 경로 instrumentation 테스트 `core/ui/FitnessGlassFallbackUiTest.kt`를 추가한다.
   - `LocalFitnessGlassEnabled provides false`로 하단 탭바와 휴식 타이머를 렌더링한다.
   - 탭 선택, 휴식 건너뛰기가 동작하는지 확인한다.
   - 캡슐 가장자리 탭이 아래 콘텐츠로 전달되지 않는지 확인한다(현재 `detectTapGestures(onTap = {})` 동작 유지).
5. API 30 에뮬레이터(글래스 미지원)에서 하단 탭바·휴식 타이머·세션 상단 바를 한 번 이상 실행하고 결과를 PR에 기록한다.

### 수용 기준

- `ComposeAppScreen.kt`에 `drawBackdrop` 직접 호출이 없다.
- 운동 세션 화면에서 스크롤하면 콘텐츠가 상단 바와 휴식 캡슐 뒤로 흐리게 비친다.
- 고대비 텍스트를 켜거나 API 30 이하에서는 불투명 표면과 테두리로 보이고 기능이 같다.

---

## 8. PR-E: 글래스 다이얼로그·시트 (P1-4)

### CURRENT

- `AlertDialog` 약 43곳, `Dialog` 약 52곳, `ModalBottomSheet` 8곳이 M3 기본 표면(`surfaceContainerHigh`, 회색)을 쓴다.
- 파일 목록은 아래 명령으로 확인한다.
  ```bash
  grep -rlE "AlertDialog\(|ModalBottomSheet\(|\bDialog\(" app/src/main/kotlin --include=*.kt
  ```

### 기술 제약

- 다이얼로그와 M3 바텀시트는 **별도 창(window)** 에 그려진다. 앱 루트의 Compose 레이어 backdrop을 다른 창에서 샘플링할 수 없다.
- 그래서 시트·다이얼로그 글래스는 **플랫폼 창 블러**로 구현한다(API 31 이상).
  - `Window.setBackgroundBlurRadius(px)`: 창 자체 배경 블러
  - `WindowManager.LayoutParams.FLAG_BLUR_BEHIND` + `attributes.blurBehindRadius`: 뒤 창 블러
  - `WindowManager.isCrossWindowBlurEnabled()`: 기기·절전 모드에 따라 꺼질 수 있다. `addCrossWindowBlurEnabledListener`로 상태를 받아 대체 표면으로 바꾼다.

### TARGET 컴포넌트

`core/ui/FitnessGlassDialogs.kt`에 아래 래퍼를 만든다.

```kotlin
@Composable
fun FitnessAlertDialog(/* AlertDialog와 같은 인자 */)

@Composable
fun FitnessModalBottomSheet(/* ModalBottomSheet와 같은 인자 */)

@Composable
internal fun FitnessDialogWindowGlass()   // 현재 창을 찾아 블러를 설정하는 내부 효과
```

동작 규칙:

- 창 블러가 가능할 때(`LocalFitnessGlassEnabled && SDK >= 31 && isCrossWindowBlurEnabled`):
  - `containerColor = surface.copy(alpha = GLASS_SHEET_SURFACE_ALPHA_LIGHT/DARK)` (0.78/0.72)
  - 창 배경 블러 `GLASS_SHEET_WINDOW_BLUR_DP`(24dp)를 px로 변환해 적용
  - 상단 가장자리 1dp 하이라이트 테두리: 흰색 알파 `GLASS_STROKE_ALPHA_LIGHT/DARK`
  - 다이얼로그 뒤 dim은 M3 기본(scrim)을 유지한다.
- 불가능할 때: `containerColor = surfaceContainerHigh`, 테두리 `outlineVariant` 1dp. 지금과 거의 같다.
- 모양: 다이얼로그 `RoundedCornerShape(28.dp)`, 시트 상단 `28.dp`.

창 획득 방법:

- `AlertDialog`·`Dialog`: 콘텐츠 안에서 `(LocalView.current.parent as? DialogWindowProvider)?.window`
- `ModalBottomSheet`: 사용 중인 material3 버전(Compose BOM `2025.02.00`)에서 시트 콘텐츠의 `LocalView`가 `DialogWindowProvider`를 제공하는지 **구현 첫 단계에서 확인한다.**
  - 제공하면 같은 방식으로 블러를 적용한다.
  - 제공하지 않으면 시트는 대체 표면을 쓰고, 이 결과를 이 문서 8절에 추가 기록한다.
- 블러 설정은 `DisposableEffect`에서 하고, `onDispose`에서 리스너를 해제한다.

### 이행 절차

1. 래퍼를 만들고 `BodyMetricsEditorScreen.kt`의 다이얼로그 하나에 먼저 적용해 실기기에서 확인한다.
2. 확인되면 나머지 호출부를 파일 단위 커밋으로 교체한다. 인자와 동작은 바꾸지 않는다.
3. 직접 `Dialog { Surface { ... } }`를 그리는 곳은 `Surface`의 색과 모양만 같은 규칙으로 바꾼다.

### 수용 기준

- 다이얼로그·시트 뒤의 화면이 흐리게 비친다(API 31 이상, 창 블러 허용 기기).
- 절전 모드에서 창 블러가 꺼지면 즉시 불투명 표면으로 바뀌고 글자가 읽힌다.
- 다이얼로그 입력·포커스·뒤로가기 동작이 같다(규칙 14.4).

---

## 9. PR-F: P2-7 휴식 종료 알림

### 9.1 결정

- 앱이 실행 중이 아니거나 백그라운드에 있을 때 휴식이 끝나면 **시스템 알림과 메시지만** 보낸다.
  - 제목: `휴식 종료`
  - 본문: `다음 세트를 시작하세요.`
  - 액션 버튼, 진행 중 카운트다운 알림, 앱 내 햅틱은 추가하지 않는다.
  - 알림을 누르면 앱을 연다(`MainActivity`, 특정 화면 딥링크 없음). 누르면 사라진다(`autoCancel`).
- 앱이 포그라운드일 때는 지금처럼 앱 내 토스트(`휴식 종료. 다음 세트를 시작하세요.`)를 쓰고, 시스템 알림은 보내지 않는다.
- 함께 고치는 결함(리팩터링 규칙 13.5에 따라 **별도 커밋과 회귀 테스트**):
  - CURRENT: 종료 판정 `LaunchedEffect`가 `RestTimerBar` 안에 있어 운동 세션·종목 상세 화면이 보일 때만 돈다.
  - 결과: 앱이 켜져 있어도 다른 탭에 있으면 휴식이 끝나도 알림이 없다가, 세션 화면으로 돌아오는 순간 토스트가 뜬다.
  - TARGET: 종료 판정을 앱 레벨로 옮겨 현재 화면과 상관없이 정시에 토스트를 띄운다. 타이머 캡슐 표시 조건(세션·상세 화면에서만 보임)은 그대로 둔다.

### 9.2 CURRENT 상태 저장

- `feature/workout/ui/WorkoutSessionViewModel.kt`
  - `WorkoutRestTimerState.Active(ownerId, endsAtMillis, totalSeconds)`
  - `startRestTimer`, `stopRestTimer`가 `SavedStateHandle`에 저장·삭제한다.
  - `restoreRestTimerState`는 종료 시각이 지났으면 `Inactive`로 복원한다.
- 알림 인프라는 `cardio/CardioTrackingService.java`의 GPS 채널(`cardio_tracking`)뿐이다.
- `POST_NOTIFICATIONS` 권한은 매니페스트에 있고, 유산소 시작 때만 요청한다(`MainActivity.continuePendingCardioAction`).
- `androidx.lifecycle:lifecycle-process` 의존성은 없다.

### 9.3 TARGET 구조

| 구성요소 | 위치 | 책임 |
|---|---|---|
| `RestTimerAlarmScheduler` (interface) | `feature/workout/api/RestTimerAlarmScheduler.kt` | `schedule(endsAtMillis: Long)`, `cancel()` |
| `AndroidRestTimerAlarmScheduler` | `feature/workout/application/AndroidRestTimerAlarmScheduler.kt` | `AlarmManager`로 예약·취소 |
| `RestTimerAlarmReceiver` | `feature/workout/application/RestTimerAlarmReceiver.kt` | 알람 수신, 포그라운드 여부 판단, 알림 게시 |
| `RestTimerNotifications` | `feature/workout/application/RestTimerNotifications.kt` | 채널 생성, 알림 게시·취소. 채널·알림 ID 상수 |
| `RestTimerNotificationPolicy` | `feature/workout/application/RestTimerNotificationPolicy.kt` | 순수 함수: 게시 여부, 예약 방식 결정 |

- `AppContainer`가 `AndroidRestTimerAlarmScheduler`를 만들고, `AppViewModelFactory`가 `WorkoutSessionViewModel`에 주입한다. ViewModel은 `AlarmManager`를 직접 참조하지 않는다(리팩터링 규칙 4.1).
- 알림 본문에는 계정·운동 정보를 넣지 않는다.

### 9.4 동작 명세

**예약**

- `WorkoutSessionViewModel.startRestTimer`가 상태를 저장한 뒤 `scheduler.schedule(endsAtMillis)`를 호출한다.
- 같은 `PendingIntent`(요청 코드 고정, `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`)를 써서 새 타이머가 이전 예약을 덮어쓴다.
- 예약 방식:
  - API 31 이상이면서 `alarmManager.canScheduleExactAlarms()`가 참: `setExactAndAllowWhileIdle(RTC_WAKEUP, endsAtMillis, pi)`
  - API 30 이하: `setExactAndAllowWhileIdle(...)`. 권한이 필요 없다.
  - 그 외(정확 알람 미허용): `setWindow(RTC_WAKEUP, endsAtMillis, 10_000L, pi)`. Android 공식 가이드의 권한 미승인 시 우아한 저하 방식이다. 수 초~수십 초 늦을 수 있다.
- 매니페스트에 `android.permission.SCHEDULE_EXACT_ALARM`을 추가한다. 앱이 이 권한을 요청하는 설정 화면으로 사용자를 보내지 않는다. `USE_EXACT_ALARM`은 Play 정책 제한 권한이므로 추가하지 않는다.

**취소**

다음 경우 `scheduler.cancel()`과 `RestTimerNotifications.cancel()`을 호출한다.

- `stopRestTimer()`(건너뛰기, 0초 휴식 포함)
- 운동 완료·취소·삭제로 세션이 끝날 때
- 로그아웃·계정 전환 때(`SettingsSessionCoordinator` 등 계정 경계 처리 지점)
- 앱이 포그라운드로 돌아올 때 이미 게시된 휴식 알림(오래된 알림 정리)

**수신**

- `RestTimerAlarmReceiver`는 매니페스트에 `android:exported="false"`로 등록한다.
- `onReceive`에서:
  1. 앱 프로세스가 포그라운드인지 확인한다. `ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)`. 이를 위해 `androidx.lifecycle:lifecycle-process:2.8.7`(기존 lifecycle과 같은 버전)을 추가한다.
  2. 포그라운드면 아무것도 하지 않는다. 앱 레벨 종료 판정이 토스트를 띄운다.
  3. 아니면 알림 권한(API 33 이상 `POST_NOTIFICATIONS`)과 `NotificationManagerCompat.areNotificationsEnabled()`를 확인한다. 허용돼 있을 때만 게시한다.
- 프로세스가 종료돼 있으면 수신기가 새 프로세스에서 실행된다. 이때 포그라운드 판단은 거짓이므로 알림을 게시한다.

**채널**

- ID `rest_timer`, 이름 `휴식 타이머`, 설명 `휴식이 끝나면 알려줍니다`
- 중요도 `IMPORTANCE_HIGH`(헤드업 표시). 사운드·진동은 시스템 채널 기본값을 따르고 코드에서 별도 패턴을 지정하지 않는다.
- 앱 시작 시와 게시 직전에 생성한다(중복 생성은 무해).

**알림**

- `NotificationCompat.Builder(context, "rest_timer")`
- 작은 아이콘: 앱의 기존 단색 알림 아이콘. 없으면 `CardioTrackingService`가 쓰는 아이콘을 재사용한다.
- `setContentTitle("휴식 종료")`, `setContentText("다음 세트를 시작하세요.")`
- `setCategory(NotificationCompat.CATEGORY_ALARM)`, `setAutoCancel(true)`
- `setContentIntent`: `MainActivity`를 여는 `PendingIntent`(`FLAG_IMMUTABLE`, `FLAG_ACTIVITY_SINGLE_TOP | FLAG_ACTIVITY_CLEAR_TOP`)
- 알림 ID 상수 하나를 쓴다. 휴식 알림은 동시에 하나만 존재한다.

**권한 요청**

- API 33 이상에서 `POST_NOTIFICATIONS`가 없으면, **처음 휴식 타이머를 시작할 때 한 번** 요청한다.
  - 요청 여부를 `SharedPreferences`(키 `rest_timer_notification_permission_requested`)에 저장해 반복 요청하지 않는다.
  - 요청은 `AppUiActions`에 `requestRestTimerNotificationPermissionIfNeeded()`를 추가해 `MainActivity`가 수행한다. 기존 유산소 권한 흐름과 요청 코드를 분리한다.
  - 거부되면 조용히 넘어간다. 포그라운드 토스트는 계속 동작한다.

**앱 레벨 종료 판정(결함 수정)**

- `RestTimerBar` 안의 종료 판정 `LaunchedEffect`를 `ComposeAppScreen`의 루트 레벨로 옮긴다.
- 조건: `restState is Active && restState.ownerId == ownerId`. 현재 화면(`screen`)과 무관하다.
- 종료 시 `stopRestTimer()` 후 `host.toast("휴식 종료. 다음 세트를 시작하세요.")`
- `RestTimerBar`는 남은 시간 표시용 시계만 유지한다.

### 9.5 테스트

- JVM 단위 테스트 `RestTimerNotificationPolicyTest`
  - 포그라운드이면 게시하지 않는다.
  - 백그라운드이고 권한이 있으면 게시한다.
  - 백그라운드이고 권한이 없으면 게시하지 않는다.
  - 정확 알람 허용 여부와 API 레벨에 따른 예약 방식 선택
- JVM 단위 테스트(`WorkoutSessionViewModel`): 가짜 `RestTimerAlarmScheduler`로
  - `startRestTimer`가 `schedule(endsAtMillis)`를 호출한다.
  - `stopRestTimer`, `restSeconds == 0`, 세션 종료가 `cancel()`을 호출한다.
- 회귀 테스트(결함 수정): 홈 탭에 있는 상태에서 타이머가 끝나면 토스트 이벤트가 발생한다.

### 9.6 실기기 검증 시나리오

각 결과를 14.5 형식으로 기록한다.

1. 세트 완료 → 홈 버튼으로 앱 백그라운드 → 종료 시각에 헤드업 알림 표시
2. 세트 완료 → 최근 앱에서 앱 스와이프 종료 → 종료 시각에 알림 표시
3. 화면 꺼짐(Doze 진입 전) 상태에서 알림 표시
4. 앱을 연 채 다른 탭에 있을 때 → 토스트만 표시, 시스템 알림 없음
5. 건너뛰기 → 알림 없음
6. 알림 권한 거부 → 알림 없음, 앱 오류 없음
7. 정확 알람 미허용(API 33 이상 신규 설치 기본값) → 알림이 늦게라도 표시되고, 지연 시간을 기록
8. 알림 탭 → 앱이 열리고 알림이 사라짐

---

## 10. PR-G: P2-9 로딩 상태 통일

### 10.1 CURRENT

| 위치 | 표시 | 크기 | 문구 | 접근성 |
|---|---|---|---|---|
| `StartupOrbScreen` (`core/ui/ThinkingOrb.kt`) | Orb + 문구 | 96dp | `기록을 불러오는 중` | `stateDescription` |
| `OrbLoadingStatus` (발전 화면, 논문 조언) | 카드 안 Orb + 순환 문구 | 52dp | 여러 문구 1.4초 순환 | `stateDescription`(순환마다 변경) |
| `RecordsCalendarLoading` (`RecordsScreen.kt`) | Orb + 첫 문구만 | 104dp | 목록의 첫 문구 | `stateDescription`만, liveRegion·진행 정보 없음 |
| 식단 하루 요약 (`MealScreen.kt` 285행 부근) | Orb + 문구 | 52dp(기본값) | `불러오는 중` | liveRegion + 진행 정보 |
| `LoadingHome` (`ComposeHomeScreen.kt`) | 글자만 | 없음 | `오늘 상태를 불러오는 중입니다.` | 없음 |

크기·문구·접근성이 모두 다르고, 짧은 로컬 조회에서도 Orb가 즉시 나타났다 사라질 수 있다.

### 10.2 TARGET 설계

**공통 컴포넌트** `core/ui/FitnessLoading.kt`

```kotlin
enum class FitnessLoadingVariant { FULL_SCREEN, SECTION, INLINE_CARD }

@Composable
fun FitnessLoadingState(
    messages: List<String>,          // 1개 이상. 첫 문구가 접근성 대표 문구
    variant: FitnessLoadingVariant,
    modifier: Modifier = Modifier
)
```

| variant | 레이아웃 | Orb 크기 | 사용처 |
|---|---|---|---|
| `FULL_SCREEN` | 화면 중앙 세로 배치, Orb 아래 24dp에 `titleMedium` | 96dp | 앱 시작 |
| `SECTION` | 영역 중앙 세로 배치, Orb 아래 12dp에 `bodyMedium`(`onSurfaceVariant`) | 72dp | 기록 달력, 식단 하루 요약, 홈 |
| `INLINE_CARD` | 카드 안 가로 배치, Orb 오른쪽에 순환 문구 | 52dp | 발전 보고서, 논문 조언 |

Orb 크기는 토큰으로 정의한다: `ORB_SIZE_FULL_SCREEN_DP = 96`, `ORB_SIZE_SECTION_DP = 72`, `ORB_SIZE_INLINE_DP = 52`.

**지연 표시와 최소 표시 시간**

- 로딩이 300ms 안에 끝나면 Orb를 보여주지 않는다(깜빡임 방지).
- 한 번 보이면 최소 500ms는 유지한다(번쩍임 방지).
- 지연 중에도 **같은 크기의 빈 공간을 미리 차지**해 로딩이 끝날 때 레이아웃이 튀지 않게 한다.
- 구현:
  ```kotlin
  @Immutable data class FitnessLoadingTiming(val showDelayMillis: Long = 300, val minVisibleMillis: Long = 500)
  val LocalFitnessLoadingTiming = staticCompositionLocalOf { FitnessLoadingTiming() }

  @Composable
  fun rememberDelayedLoadingVisible(loading: Boolean): Boolean
  ```
  - 호출부는 `val showLoading = rememberDelayedLoadingVisible(isLoading)`로 결과 콘텐츠 표시도 함께 미룬다. 최소 표시 시간 동안은 데이터가 준비돼도 로딩을 유지한다.
  - 테스트에서는 `LocalFitnessLoadingTiming provides FitnessLoadingTiming(0, 0)`으로 즉시 표시한다.
- `FULL_SCREEN`(앱 시작)은 지연을 적용하지 않는다. 시작 화면은 원래 빈 화면 대신 보이는 것이 목적이다.

**접근성**

- 컨테이너에 `liveRegion = Polite`, `progressBarRangeInfo = Indeterminate`, `contentDescription = messages.first()`를 준다.
- 순환 문구는 시각 전용이다. 순환 `Text`에 `clearAndSetSemantics {}`를 적용해, 1.4초마다 스크린리더가 다시 읽지 않게 한다. 지금 `OrbLoadingStatus`는 `stateDescription`이 순환마다 바뀌어 반복 낭독될 수 있다.
- 시스템 애니메이션이 꺼져 있으면 Orb와 문구 모두 정적이다(현재 동작 유지).

**문구 규칙**

- 형식: `<대상>을/를 불러오는 중` (마침표 없음, 존댓말 어미 없음)
- 예: `기록을 불러오는 중`, `오늘 상태를 불러오는 중`, `식단을 불러오는 중`, `발전 보고서를 불러오는 중`
- 순환 문구는 실제 작업 단계를 설명할 때만 쓴다(발전 보고서·논문 조언의 기존 목록 유지).

### 10.3 이행 절차

1. `FitnessLoading.kt`를 만든다. 기존 `OrbLoadingStatus`와 `RollingStatusText`는 `INLINE_CARD` 구현으로 옮긴다. 기존 함수는 새 함수를 호출하는 얇은 별칭으로 한 PR 동안 남겼다가 같은 PR 마지막 커밋에서 호출부를 모두 바꾸고 제거한다.
2. `StartupOrbScreen` → `FitnessLoadingState(listOf("기록을 불러오는 중"), FULL_SCREEN)`
3. `RecordsCalendarLoading` → `SECTION`. 지연 표시 적용
4. 식단 하루 요약 로딩 → `SECTION`, 문구 `식단을 불러오는 중`. 지연 표시 적용
5. `LoadingHome` → `SECTION`, 문구 `오늘 상태를 불러오는 중`. 지연 표시 적용
6. 발전 화면·논문 조언 → `INLINE_CARD`
7. 기존 테스트 태그를 유지한다: `thinking-orb`, `orb-loading-card`, `rolling-status-slot`, `records-calendar-loading-content`, `meal-day-overview-loading`. 태그는 각 호출부의 `modifier`로 넘긴다.
8. 지연 표시 때문에 기존 로딩 UI 테스트(`OrbLoadingStatusTest`, `DevelopmentLoadingUiTest`, `MealOverviewLoadingUiTest`, `RecordsLoadingUiTest`)가 깨지면 `LocalFitnessLoadingTiming(0, 0)`을 제공하도록 고친다. 지연 동작 자체는 `mainClock.autoAdvance = false`와 `advanceTimeBy`로 별도 테스트한다.
   - 299ms에는 Orb가 없다.
   - 300ms 이후 Orb가 있다.
   - 표시 후 100ms에 로딩이 끝나도 500ms까지는 유지된다.

### 10.4 수용 기준

- 앱의 모든 로딩 표시가 세 variant 중 하나다. `ThinkingOrb(`를 직접 호출하는 곳은 `FitnessLoading.kt`뿐이다.
- TalkBack에서 로딩 시작 때 대표 문구를 한 번만 읽는다.
- 로컬 조회가 빠른 기기에서 Orb가 깜빡이지 않는다.

---

## 11. 검증 매트릭스

모든 PR은 `AGENTS.md`와 리팩터링 규칙 14장을 따른다.

| PR | 최소 명령 | instrumentation | 실기기 확인 |
|---|---|---|---|
| PR-A | `.\gradlew.bat testDebugUnitTest assembleDebug` | 로딩·홈 UI 테스트 | 라이트·다크에서 TextButton·입력칸·Orb |
| PR-B | 같음 | 홈·상단 카드·뒤로가기 관련 테스트 | 홈 라이트·다크, TalkBack 뒤로가기 |
| PR-C | 같음 | 전체 UI 테스트 | 제스처·3버튼·가로·키보드 |
| PR-D | 같음 | `FitnessGlassFallbackUiTest`, 운동 세트 기록 테스트 | API 31+ 기기와 API 30 에뮬레이터, 고대비 |
| PR-E | 같음 | 다이얼로그 사용 화면 테스트 | 창 블러 허용 기기, 절전 모드 |
| PR-F | 같음 | 운동 세션 테스트 | 9.6 시나리오 1~8 |
| PR-G | 같음 | 로딩 UI 테스트 4종 | 빠른 기기·느린 기기, TalkBack |

결과는 PR마다 아래 다섯 상태를 따로 적는다: **단위 테스트**, **Android 빌드**, **instrumentation**, **실기기**, **운영 RPC·RLS**(이 명세의 PR은 해당 없음으로 기록).

화면이 바뀌는 PR(A~E, G)은 변경 전후 스크린샷을 라이트·다크로 첨부한다.

---

## 12. 범위 밖

- 기록·동기화·DB schema·외부 계약 변경
- 새 기능 추가(휴식 +30초, 알림 액션 버튼, 진행 중 카운트다운 알림 등)
- 앱 내 햅틱 추가
- 유산소 지도 화면의 글래스화(7.3절 사유)
- 콘텐츠 카드의 글래스화(1장 원칙 1)
- View 기반 잔존 화면의 디자인 변경(4.1절 5단계의 확인만 수행)
- `model_image/` 운동 이미지 변경

---

## 13. 부록: 대비 측정값

WCAG 2.x 상대 휘도 공식으로 계산했다.

| 전경 | 배경 | 대비 | 판정 |
|---|---|---|---|
| `#38BDF8` | `#FFFFFF` | 2.14 | 글자·UI 미달 (CURRENT) |
| `#38BDF8` | `#F5F5F7` | 1.97 | 글자·UI 미달 (CURRENT) |
| `#0369A1` | `#FFFFFF` | 5.93 | 통과 |
| `#0369A1` | `#F5F5F7` | 5.45 | 통과 |
| `#0369A1` | `#EEEFF2` | 5.16 | 통과 |
| `#0369A1` | `#E0F2FE` | 5.17 | 통과 |
| `#173B55` | `#38BDF8` | 5.47 | 통과 |
| `#0E2938` | `#38BDF8` | 7.04 | 통과 |
| `#38BDF8` | `#1C1E22` | 7.79 | 통과 |
| `#38BDF8` | `#272A30` | 6.71 | 통과 |
| `#686C75` | `#F0F9FF` | 4.94 | 통과 |
| `#2E7D5B` | `#F0F9FF` | 4.69 | 통과 |
| `#C0453E` | `#F0F9FF` | 4.73 | 통과 |
| `#2E7D5B` | `#E0F2FE` | 4.36 | 미달 (라이트 상단 카드에 `#E0F2FE`를 쓰지 않는 이유) |
| `#D9F0FF` | `#12394D` | 10.39 | 통과 |
| `#AFBAC4` | `#12394D` | 6.19 | 통과 |
| `#69D39E` | `#12394D` | 6.64 | 통과 |
| `#FF8A80` | `#12394D` | 5.35 | 통과 |
| `#FF8A80` | `#10324A` | 5.83 | 통과 |
| `#FF8A80` | `#214A63` | 4.14 | 미달 (`COLOR_D_HERO_END`를 쓰지 않는 이유) |
