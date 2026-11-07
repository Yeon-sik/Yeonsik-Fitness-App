# PR0 Probe Result — Galaxy Fit3 → Samsung Health → Health Connect

- **상태:** NOT_RUN — 실기기 실측 전
- **판정:** 미기록. 실측 후 `PASS` · `PARTIAL` · `BLOCKED — 대체 수집 경로 결정 필요` · `INCONCLUSIVE` 중 하나만 적는다.
- **실기기 증거:** 미수집. 판정은 실기기 실측 근거가 있을 때만 적는다.
- **기준 문서:** `docs/WEARABLE_COMMON_INTERFACE_PLAN.md` v0.3.1 §9 (PR0 판정, G-HR), §10.1 (K-09·K-12·K-13)
- **프로브 코드:** `app/src/debug/` (debug 빌드 전용, K-01)
- **실행 커밋:** (실측에 쓴 커밋 SHA)
- **실행 기간:** (yyyy-MM-dd ~ yyyy-MM-dd)

## 0. 기록 규칙 (K-02)

이 파일은 저장소에 커밋된다. 프로브 보고서는 이 규칙을 지키도록 만들어져 있으며, 손으로 적는 내용도 같은 규칙을 따른다.

| 적어도 되는 것 | 적지 않는 것 |
| --- | --- |
| 세션 기준 상대 시각(`+00:12:30`), 길이, 간격·공백 분포, coverage, 개수 | 운동 날짜·시각 같은 절대 시각, 원시 심박 값(bpm), sample 원본 목록 |
| 프로브가 만든 12자리 masked id | Health Connect record id 원문, `clientRecordId` 원문, changes token |
| 앱 패키지 이름, 기기 제조사·모델명, 앱·OS 버전 | 계정 이메일·사용자 id, 위치, 스크린샷 원본 |
| 걸음 수 구간 합계(aggregate·origin별 합계) | — |

## 1. 실행 전 확인 — 기기 데이터 보호 (K-13)

기본 debug·qa·release는 `com.yeonsik.fitnessapp`를 쓴다. A25의 기존 앱과 기본 debug APK의 서명이 달라 K-13 선택지 (c)로 별도 프로브를 설치했다.

```powershell
.\gradlew.bat -PfitnessWearableProbeIsolated=true :app:assembleDebug
```

`aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk`에서 `com.yeonsik.fitnessapp.wearableprobe`를 확인한 뒤, 해당 APK를 A25에 `adb install -r`로 설치한다.

- [x] Galaxy A25의 기존 `com.yeonsik.fitnessapp` 설치와 서명 불일치를 확인했다.
- [x] 별도 UID의 `com.yeonsik.fitnessapp.wearableprobe`를 설치하고 `HC Probe (debug)`를 실행했다.
- [x] 설치·실행 전후로 원본 앱의 패키지 경로와 설치 정보가 일치했다. DB 관련 파일 3개(`-shm` 제외)와 설정 파일 8개의 해시도 각각 일치했다.

## 2. 검증 단계별 기록

각 단계는 실제로 실행한 경우에만 PASS/FAIL을 적는다. 실행하지 않았으면 NOT_RUN과 이유를 적는다.

2026-11-06 점검 기준은 원격 커밋 `2d6d314da317b1045367a2183113ad1b6cb61f73`이다. Android CI는 Linux와 JDK 17에서, 로컬 검증은 Windows와 JDK 21에서 실행했다. [Android CI 실행 결과](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/41992165430)

```powershell
.\gradlew.bat --console=plain --max-workers=2 --continue testDebugUnitTest assembleDebug :app:lintDebug :app:processQaMainManifest :app:processReleaseMainManifest
```

| 단계 | 명령·방법 | 결과 |
| --- | --- | --- |
| 순수 분석 로직·소스 경계 테스트 | 독립 JVM 프로젝트(Kotlin 2.3.0, 경고를 오류로 처리)에서 `app/src/debug/.../analysis`와 `testDebug` 테스트 25개, `WearableProbeBuildBoundaryTest` 5개 컴파일·실행 | PASS, 30/30 (2026-11-05, 작성 세션. Gradle Android 빌드가 아니며 Android·Health Connect 의존 코드는 포함하지 않음) |
| Android 단위 테스트 | `:app:testDebugUnitTest` | PASS (CI·로컬). 로컬 XML 보고서 기준 660건 통과, 2건 건너뜀. 프로브 분석·경계 테스트 30건 모두 통과 |
| Android 빌드 | `:app:assembleDebug` | PASS (CI·로컬). 로컬 디버그 APK 생성 확인 |
| lint | `:app:lintDebug` | PASS (CI·로컬) |
| 빌드 타입별 권한·프로브 경계 | `:app:processDebugMainManifest`, `:app:processQaMainManifest`, `:app:processReleaseMainManifest` 병합 결과 비교 | PASS (로컬). `debug`에는 심박·운동·걸음 읽기 권한과 프로브 구성요소가 있다. `qa`와 `release`에는 health 권한과 프로브 구성요소가 각각 0개다. |
| 실기기 설치 | §1 격리 빌드·설치 | PASS (2026-11-06, Galaxy A25 / Android 16, `ba5a09874c018fdb6950e6660309010fc05c87f8`). 격리 APK 빌드·단위 테스트·lint를 로컬에서 다시 실행했다. 일반 debug·qa·release와 격리 debug의 병합 매니페스트도 비교했다. |
| 실기기 관측 | §3 절차 | NOT_RUN (Fit3에서 기록한 운동 중 심박의 Health Connect 유입 미확인) |

## 3. 실행 절차

1. **기기 준비:** A25에 Samsung Health와 Galaxy Wearable을 설치하고 Galaxy Wearable에서 Fit3를 페어링한다. Samsung Health의 `설정 → Health Connect`에서 삼성 헬스가 심박·운동·걸음 데이터를 쓰도록 허용한다. 실제 메뉴 경로는 §4에 적는다.
2. **프로브 실행:** launcher의 `HC Probe (debug)`를 연다. "권한 요청"으로 심박·운동·걸음 읽기를 허용한다.
3. **changes token:** 첫 운동 전에 "token 발급"을 누른다.
4. **운동 세션:** Fit3에서 세션 3회 이상을 기록한다.
   - 각 20분 이상, 1회 이상 실외 걷기·달리기, 1회 이상 일시정지·재개 포함.
   - 세션마다 시작·일시정지·재개·종료 wall-clock 시각을 따로 적어 둔다. 이 메모는 커밋하지 않는다.
5. **조회:** Samsung Health 동기화를 확인한 뒤, 프로브에 날짜·시작·종료·일시정지를 입력하고 "세션 조회"를 누른다.
   - "최근 7일 운동"으로 ExerciseSession을 누르면 날짜·시작·종료가 채워진다. 일시정지는 직접 입력한다.
   - 운동 종료 직후와 조회 시점의 차이가 "조회 시점: 세션 종료 후" 줄에 나온다.
   - 전체 심박 coverage에는 여러 앱이 쓴 기록이 합쳐진다. G-HR에는 Samsung Health(`com.sec.android.app.shealth`) origin 행의 심박 지표를 사용하고, 해당 운동 구간과 기기 메타데이터를 Fit3 기록과 대조한다. 기기 출처가 불명확하면 §5에 근거와 함께 기록한다.
6. **24시간 뒤 재조회:** 같은 구간을 다시 조회하면 "재조회 비교" 줄에 유지·새로 보임·사라짐이 나온다. 이어서 "changes 조회"를 누른다.
7. **상태 시나리오:** §6의 시나리오를 차례로 만든다. 권한 회수는 "HC 열기"에서 이 앱의 권한을 끈 뒤 조회한다.
8. **기록:** "보고서 복사"로 익명화 보고서를 복사해 §5에 붙여 넣고, 요약 표를 채운다.

## 4. 환경

프로브 보고서의 `## 환경` 부분을 붙여 넣고, 아래를 채운다.

- One UI 버전:
- Samsung Health → Health Connect 연동 메뉴 경로:
- Fit3 펌웨어 버전:
- Fit3 심박 측정 설정(연속 측정 여부 등):

## 5. 세션별 관측 (G-HR)

| 세션 | 길이 / 활동 | 실외 | 일시정지 | HR 읽기 결과 | 활동 구간 sample | 간격 중앙값 / p95 / 최대 | coverage | 최대 공백 | 잠정 K-12 | 동기화 지연 | origin | metadata.id / clientRecordId |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| S1 | | | | | | | | | | | | |
| S2 | | | | | | | | | | | | |
| S3 | | | | | | | | | | | | |

### 붙여 넣은 보고서

(세션별 보고서를 여기에 붙여 넣는다.)

## 6. 상태 구분 시나리오

| 시나리오 | 기대 결과 | 관측 결과 | 다른 시나리오와 구분됨 |
| --- | --- | --- | --- |
| 권한 거절 | `PERMISSION_DENIED` | | |
| 권한 회수 후 재조회 | `PERMISSION_DENIED` | | |
| 권한 허용, 운동 없는 구간 | `EMPTY_SUCCESS` | | |
| 미지원 타입 | `UNSUPPORTED` | | 재현할 수 없으면 "재현 불가"와 이유 |
| Health Connect 미설치·업데이트 필요 | `HC_UNAVAILABLE` / `HC_UPDATE_REQUIRED` | | 재현할 수 있을 때만 |

## 7. 재조회·변경 피드 (K-09, Unknown #3·#9)

| 항목 | 값 |
| --- | --- |
| 24시간 뒤 재조회: 유지 / 새로 보임 / 사라짐 | |
| 세션 구간 sample 수 변화 | |
| changes token 발급 후 경과 | |
| UpsertionChange / DeletionChange 수 | |
| DeletionChange 중 이전 조회 masked id와 일치 | |
| token 만료 응답 | |

## 8. 보조 관측 (G-HR을 대신하지 않음)

- ExerciseSessionRecord: 존재 여부, 수동 기록 구간과의 시작·종료 차이, segment 수
- Steps: aggregate 합계와 origin별 raw 합계의 비율, 여러 origin 중복 여부

## 9. 판정

- **G-HR 판정:** (PASS / PARTIAL / BLOCKED — 대체 수집 경로 결정 필요 / INCONCLUSIVE)
- **근거:** (§5·§6의 어떤 값이 판정표의 어떤 조건을 충족하거나 못 했는지)
- **K-12 잠정 기준:** (승인 / 조정 제안과 실측 근거)
- **K-09 `metadata.id` 안정성 관찰:**
- **다음 조치:** (설계 문서 §9 판정표의 해당 행)

걸음 수나 ExerciseSession만 확보되고 운동 구간 HR이 없으면 G-HR은 BLOCKED다. 프로브 결함이나 프로토콜 미충족은 INCONCLUSIVE이며, 고친 뒤 다시 측정한다.

## 10. 계산 정의

프로브(`ProbeStatistics`)가 쓰는 정의이며, 판정은 이 정의로 읽는다.

- **활동 구간:** 세션 구간에서 기록한 일시정지를 뺀 구간.
- **sample 간격:** 같은 활동 구간 안에서 시각이 다른 연속 sample 사이의 간격. 일시정지를 건너는 간격은 넣지 않는다.
- **백분위:** nearest-rank 방식.
- **coverage:** 활동 구간을 시작부터 60초 bin으로 나누고, 마지막 조각은 30초 이상일 때만 bin으로 센다. sample이 하나 이상 있는 bin의 비율이다.
- **최대 공백:** 활동 구간 시작부터 첫 sample, sample 사이, 마지막 sample부터 활동 구간 끝까지 가운데 가장 긴 값.
- **잠정 K-12 기준:** coverage 80% 이상이면서 최대 공백 5분 이하(경계값 포함). 판정 보조값일 뿐 PR0 판정이 아니다.
- **동기화 지연:** record의 `lastModifiedTime` − 수동 기록한 세션 종료 시각.
- **masked id:** 기기 안 무작위 salt와 SHA-256으로 만든 앞 12자리. 같은 설치 안에서만 비교할 수 있고, 원문으로 되돌릴 수 없다.
