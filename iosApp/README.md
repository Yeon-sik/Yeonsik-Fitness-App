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

## 기존 SwiftUI와 KMP 검증 호스트

This is the M6/M7 proof host, not a production iOS implementation. It imports the static `YeonsikShared` KMP framework and renders values obtained from shared `AccountScope`, `WorkoutPerformanceCalculator`, `CardioActivityType`, `BodyMetricsApplicationService`, `BodyProfile`, and the M7 `IosBodyMetricsRepository`.

The intended boundary is:

```text
SwiftUI → shared BodyMetricsApplicationService → shared BodyMetricsRepositoryApi → M7 iOS in-memory adapter
```

The Body screen supports shared-service-backed lookup, add, edit, delete, and profile save. The iOS adapter is explicitly in-memory for M7 functional-parity proof; it is not production persistence and must be replaceable by a durable adapter before a commercial iOS release.

It intentionally contains no Room, Android context, GPS/Maps, Supabase, HealthKit, CoreLocation, MapKit, or production persistence/network adapter.

## macOS host verification

Run these on macOS with Xcode installed from the repository root:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
find shared/build -name YeonsikShared.h -print
grep -n "AccountScope\|WorkoutPerformanceCalculator\|CardioActivityType\|BodyMetricsApplicationService\|BodyMetricsRepositoryApi\|IosBodyMetricsRepository\|BodyProfile" "<path-to-YeonsikShared.h>"

open iosApp/iosApp.xcodeproj
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme YeonsikIosProof \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  build
```

In Xcode, select an available simulator, run `YeonsikIosProof`, and confirm that the M6 shared-derived values and the Body screen are visible. On the Body screen, verify add → lookup → edit → delete and profile save. The generated Objective-C header is the source of truth for Swift spellings; if it differs from `ContentView.swift` or `BodyView.swift`, update the calls from that header before treating the host proof as verified. Owner A/B isolation must also be checked with the adapter or a small macOS harness; the screen intentionally uses one proof owner.

Xcode's `Build YeonsikShared framework` run-script phase calls Gradle's standard `:shared:embedAndSignAppleFrameworkForXcode` task. It must remain enabled; no CocoaPods or external dependency manager is required.
