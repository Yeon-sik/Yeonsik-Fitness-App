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

## Personal OS shared Supabase

CashOS, FitnessApp, and PersonalOSApp are separate apps, but they use one
Supabase project. Each app stores its own local login session, so you sign in
once per app. Using the same email account in each app resolves to the same
Supabase Auth `user.id`, and RLS uses that ID to isolate the user's rows.

FitnessApp uses that one connection for both areas:

- workout, body, and meal records;
- `nutrition_foods`, `nutrition_food_nutrients`, and
  `nutrition_food_components` catalog tables.

There is no separate Nutrition DB URL, account, or login.

From the `personal-os` sibling checkout, configure FitnessApp from the existing
PersonalOSApp environment and verify that CashOS points to the same project:

```powershell
cd C:\Github\personal-os\FitnessApp
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-shared-supabase.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-shared-supabase.ps1 -CheckOnly
```

The script writes the ignored `supabase/.env` file and prints only the project
reference, never the key. For a standalone checkout, put the same values in
`local.properties` instead (the file is not committed):

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_ANON_KEY=your-publishable-or-anon-key
```

The same values can also be supplied through `SUPABASE_URL` and
`SUPABASE_ANON_KEY` environment variables. `VITE_SUPABASE_URL` and
`VITE_SUPABASE_ANON_KEY` are accepted so the same naming used by the sibling
apps can be reused. When both values are complete, the build-managed connection
takes priority and cannot be edited in the app. If either value is absent,
Settings shows an explicit local-only/manual-fallback state.

After email sign-in or sign-up, the Supabase Auth UUID is used for all Fitness
record and private nutrition rows. Access and refresh tokens are stored only
through Android Keystore-backed encryption. The active nutrition migrations
live under `supabase/shared/supabase/migrations/`; see `supabase/README.md`
before applying them to a remote project.

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
