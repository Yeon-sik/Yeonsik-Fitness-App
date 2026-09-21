# KMP M8 release readiness

**Source boundary:** `feat/kmp-m8-hardening-release-gate`, based on `main@c922c24727521b375cec1c9a4cf25dd7173810ed`.

**Local evidence date/environment:** 2026-09-21, Windows host, JDK 17/Gradle wrapper, one attached Android device. This report distinguishes local execution, CI configuration, device execution, and commercial-release readiness. It does not infer a remote or runtime result from a compile result.

## VERIFIED

### CI definition

`.github/workflows/kmp-release-readiness.yml` adds non-ignored GitHub Actions gates:

- Linux: `:shared:testAndroid`, `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and `:app:lintDebug`.
- macOS: shared Android-host/common tests, iOS Simulator Kotlin main/test compile, `:shared:iosSimulatorArm64Test`, `:shared:linkDebugFrameworkIosSimulatorArm64`, and `xcodebuild` of the tracked `YeonsikIosProof` scheme.
- The Linux job rejects tracked local credential/signing paths and high-confidence credential literals before build work. It does not receive or add secret values.

No CI step uses `continue-on-error` or suppresses a failing build, test, lint, framework, or Swift host compile.

### Local Android and shared gates

The following completed successfully on this host:

```powershell
.\gradlew.bat --no-daemon :shared:testAndroid :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
.\gradlew.bat --no-daemon :shared:compileKotlinIosSimulatorArm64 :shared:compileTestKotlinIosSimulatorArm64
```

- `:shared:testAndroid` re-executed 11 XML suites with zero failures/errors; this includes `DomainApiCompatibilityTest`, Body application/use-case, `BodyProfile`, Cardio, Meal, and Records model suites.
- Android unit XML reports contain 74 suites / 335 tests with zero failures/errors; debug APK assembly and Android test APK compilation/packaging also passed.
- `lintDebug` now has `0 errors, 29 warnings, 3 hints`. The M8 corrections preserve the existing back-stack owner, use public Android main-thread/Compose APIs, and put API 27 navigation style properties in `values-v27`; no persistence or domain semantics were changed.
- iOS Simulator Kotlin source and test-source compilation passed on Windows. This is source compilation only, not Apple framework linking.

### Data and contract review

- The M8 working diff has no changed Room schema, migration, SQLite/Room adapter, Supabase migration, or external-contract path. `app/schemas`, Android database sources, `supabase`, and `shared/commonMain` were checked against the stated baseline.
- `shared/commonMain` has no imports from Android, Room, SQLite, Android Context, `java.*`, `javax.*`, or `org.json`.
- Existing regression coverage remains present for Body upsert/tombstone/owner isolation, historical database upgrade, backup/import/export, and local-preserving Supabase sync. The Android test APK compiles those instrumentation sources; runtime execution is classified separately below.
- The tracked `.jsonl` candidate found by the fixture scan is an evidence-rule source file, not a database/backup or production-user fixture.

### Security and signing review

- Static tracked-file scan found no credential/signing artifact except the intentional `supabase/.env.example` template; high-confidence API/token/private-key literal scan found no match.
- Root `.gitignore` now covers `.env`, `.env.*`, and keystore/private-key extensions while keeping documented `.env.example` templates trackable. `local.properties` and `supabase/.env` were already ignored.
- `app/build.gradle` retains separate debug, QA, and release signing paths. QA/release values remain externally supplied; no key or password was added to the repository.

## HOST/DEVICE UNVERIFIED

- macOS/Xcode is unavailable on this Windows host. The local host did not link `YeonsikShared`, inspect its generated Apple header, compile `BodyView.swift`, or run an iOS Simulator. The required `ios-host` GitHub Actions result is the only CI evidence for those compile gates.
- The connected physical Android device has `com.yeonsik.fitnessapp` installed, but its signing certificate differs from the current debug APK. To preserve existing app data, M8 did not attempt an incompatible update, uninstall, `pm clear`, or instrumentation install. Therefore real-device Android instrumentation execution is unverified.
- No interactive iOS Simulator add → lookup → edit → delete/profile workflow was executed. A successful future macOS compile job must not be reported as runtime verification.
- Supabase Auth/RLS and production sync behavior were not exercised against a remote environment.

## COMMERCIAL BLOCKER

**COMMERCIAL-IOS BLOCKED: durable persistence required**

`IosBodyMetricsRepository` is deliberately owner-scoped in-memory storage for M7 architecture/functional-parity proof. M8 confirms the replaceable shared repository boundary, but does not choose UserDefaults, a database, or another durable technology. A separately approved persistence design and adapter implementation are required before commercial iOS release; this is not a failure of the Android/KMP hardening gates.

## DEFERRED

- Real-device Android instrumentation must use an APK signed with the installed app's compatible certificate and preserve user data throughout the test path.
- iOS Simulator runtime/manual UI proof, device proof, durable persistence design, and production Apple platform adapters remain later work.
- The remaining lint warnings/hints are recorded rather than hidden: `ApplySharedPref`, `DefaultLocale`, Compose `ModifierParameter`, `ObsoleteSdkInt`, unused resources, and primitive-state hints. They are not M8 P0 build/test failures.
- Home, Nutrition Analysis, and Statistics shared expansion remains outside M5/M8 because their `java.time`, `org.json`, and JVM formatting dependencies need a separate scoped migration.

## P0 assessment and M8 decision

No P0 was observed in the Windows-executable shared/Android gates, static data-contract review, or secret scan. The per-commit macOS `ios-host` job remains a required P0 gate: a framework, Kotlin/Native iOS test, or Swift host compile failure changes the result to `M8 BLOCKED — P0`.

With that CI requirement enforced and the in-memory iOS adapter intentionally retained, the code hardening decision is:

`M8 ⚠ HARDENING COMPLETE / COMMERCIAL-IOS BLOCKED`

This status does not claim device, iOS Simulator runtime, remote Supabase/RLS, or commercial iOS persistence readiness.
