# M7 iOS Body host verification

Status: `⚠ CODE COMPLETE / HOST-UNVERIFIED` on the current Windows host.

The repository now contains the M7 integration path:

```text
SwiftUI BodyView
  → shared BodyMetricsApplicationService
  → shared BodyMetricsRepositoryApi
  → IosBodyMetricsRepository (M7 in-memory adapter)
```

`IosBodyMetricsRepository` is an owner-scoped, in-memory adapter for functional-parity verification. It is not production persistence. The adapter is intentionally replaceable before M8 without changing the SwiftUI or shared application boundary.

## Completed on Windows

- Shared Android host tests passed, including Body CRUD/use-case and owner-scope coverage.
- Android instrumentation source compilation passed; no Android device is attached for instrumentation runtime verification.
- Android shared/common and iOS Kotlin target compilation completed.
- The Xcode project contains the `BodyView.swift` source and keeps the Gradle `embedAndSignAppleFrameworkForXcode` run-script phase.
- No Room schema, migration, external contract, or production iOS persistence was changed.

## Required macOS/Xcode verification

Run from the repository root on macOS:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
find shared/build -name YeonsikShared.h -print
grep -n "BodyMetricsApplicationService\|BodyMetricsRepositoryApi\|IosBodyMetricsRepository\|BodyProfile" "<path-to-YeonsikShared.h>"
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme YeonsikIosProof \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  build
```

Use the generated Objective-C header to confirm the exact Swift import and symbol spellings before changing any Swift call sites. In the simulator, open Body and verify:

1. add a weight, look it up by date, edit it, and delete it;
2. save and reload a valid `BodyProfile` height;
3. run a small adapter/harness check showing owner A cannot read or mutate owner B's records or profile.
