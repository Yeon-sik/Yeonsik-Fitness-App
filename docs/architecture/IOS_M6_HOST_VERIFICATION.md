# M6 iOS host verification

Status: **HOST COMPILE VERIFIED / DEVICE UNVERIFIED.** GitHub Actions [run 35553619632](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/35553619632) for `be1a8651e57b8b16c65aa223205cb06e24d1850c` passed the `ios-host` job, including `linkDebugFrameworkIosSimulatorArm64` and the `YeonsikIosProof` SwiftUI host build. The Windows workspace itself still did not execute a macOS/Xcode or interactive Simulator runtime.

## Verified repository wiring

- `shared` configures static `YeonsikShared` frameworks for `iosX64`, `iosArm64`, and `iosSimulatorArm64`.
- `iosApp` imports `YeonsikShared` and calls shared `AccountScope`, `WorkoutPerformanceCalculator`, and `CardioActivityType` values.
- The tracked `YeonsikIosProof` Xcode scheme invokes the existing `embedAndSignAppleFrameworkForXcode` run-script phase. No CocoaPods or external dependency manager is used.

## macOS CI evidence

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

[Run 35553619632](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/35553619632) completed these commands successfully on a GitHub macOS host. It proves framework generation, generated Swift/Objective-C symbol compilation, and SwiftUI host compilation. It does not prove an interactive simulator launch or user-visible runtime flow.

## Scope boundary

The boundary remains `SwiftUI → shared Domain/API → iOS adapter boundary`. Production database, network, authentication, and Apple platform adapters remain deliberately out of scope.
