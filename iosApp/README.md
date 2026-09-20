# Yeonsik iOS shared integration proof

This is the M6 proof host, not a production iOS implementation. It imports the static `YeonsikShared` KMP framework and renders values obtained from `AccountScope`, `WorkoutPerformanceCalculator`, and `CardioActivityType`.

The intended boundary is:

```text
SwiftUI → shared Domain/API → future iOS adapter
```

It intentionally contains no Room, Android context, GPS/Maps, Supabase, HealthKit, CoreLocation, MapKit, or production persistence/network adapter.

## macOS host verification

Run these on macOS with Xcode installed from the repository root:

```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
find shared/build -name YeonsikShared.h -print
grep -n "AccountScope\|WorkoutPerformanceCalculator\|CardioActivityType" "<path-to-YeonsikShared.h>"

open iosApp/iosApp.xcodeproj
xcodebuild \
  -project iosApp/iosApp.xcodeproj \
  -scheme YeonsikIosProof \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  build
```

In Xcode, select an available simulator, run `YeonsikIosProof`, and confirm that the three shared-derived values are visible. If the generated Objective-C header uses different Swift spellings than `ContentView.swift`, update the calls from that generated header before treating the host proof as verified.

Xcode's `Build YeonsikShared framework` run-script phase calls Gradle's standard `:shared:embedAndSignAppleFrameworkForXcode` task. It must remain enabled; no CocoaPods or external dependency manager is required.
