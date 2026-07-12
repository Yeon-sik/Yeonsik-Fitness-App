# Fitness App UI 재설계 지시문 (Codex 구현용)

작성: 2026-07-09, UI/UX 관제탑 문서. 이 문서는 구현물이 아니라 Codex에게 줄 작업 지시문이다.

전제 제약 (절대 위반 금지):
- FitnessDatabaseHelper.java의 DATABASE_VERSION 변경 금지, schema/migration/새 테이블 금지
- SupabaseSyncManager.java 수정 금지, Fitness_Weight.json 수정 금지
- XML 레이아웃 도입 금지 — 현재 코드 생성 UI(Java, View 직접 생성) 방식 유지
- Fragment/Navigation Component 도입 금지
- git push 금지

---

## 1. 현재 피트니스 앱 UI의 핵심 문제 가정

1. **기록이 문장으로 표시된다.** 세트 데이터가 `lines()` 텍스트 줄("1세트 90kg · 7회 완료")로 나열되어, 표 형태의 스캔 가능성과 정확성 인지가 없다.
2. **모든 화면이 같은 카드 문법이다.** "운동 중"(집중·조작)과 "기록 조회"(읽기)와 "설정"(폼)이 동일한 흰 카드 나열이라 화면의 목적이 형태로 구분되지 않는다.
3. **입력이 다이얼로그 의존이다.** 세트 수정, 체중, 식사가 모두 AlertDialog + 키보드 타이핑이라 운동 중 한 손 조작이 불가능하다.
4. **상태가 색·형태로 표현되지 않는다.** 진행 중/완료/미완료 세트가 텍스트로만 구분된다.
5. **루틴 관리가 수행 흐름과 시각적으로 동급이다.** 피트니스 탭에서 루틴 카드가 운동 시작 CTA와 같은 비중을 차지한다.
6. **휴식 타이머가 없다.** 세트 간 휴식이 운동 수행의 절반인데 UI에 존재하지 않는다.
7. **숫자 타이포그래피가 없다.** 중량·횟수·볼륨이 본문 서체 그대로라 자릿수 정렬이 흔들리고 데이터로 읽히지 않는다.

## 2. 유지해야 할 제품 원칙

1. **기록이 제품이다.** 세트 그리드가 제1시민. 화면의 나머지는 그리드를 보조한다.
2. **운동 중 화면은 "장갑 낀 한 손" 기준.** 탭 1회 > 스테퍼 > 키보드 순으로 조작 비용을 설계한다.
3. **같은 데이터, 세 가지 모드.** 운동중(session) / 운동 기록(readonly) / 루틴 수정(template)은 동일 컴포넌트에 mode 파라미터만 다르다. 모드가 스타일과 가능 동작을 결정한다.
4. **숫자는 표로, 상태는 형태로.** 데이터는 괘선 그리드 + 고정폭 숫자, 상태(완료/진행)는 색 반전과 채움으로 표현한다.
5. **장식 제로.** 화면의 모든 시각 요소는 데이터이거나 액션이다. 어느 쪽도 아니면 삭제한다.
6. **루틴은 계획, 기록은 사실.** 루틴 수정이 과거 기록을 절대 바꾸지 않고, 두 화면은 시각적으로도 구분된다(template 모드는 점선 괘선, 기록은 실선).
7. **데이터 정확성 우선.** 볼륨·시간 집계는 항상 저장 시점에 DB에 기록하고, 화면 계산값과 저장값이 다르면 저장값을 보여준다.

## 3. 최종 탭 구조

```text
하단 탭 (4개, 항상 표시)
홈 / 피트니스 / 기록 / 설정

Fullscreen 루트 (하단 탭 숨김)
- WorkoutSessionScreen   (피트니스 → 운동 시작)      mode=session
- WorkoutSummaryScreen   (운동 완료 직후)
- WorkoutRecordScreen    (기록 → 운동 기록 상세)      mode=readonly (명시적 "기록 수정"으로 edit)
- RoutineEditorScreen    (피트니스 → 루틴 수정)       mode=template
- ExercisePickerScreen   (세션/루틴 편집의 하위 화면, 부모의 fullscreen 여부 승계)
```

주의: `session`은 화면 개념이다. DB에 workout_sessions 부모 테이블을 만들지 않는다. 부모는 기존 `workout_records`다.

## 4. 각 탭의 역할

**홈** — 판단과 빠른 진입만. 오늘 운동 상태(했다/안 했다/진행 중), 운동 시작 CTA 1개, 오늘 체중·식사 빠른 입력(바텀시트), 최근 운동 3건 요약, 회복 힌트(마지막 운동 부위·연속 운동일 기반 한 줄 텍스트 — 기존 데이터만 사용, 새 테이블 금지). 편집·세트 입력 금지.

**피트니스** — 실행의 중심. 상단: 진행 중 운동 이어하기(있을 때만, 최상단 고정) → 루틴 선택 리스트(이름 지정·추가·삭제·복제) → 자유 운동 시작. 루틴 카드 탭 = 운동 시작이 아니라 "루틴 페이지"로 진입하고, 루틴 페이지에서 [운동 시작] / [루틴 수정] 선택. 자유 운동은 빈 기록으로 즉시 session 진입, 완료 시 "이 구성을 루틴으로 저장할까요?" 1회 질문.

**기록** — 사실의 아카이브. 월간 달력(운동일 부위 마커) → 선택 날짜의 운동/체중/식사 → 운동 기록 탭하면 WorkoutRecordScreen(readonly). 부위별 볼륨·체중 변화는 리스트/숫자 우선, 차트는 후순위.

**설정** — 사용자 정보(이름·체중 단위 kg/lb), 머신 기본 중량 입력(SharedPreferences, DB 아님), 데이터 export(CSV/JSON 선택), Supabase 연결, 테마.

## 5. 주요 화면 목록

| # | 화면 | 모드 | 하단 탭 |
|---|---|---|---|
| S1 | HomeScreen | - | 표시 |
| S2 | FitnessScreen | - | 표시 |
| S3 | RoutinePage (루틴 상세: 시작/수정 분기) | - | 표시 |
| S4 | WorkoutSessionScreen | session | 숨김 |
| S5 | ExerciseDetailScreen (세션 내 종목 세부: 세트·중량·휴식시간) | session | 숨김 |
| S6 | ExercisePickerScreen | - | 부모 승계 |
| S7 | WorkoutSummaryScreen (+자유운동 루틴 저장 질문) | readonly | 숨김 |
| S8 | RecordsScreen (달력) | - | 표시 |
| S9 | WorkoutRecordScreen | readonly→edit | 숨김 |
| S10 | RoutineEditorScreen | template | 숨김 |
| S11 | SettingsScreen | - | 표시 |
| S12 | QuickWeightSheet / QuickMealSheet (바텀시트형 다이얼로그) | - | - |

## 6. 화면별 컴포넌트 구조

공용 코어 (이것이 재사용의 핵심):

```text
WorkoutPage(mode)                    // session | readonly | template
├── PageHeader                       // 뒤로가기, 제목, 우측 주 액션
│     session:  경과시간(모노, 틱) + [운동 완료]
│     readonly: 날짜 + [기록 수정]
│     template: 루틴 이름(편집 가능) + [저장]
├── MetricStrip                      // 총 볼륨 · 완료 세트 · 시간 (한 줄, 고정폭 숫자)
│     template에서는 숨김
├── ExerciseBlock × N                // 카드가 아니라 괘선으로 구분된 섹션
│     ├── BlockHeader: 순번. 운동명 / 부위·장비 캡션 / (session·template: 삭제)
│     ├── SetGrid                    // ★ 시그니처 컴포넌트
│     │     ├── GridHead: # | 이전 | kg | 회 | RPE | (session: 완료)
│     │     └── SetRow × M
│     │           session:  스테퍼 셀 + 행 탭 = 완료 스탬프(잉크 반전)
│     │           readonly: 텍스트 셀만, 완료 행은 좌측 잉크 바
│     │           template: kg·회 = 목표값 셀, RPE·완료 없음, 점선 괘선
│     └── BlockFooter: [+ 세트] (session·template)
├── AddExerciseButton                // session·template만
└── RestTimerBar                     // session만: 하단 고정, 남은 초(모노 대형) + [건너뛰기]
```

**S1 홈**: StatusHeadline(오늘 상태 한 문장, Display체) → PrimaryCTA(운동 시작 또는 이어하기, 화면당 1개) → QuickRow(체중/식사 두 버튼 → 바텀시트) → RecentList(최근 3건: 날짜·부위·볼륨 한 줄씩) → RecoveryHint(캡션 한 줄).

**S2 피트니스**: ContinueBanner(진행 중일 때만, Signal 배경) → SectionLabel "루틴" + RoutineList(행: 이름/종목 수/마지막 수행일, 스와이프 없이 롱프레스 메뉴: 이름 변경·복제·삭제) + [루틴 추가] → SectionLabel "자유 운동" + [빈 운동 시작].

**S3 루틴 페이지**: 루틴 이름 헤더 → 종목 미리보기(SetGrid template-readonly 축약) → [이 루틴으로 운동 시작](primary) / [루틴 수정](secondary).

**S4 세션**: WorkoutPage(session). 종목명 탭 → S5 진입.

**S5 종목 세부**: 해당 ExerciseBlock 확대 + 기본 휴식시간(초) 스테퍼(값은 workout_sets.rest_seconds에 세트별 저장) + 세트 일괄 조작.

**S7 요약**: 날짜/시간/총 볼륨/부위별 볼륨(가로 막대 아님 — 부위명 + 숫자 리스트) → 종목별 수행 그리드(readonly) → 자유 운동이었으면 "루틴으로 저장" 카드 1개 → [기록 보기][홈으로].

**S8 기록**: MonthCalendar(날짜 셀 하단에 부위 컬러 스트립 최대 3개) → DayPanel(운동 행 + 체중 행 + 식사 행).

**S9 기록 상세**: WorkoutPage(readonly). [기록 수정] 탭 시에만 WorkoutPage(edit=session 스타일이되 타이머·경과시간 없음, 완료 버튼 대신 [수정 완료]).

**S11 설정**: GroupedList(단위 / 머신 기본 중량 / Export 형식 선택 + [내보내기] / Supabase / 테마).

## 7. 디자인 시스템 방향

컨셉: **"체육관 로그북"** — 종이 세트 기록지의 정밀함. 카드 그림자 대신 괘선, 장식 대신 숫자 타이포그래피.

**컬러 토큰** (기존 상수 교체):
```text
paper   #F7F6F2   배경 (기존 #F6F6F3 유지 가능 수준)
ink     #161616   본문·주 액션·완료 스탬프
steel   #6E6B66   보조 텍스트·캡션
line    #E2DFD8   괘선(1dp hairline)
signal  #1E6E52   딥 그린: 진행 중 배너·휴식 타이머·CTA 단 하나의 강조색
danger  #B3402E   삭제 확인에만
```
부위 마커 6색(기록 탭 달력 한정, IWF 원판색 차용): 가슴 #C2402A(빨강 25) / 등 #2456A3(파랑 20) / 하체 #D9A62E(노랑 15) / 어깨 #3E8E5A(초록 10) / 팔 #8A8F98(흰색 5 대체 그레이) / 복근 #161616(검정). 이 6색은 달력 스트립과 부위 캡션 도트 외 어디에도 쓰지 않는다.

**타이포그래피** (시스템 폰트, 커스텀 폰트 도입 금지):
```text
Display  28sp Bold           화면 제목, 홈 상태 문장
Title    19sp Bold           섹션·종목명
Body     15sp Regular        일반 텍스트
Data     17sp Typeface.MONOSPACE  중량·횟수·볼륨·타이머 (우측 정렬)
DataLg   34sp MONOSPACE Bold 경과시간·휴식 타이머
Caption  11sp Bold, letterSpacing 0.08, steel, 대문자  eyebrow·그리드 헤더
```

**형태**: radius 6dp, 그림자 금지, 구분은 1dp 괘선. session 모드 실선, template 모드 점선. 완료 스탬프 = 행 배경 ink + 글자 paper 반전(애니메이션 120ms 이하 1회, 그 외 모션 금지).

**시그니처**: SetGrid의 "행 탭 = 스탬프" 인터랙션. 이 앱에서 기억에 남을 단 하나의 요소이며, 나머지는 전부 조용하게 유지한다.

## 8. 모바일 UX 규칙

1. 터치 타깃 최소 48dp, 세션 화면은 56dp.
2. 중량 = ±2.5kg 스테퍼(머신 종목은 설정의 머신 기본 중량 단위), 횟수 = ±1, RPE = ±1. 스테퍼 롱프레스 시 가속. 숫자 자체를 탭하면 키보드 입력 허용.
3. 세트 완료 = 행의 완료 영역 탭 1회(스탬프). 재탭 = 취소. 별도 확인 없음.
4. 세트 완료 시 휴식 타이머 자동 시작(해당 세트 rest_seconds, 기본 90초). 하단 고정 바, [건너뛰기], 종료 시 진동 1회.
5. 세션 중 `FLAG_KEEP_SCREEN_ON`.
6. 주 액션은 화면 하단 40%(엄지존)에 배치. 파괴적 액션(종목/기록 삭제)만 다이얼로그 확인.
7. 새 세트의 kg·회·RPE는 직전 세트 값 자동 승계.
8. 시스템 뒤로가기 = 세션에서는 [계속/임시 저장/기록 삭제] 3선택, 그 외 화면은 상위로.
9. 숫자는 항상 우측 정렬 + 단위는 그리드 헤더에만 1회 표기(셀마다 "kg" 반복 금지).
10. 모든 저장은 즉시 DB 반영(세트 저장 버튼을 누르기 전 데이터 유실 금지 — 스테퍼 변경은 디바운스 후 자동 저장).

## 9. Codex가 먼저 구현할 Phase 1 범위

Phase 1 = "디자인 토큰 + 시그니처 컴포넌트 + 세션 화면". 이것만으로 앱의 핵심 루프가 완성된다.

1. 디자인 토큰 상수 교체 (색·타이포 헬퍼: `dataText()`, `captionText()`, hairline divider 헬퍼)
2. `SetGrid`/`SetRow` 컴포넌트 (mode: session/readonly/template) — 코드 생성 UI 헬퍼 메서드로
3. WorkoutSessionScreen을 SetGrid 기반으로 재구성: 스테퍼 입력, 행 스탬프 완료, 자동 저장, 직전 세트 승계
4. RestTimerBar (CountDownTimer, rest_seconds 세트별 저장·사용)
5. 세션 중 FLAG_KEEP_SCREEN_ON, 뒤로가기 3선택 유지
6. WorkoutSummaryScreen을 MetricStrip + readonly SetGrid로 재구성

Phase 1 제외: 복수 루틴 CRUD(Phase 2), 기록 탭 달력 마커(Phase 2), 자유운동→루틴 저장(Phase 2), export/머신 중량(Phase 3), 홈 재구성(Phase 3).

## 10. Codex에게 줄 실제 구현 프롬프트

```text
너는 이 Android 프로젝트(순수 Java, XML 레이아웃 없이 코드 생성 UI)의 구현 담당이다.
docs/fitness-ui-redesign-directive.md를 읽고 Phase 1만 구현하라.

절대 금지:
- FitnessDatabaseHelper.java의 DATABASE_VERSION 변경, schema/migration/새 테이블 생성
- SupabaseSyncManager.java, Fitness_Weight.json 수정
- XML 레이아웃, Fragment, Navigation Component, 외부 라이브러리 도입
- 기존 기록 삭제, git push

구현 순서:
1. MainActivity의 색 상수를 문서 §7 토큰으로 교체하고, 타이포 헬퍼
   dataText(String, int sp) — Typeface.MONOSPACE, 우측 정렬 —
   captionText(String) — 11sp bold letterSpacing 0.08 steel —
   hairline() — 1dp line 색 View — 를 추가하라.
2. SetGrid를 렌더링하는 헬퍼를 만들어라:
   View setGrid(String recordId, SessionExerciseEntry exercise, Mode mode)
   - GridHead: # | 이전 | kg | 회 | RPE | 완료 (Caption 스타일)
   - SetRow(session): 세트번호, 이전 세트 값(steel), kg/회/RPE 스테퍼 셀
     (좌우 ±버튼 44dp + 중앙 값, 값 탭 시 EditText 전환), 완료 영역 56dp.
   - 완료 영역 탭 = 행 전체 배경 ink/글자 paper 반전 + is_completed 저장,
     재탭 = 해제. FitnessRepository.updateSet을 사용하라.
   - 스테퍼 변경은 400ms 디바운스 후 updateSet 자동 저장.
   - readonly 모드: 스테퍼 대신 고정 텍스트, 완료 행은 좌측 3dp ink 바.
3. renderWorkoutSessionScreen을 SetGrid 기반으로 재구성하라. 상단:
   뒤로가기 | 경과시간(DataLg, 1초 틱) | [운동 완료]. MetricStrip 한 줄.
   [+ 세트]는 직전 세트 값 승계(이미 repository에 lastSetForExercise 있음).
4. RestTimerBar: 세트 완료 시 자동 시작(rest_seconds, 기본 90초),
   하단 고정, DataLg 카운트다운 + [건너뛰기], 종료 시 Vibrator 1회.
   rest_seconds는 workout_sets의 기존 컬럼을 사용하라(스키마 변경 아님).
5. 세션 진입 시 getWindow().addFlags(FLAG_KEEP_SCREEN_ON), 이탈 시 해제.
6. WorkoutSummaryScreen을 MetricStrip(총 볼륨/시간/완료 세트) +
   종목별 readonly SetGrid로 재구성하라.

각 단계 후 .\gradlew.bat assembleDebug 를 실행하고, 성공 시에만
다음 단계로 진행하라. 전체 완료 후 로컬 커밋 1개:
"feat: logbook set grid and rest timer for workout session"
```

## 11. 완료 조건

- [ ] 빈 운동 시작 → 세션 화면에 SetGrid가 표시되고 하단 탭이 없다
- [ ] 스테퍼로 kg/회/RPE를 키보드 없이 조정할 수 있고 자동 저장된다
- [ ] 행 탭 1회로 세트가 완료(잉크 반전)되고 volume_kg·total_volume_kg가 갱신된다
- [ ] 세트 완료 시 휴식 타이머가 자동 시작되고 건너뛸 수 있다
- [ ] 세트 추가 시 직전 세트 값이 승계된다
- [ ] 운동 완료 → 요약에 총 볼륨/시간/세트가 저장값 기준으로 표시된다
- [ ] 세션 중 화면이 꺼지지 않는다
- [ ] 뒤로가기 시 기록이 삭제되지 않고 in_progress로 남는다
- [ ] readonly 기록 화면에서 어떤 값도 수정되지 않는다
- [ ] `.\gradlew.bat assembleDebug` 성공, DB 버전 4 그대로

## 12. 금지 사항

- 그라데이션, 그림자, 이모지, 장식용 아이콘/일러스트
- 의미 없는 카드 래핑(정보 1줄을 카드 1장에 넣기), SaaS 대시보드식 통계 타일
- signal(#1E6E52) 외 추가 강조색, 부위 6색의 달력 밖 사용
- 커스텀 폰트 번들, 외부 UI 라이브러리, Lottie
- 루틴 수정이 과거 기록을 변경하는 어떤 경로
- 세션 화면에 루틴 목록·루틴 생성 UI 노출
- 다이얼로그로만 끝나는 세트 입력(추가 즉시 그리드에 행이 보여야 함)
- DB schema/version 변경, workout_sessions 부모 테이블, weight/meal 신규 테이블
