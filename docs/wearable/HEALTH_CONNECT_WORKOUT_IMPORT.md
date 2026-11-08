# Health Connect workout import

Implemented on `feat/health-connect-workout-import-20261109`.

## User flow

- Fitness tab → **밴드 운동·심박 가져오기** → read permissions → date → query → select → confirm save.
- Active or completed strength workout → **이 운동의 밴드 기록 가져오기·확인** → select a same-day measurement → confirm link.
- Saved external workouts remain available on the import screen after process restart. Linked measurements are shown when opened from that Fitness workout.
- This reads measurements already delivered to Health Connect. It is not a live Fit3 sensor connection. The Samsung export utility is still needed on the tested device until its native export works.
- Existing canonical workouts, sets, manual averages and duration stay intact. External workouts are stored separately and do not automatically create completed workouts, add volume or alter statistics. The import screen labels the average as the measured-sample mean; provider summaries can use different calculation rules.

## Ownership and data flow

`Workout entry → WearableImportViewModel → HealthConnectWorkoutReader / WearableWorkoutRepository → Room → fitness_mvp.db`.

The main manifest requests only exercise and heart-rate read access. Health Connect is now an app dependency; the PR0 diagnostic launcher remains debug-only. The reader finishes all pages, matches the exercise origin when reading heart rate, filters by the exact interval, preserves timestamps and values, rejects conflicting same-time observations and never fills gaps. An empty heart-rate series is displayed explicitly.

`WearableImportContent` receives state and callbacks. The ViewModel owns IO, selected candidates and account scope; the activity owns the permission launcher. Imported observations are rendered as individual points so missing intervals are not presented as continuous measurements.

Room v61→v62 adds only `wearable_workouts`. Its key is `(user_id, origin, provider_id)`. Re-import replaces this account's same external identity while retaining its existing workout link. Linking requires an existing, non-deleted workout of the same account and date; an existing different link is rejected. No native workout DAO writes occur.

Measurements remain local and account-scoped. They are excluded from existing cloud sync, generic file backups and automatic anonymous-account claim. A different account requires its own explicit import. No source-app/Health Connect writes or deletes are performed. Raw measurements and source IDs must not be committed to the repository or printed in logs.

## Verification on 2026-11-09

- `testDebugUnitTest assembleDebug`: 694 unit tests, 692 passed, 2 skipped, zero failures.
- QA build signed with the same certificate as the installed app; updated using `adb install -r`, without uninstall or data clearing.
- `WearableWorkoutMigrationTest` passed on Galaxy A25: v61 fixture migration, original workout/exercise/set values, upsert identity, exact pulse persistence, account isolation, link ownership and stale-owner rejection.
- Real-device UI: query one source workout with 319 measured samples; save; repeat save; restart app. SQLite contains one external workout with the same 319 samples.
- Existing physical-device records were compared before/after by deterministic hashes of every value: 493 workouts, 2,190 exercises, 7,458 sets, one cardio session, 84 meals and 50 weight rows all unchanged.
- Active workout entry was observed on device without completing, cancelling or altering the ongoing session. Actual linkage rules were tested on synthetic fixtures; no synthetic workout was inserted into the user's history.
- No production cloud/RLS or Play distribution verification was performed. Source IDs, raw pulse series and personal workout dates are omitted from this report.

Device-test build command (existing project option):

```powershell
.\gradlew.bat -PfitnessAndroidTestBuildType=qa testDebugUnitTest assembleDebug assembleQa assembleQaAndroidTest
```

Run only the scoped instrumentation class against its isolated fixture DB:

```text
com.yeonsik.fitnessapp.core.database.WearableWorkoutMigrationTest
```

Official API reference: [Reading Health Connect records](https://developer.android.com/health-and-fitness/health-connect/read-data).
