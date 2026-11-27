# Android·iOS 회귀 비교 기준

2026-11-27에 공통 UI 작업을 시작한 기준은 Android 후속 브랜치 `a457fa23`과 설치본 계열 `e7b992ac`를 결합한 `06974787`이다. 통합 커밋의 파일 트리는 `a457fa23`과 같다. 이 문서의 화면 목록은 양쪽 기기에서 앞으로 대조할 범위다.

## 설치 및 저장 기준

| 대상 | 관측값 |
| --- | --- |
| Galaxy A25에 설치된 APK 및 기존 작업 트리 QA APK | SHA-256 `64f71fa2d038e2eeb1c988c79568c2eb11cfcf1dd391f678454d60ebd0e34b22` |
| 이전 `e7b992ac` QA APK | SHA-256 `92699d4206fee4ebc40abe3ba22f1672834f28807f3689d82c02e0b98a3b2057` |
| 두 QA APK의 서명 인증서 | SHA-256 `2996c9cd4b7513384a90d4b1f80a8b3a3be28fc52cb0545176190ebf373c7288` |
| Android 저장 경로 | `com.yeonsik.fitnessapp` → Repository → Room 62, 파일명 `fitness_mvp.db` |
| iOS 저장 경로 | 앱 컨테이너의 `FitnessWorkout/workouts.json` 및 `FitnessBody/body-metrics.json` |

## 제품 진입점 목록

| Android 목적지 | 비교할 동작 | 대상 단계 |
| --- | --- | --- |
| 메인 `HOME` | 오늘 상태, 운동·식단·체중 바로가기, 활동 내역 | 1, 4 |
| 피트니스 `WORKOUT` | 근력·유산소 등 기능 선택, 진행 중 운동 표시 | 1, 2 |
| 무산소 `STRENGTH` | 루틴·종목 선택, 시작·재개 | 2, 3 |
| `ROUTINE_ADD`, `ROUTINE_DETAIL` | 루틴 생성·편집, 종목 순서와 시작 | 2, 3 |
| `WORKOUT_SESSION`, `WORKOUT_EXERCISE_DETAIL`, `WORKOUT_EXERCISE_ADD` | 일반·특수 세트 기록, 종목 추가, 기록 변경·취소, 휴식 타이머 | 2, 3 |
| `WORKOUT_SUMMARY` | 완료 후 운동·세트 상세와 복귀 | 2, 3 |
| `CARDIO`, `CARDIO_SESSION`, `CARDIO_SUMMARY` | 수동 입력, 위치 추적, 요약과 권한 | 4, 6 |
| `MEALS`, `SUPPLEMENTS` | 식사·영양 스냅샷, 보충제 기록 | 4 |
| 기록 `RECORDS` | 기록 달력과 날짜 선택, 기록 추가 → 체중, 기존 카드 수정·삭제 | 1, 4 |
| 기록 하위 `STATISTICS`, `DEVELOPMENT` | 통계·발전 전환, 탭 재진입 | 4 |
| 설정 `SETTINGS` | 단위·테마, 정보 검토·건강 자료, 로컬 백업·복원, FLEEK·전송 가져오기, 전송·CSV 내보내기, 세 연결 계정 | 1, 5 |
| 별도 Activity·시스템 | `InformationInboxActivity`, `HealthHandoffActivity`, `WearableImportActivity`, `DailyWorkoutPermissionsActivity`, 두 런처 별칭, 건강 권한 설명·사용 내역 별칭, 위치 포그라운드 서비스 | 5, 6 |

단계 0 시점의 iOS 진입점은 운동·기록·체중이다. 운동에는 루틴·일반 세트·진행 중 복구·완료 기록이 있고, 기록에는 하루 운동 기록과 동기화 계정이 있다. 체중 탭은 공통 편집창과 앱 컨테이너 저장소를 사용한다. 위 표에서 Android에만 있는 기능은 해당 단계에서 대조한다.

## 재현 시나리오

| 검증 | 합성 입력과 기대 결과 |
| --- | --- |
| Room 61 → 62 → 62 재개방 | 두 계정에 같은 날짜의 70.25 kg·81.5 kg, 원래 운동에 12.5 kg × 10회·RIR 2, 외부 심박 100을 준비한다. 마이그레이션 전후 기존 행을 대조하고 웨어러블을 연결한다. 재개방 후 한 계정에 72.5 kg을 추가하고 원래 운동과 연결·계정 격리를 다시 확인한다. |
| Android 화면 | 합성 전용 에뮬레이터에서 메인 → 체중 → 취소 → 피트니스 → 기록 → 설정을 조작한다. 각 탭의 실제 선택 상태를 확인한 뒤 캡처한다. 기준 환경은 1080 × 2424, 420 dpi, 글꼴 배율 1.0이다. |
| iOS 체중 | 달력·편집창을 순서대로 닫고 재진입한다. 숫자 키로 70.5 입력 → 저장 → 재실행 → 72.5 수정 → 삭제 → 72.75 추가를 수행한다. 저장 파일과 재설치 전후 해시를 비교한다. |
| 탭과 표시 설정 | 초기 탭 메인, 네 탭 순서 메인·피트니스·기록·설정. 선택한 탭은 루트로 복귀한다. 설치 기본값은 kg·어둡게다. 파운드로 225를 저장하면 같은 ID의 원본은 102.05828325 kg이다. kg 재선택·재실행 뒤 값을 대조하고 밝게·어둡게·기기 설정의 적용을 확인한다. |

빈 테스트 기기에서 `python scripts/android/capture-parity-tabs.py emulator-5584 build/parity-evidence/android-before --seed-weight`를 실행하면 오늘의 합성 체중 72.5 kg과 기준 화면이 만들어진다. 비교할 APK를 덮어 설치한 뒤 같은 에뮬레이터에서 `python scripts/android/capture-parity-tabs.py emulator-5584 build/parity-evidence/android-after`를 실행한다. Room 계정·운동·체중 대조는 `WearableWorkoutMigrationTest`, iOS 입력·재설치는 `scripts/ios/test-workout-simulator.sh`에서 실행한다.

## 단계 1 Android 실행 결과

`a5e3f7a7`에서 Android 단위 테스트 691건과 공통 테스트 54건이 통과했다. Android 조건부 테스트 2건은 건너뛰었다. 에뮬레이터에서는 Room 마이그레이션, 하단바 드래그, 체중·설정·계정 입력을 다룬 6건이 통과했다. 같은 합성 데이터의 메인·체중 편집·피트니스·기록·설정 다섯 화면은 1080 × 2424 원본의 전체 너비, 세로 좌표 90 이상 2354 미만에서 RGB가 모두 같았다. [Android 변경 전후 캡처](evidence/android-tabs-stage1.webp)

Galaxy A25에서는 `8b3b6cb3`을 `com.yeonsik.fitnessapp.paritytest`로 따로 설치했다. 앱을 직접 열어 메인 → 피트니스 → 기록 → 설정 → 메인 순서와 체중 편집 취소 후 메인 복귀를 확인했다. 하단바 드래그와 체중·설정 계측 3건이 통과했다. 다음 비교를 위해 일반 세트의 키보드 이동·반복 저장·첫 세트 삭제 보호 3건도 실행했다. 사용 중인 `com.yeonsik.fitnessapp`의 설치 ID, 최초 설치·마지막 업데이트 시각과 데이터 경로는 전후 대조에서 같았다.

## 일반 세트의 기존 Android 동작

같은 A25 격리 설치본에서 파운드를 선택하고 `ParityBench` 루틴에 바벨 플랫 벤치 프레스 한 종목을 담았다. 아래 값은 앱 화면을 조작한 뒤 테스트 앱만 강제 종료하고 해당 Room 파일을 다시 읽은 결과다.

| 시점 | 첫 세트의 원래 입력 | DB 중량과 상태 |
| --- | --- | --- |
| 완료 체크 후 첫 재실행 | 95 lb × 8회, RIR 3 | 43.09127515 kg, 운동 진행 중 |
| 재개 → 첫 세트 수정 → 두 번째 50 lb × 6회·RIR 1 저장·삭제 | 100 lb × 10회, RIR 2 | 45.359237 kg, 첫 세트만 보이며 운동 진행 중 |
| 두 번째 재실행 → 운동 완료 → 기록 상세 재조회 | 100 lb × 10회, RIR 2 | 45.359237 kg, 완료 세트 1개, 총 453.59237 kg |

세 시점의 운동·종목·첫 세트 ID가 모두 같았다. 운동 완료 직후에는 운동 요약이 열렸다. 앱을 다시 열어 기록 탭의 `상세 보기`를 누르면 같은 세트의 원래 입력 단위와 횟수가 보였다. 이 경로를 공통 세트 편집기의 전후 비교에 사용한다.
