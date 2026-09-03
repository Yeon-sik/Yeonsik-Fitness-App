# Fitness App UI Redesign — PHASE 4. 입력 Form / 식단 / 외식 / 영양제

- 문서 상태: 작업 전 명세 / 고정 아님
- 기준 저장소: `Yeon-sik/Yeonsik-Fitness-App`
- 작성 시점 기준 main HEAD: `3795b01653857647832927b78f504a36b620cf8e`
- 실행 원칙: 각 Phase 시작 직전 반드시 최신 `main`을 다시 확인하고, 완료 후 코드 리뷰 결과에 따라 이후 Phase 명세를 수정할 수 있음
- 우선순위: 정확성 > 데이터 무결성 > 단순한 구조 > 유지보수성 > 개발 속도 > 미래 확장성
- 디자인 방향: 화이트/다크 테마 유지, 핵심 강조색은 Pastel Blue
- 언어 원칙: 운동명·식품명·브랜드명·제품명 등 고유명사는 원문 유지. 그 외 교체 가능한 UI 문구는 한국어로 통일. 하단 내비게이션 버튼의 라벨/구조는 별도 승인 없이 변경하지 않음.
- 금지: UI 작업을 이유로 DB schema, migration, 동기화 계약, PriceTrace/OCR 연계 계약을 임의 변경하지 않음


## 목표

길고 반복적인 입력 화면을 공통 Form System으로 통합한다.
"카드가 많아서 긴 화면"이 아니라 "정보 단위가 명확해서 빠르게 입력되는 화면"으로 바꾼다.

이 Phase는 실제 적용 전 완전 고정하지 않는다.
특히 영양정보 1행 구조, progressive disclosure, bottom sheet 범위는 실기기 테스트 후 수정 가능하다.

## 선행 조건

- PHASE 1~3 완료 및 리뷰
- 최신 main 재확인
- Nutrition / PriceTrace 계약 재확인

## 작업 범위

1. 공통 Form System
   - Section title
   - Field label
   - Text input
   - Number input
   - Unit suffix
   - Selector
   - Read-only field
   - Helper / error text
   - Inline total row
   - Bottom action
   - Disabled / loading state
   를 공통 컴포넌트화

2. 영양정보 row
   기본 표시 순서:
   - 칼로리
   - 탄수화물
   - 단백질
   - 지방
   - 당류
   - 포화지방
   - 나트륨

   한 항목당 한 줄을 기본으로 한다.
   예:
   `단백질          31 g`

   입력 화면에서도 필요 이상으로 영양소 하나당 큰 카드 하나를 쓰지 않는다.

3. 끼니 메뉴 총합계
   - 개별 메뉴와 같은 nutrient-row component 재사용
   - 합계라는 이유로 별도 거대 카드 문법을 만들지 않음
   - 사용자 섭취 비율이 있다면 실제 저장/계산 계약에 맞춰 반영

4. 식단
   - kcal / 탄수화물 / 단백질 / 지방을 1차 정보
   - 당류 / 포화지방 / 나트륨은 2차 정보
   - daily progress는 Pastel Blue tonal surface 사용 가능
   - 고유 식품명/브랜드명 유지

5. 외식
   정보 단계:
   - 식당
   - 메뉴
   - 기본 제공/유료 추가 옵션
   - 섭취량/영양
   - 저장

   한 화면에서 모두 펼쳐놓기보다 progressive disclosure 적용 검토
   단, 불필요하게 다중 페이지 wizard로 쪼개지 않음

6. 옵션 영양성분
   - 메뉴 옵션도 기존 영양 contract와 호환되는 범위에서 동일 nutrient row 사용
   - 기본 제공과 유료 추가의 시각 상태를 구분
   - 선택/비선택/기본포함의 의미를 색 하나에만 의존하지 않음

7. 영양제
   섹션:
   - 제품 정보
   - 섭취량
   - 주요 성분
   - 복용 계획
   선택 필드는 처음부터 전부 노출하지 않도록 검토
   현재의 의료 권장량이 아니라는 disclaimer는 유지

8. 팝업 / Bottom Sheet
   - AlertDialog처럼 보이는 generic system popup 제거 방향 유지
   - 모든 popup/sheet에 동일 radius, spacing, CTA 문법 적용
   - selector마다 다른 gradient 금지
   - 선택 상태는 하나의 Pastel Blue semantic rule 사용

9. 언어
   - form label, helper, 상태, 버튼 중 번역 가능한 영어는 한국어화
   - 브랜드/제품/음식/운동 고유명사는 원문 유지
   - 하단 내비게이션 라벨은 변경하지 않음

## 반드시 유지

- Nutrition repository contract
- PriceTrace read/write 경계
- 기존 식당/메뉴/옵션 canonical 모델 의미
- kcal/탄단지 필수 규칙 등 최신 main의 validation
- 기존 저장 데이터 호환성

## 비범위

- DB schema 변경
- 새로운 영양소 필드 추가
- OCR importer contract 변경
- 기록 달력 재설계

## 완료 조건

- 영양정보가 한눈에 세로로 스캔 가능
- 입력필드마다 불필요한 거대 카드가 없어짐
- 메뉴 총합과 개별 영양정보가 같은 문법을 사용
- 외식 flow에서 사용자가 현재 단계와 저장 전 상태를 이해할 수 있음
- bottom sheet/popup 디자인이 전역적으로 일관됨
- validation/error/disabled 상태가 색상 외 형태/문구로도 구분됨

## 코드 리뷰 체크포인트

- Form component 재사용이 과도한 abstraction이 되지 않았는가
- 입력 UI 변경으로 validation이 완화/변경되지 않았는가
- read-only PriceTrace 값이 편집 가능해지지 않았는가
- 옵션/메뉴/식당의 데이터 소유권을 UI가 혼동하지 않는가
- 긴 화면을 줄이려다 중요한 값이 숨겨지지 않았는가

## Phase 종료 후 결정

실기기에서 실제 식사/외식 3건 이상 입력 후:
- nutrient row 높이
- section 접기/펼치기 범위
- bottom sheet 사용 범위
- CTA 고정 여부

를 조정할 수 있다.
