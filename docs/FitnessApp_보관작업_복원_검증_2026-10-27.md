# 별도 보관 작업 복원과 검증

작성일: 2026-10-27. 제품 코드 기준: `86281f9` / `feat/fitness-glass-completion`.

원격 글래스 UI와 PriceTrace 통합을 마친 `8eb88e7` 위에, 원격 작업 전에 분리 보관한 구현을 복원했다. **보관본의 제품 소스·테스트 54개 경로를 모두 확인했고, 이미 반영한 PT 부분과 나머지 화면·입력 개선을 통합했다.** 코드 구현과 로컬 검증을 완료한 뒤, 사용자 요청으로 `886987c`의 QA APK를 실기기에 교체 설치하고 실행·데이터 보존까지 확인했다. 계측 실행·운영 동기화 검증은 수행하지 않았다.

## 1. 복원 기준과 보존 범위

- 원래 작업 브랜치: `feat/records-ux-clarity`, 기준 커밋 `8c06a28`.
- 분리 보관본: `fitness-local-20261026-remote-resume`의 패치, 파일 사본, SHA-256 manifest.
- 보관본 60개 일반 파일은 제품 소스·테스트 54개, 로컬 Serena 설정 4개, 과거 조사·검증 문서 2개로 구성된다.
- 제품 소스·테스트 중 PT 전용 11개 경로는 앞선 `9388c44`에 반영되어 있었다. 나머지 43개 경로를 이번에 복원·병합했다. 이 43개에는 PT와 함께 수정했던 혼합 파일의 나머지 UI 변경도 포함된다.
- 원래 체크아웃에만 있던 로컬 커밋 4개도 확인하고 `cherry-pick -x`로 원 커밋 출처를 남겼다.
- 원본과 보관 사본 각각 60개 파일의 SHA-256을 다시 대조해 불일치 0개를 확인했다. 원래 브랜치와 미커밋 작업은 유지했다.

과거 조사·검증 문서는 기존 미추적 보관 방침을 유지한다. 그 문서에 기재된 이전 기기 시험은 이번 검증 횟수에 합산하지 않는다. 조사 문서의 제안은 과거 판단 근거로 보관하며, 이번 구현 범위는 보관된 코드다. 로컬 도구 설정과 독립 이미지 파이프라인 저장소도 제품 변경으로 가져오지 않았다. 별도로 존재하는 2026-09-29·2026-10-17의 과거 stash 2개는 이번 분리 보관본과 구분해 그대로 유지했다.

## 2. 반영한 구현

| 범위 | 현재 동작 | 커밋 |
|---|---|---|
| 설정 | 계정·동기화 상태와 실행 버튼을 모으고 단위·테마 선택을 명확히 표시. 백업·가져오기·내보내기는 펼쳐서 접근. 실제 동기화 시각을 현지 시각으로 표시 | `af9afb3` ← `29a05c3` |
| 완료 요약 발행 | 프로젝트·소유자별 fingerprint로 변경 없는 요약의 재전송을 줄임. 성공 응답만 저장하고 부분 실패 전의 성공도 보존. 조회를 묶고 최신 로컬 수정이 있으면 덮어쓰지 않음 | `7732dd2` ← `391f6ad` |
| 영양 카탈로그 동기화 | 한 번의 실행 안에서 원격 스냅샷과 쓰기 응답을 재사용. 시도한 행을 재조회해 조건부 쓰기 실패와 연결 교체 결과를 반영 | `e077d21` ← `3f6ebca` |
| 수동 동기화 | 큐에 넣기 전에 실행권을 확보해 연속 호출을 차단. 시작 당시 계정·프로젝트를 확인하고 진행 문구를 표시 | `80bfc12` ← `8c06a28` |
| 홈 | 오류 재시도, 활동 기록 일수와 날짜 목록, 날짜별 상세 닫기·기록 이동, 큰 글꼴에서 완료 기준 줄바꿈 | `59cb0b8` |
| 기록·발전 | 선택일 요약과 상세 시트, 조회와 삭제 관리 동작 분리, 오류 재시도. 발전 제안의 현재·이전 값과 비교 불가 조건을 구분하고 상세·설정 접근 개선 | `59cb0b8` |
| 피트니스·입력 | 운동·생활 기록 진입과 진행 중 운동 이어하기, 루틴 조회 재시도·생성 폼, 식단 검색 우선 배치와 작은 높이에서 스크롤하는 저장 영역, 유산소 선택·환경·시작 상태 정리 | `090dd81` |
| 화면 복구 | 운동 ID가 없는 피트니스·식단 화면의 재생성 시 빈 운동 ID를 복원하지 않음 | `090dd81` |
| 통계 | 날짜 간격을 유지하고 결측 구간을 연결하지 않는 차트, 일부 주 표시, 날짜별 수치 목록, 이전 기간 비교의 일/회/kg/lb 단위 구분, 집계 문구 보간 오류 수정 | `6fd5253` |
| CI 호환성 | 이미지 export별 slug 차이를 수용하면서 실제 A/B drawable 및 canonical ID 매핑을 계속 검증 | `86281f9` |

입력의 저장 경로는 기존 화면 액션 → ViewModel → repository → Room/SQLite를 유지한다. 화면 복원은 저장 규칙이나 DB schema를 바꾸지 않는다. 동기화 복원은 기존 클라이언트의 중복 실행·조회·전송을 줄이는 변경이며, 외부 API/RPC 계약이나 운영 서버를 변경하지 않았다.

## 3. 현재 원격 구현과의 병합 기준

8개 파일의 충돌을 해결하고 자동 병합된 혼합 파일도 확인했다.

- 홈의 이전 직접 blur 구현 대신 현재 `FitnessGlass` 경계와 Hero 색상 토큰을 유지했다. 완료 표시와 큰 글꼴 대응만 합쳤다.
- 기록·발전 상세 시트와 홈 날짜 선택 창은 현재 `FitnessModalBottomSheet` / `FitnessAlertDialog`를 사용한다.
- 식단 입력의 적응형 footer는 `FitnessDialogSurface` 안에 통합했다. 키보드와 큰 글꼴로 높이가 부족하면 저장 영역까지 본문과 함께 스크롤한다.
- 홈·기록의 로딩 지연을 보존하고 새로 복원한 피트니스 요약·루틴·통계도 공통 로딩 컴포넌트에 맞췄다. 테스트 fixture는 지연 0 설정과 대표 접근성 문구를 사용한다.
- 현재 탐색 아이콘, 운동 중 상단바·휴식 오버레이, 백그라운드 알림 예약을 유지했다.
- 자동 병합으로 중복된 PT 등록 콜백·회귀 시험을 제거했다. 제품 등록 후 같은 식단으로 복귀하는 연결과 브랜드 카탈로그 통합을 유지했다.

## 4. 복원 당시 소스·JVM 검증 결과

환경: Windows / Android Studio JBR 21.0.10 / Gradle 9.0.0. Java·Kotlin 컴파일 대상은 17. 작업자 1개, Kotlin in-process, Gradle JVM 최대 1536MB로 실행했다.

| 검증 | 결과 | 경계 |
|---|---|---|
| 앱 JVM 단위 시험 | **620건: 618 통과, 기존 조건부 2건 skip, 실패·오류 0** | `:app:testDebugUnitTest` |
| 공유 모듈 JVM 시험 | **33/33 통과** | `:shared:testAndroid` / `testAndroidHostTest` |
| 앱 Java·Kotlin 컴파일 | 통과 | APK 생성 없음 |
| Android 계측 시험 소스 컴파일 | Java·Kotlin 모두 통과 | 계측 실행 없음 |
| 외부 이미지 파이프라인 환경 | 이미지 카탈로그 시험 5/5 통과 | 기존 외부 export를 사용한 생성물 |
| 기본 이미지 묶음 환경 | 위 전체 시험·컴파일 통과 | 외부 이미지 경로 없이 310개 카탈로그·620개 PNG를 빌드 디렉터리에 생성 |
| 원본·백업 무결성 | 각각 60개 SHA-256 일치 | 원래 작업 체크아웃과 보관본 모두 보존 |
| 코드 공백·충돌 검사 | `git diff --check` 통과, 충돌 마커 없음 | 검토한 경로만 커밋 |

최종 기본 이미지 묶음 검증 명령:

```powershell
# FITNESS_IMAGE_PIPELINE_DIR를 지정하지 않은 기본 묶음 환경
.\gradlew.bat :shared:testAndroid :app:testDebugUnitTest `
  :app:compileDebugJavaWithJavac `
  :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac `
  --offline --no-daemon --max-workers=1 `
  '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8' `
  '-Pkotlin.compiler.execution.strategy=in-process'
```

로컬 증거 파일은 `preserved-final-build.log`, `preserved-image-pipeline-test.log`, `preserved-ci-fallback-build.log`, `preserved-test-results.json`에 보관한다. 마지막 전체 검증은 이미지 생성 단계를 생략하지 않았다. 생성 자산을 직접 수정하지 않았다.

앞선 원격 `8eb88e7`의 [Android CI 실패](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/40349161974/job/121664158094)는 기본 이미지 묶음에 없는 테스트 상수 4개 때문이었다. 실제 이미지의 canonical ID는 같지만 외부 export와 기본 묶음의 slug가 달랐다. `86281f9`에서 지원하는 두 이름을 확인한 뒤 정확한 A/B 리소스 배열과 별칭을 비교하도록 고쳤다. 시험 생략이나 임의의 빈 배열 허용은 추가하지 않았다. 이후 `886987c`의 [Android·공유 모듈·iOS CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/40355197162)와 [문서 CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/40355197153)가 모두 통과했다.

프로젝트 문서 검증은 저장소 validator와 Notion 렌더링 dry run으로 수행한다. 설정된 소개·상세 문서의 검사이며 실제 화면 품질이나 운영 결과를 인증하는 검사가 아니다. Notion 쓰기는 수행하지 않는다.

## 5. 남아 있는 검증 경계

4절의 복원 검증 당시에는 사용자 지시에 따라 추가 APK 생성·기기 실행을 제외했다. 이후 2026-10-27의 `실기기 -r` 요청으로 6절의 QA 빌드·교체 설치·시작·데이터 보존 검증을 수행했다. 큰 글꼴·IME·회전·TalkBack·창 블러의 실제 표시, 휴식 알람·Doze, 계측 시험 실행, 두 계정의 운영 동기화와 RLS는 미검증으로 남는다.

## 6. 실기기 교체 설치와 데이터 보존 — 2026-10-27

설치 소스 기준은 `886987c05899a7b752f998129154fe181822ff23`이다. Galaxy A25(`SM-A256N`, Android 16)의 기존 `com.yeonsik.fitnessapp`과 같은 QA 인증서를 확인한 뒤 `assembleQa`를 실행했다. 빌드는 1분 44초에 성공했으며 APK 버전은 `1.1.0` / code `2`다. 해당 작업은 기기 설치와 증거 기록이며 제품 소스 변경은 없다.

```powershell
adb -s <확인한_실기기_ID> install -r --user 0 app-qa.apk
```

| 검증 | 결과 |
|---|---|
| APK 검증 | 기존 설치와 인증서 SHA-256·패키지 일치 확인. 새 APK SHA-256은 `27dea0d4bb99839cbecd0168bc71a4f3b166a3a2a46662d49e49e6775d20dd9d` |
| 설치 | `Success`. 기본 사용자 0에 교체 설치 |
| 설치 전후 | 앱을 정지하고 DB·설정 12개 파일을 백업·대조. 12개 파일 SHA-256 전부 동일 |
| 첫 실행 후 | 43개 테이블의 전체 행 내용을 대조해 기존 행 변경·삭제·추가 0개. schema v57 유지, SQLite `integrity_check=ok` |
| 실행 후 파일 차이 | SQLite 공유 메모리 `fitness_mvp.db-shm`과 Android 내부 `android.app.ActivityThread.IDS.xml`만 변경. 기존 사용자 설정·계정 설정 파일은 동일 |
| 시작 확인 | `MainActivity` 시작 `Status: ok`, 현재 프로세스의 시작 충돌 표시 0개. 앱을 다시 실행한 상태로 마침 |

증거는 비공개 로컬 설치 폴더의 `assemble-qa.log`, `installed-signature.txt`, `new-signature.txt`, `install-result.txt`, `install-retention.json`, `launch-retention.json`, `first-launch-runtime.log`, `update-result.json`에 보관한다. 원본 APK·DB·설정 백업과 계정 정보는 Git에 포함하지 않는다. 이 결과는 교체 설치·기본 시작·데이터 보존의 근거이며 개별 기능의 실기기 수용 시험을 대체하지 않는다.
