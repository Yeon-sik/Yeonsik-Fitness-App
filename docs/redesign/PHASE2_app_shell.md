# Fitness App UI Redesign — PHASE 2. App Shell / Navigation / Global Layout

- 문서 상태: 작업 전 명세 / 고정 아님
- 기준 저장소: `Yeon-sik/Yeonsik-Fitness-App`
- 작성 시점 기준 main HEAD: `3795b01653857647832927b78f504a36b620cf8e`
- 실행 원칙: 각 Phase 시작 직전 반드시 최신 `main`을 다시 확인하고, 완료 후 코드 리뷰 결과에 따라 이후 Phase 명세를 수정할 수 있음
- 우선순위: 정확성 > 데이터 무결성 > 단순한 구조 > 유지보수성 > 개발 속도 > 미래 확장성
- 디자인 방향: 화이트/다크 테마 유지, 핵심 강조색은 Pastel Blue
- 언어 원칙: 운동명·식품명·브랜드명·제품명 등 고유명사는 원문 유지. 그 외 교체 가능한 UI 문구는 한국어로 통일. 하단 내비게이션 버튼의 라벨/구조는 별도 승인 없이 변경하지 않음.
- 금지: UI 작업을 이유로 DB schema, migration, 동기화 계약, PriceTrace/OCR 연계 계약을 임의 변경하지 않음


## 목표

앱 전체의 첫인상을 결정하는 shell을 정리한다.
화면별 콘텐츠보다 먼저 배경, 여백, 하단 내비게이션, 시스템 inset, 전환 상태를 일관되게 만든다.

## 선행 조건

- PHASE 1 완료 및 코드 리뷰 통과
- 최신 main 재확인
- PHASE 1에서 확정된 semantic token과 공통 컴포넌트를 사용

## 작업 범위

1. Root / page background
   - Light `Background`, Dark `Background` 적용
   - Surface와 page background를 명확히 구분
   - 현재 content padding `20dp / 40dp / 20dp / 28dp`는 실제 기기에서 검증 후 조정
   - 화면 제목/section 간 spacing token 통일

2. Bottom Navigation
   - 기존 5개 탭 구조는 유지
   - 하단 라벨은 별도 승인 없이 변경하지 않음
   - 거대한 pill × 5 구조를 단순화
   - active state와 workout-in-progress state를 분리
   - active: Pastel Blue tonal indicator 또는 icon/text 강조
   - in-progress: 작은 dot/badge 수준
   - hologram/glow 제거
   - 터치 영역은 최소 48dp 확보

3. Navigation state clarity
   - 현재 선택 탭은 오직 하나만 명확히 보이게
   - 운동 진행 여부가 다른 탭 선택 상태를 침범하지 않게
   - fullscreen 세션/요약/편집 화면에서는 하단 nav 노출 정책을 현재 기능 구조 기준으로 재검증

4. Header system
   - `screenHeader(eyebrow, title)`의 eyebrow 중 번역 가능한 영어를 한국어화
   - 페이지 title hierarchy 통일
   - 불필요한 decorative eyebrow는 삭제 가능
   - back/navigation affordance 통일

5. Global spacing
   - section 간 거리
   - card 간 거리
   - input label 간 거리
   - bottom fixed action과 scroll content 겹침 방지
   - navigation bar / gesture inset 대응

6. System UI
   - status bar / navigation bar 색상 Light/Dark 일치
   - 시스템 아이콘 대비 확보
   - edge-to-edge 여부를 현재 구조에 맞춰 일관되게 적용

## 비범위

- 홈/피트니스 화면 내부 카드 정보 구조의 대대적 변경
- 운동 세션 데이터 배치 변경
- 식단 입력폼 구조 변경
- 설정의 Commercial 분리

## 완료 조건

- 선택 탭과 운동 진행 상태가 동시에 존재해도 시각적으로 충돌하지 않음
- Light/Dark 모두에서 하단 nav가 지나치게 강한 시각 요소가 아님
- 모든 화면이 동일한 page margin / section rhythm을 사용
- 시스템 bar와 앱 배경이 어색하게 끊기지 않음
- back/fullscreen/bottom-nav 노출 정책이 기능적으로 회귀하지 않음
- 영어 UI 문자열 중 shell 수준에서 번역 가능한 것은 한국어화됨

## 실기기 리뷰 항목

이 Phase부터 숫자값은 고정하지 않는다.
특히 radius, nav indicator 크기, page horizontal padding, section spacing은 Galaxy 실기기에서 적용 후 조정 가능하다.

체크:
- 한 손 조작 시 하단 nav 터치 편의성
- 5개 탭이 과밀해 보이지 않는지
- 다크모드에서 nav와 content의 경계가 충분한지
- bottom fixed CTA가 navigation과 경쟁하지 않는지

## Phase 종료 후 결정

Shell이 안정된 뒤 PHASE 3 핵심 사용 흐름의 카드 밀도와 CTA 크기를 조정한다.
