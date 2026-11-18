# FitnessApp

Local-first Android fitness logger for workouts, cardio, meals, supplements, and progress review. Detailed workout sessions and meal data are owned by FitnessApp. Personal OS reads a separate completed-workout summary projection; the older Fitness Record Contract v1 sync path remains for compatibility.

The source baseline is [main@0b47010c](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/0b47010c3afbca14792dff162ddc46bf1819563b), reviewed on 2026-11-19. Android uses Compose and Room v61. The `:shared` Kotlin Multiplatform module provides common models, business rules, and repository contracts. The iOS host on `main` verifies SwiftUI integration with the shared framework.

## What the app owns

- Four main tabs — `메인`, `피트니스`, `기록`, and `설정` — with progress review inside Records. Current navigation uses a Compose root alongside retained Java and Android View code.
- Strength routines and free sessions, six record types, six load states, sets, RIR, rest, volume, and completion summaries.
- Walking, running, and cycling sessions with local distance, time, and route data.
- Monthly records, body profile, goals, evidence-based development review, and non-prescriptive paper advice states.
- Ingredient, recipe, external-menu, packaged-product, dining-out, and supplement records in SQLite.
- Exact exercise family/preset/visual-variant identity and verified image fallback; the exercise picker includes front and back muscle maps. PriceTrace product IDs are never replaced by name guesses.
- External-reference nutrition import has an exact URL and seven-nutrient provenance contract; it stays separate from label OCR and food estimates.
- Explicit local JSON backup/restore and CSV summary export. Android automatic backup is disabled.

Detailed scope and boundaries are in [docs/Project_Intro.md](docs/Project_Intro.md) and [docs/Project_Detail.md](docs/Project_Detail.md).

## Current verification

- [Main CI run](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/42347997219) passed Android/shared tests and build, Android emulator instrumentation, shared iOS Simulator tests, and the SwiftUI host build.
- [iOS build 297 CI](https://github.com/Yeon-sik/Yeonsik-Fitness-App/actions/runs/44125211520) passed ARM64 IPA packaging and Simulator checks for the workout flow, relaunch, and reinstall. A user confirmed launching build 297, restarting the app, and reinstalling the same IPA over the existing app on an iPhone with the in-progress workout data retained. This is evidence for the separate [sideload revision](https://github.com/Yeon-sik/Yeonsik-Fitness-App/tree/c2b997b4916c0c22b2d943305173b1b5361537d2).
- Android device upgrade, iOS shared Compose UI, release signing, production data access, and cross-device sync each require verification in the applicable environment. Earlier KMP evidence is recorded in [M8 release readiness](docs/architecture/KMP_M8_RELEASE_READINESS.md).

## Android and iOS development

The [shared development standard](docs/architecture/ANDROID_IOS_DEVELOPMENT.md) defines one implementation of shared Compose screens, state, and actions. The platform hosts connect OS events and storage. The first planned test uses the common Body contract with Android Room and durable iOS storage. Shared screens and cross-device record migration are still planned.

Use the [main SwiftUI host instructions](iosApp/README.md) for the KMP integration proof and the [sideload instructions](https://github.com/Yeon-sik/Yeonsik-Fitness-App/blob/c2b997b4916c0c22b2d943305173b1b5361537d2/iosApp/README.md) for the iPhone workout prototype.

## Build and run

```powershell
.\gradlew.bat testDebugUnitTest --no-daemon
.\gradlew.bat assembleDebug --no-daemon
.\gradlew.bat assembleDebugAndroidTest --no-daemon
```

Use a configured Android device or emulator for installation. Preserve the existing database and verify the signing certificate before an update; use `adb install -r` only with a compatible certificate. Do not use uninstall or `pm clear` as a routine workaround for an incompatible update.

For a configured, ignored session environment, the optional debug task can install the APK and provision a short-lived session outside the APK:

```powershell
.\gradlew.bat installDebugWithEnvSession --no-daemon
```

Missing session values do not prevent the APK from being built; use the in-app Settings login flow instead.

## Three external connection boundaries

FitnessApp keeps these boundaries separate:

1. **Shared Supabase** — shared Personal OS project. The legacy sync allowlist covers `devices`, workout records, `meal_records`, and `weight_records`; `sync_fitness_data_v1` is attempted first, with a bounded REST fallback. A separate v2 RPC publishes only completed-workout summary fields for Personal OS. Detailed sets, meal nutrition, GPS route points, and other local-only extensions are not part of that v2 summary. Remote RPC/RLS and cross-device behavior require project-level verification.
2. **Nutrition Supabase** — independent Nutrition project for public/private nutrition catalog data, recipes, and related ownership. It must not receive migrations intended for the shared project.
3. **PriceTrace** — external read/configuration boundary for exact product identity and nutrition linking. PriceTrace migrations are not owned by this repository.

Auth sessions, encrypted token stores, and Auth UUID checks are separate across connection boundaries. An equal email address does not make two project accounts identical.

See [supabase/README.md](supabase/README.md) before applying any migration.

## Configuration

Keep values in ignored `local.properties`, environment variables, or the documented ignored Supabase environment file. Never commit keys, passwords, tokens, or real user identifiers.

```properties
SUPABASE_URL=https://your-shared-project-ref.supabase.co
SUPABASE_ANON_KEY=your-shared-publishable-or-anon-key
NUTRITION_SUPABASE_URL=https://your-nutrition-project-ref.supabase.co
NUTRITION_SUPABASE_ANON_KEY=your-nutrition-publishable-or-anon-key
PRICETRACE_SUPABASE_URL=https://your-pricetrace-project-ref.supabase.co
PRICETRACE_SUPABASE_ANON_KEY=your-pricetrace-publishable-or-anon-key
MAPS_API_KEY=your-google-maps-android-key
FITNESS_SURFACE=personal
```

`MAPS_API_KEY` is optional for route rendering and must be restricted to the package and signing certificate used by the selected variant. An unknown app surface fails closed to the commercial policy.

## QA and release signing

Debug signing is machine-specific. QA/release variants require external signing configuration. The relevant ignored values are:

```properties
FITNESS_QA_STORE_FILE=path-to-qa-keystore
FITNESS_QA_STORE_PASSWORD=use-the-keystore-password
FITNESS_QA_KEY_ALIAS=fitness-qa
FITNESS_QA_KEY_PASSWORD=use-the-key-password
FITNESS_RELEASE_STORE_FILE=path-to-release-keystore
FITNESS_RELEASE_STORE_PASSWORD=use-the-keystore-password
FITNESS_RELEASE_KEY_ALIAS=release-alias
FITNESS_RELEASE_KEY_PASSWORD=use-the-key-password
```

Build commands:

```powershell
.\gradlew.bat assembleQa
.\gradlew.bat assembleRelease
```

An update keeps app data only when the installed APK and the new APK have compatible signing certificates. Back up first, then use a matching QA/release key and `adb install -r`. Release signing and device data-preservation gates are not verified by the current local build results.

## Data protection and recovery

Access and refresh tokens use AES/GCM with Android Keystore and separate aliases/preferences for shared, Nutrition, and PriceTrace sessions. The explicit `fitness-os.local-backup` JSON format is versioned, size-limited, transactionally restored, and reconciles catalog and route duplicates. The backup file is user data: store it securely and do not commit it.

## Project documentation

- [Project Intro](docs/Project_Intro.md)
- [Project Detail](docs/Project_Detail.md)
- [Android and iOS development standard](docs/architecture/ANDROID_IOS_DEVELOPMENT.md)
- [Refactoring and data compatibility rules](docs/FitnessApp_정돈_리팩터링_규칙.md)
- [Supabase migration boundaries](supabase/README.md)
- [Fitness Record Contract v1](https://github.com/Yeon-sik/Personal-OS/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [Fitness Summary Projection v2](docs/FITNESS_SUMMARY_PROJECTION_V2.md)
- [External reference nutrition contract](contracts/fitness-external-reference.v1.md)
- [Release readiness gates](https://github.com/Yeon-sik/Personal-OS/blob/main/docs/RELEASE_READINESS.md)
