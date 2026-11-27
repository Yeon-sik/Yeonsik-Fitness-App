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

Android 캡처 명령은 `python scripts/android/capture-parity-tabs.py emulator-5584 build/parity-evidence/android-before`다. 비교할 APK 설치 후 같은 에뮬레이터와 설정에서 출력 경로를 `android-after`로 바꿔 실행한다. Room 계정·운동·체중 대조는 `WearableWorkoutMigrationTest`, iOS 입력·재설치는 `scripts/ios/test-workout-simulator.sh`에서 실행한다.
