# FitnessApp

Local-first Android fitness logger for detailed workouts, cardio, meals, supplements, and progress review. The app owns detailed records locally and shares only completed workout summaries with Personal OS through Fitness Record Contract v1.

Current documentation is based on `main` at `89672f52cf4cd5219e6e1cd5df71bcefb3792885` (2026-09-05).

## What the app owns

- Five app areas: `메인`, `피트니스`, `기록`, `발전`, and `설정`.
- Strength routines and free sessions, six record types, six load states, sets, RIR, rest, volume, and completion summaries.
- Walking, running, and cycling sessions with local distance, time, and route data.
- Monthly records, body profile, goals, evidence-based development review, and non-prescriptive paper advice states.
- Ingredient, recipe, external-menu, packaged-product, dining-out, and supplement records in SQLite.
- Exact exercise family/preset/visual-variant identity and verified image fallback; PriceTrace product IDs are never replaced by name guesses.
- Explicit local JSON backup/restore and CSV summary export. Android automatic backup is disabled.

Detailed scope and boundaries are in [docs/Project_Intro.md](docs/Project_Intro.md) and [docs/Project_Detail.md](docs/Project_Detail.md).

## Current verification

Executed locally against the source above on 2026-09-05:

- `testDebugUnitTest` — passed.
- `assembleDebug` — passed.
- `assembleDebugAndroidTest` — passed; test APK compilation/packaging only.
- `lintDebug` — failed with 4 API 27 resource errors and 54 warnings.
- Four ADB endpoints/emulators were observed with `adb devices -l`; instrumentation was not executed.

A successful APK build is not proof of physical-device behavior, release signing, Supabase/RLS behavior, or cross-app synchronization.

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

1. **Shared Supabase** — shared Personal OS project for the current sync allowlist: `devices`, workout records, `meal_records`, and `weight_records`. `sync_fitness_data_v1` is attempted first, with a bounded legacy REST fallback. Detailed meal nutrition, GPS route points, and other local-only extensions remain local until their remote contract exists.
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
- [Supabase migration boundaries](supabase/README.md)
- [Fitness Record Contract v1](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/FITNESS_RECORD_CONTRACT_V1.md)
- [Release readiness gates](https://github.com/Yeon-sik/Always_Memo/blob/main/docs/RELEASE_READINESS.md)
