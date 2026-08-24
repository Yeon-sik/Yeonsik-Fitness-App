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

## Two Supabase connections

FitnessApp deliberately uses two independent Supabase projects:

- **Personal OS shared DB:** CashOS, FitnessApp, and PersonalOSApp use the same
  project and the same Auth user for workout, body, and meal records.
- **FitnessApp Nutrition DB:** only FitnessApp uses this project. It owns
  `nutrition_foods`, `nutrition_food_nutrients`, and
  `nutrition_food_components`.

The two URLs, anon keys, encrypted token stores, login sessions, and Auth UUIDs
are separate. A matching email address does not make the Nutrition account the
same account; create or reset its password in the Nutrition project separately.
Public nutrition rows can be read with only the Nutrition connection. Uploading
private foods or recipes requires a Nutrition DB login.

From the `personal-os` sibling checkout, configure FitnessApp from the existing
PersonalOSApp environment and verify that CashOS points to the same project:

```powershell
cd C:\Github\personal-os\FitnessApp
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-shared-supabase.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\configure-shared-supabase.ps1 -CheckOnly
```

The script writes shared values to the ignored `supabase/.env` and preserves any
Nutrition values already present. It prints only project references, never keys.
For a standalone checkout, put both connections in `local.properties`, process
environment variables, or `supabase/.env`:

```properties
SUPABASE_URL=https://your-shared-project-ref.supabase.co
SUPABASE_ANON_KEY=your-shared-publishable-or-anon-key
NUTRITION_SUPABASE_URL=https://your-nutrition-project-ref.supabase.co
NUTRITION_SUPABASE_ANON_KEY=your-nutrition-publishable-or-anon-key
MAPS_API_KEY=your-google-maps-android-key
```

`MAPS_API_KEY` is read from ignored `local.properties` or the build environment.
Restrict the Google Maps Android key to this package and the signing certificate
used by the build variant; keep the key out of Git.

`VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `ORIGINAL_DB_URL`, and
`ORIGINAL_DB_ANON` remain aliases for the shared connection. `NUTRITION_DB_URL`
and `NUTRITION_DB_ANON_KEY` are aliases for the Nutrition connection. A complete
URL/key pair becomes build-managed; an incomplete pair falls back as a whole to
the corresponding manual Settings form.

Each login's access and refresh tokens use a different Android Keystore alias
and preferences namespace. Active Nutrition migrations live under
`supabase/nutrition/supabase/migrations/`; see `supabase/README.md` before
applying them to the Fitness-only remote project.

For a local debug build, use the session-aware install task when
`supabase/.env` contains `EMAIL` and `PASSWORD`:

```powershell
.\gradlew.bat installDebugWithEnvSession --no-daemon
```

It builds the APK, installs it with `adb install -r`, requests short-lived
Supabase sessions outside the APK, and stores the returned tokens in the
Android Keystore. If the credentials are missing or automatic authentication
fails, the APK is still installed and the existing Settings login form is used.
The password is never placed in `BuildConfig` or the APK.

## Shared QA signing

The default Android debug key is generated per computer. Do not use it when
the same APK package must be updated from more than one machine. Create one
separate keystore for this app's QA builds, keep it outside Git, and move that
keystore only through a trusted, encrypted USB or other secure channel. The
keystore contains the private signing key; a certificate or public key alone
cannot sign an APK.

Create the keystore once on a trusted machine. Store it at the same
user-specific path on every build computer. Let `keytool` prompt for both
passwords so they do not appear in shell history:

```powershell
New-Item -ItemType Directory -Force `
  (Join-Path $env:USERPROFILE '.android\keystores\YeonsikFitness') | Out-Null

keytool -genkeypair -v -storetype JKS `
  -keystore (Join-Path $env:USERPROFILE '.android\keystores\YeonsikFitness\fitness-qa.jks') `
  -alias fitness-qa `
  -keyalg RSA -keysize 4096 -validity 10000
```

Copy the same keystore from the encrypted USB to
`%USERPROFILE%\.android\keystores\YeonsikFitness\fitness-qa.jks` on each
computer. Gradle uses this path by default. Only the credentials below need to
be supplied in the ignored `local.properties` file or as environment variables;
never commit them:

```properties
FITNESS_QA_STORE_PASSWORD=use-the-keystore-password
FITNESS_QA_KEY_ALIAS=fitness-qa
FITNESS_QA_KEY_PASSWORD=use-the-key-password
```

`FITNESS_QA_STORE_FILE` remains available as an optional override for an
exceptional machine, but the standard location should be used consistently.

Build and install the shared-key variant with:

```powershell
.\gradlew.bat testDebugUnitTest assembleQa
.\gradlew.bat installQaWithEnvSession --no-daemon
```

`qa` uses the same application ID as the current app, so all computers using
this keystore can update the same installation with `adb install -r`. The
current physical device was installed with a different old key. Therefore the
first migration to this newly created QA key cannot be an in-place update:
back up the device database, uninstall the old package, install the QA APK,
and restore or re-sync the data. After that one-time migration, use only the
shared QA keystore for this package. If the old key is found, use it for the
first update instead and no migration is needed.

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
