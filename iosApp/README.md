# Yeonsik Fitness iOS

## GitHub Actions에서 iPhone용 IPA 받기

`KMP release readiness` 워크플로가 브랜치 푸시와 수동 실행에서 `macos-15`로 `iphoneos` Release 빌드를 수행한다. `arm64`와 Mach-O의 iOS 플랫폼을 검사하고, `Payload/YeonsikIosProof.app`을 `Yeonsik-Fitness-unsigned.ipa`로 압축한다. 서명이나 프로비저닝 프로파일은 Windows 설치 단계에서 생성한다.

1. GitHub에 로그인하고 [KMP release readiness](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/workflows/kmp-release-readiness.yml)에서 원하는 브랜치의 푸시 또는 수동 실행을 연다.
2. **Artifacts**의 `fitness-ios-unsigned-ipa` ZIP을 내려받고 압축을 풀어 `.ipa`를 꺼낸다. 보관 기간은 14일이다. 같은 IPA를 재서명에 쓸 수 있으므로 Windows에 보관한다.
3. 무결성을 확인하려면 다운로드 폴더에서 `Get-FileHash .\Yeonsik-Fitness-unsigned.ipa -Algorithm SHA256`을 실행해 동봉된 `.ipa.sha256`의 해시와 비교한다.

공개 저장소의 표준 GitHub 호스팅 러너, Sideloadly, 무료 Apple 계정을 사용하는 경로다. CI에 Apple 계정, 기기 UDID, 인증서, 개인키를 등록할 필요가 없다. CI 산출물은 ARM64 실기기 빌드 증거다. 설치와 첫 실행은 iPhone에서 확인한다. [GitHub Actions 과금](https://docs.github.com/en/billing/concepts/product-billing/github-actions) · [Artifacts 다운로드](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)

## Windows에서 설치와 재서명

1. Windows에 [Sideloadly 공식 다운로드](https://sideloadly.io/)와 공식 페이지에서 안내하는 웹 배포판 iTunes·iCloud를 설치한다.
2. iOS 16 이상 iPhone을 잠금 해제하고 데이터 전송이 되는 USB 케이블로 연결한다. iPhone에서 **이 컴퓨터를 신뢰**하고 Sideloadly에 기기가 표시되는지 확인한다.
3. Sideloadly에 `.ipa`를 끌어 놓는다. `Apple ID Sideload` 모드로 무료 Apple 계정을 입력하고 `Start`를 누른다. 암호와 2단계 인증 코드는 Windows의 Sideloadly에서만 처리한다. 최초 설치 때 사용한 계정과 `Change Bundle ID` 설정을 이후에도 유지한다. IPA의 원본 번들 ID는 `com.yeonsik.fitness.iosproof`다.
4. 필요한 경우 iPhone의 **설정 → 개인정보 보호 및 보안 → 개발자 모드**를 켜고 재시동한 다음 확인한다. 개발자를 신뢰하라는 메시지가 나오면 **설정 → 일반 → VPN 및 기기 관리**에서 해당 개발자 계정을 신뢰한다. 홈 화면에서 **Yeonsik Fitness**를 실행한다.
5. 무료 프로비저닝은 발급일로부터 7일간 유효하다. 만료 전에 같은 Apple 계정과 동일한 최종 번들 ID로 앱 위에 다시 설치하거나 Sideloadly 자동 갱신을 사용한다. 갱신 시 iPhone에서 앱을 삭제하지 않는다. 재서명에는 이미 받은 IPA를 다시 사용할 수 있다.

[Sideloadly 설치 및 문제 해결](https://sideloadly.io/faq) · [Apple 개발자 모드](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device) · [무료 Apple 계정 제한](https://developer.apple.com/help/account/basics/about-your-developer-account/)

## 첫 운동과 데이터 보존 확인

1. **피트니스 → 무산소 운동 → 루틴 만들기**에서 이름과 종목을 등록한다. 루틴을 열고 **운동 시작**을 누른다.
2. **세트 추가**에서 중량, kg 또는 lb, 횟수, 선택 사항인 RIR을 입력하고 저장한다. 진행 중 세트를 탭하면 수정할 수 있고, 왼쪽으로 밀면 삭제할 수 있다.
3. 앱 전환기에서 앱을 종료하고 다시 열어 **진행 중**에서 세트를 확인한다. **운동 완료**를 누른 뒤 **기록**에서 운동 이름, 종목, 세트 값이 보이는지 확인한다.
4. 같은 Apple 계정과 최종 번들 ID로 Sideloadly에서 덮어 설치한다. 다시 실행해 **기록**에서 위 값을 비교하고, 새 루틴과 세트를 저장해 본다. 무료 프로비저닝 만료 전 갱신과 만료 후 갱신은 실행한 경우에만 각각 확인으로 기록한다.

데이터는 앱 컨테이너에 남는다. iPhone에서 앱을 삭제하면 운동 데이터도 삭제되며, 번들 ID를 변경하면 새 앱의 빈 저장소가 열린다. 앱 내 백업·복원은 다음 단계다. 설치 오류가 나면 기존 앱을 유지한 상태에서 계정과 번들 ID 설정을 확인한다. [Apple 앱 업그레이드 검증](https://developer.apple.com/library/archive/technotes/tn2319/) · [Sideloadly 자동 갱신](https://sideloadly.io/faq)

## iOS 구현 범위

- SwiftUI에서 루틴 생성·편집, 루틴을 바탕으로 운동 시작, 진행 중 세트 추가·수정·삭제, 완료 이력 상세 조회를 지원한다. 세트에는 원래 입력값·단위, kg 환산값, 횟수, 선택 RIR 0~5를 저장한다. 시작 시 루틴 이름과 종목을 복사하므로 이후 루틴 편집이 완료 기록에 반영되지 않는다.
- 기존 `iosArm64`와 `iosSimulatorArm64`가 빌드하는 정적 `YeonsikShared` 프레임워크의 `MassUnit`과 `WorkoutPerformanceCalculator`로 중량 환산과 Epley 추정 1RM을 계산한다. Xcode의 기존 `embedAndSignAppleFrameworkForXcode` 단계가 프레임워크를 연결한다.
- `WorkoutStore`는 `Library/Application Support/FitnessWorkout/workouts.json`에 루틴·진행 중 운동·완료 기록을 저장한다. 형식 v2는 운동을 시작할 때마다 종목 기록 ID를 새로 만들고 원래 루틴 종목 ID를 보관한다. v1 파일은 세션과 루틴 종목 ID로 같은 변환 결과를 만들어 원자적으로 저장한다. 세트 ID와 원래 입력 단위를 보존한다. 읽을 수 없는 파일이나 미래 버전은 오류로 표시한다.
- 하단 탭은 **메인·피트니스·기록·설정** 순서다. 체중 편집창·날짜 선택·기록 카드와 표시 설정은 공통 Compose 화면을 사용한다. **설정 → 표시**에서 킬로그램·파운드와 어둡게·밝게·기기 설정을 선택한다. 저장 단위는 kg이고 목록은 최근 20건이다.
- Android 운동 카탈로그와 운동 전송 파일 가져오기, 특수 세트 및 휴식 타이머, 식사·영양·보충제, GPS·지도는 후속 이식 범위다.

`기록 → 하루 운동 기록`에서 날짜를 선택하고 FLEEK CSV와 Apple 건강의 운동·심박을 가져올 수 있다. 같은 운동인지 확인한 뒤 `AI용 JSON 저장`을 누르면 그 날짜의 확정 세션과 확인 대기 건수가 포함된 파일을 저장한다. iPhone에서 Apple 건강을 읽으려면 설치된 앱의 서명에 HealthKit 권한이 포함돼야 한다. 사이드로드 검증에서는 설치 뒤 권한 요청과 실제 운동 조회까지 확인한다.

## 완료 운동 동기화

완료 근력 운동을 두 기기에서 볼 때는 iPhone의 **기록 → 동기화 계정**에서 Android 설정의 **영양 DB**와 같은 Fitness 프로젝트 주소·공개 키·계정으로 로그인한다. 양쪽 **기록 → 하루 운동 기록 → 완료 운동 기기 간 동기화**에서 날짜와 계정을 확인하고 **이 날짜 동기화**를 누른다. 받은 운동은 해당 날짜의 기록에 표시된다. 동기화 메모를 오프라인에서 저장하면 다음 전송에 반영된다. 워치 원본·연결 결정은 기기에서 관리한다. 서버 준비와 AI 조회 범위는 [Supabase 구성](../supabase/README.md)을 따른다.

## 체중 기록과 업데이트 확인

1. iPhone의 **메인 → 체중**, **피트니스 → 체중 기록** 또는 **기록 → 기록 추가 → 체중**에서 입력한다. **기록 → 체중 기록**에서 날짜별 카드를 열어 수정하거나 **이 기록 삭제**를 누를 수 있다.
2. 날짜 선택 중 돌아가면 달력부터 닫히고 편집 중인 값은 그대로 보인다. 편집창에서 취소하거나 돌아가면 저장 전 입력을 버린다.
3. 앱을 완전히 종료한 뒤 **기록 → 체중 기록**에서 값과 표시 단위를 확인한다. 빌드 297 위에 설치할 때는 Sideloadly의 같은 Apple 계정과 최종 번들 ID를 유지한다. **피트니스·기록**에서 진행 중 운동과 완료 이력을 확인한다.

iOS 체중 데이터는 `Library/Application Support/FitnessBody/body-metrics.json`에 저장된다. 같은 날 신규 등록은 가장 최근에 변경한 동일 날짜 기록의 ID를 유지한다. 체중은 기기별 로컬 저장을 사용한다. Android는 기존 `fitness_mvp.db`의 Room 조회·저장·삭제 경로를 사용한다. [공통 개발 기준](../docs/architecture/ANDROID_IOS_DEVELOPMENT.md)

## CI 검증 범위

| 확인 항목 | 확인 방법 |
| --- | --- |
| iPhone ARM64 바이너리 | `bash scripts/ios/package-device-ipa.sh`가 `iphoneos` Release를 빌드하고 패키지를 검사 |
| 시뮬레이터 재실행 | `bash scripts/ios/test-workout-simulator.sh`가 루틴과 테스트 세트를 만든 뒤 진행 중·완료 상태에서 앱을 종료하고 재실행 |
| 시뮬레이터 체중 | 날짜 선택·편집창 닫기, 등록·수정·삭제, 재실행 후 값 확인 |
| 시뮬레이터 덮어 설치 | 같은 iOS 시뮬레이터에서 앱을 덮어 설치하고 운동·체중 저장 파일의 SHA-256을 비교 |
| iPhone 설치, 실행, 재서명 | Windows와 iPhone에서 위 설치 및 데이터 보존 절차를 직접 실행 |

Android 검증은 동일 워크플로의 Android 단위·빌드 및 에뮬레이터 작업에서 수행한다.

## 다음 이식 순서

[Android·iOS 공통화 마일스톤](../docs/architecture/KMP_MILESTONES.md)에 Android 설치본과의 통합 순서, 공통 화면·설정·연동의 범위, iPhone에서 확인할 완료 조건을 정리했다. 첫 운동 검증 경로는 피트니스 탭에서 시작해 일반 세트를 기록하고, 앱 재실행 후 운동을 완료한 다음 기록 상세로 돌아오는 흐름이다.
