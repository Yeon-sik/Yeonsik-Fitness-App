# Fitness App UI Redesign — PHASE 1. Design System 교체

- 문서 상태: 작업 전 명세 / 고정 아님
- 기준 저장소: `Yeon-sik/Yeonsik-Fitness-App`
- 작성 시점 기준 main HEAD: `3795b01653857647832927b78f504a36b620cf8e`
- 실행 원칙: 각 Phase 시작 직전 반드시 최신 `main`을 다시 확인하고, 완료 후 코드 리뷰 결과에 따라 이후 Phase 명세를 수정할 수 있음
- 우선순위: 정확성 > 데이터 무결성 > 단순한 구조 > 유지보수성 > 개발 속도 > 미래 확장성
- 디자인 방향: 화이트/다크 테마 유지, 핵심 강조색은 Pastel Blue
- 언어 원칙: 운동명·식품명·브랜드명·제품명 등 고유명사는 원문 유지. 그 외 교체 가능한 UI 문구는 한국어로 통일. 하단 내비게이션 버튼의 라벨/구조는 별도 승인 없이 변경하지 않음.
- 금지: UI 작업을 이유로 DB schema, migration, 동기화 계약, PriceTrace/OCR 연계 계약을 임의 변경하지 않음


## 목표

화면별 땜질이 아니라 `FitnessUi`를 중심으로 전역 디자인 primitive와 semantic token을 재정의한다.
이 단계의 성공 기준은 "아직 모든 화면이 완성되지 않아도, 새 컴포넌트만 사용하면 동일한 시각 문법이 자동으로 적용되는 상태"다.

## CURRENT 확인 사항

현재 공통 UI는 `FitnessUi.java`가 색상 토큰, 버튼, 카드, 입력창, 선택 상태, 홀로그램/gradient, elevation 등을 대부분 소유한다.
`borderDrawable()`은 호출자가 전달한 stroke 의미보다 전역 black/white border를 강하게 적용하고 있고,
primary/selected 상태는 cyan-violet-magenta 계열 gradient 및 hologram 계열 효과를 사용한다.

## 작업 범위

1. semantic color token 재정의
   - Light:
     - Background `#F7F9FC`
     - Surface `#FFFFFF`
     - Surface Subtle `#F0F5F9`
     - Text Primary `#111827`
     - Text Secondary `#667085`
     - Border Subtle `#DCE5EC`
     - Pastel Blue `#A9D6F5`
     - Blue Container `#EAF6FF`
     - Blue Ink `#173B55`
   - Dark:
     - Background `#0E141A`
     - Surface `#151C23`
     - Surface Subtle `#1B2530`
     - Text Primary `#F5F8FA`
     - Text Secondary `#A6B0BA`
     - Border Subtle `#2A3742`
     - Pastel Blue `#8FC8EE`
     - Blue Container `#18384D`
     - Blue Ink는 실제 대비 검증 후 확정
   - Error / Success / Warning은 별도 semantic color로 유지

2. 기존 컬러 문법 정리
   - cyan / violet / magenta gradient를 일반 컴포넌트에서 제거
   - 회전 hologram / glow는 일반 상태 표현에서 제거
   - 필요 시 Hero 한정 tonal blue gradient만 허용
   - 색상 seed를 문자열에 연결하는 방식 제거 또는 deprecate

3. Shape token 정의
   - Card 16dp
   - Hero 20~24dp
   - Input 12dp
   - Button 12~14dp
   - Chip/Badge만 full-pill
   - Sheet top 24dp

4. Depth token 축소
   - 기본 카드 0~1dp
   - 강조 surface 1~3dp
   - 다크 모드 white shadow 금지
   - outline + elevation의 중복 표현 최소화

5. 공통 컴포넌트 API 정리
   - `primaryButton`
   - `secondaryButton`
   - `tonalButton`
   - `textButton`
   - `card`
   - `outlinedCard`
   - `input`
   - `searchInput`
   - `chip`
   - `statusBadge`
   - `sectionHeader`
   - `bottomSheet`
   - 기존 `button(text, boolean primary, ...)`는 호환층으로 유지 가능하나 신규 호출은 금지

6. 상태 표현 규칙
   - selected = Blue Container / Pastel Blue 계열
   - current/in-progress = 작은 status marker 또는 tonal surface
   - success = green
   - warning = amber
   - error/destructive = red
   - selected와 in-progress를 동일한 효과로 표현하지 않음

7. 언어 시스템 정리
   - 공통 컴포넌트의 영어 eyebrow, placeholder, 상태 문자열 중 번역 가능한 것을 한국어로 변경
   - 고유명사/운동명/식품명/브랜드명은 원문 보존
   - 하단 내비게이션 라벨은 이번 Phase에서 변경하지 않음

## 비범위

- 화면별 정보 구조 개편
- Bottom Navigation 재설계
- 운동 세션 UX 재배치
- 식단/외식 입력 흐름 개편
- DB / sync / repository 변경

## 구현 원칙

- 가능하면 기존 호출부를 일시적으로 호환시키는 adapter를 두고 점진 교체
- 한 번에 전 화면을 깨뜨리지 말 것
- Light/Dark 양쪽에서 동일 semantic API를 사용
- hard-coded `Color.rgb()` 신규 추가 금지. 예외는 자산 고유색/차트 semantic color에 한정

## 완료 조건

- 앱이 Light/Dark 양쪽에서 빌드/실행됨
- 일반 버튼/선택 상태에 cyan-violet-magenta gradient가 남아 있지 않음
- 일반 상태에서 hologram/glow가 제거됨
- 기본 카드/입력/버튼에 전역 black/white 테두리가 강제되지 않음
- 신규 Pastel Blue semantic token이 실제 공통 컴포넌트에 연결됨
- 주요 텍스트 대비가 WCAG AA 수준을 만족하도록 검증됨
- 공통 컴포넌트 호출만으로 화면 간 스타일이 일관됨

## 코드 리뷰 체크포인트

- `FitnessUi`가 오히려 더 거대한 God Class가 되지 않았는가
- semantic token과 literal color가 섞이지 않았는가
- deprecated API가 신규 코드에서 다시 쓰이지 않는가
- dark mode에서 surface/border/shadow가 과도하게 밝지 않은가
- 기존 화면 기능이 시각 리팩터링 때문에 깨지지 않았는가

## Phase 종료 후 결정

리뷰 결과에 따라 PHASE 2의 App Shell shape/elevation/spacing 수치를 조정한다.
3, 4단계의 세부 형태는 실제 기기 적용 결과를 보고 변경할 수 있다.
