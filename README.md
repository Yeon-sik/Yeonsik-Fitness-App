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

## Shared Supabase project

FitnessApp uses the same Supabase Auth project as CashOS and PersonalOS when
the following values are supplied in `local.properties` (the file is not
committed):

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-publishable-or-anon-key
```

The same values can also be supplied through `SUPABASE_URL` and
`SUPABASE_ANON_KEY` environment variables. When both values are complete, the
build-managed connection takes priority and cannot be edited in the app. If
either value is absent or incomplete, Settings exposes a manual fallback
connection for local setup. After email sign-in or sign-up, the Supabase Auth
user UUID is used as `user_id` for remote synchronization. An access token is
stored only through Android Keystore-backed encryption.

For local development, the same shared DB values may be stored in the ignored
`supabase/.env` file as `ORIGINAL_DB_URL` and `ORIGINAL_DB_ANON`. The build reads
those values as a fallback; `NUTRITION_DB_URL` and `NUTRITION_DB_ANON` remain
Settings-only values so the Nutrition DB connection can be changed without a
new APK build.

## Nutrition Supabase project

FitnessApp alone connects to the separate Nutrition DB. Its URL and anon key
are entered in the FitnessApp Settings screen, followed by a separate
Nutrition DB login. The Nutrition connection, account identity, and encrypted
session tokens use storage isolated from the shared Supabase project. Never
commit either project's real URL/key credentials.

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
