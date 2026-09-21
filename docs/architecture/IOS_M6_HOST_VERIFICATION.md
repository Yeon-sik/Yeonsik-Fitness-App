# M6 iOS host verification

Status: **Windows local host: HOST-UNVERIFIED.** The required macOS resolution gate is the `ios-host` job in [KMP release readiness CI](../../.github/workflows/kmp-release-readiness.yml). Its result must be read from the GitHub Actions run for the reviewed commit; this Windows document does not claim a macOS compile or Simulator result.

## Verified repository wiring

- `shared` configures static `YeonsikShared` frameworks for `iosX64`, `iosArm64`, and `iosSimulatorArm64`.
- `iosApp` imports `YeonsikShared` and calls shared `AccountScope`, `WorkoutPerformanceCalculator`, and `CardioActivityType` values.
- The tracked `YeonsikIosProof` Xcode scheme invokes the existing `embedAndSignAppleFrameworkForXcode` run-script phase. No CocoaPods or external dependency manager is used.

## macOS resolution gate

The CI job runs the following on a GitHub macOS host:

```bash
./gradlew --no-daemon \
  :shared:testAndroid \
  :shared:compileKotlinIosSimulatorArm64 \
  :shared:compileTestKotlinIosSimulatorArm64 \
  :shared:iosSimulatorArm64Test \
  :shared:linkDebugFrameworkIosSimulatorArm64

xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme YeonsikIosProof \
  -configuration Debug \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO \
  build
```

A passing job proves framework generation, generated Swift/Objective-C symbol compilation, and SwiftUI host compilation. It does not prove an interactive simulator launch or user-visible runtime flow.

## Scope boundary

The boundary remains `SwiftUI → shared Domain/API → iOS adapter boundary`. Production database, network, authentication, and Apple platform adapters remain deliberately out of scope.
