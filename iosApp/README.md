# Yeonsik iOS shared Body vertical slice

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
