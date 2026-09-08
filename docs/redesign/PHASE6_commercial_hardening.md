# Fitness App UI Redesign — PHASE 6. Commercial Surface / 접근성 / 배포 UI 안정화

- 문서 상태: 작업 전 명세 / 고정 아님
- 기준 저장소: `Yeon-sik/Yeonsik-Fitness-App`
- 작성 시점 기준 main HEAD: `3795b01653857647832927b78f504a36b620cf8e`
- 실행 원칙: 각 Phase 시작 직전 반드시 최신 `main`을 다시 확인하고, 완료 후 코드 리뷰 결과에 따라 이후 Phase 명세를 수정할 수 있음
- 우선순위: 정확성 > 데이터 무결성 > 단순한 구조 > 유지보수성 > 개발 속도 > 미래 확장성
- 디자인 방향: 화이트/다크 테마 유지, 핵심 강조색은 Pastel Blue
- 언어 원칙: 운동명·식품명·브랜드명·제품명 등 고유명사는 원문 유지. 그 외 교체 가능한 UI 문구는 한국어로 통일. 하단 내비게이션 버튼의 라벨/구조는 별도 승인 없이 변경하지 않음.
- 금지: UI 작업을 이유로 DB schema, migration, 동기화 계약, PriceTrace/OCR 연계 계약을 임의 변경하지 않음


## 목표

개인용 관제 UI와 일반 사용자 UI를 분리하고,
상업 배포 가능한 수준의 접근성, 오류 상태, 데이터 안전성, 환경별 노출 정책을 갖춘다.

## 선행 조건

- PHASE 1~5 완료 및 리뷰
- 최신 main 재확인
- Personal / Test-Friends / Commercial 배포 정책 재검토

## 작업 범위

1. 설정 화면 사용자화
   일반 사용자에게 기본 노출:
   - 계정
   - 동기화 상태
   - 백업 / 내보내기
   - 데이터 가져오기
   - 테마
   - 개인정보 및 보안
   - 앱 정보

   기본 화면에서 숨길 후보:
   - Supabase URL
   - anon key
   - project ref
   - 관리자 세션
   - PriceTrace 내부 연결 topology
   - Personal OS 내부 구조

2. Personal / Test / Commercial surface 분리
   - Personal: 개발자/내부 연결 정보 접근 가능
   - Test/Friends: 개인 production DB 정보 노출 금지
   - Commercial: 사용자 계정과 사용자별 데이터 격리를 전제로 하는 UI
   - 구현 방식은 productFlavor / BuildConfig / feature flag 중 최신 구조에 맞는 최소 방식 선택
   - 구조 변경 전 별도 코드 리뷰 필수

3. Secrets / account UI
   - 실제 key/token/password를 화면에 평문 노출하지 않음
   - 저장소에 실 secrets 추가 금지
   - `.env.example`과 실제 환경값 구분
   - 디버그 provisioning 기능이 release UI에서 노출되지 않게 검증

4. 상태 화면
   공통 상태 정의:
   - loading
   - empty
   - offline
   - permission required
   - sync delayed
   - validation error
   - server error
   - destructive confirmation
   - success

   화면마다 독자적인 색/팝업을 만들지 않음

5. 접근성
   - 최소 48dp touch target
   - text contrast WCAG AA 수준
   - 색만으로 상태 전달 금지
   - TalkBack contentDescription 검토
   - text scale 증가 시 clipping/overlap 검증
   - keyboard/IME action 일관성
   - focus order 검증

6. 화면 크기 / inset
   - 작은 Android 화면
   - 일반 20:9 화면
   - 큰 화면
   - gesture navigation
   - 3-button navigation
   - status/navigation bar inset
   에서 레이아웃 검증

7. Dark mode
   - 단순 color inversion이 아니라 semantic surface hierarchy 유지
   - white shadow/glow 금지
   - Pastel Blue가 과도하게 밝아 보이지 않도록 실기기 검증

8. 언어 QA
   - 번역 가능한 영어 UI 문자열 전수 검색
   - 한국어 용어 통일
   - 운동명/식품명/브랜드명/제품명 등 고유명사는 원문 유지
   - 하단 내비게이션 라벨/구조는 별도 승인 없이는 변경하지 않음
   - 개발자 전용 내부 키워드는 Commercial surface에서 사용자에게 노출하지 않음

9. Regression / screenshot QA
   최소 검증 화면:
   - 홈
   - 피트니스
   - 루틴 목록/상세
   - 운동 세션
   - 종목 선택
   - 식단
   - 외식 입력
   - 영양제
   - 기록 달력
   - 분석
   - 설정
   - 공통 bottom sheet / popup

   Light/Dark 각각 캡처 비교

## 반드시 유지

- 사용자 데이터 무결성
- 기존 backup/restore 의미
- sync 계약
- PriceTrace/Nutrition/Personal OS의 데이터 소유권 경계
- Personal 기능과 Commercial 기능을 혼합하지 않는 원칙

## 비범위

- 완전한 상업 인증/결제/운영 백엔드 구축
- observability 전체 플랫폼 구축
- 앱스토어 마케팅 자산 제작
- 구조적 필요가 확인되지 않은 대규모 프레임워크 전환

## 완료 조건

- 일반 사용자가 내부 DB topology를 몰라도 앱을 사용할 수 있음
- Test/Friends 빌드가 개인 production DB에 자동 연결되지 않음
- 주요 화면이 Light/Dark + text scale에서 깨지지 않음
- loading/error/offline/empty 상태가 공통 문법을 사용
- TalkBack 기본 탐색이 가능
- 번역 가능한 사용자-facing 영어가 거의 남지 않음
- release surface에 secrets/debug session 정보가 노출되지 않음

## 최종 코드 리뷰 체크포인트

- Commercial 요구 때문에 MVP 구조를 과도하게 복잡하게 만들지 않았는가
- 반대로 Personal 편의를 위해 Commercial 분리가 불가능한 결합을 남기지 않았는가
- user-facing 설정과 developer/internal 설정이 분리됐는가
- 기존 데이터 migration 없이도 가능한 UI 작업을 불필요하게 schema 변경으로 풀지 않았는가
- 각 서비스의 Source of Truth 경계가 유지되는가

## 최종 산출물

- UI design token 기준
- 공통 component inventory
- Light/Dark screenshot set
- Personal/Test/Commercial surface 차이 문서
- 남은 UI debt 목록
- 향후 Compose/architecture migration 필요성 여부는 별도 판단

## 종료 원칙

PHASE 6 완료가 "디자인이 영구 고정됨"을 뜻하지 않는다.
이후 실제 사용자 테스트와 상업 배포 준비 과정에서 수정하되,
semantic token / component / data ownership / environment separation 원칙을 깨는 변경은 별도 설계 리뷰를 거친다.
