# FitnessApp 발전 탭 코칭 구현 계획

작성일·개정일: 2026-10-31. 상태: PROPOSED — 착수 전 검토를 반영한 구현 계획이다. 기준 계획 커밋은 `3170a73`이며, 아래 준비 작업·제품 구현·main 병합은 아직 수행하지 않았다.

**첫 완료 목표는 관심 운동 하나의 ‘조정 채택 → 실제 운동 → 다음 점검 → 사용자 후속 선택’을 로컬에서 끝까지 추적하는 것이다.** P0에서는 기존 세트 제안 규칙을 유지하고, 목표별 판단 확장은 P1, 연구 검색·LLM 설명은 P2에서 진행한다.

## 1. 기준 소스와 브랜치 전략

- 오늘 확인한 원격 커밋: [44d232f](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/44d232fd3600cf94559bac95e7450684299b1820), 커밋 시각 2026-10-31 02:33:49 UTC. 푸시 이벤트 시각과 커밋 시각을 동일시하지 않는다.
- 브랜치: `feat/development-coaching-report-20261031`. `44d232f`가 추가한 파일은 [코칭 구조 개선 보고서](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/docs/FitnessApp_발전탭_코칭구조_개선보고서_2026-10-31.md) 한 개이며 계획서는 이후 `3170a73`에서 추가했다. 두 문서의 TARGET·PROPOSED는 구현 전 제안이다.
- 확인 시점 `main`: [e7c7c35](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/e7c7c35bc33c8471f470f986612cd13c343238b8). 최초 조사 당시 보고서 브랜치 `44d232f`는 main 대비 16커밋 앞서고 1커밋 뒤처지며, 오늘 문서 이전의 UI·휴식 알림 변경도 포함한다.
- 문서 개정은 `feat/development-coaching-report-20261031`에 보관한다. 구현은 1.1절 준비를 main에 선반영한 뒤 그 SHA에서 별도 `feat/development-coaching-p0` 브랜치를 만들고 보고서·계획서 문서 변경만 반영한다. 보고서 브랜치 전체를 구현 시작점으로 사용하지 않는다.
- 코칭 기능은 글래스 UI 병합을 선행 조건으로 삼지 않는다. 대신 1.2절의 통합 순서와 충돌 시 보존 조건을 고정한다.

현재 [보고서 브랜치 CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41160593934)는 Android 테스트 컴파일 실패다. iOS와 문서 검증은 통과했다. [main CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41009226252)의 통과 범위는 Android/shared 단위 테스트·빌드·lint와 iOS gate이며, **main에는 에뮬레이터 계측 job·실행 스크립트가 없다. 이 통과를 DB migration·복원 계측의 기준선으로 사용할 수 없다.** 글래스 후속 [CI run 41088063762](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41088063762)에서는 계측 실행까지 진행됐지만 7개 클래스가 실패했다. 서로 다른 커밋·검사 범위의 결과를 이번 구현의 성공 증거로 재사용하지 않는다.

구현 규칙은 [AGENTS.md](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/AGENTS.md)와 [정돈 리팩터링 규칙](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/44d232fd3600cf94559bac95e7450684299b1820/docs/FitnessApp_정돈_리팩터링_규칙.md)을 따른다. 과거 v50·Java 중심 CURRENT 설명은 실제 최신 소스로 보정하고, 기능별 소유권·공개 API·로컬 우선·명시적 migration 원칙은 유지한다.

### 1.1 준비: 계측 CI 이식과 실패 fixture 수정

별도 준비 PR에서 다음 **CI 전용 3커밋을 순서대로** main에 이식한다. 원래 브랜치의 조상에는 UI 변경도 있으므로 브랜치 전체 병합 대신 이 세 변경만 선별한다.

| 순서 | 커밋 | 이식할 내용 |
| --- | --- | --- |
| 1 | [122275d7](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/122275d7f2617705c92fb74025f0295e6c7423b4) | API 34 에뮬레이터 job, 핵심 계측 목록·실행 스크립트, cloud 개발 문서 |
| 2 | [42de2392](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/42de23920f551bd8280de3b206783d8f08fff871) | 클래스별 독립 실행, 패키지 검사, XML·HTML·logcat·실패 요약 보존 |
| 3 | [a5047615](https://github.com/Yeon-sik/Yeonsik-Fitness-App/commit/a5047615981c7f5bed765f09c66fcf7afb25ca8d) | Room schema 불일치의 열·인덱스 차이 요약 |

이어 fixture 수정은 별도 커밋으로 분리한다. 아래 7개는 위 계측 로그에서 실패가 확인된 전체 클래스다.

| 실패 클래스 | 수정·확인할 기준 |
| --- | --- |
| FitnessStageZeroBaselineTest | 신규 v50 설치의 필수 테이블과 업그레이드에만 남는 optional `dining_out_menu_add_on_links`를 구분한다. legacy 테이블 보존도 따로 검증한다. |
| FitnessDatabaseMigrationTest | 역사 버전 fixture의 `devices` 등 당시 존재한 필수 테이블·행을 복원하고 실제 업그레이드 경로와 계정 귀속을 검증한다. |
| BodyMetricsRepositoryFacadeTest | 내용 없는 DB에 version=50만 지정하는 fixture를 유효한 schema로 바꾼다. 빈 v50 DB 거절 조건을 약화하지 않는다. |
| FitnessRepositoryLoadStateTest | 필수 테이블을 갖춘 DB에서 부하·운동 identity·입력 단위 검증이 실제 assertion까지 도달하게 한다. |
| FitnessRepositorySummaryProjectionV2Test | `devices` 누락을 해결하고 완료 요약·삭제 tombstone의 기존 assertion을 보존한다. |
| WorkoutRoomReadCorrectnessTest | DB 초기화와 실제 조회값 assertion 실패를 각각 추적한다. assertion을 통과시키기 위해 조회 의미를 임의로 바꾸지 않는다. |
| MealRecordRepositoryTest | 등록용 상위 음식 fixture의 나트륨·포화지방·당류 등 필수 값을 보완하되, 검증 대상 snapshot의 known-zero·미확인·부분 자식 의미는 유지한다. |

fixture 오류와 실제 제품 결함을 구분하고 제품 결함이면 별도 회귀 테스트와 수정 커밋을 둔다. 실패 클래스를 목록에서 빼거나 새 skip·allow-failure를 추가해 통과시키지 않는다. 계측 목록의 클래스별 실제 실행·결과 존재, 기존 skip의 사유, 전체 실패 0을 확인한다.

**준비 완료 조건:** CI 이식 3커밋과 fixture 수정이 검증된 준비 PR을 P0보다 먼저 main에 반영한다. 이 main SHA의 단위 테스트·앱/테스트 APK·lint·계측 전체 결과와 보고서를 함께 고정한다. 7개 클래스와 기존 핵심 계측이 통과하기 전에는 PR-1의 신규 migration 작업에 착수하지 않는다. 이 준비는 현재 미완료다.

### 1.2 글래스 통합 순서와 충돌 시 보존 조건

이 계획의 통합 대상으로는 최신 main 기반 변경과 후속 테스트 수정이 있는 `claude/ios27-liquid-glass-style-check-e5ysk2@4be4552`를 선택한다. 병행 구현 `feat/fitness-glass-completion@97f3654`는 보관·대조용으로 두고 함께 병합하지 않는다. 이는 계획상의 선택이며 실제 병합 완료를 의미하지 않는다.

통합 순서는 **준비 PR → P0 PR-1 → PR-2 → PR-3 → PR-4 → 선택한 글래스 구현의 별도 통합 PR**로 고정한다. 글래스 통합 PR은 P0 완료 main 위에 다시 정리하고, 이미 이식한 CI 3커밋은 재적용하지 않는다. `6a603e8`(theme import), `1dd03df`(semantics 탐색) 수정은 글래스 테스트와 함께 보존한다. 글래스 신규 테스트의 목록 등록은 해당 소스 통합 뒤에 한다.

| 충돌 파일 | 통합 시 함께 보존할 내용 |
| --- | --- |
| WorkoutSessionViewModel | 코칭 생성 서비스와 `restTimerAlarms`를 모두 주입한다. 완료·취소·삭제·빈 세션 폐기의 타이머/알람 정리 경로를 보존한다. |
| AppViewModelFactory | Kotlin 생성자 인자를 이름으로 넘겨 코칭 의존성과 `restTimerAlarms`가 위치 순서로 뒤바뀌지 않게 한다. 두 의존성의 실제 주입을 검증한다. |
| AppContainer | 같은 RoomTransactionRunner·Workout 저장소를 공유하는 코칭 서비스 조립과 계정 변경 시 알람 취소를 함께 유지한다. |
| DevelopmentScreen | 코칭 상태·액션과 글래스의 FitnessLoadingState·지연/표시 정책을 함께 보존한다. |
| DevelopmentOverviewContent | 목표·조정·점검 UI와 FitnessModalBottomSheet를 함께 유지하고 재평가 액션·시트 닫기·계정 변경 시 초기화를 재검증한다. |

위 파일은 PR-2 이후 통합 확인 목록으로 관리하고 통째로 어느 한쪽 버전을 선택해 해결하지 않는다. 통합 후 P0 종단 테스트, 휴식 종료/알람 정리, 로딩·시트 계측을 다시 통과시킨다.

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
3. **이번 조정 한 가지:** 관심 운동 중 실행 가능한 제안 하나를 제시한다. 이유·기준 세트·제안 세트와 ‘실행 후 다음 비교 가능 기록에서 점검’ 조건을 보여준다. 사용자는 ‘이 구성으로 운동 시작’ 또는 ‘보류’를 선택한다.
4. **실행:** 채택한 제안과 새 세션을 함께 저장한 뒤 기존 운동 화면으로 이동한다. 입력 세트 생성은 완료 기록으로 간주하지 않는다.
5. **지난 조정의 결과:** 실제 적용 종목의 완료 여부·실제 세트 구성·수행·컨디션·누락·이후 비교 가능한 기록 수를 보여준다.
6. **후속 선택:** ‘유지’, ‘이전 본세트 수로 돌아가기’, ‘추가 관찰’을 저장한다. P0의 돌아가기는 채택 전 본세트 수를 다음 운동에 선택하는 뜻이다. 과거 중량·반복·휴식 전체를 되감지 않고 과거 기록과 루틴 원본도 수정하지 않는다.
7. **상세 근거:** 근거 기록과 연구 설명은 펼쳐서 확인한다.

P0는 기존 제안의 채택·보류와 후속 선택을 먼저 완성한다. 임의의 세트 수를 입력하는 ‘직접 수정’ 편집기는 P1로 둔다. 점검 가능 조건은 **적용 종목의 실제 완료 + 이후 같은 조건에서 비교 가능한 완료 기록 1회 + 근거 유효성**이다. 적용 세션과 후속 기록은 서로 다른 기록이어야 한다. 조건이 충족되면 7일이 지나기 전에도 점검할 수 있고, 운동하지 않은 주에는 미실행/후속 기록 대기로 남긴다.

참고 날짜가 필요하면 채택일이 아니라 적용 종목 완료일 +7일로 계산하고 완료 전에는 비워 둔다. 날짜만으로 점검 완료·기한 초과·효과를 판정하지 않으며, 이 날짜에 따른 자동 알림은 P0에 추가하지 않는다. 유지·돌아가기 같은 명시적 사용자 선택은 기록 부족 때도 가능하지만, 이를 데이터에 따른 효과 판정으로 표시하지 않는다.

## 4. 저장 모델과 트랜잭션

제안하는 새 모델·클래스 이름은 구현 시 기존 명명과 조정할 수 있다.

| 저장소 | 최소 정보 |
| --- | --- |
| development_adjustments | ID, 소유자, 중복 방지 requestKey, 사용자 선택, origin·실행 계약 버전·제안 규칙 버전·사본 형식 버전, 운동 performanceKey·루틴 ID, 목표 사본, 4.1절 입력 사본·fingerprint, 이전/제안 본세트 수, 선택 시각, 적용 세션 ID·종목 occurrence ID, 상태·종결 사유, 점검 조건·선택적 참고 날짜 |
| development_adjustment_reviews | 조정 ID·소유자·점검 ID, 점검 시각, 확인한 적용/후속 근거 ID·signature와 요약 사본, 비교 가능 여부·이유, 유지/이전 본세트 수/추가 관찰 선택, 사용자 선택용 계약 버전·다음 실행 요청/세션 연결 |

- 발전 기능이 조정·점검 이력을 소유한다. 실제 운동 기록은 Workout, 신체 기록은 Body, 회복 기록은 Recovery의 소유권을 유지한다.
- ‘수락됨’과 ‘실제로 완료함’을 분리한다. 적용 세션과 해당 종목의 현재 상태는 읽기 API로 다시 확인한다.
- 근거가 수정·삭제돼도 채택 당시 사본은 보존한다. 현재 증거와 달라졌다는 이유를 표시하고 자동 결과 판정은 보류한다. 운동 삭제가 코칭 이력을 함께 지우는 cascade는 두지 않는다.
- 동일 요청의 중복 선택은 적용 세션의 현재 상태까지 확인해 4.2절의 구분된 결과를 반환한다. UI 버튼 비활성화 외에 DB의 소유자+requestKey 제약과 트랜잭션 검사를 사용한다. requestKey는 명시적 사용자 동작마다 생성해 재시도에 재사용하며, 입력 fingerprint와 구분한다.
- 첫 범위에서는 소유자별 진행 중인 조정 1개를 보장한다. 익명→계정 귀속과 복원 때도 7절의 충돌 규칙을 적용한다. 보류·취소·참조 불가·귀속 충돌로 종결한 이력은 활성 조정으로 세지 않는다.
- 후보 제안은 매번 DB에 저장하지 않는다. 사용자 선택 시점의 정보만 영속화한다. 보류 후 재선택은 명시적 상태 전이로 처리한다.

**권장 쓰기 경로**

Development/Workout UI → DevelopmentCoachingService → 각 기능 Repository API → 같은 RoomTransactionRunner → 각 기능 DAO → SQLite.

채택·시작 한 번의 트랜잭션은 다음을 보장한다.

1. 작업 시작 당시 AccountScope와 현재 소유자를 확인한다.
2. 같은 requestKey의 기존 조정과 적용 세션의 상태를 조회한다. 4.2절에 따라 유효한 재개·완료·참조 불가를 구분하며 같은 요청으로 새 세션을 만들지 않는다.
3. 이미 진행 중인 운동이 있으면 기존 운동을 반환하고 채택·적용 이력은 새로 생성하지 않는다.
4. 4.1절의 좁은 로컬 입력만 다시 읽어 fingerprint와 제안 결과를 대조한다. 보고서 전체 재조립·56일치 운동 조회·영양 분석·체중 조회를 이 트랜잭션에서 호출하지 않는다. Workout의 소유권·세션 상태·루틴·세트 유효성 검사도 같은 입력 범위에서 유지한다.
5. 새 세션을 만들고 대상 occurrence ID를 확정한 뒤 발전 이력에 연결한다.
6. 어느 쓰기든 실패하면 세션과 이력을 함께 롤백한다. 커밋 이후 화면용 재조회 실패는 ‘운동 생성 실패’로 표시하지 않는다.

### 4.1 재검사 범위와 실패 처리

PR-2에서 화면 후보 생성과 채택 재검사가 함께 쓰는 순수 입력 모델 `SetAdvisorInputV1`과 좁은 읽기 API를 정의한다. fingerprint의 내용은 다음으로 제한한다.

- AccountScope, 기준일·운동/루틴 identity, 28일 근거 범위와 정렬 기준.
- 실제 규칙에 사용한 최신 최대 3회 기록의 ID·occurrence·proposalSignature. 트랜잭션에서 같은 owner·performanceKey·루틴·날짜 조건으로 최신 3회를 다시 선택하므로, 기존 ID만 재조회해 새로 생긴 기록을 놓치지 않는다. signature에 없는 판정 입력(부하 방식·완료 상태·동일 운동 중복 여부 등)도 명시적으로 포함한다.
- 최근 7일 회복 요약의 실제 값: 평균 수면·수면 기록일 수·낮은 에너지/준비도 일 수·최근 에너지/준비도, 그리고 그 요약의 포함 여부·기록 유무 상태.
- 제안 규칙 버전과 사용자가 확인한 목표 사본. P0에서 목표는 표시·동의 맥락이며 목표별 세트 판단으로 사용됐다고 설명하지 않는다.

정렬·날짜·null·수치 직렬화를 고정한 버전 있는 입력 사본에서 해시를 만든다. 제안 수·이유·적용 가능 여부도 동일 입력의 순수 함수로 계산한다. 기존 비교기가 이 입력 밖의 기록을 필요로 하면 저장된 비교 결과를 믿거나 조회 범위를 넓히지 않고 적용을 보류한다. 기존 판정 fixture와 대조해 추천 범위가 조용히 넓어지지 않게 한다.

회복 입력은 `INCLUDED`(요약 포함), `NOT_RECORDED`(실제 미기록), `READ_ERROR`(조회 실패)를 구분한다. 오류를 `runCatching` 뒤 null로 바꿔 회복 없이 다시 추천하지 않는다. `READ_ERROR`에서는 채택을 중단하고 재시도를 표시하며 세션·이력 쓰기는 없다. `NOT_RECORDED`에서는 기존의 보수적 규칙과 결측 표시를 유지한다. 포함 여부·요약 값이 후보 생성 때와 달라지면 재확인을 요구한다.

트랜잭션의 읽기는 이 운동의 근거 최대 3회·회복 7일 요약·목표·기존 적용/진행 세션 및 루틴 확인으로 한정한다. 좁은 조회에서도 의미 있는 동률·중복 조건을 보존하고, 대용량 기록 fixture에서 조회 범위와 쓰기 트랜잭션 시간을 확인한다. 음식·체중·전체 DevelopmentReport API를 호출하지 않는 회귀 검사도 둔다.

### 4.2 재시도 반환값과 참조 세션 소실

| 기존 요청의 적용 세션 상태 | 반환·상태 전이 |
| --- | --- |
| 같은 소유자의 유효한 진행 세션 | `RESUMED_APPLIED`와 기존 세션 ID를 반환한다. 새 세션·새 조정은 없다. |
| 완료됐고 해당 종목 참조도 유효 | `ALREADY_COMPLETED`와 조정/점검 위치를 반환한다. 완료 세션을 새 운동처럼 재개하지 않는다. |
| 취소·soft-delete·hard-delete·해당 종목 누락 또는 다른 소유자 | `APPLIED_SESSION_UNAVAILABLE(reason)`를 반환하고 조정을 `APPLICATION_UNAVAILABLE`로 종결한다. 죽은 세션 ID로 이동하지 않는다. |
| DB 조회 자체가 실패 | 재시도 가능한 읽기 오류다. 소실로 확정하거나 조정을 종결하지 않는다. |
| 같은 요청은 없지만 다른 운동이 진행 중 | `OPEN_EXISTING`을 반환하고 코칭의 채택·적용 이력은 만들지 않는다. |

`APPLICATION_UNAVAILABLE`의 requestKey는 종결 상태로 남긴다. 같은 키로 자동 재생성하거나 이후 동기화로 행이 돌아왔다고 자동 재개하지 않는다. 사용자가 다시 시작하려면 최신 근거를 확인해 새 요청을 명시적으로 만들고, 새 조정에는 이전 조정 ID를 연결한다. 어느 유입 경로에서 참조가 바뀌었든 이 정책을 적용한다. UI 재구성에서 쓰지 않고 application 서비스가 조회·전이를 처리한다.

## 5. 재평가의 비교·표시 정책

- 정확한 canonical 운동 identity, performanceKey, 부하 방식, 세트 유형, 루틴·occurrence와 완료 여부를 확인한다. 이름 유사도로 종목을 연결하지 않는다.
- 적용 세션이 완료됐어도 대상 운동을 수행하지 않았다면 ‘조정 실행 미확인’이다.
- 계획한 세트 수와 실제 완료 세트 수를 함께 표시한다. 사용자의 중량·구성 변경이 있으면 이행 및 비교 가능성에 반영한다.
- 기존 비교 규칙을 통과한 표본만 같은 조건의 수치 차이를 계산한다. RIR·휴식·컨디션은 기록 여부와 조건 차이를 별도로 표시한다. 기록되지 않은 가동범위 등은 동일했다고 추정하지 않는다.
- 세트 수가 달라진 전후는 나란히 보여주고 ‘조건이 달라 직접 비교 불가’로 표시한다. 이후 같은 조정 구성의 완료 기록이 둘 이상 쌓이면 그 안의 수행 추세를 보여줄 수 있다.
- 비교 가능한 후속 기록이 없으면 ‘효과 판정 보류 + 필요한 기록 한 가지’를 표시한다. 이후 차이는 관찰 결과이며 조정의 인과 효과라고 설명하지 않는다.
- 근거 기록 수정·삭제, 적용 세션 취소·삭제, 목표 변경을 감지한다. 과거 목표 사본은 유지하고 새 목표와 섞어 해석하지 않는다.
- ‘이전 본세트 수로 돌아가기’는 아래 사용자 선택 계약으로 다음 세션을 만드는 P0 기능이다. 기존 자동 추천 규칙을 가장하지 않는다. 최신 기준과 1~12세트·최대 1세트 변경 등 지원 조건을 만족하지 않으면 수동 구성 확인으로 안내한다.

### 5.1 P0 PR-3: 사용자 선택용 실행 계약

현재 저장 로직은 `ruleVersion == "local-sets-v1"`만 허용한다. 따라서 복귀 실행에 필요한 생산자·Workout API/저장 검증자·저장 사본·이력 reader를 **P0 PR-3에서 함께** 구현한다. 문자열만 바꾸거나 자동 추천 검증에 무조건 OR 조건을 붙이지 않는다.

| 경계 | 자동 세트 제안 | 점검 후 사용자 복귀 선택 |
| --- | --- | --- |
| 요청 모델·출처 | 기존 WorkoutSetCountProposal, `LOCAL_ADVISOR` | 신규 WorkoutUserSetCountChoice, `USER_REVIEW` |
| 버전 | 기존 `local-sets-v1` 유지 | 실행 계약 `user-set-choice-v1`, action=`REVERT_TO_BASELINE` |
| 생산자 | 기존 DevelopmentSetAdvisor | DevelopmentCoachingService가 실제 복귀 확인 후 원래 조정·점검과 최신 근거로 생성 |
| 요청 필드 | 기존 근거·identity·signature·세트 수·규칙 버전 | 조정 ID·점검 ID·requestKey·action·계약 버전, 최신 근거 기록/occurrence/date/referenceDate·루틴·performanceKey·signature, 목표 본세트 수 |
| 유효성 검사 | 기존 local-sets-v1 검증 | 발전 서비스는 같은 owner의 조정/점검·명시적 선택·원래 기준 세트 수 일치를 확인한다. Workout 저장소는 타입/버전·최신 소유 기록·identity·루틴·signature·허용 세트 범위를 확인한다. |
| 저장 사본 | 자동 추천 출처와 규칙 버전 | 사용자 선택 출처·계약 버전·원래 제안 규칙 버전·조정/점검 참조·실제 적용 세트 수를 구분해 보존 |

사용자 선택 경로는 `createSessionWithUserSetCountChoice` 같은 별도 공개 Workout API로 추가하고, 공통 세션 생성·검증 코드는 저장 구현 내부에서 재사용한다. Workout이 발전 DAO를 직접 읽지 않도록 조정/점검 권한 검사는 발전 서비스가 같은 Room 트랜잭션에서 담당한다. 복귀할 본세트 수는 채택 전 사본에서 가져오되 중량·반복 입력은 최신 유효 기록에서 준비한다.

두 경로 모두 미완료 입력 세트로 새 세션을 만들며 루틴·과거 기록을 수정하지 않는다. 알 수 없는 버전·출처/action 불일치·변조한 기준 세트 수·타 계정 점검·삭제된 근거는 거절한다. 4.2절의 중복 요청·취소/삭제 상태와 동일한 정책을 적용한다. PR-3에서 신규 계약의 round-trip, 자동 추천 v1의 기존 동작, 공유 모델의 Android/iOS 소비자 컴파일을 함께 검증한다.

## 6. PR 분할과 완료 조건

| 순서 | 구현 범위와 주요 변경 위치 | 완료 조건 |
| --- | --- | --- |
| 준비 | CI 전용 `122275d7 → 42de2392 → a5047615` 이식과 7개 실패 클래스 fixture 수정을 별도 커밋으로 준비 PR에 구성해 main에 선반영 | 새 main SHA에서 단위·빌드·lint·핵심 계측 전체 통과를 고정한 뒤 PR-1 착수. UI 변경 미포함, 새 skip/실패 제외 없음 |
| PR-1: 이력 저장 | feature/development/model·api·data에 조정/점검 모델과 Repository 추가. Entity·DAO·migration·백업 연결. 계정 귀속에서도 쓸 최소 세션/종목 ID별 상태 읽기 API, AccountOwnershipRoomDao의 두 claim 및 충돌 처리 추가 | 신규 DB/구버전 업그레이드·백업 호환 및 대상 계정/익명 양쪽 활성 조정 충돌에서 이력 보존·활성 1개·원자성 확인 |
| PR-2: 채택부터 실행 | DevelopmentCoachingService와 좁은 SetAdvisorInputV1 조회/fingerprint, 명시적 회복 오류 처리, AppContainer/Factory 조립, 기존 운동 시작·채택/보류 UI 연결 | 세션/이력 원자성, 중복 요청 상태별 반환, 56일 전체 보고서 없는 재검사, 회복 조회 오류 시 무쓰기. 1.2절 충돌 목록 유지 |
| PR-3: 실행·결과 점검 | PR-1의 ID별 상태 읽기에 후속 비교 근거 조회를 확장. DevelopmentCoachingReviewAnalyzer·실행 후 점검 조건과 `user-set-choice-v1` 생산자·Workout API/검증자·사본/reader를 함께 추가해 복귀 실행 연결 | 미완료·취소/삭제·비교 불가 구분, 유지/복귀/관찰 저장·복원, 새 사용자 계약의 실제 새 세션 생성과 v1 추천 회귀 통과 |
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
- 익명→계정 귀속은 아래 명시적 claim·충돌 규칙으로 처리한다. 계정 A의 늦은 비동기 결과를 계정 B에 적용하지 않는다.
- P0 이력은 로컬에 저장한다. 기존 외부 동기화 계약에 새 상세 이력을 추가하는 작업은 별도 범위다.

### 7.1 계정 귀속 충돌과 새 테이블 claim

AccountOwnershipService.claimLocalRows는 테이블별 DAO 메서드를 직접 호출한다. 새 테이블을 추가해도 자동 귀속되지 않으므로 PR-1에서 AccountOwnershipRoomDao에 `claimDevelopmentAdjustments`와 `claimDevelopmentAdjustmentReviews`를 각각 추가하고 기존 claimLocalRows 트랜잭션에 연결한다.

활성 조정 충돌의 우선순위는 **대상 계정에 이미 있는 유효한 활성 조정 우선**으로 고정한다. 먼저 참조 세션의 상태를 확인해 취소/삭제된 조정을 활성 후보에서 제외한다. 소유권 변경 전에 익명·대상 후보 ID를 확정하고, 충돌하는 익명 조정은 `SUPERSEDED_ON_CLAIM`으로 비활성화한 후 부모 조정→자식 점검 순서로 귀속한다. 대상에 활성 조정이 없으면 유효한 익명 조정 하나를 유지한다.

귀속 때문에 운동·조정·점검 이력을 삭제하거나 서로 다른 운동을 합치지 않는다. 기존 목표의 익명 행 삭제 정책을 조정 이력에 그대로 적용하지 않는다. goal 사본은 원래 값을 보존하고 대상 계정의 현재 목표와 다르면 재확인을 표시한다. 원래 requestKey도 보존하며, ID/requestKey가 충돌하는데 내용이 다르면 전체 귀속을 롤백하고 복구 가능한 충돌을 반환한다. 같은 요청의 완전히 동일한 사본만 명시적 중복 처리로 허용한다.

부모/자식 귀속·상태 전이·기존 Workout 귀속·활성 1개 검사를 같은 트랜잭션에서 처리한다. 중간 실패 시 이전 owner·행·상태가 모두 복구돼야 한다. 재귀속·프로세스 재시작·다른 로그인 계정으로의 단순 전환을 구분하며, 로그인 계정 A의 이력을 B로 옮기지 않는다. 복원에도 같은 활성 충돌 우선순위와 참조 검증을 적용한다.

## 8. 검증 계획

| 영역 | 반드시 확인할 사례 |
| --- | --- |
| 준비 기준선 | CI 3커밋만 선별 이식, 7개 실패 클래스 수정, 목록의 모든 클래스가 실제 실행된 XML·결과 존재, 실패 0·새 skip 없음, 기존 skip 사유 기록 |
| 저장·원자성 | 세션 저장 성공 후 이력 저장 실패, 그 반대 단계의 실패, 중복 클릭·재시도, 커밋 후 재조회 실패, 진행 중 운동 존재 |
| 중복 요청 상태 | 같은 키의 세션이 진행/완료/취소/soft-delete/hard-delete/종목 누락/타 owner인 경우, 조회 오류와 실제 소실 구분, 죽은 세션 재개·동일 키 자동 재생성 차단, 새 명시적 요청만 재시작 허용 |
| 제안 유효성 | 날짜 경계, 오래된 제안, 사용한 최대 3회 signature·회복 요약·포함 여부 변경, 더 최신 기록의 추가, 다른 목표·규칙 버전, 루틴 또는 종목 연결 변경 |
| 재검사 범위·오류 | 56일 보고서·영양·체중 호출 없이 좁은 입력 재검사, 대용량 기록에서 조회 범위/트랜잭션 시간, 회복 READ_ERROR 시 무쓰기·재시도, 미기록과 조회 실패의 구분 |
| 복귀 실행 계약 | user-set-choice-v1 생산→검증→사본/reader round-trip, 실제 세션 생성, local-sets-v1 유지, 잘못된 출처/action·알 수 없는 버전·변조한 기준 수·타 owner 점검·삭제된 근거 거절 |
| 실제 실행 | 채택 후 미완료, 취소·삭제, 다른 종목만 완료, 계획 세트와 실제 세트 차이, 운동 구성 직접 수정 |
| 재평가·시점 | 적용 종목 완료+다음 비교 가능 완료 1회에서 점검 가능, 날짜만 지난 주/운동하지 않은 주/후속 기록 부족, 7일 이전 충족, 변경된 세트·중량·부하 조건, 근거 수정·삭제, 56일보다 오래된 조정 |
| 계정·귀속 | 두 테이블 claim, 요청 중 계정 전환, 익명/대상 모두 활성인 충돌에서 대상 우선·익명 이력 보존, 대상 없음·참조 불가, goal 사본 보존, 중간 실패 롤백·재귀속·requestKey 충돌 |
| 데이터 유입·복구 | 기존 로컬 백업 복원, workout transfer import의 동일 ID 재수입·수정·중복 처리, 기존 sync 삭제/tombstone 반영 후 적용/근거 참조 재검증, 기록 변경·누락 시 APPLICATION_UNAVAILABLE 또는 근거 무효화, 프로세스 재시작 |
| DB migration | 신규 설치, 57→다음 버전, 지원 배포 버전의 전체 업그레이드 경로, Room schema 검증, 기존 데이터 보존 |
| UI | 채택·보류·유지·복귀·관찰, 목표 미설정, 비교 불가 설명, 큰 글자·작은 화면·명암 테마, 화면 재생성·뒤로가기, 로딩/오류 후 재시도 |
| 기존 기능 회귀 | 루틴 원본 보존, 기존 운동 시작·완료·삭제, 입력 단위, 휴식 타이머, 목표 수정, 관심 운동, 기존 영양·회복 안내 |
| 글래스 통합 | 1.2절의 5파일 충돌 해결 후 실제 서비스/알람 동시 주입, 완료·취소·삭제·계정 전환 시 타이머 정리, 로딩 정책과 시트 액션/닫기 보존 |

transfer/sync가 원래 지원하지 않는 삭제 기능을 이번 코칭 작업에서 새로 추가하지 않는다. 실제 유입 경로의 기존 의미를 보존하면서, 참조 ID의 수정·삭제/tombstone·소실 결과에 코칭 상태가 어떻게 반응하는지 검증한다. 특히 sync 응답에 행이 없다는 이유만으로 로컬 기록을 지우거나 조정을 무효화하지 않는다. 명시적 tombstone·로컬 참조 상태와 단순 원격 미반환을 구분한다. 원격 유입 테스트는 폐기 가능한 로컬 DB와 합성 payload로 실행하며 운영 서버 검증으로 설명하지 않는다.

기본 검증은 저장소가 요구하는 `testDebugUnitTest assembleDebug`이며 Linux에서는 `bash scripts/cloud/verify.sh`로 shared 테스트·Android 단위 테스트·앱/테스트 APK 빌드·lint를 실행한다. 준비에서 이식한 `bash scripts/cloud/instrumented-core.sh`는 **DB를 지우는 클래스가 있으므로 일회용 에뮬레이터 하나에서만** 실행한다. PR-1 이후 새 DB/코칭 계측 클래스를 목록에 추가해 핵심 suite 전체와 함께 실행한다. PR-1의 읽기 API와 PR-3의 사용자 선택 모델 등 shared API가 바뀌는 PR은 macOS KMP·Swift host gate도 확인한다.

서명 호환성이 확인된 테스트 기기에서는 기존 DB를 보존한 업데이트와 P0 종단 흐름을 확인한다. 테스트 APK 빌드는 instrumentation 실행과 구분하고, 기존 사용자 데이터를 삭제해 설치 문제를 우회하지 않는다.

**P0 인수 기준:** 같은 계정에서 제안 하나를 채택하고 적용 종목을 실제 완료한 뒤, 이후 같은 조건의 비교 가능한 기록 1회를 완료하면 점검을 제공한다. 앱 재실행 후에도 적용 기록·실제 수행·비교 가능성·컨디션·후속 선택이 유지되고, 사용자 복귀 선택은 user-set-choice-v1 검증을 통과해 다음 세션으로 실행된다. 후속 기록이 없으면 필요한 입력 한 가지와 대기 상태를 유지한다. 취소·삭제된 적용 세션을 재개하지 않고, 중복 생성·교차 계정 노출·귀속 후 활성 조정 중복·과거 기록 변경이 없어야 한다.

## 9. P1·P2의 설계 경계

P1은 현재 목표 5종을 보존한다. 근비대·근력·감량·유지에 대한 설명을 목표별로 구분하고, 지구력에는 검증되지 않은 근력 규칙을 그대로 적용하지 않는다. 근비대 판단은 장기 신체 변화와 수행을 함께 참고하며 주간 세트 증가나 체중 증가만으로 확정하지 않는다. 주간 훈련량은 같은 기간끼리 비교하고 운동의 지정 부위 기준임을 표시한다. 보조 근육 기여도를 임의의 정밀 점수로 만들지 않는다.

P0 PR-3에서 이미 local-sets-v1 자동 추천과 user-set-choice-v1 사용자 선택의 생산자·검증자·사본/reader를 구분해 완성한다. P1에서 목표별 자동 추천 버전을 추가로 바꾸면 그 버전의 생산자·Workout 검증자·저장 사본·과거 이력 reader를 다시 함께 검증한다. 복귀 실행에 필요한 버전 지원을 P1까지 미루지 않는다.

P2는 계산·기록 소유권을 앱에 유지한다. 기록 사실→가능한 해석→선택 가능한 행동→출처·한계 순서로 설명하고, 외부 모델에는 동의한 최소 요약만 보낸다. 네트워크 실패 시 로컬 보고서와 운동 기록은 계속 동작한다. 연구의 대상자·기간·평가 지표·발행일과 주장-인용 일치를 확인한다. 문서에 링크된 문헌의 주장을 이번 계획에서 새로 검증한 것으로 표현하지 않는다. 멀티 LLM은 단일 모델 평가에서 확인된 오류 감소가 입증될 때 검토한다.

## 10. 주요 소스 근거

제품 구조 근거는 보고서 브랜치 `44d232f` 기준이며 문서만 추가된 `3170a73`과 관련 코드가 같다. CI 전용 커밋과 계측 로그는 각 링크의 별도 SHA/실행 기준이다. 구현 착수 시 준비가 반영된 main과 다시 대조한다.

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
- [main의 KMP workflow](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/e7c7c35bc33c8471f470f986612cd13c343238b8/.github/workflows/kmp-release-readiness.yml): Android/shared 및 iOS job만 있으며 에뮬레이터 계측 job은 없다.
- [실제 계측 실패 job](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41088063762/job/124174369710): 7개 실패 클래스, v50 필수/optional 테이블·빈 DB·devices 누락·영양 fixture 실패 근거.
- [AccountOwnershipService.java](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/3170a73df1e3963e769541fa07ef5917fd007d7e/app/src/main/java/com/yeonsik/fitnessapp/core/account/AccountOwnershipService.java): claimLocalRows의 테이블별 DAO 호출과 익명 목표 삭제 정책.
- [DevelopmentReadApi.kt](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/3170a73df1e3963e769541fa07ef5917fd007d7e/app/src/main/kotlin/com/yeonsik/fitnessapp/feature/development/api/DevelopmentReadApi.kt): 목표·회복 요약의 좁은 읽기 경계.
- [기존 sync 로컬 보존 검사](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/3170a73df1e3963e769541fa07ef5917fd007d7e/app/src/androidTest/java/com/yeonsik/fitnessapp/data/SupabaseSyncManagerLocalPreservationTest.java): 원격 미반환을 로컬 삭제로 취급하지 않는 계약. 코칭의 tombstone 연결 검사는 신규 계획이다.

검증 상태: 원격 커밋·문서·지정 소스·CI 로그 대조 및 착수 전 계획 보완 완료. 이 개정은 계획서만 변경한다. CI 이식·fixture 수정·사용자 선택 계약·계정 귀속 정책은 아직 구현하지 않았으며 제품 단위 테스트·Android 빌드·instrumentation·실기기·운영 검증도 새로 실행하지 않았다.
