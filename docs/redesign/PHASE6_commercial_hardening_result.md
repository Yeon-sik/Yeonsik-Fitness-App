# FitnessApp PHASE 6 결과 — Commercial Surface Hardening

## 문서 상태와 근거 경계

- 대상 브랜치: **feat/redesign_all**
- 범위: PHASE 6의 설정 사용자화, surface 분리, 공통 상태, 접근성 기반 보강 및
  P6 security hardening 보정
- 근거 경계: Phase 6 구현 커밋 d6e21a2, security 보정 커밋 4b718ad와
  아래에 기록한 로컬 명령
- 이 문서는 실기기·스토어·운영 환경의 성공을 주장하지 않는다.

## 반영한 내용

### 1. Personal / Test-Friends / Commercial surface

AppSurfacePolicy가 BuildConfig의 FITNESS_SURFACE를 읽어 다음 정책을 적용한다.

| Surface | 기본 variant | 개발자 연결 UI | managed Supabase 기본값 | debug session provisioning | 로컬 설정/세션 저장 |
| --- | --- | --- | --- | --- | --- |
| Personal | debug | 허용 | 허용 | 허용 | 기존 namespace 유지 |
| Test-Friends | qa | 차단 | 차단 | 차단 | test-friends 별도 namespace |
| Commercial | release | 차단 | 차단 | 차단 | commercial 별도 namespace |

QA/release variant에는 Supabase, Nutrition, PriceTrace 연결값을 빈 BuildConfig 값으로
넣는다. 따라서 개인 환경값을 빌드 결과에 재사용하지 않으며, DB schema·migration·sync
contract는 변경하지 않았다. Personal OS, Nutrition, PriceTrace의 기존 ownership 경계도
그대로 둔다.

debug session provisioning은 Personal surface에서만 허용한다. MainActivity의 기존
BuildConfig.DEBUG와 AppSurfacePolicy 조합 가드는 유지하며, QA가 debuggable
variant여도 Test-Friends 정책이 경로를 차단한다. Commercial도 동일하게 차단한다.

FITNESS_SURFACE는 미지정일 때만 personal을 기본값으로 사용한다. 명시된 값이
허용 목록(personal, test-friends, commercial)에 없거나 빈 값이면 Gradle
configuration 단계에서 실제 입력값과 허용 목록을 포함한 오류를 내고 중단한다.
Runtime의 unknown, null, blank 값도 PERSONAL로 fallback하지 않고 COMMERCIAL의
최소 권한 정책으로 처리한다.

### 2. 일반 사용자 설정 화면

비개발자 surface의 설정에는 계정, 동기화 상태, 테마, 백업·복원·CSV 내보내기,
FLEEK CSV 가져오기, 개인정보 및 보안, 앱 정보만 표시한다.

URL, anon key, project ref, PriceTrace 관리자 세션, DB topology, raw sync detail은
비개발자 surface에서 렌더링하지 않는다. Personal surface에서는 기존 개발자 연결
카드와 내부 상태 확인 경로를 유지한다.

### 3. 공통 상태와 접근성 기반

- UiState에 loading, empty, offline, permission required, sync delayed,
  validation error, server error, destructive confirmation, success를 정의했다.
- FitnessUi.stateBadge()가 상태 텍스트와 semantic color를 함께 제공한다.
- 상태 점은 TalkBack에서 중복 읽히지 않게 제외하고, 배지에는 상태 설명을 둔다.
- 공통 버튼은 최소 52dp, 기존 칩·텍스트 액션은 최소 48dp 정책을 유지한다.
- 기존 FormSystem의 loading/error 처리와 FitnessUi.emptyStateCard()를 공통 상태
  기반으로 계속 사용한다.
- 기존 Android 15 system bar/display cutout inset 처리는 유지한다.

## 공통 component inventory

| 책임 | 공통 구현 | 사용 원칙 |
| --- | --- | --- |
| 실행 액션 | FitnessUi.primaryButton, secondaryButton, textButton | 버튼 문구와 TalkBack 설명을 함께 제공 |
| 선택 상태 | FitnessUi.chip, filterButton | 선택 여부를 텍스트 설명에도 반영 |
| 상태 배지 | FitnessUi.statusDotBadge, stateBadge | 색만 사용하지 않고 상태 텍스트 제공 |
| 빈 상태 | FitnessUi.emptyStateCard, BaseScreen.emptyState | 화면별 빈 카드 재구현 금지 |
| 로딩/오류 | FormSystem.loading, showError, clearError | 입력 폼의 독자적인 popup 대신 공통 처리 |
| 파괴적 액션 확인 | FitnessUi.confirmSheet | 삭제·복원 등 확인 문법 통일 |
| 시스템 inset | MainActivity.applySystemBarInsets | status bar, cutout, navigation 영역을 루트에서 흡수 |

## UI token 기준

P1~P4에서 확립한 Pastel Blue 중심의 semantic token과 White/Dark surface hierarchy를
변경하지 않았다. 이번 변경은 상태 배지와 설정 카드가 기존 FitnessUi 색상·깊이·
간격 토큰을 사용하도록 연결하는 수준이다. Dark shadow/glow나 하단 navigation
라벨·구조는 변경하지 않았다.

## 검증 결과

| 명령 | 결과 | 범위 |
| --- | --- | --- |
| .\gradlew.bat testDebugUnitTest | 통과 | 기존 unit test와 AppSurfacePolicy/SettingsScreen/UiState 테스트 |
| .\gradlew.bat generateQaBuildConfig generateReleaseBuildConfig | 통과 | QA/release BuildConfig 생성 |
| .\gradlew.bat compileQaJavaWithJavac compileReleaseJavaWithJavac | 통과 | QA/release Java 컴파일 |
| FITNESS_SURFACE=prod로 Gradle configuration 실행 | 의도된 실패 | Invalid FITNESS_SURFACE 'prod'. Allowed values: personal, test-friends, commercial. |
| git diff --check | 통과 | whitespace/patch 검사 |

생성된 variant 값은 debug=personal, qa=test-friends, release=commercial이며,
qa/release의 managed-default 허용값은 false이고 Supabase/Nutrition/PriceTrace
연결값은 빈 문자열이다. 이는 로컬 소스·생성 결과 확인이지 실제 설치·실기기·운영
DB 연결 검증은 아니다.

## Screenshot / device QA

아래 항목은 d6e21a2 및 4b718ad 기준으로 실행하지 않았고 실기기 확인이 필요하다.

- 홈, 피트니스, 루틴 목록·상세, 운동 세션, 종목 선택
- 식단, 외식 입력, 영양제
- 기록 달력, 분석, 설정
- 공통 bottom sheet / popup
- 각 화면 Light/Dark 캡처 비교
- 작은 화면, 일반 20:9, 큰 화면
- gesture navigation, 3-button navigation, status/navigation inset
- TalkBack 순서·contentDescription
- 큰 text scale의 clipping/overlap
- 키보드/IME action과 focus order
- 실제 release 서명 APK의 설치·실행

## 남은 UI debt

1. 실기기 screenshot set과 접근성 수동 검증이 필요하다.
2. 모든 화면의 loading/empty/offline/error 상태가 새 UiState 배지로 완전히
   연결되었는지는 추가 화면별 audit가 필요하다. 기존 FormSystem과 empty state
   구현은 유지되지만, 이번 변경에서 모든 화면을 대규모 재작성하지 않았다.
3. Commercial의 완전한 인증·계정 서버·운영 격리는 비범위다. 이번 변경은
   production endpoint 자동 주입과 내부 연결 UI 노출을 차단하는 표면/환경 경계만
   다룬다.
4. Compose/architecture migration은 필요성을 확인할 수 있는 runtime·유지보수
   근거가 아직 없으므로 이번 Phase6에서 시작하지 않았다.

## 변경하지 않은 경계

DB schema, migration, 동기화 계약, nutrition 계산, PriceTrace/OCR 연계 계약,
하단 navigation 라벨·구조는 변경하지 않았다. 사용자 데이터와 기존 backup/restore
의미도 변경하지 않았다.
