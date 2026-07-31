# FitnessApp 1.0

Local-first Android workout logger connected to Personal OS through the shared
Fitness Record Contract v1.

## Scope

- Owns detailed workout exercises and sets.
- Publishes only completed workout summaries to Personal OS.
- Supports `weight_reps`, `reps_only`, `time`, `weight_time`,
  `assisted_weight_reps`, and `bodyweight_added_weight_reps`.
- Uses Supabase Auth access tokens and production RLS; an anon key alone never
  authorizes record access.
- Encrypts persisted Android session tokens with Android Keystore.

## Run

```powershell
cd C:\Github\personal-os\FitnessApp
.\gradlew.bat testDebugUnitTest assembleDebug
```

## Release

Signed release builds require:

```text
FITNESS_RELEASE_STORE_FILE
FITNESS_RELEASE_STORE_PASSWORD
FITNESS_RELEASE_KEY_ALIAS
FITNESS_RELEASE_KEY_PASSWORD
```

Then run:

```powershell
.\gradlew.bat assembleRelease
```

See `..\PersonalOSApp\docs\RELEASE_READINESS.md` for the database, RLS,
cross-app, signing, privacy, and physical-device gates.
