# M7 iOS Body host verification

Status: **Windows local host: CODE COMPLETE / HOST-UNVERIFIED.** The M8 `ios-host` CI job is the required macOS resolution gate. Its GitHub Actions result, not this Windows workspace, determines whether framework generation and Swift compilation are host-verified for a reviewed commit.

## Integration path

```text
SwiftUI BodyView
  → shared BodyMetricsApplicationService
  → shared BodyMetricsRepositoryApi
  → IosBodyMetricsRepository (in-memory iOS adapter)
```

`IosBodyMetricsRepository` is owner-scoped and intentionally in-memory for functional-parity verification. It is not production persistence. Its replaceable repository API boundary must remain intact until a separately approved durable iOS adapter is supplied.

## Verified on Windows (2026-09-21)

- `:shared:compileKotlinIosSimulatorArm64` and `:shared:compileTestKotlinIosSimulatorArm64` completed successfully.
- `:shared:testAndroid`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug` completed successfully.
- The tracked Xcode scheme builds `BodyView.swift` and retains the Gradle `embedAndSignAppleFrameworkForXcode` run-script phase.
- No Room schema, migration, external contract, or production iOS persistence implementation changed in M8.

Android instrumentation runtime is not claimed here because the attached device has a signing certificate incompatible with the current debug APK; M8 preserved its existing app data instead of forcing an install.

## Required macOS/Xcode resolution gate

The `ios-host` CI job runs:

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

`iosSimulatorArm64Test` includes `IosBodyMetricsRepositoryTest`, which checks in-memory Body CRUD, profile handling, and owner A/B isolation. A passing macOS CI job verifies framework linking and Swift symbol compilation; it does not prove an interactive simulator run. The latter must still cover add → lookup → edit → delete, profile save, and owner-isolation behavior before a device/runtime claim.
