# M6 iOS host verification

Status: **HOST-UNVERIFIED on Windows**

The repository contains the `iosApp` SwiftUI proof host and static `YeonsikShared` framework configuration. This Windows workspace cannot run Xcode, compile Swift, inspect the generated Apple header, or launch an iOS Simulator. Therefore no iOS runtime success is claimed.

The required macOS verification commands and visual confirmation steps are recorded in [`iosApp/README.md`](../../iosApp/README.md).

Scope boundary remains `SwiftUI → shared Domain/API → future iOS adapter`; production database, network, authentication, and Apple platform adapters are deliberately out of scope.
