# FitnessApp 발전 탭 코칭 구현 계획

작성일: 2026-10-31. 상태: PROPOSED — 원격 소스와 CI 증거를 대조한 구현 계획이며 제품 코드는 변경하지 않았다.

**첫 완료 목표는 관심 운동 하나의 ‘조정 채택 → 실제 운동 → 다음 점검 → 사용자 후속 선택’을 로컬에서 끝까지 추적하는 것이다.** P0에서는 기존 세트 제안 규칙을 유지하고, 목표별 판단 확장은 P1, 연구 검색·LLM 설명은 P2에서 진행한다.

## 1. 기준 소스와 브랜치 전략

- 오늘 확인한 원격 커밋: [44d232f](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/44d232fd3600cf94559bac95e7450684299b1820), 커밋 시각 2026-10-31 02:33:49 UTC. 푸시 이벤트 시각과 커밋 시각을 동일시하지 않는다.
- 브랜치: `feat/development-coaching-report-20261031`. 추가된 파일은 [코칭 구조 개선 보고서](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/docs/FitnessApp_발전탭_코칭구조_개선보고서_2026-10-31.md) 한 개다. 이 문서의 TARGET·PROPOSED는 구현 전 제안이다.
- 확인 시점 `main`: [e7c7c35](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/e7c7c35bc33c8471f470f986612cd13c343238b8). 코칭 보고서 브랜치는 main 대비 16커밋 앞서고 1커밋 뒤처진다. 오늘 문서 이전의 UI·휴식 알림 변경도 포함한다.
- 구현 시작 시 원격 main을 다시 확인하고 별도 `feat/development-coaching-p0` 브랜치를 만든다. 현재 기준으로는 main에 오늘 보고서 커밋만 적용해 시작한다. 코칭 기능은 글래스 UI 변경의 병합을 선행 조건으로 삼지 않는다.
- 글래스 구현도 함께 사용할 경우 별도 통합 작업에서 충돌을 정리한다. `claude/ios27-liquid-glass-style-check-e5ysk2@4be4552`에는 보고서 브랜치에 없는 후속 3커밋이 있다. 그중 `6a603e8`은 RecordsLoadingUiTest의 FitnessComposeTheme import 수정, `1dd03df`는 글래스 테스트의 semantics 탐색 수정이다. 과거 브랜치 전체를 중복 병합하지 않는다.

현재 [보고서 브랜치 CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41160593934)는 Android 테스트 컴파일 실패다. iOS와 문서 검증은 통과했다. [main CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41009226252)는 통과했다. 서로 다른 커밋의 결과를 이번 구현의 성공 증거로 재사용하지 않는다.

구현 규칙은 [AGENTS.md](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/AGENTS.md)와 [정돈 리팩터링 규칙](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/docs/FitnessApp_정돈_리팩터링_규칙.md)을 따른다. 과거 v50·Java 중심 CURRENT 설명은 실제 최신 소스로 보정하고, 기능별 소유권·공개 API·로컬 우선·명시적 migration 원칙은 유지한다.

## 2. 현재 구현에서 재사용할 부분과 공백

| 확인한 현재 구현 | 구현 계획에 미치는 영향 |
| --- | --- |
| DevelopmentGoal에 목표·주간 횟수·집중 부위·시작일이 있다. 목표는 근비대·근력·감량·지구력·유지 5종이다. | 목표 모델을 중복 생성하지 않는다. 보고서에 없는 지구력도 보존한다. P0에서는 기존 목표와 시작일을 표시하고 채택 시 목표 사본을 남긴다. |
| DevelopmentReportService가 Workout·Body·Meal·Development의 읽기 API를 조합한다. | 새 코칭 보고서도 같은 방식으로 조립한다. 다른 기능 DAO에 직접 접근하지 않는다. |
| DevelopmentSetAdvisor는 최근 28일 같은 루틴의 최대 3회 기록과 최근 7일 회복 요약을 사용한다. 목표는 판단 입력이 아니다. | P0는 이 규칙을 유지한다. 목표가 이미 세트 추천을 결정하는 것처럼 설명하지 않는다. |
| DevelopmentOverviewContent는 주간 요약, 다음 운동, 회복·식사 조언, 관심 운동 변화, 접을 수 있는 상세 근거를 제공한다. | 이 컴포넌트들을 재배치하고 조정 이력·후속 점검을 추가한다. 전체 화면을 다시 만들 필요가 없다. |
| WorkoutSetCountProposal은 근거 기록·종목·날짜·루틴·performanceKey·signature·목표 세트 수·규칙 버전을 가진다. | 기존 DTO를 출발점으로 쓰되, 별도 제안 ID·사용자 선택·적용 세션·후속 결과를 발전 기능에 저장한다. |
| WorkoutRoomStorage.createSessionWithSetCountProposal은 검증과 새 세션/입력 세트 생성을 트랜잭션으로 묶는다. | 새 세션 생성과 코칭 채택 기록을 하나의 외부 트랜잭션으로 묶어 확장한다. |
| 기존 시작 경로는 진행 중인 운동이 있으면 OPEN_EXISTING으로 기존 운동을 연다. | 이 분기에서는 새 조정이 적용됐다고 저장하지 않는다. |
| 기존 비교기는 본세트 수가 달라지면 DIFFERENT_CONDITIONS를 반환한다. | 세트 추가 후 반복수 합계 증가를 효과로 판정하지 않는다. 전후를 병렬 표시하고 별도 비교 가능성·이행 정보를 제공한다. |
| 조회는 최근 56일·운동별 최대 8개 표본 중심이다. | 오래된 조정 이력이 사라지지 않도록 명시적 ID로 상태·근거를 읽는 좁은 Workout 읽기 API가 필요하다. |
| Room 현재 버전은 57이며 명시적 백업·복원과 계정별 소유권 처리가 있다. | 다음 미사용 버전의 additive migration, 백업·복원·소유권 이전 검증을 함께 계획한다. |

**P0 자동 적용 지원 범위:** 기존과 같은 루틴의 식별 가능한 단일 근력 종목, WEIGHT_REPS, 유효한 일반 본세트, 묶음·드롭세트 제외, 지원되는 부하 조건. 기존의 1~12세트 및 기준 대비 최대 1세트 변경 제한을 보존한다. 지원 밖 기록은 직접 확인 경로를 제공하고 자동 적용하지 않는다.

## 3. P0 사용자 흐름

1. **이번 목표:** 현재 목표, 시작일, 관심 운동·부위를 보여준다. 미설정이면 설정 경로를 제공하고 목표에 따른 판정은 보류한다.
2. **지난 기간의 변화:** 수행·컨디션·신체·이행을 분리해 표시한다. 비교 기간·기록 수·누락을 함께 보여준다. P0에서는 현재 제공 가능한 사실을 재사용한다.
3. **이번 조정 한 가지:** 관심 운동 중 실행 가능한 제안 하나를 제시한다. 이유·기준 세트·제안 세트·점검 예정일을 보여준다. 사용자는 ‘이 구성으로 운동 시작’ 또는 ‘보류’를 선택한다.
4. **실행:** 채택한 제안과 새 세션을 함께 저장한 뒤 기존 운동 화면으로 이동한다. 입력 세트 생성은 완료 기록으로 간주하지 않는다.
5. **지난 조정의 결과:** 실제 적용 종목의 완료 여부·실제 세트 구성·수행·컨디션·누락·이후 비교 가능한 기록 수를 보여준다.
6. **후속 선택:** ‘유지’, ‘이전 구성으로 돌아가기’, ‘추가 관찰’을 저장한다. 돌아가기는 다음 운동의 입력 구성 선택이다. 과거 기록과 루틴 원본을 수정하지 않는다.
7. **상세 근거:** 근거 기록과 연구 설명은 펼쳐서 확인한다.

P0는 기존 제안의 채택·보류와 후속 선택을 먼저 완성한다. 임의의 세트 수를 입력하는 ‘직접 수정’ 편집기는 P1로 둔다. 점검 예정일은 기본적으로 채택일 +7일인 제품 정책으로 제안한다. 날짜가 지나도 기록이 부족하면 ‘점검 예정/기록 필요’로 남기며 훈련 효과나 근비대를 판정하지 않는다.

## 4. 저장 모델과 트랜잭션

제안하는 새 모델·클래스 이름은 구현 시 기존 명명과 조정할 수 있다.

| 저장소 | 최소 정보 |
| --- | --- |
| development_adjustments | ID, 소유자, 중복 방지 키, 사용자 선택, 운동 performanceKey·루틴 ID, 제안 규칙·사본 버전, 목표 사본, 근거 기록 ID·signature·기간·비교 조건, 이전/제안 구성, 선택 시각, 적용 세션 ID·종목 occurrence ID, 점검 예정일 |
| development_adjustment_reviews | 조정 ID·소유자, 점검 시각, 사용자가 확인한 적용/후속 근거 ID·signature와 요약 사본, 비교 가능 여부·이유, 유지/이전 구성/추가 관찰 선택 |

- 발전 기능이 조정·점검 이력을 소유한다. 실제 운동 기록은 Workout, 신체 기록은 Body, 회복 기록은 Recovery의 소유권을 유지한다.
- ‘수락됨’과 ‘실제로 완료함’을 분리한다. 적용 세션과 해당 종목의 현재 상태는 읽기 API로 다시 확인한다.
- 근거가 수정·삭제돼도 채택 당시 사본은 보존한다. 현재 증거와 달라졌다는 이유를 표시하고 자동 결과 판정은 보류한다. 운동 삭제가 코칭 이력을 함께 지우는 cascade는 두지 않는다.
- 동일 제안의 중복 선택은 같은 조정·세션을 반환한다. UI 버튼 비활성화 외에 DB의 소유자+중복 방지 키 제약과 트랜잭션 검사를 사용한다.
- 첫 범위에서는 진행 중인 조정 하나를 명확히 표시한다. 진행 중인 조정을 덮어써서 새 제안으로 교체하지 않는다.
- 후보 제안은 매번 DB에 저장하지 않는다. 사용자 선택 시점의 정보만 영속화한다. 보류 후 재선택은 명시적 상태 전이로 처리한다.

**권장 쓰기 경로**

Development/Workout UI → DevelopmentCoachingService → 각 기능 Repository API → 같은 RoomTransactionRunner → 각 기능 DAO → SQLite.

채택·시작 한 번의 트랜잭션은 다음을 보장한다.

1. 작업 시작 당시 AccountScope와 현재 소유자를 확인한다.
2. 같은 선택 요청의 기존 적용 결과를 조회한다. 있으면 새 세션을 만들지 않는다.
3. 이미 진행 중인 운동이 있으면 기존 운동을 반환하고 채택·적용 이력은 새로 생성하지 않는다.
4. UI의 오래된 제안을 그대로 믿지 않는다. 최신 근거로 제안을 재생성해 fingerprint를 대조하고 Workout의 기존 유효성 검사도 실행한다.
5. 새 세션을 만들고 대상 occurrence ID를 확정한 뒤 발전 이력에 연결한다.
6. 어느 쓰기든 실패하면 세션과 이력을 함께 롤백한다. 커밋 이후 화면용 재조회 실패는 ‘운동 생성 실패’로 표시하지 않는다.

현재 Workout signature는 마지막 근거 운동을 검증한다. 코칭 판단에는 그 이전 회차와 회복 정보도 기여하므로 채택 시 전체 입력 근거의 최신성 확인을 추가한다. 목표·근거·규칙 버전의 사본으로 나중에 추천 이유를 재현할 수 있어야 한다.

## 5. 재평가의 비교·표시 정책

- 정확한 canonical 운동 identity, performanceKey, 부하 방식, 세트 유형, 루틴·occurrence와 완료 여부를 확인한다. 이름 유사도로 종목을 연결하지 않는다.
- 적용 세션이 완료됐어도 대상 운동을 수행하지 않았다면 ‘조정 실행 미확인’이다.
- 계획한 세트 수와 실제 완료 세트 수를 함께 표시한다. 사용자의 중량·구성 변경이 있으면 이행 및 비교 가능성에 반영한다.
- 기존 비교 규칙을 통과한 표본만 같은 조건의 수치 차이를 계산한다. RIR·휴식·컨디션은 기록 여부와 조건 차이를 별도로 표시한다. 기록되지 않은 가동범위 등은 동일했다고 추정하지 않는다.
- 세트 수가 달라진 전후는 나란히 보여주고 ‘조건이 달라 직접 비교 불가’로 표시한다. 이후 같은 조정 구성의 완료 기록이 둘 이상 쌓이면 그 안의 수행 추세를 보여줄 수 있다.
- 비교 가능한 후속 기록이 없으면 ‘효과 판정 보류 + 필요한 기록 한 가지’를 표시한다. 이후 차이는 관찰 결과이며 조정의 인과 효과라고 설명하지 않는다.
- 근거 기록 수정·삭제, 적용 세션 취소·삭제, 목표 변경을 감지한다. 과거 목표 사본은 유지하고 새 목표와 섞어 해석하지 않는다.
- ‘이전 구성으로 돌아가기’는 최신 기록으로 유효성을 다시 검사한 명시적 다음 세션 생성이다. 자동 추천 규칙 버전을 가장하지 않고 사용자 선택임을 구분한다. 기존 자동 적용 제한을 만족하지 않으면 수동 구성 확인으로 안내한다.

## 6. PR 분할과 완료 조건

| 순서 | 구현 범위와 주요 변경 위치 | 완료 조건 |
| --- | --- | --- |
| 준비 | main 재확인, 보고서만 반영, 기존 최소 검증 기준 확보 | 기준 SHA·검사 결과 고정. 코칭 작업에 글래스 브랜치 변경이 섞이지 않음 |
| PR-1: 이력 저장 | feature/development/model·api·data에 조정/점검 모델과 Repository 추가. core/database에 Entity·DAO·migration 등록. 백업·복원·계정 처리 연결 | 신규 DB와 구버전 업그레이드, 두 계정 격리, 이력 저장·조회, 구형 백업 호환 확인 |
| PR-2: 채택부터 실행 | DevelopmentCoachingService, AppContainer/Factory 조립, DevelopmentViewModel·ScreenActions·WorkoutSessionViewModel의 기존 시작 경로 연결. 채택·보류 UI | 채택 한 번에 세션 하나와 이력 하나. 재시도·빠른 연타·프로세스 복구·저장 실패에서 중복·고아 데이터 없음 |
| PR-3: 실행·결과 점검 | 필요한 ID 기반 Workout 읽기 API, DevelopmentCoachingReviewAnalyzer/조회 서비스, 실제 수행 및 비교 가능성 표시, 후속 선택과 다음 운동 연결 | 완료·취소·미실행·비교 불가를 구분. 유지/복귀/관찰을 저장하고 다시 표시. P0 종단 흐름 완료 |
| PR-4: 화면 정리 | DevelopmentReportService·UiState·DevelopmentOverviewContent에서 목표→변화→조정→결과→근거 순서 구성, 오류·빈 상태·접근성 정리 | 큰 글자·화면 재생성·뒤로가기·계정 전환에서 주요 행동과 선택 상태 유지. 목표 수정·관심 운동·기존 조언 접근 보존 |
| PR-5: 목표별 판단 P1 | DevelopmentSetAdvisor의 명시적 입력에 목표·주간 분포·여러 회차 수행·회복·신체 추세를 연결. 직접 수정 UX | 같은 기록에서 목표에 맞는 설명 차이, 결측 시 판단 보류, 규칙 버전 전환 회귀 검증 |
| PR-6: 연구 설명 P2 | 검토 자료 검색·출처 메타데이터·단일 LLM 어댑터·동의/실패 대체 경로 | 고정 질문 10개의 수치 일치·인용 적합성·판단 보류·시간·비용 평가 통과 |

PR-1~4는 P0 전체 범위다. 각 PR은 독립 검증 가능한 변경으로 제한하되 PR-2부터 실제 사용자 흐름을 확인한다. 순수 비교·판단 로직은 JVM 단위 테스트가 가능하도록 Android 의존성을 분리한다. 새 범용 프레임워크, 이벤트 버스, 도메인별 모듈 전면 분할은 필요하지 않다.

## 7. DB·백업·계정 세부 조건

- 확인 시점 최신 버전은 57이다. 구현 시 최신 버전을 다시 확인하고 다음 미사용 번호로 additive migration을 추가한다. 현 상태라면 57→58이다.
- 기존 DB 파일명, 운동/세트 ID, 부하·입력 단위, 스냅샷, 삭제 상태와 sync cursor를 보존한다. destructive migration은 사용하지 않는다.
- Room 등록 Entity·DAO·exported schema와 신규 설치/업그레이드 검증을 함께 갱신한다.
- FitnessDatabaseContract의 v50 인수용 고정 테이블 목록과 현재 추가 테이블 목록을 혼동하지 않는다. 새 테이블 때문에 과거 v50 DB를 거절하도록 만들지 않는다.
- LocalDataBackupService의 테이블 순서·버전별 허용 테이블·소유자 처리·복원 검증을 확장한다. 백업 형식 버전과 DB 버전을 구분한다.
- 구형 백업에는 코칭 이력이 없어도 복원된다. 복원한 Workout과 맞지 않는 기존 코칭 연결을 남기지 않도록 해당 소유자의 이력 처리도 같은 복원 트랜잭션에 포함한다.
- 신규 백업은 조정·점검·세션 연결을 보존하고 다른 계정의 이력이 섞이지 않게 한다.
- 익명 로컬 기록을 로그인 계정으로 귀속시키는 기존 정책을 따라 새 이력의 owner와 세션 연결도 함께 이전한다. 계정 A의 늦은 비동기 결과를 계정 B에 적용하지 않는다.
- P0 이력은 로컬에 저장한다. 기존 외부 동기화 계약에 새 상세 이력을 추가하는 작업은 별도 범위다.

## 8. 검증 계획

| 영역 | 반드시 확인할 사례 |
| --- | --- |
| 저장·원자성 | 세션 저장 성공 후 이력 저장 실패, 그 반대 단계의 실패, 중복 클릭·재시도, 커밋 후 재조회 실패, 진행 중 운동 존재 |
| 제안 유효성 | 날짜 경계, 오래된 제안, 최신 근거 변경, 이전 근거 회차/회복 수정, 다른 목표·규칙 버전, 루틴 또는 종목 연결 변경 |
| 실제 실행 | 채택 후 미완료, 취소·삭제, 다른 종목만 완료, 계획 세트와 실제 세트 차이, 운동 구성 직접 수정 |
| 재평가 | 동일 조건 표본, 변경된 세트 수·중량·부하 조건, 후속 표본 부족, 근거 수정·삭제, 56일보다 오래된 조정 |
| 계정·복구 | 두 계정 격리, 요청 중 계정 전환, 익명→계정 귀속, 프로세스 종료·재실행, 백업·복원 |
| DB migration | 신규 설치, 57→다음 버전, 지원 배포 버전의 전체 업그레이드 경로, Room schema 검증, 기존 데이터 보존 |
| UI | 채택·보류·유지·복귀·관찰, 목표 미설정, 비교 불가 설명, 큰 글자·작은 화면·명암 테마, 화면 재생성·뒤로가기, 로딩/오류 후 재시도 |
| 기존 기능 회귀 | 루틴 원본 보존, 기존 운동 시작·완료·삭제, 입력 단위, 휴식 타이머, 목표 수정, 관심 운동, 기존 영양·회복 안내 |

기본 검증은 저장소가 요구하는 `testDebugUnitTest assembleDebug`이며 Linux에서는 `bash scripts/cloud/verify.sh`로 shared 테스트·Android 단위 테스트·앱/테스트 APK 빌드·lint를 실행한다. DB/Repository PR은 관련 instrumentation을 에뮬레이터 또는 연결된 테스트 기기에서 추가 실행한다. shared API가 바뀌면 기존 macOS KMP·Swift host gate도 확인한다.

서명 호환성이 확인된 테스트 기기에서는 기존 DB를 보존한 업데이트와 P0 종단 흐름을 확인한다. 테스트 APK 빌드는 instrumentation 실행과 구분하고, 기존 사용자 데이터를 삭제해 설치 문제를 우회하지 않는다.

**P0 인수 기준:** 같은 계정에서 제안 하나를 채택하고 실제 적용 종목을 완료한 뒤 앱을 다시 열었을 때, 적용 기록·실제 수행·비교 가능성·컨디션·필요한 추가 기록과 후속 선택이 유지된다. 중복 생성·교차 계정 노출·과거 기록 변경이 없어야 한다.

## 9. P1·P2의 설계 경계

P1은 현재 목표 5종을 보존한다. 근비대·근력·감량·유지에 대한 설명을 목표별로 구분하고, 지구력에는 검증되지 않은 근력 규칙을 그대로 적용하지 않는다. 근비대 판단은 장기 신체 변화와 수행을 함께 참고하며 주간 세트 증가나 체중 증가만으로 확정하지 않는다. 주간 훈련량은 같은 기간끼리 비교하고 운동의 지정 부위 기준임을 표시한다. 보조 근육 기여도를 임의의 정밀 점수로 만들지 않는다.

목표별 판단 규칙을 바꿀 때 WorkoutRoomStorage가 현재 `local-sets-v1`만 허용하는 점을 함께 수정해야 한다. 발전 쪽 버전 문자열만 변경해 실행 경로가 끊기지 않도록 생산자·검증자·저장 사본·과거 이력 reader를 함께 검증한다.

P2는 계산·기록 소유권을 앱에 유지한다. 기록 사실→가능한 해석→선택 가능한 행동→출처·한계 순서로 설명하고, 외부 모델에는 동의한 최소 요약만 보낸다. 네트워크 실패 시 로컬 보고서와 운동 기록은 계속 동작한다. 연구의 대상자·기간·평가 지표·발행일과 주장-인용 일치를 확인한다. 문서에 링크된 문헌의 주장을 이번 계획에서 새로 검증한 것으로 표현하지 않는다. 멀티 LLM은 단일 모델 평가에서 확인된 오류 감소가 입증될 때 검토한다.

## 10. 주요 소스 근거

아래는 모두 보고서 브랜치 `44d232f` 기준이다. 구현 착수 시 main과 다시 대조한다.

- [DevelopmentGoal.java](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/java/com/yeonsik/fitnessapp/development/DevelopmentGoal.java): 기존 목표 필드와 5개 목표.
- [DevelopmentReportService.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/development/application/DevelopmentReportService.kt): 기능별 읽기 API 조합, 회복 요약과 기존 세트 제안 호출.
- [DevelopmentSetAdvisor.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/development/application/DevelopmentSetAdvisor.kt): 적용 대상과 local-sets-v1 판단 규칙.
- [DevelopmentProgressAnalyzer.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/development/application/DevelopmentProgressAnalyzer.kt#L42): 비교 가능성, 세트 수 차이의 처리.
- [WorkoutSetCountProposal.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/shared/src/commonMain/kotlin/com/yeonsik/fitness/shared/feature/workout/model/WorkoutSetCountProposal.kt): 제안 DTO와 근거 signature.
- [WorkoutRoomStorage.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/workout/data/WorkoutRoomStorage.kt#L399): 검증 및 새 세션 원자적 생성.
- [WorkoutSessionViewModel.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/workout/ui/WorkoutSessionViewModel.kt#L442): 기존 진행 중 운동 재개·제안 시작·커밋 후 조회 실패 처리.
- [RoomTransactionRunner.java](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/java/com/yeonsik/fitnessapp/core/database/RoomTransactionRunner.java): 교차 기능 작업의 동일 DB 트랜잭션 재사용.
- [DevelopmentOverviewContent.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/development/ui/DevelopmentOverviewContent.kt): 현재 화면 순서와 액션.
- [FitnessDatabaseContract.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessDatabaseContract.kt), [FitnessRoomMigrations.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/kotlin/com/yeonsik/fitnessapp/core/database/FitnessRoomMigrations.kt): 현재 버전·v50 경계·명시적 migration.
- [LocalDataBackupService.java](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/app/src/main/java/com/yeonsik/fitnessapp/backup/LocalDataBackupService.java): 백업 형식과 테이블·소유자 처리.
- [후속 브랜치 비교](https://github.com/Yeon-sik/Yeonsik-Fitness-App/compare/44d232fd3600cf94559bac95e7450684299b1820...4be4552726e7609a8848706fb39b4a0b1a90281e): 오늘 보고서 브랜치에 없는 테스트 수정.

검증 상태: 원격 커밋·문서·지정 소스·CI 로그 읽기 및 계획 작성 완료. 이번 작업에서 제품 단위 테스트·Android 빌드·instrumentation·실기기 검증은 실행하지 않았다.
