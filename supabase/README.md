# Supabase layout

FitnessApp has two independent remote database targets:

- `shared/supabase/`: configuration boundary for the Personal OS project used
  by CashOS, FitnessApp, and PersonalOSApp. FitnessApp does not own or deploy
  shared-project migrations from this directory.
- `nutrition/supabase/`: migrations for the FitnessApp-only Nutrition project.
  It contains the Fitness-owned food, recipe, nutrient catalog, and authenticated
  detailed Meal ingest tables. Catalog synchronization remains limited to the
  catalog tables; Meal/MealItem snapshots are written only by their verified RPC.
- `.env`: ignored build configuration containing both independent URL/key pairs.

## Completed strength workouts across devices

The Fitness-owned project under `nutrition/supabase` stores completed strength
sessions in `fitness_workout_private.completed_workouts`. Deploy the
`20261124130719_fitness_completed_workout_sync_v1` migration to this target
before using the two mobile clients. The RPCs use the signed-in user's identity.
The private schema stays outside the Data API's exposed schemas. Each device
explicitly exchanges one recorded date. Session, exercise and set IDs, volume,
original units and repetitions travel together. A memo can be queued offline.
Revisions detect concurrent edits and keep verified deletions as tombstones.
The native workout stays on its originating device. The receiving app caches
the dated snapshot under the signed-in account and shows it in the daily record.
In the sync dialog, **최근 7일 AI 요약 저장** exports only aggregate counts,
volume and duration coverage from the synced Fitness sessions of either device.
It uses the read-only `fitness_completed_workout_summary_v1` RPC.

The Personal OS shared project serves the separate `fitness-ai-summary` Edge
Function from `shared/supabase/functions`. After deployment an authenticated
first-party caller can POST `{"from":"2025-01-01","to":"2025-01-07"}` for up
to 31 days using that project's user session. It returns completed session counts,
known duration with coverage, and body-part set totals from the existing v2
projection. A Fitness-private snapshot contributes to this endpoint only when a
completed summary has been published to Personal OS. Keep user credentials in
the calling app and hand the selected summary to the AI only on user request.

Never link `supabase/nutrition` to the Personal OS shared project. The first
Nutrition migration intentionally fails if it detects workout, body, or meal
record tables, preventing the catalog schema from being deployed to the wrong
project.

Verify and deploy the Nutrition project:

```powershell
supabase link --project-ref <nutrition-project-ref> --workdir supabase/nutrition
supabase migration list --linked --workdir supabase/nutrition
supabase db push --linked --dry-run --workdir supabase/nutrition
supabase db push --linked --yes --workdir supabase/nutrition
supabase db lint --linked --workdir supabase/nutrition
```

Verify the shared project reference without applying Nutrition migrations:

```powershell
supabase link --project-ref <shared-project-ref> --workdir supabase/shared
supabase migration list --linked --workdir supabase/shared
```

An earlier local branch revision deployed Nutrition tables to the shared remote
project. The corrected app never reads or writes those shared-project tables.
Dropping remote tables is destructive and must be handled later as a separately
reviewed cleanup after confirming that they contain no required rows.
